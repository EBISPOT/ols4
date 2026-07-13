//! Streaming JSON serialization of the ontology graph using `serde_json`.
//!
//! We reproduce the *content* of the original Java output (field names, value
//! shapes, key/array ordering) but rely on `serde_json` for the JSON encoding
//! rather than matching Gson's byte formatting.
//!
//! Output is **streamed**: the classes/properties/individuals arrays are emitted
//! one entity at a time via custom `Serialize` impls, so memory stays bounded by
//! a single entity (plus the in-memory graph) even for very large ontologies —
//! we never build a full `Value` tree or output string for the whole document.

use std::io::Write;

use serde::ser::{Serialize, SerializeMap, SerializeSeq, Serializer};
use serde_json::{Map, Value};

use crate::config::OntologyConfig;
use crate::error::Rdf2JsonError;
use crate::graph::OntologyGraph;
use crate::model::*;

type Obj = Map<String, Value>;

const CLASS_TYPES: &[&str] = &["class", "entity"];
const OBJECT_PROPERTY_TYPES: &[&str] = &["entity", "objectProperty", "property"];
const ANNOTATION_PROPERTY_TYPES: &[&str] = &["annotationProperty", "entity", "property"];
const DATA_PROPERTY_TYPES: &[&str] = &["dataProperty", "entity", "property"];
const PROPERTY_TYPES: &[&str] = &["entity", "property"];
const INDIVIDUAL_TYPES: &[&str] = &["entity", "individual"];
const ONTOLOGY_TYPES: &[&str] = &["ontology"];

fn is_xml_builtin_datatype(uri: &str) -> bool {
    uri.starts_with("http://www.w3.org/2001/XMLSchema#")
}

fn type_array(types: &[&str]) -> Value {
    Value::Array(types.iter().map(|t| Value::String(t.to_string())).collect())
}

/// Sort a list of values by their compact serialized form (Java sorts the
/// serialized JSON of each value before writing multi-valued properties).
fn sorted(mut values: Vec<Value>) -> Vec<Value> {
    values.sort_by(|a, b| {
        serde_json::to_string(a)
            .unwrap_or_default()
            .cmp(&serde_json::to_string(b).unwrap_or_default())
    });
    values
}

/// Stream the full output document `{ "ontologies": [ ... ] }` to `out`.
pub fn write_document<W: Write>(graphs: &[OntologyGraph], out: W) -> Result<(), Rdf2JsonError> {
    let mut ser = serde_json::Serializer::pretty(out);
    Document { graphs }
        .serialize(&mut ser)
        .map_err(|e| Rdf2JsonError::Other(format!("Error writing JSON: {e}")))
}

struct Document<'a> {
    graphs: &'a [OntologyGraph],
}

impl Serialize for Document<'_> {
    fn serialize<S: Serializer>(&self, s: S) -> Result<S::Ok, S::Error> {
        let mut m = s.serialize_map(Some(1))?;
        m.serialize_entry("ontologies", &OntologiesSeq { graphs: self.graphs })?;
        m.end()
    }
}

struct OntologiesSeq<'a> {
    graphs: &'a [OntologyGraph],
}

impl Serialize for OntologiesSeq<'_> {
    fn serialize<S: Serializer>(&self, s: S) -> Result<S::Ok, S::Error> {
        let mut seq = s.serialize_seq(Some(self.graphs.len()))?;
        for g in self.graphs {
            seq.serialize_element(&Ontology { graph: g })?;
        }
        seq.end()
    }
}

struct Ontology<'a> {
    graph: &'a OntologyGraph,
}

impl Serialize for Ontology<'_> {
    fn serialize<S: Serializer>(&self, s: S) -> Result<S::Ok, S::Error> {
        let graph = self.graph;
        let config = &graph.config;
        let ontology_id = config
            .get("id")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_lowercase();
        let onto_node = graph.ontology_node().expect("ontology node present");

        let mut m = s.serialize_map(None)?;
        m.serialize_entry("ontologyId", &ontology_id)?;
        m.serialize_entry("iri", &onto_node.uri.clone().unwrap_or_default())?;

        for (key, val) in config_iter_for_output(config) {
            m.serialize_entry(&key, &val)?;
        }
        if matches!(config.get("is_deprecated"), Some(Value::Bool(true))) {
            m.serialize_entry("is_deprecated", &true)?;
        }

        for (key, val) in property_entries(graph, &onto_node.properties, Some(ONTOLOGY_TYPES)) {
            m.serialize_entry(&key, &val)?;
        }

        m.serialize_entry("classes", &EntitiesSeq { graph, kind: EntityKind::Classes })?;
        m.serialize_entry("properties", &EntitiesSeq { graph, kind: EntityKind::Properties })?;
        m.serialize_entry("individuals", &EntitiesSeq { graph, kind: EntityKind::Individuals })?;

        m.end()
    }
}

