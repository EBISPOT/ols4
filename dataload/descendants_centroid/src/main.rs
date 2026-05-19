//! descendants_centroid
//!
//! For each term in the loaded avg-parquets, compute the mean embedding of all
//! strict descendants (i.e. the full subtree minus the term itself).  Leaf terms
//! (no descendants that have an embedding) produce no output row, which means
//! json2postgres will write NULL for their column.
//!
//! Additionally, one "ontology-level" row is written per ontology_id: the mean
//! of ALL term embeddings in that ontology.  The row uses entity_type="ontology"
//! and iri=ontology_id so json2postgres can look it up when writing the
//! ontology header row.
//!
//! Usage:
//!   descendants_centroid \
//!     --embedding-parquets <file…>  \
//!     --ontology-jsons <file…> \
//!     --out-dir <dir>
//!
//! Output: one  `{model}_descendants_centroid.parquet`  per model, written to
//! `--out-dir`.  Schema: pk, ontology_id, entity_type, iri, label, embedding.

use std::collections::{HashMap, HashSet};
use std::fs::File;
use std::io::BufReader;
use std::path::Path;
use std::sync::Arc;

use arrow::array::{Array, FixedSizeListArray, Float32Array,
                   LargeStringArray, StringArray};
use arrow::datatypes::{DataType, Field, Schema};
use arrow::record_batch::RecordBatch;
use clap::Parser;
use parquet::arrow::arrow_reader::ParquetRecordBatchReaderBuilder;
use parquet::arrow::ArrowWriter;
use parquet::basic::{Compression, ZstdLevel};
use parquet::file::properties::WriterProperties;
use struson::reader::{JsonReader, JsonStreamReader, ValueType};

use ols_shared::streaming::skip_value;

// ── CLI ────────────────────────────────────────────────────────────────────

#[derive(Parser)]
#[command(about = "Compute average-of-descendants embeddings per term/ontology")]
struct Args {
    /// PCA embedding parquet files (output of the linker/join_embeddings step —
    /// multiple rows per term, one per label/synonym, with a `string_type` column).
    /// CURATION rows are filtered out and the remaining vectors are averaged per term.
    /// The model name is inferred from the filename stem.
    #[arg(long = "embedding-parquets", required = true, num_args = 1..)]
    embedding_parquets: Vec<String>,

    /// Linked ontology JSON files (output of the linker).
    #[arg(long = "ontology-jsons", required = true, num_args = 1..)]
    ontology_jsons: Vec<String>,

    /// Output directory for the *_descendants_centroid.parquet files.
    #[arg(long = "out-dir", required = true)]
    out_dir: String,
}

// ── Data types ─────────────────────────────────────────────────────────────

/// Metadata for a single term (loaded from avg parquet).
struct TermMeta {
    pk: String,
    ontology_id: String,
    entity_type: String,
    iri: String,
    label: String,
}

/// All data loaded from avg parquets for one model.
struct ModelData {
    /// Ordered list of term metadata (one per row in the avg parquet).
    terms: Vec<TermMeta>,
    /// key -> embedding vector; key = make_key(ontology_id, entity_type, iri)
    embeddings: HashMap<String, Vec<f32>>,
}

// ── Key helpers ────────────────────────────────────────────────────────────

#[inline]
fn make_key(ontology_id: &str, entity_type: &str, iri: &str) -> String {
    format!("{}|{}|{}", ontology_id, entity_type, iri)
}

/// Parse a key back into (ontology_id, entity_type, iri).
/// Splits on the first two '|' separators (IRIs containing '|' are preserved).
fn split_key(key: &str) -> Option<(&str, &str, &str)> {
    let p1 = key.find('|')?;
    let rest = &key[p1 + 1..];
    let p2 = rest.find('|')?;
    Some((&key[..p1], &rest[..p2], &rest[p2 + 1..]))
}

