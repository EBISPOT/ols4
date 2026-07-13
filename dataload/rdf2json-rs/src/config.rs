//! Config loading and merging, ported from `RDF2JSON.main`.
//!
//! A config is a JSON document `{ "ontologies": [ { ...per-ontology... } ] }`.
//! Multiple config files are merged by lowercased ontology `id`; later configs
//! override/extend earlier ones while preserving original key insertion order
//! (Gson uses `LinkedHashMap`, so we rely on `serde_json`'s `preserve_order`).

use serde_json::{Map, Value};
use std::io::Read;

use crate::error::Rdf2JsonError;

/// A single ontology's config object (insertion-ordered).
pub type OntologyConfig = Map<String, Value>;

/// Load all config files and merge them, returning ontology configs in
/// insertion order (the values of the merged `LinkedHashMap` in Java).
pub fn load_and_merge_configs(paths: &[String]) -> Result<Vec<OntologyConfig>, Rdf2JsonError> {
    let mut all: Vec<OntologyConfig> = Vec::new();

    for path in paths {
        if path.ends_with(".json") {
            let bytes = read_config_bytes(path)?;
            let parsed: Value = serde_json::from_slice(&bytes)
                .map_err(|e| Rdf2JsonError::Config(format!("Error parsing config {path}: {e}")))?;
            let ontologies = parsed
                .get("ontologies")
                .and_then(|v| v.as_array())
                .ok_or_else(|| {
                    Rdf2JsonError::Config(format!("Config {path} has no 'ontologies' array"))
                })?;
            for onto in ontologies {
                if let Value::Object(map) = onto {
                    all.push(map.clone());
                }
            }
        } else {
            // An OWL file given directly: fabricate a minimal config for it.
            // id = filename without extension (first '.' after the last '/').
            let last_slash = path.rfind('/').map(|i| i + 1).unwrap_or(0);
            let dot = path[last_slash..]
                .find('.')
                .map(|i| last_slash + i)
                .unwrap_or(path.len());
            let ontology_id = path[last_slash..dot].to_string();
            let mut map = Map::new();
            map.insert("id".to_string(), Value::String(ontology_id));
            map.insert("ontology_purl".to_string(), Value::String(path.clone()));
            all.push(map);
        }
    }

    // Merge by lowercased id, preserving first-seen order, overriding values.
    let mut order: Vec<String> = Vec::new();
    let mut merged: std::collections::HashMap<String, OntologyConfig> =
        std::collections::HashMap::new();

    for config in all {
        let id = config
            .get("id")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_lowercase();
        if let Some(existing) = merged.get_mut(&id) {
            for (k, v) in config {
                existing.insert(k, v);
            }
        } else {
            order.push(id.clone());
            merged.insert(id, config);
        }
    }

    Ok(order.into_iter().map(|id| merged.remove(&id).unwrap()).collect())
}

fn read_config_bytes(path: &str) -> Result<Vec<u8>, Rdf2JsonError> {
    if path.contains("://") {
        let resp = ureq::get(path)
            .call()
            .map_err(|e| Rdf2JsonError::Config(format!("Error loading config {path}: {e}")))?;
        let mut buf = Vec::new();
        resp.into_reader()
            .read_to_end(&mut buf)
            .map_err(|e| Rdf2JsonError::Config(format!("Error reading config {path}: {e}")))?;
        Ok(buf)
    } else {
        std::fs::read(path)
            .map_err(|e| Rdf2JsonError::Config(format!("Error loading config file: {path}: {e}")))
    }
}

/// Helpers for reading typed values out of a config object.
pub trait ConfigExt {
    fn get_str(&self, key: &str) -> Option<&str>;
    fn get_str_array(&self, key: &str) -> Option<Vec<String>>;
    fn is_true(&self, key: &str) -> bool;
}

impl ConfigExt for OntologyConfig {
    fn get_str(&self, key: &str) -> Option<&str> {
        self.get(key).and_then(|v| v.as_str())
    }
    fn get_str_array(&self, key: &str) -> Option<Vec<String>> {
        match self.get(key) {
            Some(Value::Array(a)) => Some(
                a.iter()
                    .filter_map(|v| v.as_str().map(|s| s.to_string()))
                    .collect(),
            ),
            _ => None,
        }
    }
    fn is_true(&self, key: &str) -> bool {
        matches!(self.get(key), Some(Value::Bool(true)))
    }
}
