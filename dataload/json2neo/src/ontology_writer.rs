use std::collections::HashMap;
use std::fs::File;
use std::io::BufWriter;

use csv::{Writer, WriterBuilder, QuoteStyle};
use indexmap::IndexMap;
use serde_json::{Map, Value};

use ols_shared::Embeddings;
#[allow(unused_imports)]
use crate::manifest::{NodeType, OntologyManifestInfo};

/// Property blacklist - these shouldn't be stored as Neo4j node properties
#[allow(dead_code)]
const PROPERTY_BLACKLIST: &[&str] = &[
    "appearsIn",                    // large and doesn't get queried
    "searchableAnnotationValues",   // all property values together, for solr not neo4j
];

/// Edge blacklist - these shouldn't create edges
const EDGE_BLACKLIST: &[&str] = &[
    "iri",                   // don't create lots of "iri" edges pointing from each node to itself
    "hierarchicalProperty",  // informational only
    "definitionProperty",    // informational only
    "synonymProperty",       // informational only
    "directAncestor",        // redundant - we have parent edges and cypher can be recursive
    "hierarchicalAncestor",  // redundant - we have parent edges and cypher can be recursive
    "relatedFrom",           // redundant - we already have relatedTo which can be queried both ways
];

/// Entity writer state for streaming writes
struct EntityWriter {
    writer: Writer<BufWriter<File>>,
    properties: Vec<String>,
    embedding_model_names: Vec<String>,
    node_labels: String,
    entity_type_str: String,
}

/// Writer for Embedding child nodes
struct EmbeddingNodeWriter {
    writer: Writer<BufWriter<File>>,
}

/// Writer for HAS_EMBEDDING edges
struct EmbeddingEdgeWriter {
    writer: Writer<BufWriter<File>>,
}

pub struct OntologyWriter<'a> {
    output_file_path: String,
    ontology_id: String,
    manifest_info: OntologyManifestInfo,
    embeddings: &'a HashMap<String, Embeddings>,
    edges_properties: Vec<String>,
    edges_writer: Writer<BufWriter<File>>,
    /// Current entity writer (for streaming entity writes)
    current_entity_writer: Option<EntityWriter>,
    /// Embedding node writer (created once, shared across entity types)
    embedding_node_writer: Option<EmbeddingNodeWriter>,
    /// Embedding edge writer (created once, shared across entity types)
    embedding_edge_writer: Option<EmbeddingEdgeWriter>,
}

impl<'a> OntologyWriter<'a> {
    pub fn new(
        output_file_path: &str,
        manifest_info: OntologyManifestInfo,
        embeddings: &'a HashMap<String, Embeddings>,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        let ontology_id = manifest_info.ontology_id.clone();

        let mut edges_properties: Vec<String> = manifest_info.all_edge_properties.iter().cloned().collect();
        edges_properties.sort();

        // Set up edges CSV writer
        let mut edges_csv_header = vec![
            ":START_ID".to_string(),
            ":TYPE".to_string(),
            ":END_ID".to_string(),
            "_json".to_string(),
        ];
        edges_csv_header.extend(property_headers(&edges_properties));

        let edges_file = File::create(format!("{}/{}_edges.csv", output_file_path, ontology_id))?;
        let mut edges_writer = WriterBuilder::new()
            .quote_style(QuoteStyle::Always)
            .from_writer(BufWriter::new(edges_file));
        edges_writer.write_record(&edges_csv_header)?;

        Ok(Self {
            output_file_path: output_file_path.to_string(),
            ontology_id,
            manifest_info,
            embeddings,
            edges_properties,
            edges_writer,
            current_entity_writer: None,
            embedding_node_writer: None,
            embedding_edge_writer: None,
        })
    }
    
