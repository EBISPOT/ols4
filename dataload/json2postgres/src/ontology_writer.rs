use std::collections::HashMap;
use std::fs::File;
use std::io::{BufWriter, Write};

use flate2::Compression;
use flate2::write::GzEncoder;
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

// ── PostgreSQL binary COPY writer ──────────────────────────────────────────

/// Writes PostgreSQL binary COPY format (as documented in the COPY section of
/// the PostgreSQL manual).  All multi-byte integers are big-endian (network
/// byte order).
struct BinaryCopyWriter {
    writer: BufWriter<File>,
}

impl BinaryCopyWriter {
    /// Create a new writer and emit the 19-byte file header.
    fn new(file: File) -> std::io::Result<Self> {
        let mut writer = BufWriter::with_capacity(256 * 1024, file);
        // 11-byte signature
        writer.write_all(b"PGCOPY\n\xff\r\n\0")?;
        // Flags field: 0 (no OIDs)
        writer.write_all(&0u32.to_be_bytes())?;
        // Header extension area length: 0
        writer.write_all(&0u32.to_be_bytes())?;
        Ok(Self { writer })
    }

    /// Start a new tuple with the given number of fields.
    #[inline]
    fn begin_row(&mut self, num_fields: i16) -> std::io::Result<()> {
        self.writer.write_all(&num_fields.to_be_bytes())
    }

    /// Write a NULL field (-1 length, no data).
    #[inline]
    fn write_null(&mut self) -> std::io::Result<()> {
        self.writer.write_all(&(-1i32).to_be_bytes())
    }

    /// Write a TEXT field (length-prefixed UTF-8 bytes).
    #[inline]
    fn write_text(&mut self, s: &str) -> std::io::Result<()> {
        let bytes = s.as_bytes();
        self.writer.write_all(&(bytes.len() as i32).to_be_bytes())?;
        self.writer.write_all(bytes)
    }

    /// Write a BYTEA field (length-prefixed raw bytes).
    #[inline]
    fn write_bytea(&mut self, bytes: &[u8]) -> std::io::Result<()> {
        self.writer.write_all(&(bytes.len() as i32).to_be_bytes())?;
        self.writer.write_all(bytes)
    }

    /// Gzip-compress a string at maximum compression and write as BYTEA.
    fn write_gzipped_text(&mut self, s: &str) -> std::io::Result<()> {
        let mut encoder = GzEncoder::new(Vec::new(), Compression::best());
        encoder.write_all(s.as_bytes())?;
        let compressed = encoder.finish()?;
        self.write_bytea(&compressed)
    }

    /// Write a BOOLEAN field (1 byte: 0 or 1).
    #[inline]
    fn write_bool(&mut self, b: bool) -> std::io::Result<()> {
        self.writer.write_all(&1i32.to_be_bytes())?;
        self.writer.write_all(&[u8::from(b)])
    }

    /// Write a TEXT[] (1-D text array) field in PostgreSQL binary array format.
    fn write_text_array(&mut self, values: &[String]) -> std::io::Result<()> {
        if values.is_empty() {
            // Empty array: ndim=0, has_null=0, elem_oid=25  → 12 bytes of data
            self.writer.write_all(&12i32.to_be_bytes())?;
            self.writer.write_all(&0i32.to_be_bytes())?;  // ndim
            self.writer.write_all(&0i32.to_be_bytes())?;  // has_null
            self.writer.write_all(&25i32.to_be_bytes())?;  // elem oid (TEXT)
        } else {
            // data = header(12) + dim_info(8) + elements(4+len each)
            let elems_len: usize = values.iter().map(|v| 4 + v.as_bytes().len()).sum();
            let data_len = (12 + 8 + elems_len) as i32;
            self.writer.write_all(&data_len.to_be_bytes())?;
            self.writer.write_all(&1i32.to_be_bytes())?;                  // ndim = 1
            self.writer.write_all(&0i32.to_be_bytes())?;                  // has_null = 0
            self.writer.write_all(&25i32.to_be_bytes())?;                 // elem oid (TEXT)
            self.writer.write_all(&(values.len() as i32).to_be_bytes())?; // dim size
            self.writer.write_all(&1i32.to_be_bytes())?;                  // lower bound = 1
            for v in values {
                let bytes = v.as_bytes();
                self.writer.write_all(&(bytes.len() as i32).to_be_bytes())?;
                self.writer.write_all(bytes)?;
            }
        }
        Ok(())
    }

