use std::collections::{HashMap, HashSet};
use serde::{Deserialize, Serialize};

/// Result from LinkerPass1 - matches the Java LinkerPass1Result class
#[derive(Debug, Clone, Deserialize, Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct LinkerPass1Result {
    /// entity IRI -> all definitions of that IRI from ontologies
    #[serde(default)]
    pub iri_to_definitions: HashMap<String, EntityDefinitionSet>,

    /// ontology IRI -> IDs for that ontology (usually only 1)
    #[serde(default)]
    pub ontology_iri_to_ontology_ids: HashMap<String, HashSet<String>>,

    /// preferred prefix -> ontology IDs with that prefix (usually only 1)
    #[serde(default)]
    pub preferred_prefix_to_ontology_ids: HashMap<String, HashSet<String>>,

    /// ontology id -> defined base URIs for that ontology
    #[serde(default)]
    pub ontology_id_to_base_uris: HashMap<String, HashSet<String>>,

    /// ontology id -> IDs of ontologies that import at least 1 term from the ontology
    #[serde(default)]
    pub ontology_id_to_importing_ontology_ids: HashMap<String, HashSet<String>>,

    /// ontology id -> IDs of ontologies it imports at least 1 term from
    #[serde(default)]
    pub ontology_id_to_imported_ontology_ids: HashMap<String, HashSet<String>>,

    /// ontology id -> set of properties found in ontology metadata
    #[serde(default)]
    pub ontology_id_to_ontology_properties: HashMap<String, HashSet<String>>,

    /// ontology id -> set of properties found in classes
    #[serde(default)]
    pub ontology_id_to_class_properties: HashMap<String, HashSet<String>>,

    /// ontology id -> set of properties found in properties
    #[serde(default)]
    pub ontology_id_to_property_properties: HashMap<String, HashSet<String>>,

    /// ontology id -> set of properties found in individuals
    #[serde(default)]
    pub ontology_id_to_individual_properties: HashMap<String, HashSet<String>>,

    /// ontology id -> set of properties found on edges
    #[serde(default)]
    pub ontology_id_to_edge_properties: HashMap<String, HashSet<String>>,

    /// ontology id -> URI -> set of node types for that URI in that ontology
    #[serde(default)]
    pub ontology_id_to_uri_to_types: HashMap<String, HashMap<String, HashSet<String>>>,
}

#[derive(Debug, Clone, Deserialize, Serialize, Default)]
pub struct EntityDefinitionSet {
    // Add fields as needed
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum NodeType {
    Ontology,
    Class,
    Property,
    Individual,
}

impl NodeType {
    pub fn to_string_lowercase(&self) -> &'static str {
        match self {
            NodeType::Ontology => "ontology",
            NodeType::Class => "class",
            NodeType::Property => "property",
            NodeType::Individual => "individual",
        }
    }
}

/// Information about an ontology extracted from the manifest.
#[derive(Debug, Clone)]
#[allow(dead_code)]
pub struct OntologyManifestInfo {
    pub ontology_id: String,
    pub ontology_uri: String,
    pub all_ontology_properties: HashSet<String>,
    pub all_class_properties: HashSet<String>,
    pub all_property_properties: HashSet<String>,
    pub all_individual_properties: HashSet<String>,
    pub all_edge_properties: HashSet<String>,
    pub uri_to_types: HashMap<String, HashSet<NodeType>>,
}
