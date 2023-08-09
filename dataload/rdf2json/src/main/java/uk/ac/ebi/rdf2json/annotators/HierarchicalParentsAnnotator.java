package uk.ac.ebi.rdf2json.annotators;

import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.*;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class HierarchicalParentsAnnotator {

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

                if(parents != null) {
                    for(PropertyValue parent : parents) {

                        if (parent.getType() == PropertyValue.Type.URI && graph.nodes.containsKey(((PropertyValueURI) parent).getUri())) {

                            // Direct parent; these are also considered hierarchical parents
                            c.properties.addProperty("hierarchicalParent", parent);

                        }
                    }
                }

                // any non direct parents have already been interpreted by RelatedAnnotator, so we
                // can find their values in relatedTo
                //
                List<PropertyValue> relatedTo = (List<PropertyValue>) c.properties.getPropertyValues("relatedTo");

                if(relatedTo != null) {
                    for(PropertyValue related : relatedTo) {

                        if(related.getType() == PropertyValue.Type.RELATED) {

                            String property = ((PropertyValueRelated) related).getProperty();



                            // if the child->parent property is "part of" we also want to know the parent->child (inverse) property "has part"
                            //
                            var propertyNode = graph.nodes.get(property);
                            String inverseProperty = null;
                            if(propertyNode != null) {
                                var inversePropertyValue = propertyNode.properties.getPropertyValue("http://www.w3.org/2002/07/owl#inverseOf");
                                if(inversePropertyValue != null && inversePropertyValue.getType() == PropertyValue.Type.URI) {
                                    inverseProperty = ((PropertyValueURI) inversePropertyValue).getUri();
                                }
                            }


                            if(hierarchicalProperties.contains(property)) {

                                var filler = new PropertyValueURI(
                                        ((PropertyValueRelated) related).getFiller().uri
                                );

                                c.properties.addProperty("hierarchicalParent", filler);

                                // reify the hierarchicalParent edge with the property IRIs
                                // this enables the frontend to display e.g. "has part" relations in the tree
                                //
                                PropertySet reifiedProperties = new PropertySet();
                                reifiedProperties.addProperty("childRelationToParent", PropertyValueURI.fromUri(property));
                                if(inverseProperty != null) {
                                    reifiedProperties.addProperty("parentRelationToChild", PropertyValueURI.fromUri(inverseProperty));
                                }
                                c.properties.annotatePropertyWithAxiom("hierarchicalParent", filler, reifiedProperties, graph);

                            }
                        }
                    }
                }
            }
        }
        long endTime3 = System.nanoTime();
        System.out.println("annotate hierarchical parents: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }


}
