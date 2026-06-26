//! Core data model for the RDF triple graph: ported from the Java classes
//! `OntologyNode`, `PropertySet`, and the `PropertyValue*` hierarchy.
//!
//! The graph is a node-per-subject model keyed by IRI (named nodes) or blank
//! node id. Each node has a sorted set of types and a `PropertySet` mapping
//! predicate IRIs to ordered, de-duplicated lists of values. The structure and
//! de-duplication semantics mirror the original Java exactly so that the JSON
//! serialization can be reproduced byte-for-byte.

// Faithful port of the Java model: some parity/accessor methods and NodeType
// variants are part of the intended model API but are not exercised by every
// code path, so suppress dead-code warnings for the module as a whole.
#![allow(dead_code)]

use std::cell::RefCell;
use std::collections::{BTreeMap, BTreeSet};
use std::rc::Rc;

/// xsd / rdf datatype IRIs used throughout.
pub const XSD_STRING: &str = "http://www.w3.org/2001/XMLSchema#string";
pub const XSD_BOOLEAN: &str = "http://www.w3.org/2001/XMLSchema#boolean";
pub const XSD_INTEGER: &str = "http://www.w3.org/2001/XMLSchema#integer";
pub const RDF_LANG_STRING: &str = "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString";

pub const RDF_TYPE: &str = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
pub const RDF_FIRST: &str = "http://www.w3.org/1999/02/22-rdf-syntax-ns#first";
pub const RDF_REST: &str = "http://www.w3.org/1999/02/22-rdf-syntax-ns#rest";
pub const RDF_NIL: &str = "http://www.w3.org/1999/02/22-rdf-syntax-ns#nil";
pub const RDF_PROPERTY: &str = "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property";
pub const OWL_IMPORTS: &str = "http://www.w3.org/2002/07/owl#imports";

// rdf:type object IRIs recognised by `handle_type`.
pub const OWL_ONTOLOGY: &str = "http://www.w3.org/2002/07/owl#Ontology";
pub const OWL_CLASS: &str = "http://www.w3.org/2002/07/owl#Class";
pub const RDFS_CLASS: &str = "http://www.w3.org/2000/01/rdf-schema#Class";
pub const SKOS_CONCEPT: &str = "http://www.w3.org/2004/02/skos/core#Concept";
pub const OWL_ANNOTATION_PROPERTY: &str = "http://www.w3.org/2002/07/owl#AnnotationProperty";
pub const OWL_OBJECT_PROPERTY: &str = "http://www.w3.org/2002/07/owl#ObjectProperty";
pub const OWL_DATATYPE_PROPERTY: &str = "http://www.w3.org/2002/07/owl#DatatypeProperty";
pub const OWL_NAMED_INDIVIDUAL: &str = "http://www.w3.org/2002/07/owl#NamedIndividual";
pub const OWL_AXIOM: &str = "http://www.w3.org/2002/07/owl#Axiom";
pub const OWL_RESTRICTION: &str = "http://www.w3.org/2002/07/owl#Restriction";
pub const OWL_ALL_DISJOINT_CLASSES: &str = "http://www.w3.org/2002/07/owl#AllDisjointClasses";
pub const OWL_ALL_DISJOINT_PROPERTIES: &str = "http://www.w3.org/2002/07/owl#AllDisjointProperties";
pub const OWL_ALL_DIFFERENT: &str = "http://www.w3.org/2002/07/owl#AllDifferent";
pub const OWL_NEGATIVE_PROPERTY_ASSERTION: &str =
    "http://www.w3.org/2002/07/owl#NegativePropertyAssertion";
pub const RDFS_DATATYPE: &str = "http://www.w3.org/2000/01/rdf-schema#Datatype";

/// Node types. Order of variants matters: `BTreeSet<NodeType>` is serialized in
/// this declaration order, and the Java `TreeSet<NodeType>` orders by the enum's
/// natural (declaration) ordering — so this must match the Java enum order.
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum NodeType {
    Ontology,
    Entity,
    Class,
    Property,
    Individual,
    AnnotationProperty,
    ObjectProperty,
    DataProperty,
    Axiom,
    Restriction,
    RdfList,
    AllDisjointClasses,
    AllDifferent,
    AllDisjointProperties,
    NegativePropertyAssertion,
    Datatype,
}

