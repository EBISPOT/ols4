use std::collections::{HashMap, HashSet};
use std::fs::File;
use std::io::BufWriter;

use csv::{Writer, WriterBuilder, QuoteStyle};
use indexmap::IndexMap;
use serde_json::{Map, Value};

use crate::embeddings::Embeddings;
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

pub struct OntologyWriter<'a> {
    output_file_path: String,
    ontology_id: String,
    manifest_info: OntologyManifestInfo,
    embeddings: &'a HashMap<String, Embeddings>,
    edges_properties: Vec<String>,
    edges_writer: Writer<BufWriter<File>>,
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
        })
    }

    pub fn write(&mut self, ontology: &Map<String, Value>) -> Result<(), Box<dyn std::error::Error>> {
        // Process entities first (classes, properties, individuals)
        for (name, value) in ontology {
            match name.as_str() {
                "classes" => {
                    if let Some(classes) = value.as_array() {
                        self.write_entities(
                            &format!("{}/{}_classes.csv", self.output_file_path, self.manifest_info.ontology_id),
                            "OntologyEntity|OntologyClass",
                            "class",
                            &self.manifest_info.all_class_properties.clone(),
                            classes,
                        )?;
                    }
                }
                "properties" => {
                    if let Some(properties) = value.as_array() {
                        self.write_entities(
                            &format!("{}/{}_properties.csv", self.output_file_path, self.manifest_info.ontology_id),
                            "OntologyEntity|OntologyProperty",
                            "property",
                            &self.manifest_info.all_property_properties.clone(),
                            properties,
                        )?;
                    }
                }
                "individuals" => {
                    if let Some(individuals) = value.as_array() {
                        self.write_entities(
                            &format!("{}/{}_individuals.csv", self.output_file_path, self.manifest_info.ontology_id),
                            "OntologyEntity|OntologyIndividual",
                            "individual",
                            &self.manifest_info.all_individual_properties.clone(),
                            individuals,
                        )?;
                    }
                }
                _ => {}
            }
        }

        // Build ontology properties map (excluding entities) preserving original order
        let ontology_properties: Map<String, Value> = ontology
            .iter()
            .filter(|(k, _)| k.as_str() != "classes" && k.as_str() != "properties" && k.as_str() != "individuals")
            .map(|(k, v)| (k.clone(), v.clone()))
            .collect();

        // Write ontology node
        self.write_ontology(&ontology_properties)?;

        // Flush edges writer
        self.edges_writer.flush()?;

        Ok(())
    }

    fn write_ontology(&mut self, ontology_properties: &Map<String, Value>) -> Result<(), Box<dyn std::error::Error>> {
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

    fn write_entities(
        &mut self,
        out_name: &str,
        node_labels: &str,
        entity_type: &str,
        all_entity_properties: &HashSet<String>,
        entities: &[Value],
    ) -> Result<(), Box<dyn std::error::Error>> {
        let mut properties: Vec<String> = all_entity_properties.iter().cloned().collect();
        properties.sort();

        // Get embedding model names for CSV columns
        let mut embedding_model_names: Vec<String> = self.embeddings.keys().cloned().collect();
        embedding_model_names.sort();

        let mut csv_header = vec![
            "id:ID".to_string(),
            ":LABEL".to_string(),
            "_json".to_string(),
        ];
        csv_header.extend(property_headers(&properties));

        // Add embedding columns
        for model_name in &embedding_model_names {
            csv_header.push(format!("embeddings_{}:float[]", model_name));
        }

        let file = File::create(out_name)?;
        let mut writer = WriterBuilder::new()
            .quote_style(QuoteStyle::Always)
            .from_writer(BufWriter::new(file));
        writer.write_record(&csv_header)?;

        for entity_value in entities {
            if let Some(entity) = entity_value.as_object() {
                let iri = entity
                    .get("iri")
                    .and_then(|v| v.as_str())
                    .unwrap_or("");

                let mut row: Vec<String> = Vec::with_capacity(csv_header.len());
                row.push(format!("{}+{}+{}", self.ontology_id, entity_type, iri));
                row.push(node_labels.to_string());
                
                // _json will be set after processing properties
                let json_idx = row.len();
                row.push(String::new()); // placeholder

                // Convert to IndexMap to preserve order
                let entity_map: IndexMap<String, Value> = entity.iter()
                    .map(|(k, v)| (k.clone(), v.clone()))
                    .collect();

                for column in &properties {
                    row.push(self.serialize_value(&entity_map, column, Some(iri))?);
                }

                // Serialize embedding values
                for model_name in &embedding_model_names {
                    row.push(self.serialize_embedding(entity_type, iri, model_name));
                }

                // Set _json
                row[json_idx] = serde_json::to_string(entity_value)?;

                writer.write_record(&row)?;
            }
        }

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
            for a_type in a_types {
                for b_type in b_types {
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

    fn serialize_embedding(&self, entity_type: &str, iri: &str, model_name: &str) -> String {
        if let Some(emb) = self.embeddings.get(model_name) {
            if let Some(embeddings_array) = emb.get_embeddings(&self.ontology_id, entity_type, iri) {
                return embeddings_array
                    .iter()
                    .map(|f| f.to_string())
                    .collect::<Vec<_>>()
                    .join("|");
            }
        }
        String::new()
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