    /// Write a pgvector `vector(N)` field in its binary transfer format:
    /// uint16 dim, uint16 unused(0), N × float32.
    fn write_vector(&mut self, values: &[f32]) -> std::io::Result<()> {
        let data_len = (4 + values.len() * 4) as i32;
        self.writer.write_all(&data_len.to_be_bytes())?;
        self.writer.write_all(&(values.len() as u16).to_be_bytes())?;
        self.writer.write_all(&0u16.to_be_bytes())?;
        for v in values {
            self.writer.write_all(&v.to_be_bytes())?;
        }
        Ok(())
    }

    /// Write the file trailer (-1 as int16) and flush.
    fn finish(&mut self) -> std::io::Result<()> {
        self.writer.write_all(&(-1i16).to_be_bytes())?;
        self.writer.flush()
    }
}

// ── Helper extraction functions ────────────────────────────────────────────

/// Extract is_obsolete from entity JSON. Returns true if the entity is obsolete.
fn extract_is_obsolete(entity: &Map<String, Value>) -> bool {
    match entity.get("isObsolete") {
        Some(Value::Bool(b)) => *b,
        Some(Value::Array(arr)) => arr.iter().any(|v| v.as_str() == Some("true")),
        Some(Value::String(s)) => s == "true",
        _ => false,
    }
}

