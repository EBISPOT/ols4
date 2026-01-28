use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::{BTreeMap, BTreeSet};

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

/// Set of all definitions for a single IRI across all ontologies
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct EntityDefinitionSet {
    #[serde(default)]
    pub definitions: BTreeSet<EntityDefinition>,
    #[serde(default)]
    pub defining_definitions: BTreeSet<EntityDefinition>,
    #[serde(default)]
    pub defining_ontology_iris: BTreeSet<String>,
    #[serde(default)]
    pub defining_ontology_ids: BTreeSet<String>,
    #[serde(default)]
    pub ontology_id_to_definitions: BTreeMap<String, EntityDefinition>,
}

/// Result from LinkerPass1 - matches the Java LinkerPass1Result class
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct LinkerPass1Result {
    /// entity IRI -> all definitions of that IRI from ontologies
    #[serde(default)]
    pub iri_to_definitions: BTreeMap<String, EntityDefinitionSet>,

    /// ontology IRI -> IDs for that ontology (usually only 1)
    #[serde(default)]
    pub ontology_iri_to_ontology_ids: BTreeMap<String, BTreeSet<String>>,

    /// preferred prefix -> ontology IDs with that prefix (usually only 1)
    #[serde(default)]
    pub preferred_prefix_to_ontology_ids: BTreeMap<String, BTreeSet<String>>,

    /// ontology id -> defined base URIs for that ontology
    #[serde(default)]
    pub ontology_id_to_base_uris: BTreeMap<String, BTreeSet<String>>,

    /// ontology id -> IDs of ontologies that import at least 1 term from the ontology
    #[serde(default)]
    pub ontology_id_to_importing_ontology_ids: BTreeMap<String, BTreeSet<String>>,

    /// ontology id -> IDs of ontologies it imports at least 1 term from
    #[serde(default)]
    pub ontology_id_to_imported_ontology_ids: BTreeMap<String, BTreeSet<String>>,

    /// ontology id -> set of properties found in ontology metadata
    #[serde(default)]
    pub ontology_id_to_ontology_properties: BTreeMap<String, BTreeSet<String>>,

    /// ontology id -> set of properties found in classes
    #[serde(default)]
    pub ontology_id_to_class_properties: BTreeMap<String, BTreeSet<String>>,

    /// ontology id -> set of properties found in properties
    #[serde(default)]
    pub ontology_id_to_property_properties: BTreeMap<String, BTreeSet<String>>,

    /// ontology id -> set of properties found in individuals
    #[serde(default)]
    pub ontology_id_to_individual_properties: BTreeMap<String, BTreeSet<String>>,

    /// ontology id -> set of properties found on edges
    #[serde(default)]
    pub ontology_id_to_edge_properties: BTreeMap<String, BTreeSet<String>>,

    /// ontology id -> URI -> set of node types for that URI in that ontology
    #[serde(default)]
    pub ontology_id_to_uri_to_types: BTreeMap<String, BTreeMap<String, BTreeSet<String>>>,
}

/// Result from mapping a CURIE to URL
pub struct CurieMapResult {
    pub url: String,
    pub source: String,
}
