use regex::Regex;
use serde::Deserialize;
use std::collections::HashMap;
use std::sync::LazyLock;

/// The Bioregistry (https://bioregistry.io) is an open source, community curated registry
/// of prefixes for biomedical ontologies/vocabularies and their associated metadata.
/// It can be used to look generate links for prefixes found in xrefs and other components of
/// ontologies.
///
/// Source code and data is available under CC0/MIT licenses at https://github.com/biopragmatics/bioregistry

const DEFAULT_REGISTRY_URL: &str = "https://raw.githubusercontent.com/biopragmatics/bioregistry/main/exports/registry/registry.json";

#[derive(Debug, Deserialize)]
struct RegistryEntry {
    #[serde(default)]
    preferred_prefix: Option<String>,
    #[serde(default)]
    synonyms: Option<Vec<String>>,
    #[serde(default)]
    uri_format: Option<String>,
    #[serde(default)]
    pattern: Option<String>,
}

pub struct Bioregistry {
    registry_url: String,
    prefix_to_database: HashMap<String, DatabaseEntry>,
    /// Maps URI prefix -> canonical database ID (sorted by length descending for longest match first)
    iri_prefix_to_database: Vec<(String, String)>,
    patterns: HashMap<String, Regex>,
}

#[derive(Clone)]
struct DatabaseEntry {
    uri_format: Option<String>,
    pattern: Option<String>,
}

impl Bioregistry {
    pub fn new() -> Result<Self, Box<dyn std::error::Error>> {
        Self::with_url(DEFAULT_REGISTRY_URL)
    }

    pub fn with_url(json_url: &str) -> Result<Self, Box<dyn std::error::Error>> {
        let response = ureq::get(json_url).call()?;
        let registry: HashMap<String, RegistryEntry> = response.into_json()?;
        
        let mut prefix_to_database: HashMap<String, DatabaseEntry> = HashMap::new();
        // Use a HashMap to deduplicate - later entries with same prefix will overwrite earlier ones
        let mut iri_prefix_map: HashMap<String, String> = HashMap::new();
        
        // Sort registry keys to get consistent order (like Java's iteration over JSON object)
        let mut registry_keys: Vec<_> = registry.keys().collect();
        registry_keys.sort();
        
        for key in registry_keys {
            let entry = &registry[key];
            let db_entry = DatabaseEntry {
                uri_format: entry.uri_format.clone(),
                pattern: entry.pattern.clone(),
            };
            
            // The key is the canonical Bioregistry prefix, always lowercase
            prefix_to_database.insert(norm(key), db_entry);
            
            // The preferred prefix can have various capitalization
            if let Some(ref preferred) = entry.preferred_prefix {
                let db_entry = DatabaseEntry {
                    uri_format: entry.uri_format.clone(),
                    pattern: entry.pattern.clone(),
                };
                prefix_to_database.insert(norm(preferred), db_entry);
            }
            
            // Handle synonyms
            if let Some(ref synonyms) = entry.synonyms {
                for synonym in synonyms {
                    let db_entry = DatabaseEntry {
                        uri_format: entry.uri_format.clone(),
                        pattern: entry.pattern.clone(),
                    };
                    prefix_to_database.insert(norm(synonym), db_entry);
                }
            }
            
            // Build IRI prefix mapping - later entries with same uri_prefix will overwrite
            if let Some(ref uri_format) = entry.uri_format {
                if uri_format.ends_with("$1") {
                    let uri_prefix = &uri_format[..uri_format.len() - 2];
                    iri_prefix_map.insert(uri_prefix.to_string(), key.to_string());
                }
            }
        }
        
        // Convert to Vec and sort by length descending for longest match first
        let mut iri_prefix_to_database: Vec<(String, String)> = iri_prefix_map.into_iter().collect();
        iri_prefix_to_database.sort_by(|a, b| {
            b.0.len().cmp(&a.0.len()).then_with(|| a.0.cmp(&b.0))
        });
        
        Ok(Self {
            registry_url: json_url.to_string(),
            prefix_to_database,
            iri_prefix_to_database,
            patterns: HashMap::new(),
        })
    }

    pub fn get_registry_url(&self) -> &str {
        &self.registry_url
    }

    pub fn get_url_for_id(&mut self, database_id: &str, id: &str) -> Option<String> {
        // Clone the database entry to avoid borrow conflicts
        let db = self.prefix_to_database.get(&norm(database_id))?.clone();
        
        // Java returns null if there's no pattern - we need to match this behavior
        let pattern_str = db.pattern.as_ref()?;
        
        let pattern = self.get_pattern(pattern_str);
        // We need to check if the pattern matches the ENTIRE string (like Java's matches())
        // Using find() and checking that it covers the whole string
        if let Some(m) = pattern.find(id) {
            if m.start() != 0 || m.end() != id.len() {
                return None;
            }
        } else {
            return None;
        }
        
        let uri_format = db.uri_format.as_ref()?;
        Some(uri_format.replace("$1", id))
    }

    pub fn get_curie_for_url(&self, url: &str) -> Option<String> {
        for (prefix, database_id) in &self.iri_prefix_to_database {
            if url.starts_with(prefix) {
                let local_unique_identifier = &url[prefix.len()..];
                return Some(format!("{}:{}", database_id, local_unique_identifier));
            }
        }
        None
    }

    fn get_pattern(&mut self, pattern_str: &str) -> &Regex {
        if !self.patterns.contains_key(pattern_str) {
            let pattern = Regex::new(pattern_str).unwrap_or_else(|_| Regex::new(".*").unwrap());
            self.patterns.insert(pattern_str.to_string(), pattern);
        }
        self.patterns.get(pattern_str).unwrap()
    }
}

/// Normalize a prefix string according to bioregistry conventions
fn norm(s: &str) -> String {
    s.to_lowercase()
        .replace('.', "")
        .replace('-', "")
        .replace('_', "")
        .replace('/', "")
}

// Check if a string looks like a CURIE
// Note: [A-z] matches A-Z, [, \, ], ^, _, `, a-z (ASCII 65-122)
// This matches Java's behavior exactly
static CURIE_PATTERN: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"^[A-z0-9]+:[A-z0-9-]+$").unwrap()
});

pub fn is_curie(s: &str) -> bool {
    CURIE_PATTERN.is_match(s)
}