/// Extract string values from entity JSON. Handles both plain strings and localized objects.
fn extract_localized_strings(entity: &Map<String, Value>, key: &str) -> Vec<String> {
    match entity.get(key) {
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

/// Extract a single string value from entity JSON.
fn extract_single_string(entity: &Map<String, Value>, key: &str) -> Option<String> {
    match entity.get(key) {
        Some(Value::String(s)) => Some(s.clone()),
        Some(Value::Array(arr)) => arr.first().and_then(|v| match v {
            Value::String(s) => Some(s.clone()),
            Value::Object(obj) => obj.get("value").and_then(|v| v.as_str()).map(String::from),
            _ => None,
        }),
        _ => None,
    }
}

/// Extract a boolean value from entity JSON.
fn extract_bool(entity: &Map<String, Value>, key: &str) -> bool {
    match entity.get(key) {
        Some(Value::Bool(b)) => *b,
        Some(Value::Array(arr)) => arr.iter().any(|v| v.as_str() == Some("true") || v.as_bool() == Some(true)),
        Some(Value::String(s)) => s == "true",
        _ => false,
    }
}

// ── OntologyWriter ─────────────────────────────────────────────────────────

pub struct OntologyWriter<'a> {
    #[allow(dead_code)]
    output_file_path: String,
    ontology_id: String,
    manifest_info: OntologyManifestInfo,
    embeddings: &'a HashMap<String, Embeddings>,
    embedding_model_names: Vec<String>,
    ontology_iri: String,
    ontology_preferred_prefix: String,
    filter_property_names: Vec<String>,
    entity_field_count: i16,
    edge_field_count: i16,
    emb_node_field_count: i16,
    entities_writer: BinaryCopyWriter,
    edges_writer: BinaryCopyWriter,
    embedding_nodes_writer: Option<BinaryCopyWriter>,
}

impl<'a> OntologyWriter<'a> {
    pub fn new(
        output_file_path: &str,
        manifest_info: OntologyManifestInfo,
        embeddings: &'a HashMap<String, Embeddings>,
        ontology_properties: &Map<String, Value>,
        filter_property_names: Vec<String>,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        let ontology_id = manifest_info.ontology_id.clone();

        let ontology_iri = ontology_properties.get("iri")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();
        let ontology_preferred_prefix = ontology_properties.get("preferredPrefix")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();

        let mut embedding_model_names: Vec<String> = embeddings.keys().cloned().collect();
        embedding_model_names.sort();

        // Field counts (must match create_postgres_schema.py column order)
        let entity_field_count = (26 + filter_property_names.len() + embedding_model_names.len()) as i16;
        let edge_field_count: i16 = 5;
        let emb_node_field_count = (3 + embedding_model_names.len()) as i16;

        // Create binary COPY files
        let entities_file = File::create(format!("{}/{}_entities.pgbin", output_file_path, ontology_id))?;
        let entities_writer = BinaryCopyWriter::new(entities_file)?;

        let edges_file = File::create(format!("{}/{}_edges.pgbin", output_file_path, ontology_id))?;
        let edges_writer = BinaryCopyWriter::new(edges_file)?;

        let embedding_nodes_writer = if !embedding_model_names.is_empty() {
            let emb_file = File::create(format!("{}/{}_embedding_nodes.pgbin", output_file_path, ontology_id))?;
            Some(BinaryCopyWriter::new(emb_file)?)
        } else {
            None
        };

        Ok(Self {
            output_file_path: output_file_path.to_string(),
            ontology_id,
            manifest_info,
            embeddings,
            embedding_model_names,
            ontology_iri,
            ontology_preferred_prefix,
            filter_property_names,
            entity_field_count,
            edge_field_count,
            emb_node_field_count,
            entities_writer,
            edges_writer,
            embedding_nodes_writer,
        })
    }

    /// Write a single entity row in binary COPY format.
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

        // Extract searchable fields
        let labels = extract_localized_strings(entity, "label");
        let direct_ancestors = extract_string_array(entity, "directAncestor");
        let hierarchical_ancestors = extract_string_array(entity, "hierarchicalAncestor");
        let short_form = extract_single_string(entity, "shortForm").unwrap_or_default();
        let curie = extract_single_string(entity, "curie").unwrap_or_default();
        let synonyms = extract_localized_strings(entity, "synonym");
        let definitions = extract_localized_strings(entity, "definition");
        let is_defining = self.is_defining_entity(entity);
        let subset = extract_string_array(entity, "http://www.geneontology.org/formats/oboInOwl#inSubset");
        let related_to = extract_string_array(entity, "relatedTo");
        let curated_from_sources = extract_string_array(entity, "curatedFromSources");

        let w = &mut self.entities_writer;
        w.begin_row(self.entity_field_count)?;

        // Base columns (26)
        w.write_text(&entity_node_id)?;                          // id
        w.write_text(pg_type)?;                                  // type
        w.write_text(iri)?;                                      // iri
        w.write_text(&self.ontology_id)?;                        // ontology_id
        w.write_gzipped_text(&json_str)?;                        // _json (gzip-compressed bytea)
        w.write_bool(extract_is_obsolete(entity))?;              // is_obsolete
        w.write_text_array(&labels)?;                            // label
        w.write_text_array(&direct_ancestors)?;                  // direct_ancestors
        w.write_text_array(&hierarchical_ancestors)?;            // hierarchical_ancestors
        w.write_text(entity_type_str)?;                          // search_type
        w.write_text(&short_form)?;                              // short_form
        w.write_text(&curie)?;                                   // curie
        w.write_text(&curie)?;                                   // obo_id
        w.write_text_array(&synonyms)?;                          // synonym
        w.write_text_array(&definitions)?;                       // definition
        w.write_bool(is_defining)?;                              // is_defining_ontology
        w.write_bool(extract_bool(entity, "hasDirectParents"))?; // has_direct_parents
        w.write_bool(extract_bool(entity, "hasHierarchicalParents"))?; // has_hierarchical_parents
        w.write_bool(extract_bool(entity, "hasDirectChildren"))?;      // has_direct_children
        w.write_bool(extract_bool(entity, "hasHierarchicalChildren"))?; // has_hierarchical_children
        w.write_bool(extract_bool(entity, "isPreferredRoot"))?; // is_preferred_root
        w.write_text(&self.ontology_iri)?;                       // ontology_iri
        w.write_text(&self.ontology_preferred_prefix)?;          // ontology_preferred_prefix
        w.write_text_array(&subset)?;                            // subset
        w.write_text_array(&related_to)?;                        // related_to
        w.write_text_array(&curated_from_sources)?;              // curated_from_sources

        // Configurable filter property columns
        for i in 0..self.filter_property_names.len() {
            let values = extract_localized_strings(entity, &self.filter_property_names[i]);
            w.write_text_array(&values)?;
        }

        // One embedding column per model (average embedding on parent entity)
        for model_name in &self.embedding_model_names.clone() {
            if let Some(avg) = self.get_average_embedding(entity, entity_type_str, iri, model_name) {
                self.entities_writer.write_vector(&avg)?;
            } else {
                self.entities_writer.write_null()?;
            }
        }

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

    /// Write the ontology node as an entity row (binary).
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

        let definitions = extract_localized_strings(ontology_properties, "definition");
        let labels = extract_localized_strings(ontology_properties, "label");
        let preferred_prefix = ontology_properties
            .get("preferredPrefix")
            .and_then(|v| v.as_str())
            .unwrap_or("");

        let empty: Vec<String> = vec![];

        self.entities_writer.begin_row(self.entity_field_count)?;

        // Base columns (26)
        self.entities_writer.write_text(&entity_node_id)?;       // id
        self.entities_writer.write_text("Ontology")?;            // type
        self.entities_writer.write_text(iri)?;                   // iri
        self.entities_writer.write_text(ontology_id)?;           // ontology_id
        self.entities_writer.write_gzipped_text(&json_str)?;     // _json (gzip-compressed bytea)
        self.entities_writer.write_bool(false)?;                 // is_obsolete
        self.entities_writer.write_text_array(&labels)?;         // label
        self.entities_writer.write_text_array(&empty)?;          // direct_ancestors
        self.entities_writer.write_text_array(&empty)?;          // hierarchical_ancestors
        self.entities_writer.write_text("ontology")?;            // search_type
        self.entities_writer.write_text("")?;                    // short_form
        self.entities_writer.write_text("")?;                    // curie
        self.entities_writer.write_text("")?;                    // obo_id
        self.entities_writer.write_text_array(&empty)?;          // synonym
        self.entities_writer.write_text_array(&definitions)?;    // definition
        self.entities_writer.write_bool(false)?;                 // is_defining_ontology
        self.entities_writer.write_bool(false)?;                 // has_direct_parents
        self.entities_writer.write_bool(false)?;                 // has_hierarchical_parents
        self.entities_writer.write_bool(false)?;                 // has_direct_children
        self.entities_writer.write_bool(false)?;                 // has_hierarchical_children
        self.entities_writer.write_bool(false)?;                 // is_preferred_root
        self.entities_writer.write_text(iri)?;                   // ontology_iri
        self.entities_writer.write_text(preferred_prefix)?;      // ontology_preferred_prefix
        self.entities_writer.write_text_array(&empty)?;          // subset
        self.entities_writer.write_text_array(&empty)?;          // related_to
        self.entities_writer.write_text_array(&empty)?;          // curated_from_sources

        // Empty filter property columns
        for _ in &self.filter_property_names {
            self.entities_writer.write_text_array(&empty)?;
        }

        // Ontology nodes don't have embeddings
        for _ in &self.embedding_model_names {
            self.entities_writer.write_null()?;
        }

        Ok(())
    }

    /// Write binary COPY trailers and flush all writers.
    pub fn finish(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        self.entities_writer.finish()?;
        self.edges_writer.finish()?;
        if let Some(ref mut w) = self.embedding_nodes_writer {
            w.finish()?;
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

                    let prop_values: Vec<String> = edge_props.iter()
                        .filter(|(k, _)| k.as_str() != "type" && k.as_str() != "value")
                        .flat_map(|(_, v)| match v {
                            Value::String(s) => vec![s.clone()],
                            Value::Array(arr) => arr.iter().filter_map(|x| x.as_str().map(String::from)).collect(),
                            _ => vec![],
                        })
                        .collect();

                    self.edges_writer.begin_row(self.edge_field_count)?;
                    self.edges_writer.write_text(&start_id)?;
                    self.edges_writer.write_text(&end_id)?;
                    self.edges_writer.write_text(predicate)?;
                    self.edges_writer.write_gzipped_text(&json_str)?;
                    self.edges_writer.write_text_array(&prop_values)?;
                }
            }
        }

        Ok(())
    }

    /// Return the average embedding for a model, or None if unavailable/not defining.
    fn get_average_embedding(&self, entity: &Map<String, Value>, entity_type: &str, iri: &str, model_name: &str) -> Option<Vec<f32>> {
        if !self.is_defining_entity(entity) {
            return None;
        }
        self.embeddings.get(model_name)
            .and_then(|emb| emb.get_average_embedding(&self.ontology_id, entity_type, iri))
    }

    /// Write embedding child nodes for all models for a given entity (binary).
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
                writer.begin_row(self.emb_node_field_count)?;
                writer.write_text(&row.node_id)?;
                writer.write_text(&row.emb_type)?;
                writer.write_text(&row.entity_id)?;
                for i in 0..num_models {
                    if i == row.model_idx {
                        writer.write_vector(&row.vector)?;
                    } else {
                        writer.write_null()?;
                    }
                }
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
