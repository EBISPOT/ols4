use std::collections::HashMap;
use std::fs::File;
use std::io::{BufRead, BufReader};
use std::path::Path;

/// A single curated mapping entry.
#[derive(Debug, Clone)]
pub struct CuratedFromEntry {
    pub text: String,
    pub source: String,
    pub subject_categories: Vec<String>,
}

/// Aggregation key for grouping raw SSSOM rows before merging IDs.
#[derive(Hash, Eq, PartialEq)]
struct AggKey {
    iri: String,
    text: String,
    source: String,
}

/// Parse all SSSOM files and build an IRI → Vec<CuratedFromEntry> map.
///
/// Each file is parsed independently; results are merged into a single map.
pub fn load_sssom_files(paths: &[String]) -> Result<HashMap<String, Vec<CuratedFromEntry>>, Box<dyn std::error::Error>> {
    // First pass: aggregate raw rows by (IRI, text, source)
    let mut agg: HashMap<AggKey, CuratedFromEntry> = HashMap::new();

    for path in paths {
        parse_sssom_file(path, &mut agg)?;
    }

    eprintln!("SSSOM: loaded {} unique (IRI, text, source) entries from {} files",
              agg.len(), paths.len());

    // Second pass: group by IRI
    let mut result: HashMap<String, Vec<CuratedFromEntry>> = HashMap::new();
    for (key, entry) in agg {
        result.entry(key.iri).or_default().push(entry);
    }

    Ok(result)
}

/// Derive the source name from a raw stem (filename without `.sssom.tsv`).
/// For split files like `clinvar-xrefs-01`, strips the trailing `-NN` suffix.
fn strip_split_suffix(stem: &str) -> String {
    if let Some(pos) = stem.rfind('-') {
        let suffix = &stem[pos + 1..];
        if !suffix.is_empty() && suffix.chars().all(|c| c.is_ascii_digit()) {
            return stem[..pos].to_string();
        }
    }
    stem.to_string()
}

/// Derive the source name from the SSSOM `mapping_set_id` URL.
/// Extracts the last path segment and strips `.sssom.tsv` and split suffixes.
fn source_from_mapping_set_id(url: &str) -> String {
    let last_segment = url.rsplit('/').next().unwrap_or("");
    let stem = last_segment.strip_suffix(".sssom.tsv").unwrap_or(last_segment);
    strip_split_suffix(stem)
}

/// Derive the source name from the SSSOM filename stem.
/// For split files like `clinvar-xrefs-01.sssom.tsv`, strips the `-NN` suffix.
fn source_from_filename(path: &str) -> String {
    let stem = Path::new(path)
        .file_name()
        .unwrap_or_default()
        .to_str()
        .unwrap_or("")
        .strip_suffix(".sssom.tsv")
        .unwrap_or("");
    strip_split_suffix(stem)
}

