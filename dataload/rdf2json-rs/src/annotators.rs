//! All ~22 derived-property annotators, ported one-to-one from the Java
//! `uk.ac.ebi.rdf2json.annotators.*` classes. They run in the exact order the
//! Java `OntologyGraph` constructor invokes them.

use std::collections::{BTreeSet, HashSet};

use serde_json::Value;

use crate::config::{ConfigExt, OntologyConfig};
use crate::graph::OntologyGraph;
use crate::model::*;

// DefinedFields text values.
const DIRECT_PARENT: &str = "directParent";
const HIERARCHICAL_PARENT: &str = "hierarchicalParent";
const DIRECT_ANCESTOR: &str = "directAncestor";
const HIERARCHICAL_ANCESTOR: &str = "hierarchicalAncestor";
const NUM_DESCENDANTS: &str = "numDescendants";
const NUM_HIERARCHICAL_DESCENDANTS: &str = "numHierarchicalDescendants";
const RELATED_TO: &str = "relatedTo";
const IS_OBSOLETE: &str = "isObsolete";
const IS_PREFERRED_ROOT: &str = "isPreferredRoot";
const PREFERRED_ROOT: &str = "preferredRoot";
const LABEL: &str = "label";
const SYNONYM: &str = "synonym";
const DEFINITION: &str = "definition";
const HAS_DIRECT_PARENTS: &str = "hasDirectParents";
const HAS_HIERARCHICAL_PARENTS: &str = "hasHierarchicalParents";
const HAS_DIRECT_CHILDREN: &str = "hasDirectChildren";
const HAS_HIERARCHICAL_CHILDREN: &str = "hasHierarchicalChildren";
const HAS_INDIVIDUALS: &str = "hasIndividuals";

const RDFS_SUBCLASSOF: &str = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
const RDFS_SUBPROPERTYOF: &str = "http://www.w3.org/2000/01/rdf-schema#subPropertyOf";
const RDFS_LABEL: &str = "http://www.w3.org/2000/01/rdf-schema#label";
const OWL_THING: &str = "http://www.w3.org/2002/07/owl#Thing";
const OWL_TOP_OBJECT_PROPERTY: &str = "http://www.w3.org/2002/07/owl#TopObjectProperty";
const OWL_DEPRECATED: &str = "http://www.w3.org/2002/07/owl#deprecated";
const OWL_INVERSE_OF: &str = "http://www.w3.org/2002/07/owl#inverseOf";
const OWL_EQUIVALENT_CLASS: &str = "http://www.w3.org/2002/07/owl#equivalentClass";
const OWL_EQUIVALENT_PROPERTY: &str = "http://www.w3.org/2002/07/owl#equivalentProperty";
const OWL_ONE_OF: &str = "http://www.w3.org/2002/07/owl#oneOf";
const OWL_INTERSECTION_OF: &str = "http://www.w3.org/2002/07/owl#intersectionOf";
const OWL_ON_PROPERTY: &str = "http://www.w3.org/2002/07/owl#onProperty";
const OWL_SOME_VALUES_FROM: &str = "http://www.w3.org/2002/07/owl#someValuesFrom";
const OWL_HAS_VALUE: &str = "http://www.w3.org/2002/07/owl#hasValue";
const OWL_MEMBERS: &str = "http://www.w3.org/2002/07/owl#members";
const OWL_DISTINCT_MEMBERS: &str = "http://www.w3.org/2002/07/owl#distinctMembers";
const OBO_OBSOLETE_CLASS: &str = "http://www.geneontology.org/formats/oboInOwl#ObsoleteClass";
const OBO_HAS_SYNONYM_TYPE: &str = "http://www.geneontology.org/formats/oboInOwl#hasSynonymType";

fn node_ids(graph: &OntologyGraph) -> Vec<String> {
    graph.nodes.keys().cloned().collect()
}

/// Run every annotator in the same order as the Java `OntologyGraph` ctor.
pub fn run_all(graph: &mut OntologyGraph) {
    annotate_searchable_annotation_values(graph);
    annotate_inverse_of(graph);
    annotate_negative_property_assertions(graph);
    annotate_obo_synonym_type_names(graph);
    annotate_direct_parents(graph);
    annotate_related(graph);
    annotate_hierarchical_parents(graph);
    annotate_ancestors(graph);
    annotate_hierarchy_metrics(graph);
    annotate_short_forms(graph);
    annotate_definitions(graph);
    annotate_synonyms(graph);
    annotate_reified_properties(graph);
    annotate_ontology_metadata(graph);
    annotate_hierarchy_flags(graph);
    annotate_is_obsolete(graph);
    annotate_labels(graph);
    annotate_configurable_properties(graph);
    annotate_preferred_roots(graph);
    annotate_disjoint_with(graph);
    annotate_has_individuals(graph);
    annotate_equivalence(graph);
}

// ---- 1. SearchableAnnotationValues ----

fn is_searchable_predicate(predicate: &str) -> bool {
    if matches!(predicate, "loaded" | "sourceFileTimestamp" | "updated") {
        return false;
    }
    !predicate.starts_with("http://www.w3.org/1999/02/22-rdf-syntax-ns#")
        && !predicate.starts_with("http://www.w3.org/2000/01/rdf-schema#")
        && !predicate.starts_with("http://www.w3.org/2002/07/owl#")
}

fn annotate_searchable_annotation_values(graph: &mut OntologyGraph) {
    for id in node_ids(graph) {
        let to_add: Vec<PropertyValue> = {
            let node = graph.nodes.get(&id).unwrap();
            let is_target = node.has_type(NodeType::Class)
                || node.has_type(NodeType::Property)
                || node.has_type(NodeType::Individual)
                || node.has_type(NodeType::Ontology);
            if !is_target {
                continue;
            }
            let mut values = Vec::new();
            for predicate in node.properties.predicates() {
                if !is_searchable_predicate(predicate) {
                    continue;
                }
                for value in node.properties.get_property_values(predicate).unwrap() {
                    if matches!(value.kind, PVKind::Literal(_)) {
                        values.push(value.clone());
                    }
                }
            }
            values
        };
        let node = graph.nodes.get_mut(&id).unwrap();
        for v in to_add {
            node.properties.add_property("searchableAnnotationValues", v);
        }
    }
}

