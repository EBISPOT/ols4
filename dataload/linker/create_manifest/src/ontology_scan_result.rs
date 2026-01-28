use std::collections::{BTreeMap, BTreeSet};

use crate::node_type::NodeType;

/// Result from scanning a single ontology
#[derive(Debug, Clone, Default)]
pub struct OntologyScanResult {
    pub ontology_id: String,
    pub ontology_uri: String,
    pub all_ontology_properties: BTreeSet<String>,
    pub all_class_properties: BTreeSet<String>,
    pub all_property_properties: BTreeSet<String>,
    pub all_individual_properties: BTreeSet<String>,
    pub all_edge_properties: BTreeSet<String>,
    pub uri_to_types: BTreeMap<String, BTreeSet<NodeType>>,
}