/// Build an index `(entity_type, iri) -> defining_key`.
/// Since `extract_strings_from_terms` only emits embeddings for
/// isDefiningOntology=true entities, every key in `all_embeddings` is already
/// a defining key.  This index lets us look up the defining key for an IRI
/// referenced from an importing ontology's JSON.
fn build_defining_key_index(
    all_embeddings: &HashMap<String, Vec<f32>>,
) -> HashMap<(String, String), String> {
    let mut idx: HashMap<(String, String), String> = HashMap::new();
    for key in all_embeddings.keys() {
        if let Some((_onto, et, iri)) = split_key(key) {
            idx.entry((et.to_string(), iri.to_string()))
                .or_insert_with(|| key.clone());
        }
    }
    idx
}

// ── Parquet loading ────────────────────────────────────────────────────────

/// Read a string column value by row index, supporting both StringArray and LargeStringArray.
fn read_string(col: &dyn Array, i: usize) -> Result<&str, Box<dyn std::error::Error>> {
    if let Some(arr) = col.as_any().downcast_ref::<StringArray>() {
        Ok(arr.value(i))
    } else if let Some(arr) = col.as_any().downcast_ref::<LargeStringArray>() {
        Ok(arr.value(i))
    } else {
        Err("not a string array".into())
    }
}

/// Load raw PCA parquets (multi-row per term, `string_type` column present).
/// Groups by `pk`, skips CURATION rows, and element-wise-averages the vectors.
/// Returns a map of `model_name -> ModelData`.  Model name is inferred from
/// the filename stem.
fn load_embedding_parquets(paths: &[String]) -> Result<HashMap<String, ModelData>, Box<dyn std::error::Error>> {
    let mut models: HashMap<String, ModelData> = HashMap::new();

    for path in paths {
        let model_name = infer_model_name(path);
        eprintln!("Loading PCA parquet for model '{}': {}", model_name, path);

        let file = File::open(path)?;
        let builder = ParquetRecordBatchReaderBuilder::try_new(file)?;
        let reader  = builder.build()?;

        let model = models.entry(model_name).or_insert_with(|| ModelData {
            terms: Vec::new(),
            embeddings: HashMap::new(),
        });

        struct TermAccum {
            meta:  TermMeta,
            sum:   Vec<f32>,
            count: usize,
        }
        let mut accum: HashMap<String, TermAccum> = HashMap::new();

        for batch_result in reader {
            let batch = batch_result?;
            let n = batch.num_rows();

            let pk_col          = batch.column_by_name("pk").ok_or("Missing 'pk'")?;
            let ontology_id_col = batch.column_by_name("ontology_id").ok_or("Missing 'ontology_id'")?;
            let entity_type_col = batch.column_by_name("entity_type").ok_or("Missing 'entity_type'")?;
            let iri_col         = batch.column_by_name("iri").ok_or("Missing 'iri'")?;
            let label_col       = batch.column_by_name("label").ok_or("Missing 'label'")?;
            let string_type_col = batch.column_by_name("string_type").ok_or("Missing 'string_type'")?;
            let embedding_col   = batch.column_by_name("embedding").ok_or("Missing 'embedding'")?;

            let list = embedding_col.as_any().downcast_ref::<FixedSizeListArray>()
                .ok_or("'embedding' is not FixedSizeListArray")?;

            for i in 0..n {
                if read_string(string_type_col.as_ref(), i)? == "CURATION" {
                    continue;
                }

                let pk = read_string(pk_col.as_ref(), i)?.to_string();

                let values_arr = list.value(i);
                let floats = values_arr.as_any().downcast_ref::<Float32Array>()
                    .ok_or("embedding elements are not Float32")?;
                let vector: Vec<f32> = (0..floats.len()).map(|j| floats.value(j)).collect();

                if let Some(entry) = accum.get_mut(&pk) {
                    for (s, v) in entry.sum.iter_mut().zip(vector.iter()) {
                        *s += v;
                    }
                    entry.count += 1;
                } else {
                    let ontology_id = read_string(ontology_id_col.as_ref(), i)?.to_string();
                    let entity_type = read_string(entity_type_col.as_ref(), i)?.to_string();
                    let iri         = read_string(iri_col.as_ref(), i)?.to_string();
                    let label = if label_col.is_null(i) {
                        String::new()
                    } else {
                        read_string(label_col.as_ref(), i)?.to_string()
                    };
                    accum.insert(pk.clone(), TermAccum {
                        meta: TermMeta { pk, ontology_id, entity_type, iri, label },
                        sum: vector,
                        count: 1,
                    });
                }
            }
        }

        for (_pk, entry) in accum {
            let mean: Vec<f32> = entry.sum.iter().map(|v| v / entry.count as f32).collect();
            let key = make_key(&entry.meta.ontology_id, &entry.meta.entity_type, &entry.meta.iri);
            model.embeddings.insert(key, mean);
            model.terms.push(entry.meta);
        }

        eprintln!("  -> loaded {} terms", model.terms.len());
    }

    Ok(models)
}

