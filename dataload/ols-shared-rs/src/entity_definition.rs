//! Entity definition type shared between linker tools

use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::BTreeSet;

/// Entity definition within an ontology
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct EntityDefinition {
    pub ontology_id: String,
    pub entity_types: BTreeSet<String>,
    #[serde(default)]
    pub is_defining_ontology: bool,
    #[serde(default)]
    pub label: Option<Value>,
    #[serde(default)]
    pub curie: Option<Value>,
    #[serde(default)]
    pub is_obsolete: bool,
}

impl PartialOrd for EntityDefinition {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for EntityDefinition {
    fn cmp(&self, other: &Self) -> std::cmp::Ordering {
        match self.ontology_id.cmp(&other.ontology_id) {
            std::cmp::Ordering::Equal => {
                let self_types: Vec<_> = self.entity_types.iter().collect();
                let other_types: Vec<_> = other.entity_types.iter().collect();
                self_types.cmp(&other_types)
            }
            other => other,
        }
    }
}
