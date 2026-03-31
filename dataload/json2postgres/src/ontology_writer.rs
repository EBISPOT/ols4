use std::collections::HashMap;
use std::fs::File;
use std::io::{BufWriter, Write};

use indexmap::IndexMap;
use serde_json::{Map, Value};

use ols_shared::Embeddings;
use crate::manifest::OntologyManifestInfo;

/// Edge blacklist - these shouldn't create edges in the edges table.
/// Note: directAncestor/hierarchicalAncestor are NOT blacklisted for edges because
/// we don't create edges from them. But they ARE kept as entity properties (IRI arrays)
/// so PostgreSQL can use them for hierarchy queries without recursive SQL.
const EDGE_BLACKLIST: &[&str] = &[
    "iri",                   // don't create lots of "iri" edges pointing from each node to itself
    "hierarchicalProperty",  // informational only
    "definitionProperty",    // informational only
    "synonymProperty",       // informational only
    "directAncestor",        // stored as entity column, not as edge
    "hierarchicalAncestor",  // stored as entity column, not as edge
    "relatedFrom",           // redundant - we already have relatedTo which can be queried both ways
];

pub struct OntologyWriter<'a> {
    #[allow(dead_code)]
    output_file_path: String,
    ontology_id: String,
    manifest_info: OntologyManifestInfo,
    embeddings: &'a HashMap<String, Embeddings>,
    embedding_model_names: Vec<String>,
    entities_writer: BufWriter<File>,
    edges_writer: BufWriter<File>,
    embedding_nodes_writer: Option<BufWriter<File>>,
}

/// Escape a string for PostgreSQL COPY TEXT format.
/// Backslash-escapes: tab → \t, newline → \n, carriage return → \r, backslash → \\
fn escape_tsv(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for c in s.chars() {
        match c {
            '\t' => out.push_str("\\t"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\\' => out.push_str("\\\\"),
            _ => out.push(c),
        }
    }
    out
}

/// Encode a list of strings as a PostgreSQL text array literal: {val1,val2,...}
/// Each element is double-quoted with internal quotes and backslashes escaped.
fn pg_text_array(values: &[String]) -> String {
    if values.is_empty() {
        return "{}".to_string();
    }
    let mut out = String::from("{");
    for (i, v) in values.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        out.push('"');
        for c in v.chars() {
            match c {
                '"' => out.push_str("\\\""),
                '\\' => out.push_str("\\\\"),
                _ => out.push(c),
            }
        }
        out.push('"');
    }
    out.push('}');
    out
}

/// Encode a float vector as a pgvector literal: [0.1,0.2,0.3]
fn pg_vector(values: &[f32]) -> String {
    let mut out = String::from("[");
    for (i, v) in values.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        out.push_str(&v.to_string());
    }
    out.push(']');
    out
}

/// Extract is_obsolete from entity JSON. Returns true if the entity is obsolete.
fn extract_is_obsolete(entity: &Map<String, Value>) -> bool {
    match entity.get("isObsolete") {
        Some(Value::Bool(b)) => *b,
        Some(Value::Array(arr)) => arr.iter().any(|v| v.as_str() == Some("true")),
        Some(Value::String(s)) => s == "true",
        _ => false,
    }
}

/// Extract label strings from entity JSON. Handles both plain strings and localized objects.
fn extract_label_strings(entity: &Map<String, Value>) -> Vec<String> {
    match entity.get("label") {
        Some(Value::Array(arr)) => arr.iter().filter_map(|v| {
            match v {
                Value::String(s) => Some(s.clone()),
                Value::Object(obj) => obj.get("value").and_then(|v| v.as_str()).map(String::from),
                _ => None,
            }
        }).collect(),
        Some(Value::String(s)) => vec![s.clone()],
        _ => vec![],
    }
}

/// Extract a string array property from entity JSON.
fn extract_string_array(entity: &Map<String, Value>, key: &str) -> Vec<String> {
    match entity.get(key) {
        Some(Value::Array(arr)) => arr.iter().filter_map(|v| v.as_str().map(String::from)).collect(),
        Some(Value::String(s)) => vec![s.clone()],
        _ => vec![],
    }
}