/// Derive a model name from a parquet file path.
/// Use the file stem as-is so the output column name
/// `descendants_centroid_{stem}` stays in sync with the regular
/// `embeddings_{stem}` column produced from the same parquet file.
fn infer_model_name(path: &str) -> String {
    Path::new(path)
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or(path)
        .to_string()
}

// ── JSON streaming: build child map ───────────────────────────────────────

/// For each term present in the embeddings, read its directParent IRIs from
/// the linked ontology JSONs and build a parent→children map.
///
/// Returns `child_map: HashMap<parent_key, Vec<child_key>>`.
/// Also populates `ontology_terms: HashMap<ontology_id, Vec<key>>` with all
/// keys that exist in the embeddings.
fn build_child_map(
    ontology_jsons: &[String],
    all_embeddings: &HashMap<String, Vec<f32>>,  // key -> vector (union across all models)
) -> Result<(HashMap<String, Vec<String>>, HashMap<String, Vec<String>>), Box<dyn std::error::Error>> {
    let mut child_map: HashMap<String, Vec<String>> = HashMap::new();
    let mut ontology_terms: HashMap<String, Vec<String>> = HashMap::new();

    // Index of (entity_type, iri) -> defining_key.  Used to resolve imported
    // entities (referenced in another ontology's JSON) back to the defining
    // ontology's embedding key, since only defining entities have embeddings.
    let defining_key_index = build_defining_key_index(all_embeddings);
    eprintln!("Defining-key index: {} unique (entity_type, iri) entries", defining_key_index.len());

    for json_path in ontology_jsons {
        eprintln!("Streaming ontology JSON for hierarchy: {}", json_path);

        let file = File::open(json_path)?;
        let reader = BufReader::new(file);
        let mut json = JsonStreamReader::new(reader);

        // { "ontologies": [ { ... }, ... ] }
        json.begin_object()?;
        while json.has_next()? {
            let top_key = json.next_name_owned()?;
            if top_key != "ontologies" {
                skip_value(&mut json);
                continue;
            }
            json.begin_array()?;
            while json.has_next()? {
                process_ontology_object(
                    &mut json,
                    all_embeddings,
                    &defining_key_index,
                    &mut child_map,
                    &mut ontology_terms,
                )?;
            }
            json.end_array()?;
        }
        json.end_object()?;
    }

    eprintln!(
        "child_map has {} parent entries; ontology_terms has {} ontologies",
        child_map.len(),
        ontology_terms.len()
    );

    Ok((child_map, ontology_terms))
}

