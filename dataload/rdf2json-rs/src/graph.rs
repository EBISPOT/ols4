//! The ontology graph: builds a node-per-subject model from RDF triples,
//! ported from the Java `OntologyGraph` (which implements Jena's `StreamRDF`).

use std::collections::{BTreeMap, BTreeSet};

use oxrdfio::{RdfFormat, RdfParser};

use crate::config::OntologyConfig;
use crate::error::Rdf2JsonError;
use crate::fetch::{self, SourceData};
use crate::model::*;
use crate::validate_language::validate_language;

/// A single subject/predicate/object position of a raw RDF triple. Kept local
/// (built directly on oxrdfio) so the crate does not depend on any addition to
/// the horned-owl fork — horned-owl is used only via its high-level OWL reader.
enum RawNode {
    Iri(String),
    BlankNode(String),
    Literal {
        value: String,
        datatype: String,
        lang: String,
    },
}

/// Read all triples from `bytes` in the given RDF `format` (the non-OWL fallback
/// reader). Language-tagged literals report `rdf:langString`, plain literals
/// their datatype — matching the RDF 1.1 abstract syntax.
fn read_raw_triples(bytes: &[u8], format: RdfFormat) -> Result<Vec<[RawNode; 3]>, Rdf2JsonError> {
    let parser = RdfParser::from_format(format);
    let mut triples = Vec::new();
    for quad in parser.for_reader(bytes) {
        let q = quad.map_err(|e| Rdf2JsonError::Parse(format!("Error parsing RDF: {e}")))?;
        let t: oxrdf::Triple = q.into();
        triples.push([
            raw_from_subject(&t.subject),
            RawNode::Iri(t.predicate.as_str().to_string()),
            raw_from_object(&t.object),
        ]);
    }
    Ok(triples)
}

fn raw_from_subject(s: &oxrdf::NamedOrBlankNode) -> RawNode {
    match s {
        oxrdf::NamedOrBlankNode::NamedNode(nn) => RawNode::Iri(nn.as_str().to_string()),
        oxrdf::NamedOrBlankNode::BlankNode(bn) => RawNode::BlankNode(bn.as_str().to_string()),
    }
}

fn raw_from_object(o: &oxrdf::Term) -> RawNode {
    match o {
        oxrdf::Term::NamedNode(nn) => RawNode::Iri(nn.as_str().to_string()),
        oxrdf::Term::BlankNode(bn) => RawNode::BlankNode(bn.as_str().to_string()),
        oxrdf::Term::Literal(l) => {
            if let Some(lang) = l.language() {
                RawNode::Literal {
                    value: l.value().to_string(),
                    datatype: RDF_LANG_STRING.to_string(),
                    lang: lang.to_string(),
                }
            } else {
                RawNode::Literal {
                    value: l.value().to_string(),
                    datatype: l.datatype().as_str().to_string(),
                    lang: String::new(),
                }
            }
        }
    }
}

pub struct Options {
    pub load_local_files: bool,
    pub base_path: Option<String>,
    pub no_dates: bool,
    pub downloaded_path: Option<String>,
}

pub struct OntologyGraph {
    pub config: OntologyConfig,
    pub nodes: BTreeMap<String, OntologyNode>,
    pub ontology_node_id: Option<String>,
    pub import_urls: Vec<String>,
    pub languages: BTreeSet<String>,
    pub source_file_timestamp_millis: i64,

    pub number_of_classes: i64,
    pub number_of_properties: i64,
    pub number_of_individuals: i64,

    opts: Options,
}

impl OntologyGraph {
    fn new(config: OntologyConfig, opts: Options) -> OntologyGraph {
        let mut languages = BTreeSet::new();
        languages.insert("en".to_string());
        OntologyGraph {
            config,
            nodes: BTreeMap::new(),
            ontology_node_id: None,
            import_urls: Vec::new(),
            languages,
            source_file_timestamp_millis: 0,
            number_of_classes: 0,
            number_of_properties: 0,
            number_of_individuals: 0,
            opts,
        }
    }

    // ---- Triple ingestion (Java: triple/handleLiteralTriple/handleNamedNodeTriple) ----

    fn ensure_node(&mut self, id: &str, is_blank: bool) {
        let node = self.nodes.entry(id.to_string()).or_default();
        if !is_blank && node.uri.is_none() {
            node.uri = Some(id.to_string());
        }
    }