impl<'a> OntologyWriter<'a> {
    pub fn new(
        output_file_path: &str,
        manifest_info: OntologyManifestInfo,
        embeddings: &'a HashMap<String, Embeddings>,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        let ontology_id = manifest_info.ontology_id.clone();

        let mut embedding_model_names: Vec<String> = embeddings.keys().cloned().collect();
        embedding_model_names.sort();

        // Create entities TSV (no header - PostgreSQL COPY doesn't use headers)
        let entities_file = File::create(format!("{}/{}_entities.tsv", output_file_path, ontology_id))?;
        let entities_writer = BufWriter::new(entities_file);

        // Create edges TSV
        let edges_file = File::create(format!("{}/{}_edges.tsv", output_file_path, ontology_id))?;
        let edges_writer = BufWriter::new(edges_file);

        // Create embedding nodes TSV if we have embeddings
        let embedding_nodes_writer = if !embedding_model_names.is_empty() {
            let emb_file = File::create(format!("{}/{}_embedding_nodes.tsv", output_file_path, ontology_id))?;
            Some(BufWriter::new(emb_file))
        } else {
            None
        };

        Ok(Self {
            output_file_path: output_file_path.to_string(),
            ontology_id,
            manifest_info,
            embeddings,
            embedding_model_names,
            entities_writer,
            edges_writer,
            embedding_nodes_writer,
        })
    }

    /// Write a single entity row to the entities TSV.
    /// Columns: id, type, iri, ontology_id, _json, [embedding columns...]
    pub fn write_entity(
        &mut self,
        entity_type_plural: &str,
        entity: &Map<String, Value>,
        entity_value: &Value,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let (pg_type, entity_type_str) = match entity_type_plural {
            "classes" => ("OntologyClass", "class"),
            "properties" => ("OntologyProperty", "property"),
            "individuals" => ("OntologyIndividual", "individual"),
            _ => return Err(format!("Unknown entity type: {}", entity_type_plural).into()),
        };

        let iri = entity
            .get("iri")
            .and_then(|v| v.as_str())
            .unwrap_or("");

        let entity_node_id = format!("{}+{}+{}", self.ontology_id, entity_type_str, iri);
        let json_str = serde_json::to_string(entity_value)?;

        // Write entity row: id \t type \t iri \t ontology_id \t _json \t is_obsolete \t label \t direct_ancestors \t hierarchical_ancestors \t [embeddings...]
        write!(self.entities_writer, "{}\t{}\t{}\t{}\t{}\t{}\t{}\t{}\t{}",
            escape_tsv(&entity_node_id),
            escape_tsv(pg_type),
            escape_tsv(iri),
            escape_tsv(&self.ontology_id),
            escape_tsv(&json_str),
            if extract_is_obsolete(entity) { "t" } else { "f" },
            escape_tsv(&pg_text_array(&extract_label_strings(entity))),
            escape_tsv(&pg_text_array(&extract_string_array(entity, "directAncestor"))),
            escape_tsv(&pg_text_array(&extract_string_array(entity, "hierarchicalAncestor"))),
        )?;

        // Write one embedding column per model (average embedding on parent entity)
        for model_name in &self.embedding_model_names {
            let emb_str = self.serialize_average_embedding(entity, entity_type_str, iri, model_name);
            write!(self.entities_writer, "\t{}", emb_str)?;
        }

        writeln!(self.entities_writer)?;

        // Write edges from entity properties
        let entity_map: IndexMap<String, Value> = entity.iter()
            .map(|(k, v)| (k.clone(), v.clone()))
            .collect();
        for (property, value) in &entity_map {
            self.maybe_write_edges(iri, property, value)?;
        }

        // Write embedding child nodes
        self.write_embedding_child_nodes(&entity_node_id, entity, entity_type_str, iri)?;

        Ok(())
    }