// ---- 2. InverseOf ----

fn annotate_inverse_of(graph: &mut OntologyGraph) {
    let mut additions: Vec<(String, String)> = Vec::new(); // (target_uri, c_uri)
    for id in node_ids(graph) {
        let node = graph.nodes.get(&id).unwrap();
        if node.uri.is_none() || !node.has_type(NodeType::Property) {
            continue;
        }
        let c_uri = node.uri.clone().unwrap();
        if let Some(values) = node.properties.get_property_values(OWL_INVERSE_OF) {
            for v in values {
                if let PVKind::Uri(target) = &v.kind {
                    if graph.nodes.contains_key(target) {
                        additions.push((target.clone(), c_uri.clone()));
                    }
                }
            }
        }
    }
    for (target, c_uri) in additions {
        if let Some(t) = graph.nodes.get_mut(&target) {
            t.properties
                .add_property(OWL_INVERSE_OF, PropertyValue::uri(c_uri));
        }
    }
}

// ---- 3. NegativePropertyAssertion ----

fn annotate_negative_property_assertions(graph: &mut OntologyGraph) {
    let mut additions: Vec<(String, String, PropertyValue)> = Vec::new();
    for id in node_ids(graph) {
        let node = graph.nodes.get(&id).unwrap();
        if !node.has_type(NodeType::NegativePropertyAssertion) {
            continue;
        }
        let source = node
            .properties
            .get_property_value("http://www.w3.org/2002/07/owl#sourceIndividual");
        let assertion = node
            .properties
            .get_property_value("http://www.w3.org/2002/07/owl#assertionProperty");
        let target_individual = node
            .properties
            .get_property_value("http://www.w3.org/2002/07/owl#targetIndividual");
        let target_value = node
            .properties
            .get_property_value("http://www.w3.org/2002/07/owl#targetValue");

        let assertion_uri = match assertion.and_then(|v| v.as_uri()) {
            Some(u) => u.to_string(),
            None => continue,
        };
        let source_id = match source.and_then(|v| v.as_uri()) {
            Some(u) => u.to_string(),
            None => continue,
        };
        if !graph.nodes.contains_key(&source_id) {
            continue;
        }
        let predicate = format!("negativePropertyAssertion+{assertion_uri}");
        if let Some(ti) = target_individual {
            additions.push((source_id, predicate, ti.clone()));
        } else if let Some(tv) = target_value {
            additions.push((source_id, predicate, tv.clone()));
        }
    }
    for (source_id, predicate, value) in additions {
        if let Some(n) = graph.nodes.get_mut(&source_id) {
            n.properties.add_property(&predicate, value);
        }
    }
}

// ---- 4. OboSynonymTypeName ----

fn annotate_obo_synonym_type_names(graph: &mut OntologyGraph) {
    let mut additions: Vec<(String, String)> = Vec::new(); // (axiom_id, label)
    for id in node_ids(graph) {
        let node = graph.nodes.get(&id).unwrap();
        if !node.has_type(NodeType::Axiom) {
            continue;
        }
        if let Some(values) = node.properties.get_property_values(OBO_HAS_SYNONYM_TYPE) {
            for v in values {
                if let PVKind::Uri(type_uri) = &v.kind {
                    if let Some(type_node) = graph.nodes.get(type_uri) {
                        if let Some(label) = type_node.properties.get_property_value(RDFS_LABEL) {
                            if let PVKind::Literal(lit) = &label.kind {
                                additions.push((id.clone(), lit.value.clone()));
                            }
                        }
                    }
                }
            }
        }
    }
    for (axiom_id, label) in additions {
        let n = graph.nodes.get_mut(&axiom_id).unwrap();
        n.properties.add_property(
            "oboSynonymTypeName",
            PropertyValue::literal(Literal::from_string(label)),
        );
    }
}

// ---- 5. DirectParents ----

fn collect_uri_parents(node: &OntologyNode, predicate: &str, graph: &OntologyGraph) -> Vec<PropertyValue> {
    let mut out = Vec::new();
    if let Some(values) = node.properties.get_property_values(predicate) {
        for v in values {
            if let PVKind::Uri(uri) = &v.kind {
                if graph.nodes.contains_key(uri) {
                    // Reuse (clone, sharing axioms) the source value object, as Java does.
                    out.push(v.clone());
                }
            }
        }
    }
    out
}

fn annotate_direct_parents(graph: &mut OntologyGraph) {
    for id in node_ids(graph) {
        let direct_parents: Option<Vec<PropertyValue>> = {
            let node = graph.nodes.get(&id).unwrap();
            if node.uri.is_none() {
                continue;
            }
            if node.has_type(NodeType::Class) {
                Some(collect_uri_parents(node, RDFS_SUBCLASSOF, graph))
            } else if node.has_type(NodeType::Property) {
                Some(collect_uri_parents(node, RDFS_SUBPROPERTYOF, graph))
            } else if node.has_type(NodeType::Individual) {
                // Individual's rdf:type values (except owl:NamedIndividual) are parents.
                let mut out = Vec::new();
                if let Some(values) = node.properties.get_property_values(RDF_TYPE) {
                    for v in values {
                        if let PVKind::Uri(uri) = &v.kind {
                            if uri == OWL_NAMED_INDIVIDUAL {
                                continue;
                            }
                            if graph.nodes.contains_key(uri) {
                                out.push(v.clone());
                            }
                        }
                    }
                }
                Some(out)
            } else {
                None
            }
        };
        if let Some(parents) = direct_parents {
            if !parents.is_empty() {
                graph
                    .nodes
                    .get_mut(&id)
                    .unwrap()
                    .properties
                    .add_property(DIRECT_PARENT, PropertyValue::list(parents));
            }
        }
    }
}

// ---- 6. Related ----