/// Process a single ontology object from the JSON stream.
fn process_ontology_object(
    json: &mut JsonStreamReader<BufReader<File>>,
    all_embeddings: &HashMap<String, Vec<f32>>,
    defining_key_index: &HashMap<(String, String), String>,
    child_map: &mut HashMap<String, Vec<String>>,
    ontology_terms: &mut HashMap<String, Vec<String>>,
) -> Result<(), Box<dyn std::error::Error>> {
    json.begin_object()?;

    let mut ontology_id = String::new();
    // We first scan for ontologyId to get the id, then process entity sections.
    // Because struson is forward-only, we do one pass and handle sections as we encounter them.
    let mut deferred_entities: Vec<(String, Vec<(String, Vec<String>)>)> = Vec::new();

    while json.has_next()? {
        let key = json.next_name_owned()?;
        match key.as_str() {
            "ontologyId" => {
                ontology_id = json.next_string()?.to_string();
            }
            "classes" | "properties" | "individuals" => {
                let entity_type = match key.as_str() {
                    "classes" => "class",
                    "properties" => "property",
                    "individuals" => "individual",
                    _ => unreachable!(),
                };
                let entities = read_entities(json, entity_type)?;
                deferred_entities.push((entity_type.to_string(), entities));
            }
            _ => {
                skip_value(json);
            }
        }
    }

    json.end_object()?;

    // Now process the collected entities
    for (entity_type, entities) in deferred_entities {
        for (iri, parent_iris) in entities {
            // Resolve the child to its defining-ontology embedding key.
            // If this ontology is the defining one for `iri`, the key will be
            // (ontology_id, entity_type, iri) directly.  Otherwise (imported
            // entity), fall back to the defining ontology's key so imported
            // classes still contribute to their parent's descendants.
            let local_key = make_key(&ontology_id, &entity_type, &iri);
            let is_defining = all_embeddings.contains_key(&local_key);
            let child_key = if is_defining {
                local_key.clone()
            } else if let Some(k) = defining_key_index.get(&(entity_type.clone(), iri.clone())) {
                k.clone()
            } else {
                continue;
            };

            // Track this term under its ontology for the ontology-level centroid
            // ONLY if this ontology is the defining one (i.e. isDefiningOntology=true).
            // Imported entities should not contribute to an importing ontology's centroid,
            // but they DO still contribute as descendants in child_map below.
            if is_defining {
                ontology_terms
                    .entry(ontology_id.clone())
                    .or_default()
                    .push(child_key.clone());
            }

            // Build parent -> [child] edges
            for parent_iri in parent_iris {
                // Try this ontology first (covers native parents); fall back to
                // the defining ontology of the parent IRI.
                let parent_key = find_parent_key(&ontology_id, &parent_iri, all_embeddings)
                    .or_else(|| {
                        for et in &["class", "property", "individual"] {
                            if let Some(k) = defining_key_index.get(&((*et).to_string(), parent_iri.clone())) {
                                return Some(k.clone());
                            }
                        }
                        None
                    });
                if let Some(pk) = parent_key {
                    let children = child_map.entry(pk).or_default();
                    if !children.contains(&child_key) {
                        children.push(child_key.clone());
                    }
                }
            }
        }
    }

    Ok(())
}

/// Read all entities of a given type from the JSON array, returning
/// `Vec<(iri, directParent_iris)>`.
fn read_entities(
    json: &mut JsonStreamReader<BufReader<File>>,
    _entity_type: &str,
) -> Result<Vec<(String, Vec<String>)>, Box<dyn std::error::Error>> {
    let mut result = Vec::new();

    json.begin_array()?;
    while json.has_next()? {
        let entity = read_entity_minimal(json)?;
        result.push(entity);
    }
    json.end_array()?;

    Ok(result)
}

/// Stream a single entity object, extracting `iri` and `directParent`.
fn read_entity_minimal(
    json: &mut JsonStreamReader<BufReader<File>>,
) -> Result<(String, Vec<String>), Box<dyn std::error::Error>> {
    let mut iri = String::new();
    let mut direct_parents: Vec<String> = Vec::new();

    json.begin_object()?;
    while json.has_next()? {
        let key = json.next_name_owned()?;
        match key.as_str() {
            "iri" => {
                iri = json.next_string()?.to_string();
            }
            "directParent" => {
                direct_parents = read_iri_array(json)?;
            }
            _ => {
                skip_value(json);
            }
        }
    }
    json.end_object()?;

    Ok((iri, direct_parents))
}

