package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.annotators.helpers.ClassExpressionIRIExtractor;
import uk.ac.ebi.owl2json.properties.PropertyValue;
import uk.ac.ebi.owl2json.properties.PropertyValueLiteral;
import uk.ac.ebi.owl2json.properties.PropertyValueURI;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HierarchyFlagsAnnotator {

    public static void annotateHierarchyFlags(OwlGraph graph) {

        long startTime3 = System.nanoTime();

        // Set of IRIs that have children
        Set<String> hasChildren = new HashSet<>();

        for(String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);

            if (c.types.contains(OwlNode.NodeType.CLASS) ||
                    c.types.contains(OwlNode.NodeType.PROPERTY) ||
                    c.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {

                // skip bnodes
                if(c.uri == null)
                    continue;

                List<PropertyValue> parents = c.properties.getPropertyValues("http://www.w3.org/2000/01/rdf-schema#subClassOf");

                boolean hasDirectParent = false;

                if(parents != null) {
                    for(PropertyValue parent : parents) {
                        if(parent.getType().equals(PropertyValue.Type.URI)) {

                            String iri = ((PropertyValueURI) parent).getUri();

                            if(!iri.equals("http://www.w3.org/2002/07/owl#Thing")) {
                                hasDirectParent = true;
                                hasChildren.add(iri);
                            }
                        }
                    }
                }

                c.properties.addProperty("isRoot", PropertyValueLiteral.fromString(hasDirectParent ? "false" : "true"));
            }
        }

        for(String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);

            if (c.types.contains(OwlNode.NodeType.CLASS) ||
                    c.types.contains(OwlNode.NodeType.PROPERTY) ||
                    c.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {

                // skip bnodes
                if(c.uri == null)
                    continue;

                if(hasChildren.contains(c.uri)) {
                    c.properties.addProperty("hasChildren", PropertyValueLiteral.fromString("true"));
                } else {
                    c.properties.addProperty("hasChildren", PropertyValueLiteral.fromString("false"));
                }
            }
        }

        long endTime3 = System.nanoTime();
        System.out.println("annotate hierarchy flags: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


    }
}


