use std::collections::{BTreeSet, HashSet};
use std::io::Read;

use ols_shared::streaming::read_value;
use struson::reader::{JsonReader, JsonStreamReader};

use crate::node_type::NodeType;
use crate::ontology_scan_result::OntologyScanResult;

/// Scan an ontology from a streaming JSON reader
/// This matches the behavior of Java's OntologyScanner.scanOntology
pub fn scan_ontology<R: Read>(
    reader: &mut JsonStreamReader<R>,
    ignore_properties: &HashSet<String>,
) -> OntologyScanResult {
    let mut result = OntologyScanResult::default();

    reader.begin_object().unwrap();

    while reader.has_next().unwrap() {
        let name = reader.next_name_owned().unwrap();

        match name.as_str() {
            "classes" => {
                reader.begin_array().unwrap();
                while reader.has_next().unwrap() {
                    reader.begin_object().unwrap();
                    while reader.has_next().unwrap() {
                        let property = reader.next_name_owned().unwrap();

                        if !ignore_properties.contains(&property) {
                            result.all_class_properties.insert(property.clone());
                        }

                        if property == "iri" {
                            let iri = reader.next_string().unwrap().to_string();
                            add_type(&mut result, &iri, NodeType::Class);
                        } else {
                            let value = read_value(reader);
                            visit_value(
                                &property,
                                &value,
                                &mut result.all_class_properties,
                                &mut result.all_edge_properties,
                                ignore_properties,
                            );
                        }
                    }
                    reader.end_object().unwrap();
                }
                reader.end_array().unwrap();
            }
            "properties" => {
                reader.begin_array().unwrap();
                while reader.has_next().unwrap() {
                    reader.begin_object().unwrap();
                    while reader.has_next().unwrap() {
                        let property = reader.next_name_owned().unwrap();

                        if !ignore_properties.contains(&property) {
                            result.all_property_properties.insert(property.clone());
                        }

                        if property == "iri" {
                            let iri = reader.next_string().unwrap().to_string();
                            add_type(&mut result, &iri, NodeType::Property);
                        } else {
                            let value = read_value(reader);
                            visit_value(
                                &property,
                                &value,
                                &mut result.all_property_properties,
                                &mut result.all_edge_properties,
                                ignore_properties,
                            );
                        }
                    }
                    reader.end_object().unwrap();
                }
                reader.end_array().unwrap();
            }
            "individuals" => {
                reader.begin_array().unwrap();
                while reader.has_next().unwrap() {
                    reader.begin_object().unwrap();
                    while reader.has_next().unwrap() {
                        let property = reader.next_name_owned().unwrap();

                        if !ignore_properties.contains(&property) {
                            result.all_individual_properties.insert(property.clone());
                        }

                        if property == "iri" {
                            let iri = reader.next_string().unwrap().to_string();
                            add_type(&mut result, &iri, NodeType::Individual);
                        } else {
                            let value = read_value(reader);
                            visit_value(
                                &property,
                                &value,
                                &mut result.all_individual_properties,
                                &mut result.all_edge_properties,
                                ignore_properties,
                            );
                        }
                    }
                    reader.end_object().unwrap();
                }
                reader.end_array().unwrap();
            }
            "iri" => {
                let uri = reader.next_string().unwrap().to_string();
                result.ontology_uri = uri.clone();
                add_type(&mut result, &uri, NodeType::Ontology);
                if !ignore_properties.contains("iri") {
                    result.all_ontology_properties.insert("iri".to_string());
                }
            }
            "ontologyId" => {
                result.ontology_id = reader.next_string().unwrap().to_string();
                if !ignore_properties.contains("ontologyId") {
                    result.all_ontology_properties.insert("ontologyId".to_string());
                }
            }
            _ => {
                if !ignore_properties.contains(&name) {
                    result.all_ontology_properties.insert(name.clone());
                }
                let value = read_value(reader);
                visit_value(
                    &name,
                    &value,
                    &mut result.all_ontology_properties,
                    &mut result.all_edge_properties,
                    ignore_properties,
                );
            }
        }
    }

    reader.end_object().unwrap();

    result
}

fn add_type(result: &mut OntologyScanResult, uri: &str, node_type: NodeType) {
    result
        .uri_to_types
        .entry(uri.to_string())
        .or_default()
        .insert(node_type);
}

fn visit_value(
    predicate: &str,
    value: &serde_json::Value,
    out_props: &mut BTreeSet<String>,
    out_edge_props: &mut BTreeSet<String>,
    ignore_properties: &HashSet<String>,
) {
    if predicate == "linkedEntities" {
        return;
    }

    match value {
        serde_json::Value::String(_) => {}
        serde_json::Value::Array(arr) => {
            for entry in arr {
                visit_value(predicate, entry, out_props, out_edge_props, ignore_properties);
            }
        }
        serde_json::Value::Object(map) => {
            // Could be a typed literal, a relatedTo object, reification, a bnode,
            // or some json junk from the ontology config
            let type_val = map.get("type");

            let types: Vec<String> = match type_val {
                Some(serde_json::Value::Array(arr)) => arr
                    .iter()
                    .filter_map(|v| v.as_str().map(String::from))
                    .collect(),
                _ => return, // bnode (anon. class) or json junk
            };

            if types.contains(&"literal".to_string()) {
                // Is this a localization?
                if let Some(serde_json::Value::String(lang)) = map.get("lang") {
                    // Add a localized property like e.g. fr+http://some/predicate
                    // (english is the default and doesn't get a prefix)
                    if lang != "en" && !ignore_properties.contains(predicate) {
                        out_props.insert(format!("{}+{}", lang, predicate));
                    }
                }
            } else if types.contains(&"related".to_string()) {
                if let Some(inner_value) = map.get("value") {
                    visit_value(
                        predicate,
                        inner_value,
                        out_props,
                        out_edge_props,
                        ignore_properties,
                    );
                }
            } else if types.contains(&"reification".to_string()) {
                if let Some(serde_json::Value::Array(axioms)) = map.get("axioms") {
                    for axiom_obj in axioms {
                        if let serde_json::Value::Object(axiom) = axiom_obj {
                            // Predicates used to describe the edge itself
                            for edge_predicate in axiom.keys() {
                                if edge_predicate == "type" {
                                    continue;
                                }
                                if !ignore_properties.contains(edge_predicate) {
                                    out_edge_props.insert(edge_predicate.clone());
                                }
                            }
                        }
                    }
                }
                if let Some(inner_value) = map.get("value") {
                    visit_value(
                        predicate,
                        inner_value,
                        out_props,
                        out_edge_props,
                        ignore_properties,
                    );
                }
            } else if types.contains(&"datatype".to_string()) {
                // Nothing to do for datatypes
            }
            // Other types are ignored
        }
        _ => {}
    }
}