/// Read an array of IRI values (either plain strings or `{"value": "..."}` objects).
fn read_iri_array(
    json: &mut JsonStreamReader<BufReader<File>>,
) -> Result<Vec<String>, Box<dyn std::error::Error>> {
    let mut result = Vec::new();
    if json.peek()? != ValueType::Array {
        skip_value(json);
        return Ok(result);
    }
    json.begin_array()?;
    while json.has_next()? {
        match json.peek()? {
            ValueType::String => {
                result.push(json.next_string()?.to_string());
            }
            ValueType::Object => {
                let mut value: Option<String> = None;
                json.begin_object()?;
                while json.has_next()? {
                    let k = json.next_name_owned()?;
                    if k == "value" {
                        value = Some(json.next_string()?.to_string());
                    } else {
                        skip_value(json);
                    }
                }
                json.end_object()?;
                if let Some(v) = value {
                    result.push(v);
                }
            }
            _ => {
                skip_value(json);
            }
        }
    }
    json.end_array()?;
    Ok(result)
}

/// Try to find the embedding key for a parent IRI.
/// Tries entity types in order: class, property, individual.
fn find_parent_key(
    ontology_id: &str,
    parent_iri: &str,
    all_embeddings: &HashMap<String, Vec<f32>>,
) -> Option<String> {
    for et in &["class", "property", "individual"] {
        let k = make_key(ontology_id, et, parent_iri);
        if all_embeddings.contains_key(&k) {
            return Some(k);
        }
    }
    None
}

// ── DFS ───────────────────────────────────────────────────────────────────

/// Compute average-of-descendants for every key that has descendants.
///
/// Returns `HashMap<key, Vec<f32>>`: the descendant-mean vector.
/// Leaf terms (no descendants with embeddings) are absent from the map.
fn compute_descendants(
    embeddings: &HashMap<String, Vec<f32>>,
    child_map: &HashMap<String, Vec<String>>,
) -> HashMap<String, Vec<f32>> {
    /// Recursive DFS.  Returns `(subtree_sum, subtree_count)` which includes
    /// the node's own embedding (if any) plus all descendants.
    fn dfs(
        key: &str,
        embeddings: &HashMap<String, Vec<f32>>,
        child_map: &HashMap<String, Vec<String>>,
        memo: &mut HashMap<String, Option<(Vec<f64>, usize)>>,
        in_progress: &mut HashSet<String>,
    ) -> Option<(Vec<f64>, usize)> {
        if let Some(cached) = memo.get(key) {
            return cached.clone();
        }
        if in_progress.contains(key) {
            // Cycle: return None so this edge is ignored
            return None;
        }
        in_progress.insert(key.to_string());

        let children = child_map.get(key).map(|v| v.as_slice()).unwrap_or(&[]);
        let mut acc_sum: Option<Vec<f64>> = None;
        let mut acc_count: usize = 0;

        for child_key in children {
            if let Some((child_sum, child_count)) = dfs(child_key, embeddings, child_map, memo, in_progress) {
                acc_count += child_count;
                match acc_sum {
                    None => acc_sum = Some(child_sum),
                    Some(ref mut s) => {
                        for (a, b) in s.iter_mut().zip(child_sum.iter()) {
                            *a += b;
                        }
                    }
                }
            }
        }

        // Add own embedding
        if let Some(own) = embeddings.get(key) {
            acc_count += 1;
            match acc_sum {
                None => acc_sum = Some(own.iter().map(|&v| v as f64).collect()),
                Some(ref mut s) => {
                    for (a, b) in s.iter_mut().zip(own.iter()) {
                        *a += *b as f64;
                    }
                }
            }
        }

        in_progress.remove(key);
        let result = acc_sum.map(|s| (s, acc_count));
        memo.insert(key.to_string(), result.clone());
        result
    }

    let mut memo: HashMap<String, Option<(Vec<f64>, usize)>> = HashMap::new();
    let mut in_progress: HashSet<String> = HashSet::new();
    let mut output: HashMap<String, Vec<f32>> = HashMap::new();

    // Visit all keys so even roots without parents in child_map get visited
    let all_keys: Vec<String> = embeddings.keys().cloned().collect();

    for key in &all_keys {
        if memo.contains_key(key) {
            continue;
        }
        let result = dfs(key, embeddings, child_map, &mut memo, &mut in_progress);
        // result is stored in memo; we process the output below
        drop(result);
    }

    // Build output: for each key, subtract own from subtree to get descendant mean
    for key in &all_keys {
        let subtree = match memo.get(key) {
            Some(Some(v)) => v,
            _ => continue,
        };
        let (subtree_sum, subtree_count) = subtree;
        let own = embeddings.get(key);
        let own_count = if own.is_some() { 1usize } else { 0 };
        let desc_count = subtree_count - own_count;
        if desc_count == 0 {
            // Leaf or no descendants with embeddings
            continue;
        }

        // descendant_sum = subtree_sum - own (if present)
        let mut desc_sum = subtree_sum.clone();
        if let Some(own_vec) = own {
            for (a, b) in desc_sum.iter_mut().zip(own_vec.iter()) {
                *a -= *b as f64;
            }
        }

        // mean = desc_sum / desc_count
        let mean: Vec<f32> = desc_sum.iter().map(|v| (v / desc_count as f64) as f32).collect();
        output.insert(key.clone(), mean);
    }

    output
}