    /// Write the ontology node as an entity row.
    pub fn write_ontology(&mut self, ontology_properties: &Map<String, Value>) -> Result<(), Box<dyn std::error::Error>> {
        let ontology_id = ontology_properties
            .get("ontologyId")
            .and_then(|v| v.as_str())
            .unwrap_or("");
        let iri = ontology_properties
            .get("iri")
            .and_then(|v| v.as_str())
            .unwrap_or("");

        let entity_node_id = format!("{}+ontology+{}", ontology_id, iri);
        let json_str = serde_json::to_string(&Value::Object(ontology_properties.clone()))?;

        // Write entity row: id \t type \t iri \t ontology_id \t _json \t is_obsolete \t label \t direct_ancestors \t hierarchical_ancestors \t [embeddings (empty)...]
        write!(self.entities_writer, "{}\t{}\t{}\t{}\t{}\t{}\t{}\t{}\t{}",
            escape_tsv(&entity_node_id),
            "Ontology",
            escape_tsv(iri),
            escape_tsv(ontology_id),
            escape_tsv(&json_str),
            "f",
            escape_tsv(&pg_text_array(&extract_label_strings(ontology_properties))),
            "{}",
            "{}",
        )?;

        // Ontology nodes don't have embeddings
        for _ in &self.embedding_model_names {
            write!(self.entities_writer, "\t\\N")?;
        }

        writeln!(self.entities_writer)?;

        Ok(())
    }

    /// Flush all writers.
    pub fn finish(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        self.entities_writer.flush()?;
        self.edges_writer.flush()?;
        if let Some(ref mut w) = self.embedding_nodes_writer {
            w.flush()?;
        }
        Ok(())
    }

    fn maybe_write_edges(&mut self, subject: &str, property: &str, value: &Value) -> Result<(), Box<dyn std::error::Error>> {
        let values: Vec<&Value> = if value.is_array() {
            value.as_array().unwrap().iter().collect()
        } else {
            vec![value]
        };

        for v in values {
            if let Some(map_value) = v.as_object() {
                if let Some(type_val) = map_value.get("type") {
                    if let Some(types) = type_val.as_array() {
                        let type_strs: Vec<&str> = types.iter().filter_map(|t| t.as_str()).collect();

                        if type_strs.contains(&"reification") {
                            if let Some(reified_value) = map_value.get("value").and_then(|v| v.as_str()) {
                                if let Some(axioms) = map_value.get("axioms").and_then(|a| a.as_array()) {
                                    if self.manifest_info.uri_to_types.contains_key(reified_value) {
                                        for axiom in axioms {
                                            if let Some(axiom_obj) = axiom.as_object() {
                                                let axiom_map: IndexMap<String, Value> = axiom_obj.iter()
                                                    .map(|(k, v)| (k.clone(), v.clone()))
                                                    .collect();
                                                self.print_edge(subject, property, reified_value, &axiom_map)?;
                                            }
                                        }
                                    }
                                }
                            }
                        } else if type_strs.contains(&"related") {
                            if let Some(related_value) = map_value.get("value").and_then(|v| v.as_str()) {
                                if self.manifest_info.uri_to_types.contains_key(related_value) {
                                    let edge_props: IndexMap<String, Value> = map_value.iter()
                                        .map(|(k, v)| (k.clone(), v.clone()))
                                        .collect();
                                    self.print_edge(subject, property, related_value, &edge_props)?;
                                }
                            }
                        }
                    }
                }
            } else if let Some(uri) = v.as_str() {
                if self.manifest_info.uri_to_types.contains_key(uri) {
                    self.print_edge(subject, property, uri, &IndexMap::new())?;
                }
            }
        }

        Ok(())
    }

