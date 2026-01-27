use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::{BTreeMap, BTreeSet, HashMap, HashSet};

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

impl LinkerPass1Result {
    pub fn new() -> Self {
        Self::default()
    }

    /// Merge another LinkerPass1Result into this one
    pub fn merge(&mut self, source: LinkerPass1Result) {
        // Merge iriToDefinitions
        for (iri, def_set) in source.iri_to_definitions {
            let target_def_set = self
                .iri_to_definitions
                .entry(iri)
                .or_insert_with(EntityDefinitionSet::default);

            target_def_set.definitions.extend(def_set.definitions);
            target_def_set
                .defining_definitions
                .extend(def_set.defining_definitions);
            target_def_set
                .defining_ontology_iris
                .extend(def_set.defining_ontology_iris);
            target_def_set
                .defining_ontology_ids
                .extend(def_set.defining_ontology_ids);
            target_def_set
                .ontology_id_to_definitions
                .extend(def_set.ontology_id_to_definitions);
        }

        // Merge ontologyIriToOntologyIds
        for (iri, ids) in source.ontology_iri_to_ontology_ids {
            self.ontology_iri_to_ontology_ids
                .entry(iri)
                .or_insert_with(BTreeSet::new)
                .extend(ids);
        }

        // Merge preferredPrefixToOntologyIds
        for (prefix, ids) in source.preferred_prefix_to_ontology_ids {
            self.preferred_prefix_to_ontology_ids
                .entry(prefix)
                .or_insert_with(BTreeSet::new)
                .extend(ids);
        }

        // Merge ontologyIdToBaseUris
        for (id, uris) in source.ontology_id_to_base_uris {
            self.ontology_id_to_base_uris
                .entry(id)
                .or_insert_with(BTreeSet::new)
                .extend(uris);
        }

        // Merge ontologyIdToImportingOntologyIds
        for (id, values) in source.ontology_id_to_importing_ontology_ids {
            self.ontology_id_to_importing_ontology_ids
                .entry(id)
                .or_insert_with(BTreeSet::new)
                .extend(values);
        }

        // Merge ontologyIdToImportedOntologyIds
        for (id, values) in source.ontology_id_to_imported_ontology_ids {
            self.ontology_id_to_imported_ontology_ids
                .entry(id)
                .or_insert_with(BTreeSet::new)
                .extend(values);
        }

        // Merge scanner results - property sets
        self.ontology_id_to_ontology_properties
            .extend(source.ontology_id_to_ontology_properties);
        self.ontology_id_to_class_properties
            .extend(source.ontology_id_to_class_properties);
        self.ontology_id_to_property_properties
            .extend(source.ontology_id_to_property_properties);
        self.ontology_id_to_individual_properties
            .extend(source.ontology_id_to_individual_properties);
        self.ontology_id_to_edge_properties
            .extend(source.ontology_id_to_edge_properties);

        // Merge scanner results - URI to types mapping
        self.ontology_id_to_uri_to_types
            .extend(source.ontology_id_to_uri_to_types);
    }
}

/// Node type for tracking what type(s) a URI represents
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub enum NodeType {
    Ontology,
    Class,
    Property,
    Individual,
}

impl NodeType {
    pub fn as_str(&self) -> &'static str {
        match self {
            NodeType::Ontology => "ONTOLOGY",
            NodeType::Class => "CLASS",
            NodeType::Property => "PROPERTY",
            NodeType::Individual => "INDIVIDUAL",
        }
    }
}

impl std::fmt::Display for NodeType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.as_str())
    }
}

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