// ── Parquet writing ────────────────────────────────────────────────────────

/// Write one `{model}_descendants_centroid.parquet` file.
fn write_output_parquet(
    model_name: &str,
    out_dir: &str,
    terms: &[TermMeta],
    descendants: &HashMap<String, Vec<f32>>,
    ontology_terms: &HashMap<String, Vec<String>>,
    embeddings: &HashMap<String, Vec<f32>>,
) -> Result<(), Box<dyn std::error::Error>> {
    // Determine embedding dimension from any entry in descendants or ontology avgs
    let dim = descendants.values()
        .next()
        .or_else(|| {
            ontology_terms.values()
                .flat_map(|keys| keys.iter())
                .find_map(|k| embeddings.get(k))
        })
        .map(|v| v.len())
        .unwrap_or(0);

    if dim == 0 {
        eprintln!("Model '{}': no output rows, skipping parquet write", model_name);
        return Ok(());
    }

    // Collect rows: per-term descendants_centroid rows
    let mut out_pk: Vec<String> = Vec::new();
    let mut out_ontology_id: Vec<String> = Vec::new();
    let mut out_entity_type: Vec<String> = Vec::new();
    let mut out_iri: Vec<String> = Vec::new();
    let mut out_label: Vec<String> = Vec::new();
    let mut out_embedding: Vec<f32> = Vec::new();  // flat, len = rows * dim

    for term in terms {
        let key = make_key(&term.ontology_id, &term.entity_type, &term.iri);
        if let Some(vec) = descendants.get(&key) {
            out_pk.push(term.pk.clone());
            out_ontology_id.push(term.ontology_id.clone());
            out_entity_type.push(term.entity_type.clone());
            out_iri.push(term.iri.clone());
            out_label.push(term.label.clone());
            out_embedding.extend_from_slice(vec);
        }
    }

    // Per-ontology rows: one per ontology_id
    // key = make_key(ontology_id, "ontology", ontology_id)
    let mut ontology_ids_sorted: Vec<&str> = ontology_terms.keys().map(|s| s.as_str()).collect();
    ontology_ids_sorted.sort();

    for onto_id in ontology_ids_sorted {
        let keys = &ontology_terms[onto_id];
        let vecs: Vec<&Vec<f32>> = keys.iter()
            .filter_map(|k| embeddings.get(k))
            .collect();
        if vecs.is_empty() {
            continue;
        }
        let mean = mean_vectors(&vecs);
        let pk = format!("{}+ontology+{}", onto_id, onto_id);
        out_pk.push(pk.clone());
        out_ontology_id.push(onto_id.to_string());
        out_entity_type.push("ontology".to_string());
        out_iri.push(onto_id.to_string());
        out_label.push(onto_id.to_string());
        out_embedding.extend_from_slice(&mean);
    }

    let n_rows = out_pk.len();
    if n_rows == 0 {
        eprintln!("Model '{}': no output rows, skipping parquet write", model_name);
        return Ok(());
    }

    eprintln!("Model '{}': writing {} rows (dim={})", model_name, n_rows, dim);

    // Build Arrow arrays
    let pk_array = Arc::new(StringArray::from(out_pk)) as Arc<dyn Array>;
    let ontology_id_array = Arc::new(StringArray::from(out_ontology_id)) as Arc<dyn Array>;
    let entity_type_array = Arc::new(StringArray::from(out_entity_type)) as Arc<dyn Array>;
    let iri_array = Arc::new(StringArray::from(out_iri)) as Arc<dyn Array>;
    let label_array = Arc::new(StringArray::from(out_label)) as Arc<dyn Array>;

    // FixedSizeList embedding
    let values_arr = Arc::new(Float32Array::from(out_embedding));
    let embedding_array = Arc::new(FixedSizeListArray::new(
        Arc::new(Field::new("item", DataType::Float32, false)),
        dim as i32,
        values_arr,
        None,
    )) as Arc<dyn Array>;

    // Schema
    let schema = Arc::new(Schema::new(vec![
        Field::new("pk", DataType::Utf8, false),
        Field::new("ontology_id", DataType::Utf8, false),
        Field::new("entity_type", DataType::Utf8, false),
        Field::new("iri", DataType::Utf8, false),
        Field::new("label", DataType::Utf8, false),
        Field::new(
            "embedding",
            DataType::FixedSizeList(
                Arc::new(Field::new("item", DataType::Float32, false)),
                dim as i32,
            ),
            false,
        ),
    ]));

    let batch = RecordBatch::try_new(
        schema.clone(),
        vec![pk_array, ontology_id_array, entity_type_array, iri_array, label_array, embedding_array],
    )?;

    let out_path = format!("{}/{}_descendants_centroid.parquet", out_dir, model_name);
    let file = File::create(&out_path)?;

    let props = WriterProperties::builder()
        .set_compression(Compression::ZSTD(ZstdLevel::try_new(3)?))
        .build();

    let mut writer = ArrowWriter::try_new(file, schema, Some(props))?;
    writer.write(&batch)?;
    writer.close()?;

    eprintln!("Wrote {}", out_path);
    Ok(())
}