fn annotate_related(graph: &mut OntologyGraph) {
    // (target_node_uri, related_value)
    let mut related: Vec<(String, PropertyValue)> = Vec::new();

    for id in node_ids(graph) {
        let node = graph.nodes.get(&id).unwrap();
        if !node.has_type(NodeType::Class) || node.uri.is_none() {
            continue;
        }
        let c_uri = node.uri.clone().unwrap();
        let parents: Vec<PropertyValue> = match node.properties.get_property_values(RDFS_SUBCLASSOF) {
            Some(v) => v.clone(),
            None => continue,
        };
        for parent in parents {
            let bnode_id = match &parent.kind {
                PVKind::Bnode(b) => b.clone(),
                _ => continue,
            };
            let ce = match graph.nodes.get(&bnode_id) {
                Some(n) => n,
                None => continue,
            };
            let on_property = ce.properties.get_property_value(OWL_ON_PROPERTY).cloned();
            match on_property {
                None => related_class_expr(graph, &c_uri, &bnode_id, &mut related),
                Some(on_prop) => {
                    related_restriction(graph, &c_uri, &bnode_id, &on_prop, &mut related)
                }
            }
        }
    }

    // Group per target node, preserving order; add a single relatedTo list.
    let mut order: Vec<String> = Vec::new();
    let mut by_node: std::collections::HashMap<String, Vec<PropertyValue>> =
        std::collections::HashMap::new();
    for (target, value) in related {
        by_node
            .entry(target.clone())
            .or_insert_with(|| {
                order.push(target.clone());
                Vec::new()
            })
            .push(value);
    }
    for target in order {
        let list = by_node.remove(&target).unwrap();
        if let Some(n) = graph.nodes.get_mut(&target) {
            n.properties.add_property(RELATED_TO, PropertyValue::list(list));
        }
    }
}

fn make_related(class_expr_id: &str, property: &str, filler_uri: &str) -> PropertyValue {
    PropertyValue::new(PVKind::Related {
        class_expr_id: class_expr_id.to_string(),
        property: property.to_string(),
        filler_uri: filler_uri.to_string(),
    })
}

fn related_class_expr(
    graph: &OntologyGraph,
    c_uri: &str,
    ce_id: &str,
    out: &mut Vec<(String, PropertyValue)>,
) {
    let ce = graph.nodes.get(ce_id).unwrap();
    if let Some(one_of) = ce.properties.get_property_value(OWL_ONE_OF) {
        if let PVKind::Bnode(list_id) = &one_of.kind {
            if let Some(list_node) = graph.nodes.get(list_id) {
                for item in graph.evaluate_rdf_list(list_node) {
                    if let PVKind::Uri(ind_uri) = &item.kind {
                        if graph.nodes.contains_key(ind_uri) {
                            out.push((
                                c_uri.to_string(),
                                make_related(ce_id, RDFS_SUBCLASSOF, ind_uri),
                            ));
                        }
                    }
                }
            }
        }
        return;
    }
    if let Some(inter) = ce.properties.get_property_value(OWL_INTERSECTION_OF) {
        if let PVKind::Bnode(list_id) = &inter.kind {
            if let Some(list_node) = graph.nodes.get(list_id) {
                for item in graph.evaluate_rdf_list(list_node) {
                    if let Some(filler) = graph.node_for_value(&item) {
                        if let Some(filler_uri) = &filler.uri {
                            out.push((
                                c_uri.to_string(),
                                make_related(ce_id, RDFS_SUBCLASSOF, filler_uri),
                            ));
                        }
                    }
                }
            }
        }
    }
}