    /// Begin writing entities of a specific type (streaming mode)
    pub fn begin_entities(&mut self, entity_type: &str) -> Result<(), Box<dyn std::error::Error>> {
        let (out_name, node_labels, entity_type_str, all_entity_properties) = match entity_type {
            "classes" => (
                format!("{}/{}_classes.csv", self.output_file_path, self.ontology_id),
                "OntologyEntity|OntologyClass",
                "class",
                &self.manifest_info.all_class_properties,
            ),
            "properties" => (
                format!("{}/{}_properties.csv", self.output_file_path, self.ontology_id),
                "OntologyEntity|OntologyProperty",
                "property",
                &self.manifest_info.all_property_properties,
            ),
            "individuals" => (
                format!("{}/{}_individuals.csv", self.output_file_path, self.ontology_id),
                "OntologyEntity|OntologyIndividual",
                "individual",
                &self.manifest_info.all_individual_properties,
            ),
            _ => return Err(format!("Unknown entity type: {}", entity_type).into()),
        };
        
        let mut properties: Vec<String> = all_entity_properties.iter().cloned().collect();
        properties.sort();
        
        let mut embedding_model_names: Vec<String> = self.embeddings.keys().cloned().collect();
        embedding_model_names.sort();
        
        // Entity CSV header: id, labels, _json, properties..., average embedding per model
        let mut csv_header = vec![
            "id:ID".to_string(),
            ":LABEL".to_string(),
            "_json".to_string(),
        ];
        csv_header.extend(property_headers(&properties));
        
        for model_name in &embedding_model_names {
            csv_header.push(format!("embeddings_{}:float[]", model_name));
        }
        
        let file = File::create(&out_name)?;
        let mut writer = WriterBuilder::new()
            .quote_style(QuoteStyle::Always)
            .from_writer(BufWriter::new(file));
        writer.write_record(&csv_header)?;
        
        // Initialize embedding node/edge writers on first call (shared across entity types)
        if !embedding_model_names.is_empty() && self.embedding_node_writer.is_none() {
            // Embedding nodes CSV: id, label, one embedding column per model
            let mut emb_node_header = vec![
                "id:ID".to_string(),
                ":LABEL".to_string(),
            ];
            for model_name in &embedding_model_names {
                emb_node_header.push(format!("embedding_{}:float[]", model_name));
            }
            
            let emb_node_file = File::create(format!("{}/{}_embedding_nodes.csv", self.output_file_path, self.ontology_id))?;
            let mut emb_node_writer = WriterBuilder::new()
                .quote_style(QuoteStyle::Always)
                .from_writer(BufWriter::new(emb_node_file));
            emb_node_writer.write_record(&emb_node_header)?;
            
            self.embedding_node_writer = Some(EmbeddingNodeWriter {
                writer: emb_node_writer,
            });
            
            // Embedding edges CSV: start_id, type, end_id
            let emb_edge_header = vec![
                ":START_ID".to_string(),
                ":TYPE".to_string(),
                ":END_ID".to_string(),
            ];
            
            let emb_edge_file = File::create(format!("{}/{}_embedding_edges.csv", self.output_file_path, self.ontology_id))?;
            let mut emb_edge_writer = WriterBuilder::new()
                .quote_style(QuoteStyle::Always)
                .from_writer(BufWriter::new(emb_edge_file));
            emb_edge_writer.write_record(&emb_edge_header)?;
            
            self.embedding_edge_writer = Some(EmbeddingEdgeWriter {
                writer: emb_edge_writer,
            });
        }
        
        self.current_entity_writer = Some(EntityWriter {
            writer,
            properties,
            embedding_model_names,
            node_labels: node_labels.to_string(),
            entity_type_str: entity_type_str.to_string(),
        });
        
        Ok(())
    }
    