    fn ingest_triple(&mut self, triple: [RawNode; 3]) {
        let [subject, predicate, object] = triple;

        let (subj_id, subj_blank) = match subject {
            RawNode::Iri(u) => (u, false),
            RawNode::BlankNode(b) => (b, true),
            RawNode::Literal { .. } => return, // literal subjects are impossible
        };
        let predicate = match predicate {
            RawNode::Iri(p) => p,
            _ => return,
        };

        self.ensure_node(&subj_id, subj_blank);

        match object {
            RawNode::Literal {
                value,
                datatype,
                lang,
            } => {
                let vlang = validate_language(&lang);
                if !vlang.is_empty() {
                    self.languages.insert(vlang.clone());
                }
                let pv = PropertyValue::literal(Literal {
                    value,
                    datatype: Some(datatype),
                    lang: vlang,
                });
                self.nodes
                    .get_mut(&subj_id)
                    .unwrap()
                    .properties
                    .add_property(&predicate, pv);
            }
            RawNode::Iri(obj) => {
                match predicate.as_str() {
                    RDF_TYPE => self.handle_type(&subj_id, &obj),
                    RDF_REST | RDF_FIRST => {
                        self.nodes
                            .get_mut(&subj_id)
                            .unwrap()
                            .types
                            .insert(NodeType::RdfList);
                    }
                    OWL_IMPORTS => self.import_urls.push(obj.clone()),
                    _ => {}
                }
                self.nodes
                    .get_mut(&subj_id)
                    .unwrap()
                    .properties
                    .add_property(&predicate, PropertyValue::uri(obj));
            }
            RawNode::BlankNode(obj) => {
                if matches!(predicate.as_str(), RDF_REST | RDF_FIRST) {
                    self.nodes
                        .get_mut(&subj_id)
                        .unwrap()
                        .types
                        .insert(NodeType::RdfList);
                }
                self.nodes
                    .get_mut(&subj_id)
                    .unwrap()
                    .properties
                    .add_property(&predicate, PropertyValue::bnode(obj));
            }
        }
    }

    fn handle_type(&mut self, subj_id: &str, type_uri: &str) {
        let has_uri;
        {
            let node = self.nodes.get_mut(subj_id).unwrap();
            has_uri = node.uri.is_some();
            match type_uri {
                OWL_ONTOLOGY => {
                    node.types.insert(NodeType::Ontology);
                }
                OWL_CLASS | RDFS_CLASS | SKOS_CONCEPT => {
                    node.types.insert(NodeType::Class);
                }
                OWL_ANNOTATION_PROPERTY => {
                    node.types.insert(NodeType::AnnotationProperty);
                    node.types.insert(NodeType::Property);
                }
                OWL_OBJECT_PROPERTY => {
                    node.types.insert(NodeType::ObjectProperty);
                    node.types.insert(NodeType::Property);
                }
                OWL_DATATYPE_PROPERTY => {
                    node.types.insert(NodeType::DataProperty);
                    node.types.insert(NodeType::Property);
                }
                RDF_PROPERTY => {
                    node.types.insert(NodeType::Property);
                }
                OWL_NAMED_INDIVIDUAL => {
                    node.types.insert(NodeType::Individual);
                }
                OWL_AXIOM => {
                    node.types.insert(NodeType::Axiom);
                }
                OWL_RESTRICTION => {
                    node.types.insert(NodeType::Restriction);
                }
                OWL_ALL_DISJOINT_CLASSES => {
                    node.types.insert(NodeType::AllDisjointClasses);
                }
                OWL_ALL_DISJOINT_PROPERTIES => {
                    node.types.insert(NodeType::AllDisjointProperties);
                }
                OWL_ALL_DIFFERENT => {
                    node.types.insert(NodeType::AllDifferent);
                }
                OWL_NEGATIVE_PROPERTY_ASSERTION => {
                    node.types.insert(NodeType::NegativePropertyAssertion);
                }
                RDFS_DATATYPE => {
                    node.types.insert(NodeType::Datatype);
                }
                _ => {}
            }
        }

        match type_uri {
            OWL_ONTOLOGY => {
                if self.ontology_node_id.is_none() {
                    self.ontology_node_id = Some(subj_id.to_string());
                }
            }
            OWL_CLASS | RDFS_CLASS | SKOS_CONCEPT => {
                if has_uri {
                    self.number_of_classes += 1;
                }
            }
            OWL_ANNOTATION_PROPERTY | OWL_OBJECT_PROPERTY | OWL_DATATYPE_PROPERTY | RDF_PROPERTY => {
                if has_uri {
                    self.number_of_properties += 1;
                }
            }
            OWL_NAMED_INDIVIDUAL => {
                if has_uri {
                    self.number_of_individuals += 1;
                }
            }
            _ => {}
        }
    }