// ── Mean vector helper ─────────────────────────────────────────────────────

fn mean_vectors(vecs: &[&Vec<f32>]) -> Vec<f32> {
    if vecs.is_empty() {
        return Vec::new();
    }
    let dim = vecs[0].len();
    let n = vecs.len() as f32;
    let mut result = vec![0.0f32; dim];
    for v in vecs {
        for (i, &val) in v.iter().enumerate() {
            result[i] += val;
        }
    }
    for val in &mut result {
        *val /= n;
    }
    result
}

// ── Main ───────────────────────────────────────────────────────────────────

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args = Args::parse();

    // 1. Load embedding parquets (raw PCA or pre-averaged)
    let models = load_embedding_parquets(&args.embedding_parquets)?;

    // 2. Build a union embedding index (all models) for child_map construction.
    //    We only need presence, not values, but we reuse the same HashMap<key, Vec<f32>>
    //    from one model (the first) since presence is the same across models for the
    //    same set of terms.  If models disagree on which terms have embeddings,
    //    we take the union.
    let mut all_embeddings: HashMap<String, Vec<f32>> = HashMap::new();
    for model_data in models.values() {
        for (k, v) in &model_data.embeddings {
            all_embeddings.entry(k.clone()).or_insert_with(|| v.clone());
        }
    }

    // 3. Build child map from ontology JSONs
    let (child_map, ontology_terms) = build_child_map(&args.ontology_jsons, &all_embeddings)?;

    // 4. Compute descendants_centroid and write output per model
    std::fs::create_dir_all(&args.out_dir)?;

    for (model_name, model_data) in &models {
        eprintln!("Computing descendants centroid for model '{}'...", model_name);
        let centroid = compute_descendants(&model_data.embeddings, &child_map);
        eprintln!("  -> {} terms with at least one descendant", centroid.len());

        write_output_parquet(
            model_name,
            &args.out_dir,
            &model_data.terms,
            &centroid,
            &ontology_terms,
            &model_data.embeddings,
        )?;
    }

    Ok(())
}