#[derive(Clone, Copy)]
enum EntityKind {
    Classes,
    Properties,
    Individuals,
}

struct EntitiesSeq<'a> {
    graph: &'a OntologyGraph,
    kind: EntityKind,
}

impl Serialize for EntitiesSeq<'_> {
    fn serialize<S: Serializer>(&self, s: S) -> Result<S::Ok, S::Error> {
        let mut seq = s.serialize_seq(None)?;
        for node in self.graph.nodes.values() {
            if node.uri.is_none() {
                continue;
            }
            let types = match self.kind {
                EntityKind::Classes => node.has_type(NodeType::Class).then_some(CLASS_TYPES),
                EntityKind::Properties => {
                    if node.has_type(NodeType::ObjectProperty) {
                        Some(OBJECT_PROPERTY_TYPES)
                    } else if node.has_type(NodeType::AnnotationProperty) {
                        Some(ANNOTATION_PROPERTY_TYPES)
                    } else if node.has_type(NodeType::DataProperty) {
                        Some(DATA_PROPERTY_TYPES)
                    } else if node.has_type(NodeType::Property) {
                        Some(PROPERTY_TYPES)
                    } else {
                        None
                    }
                }
                EntityKind::Individuals => {
                    node.has_type(NodeType::Individual).then_some(INDIVIDUAL_TYPES)
                }
            };
            if let Some(types) = types {
                seq.serialize_element(&node_to_value(self.graph, node, Some(types)))?;
            }
        }
        seq.end()
    }
}

fn config_iter_for_output(config: &OntologyConfig) -> Vec<(String, Value)> {
    let mut out = Vec::new();
    for (key, val) in config.iter() {
        if key == "id" || key == "ontologyId" || key == "iri" {
            continue;
        }
        if key == "preferred_root_term" || key == "is_deprecated" {
            continue;
        }
        let (out_key, out_val) = if key == "base_uri" {
            let v = if val.is_array() {
                val.clone()
            } else {
                Value::Array(vec![val.clone()])
            };
            ("baseUri".to_string(), v)
        } else if key.eq_ignore_ascii_case("ontology_purl") {
            ("ontologyPurl".to_string(), val.clone())
        } else if key.eq_ignore_ascii_case("mailing_list") {
            ("mailingList".to_string(), val.clone())
        } else {
            (key.clone(), val.clone())
        };
        out.push((out_key, out_val));
    }
    out
}

/// Java `writeNode`: an object (iri + properties) or, for RDF list nodes, an
/// array of the list's values. Builds a (small) `Value` for one node.
fn node_to_value(graph: &OntologyGraph, node: &OntologyNode, types: Option<&[&str]>) -> Value {
    if node.has_type(NodeType::RdfList) {
        let arr = graph
            .evaluate_rdf_list(node)
            .iter()
            .map(|entry| property_value_to_value(graph, entry))
            .collect();
        Value::Array(arr)
    } else {
        let mut obj = Obj::new();
        if let Some(uri) = &node.uri {
            obj.insert("iri".to_string(), Value::String(uri.clone()));
        }
        for (key, val) in property_entries(graph, &node.properties, types) {
            obj.insert(key, val);
        }
        Value::Object(obj)
    }
}

/// Java `writeProperties`: ordered (key, value) entries — `type` (if present)
/// then predicates in sorted order.
fn property_entries(
    graph: &OntologyGraph,
    properties: &PropertySet,
    types: Option<&[&str]>,
) -> Vec<(String, Value)> {
    let mut entries = Vec::new();
    if let Some(types) = types {
        entries.push(("type".to_string(), type_array(types)));
    }
    let is_ontology = types.map(|t| t.contains(&"ontology")).unwrap_or(false);

    for predicate in properties.predicates() {
        if is_ontology && predicate == "ontologyId" {
            continue;
        }
        let values = properties.get_property_values(predicate).unwrap();
        let value = if values.len() == 1 && values[0].is_list() {
            let elements = values[0].as_list().unwrap();
            Value::Array(sorted(
                elements
                    .iter()
                    .map(|e| property_value_to_value(graph, e))
                    .collect(),
            ))
        } else if values.len() == 1 {
            property_value_to_value(graph, &values[0])
        } else {
            Value::Array(sorted(
                values
                    .iter()
                    .map(|v| property_value_to_value(graph, v))
                    .collect(),
            ))
        };
        entries.push((predicate.clone(), value));
    }
    entries
}