    // ---- Graph helpers used by annotators / writer ----

    pub fn ontology_node(&self) -> Option<&OntologyNode> {
        self.ontology_node_id
            .as_ref()
            .and_then(|id| self.nodes.get(id))
    }

    /// The node referenced by a URI or BNODE value, if present.
    pub fn node_for_value(&self, value: &PropertyValue) -> Option<&OntologyNode> {
        value.node_id().and_then(|id| self.nodes.get(id))
    }

    /// Java `areSubgraphsIsomorphic`: structural comparison of two bnode (or
    /// node) subgraphs reachable from the given property values.
    pub fn subgraphs_isomorphic(&self, a: &PropertyValue, b: &PropertyValue) -> bool {
        let na = match a.node_id().and_then(|id| self.nodes.get(id)) {
            Some(n) => n,
            None => return false,
        };
        let nb = match b.node_id().and_then(|id| self.nodes.get(id)) {
            Some(n) => n,
            None => return false,
        };
        self.nodes_isomorphic(na, nb)
    }

    fn nodes_isomorphic(&self, a: &OntologyNode, b: &OntologyNode) -> bool {
        let preds_a: Vec<&String> = a.properties.predicates().collect();
        let preds_b: Vec<&String> = b.properties.predicates().collect();
        if preds_a != preds_b {
            return false;
        }
        for predicate in preds_a {
            let va = a.properties.get_property_values(predicate).unwrap();
            let vb = b.properties.get_property_values(predicate).unwrap();
            if va.len() != vb.len() {
                return false;
            }
            for (value_a, value_b) in va.iter().zip(vb) {
                // Correct structural comparison (the Java version had a
                // fall-through bug that made this always fail for non-bnode
                // values, duplicating annotated bnode edges; we fix it here so an
                // annotated bnode restriction merges into a single reified edge).
                match (&value_a.kind, &value_b.kind) {
                    (PVKind::Bnode(_), PVKind::Bnode(_)) => {
                        if !self.subgraphs_isomorphic(value_a, value_b) {
                            return false;
                        }
                    }
                    (PVKind::Bnode(_), _) | (_, PVKind::Bnode(_)) => return false,
                    _ => {
                        if !value_a.value_eq(value_b) {
                            return false;
                        }
                    }
                }
            }
        }
        true
    }