    /// Write a single entity (streaming mode)
    pub fn write_entity(
        &mut self,
        _entity_type: &str,
        entity: &Map<String, Value>,
        entity_value: &Value,
    ) -> Result<(), Box<dyn std::error::Error>> {
        // Extract what we need from current_entity_writer first to avoid borrow conflicts
        let (properties, embedding_model_names, node_labels, entity_type_str) = {
            let ew = self.current_entity_writer.as_ref()
                .ok_or("No entity writer active - call begin_entities first")?;
            (
                ew.properties.clone(),
                ew.embedding_model_names.clone(),
                ew.node_labels.clone(),
                ew.entity_type_str.clone(),
            )
        };
        
        let iri = entity
            .get("iri")
            .and_then(|v| v.as_str())
            .unwrap_or("");
        
        let entity_node_id = format!("{}+{}+{}", self.ontology_id, entity_type_str, iri);
        
        let mut row: Vec<String> = Vec::with_capacity(3 + properties.len() + embedding_model_names.len());
        row.push(entity_node_id.clone());
        row.push(node_labels);
        
        let json_idx = row.len();
        row.push(String::new()); // placeholder
        
        let entity_map: IndexMap<String, Value> = entity.iter()
            .map(|(k, v)| (k.clone(), v.clone()))
            .collect();
        
        for column in &properties {
            row.push(self.serialize_value(&entity_map, column, Some(iri))?);
        }
        
        // Write average embedding per model on the parent node
        for model_name in &embedding_model_names {
            row.push(self.serialize_average_embedding(entity, &entity_type_str, iri, model_name));
        }
        
        row[json_idx] = serde_json::to_string(entity_value)?;
        
        // Write the entity row
        let ew = self.current_entity_writer.as_mut().unwrap();
        ew.writer.write_record(&row)?;
        
        // Write individual Embedding child nodes + HAS_EMBEDDING edges
        self.write_embedding_child_nodes(&entity_node_id, entity, &entity_type_str, iri, &embedding_model_names)?;
        
        Ok(())
    }
    
    /// End writing entities of the current type (streaming mode)
    pub fn end_entities(&mut self, _entity_type: &str) -> Result<(), Box<dyn std::error::Error>> {
        if let Some(mut ew) = self.current_entity_writer.take() {
            ew.writer.flush()?;
        }
        Ok(())
    }
    
    /// Write an empty entities file (for entity types not present in the ontology)
    pub fn write_empty_entities(&mut self, entity_type: &str) -> Result<(), Box<dyn std::error::Error>> {
        self.begin_entities(entity_type)?;
        self.end_entities(entity_type)?;
        Ok(())
    }
    
    /// Finish writing (flush all writers)
    pub fn finish(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        self.edges_writer.flush()?;
        if let Some(ref mut w) = self.embedding_node_writer {
            w.writer.flush()?;
        }
        if let Some(ref mut w) = self.embedding_edge_writer {
            w.writer.flush()?;
        }
        Ok(())
    }