/// Java `writePropertyValue` (reification wrapper).
fn property_value_to_value(graph: &OntologyGraph, value: &PropertyValue) -> Value {
    let axioms = value.axioms.borrow();
    if axioms.is_empty() {
        return value_to_value(graph, value);
    }
    let mut obj = Obj::new();
    obj.insert("type".to_string(), type_array(&["reification"]));
    obj.insert("value".to_string(), value_to_value(graph, value));
    let axiom_values = sorted(axioms.iter().map(|a| axiom_to_value(graph, a)).collect());
    obj.insert("axioms".to_string(), Value::Array(axiom_values));
    Value::Object(obj)
}

fn axiom_to_value(graph: &OntologyGraph, axiom: &PropertySet) -> Value {
    let mut obj = Obj::new();
    for (key, val) in property_entries(graph, axiom, None) {
        obj.insert(key, val);
    }
    Value::Object(obj)
}

/// Java `writeValue`.
fn value_to_value(graph: &OntologyGraph, value: &PropertyValue) -> Value {
    match &value.kind {
        PVKind::Bnode(id) => match graph.nodes.get(id) {
            None => Value::String(String::new()),
            Some(node) => node_to_value(graph, node, None),
        },
        PVKind::Literal(lit) => literal_to_value(lit),
        PVKind::Uri(uri) => match graph.nodes.get(uri) {
            Some(node) if !is_xml_builtin_datatype(uri) && node.has_type(NodeType::Datatype) => {
                node_to_value(graph, node, Some(&["datatype"]))
            }
            _ => Value::String(uri.clone()),
        },
        PVKind::Related {
            class_expr_id,
            property,
            filler_uri,
        } => {
            let mut obj = Obj::new();
            obj.insert("property".to_string(), Value::String(property.clone()));
            obj.insert("value".to_string(), Value::String(filler_uri.clone()));
            if let Some(ce) = graph.nodes.get(class_expr_id) {
                for (key, val) in property_entries(graph, &ce.properties, Some(&["related"])) {
                    obj.insert(key, val);
                }
            }
            Value::Object(obj)
        }
        PVKind::Ancestors {
            node_id,
            hierarchy_predicate,
        } => {
            let ancestors = match graph.nodes.get(node_id) {
                Some(n) => graph.ancestors_closure(n, hierarchy_predicate),
                None => Default::default(),
            };
            Value::Array(ancestors.into_iter().map(Value::String).collect())
        }
        PVKind::List(items) => {
            Value::Array(items.iter().map(|pv| value_to_value(graph, pv)).collect())
        }
    }
}

fn literal_to_value(lit: &Literal) -> Value {
    let datatype = match &lit.datatype {
        None => return Value::Null, // never happens for parsed literals
        Some(d) => d.as_str(),
    };
    match datatype {
        XSD_BOOLEAN => Value::Bool(lit.value.eq_ignore_ascii_case("true")),
        "http://www.w3.org/2001/XMLSchema#double" => lit
            .value
            .parse::<f64>()
            .ok()
            .and_then(serde_json::Number::from_f64)
            .map(Value::Number)
            .unwrap_or(Value::Null),
        XSD_INTEGER => match lit.value.parse::<i32>() {
            Ok(i) => Value::Number(i.into()),
            Err(_) => lit
                .value
                .parse::<f64>()
                .ok()
                .and_then(serde_json::Number::from_f64)
                .map(Value::Number)
                .unwrap_or(Value::Null),
        },
        XSD_STRING => {
            let mut obj = Obj::new();
            obj.insert("type".to_string(), type_array(&["literal"]));
            obj.insert("value".to_string(), Value::String(lit.value.clone()));
            if !lit.lang.is_empty() {
                obj.insert("lang".to_string(), Value::String(lit.lang.clone()));
            }
            Value::Object(obj)
        }
        _ => {
            let mut obj = Obj::new();
            obj.insert("type".to_string(), type_array(&["literal"]));
            obj.insert("datatype".to_string(), Value::String(datatype.to_string()));
            obj.insert("value".to_string(), Value::String(lit.value.clone()));
            if !lit.lang.is_empty() {
                obj.insert("lang".to_string(), Value::String(lit.lang.clone()));
            }
            Value::Object(obj)
        }
    }
}