impl NodeType {
    /// The lowercase camelCase name used in the JSON `type` array.
    pub fn name(self) -> &'static str {
        match self {
            NodeType::Ontology => "ontology",
            NodeType::Entity => "entity",
            NodeType::Class => "class",
            NodeType::Property => "property",
            NodeType::Individual => "individual",
            NodeType::AnnotationProperty => "annotationProperty",
            NodeType::ObjectProperty => "objectProperty",
            NodeType::DataProperty => "dataProperty",
            NodeType::Axiom => "axiom",
            NodeType::Restriction => "restriction",
            NodeType::RdfList => "rdfList",
            NodeType::AllDisjointClasses => "allDisjointClasses",
            NodeType::AllDifferent => "allDifferent",
            NodeType::AllDisjointProperties => "allDisjointProperties",
            NodeType::NegativePropertyAssertion => "negativePropertyAssertion",
            NodeType::Datatype => "datatype",
        }
    }

    /// Build the sorted set of type names (matching `NodeType.toString(Set)` in
    /// Java, which collects into a `TreeSet<String>` — i.e. sorted by the *name*
    /// string, not the enum order).
    pub fn names_sorted(types: &[NodeType]) -> BTreeSet<String> {
        types.iter().map(|t| t.name().to_string()).collect()
    }
}

/// A literal value: lexical form, optional datatype IRI and language tag.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Literal {
    pub value: String,
    pub datatype: Option<String>,
    pub lang: String,
}

impl Literal {
    pub fn from_string(s: impl Into<String>) -> Literal {
        Literal {
            value: s.into(),
            datatype: Some(XSD_STRING.to_string()),
            lang: String::new(),
        }
    }
    pub fn from_boolean(b: bool) -> Literal {
        Literal {
            value: if b { "true" } else { "false" }.to_string(),
            datatype: Some(XSD_BOOLEAN.to_string()),
            lang: String::new(),
        }
    }
    pub fn from_integer(s: impl Into<String>) -> Literal {
        Literal {
            value: s.into(),
            datatype: Some(XSD_INTEGER.to_string()),
            lang: String::new(),
        }
    }
}

/// A value attached to a predicate. Mirrors the Java `PropertyValue` hierarchy.
/// `axioms` carries reification (OWL axiom annotations) and is excluded from
/// equality, exactly as in Java where `equals(PropertyValue)` ignores axioms.
///
/// In the Java code, derived fields (e.g. `directParent`, `hierarchicalParent`,
/// `definition`, `label`) reuse the *same* `PropertyValue` object as the source
/// predicate, so reifying one reifies all. We replicate that aliasing by sharing
/// the `axioms` list behind `Rc<RefCell<…>>`: cloning a value into a derived list
/// shares its axioms, and reifying the value is visible through every clone.
#[derive(Clone, Debug)]
pub struct PropertyValue {
    pub kind: PVKind,
    pub axioms: Rc<RefCell<Vec<PropertySet>>>,
}

#[derive(Clone, Debug)]
pub enum PVKind {
    Literal(Literal),
    Uri(String),
    Bnode(String),
    List(Vec<PropertyValue>),
    /// A `relatedTo` value (class expression rendered as property+filler).
    /// `class_expr_id` is the bnode id of the restriction/class-expression node
    /// whose raw properties are inlined; `filler_uri` is the target entity IRI.
    Related {
        class_expr_id: String,
        property: String,
        filler_uri: String,
    },
    /// Lazily-evaluated ancestor closure (kept lazy to avoid RAM blow-up on big
    /// hierarchical ontologies, matching `PropertyValueAncestors`).
    Ancestors {
        node_id: String,
        hierarchy_predicate: String,
    },
}

impl PropertyValue {
    pub fn new(kind: PVKind) -> PropertyValue {
        PropertyValue {
            kind,
            axioms: Rc::new(RefCell::new(Vec::new())),
        }
    }

    pub fn has_axioms(&self) -> bool {
        !self.axioms.borrow().is_empty()
    }

    pub fn push_axiom(&self, axiom: PropertySet) {
        self.axioms.borrow_mut().push(axiom);
    }
    pub fn literal(l: Literal) -> PropertyValue {
        PropertyValue::new(PVKind::Literal(l))
    }
    pub fn uri(s: impl Into<String>) -> PropertyValue {
        PropertyValue::new(PVKind::Uri(s.into()))
    }
    pub fn bnode(s: impl Into<String>) -> PropertyValue {
        PropertyValue::new(PVKind::Bnode(s.into()))
    }
    pub fn list(v: Vec<PropertyValue>) -> PropertyValue {
        PropertyValue::new(PVKind::List(v))
    }

    pub fn as_uri(&self) -> Option<&str> {
        match &self.kind {
            PVKind::Uri(u) => Some(u),
            _ => None,
        }
    }
    pub fn as_bnode(&self) -> Option<&str> {
        match &self.kind {
            PVKind::Bnode(b) => Some(b),
            _ => None,
        }
    }
    pub fn as_literal(&self) -> Option<&Literal> {
        match &self.kind {
            PVKind::Literal(l) => Some(l),
            _ => None,
        }
    }
    pub fn as_list(&self) -> Option<&Vec<PropertyValue>> {
        match &self.kind {
            PVKind::List(l) => Some(l),
            _ => None,
        }
    }
    pub fn is_list(&self) -> bool {
        matches!(self.kind, PVKind::List(_))
    }