    fn print_edge(
        &mut self,
        a_uri: &str,
        predicate: &str,
        b_uri: &str,
        edge_props: &IndexMap<String, Value>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        if EDGE_BLACKLIST.contains(&predicate) {
            return Ok(());
        }

        let a_types = self.manifest_info.uri_to_types.get(a_uri);
        let b_types = self.manifest_info.uri_to_types.get(b_uri);

        if let (Some(a_types), Some(b_types)) = (a_types, b_types) {
            let mut a_types_sorted: Vec<_> = a_types.iter().collect();
            let mut b_types_sorted: Vec<_> = b_types.iter().collect();
            a_types_sorted.sort_by_key(|t| t.to_string_lowercase());
            b_types_sorted.sort_by_key(|t| t.to_string_lowercase());

            for a_type in &a_types_sorted {
                for b_type in &b_types_sorted {
                    let start_id = format!("{}+{}+{}", self.ontology_id, a_type.to_string_lowercase(), a_uri);
                    let end_id = format!("{}+{}+{}", self.ontology_id, b_type.to_string_lowercase(), b_uri);
                    let json_str = serde_json::to_string(edge_props)?;

                    // Extract property values for the property column
                    let prop_values: Vec<String> = edge_props.iter()
                        .filter(|(k, _)| k.as_str() != "type" && k.as_str() != "value")
                        .flat_map(|(_, v)| match v {
                            Value::String(s) => vec![s.clone()],
                            Value::Array(arr) => arr.iter().filter_map(|x| x.as_str().map(String::from)).collect(),
                            _ => vec![],
                        })
                        .collect();

                    // Edge TSV: start_id \t end_id \t type \t _json \t property
                    writeln!(self.edges_writer, "{}\t{}\t{}\t{}\t{}",
                        escape_tsv(&start_id),
                        escape_tsv(&end_id),
                        escape_tsv(predicate),
                        escape_tsv(&json_str),
                        escape_tsv(&pg_text_array(&prop_values)),
                    )?;
                }
            }
        }

        Ok(())
    }

    /// Serialize the average embedding for a given model on the parent entity node.
    fn serialize_average_embedding(&self, entity: &Map<String, Value>, entity_type: &str, iri: &str, model_name: &str) -> String {
        if !self.is_defining_entity(entity) {
            return "\\N".to_string();
        }

        if let Some(emb) = self.embeddings.get(model_name) {
            if let Some(avg) = emb.get_average_embedding(&self.ontology_id, entity_type, iri) {
                return pg_vector(&avg);
            }
        }
        "\\N".to_string()
    }

    /// Write Embedding child nodes for all models for a given entity.
    fn write_embedding_child_nodes(
        &mut self,
        entity_node_id: &str,
        entity: &Map<String, Value>,
        entity_type: &str,
        iri: &str,
    ) -> Result<(), Box<dyn std::error::Error>> {
        if !self.is_defining_entity(entity) {
            return Ok(());
        }

        // Collect rows first to avoid borrow conflicts
        struct EmbRow {
            node_id: String,
            emb_type: String,
            entity_id: String,
            model_idx: usize,
            vector: Vec<f32>,
        }

        let num_models = self.embedding_model_names.len();
        let mut rows: Vec<EmbRow> = Vec::new();

        for (model_idx, model_name) in self.embedding_model_names.iter().enumerate() {
            if let Some(emb) = self.embeddings.get(model_name) {
                if let Some(entries) = emb.get_embeddings(&self.ontology_id, entity_type, iri) {
                    for (vec_idx, entry) in entries.iter().enumerate() {
                        let node_id = format!("{}+emb+{}+{}+{}+{}", self.ontology_id, model_name, entity_type, iri, vec_idx);
                        let emb_type = if entry.string_type == "CURATION" {
                            "CurationEmbedding".to_string()
                        } else {
                            "LabelEmbedding".to_string()
                        };
                        rows.push(EmbRow {
                            node_id,
                            emb_type,
                            entity_id: entity_node_id.to_string(),
                            model_idx,
                            vector: entry.vector.clone(),
                        });
                    }
                }
            }
        }

        if let Some(ref mut writer) = self.embedding_nodes_writer {
            for row in &rows {
                // Embedding node TSV: id \t type \t entity_id \t [embedding columns...]
                write!(writer, "{}\t{}\t{}",
                    escape_tsv(&row.node_id),
                    escape_tsv(&row.emb_type),
                    escape_tsv(&row.entity_id),
                )?;
                for i in 0..num_models {
                    if i == row.model_idx {
                        write!(writer, "\t{}", pg_vector(&row.vector))?;
                    } else {
                        write!(writer, "\t\\N")?;
                    }
                }
                writeln!(writer)?;
            }
        }

        Ok(())
    }

    fn is_defining_entity(&self, entity: &Map<String, Value>) -> bool {
        entity
            .get("isDefiningOntology")
            .map(|v| {
                if let Some(b) = v.as_bool() {
                    b
                } else if let Some(arr) = v.as_array() {
                    arr.iter().any(|v| v.as_str() == Some("true"))
                } else {
                    false
                }
            })
            .unwrap_or(false)
    }
}