    /// Java `RdfListEvaluator.evaluateRdfList`: walk an RDF list node to its
    /// values (the `rdf:first` of each cell, following `rdf:rest`).
    pub fn evaluate_rdf_list<'a>(&'a self, start: &'a OntologyNode) -> Vec<PropertyValue> {
        let mut res = Vec::new();
        let mut cur: &OntologyNode = start;
        loop {
            if let Some(first) = cur.properties.get_property_value(RDF_FIRST) {
                res.push(first.clone());
            }
            let rest = match cur.properties.get_property_value(RDF_REST) {
                Some(r) => r,
                None => break,
            };
            match &rest.kind {
                PVKind::Uri(u) if u == RDF_NIL => break,
                _ => {}
            }
            cur = match rest.node_id().and_then(|id| self.nodes.get(id)) {
                Some(n) => n,
                None => break,
            };
        }
        res
    }

    /// Recursive ancestor closure over a hierarchy predicate whose value is a
    /// `PropertyValueList` of URIs (Java `AncestorsClosure.getAncestors`).
    pub fn ancestors_closure(&self, start: &OntologyNode, predicate: &str) -> BTreeSet<String> {
        let mut acc = BTreeSet::new();
        self.ancestors_closure_rec(start, predicate, &mut acc);
        acc
    }

    fn ancestors_closure_rec(
        &self,
        node: &OntologyNode,
        predicate: &str,
        acc: &mut BTreeSet<String>,
    ) {
        if let Some(values) = node.properties.get_property_values(predicate) {
            for v in values {
                if let PVKind::List(list) = &v.kind {
                    for parent in list {
                        if let PVKind::Uri(uri) = &parent.kind {
                            if !acc.contains(uri) {
                                acc.insert(uri.clone());
                                if let Some(pn) = self.nodes.get(uri) {
                                    self.ancestors_closure_rec(pn, predicate, acc);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- Reification (Java: PropertySet.annotatePropertyWithAxiom) ----

    /// Attach `axiom` to the value `value` of `predicate` on node `source_id`,
    /// reusing an equal/isomorphic existing value where possible (else appending
    /// the value). Mirrors `annotatePropertyWithAxiom`.
    pub fn annotate_property_with_axiom(
        &mut self,
        source_id: &str,
        predicate: &str,
        value: PropertyValue,
        axiom: PropertySet,
    ) {
        if !self.nodes.contains_key(source_id) {
            return;
        }

        let target = self.find_reify_target(source_id, predicate, &value);

        let node = self.nodes.get_mut(source_id).unwrap();
        match target {
            ReifyTarget::TopLevel(idx) => {
                node.properties.get_property_values_mut(predicate).unwrap()[idx].push_axiom(axiom);
            }
            ReifyTarget::ListElement(idx, elem) => {
                let values = node.properties.get_property_values_mut(predicate).unwrap();
                if let PVKind::List(list) = &mut values[idx].kind {
                    list[elem].push_axiom(axiom);
                }
            }
            ReifyTarget::AppendNew => {
                value.push_axiom(axiom);
                node.properties.add_property_no_dedup(predicate, value);
            }
        }
    }

    fn find_reify_target(
        &self,
        source_id: &str,
        predicate: &str,
        value: &PropertyValue,
    ) -> ReifyTarget {
        let node = self.nodes.get(source_id).unwrap();
        let existing = match node.properties.get_property_values(predicate) {
            Some(v) if !v.is_empty() => v,
            _ => return ReifyTarget::AppendNew,
        };

        if matches!(value.kind, PVKind::Bnode(_)) {
            for (i, ev) in existing.iter().enumerate() {
                if matches!(ev.kind, PVKind::Bnode(_)) && self.subgraphs_isomorphic(ev, value) {
                    return ReifyTarget::TopLevel(i);
                }
            }
        } else {
            for (i, ev) in existing.iter().enumerate() {
                if let PVKind::List(list) = &ev.kind {
                    for (j, elem) in list.iter().enumerate() {
                        if elem.value_eq(value) {
                            return ReifyTarget::ListElement(i, j);
                        }
                    }
                } else if ev.value_eq(value) {
                    return ReifyTarget::TopLevel(i);
                }
            }
        }
        ReifyTarget::AppendNew
    }

    // ---- Loading ----

    pub fn load(
        config: OntologyConfig,
        opts: Options,
    ) -> Result<OntologyGraph, Rdf2JsonError> {
        let mut graph = OntologyGraph::new(config, opts);

        let url = graph.resolve_ontology_url();
        let url = match url {
            Some(u) => u,
            None => {
                return Err(Rdf2JsonError::Other(
                    "Could not determine URL for ontology".to_string(),
                ));
            }
        };

        graph.parse_rdf(&url)?;

        // Mark everything parsed so far as not imported.
        for node in graph.nodes.values_mut() {
            if node.uri.is_some() {
                node.properties
                    .add_property("imported", PropertyValue::literal(Literal::from_boolean(false)));
            }
        }

        // Resolve imports (FIFO, may append more).
        while !graph.import_urls.is_empty() {
            let import_url = graph.import_urls.remove(0);
            // Imports that fail should not abort the whole ontology.
            if let Err(e) = graph.parse_rdf(&import_url) {
                eprintln!("Warning: failed to parse import {import_url}: {e}");
            }
        }

        // Everything added by imports (no `imported` yet) is imported = true.
        for node in graph.nodes.values_mut() {
            if node.uri.is_some() && !node.properties.has_property("imported") {
                node.properties
                    .add_property("imported", PropertyValue::literal(Literal::from_boolean(true)));
            }
        }

        graph.resolve_ontology_node_fallbacks();

        if graph.ontology_node_id.is_none() {
            // Nothing we can do; caller treats this as "no ontology node".
            return Ok(graph);
        }

        graph.add_ontology_metadata_counts();

        Ok(graph)
    }

    /// Java: resolve `ontology_purl`, falling back to a `.owl` product.
    fn resolve_ontology_url(&self) -> Option<String> {
        use crate::config::ConfigExt;
        if let Some(purl) = self.config.get_str("ontology_purl") {
            return Some(purl.to_string());
        }
        if let Some(products) = self.config.get("products").and_then(|v| v.as_array()) {
            for product in products {
                if let Some(purl) = product.get("ontology_purl").and_then(|v| v.as_str()) {
                    if purl.ends_with(".owl") {
                        return Some(purl.to_string());
                    }
                }
            }
        }
        None
    }

    fn parse_rdf(&mut self, url: &str) -> Result<(), Rdf2JsonError> {
        let source = fetch::fetch_source(
            url,
            self.opts.load_local_files,
            self.opts.base_path.as_deref(),
            self.opts.downloaded_path.as_deref(),
        )?;
        self.source_file_timestamp_millis = source.timestamp_millis;
        self.ingest_source(source)
    }

    fn ingest_source(&mut self, source: SourceData) -> Result<(), Rdf2JsonError> {
        let SourceData {
            mut bytes,
            mut effective_url,
            content_type,
            ..
        } = source;

        if effective_url.ends_with(".gz") {
            bytes = fetch::gunzip(&bytes)?;
            effective_url.truncate(effective_url.len() - 3);
        }

        fetch::detect_error_xml(&bytes, &effective_url)?;

        let format = fetch::determine_format(&effective_url, content_type.as_deref());

        let triples = read_raw_triples(&bytes, format)?;
        for triple in triples {
            self.ingest_triple(triple);
        }
        Ok(())
    }

    fn resolve_ontology_node_fallbacks(&mut self) {
        use crate::config::ConfigExt;
        if self.ontology_node_id.is_some() {
            return;
        }

        // Fallback 1: a single node without an rdf:type.
        let no_type: Vec<String> = self
            .nodes
            .iter()
            .filter(|(_, n)| n.uri.is_some() && !n.properties.has_property(RDF_TYPE))
            .map(|(id, _)| id.clone())
            .collect();
        if no_type.len() == 1 {
            self.ontology_node_id = Some(no_type.into_iter().next().unwrap());
            return;
        }

        // Fallback 2: fabricate from base_uri.
        if let Some(base_uris) = self.config.get_str_array("base_uri") {
            if let Some(first) = base_uris.into_iter().next() {
                self.fabricate_ontology_node(first);
                return;
            }
        }

        // Fallback 3: fabricate from purl.
        if let Some(purl) = self.config.get_str("ontology_purl").map(|s| s.to_string()) {
            self.fabricate_ontology_node(purl);
        }
    }

    fn fabricate_ontology_node(&mut self, iri: String) {
        let mut node = OntologyNode::new();
        node.uri = Some(iri.clone());
        node.types.insert(NodeType::Ontology);
        self.nodes.insert(iri.clone(), node);
        self.ontology_node_id = Some(iri);
    }

    fn add_ontology_metadata_counts(&mut self) {
        let total = self.number_of_classes + self.number_of_properties + self.number_of_individuals;
        let n_classes = self.number_of_classes;
        let n_props = self.number_of_properties;
        let n_individuals = self.number_of_individuals;
        let languages: Vec<PropertyValue> = self
            .languages
            .iter()
            .map(|l| PropertyValue::literal(Literal::from_string(l.clone())))
            .collect();
        let no_dates = self.opts.no_dates;
        let ts = self.source_file_timestamp_millis;

        let onto = self
            .nodes
            .get_mut(self.ontology_node_id.as_ref().unwrap())
            .unwrap();
        let props = &mut onto.properties;
        props.add_property(
            "numberOfEntities",
            PropertyValue::literal(Literal::from_string(total.to_string())),
        );
        props.add_property(
            "numberOfClasses",
            PropertyValue::literal(Literal::from_string(n_classes.to_string())),
        );
        props.add_property(
            "numberOfProperties",
            PropertyValue::literal(Literal::from_string(n_props.to_string())),
        );
        props.add_property(
            "numberOfIndividuals",
            PropertyValue::literal(Literal::from_string(n_individuals.to_string())),
        );

        if !no_dates {
            let now = fetch::now_local_datetime_string();
            props.add_property("loaded", PropertyValue::literal(Literal::from_string(now)));
            props.add_property(
                "sourceFileTimestamp",
                PropertyValue::literal(Literal::from_string(fetch::date_to_string(ts))),
            );
        }

        props.add_property("language", PropertyValue::list(languages));
    }
}

enum ReifyTarget {
    TopLevel(usize),
    ListElement(usize, usize),
    AppendNew,
}