fn related_restriction(
    graph: &OntologyGraph,
    c_uri: &str,
    restriction_id: &str,
    on_property: &PropertyValue,
    out: &mut Vec<(String, PropertyValue)>,
) {
    let property_uri = match on_property.as_uri() {
        Some(u) => u.to_string(),
        None => return,
    };
    let restriction = graph.nodes.get(restriction_id).unwrap();

    if let Some(svf) = restriction.properties.get_property_value(OWL_SOME_VALUES_FROM).cloned() {
        match &svf.kind {
            PVKind::Uri(filler_uri) => {
                if filler_uri != c_uri && graph.nodes.contains_key(filler_uri) {
                    out.push((
                        c_uri.to_string(),
                        make_related(restriction_id, &property_uri, filler_uri),
                    ));
                }
            }
            PVKind::Bnode(sce_id) => {
                if let Some(sce) = graph.nodes.get(sce_id) {
                    if let Some(one_of) = sce.properties.get_property_value(OWL_ONE_OF) {
                        if let PVKind::Bnode(list_id) = &one_of.kind {
                            if let Some(list_node) = graph.nodes.get(list_id) {
                                for item in graph.evaluate_rdf_list(list_node) {
                                    if let PVKind::Uri(ind_uri) = &item.kind {
                                        if graph.nodes.contains_key(ind_uri) {
                                            out.push((
                                                c_uri.to_string(),
                                                make_related(list_id, &property_uri, ind_uri),
                                            ));
                                        }
                                    }
                                }
                            }
                        }
                    } else if let Some(inter) =
                        sce.properties.get_property_value(OWL_INTERSECTION_OF)
                    {
                        if let PVKind::Bnode(list_id) = &inter.kind {
                            if let Some(list_node) = graph.nodes.get(list_id) {
                                for item in graph.evaluate_rdf_list(list_node) {
                                    if let Some(filler) = graph.node_for_value(&item) {
                                        if let Some(filler_uri) = &filler.uri {
                                            out.push((
                                                c_uri.to_string(),
                                                make_related(sce_id, &property_uri, filler_uri),
                                            ));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            _ => {}
        }
        return;
    }

    if let Some(has_value) = restriction.properties.get_property_value(OWL_HAS_VALUE).cloned() {
        if let PVKind::Uri(filler_uri) = &has_value.kind {
            if let Some(filler) = graph.nodes.get(filler_uri) {
                if filler.has_type(NodeType::Individual) {
                    out.push((
                        filler_uri.clone(),
                        make_related(restriction_id, &property_uri, c_uri),
                    ));
                }
            }
        }
    }
}

// ---- helper: hierarchical/definition/synonym property sets ----

fn hierarchical_properties(config: &OntologyConfig) -> BTreeSet<String> {
    let mut set = BTreeSet::new();
    set.insert(RDFS_SUBCLASSOF.to_string());
    if let Some(props) = config.get_str_array("hierarchical_property") {
        set.extend(props);
    } else {
        set.insert("http://purl.obolibrary.org/obo/BFO_0000050".to_string());
    }
    set
}

fn definition_properties(config: &OntologyConfig) -> BTreeSet<String> {
    let mut set: BTreeSet<String> = [
        "http://www.w3.org/2000/01/rdf-schema#comment",
        "http://purl.obolibrary.org/obo/IAO_0000115",
        "http://purl.org/dc/terms/description",
        "http://purl.org/dc/elements/1.1/description",
    ]
    .iter()
    .map(|s| s.to_string())
    .collect();
    if let Some(props) = config.get_str_array("definition_property") {
        set.extend(props);
    }
    set
}

fn synonym_properties(config: &OntologyConfig) -> BTreeSet<String> {
    let mut set: BTreeSet<String> = [
        "http://www.geneontology.org/formats/oboInOwl#hasExactSynonym",
        "http://www.geneontology.org/formats/oboInOwl#hasRelatedSynonym",
        "http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym",
        "http://www.geneontology.org/formats/oboInOwl#hasBroadSynonym",
        "http://www.geneontology.org/formats/oboInOwl#hasSynonym",
    ]
    .iter()
    .map(|s| s.to_string())
    .collect();
    if let Some(props) = config.get_str_array("synonym_property") {
        set.extend(props);
    }
    set
}

// ---- 7. HierarchicalParents ----

fn annotate_hierarchical_parents(graph: &mut OntologyGraph) {
    let hier_props = hierarchical_properties(&graph.config);

    for id in node_ids(graph) {
        // (hierarchical parents list, reifications [(filler_uri, reified PropertySet)])
        let computed = {
            let node = graph.nodes.get(&id).unwrap();
            let is_target = node.has_type(NodeType::Class)
                || node.has_type(NodeType::Property)
                || node.has_type(NodeType::Individual);
            if !is_target || node.uri.is_none() {
                continue;
            }

            let mut hierarchical_parents: Vec<PropertyValue> = Vec::new();
            if let Some(values) = node.properties.get_property_values(RDFS_SUBCLASSOF) {
                for v in values {
                    if let PVKind::Uri(uri) = &v.kind {
                        if graph.nodes.contains_key(uri) {
                            // Reuse (clone, sharing axioms) the source subClassOf value.
                            hierarchical_parents.push(v.clone());
                        }
                    }
                }
            }

            let mut reifications: Vec<(String, PropertySet)> = Vec::new();
            if let Some(related_values) = node.properties.get_property_values(RELATED_TO) {
                for rel_list in related_values {
                    if let PVKind::List(list) = &rel_list.kind {
                        for related in list {
                            if let PVKind::Related {
                                property,
                                filler_uri,
                                ..
                            } = &related.kind
                            {
                                let inverse = graph.nodes.get(property).and_then(|pn| {
                                    pn.properties
                                        .get_property_value(OWL_INVERSE_OF)
                                        .and_then(|v| v.as_uri().map(|s| s.to_string()))
                                });
                                if hier_props.contains(property) {
                                    hierarchical_parents
                                        .push(PropertyValue::uri(filler_uri.clone()));
                                    let mut reified = PropertySet::new();
                                    reified.add_property(
                                        "childRelationToParent",
                                        PropertyValue::uri(property.clone()),
                                    );
                                    if let Some(inv) = inverse {
                                        reified.add_property(
                                            "parentRelationToChild",
                                            PropertyValue::uri(inv),
                                        );
                                    }
                                    reifications.push((filler_uri.clone(), reified));
                                }
                            }
                        }
                    }
                }
            }
            (hierarchical_parents, reifications)
        };

        let (hierarchical_parents, reifications) = computed;
        if !hierarchical_parents.is_empty() {
            graph
                .nodes
                .get_mut(&id)
                .unwrap()
                .properties
                .add_property(HIERARCHICAL_PARENT, PropertyValue::list(hierarchical_parents));
            for (filler_uri, reified) in reifications {
                graph.annotate_property_with_axiom(
                    &id,
                    HIERARCHICAL_PARENT,
                    PropertyValue::uri(filler_uri),
                    reified,
                );
            }
        }
    }
}

// ---- 8. Ancestors ----

fn annotate_ancestors(graph: &mut OntologyGraph) {
    for id in node_ids(graph) {
        let is_class = {
            let node = graph.nodes.get(&id).unwrap();
            if node.uri.is_none() {
                continue;
            }
            node.has_type(NodeType::Class)
        };
        let node = graph.nodes.get_mut(&id).unwrap();
        if is_class {
            node.properties.add_property(
                HIERARCHICAL_ANCESTOR,
                PropertyValue::new(PVKind::Ancestors {
                    node_id: id.clone(),
                    hierarchy_predicate: HIERARCHICAL_PARENT.to_string(),
                }),
            );
        }
        node.properties.add_property(
            DIRECT_ANCESTOR,
            PropertyValue::new(PVKind::Ancestors {
                node_id: id.clone(),
                hierarchy_predicate: DIRECT_PARENT.to_string(),
            }),
        );
    }
}

// ---- 9. HierarchyMetrics ----

fn annotate_hierarchy_metrics(graph: &mut OntologyGraph) {
    annotate_one_metric(graph, DIRECT_PARENT, NUM_DESCENDANTS);
    annotate_one_metric(graph, HIERARCHICAL_PARENT, NUM_HIERARCHICAL_DESCENDANTS);
}

fn annotate_one_metric(graph: &mut OntologyGraph, hierarchy_predicate: &str, metric: &str) {
    let mut counts: std::collections::HashMap<String, i64> = std::collections::HashMap::new();
    for id in node_ids(graph) {
        let node = graph.nodes.get(&id).unwrap();
        if node.uri.is_none() {
            continue;
        }
        let ancestors = graph.ancestors_closure(node, hierarchy_predicate);
        for uri in ancestors {
            *counts.entry(uri).or_insert(0) += 1;
        }
    }
    for id in node_ids(graph) {
        let uri = {
            let node = graph.nodes.get(&id).unwrap();
            match &node.uri {
                Some(u) => u.clone(),
                None => continue,
            }
        };
        let count = counts.get(&uri).copied().unwrap_or(0);
        graph
            .nodes
            .get_mut(&id)
            .unwrap()
            .properties
            .add_property(metric, PropertyValue::literal(Literal::from_integer(count.to_string())));
    }
}

// ---- 10. ShortForm ----

fn ontology_base_uris(config: &OntologyConfig) -> Vec<String> {
    let mut uris: Vec<String> = Vec::new();
    if let Some(base) = config.get_str_array("base_uri") {
        uris.extend(base);
    }
    if let Some(prefix) = config.get_str("preferredPrefix") {
        uris.push(format!("http://purl.obolibrary.org/obo/{prefix}_"));
    }
    uris
}

fn annotate_short_forms(graph: &mut OntologyGraph) {
    let base_uris = ontology_base_uris(&graph.config);
    let config_prefix = graph.config.get_str("preferredPrefix").map(|s| s.to_string());
    let short_form_pattern = graph
        .config
        .get_str("shortFormExtractionPattern")
        .map(|s| s.to_string());
    let id_upper = graph
        .config
        .get_str("id")
        .unwrap_or("")
        .to_uppercase();

    let effective_prefix = match &config_prefix {
        Some(p) if !p.is_empty() => p.clone(),
        _ => id_upper,
    };

    let pattern_re = short_form_pattern
        .as_ref()
        .and_then(|p| regex::Regex::new(p).ok());

    for id in node_ids(graph) {
        let uri = {
            let node = graph.nodes.get(&id).unwrap();
            let is_target = node.has_type(NodeType::Class)
                || node.has_type(NodeType::Property)
                || node.has_type(NodeType::Individual)
                || node.has_type(NodeType::Datatype);
            if !is_target || node.uri.is_none() {
                continue;
            }
            node.uri.clone().unwrap()
        };

        let short_form = extract_short_form(
            &uri,
            &base_uris,
            &effective_prefix,
            short_form_pattern.as_deref(),
            pattern_re.as_ref(),
        );
        let curie = make_curie(&short_form, &effective_prefix, short_form_pattern.as_deref());

        let node = graph.nodes.get_mut(&id).unwrap();
        node.properties
            .add_property("shortForm", PropertyValue::literal(Literal::from_string(short_form)));
        node.properties
            .add_property("curie", PropertyValue::literal(Literal::from_string(curie)));
    }
}

fn extract_short_form(
    uri: &str,
    base_uris: &[String],
    preferred_prefix: &str,
    short_form_pattern: Option<&str>,
    pattern_re: Option<&regex::Regex>,
) -> String {
    if let Some(rest) = uri.strip_prefix("urn:") {
        return rest.to_string();
    }
    if short_form_pattern.is_some() {
        if let Some(re) = pattern_re {
            if let Some(caps) = re.captures(uri) {
                if let Some(local) = caps.get(1) {
                    return format!("{preferred_prefix}_{}", local.as_str());
                }
            }
        }
    }
    for base in base_uris {
        if uri.starts_with(base) {
            return format!("{preferred_prefix}_{}", &uri[base.len()..]);
        }
    }
    if let Some(pos) = uri.rfind(['/', '#']) {
        uri[pos + 1..].to_string()
    } else {
        uri.to_string()
    }
}

fn make_curie(short_form: &str, preferred_prefix: &str, short_form_pattern: Option<&str>) -> String {
    if let Some(p) = short_form_pattern {
        if !p.is_empty() && short_form.contains('_') {
            return format!("{preferred_prefix}:{}", &short_form[preferred_prefix.len() + 1..]);
        }
    }
    // single underscore, prefix == preferredPrefix
    if let Some(rest) = short_form.strip_prefix(preferred_prefix) {
        if let Some(after) = rest.strip_prefix('_') {
            if !after.is_empty() && !after.contains('_') {
                return format!("{preferred_prefix}:{after}");
            }
        }
    }
    // single underscore, suffix all digits
    if let Some((before, after)) = split_once_only_underscore(short_form) {
        if !after.is_empty() && after.bytes().all(|b| b.is_ascii_digit()) {
            return format!("{before}:{after}");
        }
    }
    // multiple underscores, trailing digits after last underscore
    if let Some(pos) = short_form.rfind('_') {
        let after = &short_form[pos + 1..];
        if !after.is_empty() && after.bytes().all(|b| b.is_ascii_digit()) {
            return format!("{}:{}", &short_form[..pos], after);
        }
    }
    short_form.to_string()
}

/// If `s` has exactly one '_' with no '_' before it, return (before, after).
fn split_once_only_underscore(s: &str) -> Option<(&str, &str)> {
    let first = s.find('_')?;
    let before = &s[..first];
    let after = &s[first + 1..];
    if before.contains('_') || after.contains('_') {
        None
    } else {
        Some((before, after))
    }
}

// ---- 11. Definition ----

fn annotate_definitions(graph: &mut OntologyGraph) {
    let props = definition_properties(&graph.config);
    for id in node_ids(graph) {
        let collected = {
            let node = graph.nodes.get(&id).unwrap();
            if node.uri.is_none() {
                continue;
            }
            let mut values = Vec::new();
            for prop in &props {
                if let Some(vs) = node.properties.get_property_values(prop) {
                    for v in vs {
                        values.push(v.clone());
                    }
                }
            }
            values
        };
        if !collected.is_empty() {
            graph
                .nodes
                .get_mut(&id)
                .unwrap()
                .properties
                .add_property(DEFINITION, PropertyValue::list(collected));
        }
    }
}

// ---- 12. Synonym ----

fn annotate_synonyms(graph: &mut OntologyGraph) {
    let props = synonym_properties(&graph.config);
    for id in node_ids(graph) {
        {
            let node = graph.nodes.get(&id).unwrap();
            if node.uri.is_none() {
                continue;
            }
        }
        let mut synonyms: Vec<PropertyValue> = Vec::new();
        for prop in &props {
            // Capture the original values, then normalise single values to a list.
            let original: Option<Vec<PropertyValue>> = graph
                .nodes
                .get(&id)
                .unwrap()
                .properties
                .get_property_values(prop)
                .cloned();
            if let Some(original) = original {
                if original.len() == 1 && !original[0].is_list() {
                    let node = graph.nodes.get_mut(&id).unwrap();
                    node.properties.remove_property(prop);
                    node.properties
                        .add_property(prop, PropertyValue::list(original.clone()));
                }
                for v in original {
                    synonyms.push(v);
                }
            }
        }
        if !synonyms.is_empty() {
            graph
                .nodes
                .get_mut(&id)
                .unwrap()
                .properties
                .add_property(SYNONYM, PropertyValue::list(synonyms));
        }
    }
}

// ---- 13. ReifiedProperty ----

fn annotate_reified_properties(graph: &mut OntologyGraph) {
    let axiom_ids: Vec<String> = graph
        .nodes
        .iter()
        .filter(|(_, n)| n.has_type(NodeType::Axiom))
        .map(|(id, _)| id.clone())
        .collect();

    for id in axiom_ids {
        let extracted = {
            let node = graph.nodes.get(&id).unwrap();
            let source = node
                .properties
                .get_property_value("http://www.w3.org/2002/07/owl#annotatedSource");
            let property = node
                .properties
                .get_property_value("http://www.w3.org/2002/07/owl#annotatedProperty");
            let target = node
                .properties
                .get_property_value("http://www.w3.org/2002/07/owl#annotatedTarget");

            let source_id = source.and_then(|v| v.node_id()).map(|s| s.to_string());
            let property_uri = property.and_then(|v| v.as_uri()).map(|s| s.to_string());
            let target = target.cloned();

            match (source_id, property_uri, target) {
                (Some(s), Some(p), Some(t)) if graph.nodes.contains_key(&s) => {
                    let mut axiom = PropertySet::new();
                    for predicate in node.properties.predicates() {
                        if matches!(
                            predicate.as_str(),
                            "http://www.w3.org/2002/07/owl#annotatedSource"
                                | "http://www.w3.org/2002/07/owl#annotatedProperty"
                                | "http://www.w3.org/2002/07/owl#annotatedTarget"
                                | RDF_TYPE
                        ) {
                            continue;
                        }
                        for v in node.properties.get_property_values(predicate).unwrap() {
                            axiom.add_property(predicate, v.clone());
                        }
                    }
                    Some((s, p, t, axiom))
                }
                _ => None,
            }
        };

        if let Some((source_id, property_uri, target, axiom)) = extracted {
            graph.annotate_property_with_axiom(&source_id, &property_uri, target, axiom);
        }
    }
}

// ---- 14. OntologyMetadata ----

fn annotate_ontology_metadata(graph: &mut OntologyGraph) {
    let ontology_id = graph.config.get_str("id").unwrap_or("").to_lowercase();
    let preferred_prefix = graph.config.get_str("preferredPrefix").map(|s| s.to_string());
    let ontology_iri = graph
        .ontology_node()
        .and_then(|n| n.uri.clone())
        .unwrap_or_default();

    for id in node_ids(graph) {
        let node = graph.nodes.get_mut(&id).unwrap();
        let is_target = node.has_type(NodeType::Class)
            || node.has_type(NodeType::Property)
            || node.has_type(NodeType::Individual);
        if !is_target || node.uri.is_none() {
            continue;
        }
        node.properties.add_property(
            "ontologyId",
            PropertyValue::literal(Literal::from_string(ontology_id.clone())),
        );
        if let Some(pp) = &preferred_prefix {
            node.properties.add_property(
                "ontologyPreferredPrefix",
                PropertyValue::literal(Literal::from_string(pp.clone())),
            );
        }
        node.properties.add_property(
            "ontologyIri",
            PropertyValue::literal(Literal::from_string(ontology_iri.clone())),
        );
    }
}

// ---- 15. HierarchyFlags ----

fn annotate_hierarchy_flags(graph: &mut OntologyGraph) {
    let mut children: HashSet<String> = HashSet::new();
    let mut hierarchical_children: HashSet<String> = HashSet::new();

    // Pass 1: parent flags + collect children sets.
    for id in node_ids(graph) {
        let (has_direct_parents, has_direct_parents_present, has_hierarchical_parents) = {
            let node = graph.nodes.get(&id).unwrap();
            let is_target = node.has_type(NodeType::Class)
                || node.has_type(NodeType::Property)
                || node.has_type(NodeType::Individual);
            if !is_target || node.uri.is_none() {
                continue;
            }

            // direct parents
            let mut has_direct = false;
            let direct_present = node.properties.get_property_value(DIRECT_PARENT).is_some();
            if let Some(PVKind::List(list)) = node
                .properties
                .get_property_value(DIRECT_PARENT)
                .map(|v| &v.kind)
            {
                for parent in list {
                    if let PVKind::Uri(iri) = &parent.kind {
                        if iri == OWL_THING || iri == OWL_TOP_OBJECT_PROPERTY {
                            continue;
                        }
                        has_direct = true;
                        children.insert(iri.clone());
                    }
                }
            }

            // hierarchical parents (stored as a single PropertyValueList)
            let mut has_hier = false;
            if let Some(values) = node.properties.get_property_values(HIERARCHICAL_PARENT) {
                if values.len() == 1 {
                    if let PVKind::List(list) = &values[0].kind {
                        for parent in list {
                            if let PVKind::Uri(iri) = &parent.kind {
                                if iri == OWL_THING || iri == OWL_TOP_OBJECT_PROPERTY {
                                    continue;
                                }
                                has_hier = true;
                                hierarchical_children.insert(iri.clone());
                            }
                        }
                    }
                }
            }

            (has_direct, direct_present, has_hier)
        };

        let node = graph.nodes.get_mut(&id).unwrap();
        if has_direct_parents_present {
            node.properties.add_property(
                HAS_DIRECT_PARENTS,
                PropertyValue::literal(Literal::from_boolean(has_direct_parents)),
            );
        } else {
            node.properties
                .add_property(HAS_DIRECT_PARENTS, PropertyValue::literal(Literal::from_boolean(false)));
        }
        node.properties.add_property(
            HAS_HIERARCHICAL_PARENTS,
            PropertyValue::literal(Literal::from_boolean(has_hierarchical_parents)),
        );
    }

    // Pass 2: children flags.
    for id in node_ids(graph) {
        let uri = {
            let node = graph.nodes.get(&id).unwrap();
            let is_target = node.has_type(NodeType::Class)
                || node.has_type(NodeType::Property)
                || node.has_type(NodeType::Individual);
            if !is_target || node.uri.is_none() {
                continue;
            }
            node.uri.clone().unwrap()
        };
        let node = graph.nodes.get_mut(&id).unwrap();
        node.properties.add_property(
            HAS_DIRECT_CHILDREN,
            PropertyValue::literal(Literal::from_boolean(children.contains(&uri))),
        );
        node.properties.add_property(
            HAS_HIERARCHICAL_CHILDREN,
            PropertyValue::literal(Literal::from_boolean(hierarchical_children.contains(&uri))),
        );
    }
}

// ---- 16. IsObsolete ----

fn is_entity_obsolete(node: &OntologyNode) -> bool {
    if let Some(dep) = node.properties.get_property_value(OWL_DEPRECATED) {
        if let PVKind::Literal(lit) = &dep.kind {
            if lit.value.eq_ignore_ascii_case("true") || lit.value == "1" {
                return true;
            }
        }
    }
    if let Some(parents) = node.properties.get_property_values(RDFS_SUBCLASSOF) {
        for parent in parents {
            if let PVKind::Uri(uri) = &parent.kind {
                if uri == OBO_OBSOLETE_CLASS {
                    return true;
                }
            }
        }
    }
    false
}

fn annotate_is_obsolete(graph: &mut OntologyGraph) {
    for id in node_ids(graph) {
        let obsolete = {
            let node = graph.nodes.get(&id).unwrap();
            let is_entity = node.has_type(NodeType::Class)
                || node.has_type(NodeType::Property)
                || node.has_type(NodeType::Individual);
            if is_entity && node.uri.is_none() {
                continue;
            }
            is_entity_obsolete(node)
        };
        graph
            .nodes
            .get_mut(&id)
            .unwrap()
            .properties
            .add_property(IS_OBSOLETE, PropertyValue::literal(Literal::from_boolean(obsolete)));
    }
}

// ---- 17. Label ----

fn label_properties(config: &OntologyConfig) -> BTreeSet<String> {
    if let Some(props) = config.get_str_array("label_property") {
        return props.into_iter().collect();
    }
    [
        "http://www.w3.org/2000/01/rdf-schema#label",
        "http://purl.org/dc/elements/1.1/title",
        "http://purl.org/dc/terms/title",
        "http://www.w3.org/2004/02/skos/core#prefLabel",
    ]
    .iter()
    .map(|s| s.to_string())
    .collect()
}

fn is_non_english(lit: &Literal) -> bool {
    !lit.lang.is_empty() && lit.lang != "en"
}

fn annotate_labels(graph: &mut OntologyGraph) {
    let source_props = label_properties(&graph.config);
    let fallback_props = ["shortForm"];

    for id in node_ids(graph) {
        let labels = {
            let node = graph.nodes.get(&id).unwrap();
            if node.uri.is_none() {
                continue;
            }
            let mut labels: Vec<PropertyValue> = Vec::new();
            let mut has_english = false;
            for prop in &source_props {
                if let Some(values) = node.properties.get_property_values(prop) {
                    for value in values {
                        match &value.kind {
                            PVKind::Literal(lit) => {
                                labels.push(value.clone());
                                if !is_non_english(lit) {
                                    has_english = true;
                                }
                            }
                            PVKind::List(list) => {
                                for inner in list {
                                    if let PVKind::Literal(lit) = &inner.kind {
                                        labels.push(inner.clone());
                                        if !is_non_english(lit) {
                                            has_english = true;
                                        }
                                    }
                                }
                            }
                            _ => {}
                        }
                    }
                }
            }
            if !has_english {
                for prop in fallback_props {
                    if let Some(values) = node.properties.get_property_values(prop) {
                        for value in values {
                            match &value.kind {
                                PVKind::Literal(_) => labels.push(value.clone()),
                                PVKind::List(list) => {
                                    for inner in list {
                                        if matches!(inner.kind, PVKind::Literal(_)) {
                                            labels.push(inner.clone());
                                        }
                                    }
                                }
                                _ => {}
                            }
                        }
                    }
                }
            }
            labels
        };
        if !labels.is_empty() {
            graph
                .nodes
                .get_mut(&id)
                .unwrap()
                .properties
                .add_property(LABEL, PropertyValue::list(labels));
        }
    }
}

// ---- 18. ConfigurableProperty ----

fn annotate_configurable_properties(graph: &mut OntologyGraph) {
    let hier = hierarchical_properties(&graph.config);
    let def = definition_properties(&graph.config);
    let syn = synonym_properties(&graph.config);

    for id in node_ids(graph) {
        let (h, d, s): (Vec<String>, Vec<String>, Vec<String>) = {
            let node = graph.nodes.get(&id).unwrap();
            let is_target = node.has_type(NodeType::Class)
                || node.has_type(NodeType::Property)
                || node.has_type(NodeType::Individual);
            if !is_target || node.uri.is_none() {
                continue;
            }
            (
                hier.iter().filter(|p| node.properties.has_property(p)).cloned().collect(),
                def.iter().filter(|p| node.properties.has_property(p)).cloned().collect(),
                syn.iter().filter(|p| node.properties.has_property(p)).cloned().collect(),
            )
        };
        let node = graph.nodes.get_mut(&id).unwrap();
        for p in h {
            node.properties
                .add_property("hierarchicalProperty", PropertyValue::literal(Literal::from_string(p)));
        }
        for p in d {
            node.properties
                .add_property("definitionProperty", PropertyValue::literal(Literal::from_string(p)));
        }
        for p in s {
            node.properties
                .add_property("synonymProperty", PropertyValue::literal(Literal::from_string(p)));
        }
    }
}

// ---- 19. PreferredRoots ----

fn preferred_roots(graph: &OntologyGraph) -> Vec<String> {
    let mut roots: Vec<String> = Vec::new();
    let mut seen: HashSet<String> = HashSet::new();
    let push = |s: String, roots: &mut Vec<String>, seen: &mut HashSet<String>| {
        if seen.insert(s.clone()) {
            roots.push(s);
        }
    };
    if let Some(Value::Array(arr)) = graph.config.get("preferred_root_term") {
        for v in arr {
            if let Some(s) = v.as_str() {
                push(s.to_string(), &mut roots, &mut seen);
            }
        }
    }
    if let Some(onto) = graph.ontology_node() {
        for predicate in [
            "http://purl.obolibrary.org/obo/IAO_0000700",
            "http://www.ebi.ac.uk/ols/vocabulary/hasPreferredRootTerm",
        ] {
            if let Some(values) = onto.properties.get_property_values(predicate) {
                for v in values {
                    if let PVKind::Uri(uri) = &v.kind {
                        push(uri.clone(), &mut roots, &mut seen);
                    }
                }
            }
        }
    }
    roots
}

fn annotate_preferred_roots(graph: &mut OntologyGraph) {
    let roots = preferred_roots(graph);
    let roots_set: HashSet<&String> = roots.iter().collect();

    if !roots.is_empty() {
        let list: Vec<PropertyValue> = roots.iter().map(|r| PropertyValue::uri(r.clone())).collect();
        if let Some(onto_id) = graph.ontology_node_id.clone() {
            graph
                .nodes
                .get_mut(&onto_id)
                .unwrap()
                .properties
                .add_property(PREFERRED_ROOT, PropertyValue::list(list));
        }
    }

    for id in node_ids(graph) {
        let is_root = {
            let node = graph.nodes.get(&id).unwrap();
            let is_target = node.has_type(NodeType::Class) || node.has_type(NodeType::Property);
            if !is_target || node.uri.is_none() {
                continue;
            }
            roots_set.contains(node.uri.as_ref().unwrap())
        };
        graph
            .nodes
            .get_mut(&id)
            .unwrap()
            .properties
            .add_property(IS_PREFERRED_ROOT, PropertyValue::literal(Literal::from_boolean(is_root)));
    }
}

// ---- 20. DisjointWith ----

fn annotate_disjoint_with(graph: &mut OntologyGraph) {
    let mut additions: Vec<(String, String, String)> = Vec::new(); // (target_uri, predicate, other_uri)

    for id in node_ids(graph) {
        let node = graph.nodes.get(&id).unwrap();
        let (members_pred, out_pred) = if node.has_type(NodeType::AllDisjointClasses) {
            (OWL_MEMBERS, "http://www.w3.org/2002/07/owl#disjointWith")
        } else if node.has_type(NodeType::AllDisjointProperties) {
            (OWL_MEMBERS, "http://www.w3.org/2002/07/owl#propertyDisjointWith")
        } else if node.has_type(NodeType::AllDifferent) {
            (OWL_DISTINCT_MEMBERS, "http://www.w3.org/2002/07/owl#differentFrom")
        } else {
            continue;
        };

        let members_value = match node.properties.get_property_value(members_pred) {
            Some(v) => v,
            None => continue,
        };
        let list_node = match graph.node_for_value(members_value) {
            Some(n) => n,
            None => continue,
        };
        let member_uris: Vec<String> = graph
            .evaluate_rdf_list(list_node)
            .iter()
            .filter_map(|v| graph.node_for_value(v).and_then(|n| n.uri.clone()))
            .collect();

        for a in &member_uris {
            for b in &member_uris {
                if a != b {
                    additions.push((a.clone(), out_pred.to_string(), b.clone()));
                }
            }
        }
    }

    for (target, predicate, other) in additions {
        if let Some(n) = graph.nodes.get_mut(&target) {
            n.properties.add_property(&predicate, PropertyValue::uri(other));
        }
    }
}

// ---- 21. HasIndividuals ----

fn annotate_has_individuals(graph: &mut OntologyGraph) {
    let mut targets: Vec<String> = Vec::new();
    for id in node_ids(graph) {
        let node = graph.nodes.get(&id).unwrap();
        if !node.has_type(NodeType::Individual) || node.uri.is_none() {
            continue;
        }
        if let Some(types) = node.properties.get_property_values(RDF_TYPE) {
            for t in types {
                if let Some(type_node) = graph.node_for_value(t) {
                    if type_node.has_type(NodeType::Class) && type_node.uri.is_some() {
                        targets.push(type_node.uri.clone().unwrap());
                    }
                }
            }
        }
    }
    for target in targets {
        if let Some(n) = graph.nodes.get_mut(&target) {
            n.properties
                .add_property(HAS_INDIVIDUALS, PropertyValue::literal(Literal::from_boolean(true)));
        }
    }
}

// ---- 22. Equivalence ----

fn annotate_equivalence(graph: &mut OntologyGraph) {
    let mut additions: Vec<(String, String, String)> = Vec::new(); // (target_uri, predicate, c_uri)
    for id in node_ids(graph) {
        let node = graph.nodes.get(&id).unwrap();
        if node.uri.is_none() {
            continue;
        }
        let c_uri = node.uri.clone().unwrap();
        let predicate = if node.has_type(NodeType::Class) {
            OWL_EQUIVALENT_CLASS
        } else if node.has_type(NodeType::Property) {
            OWL_EQUIVALENT_PROPERTY
        } else {
            continue;
        };
        if let Some(values) = node.properties.get_property_values(predicate) {
            for v in values {
                if let PVKind::Uri(target) = &v.kind {
                    if graph.nodes.contains_key(target) {
                        additions.push((target.clone(), predicate.to_string(), c_uri.clone()));
                    }
                }
            }
        }
    }
    for (target, predicate, c_uri) in additions {
        if let Some(n) = graph.nodes.get_mut(&target) {
            n.properties.add_property(&predicate, PropertyValue::uri(c_uri));
        }
    }
}
