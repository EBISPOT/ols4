package uk.ac.ebi.rdf2json.annotators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.*;

import java.util.*;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

public class HierarchicalParentsAnnotator {
    private static final Logger logger = LoggerFactory.getLogger(HierarchicalParentsAnnotator.class);

    public static Set<String> getHierarchicalProperties(OntologyGraph graph) {

        Set<String> hierarchicalProperties = new TreeSet<>(
                List.of(
                        "http://www.w3.org/2000/01/rdf-schema#subClassOf"
                )
        );

        Object configHierarchicalProperties = graph.config.get("hierarchical_property");

        if(configHierarchicalProperties instanceof Collection<?>) {
            hierarchicalProperties.addAll((Collection<String>) configHierarchicalProperties);
        } else {
            hierarchicalProperties.add("http://purl.obolibrary.org/obo/BFO_0000050");
        }

        return hierarchicalProperties;
    }

    public static void annotateHierarchicalParents(OntologyGraph graph) {

	Set<String> hierarchicalProperties = getHierarchicalProperties(graph);

        long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {
            OntologyNode c = graph.nodes.get(id);
            if (c.types.contains(OntologyNode.NodeType.CLASS) ||
                    c.types.contains(OntologyNode.NodeType.PROPERTY) ||
                    c.types.contains(OntologyNode.NodeType.INDIVIDUAL)) {

                // skip bnodes
                if(c.uri == null)
                    continue;

                List<PropertyValue> parents = c.properties.getPropertyValues("http://www.w3.org/2000/01/rdf-schema#subClassOf");
                List<PropertyValueURI> hierarchicalParents = new ArrayList<>();

                if(parents != null) {
                    for(PropertyValue parent : parents) {

                        if (parent.getType() == PropertyValue.Type.URI && graph.nodes.containsKey(((PropertyValueURI) parent).getUri())) {

                            // Direct parent; these are also considered hierarchical parents
                            hierarchicalParents.add((PropertyValueURI) parent);
                        }
                    }
                }

                // any non direct parents have already been interpreted by RelatedAnnotator, so we
                // can find their values in relatedTo
                //
                List<PropertyValue> relatedToList = (List<PropertyValue>) c.properties.getPropertyValues(RELATED_TO.getText());
                Map<PropertyValueURI, PropertySet> fillerToReifiedPropertiesMap = new HashMap<>();

                if(relatedToList != null) {
                    for(PropertyValue relatedToListElement : relatedToList) {
                        if (relatedToListElement.getType() == PropertyValue.Type.LIST) {
                            for (PropertyValue related : ((PropertyValueList)relatedToListElement).getPropertyValues()) {

                                if (related.getType() == PropertyValue.Type.RELATED) {

                                    String property = ((PropertyValueRelated) related).getProperty();


                                    // if the child->parent property is "part of" we also want to know the parent->child (inverse) property "has part"
                                    //
                                    String inverseProperty = getInverseProperty(graph, property);


                                    if (hierarchicalProperties.contains(property)) {

                                        var filler = new PropertyValueURI(
                                                ((PropertyValueRelated) related).getFiller().uri
                                        );

                                        hierarchicalParents.add(filler);

                                        // reify the hierarchicalParent edge with the property IRIs
                                        // this enables the frontend to display e.g. "has part" relations in the tree
                                        //
                                        PropertySet reifiedProperties = new PropertySet();
                                        reifiedProperties.addProperty("childRelationToParent", PropertyValueURI.fromUri(property));
                                        if (inverseProperty != null) {
                                            reifiedProperties.addProperty("parentRelationToChild", PropertyValueURI.fromUri(inverseProperty));
                                        }
                                        fillerToReifiedPropertiesMap.put(filler, reifiedProperties);

                                    }
                                }
                            }
                        }
                    }
                }
                // Individuals are not covered by RelatedAnnotator, so their hierarchical
                // properties are read here instead. When the target is another individual
                // the relation is a direct triple; when it is a class it is an existential
                // restriction the individual is typed with (rdf:type [P someValuesFrom C]).
                // rdfs:subClassOf is already handled above.
                //
                if (c.types.contains(OntologyNode.NodeType.INDIVIDUAL)) {
                    for (String hierarchicalProperty : hierarchicalProperties) {

                        if (hierarchicalProperty.equals("http://www.w3.org/2000/01/rdf-schema#subClassOf")) {
                            continue;
                        }

                        List<PropertyValue> assertions = c.properties.getPropertyValues(hierarchicalProperty);
                        if (assertions == null) {
                            continue;
                        }

                        String inverseProperty = getInverseProperty(graph, hierarchicalProperty);

                        for (PropertyValue assertion : assertions) {

                            if (assertion.getType() != PropertyValue.Type.URI) {
                                continue;
                            }

                            String parentUri = ((PropertyValueURI) assertion).getUri();

                            // an entity cannot be its own hierarchical parent
                            if (parentUri.equals(c.uri) || !graph.nodes.containsKey(parentUri)) {
                                continue;
                            }

                            var filler = new PropertyValueURI(parentUri);
                            hierarchicalParents.add(filler);

                            PropertySet reifiedProperties = new PropertySet();
                            reifiedProperties.addProperty("childRelationToParent", PropertyValueURI.fromUri(hierarchicalProperty));
                            if (inverseProperty != null) {
                                reifiedProperties.addProperty("parentRelationToChild", PropertyValueURI.fromUri(inverseProperty));
                            }
                            fillerToReifiedPropertiesMap.put(filler, reifiedProperties);
                        }
                    }

                    List<PropertyValue> types = c.properties.getPropertyValues("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
                    if (types != null) {
                        for (PropertyValue type : types) {

                            if (type.getType() != PropertyValue.Type.BNODE) {
                                continue;
                            }

                            OntologyNode restriction = graph.nodes.get(((PropertyValueBNode) type).getId());
                            if (restriction == null) {
                                continue;
                            }

                            PropertyValue onProperty = restriction.properties.getPropertyValue("http://www.w3.org/2002/07/owl#onProperty");
                            if (onProperty == null || onProperty.getType() != PropertyValue.Type.URI) {
                                continue;
                            }

                            String propertyUri = ((PropertyValueURI) onProperty).getUri();
                            if (!hierarchicalProperties.contains(propertyUri)) {
                                continue;
                            }

                            PropertyValue someValuesFrom = restriction.properties.getPropertyValue("http://www.w3.org/2002/07/owl#someValuesFrom");
                            if (someValuesFrom == null || someValuesFrom.getType() != PropertyValue.Type.URI) {
                                continue;
                            }

                            String parentUri = ((PropertyValueURI) someValuesFrom).getUri();

                            // an entity cannot be its own hierarchical parent
                            if (parentUri.equals(c.uri) || !graph.nodes.containsKey(parentUri)) {
                                continue;
                            }

                            var filler = new PropertyValueURI(parentUri);
                            hierarchicalParents.add(filler);

                            PropertySet reifiedProperties = new PropertySet();
                            reifiedProperties.addProperty("childRelationToParent", PropertyValueURI.fromUri(propertyUri));
                            String inverseProperty = getInverseProperty(graph, propertyUri);
                            if (inverseProperty != null) {
                                reifiedProperties.addProperty("parentRelationToChild", PropertyValueURI.fromUri(inverseProperty));
                            }
                            fillerToReifiedPropertiesMap.put(filler, reifiedProperties);
                        }
                    }
                }

                if (hierarchicalParents.size()>0) {
                    c.properties.addProperty(HIERARCHICAL_PARENT.getText(), new PropertyValueList(hierarchicalParents));
                    for (PropertyValueURI propertyValueURI: fillerToReifiedPropertiesMap.keySet()) {
                        c.properties.annotatePropertyWithAxiom(HIERARCHICAL_PARENT.getText(), propertyValueURI,
                                fillerToReifiedPropertiesMap.get(propertyValueURI), graph);

                    }
                }
            }
        }


        long endTime3 = System.nanoTime();
        logger.info("annotate hierarchical parents: {}", ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }

    private static String getInverseProperty(OntologyGraph graph, String property) {
        var propertyNode = graph.nodes.get(property);
        if (propertyNode != null) {
            var inversePropertyValue = propertyNode.properties.getPropertyValue("http://www.w3.org/2002/07/owl#inverseOf");
            if (inversePropertyValue != null && inversePropertyValue.getType() == PropertyValue.Type.URI) {
                return ((PropertyValueURI) inversePropertyValue).getUri();
            }
        }
        return null;
    }


}