/// Parse a single SSSOM file and merge into the aggregation map.
fn parse_sssom_file(
    path: &str,
    agg: &mut HashMap<AggKey, CuratedFromEntry>,
) -> Result<(), Box<dyn std::error::Error>> {
    let file = File::open(path)?;
    let reader = BufReader::new(file);
    let mut lines = reader.lines();

    // ── Parse YAML header (lines starting with #) ──
    let mut curie_map: HashMap<String, String> = HashMap::new();
    let mut in_curie_map = false;
    let mut first_data_line: Option<String> = None;
    let mut mapping_set_id: Option<String> = None;

    for line_result in &mut lines {
        let line = line_result?;

        if let Some(stripped) = line.strip_prefix('#') {
            let trimmed = stripped.trim();

            if trimmed.starts_with("curie_map:") {
                in_curie_map = true;
                continue;
            }

            if in_curie_map {
                // curie_map entries are indented with 2 spaces: `  PREFIX: http://...`
                if stripped.starts_with("  ") {
                    let entry = stripped.trim();
                    if let Some(colon_pos) = entry.find(':') {
                        let prefix = entry[..colon_pos].trim();
                        let expansion = entry[colon_pos + 1..].trim();
                        if !prefix.is_empty() && !expansion.is_empty() {
                            curie_map.insert(prefix.to_string(), expansion.to_string());
                        }
                    }
                } else {
                    // No longer indented → end of curie_map block
                    in_curie_map = false;
                }
            }

            // Parse mapping_set_id from YAML header
            if let Some(rest) = trimmed.strip_prefix("mapping_set_id:") {
                let val = rest.trim();
                if !val.is_empty() {
                    mapping_set_id = Some(val.to_string());
                }
            }

            continue;
        }

        // First non-# line is the TSV header
        first_data_line = Some(line);
        break;
    }

    // Derive source from mapping_set_id (preferred) or filename (fallback)
    let source = if let Some(ref msid) = mapping_set_id {
        source_from_mapping_set_id(msid)
    } else {
        source_from_filename(path)
    };

    // ── Parse TSV header ──
    let header_line = first_data_line.ok_or_else(|| {
        format!("SSSOM file {} has no TSV header", path)
    })?;

    let headers: Vec<&str> = header_line.split('\t').collect();

    // Required columns
    let idx_subject_label = headers.iter().position(|h| *h == "subject_label")
        .ok_or_else(|| format!("SSSOM file {} missing required column 'subject_label'", path))?;
    let idx_object_id = headers.iter().position(|h| *h == "object_id")
        .ok_or_else(|| format!("SSSOM file {} missing required column 'object_id'", path))?;

    // Optional columns
    let idx_subject_category = headers.iter().position(|h| *h == "subject_category");

    // ── Parse TSV body ──
    let mut row_count: u64 = 0;

    for line_result in lines {
        let line = line_result?;
        if line.is_empty() {
            continue;
        }

        let fields: Vec<&str> = line.split('\t').collect();

        if fields.len() <= idx_subject_label || fields.len() <= idx_object_id {
            continue; // skip malformed rows
        }

        let subject_label = fields[idx_subject_label].trim();
        let object_id_curie = fields[idx_object_id].trim();

        if subject_label.is_empty() || object_id_curie.is_empty() {
            continue;
        }

        // Expand CURIE to IRI
        let iri = match expand_curie(object_id_curie, &curie_map) {
            Some(iri) => iri,
            None => {
                // If we can't expand, skip this row
                continue;
            }
        };

        // Parse subject_category (may be comma-separated for FAANG)
        let categories: Vec<String> = idx_subject_category
            .and_then(|i| fields.get(i))
            .map(|s| s.trim())
            .filter(|s| !s.is_empty())
            .map(|s| s.split(',').map(|c| c.trim().to_string()).filter(|c| !c.is_empty()).collect())
            .unwrap_or_default();

        let key = AggKey {
            iri: iri.clone(),
            text: subject_label.to_string(),
            source: source.clone(),
        };

        let entry = agg.entry(key).or_insert_with(|| CuratedFromEntry {
            text: subject_label.to_string(),
            source: source.clone(),
            subject_categories: Vec::new(),
        });

        // Merge subject categories (dedup)
        for cat in &categories {
            if !entry.subject_categories.contains(cat) {
                entry.subject_categories.push(cat.clone());
            }
        }

        row_count += 1;
    }

    eprintln!("SSSOM: parsed {} rows from {} (source: {})", row_count, path, source);
    Ok(())
}

/// Expand a CURIE like `CHEBI:15929` to an IRI using the curie_map.
/// OBO-style CURIEs use `_` as separator in IRIs (e.g. `CHEBI_15929`).
fn expand_curie(curie: &str, curie_map: &HashMap<String, String>) -> Option<String> {
    let colon_pos = curie.find(':')?;
    let prefix = &curie[..colon_pos];
    let local_id = &curie[colon_pos + 1..];

    let expansion = curie_map.get(prefix)?;

    // OBO CURIEs: the expansion typically ends with `_` or `/` and local_id follows
    // SSSOM curie_map expansions are complete URI prefixes
    Some(format!("{}{}", expansion, local_id))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_source_from_filename() {
        assert_eq!(source_from_filename("atlas.sssom.tsv"), "atlas");
        assert_eq!(source_from_filename("clinvar-xrefs-01.sssom.tsv"), "clinvar-xrefs");
        assert_eq!(source_from_filename("clinvar-xrefs-32.sssom.tsv"), "clinvar-xrefs");
        assert_eq!(source_from_filename("/path/to/gwas.sssom.tsv"), "gwas");
        assert_eq!(source_from_filename("faang.sssom.tsv"), "faang");
        assert_eq!(source_from_filename("ebi-biosamples.sssom.tsv"), "ebi-biosamples");
    }

    #[test]
    fn test_source_from_mapping_set_id() {
        assert_eq!(
            source_from_mapping_set_id("https://raw.githubusercontent.com/mapping-commons/ebi-text-mappings/main/mappings/atlas.sssom.tsv"),
            "atlas"
        );
        assert_eq!(
            source_from_mapping_set_id("https://raw.githubusercontent.com/mapping-commons/ebi-text-mappings/main/mappings/clinvar-xrefs/clinvar-xrefs-01.sssom.tsv"),
            "clinvar-xrefs"
        );
        assert_eq!(
            source_from_mapping_set_id("https://example.com/faang.sssom.tsv"),
            "faang"
        );
    }

    #[test]
    fn test_expand_curie() {
        let mut curie_map = HashMap::new();
        curie_map.insert("CHEBI".to_string(), "http://purl.obolibrary.org/obo/CHEBI_".to_string());
        curie_map.insert("EFO".to_string(), "http://www.ebi.ac.uk/efo/EFO_".to_string());

        assert_eq!(
            expand_curie("CHEBI:15929", &curie_map),
            Some("http://purl.obolibrary.org/obo/CHEBI_15929".to_string())
        );
        assert_eq!(
            expand_curie("EFO:0001421", &curie_map),
            Some("http://www.ebi.ac.uk/efo/EFO_0001421".to_string())
        );
        assert_eq!(expand_curie("UNKNOWN:123", &curie_map), None);
    }
}
