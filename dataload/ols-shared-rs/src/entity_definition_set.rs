//! Entity definition set type shared between linker tools

use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, BTreeSet};

use crate::EntityDefinition;

/// Set of all definitions for a single IRI across all ontologies
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct EntityDefinitionSet {
    #[serde(default)]
    pub definitions: BTreeSet<EntityDefinition>,
    #[serde(default)]
    pub defining_definitions: BTreeSet<EntityDefinition>,
    #[serde(default)]
    pub defining_ontology_ids: BTreeSet<String>,
    #[serde(default)]
    pub ontology_id_to_definitions: BTreeMap<String, EntityDefinition>,
}