    pub fn write_ontology(&mut self, ontology_properties: &Map<String, Value>) -> Result<(), Box<dyn std::error::Error>> {
        let mut properties: Vec<String> = self.manifest_info.all_ontology_properties.iter().cloned().collect();
        properties.sort();

        let mut csv_header = vec![
            "id:ID".to_string(),
            ":LABEL".to_string(),
            "_json".to_string(),
        ];
        csv_header.extend(property_headers(&properties));

        let out_name = format!("{}/{}_ontologies.csv", self.output_file_path, self.manifest_info.ontology_id);
        let file = File::create(&out_name)?;
        let mut writer = WriterBuilder::new()
            .quote_style(QuoteStyle::Always)
            .from_writer(BufWriter::new(file));
        writer.write_record(&csv_header)?;

        let ontology_id = ontology_properties
            .get("ontologyId")
            .and_then(|v| v.as_str())
            .unwrap_or("");
        let iri = ontology_properties
            .get("iri")
            .and_then(|v| v.as_str())
            .unwrap_or("");

        // Convert to IndexMap for property access, but use original for JSON serialization
        let ontology_props_indexmap: IndexMap<String, Value> = ontology_properties
            .iter()
            .map(|(k, v)| (k.clone(), v.clone()))
            .collect();

        let mut row: Vec<String> = Vec::with_capacity(csv_header.len());
        row.push(format!("{}+ontology+{}", ontology_id, iri));
        row.push("Ontology".to_string());
        
        // Serialize the ontology properties preserving original order
        row.push(serde_json::to_string(&Value::Object(ontology_properties.clone()))?);

        for column in &properties {
            row.push(self.serialize_value(&ontology_props_indexmap, column, Some(iri))?);
        }

        writer.write_record(&row)?;
        writer.flush()?;

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
                            // reification
                            if let Some(reified_value) = map_value.get("value").and_then(|v| v.as_str()) {
                                if let Some(axioms) = map_value.get("axioms").and_then(|a| a.as_array()) {
                                    // is the value the URI of something that exists in the ontology?
                                    if self.manifest_info.uri_to_types.contains_key(reified_value) {
                                        // create one edge for each axiom
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
                                // is the value the URI of something that exists in the ontology?
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
                // is the value the URI of something that exists in the ontology?
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

        // In the case of punning, the same URI can have multiple types
        let a_types = self.manifest_info.uri_to_types.get(a_uri);
        let b_types = self.manifest_info.uri_to_types.get(b_uri);

        if let (Some(a_types), Some(b_types)) = (a_types, b_types) {
            // Sort types for deterministic output order
            let mut a_types_sorted: Vec<_> = a_types.iter().collect();
            let mut b_types_sorted: Vec<_> = b_types.iter().collect();
            a_types_sorted.sort_by_key(|t| t.to_string_lowercase());
            b_types_sorted.sort_by_key(|t| t.to_string_lowercase());

            for a_type in a_types_sorted {
                for b_type in b_types_sorted.iter() {
                    let mut row: Vec<String> = Vec::with_capacity(4 + self.edges_properties.len());

                    row.push(format!("{}+{}+{}", self.ontology_id, a_type.to_string_lowercase(), a_uri));
                    row.push(predicate.to_string());
                    row.push(format!("{}+{}+{}", self.ontology_id, b_type.to_string_lowercase(), b_uri));
                    row.push(serde_json::to_string(edge_props)?);

                    for column in &self.edges_properties {
                        row.push(serialize_edge_value(edge_props, column));
                    }

                    self.edges_writer.write_record(&row)?;
                }
            }
        }

        Ok(())
    }

    fn serialize_value(
        &mut self,
        entity_properties: &IndexMap<String, Value>,
        column: &str,
        uri: Option<&str>,
    ) -> Result<String, Box<dyn std::error::Error>> {
        // Handle localized values (e.g., "en+label")
        if column.contains('+') && !column.starts_with("related") {
            let parts: Vec<&str> = column.splitn(2, '+').collect();
            if parts.len() == 2 {
                let lang = parts[0];
                let predicate = parts[1];
                return Ok(value_to_csv(&get_localized_value(entity_properties, predicate, lang)));
            }
        }

        let value = entity_properties.get(column);

        // BNodes subjects don't get edges in the graph
        if let (Some(uri), Some(value)) = (uri, value) {
            self.maybe_write_edges(uri, column, value)?;
        }

        Ok(value_to_csv(&value.cloned()))
    }

    /// Serialize the average embedding for a given model on the parent entity node.
    fn serialize_average_embedding(&self, entity: &Map<String, Value>, entity_type: &str, iri: &str, model_name: &str) -> String {
        if !self.is_defining_entity(entity) {
            return String::new();
        }
        
        if let Some(emb) = self.embeddings.get(model_name) {
            if let Some(avg) = emb.get_average_embedding(&self.ontology_id, entity_type, iri) {
                return avg
                    .iter()
                    .map(|f| f.to_string())
                    .collect::<Vec<_>>()
                    .join("|");
            }
        }
        String::new()
    }
    
    /// Write Embedding child nodes and HAS_EMBEDDING edges for all models for a given entity.
    fn write_embedding_child_nodes(
        &mut self,
        entity_node_id: &str,
        entity: &Map<String, Value>,
        entity_type: &str,
        iri: &str,
        embedding_model_names: &[String],
    ) -> Result<(), Box<dyn std::error::Error>> {
        if !self.is_defining_entity(entity) {
            return Ok(());
        }
        
        // Collect all embedding child node data first to avoid borrow conflicts
        struct EmbNodeRow {
            node_id: String,
            neo4j_label: String,
            /// Index of the model in embedding_model_names
            model_idx: usize,
            embedding_str: String,
        }
        
        let num_models = embedding_model_names.len();
        let mut emb_rows: Vec<EmbNodeRow> = Vec::new();
        
        for (model_idx, model_name) in embedding_model_names.iter().enumerate() {
            if let Some(emb) = self.embeddings.get(model_name) {
                if let Some(entries) = emb.get_embeddings(&self.ontology_id, entity_type, iri) {
                    for (vec_idx, entry) in entries.iter().enumerate() {
                        let node_id = format!("{}+emb+{}+{}+{}+{}", self.ontology_id, model_name, entity_type, iri, vec_idx);
                        let embedding_str = entry.vector
                            .iter()
                            .map(|f| f.to_string())
                            .collect::<Vec<_>>()
                            .join("|");
                        let neo4j_label = if entry.string_type == "CURATION" {
                            "Embedding|CurationEmbedding".to_string()
                        } else {
                            "Embedding|LabelEmbedding".to_string()
                        };
                        emb_rows.push(EmbNodeRow {
                            node_id,
                            neo4j_label,
                            model_idx,
                            embedding_str,
                        });
                    }
                }
            }
        }
        
        // Now write the rows
        if let (Some(node_writer), Some(edge_writer)) = (
            self.embedding_node_writer.as_mut(),
            self.embedding_edge_writer.as_mut(),
        ) {
            for emb_row in &emb_rows {
                // Build embedding node row: id, :LABEL, then one column per model (only one filled)
                let mut row = Vec::with_capacity(2 + num_models);
                row.push(emb_row.node_id.clone());
                row.push(emb_row.neo4j_label.clone());
                for i in 0..num_models {
                    if i == emb_row.model_idx {
                        row.push(emb_row.embedding_str.clone());
                    } else {
                        row.push(String::new());
                    }
                }
                node_writer.writer.write_record(&row)?;
                
                // Build HAS_EMBEDDING edge row
                let edge_row = vec![
                    entity_node_id.to_string(),
                    "HAS_EMBEDDING".to_string(),
                    emb_row.node_id.clone(),
                ];
                edge_writer.writer.write_record(&edge_row)?;
            }
        }
        
        Ok(())
    }
    
    /// Check if entity is a defining entity (only defining entities get embeddings)
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

fn property_headers(field_names: &[String]) -> Vec<String> {
    field_names
        .iter()
        .filter_map(|k| {
            if k == "iri" {
                Some("iri".to_string())
            } else if k.starts_with("embeddings_") {
                None // Skip embedding headers here, they're added separately
            } else {
                Some(format!("{}:string[]", k.replace(':', "__")))
            }
        })
        .collect()
}

fn value_to_csv(value: &Option<Value>) -> String {
    match value {
        None => String::new(),
        Some(Value::Null) => String::new(),
        Some(Value::String(s)) => replace_neo4j_special_chars(s),
        Some(Value::Array(arr)) => {
            arr.iter()
                .map(|v| value_to_csv(&Some(v.clone())))
                .collect::<Vec<_>>()
                .join("|")
        }
        Some(Value::Object(map)) => {
            // Could be a reification or a localisation
            if let Some(val) = map.get("value") {
                value_to_csv(&Some(val.clone()))
            } else {
                // Probably a class expression; wouldn't result in anything queryable
                String::new()
            }
        }
        Some(Value::Bool(b)) => b.to_string(),
        Some(Value::Number(n)) => n.to_string(),
    }
}

fn serialize_edge_value(edge_props: &IndexMap<String, Value>, column: &str) -> String {
    let value = edge_props.get(column);
    value_to_csv(&value.cloned())
}

fn replace_neo4j_special_chars(val: &str) -> String {
    val.replace('|', "\\u007C")
}

fn get_localized_value(properties: &IndexMap<String, Value>, predicate: &str, lang: &str) -> Option<Value> {
    let values = properties.get(predicate)?;

    let values_arr = if values.is_array() {
        values.as_array().unwrap()
    } else {
        return Some(values.clone());
    };

    for value in values_arr {
        if let Some(map) = value.as_object() {
            if let Some(value_lang) = map.get("lang").and_then(|l| l.as_str()) {
                if value_lang == lang {
                    if let Some(v) = map.get("value") {
                        return Some(Value::String(value_to_csv(&Some(v.clone()))));
                    }
                }
            }
        }
    }

    None
}
