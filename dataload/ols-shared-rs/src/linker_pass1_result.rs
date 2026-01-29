//! LinkerPass1Result type shared between linker tools
//!
//! This matches the Java LinkerPass1Result class for JSON compatibility.

use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, BTreeSet};

use crate::EntityDefinitionSet;

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