    /// The node id (IRI or bnode id) referenced by a URI/BNODE value.
    pub fn node_id(&self) -> Option<&str> {
        match &self.kind {
            PVKind::Uri(u) => Some(u),
            PVKind::Bnode(b) => Some(b),
            _ => None,
        }
    }

    /// Value equality matching Java `PropertyValue.equals(PropertyValue)`
    /// (ignores `axioms`). Cross-node identity for Related/Ancestors is
    /// approximated by their stored fields, which suffices for the cases the
    /// original code actually compares.
    pub fn value_eq(&self, other: &PropertyValue) -> bool {
        match (&self.kind, &other.kind) {
            (PVKind::Literal(a), PVKind::Literal(b)) => a == b,
            (PVKind::Uri(a), PVKind::Uri(b)) => a == b,
            (PVKind::Bnode(a), PVKind::Bnode(b)) => a == b,
            (PVKind::List(a), PVKind::List(b)) => {
                a.len() == b.len() && a.iter().zip(b).all(|(x, y)| x.value_eq(y))
            }
            (
                PVKind::Related {
                    class_expr_id: c1,
                    property: p1,
                    filler_uri: f1,
                },
                PVKind::Related {
                    class_expr_id: c2,
                    property: p2,
                    filler_uri: f2,
                },
            ) => c1 == c2 && p1 == p2 && f1 == f2,
            (
                PVKind::Ancestors {
                    node_id: n1,
                    hierarchy_predicate: h1,
                },
                PVKind::Ancestors {
                    node_id: n2,
                    hierarchy_predicate: h2,
                },
            ) => n1 == n2 && h1 == h2,
            _ => false,
        }
    }
}

/// Ordered map of predicate IRI -> de-duplicated value list. Mirrors the Java
/// `PropertySet` (a `TreeMap<String, List<PropertyValue>>`).
#[derive(Clone, Debug, Default)]
pub struct PropertySet {
    properties: BTreeMap<String, Vec<PropertyValue>>,
}

impl PropertySet {
    pub fn new() -> PropertySet {
        PropertySet::default()
    }

    /// Add a value, de-duplicating against existing values of the same predicate
    /// (so the same triple appearing in multiple files doesn't duplicate).
    pub fn add_property(&mut self, predicate: &str, value: PropertyValue) {
        let props = self.properties.entry(predicate.to_string()).or_default();
        for p in props.iter() {
            if p.value_eq(&value) {
                return;
            }
        }
        props.push(value);
    }

    /// Append a value without de-duplication (matches the direct `props.add`
    /// used in `annotatePropertyWithAxiom`'s "no existing match" branch).
    pub fn add_property_no_dedup(&mut self, predicate: &str, value: PropertyValue) {
        self.properties
            .entry(predicate.to_string())
            .or_default()
            .push(value);
    }

    pub fn has_property(&self, predicate: &str) -> bool {
        self.properties.contains_key(predicate)
    }

    pub fn get_property_values(&self, predicate: &str) -> Option<&Vec<PropertyValue>> {
        self.properties.get(predicate)
    }

    pub fn get_property_values_mut(&mut self, predicate: &str) -> Option<&mut Vec<PropertyValue>> {
        self.properties.get_mut(predicate)
    }

    /// First value of a predicate, if any (matching `getPropertyValue`).
    pub fn get_property_value(&self, predicate: &str) -> Option<&PropertyValue> {
        self.properties.get(predicate).and_then(|v| v.first())
    }

    pub fn predicates(&self) -> impl Iterator<Item = &String> {
        self.properties.keys()
    }

    pub fn remove_property(&mut self, predicate: &str) {
        self.properties.remove(predicate);
    }
}

/// A node in the RDF graph (one per subject). `uri` is `None` for blank nodes.
#[derive(Clone, Debug)]
pub struct OntologyNode {
    pub uri: Option<String>,
    pub types: BTreeSet<NodeType>,
    pub properties: PropertySet,
}

impl OntologyNode {
    pub fn new() -> OntologyNode {
        OntologyNode {
            uri: None,
            types: BTreeSet::new(),
            properties: PropertySet::new(),
        }
    }

    pub fn has_type(&self, t: NodeType) -> bool {
        self.types.contains(&t)
    }

    /// Java `OntologyNode.equals`: equal iff uri and types are equal.
    pub fn node_eq(&self, other: &OntologyNode) -> bool {
        self.uri == other.uri && self.types == other.types
    }
}

impl Default for OntologyNode {
    fn default() -> Self {
        OntologyNode::new()
    }
}
