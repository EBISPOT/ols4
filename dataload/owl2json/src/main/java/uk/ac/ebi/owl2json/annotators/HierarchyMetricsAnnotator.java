
package uk.ac.ebi.owl2json.annotators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.properties.*;

public class HierarchyMetricsAnnotator {

    public static void annotateHierarchyMetrics(OwlGraph graph) {

        long startTime3 = System.nanoTime();

        Map<String, Integer> iriToNumDescendants = new HashMap<>();
        Map<String, Integer> iriToNumHierarchicalDescendants = new HashMap<>();

        for (String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);

            // skip bnodes
            if (c.uri == null)
                continue;

            if (c.types.contains(OwlNode.NodeType.CLASS)    
                    || c.types.contains(OwlNode.NodeType.PROPERTY)) {
                incrementCountInAncestors(c, iriToNumDescendants, "directParent", graph, new HashSet<>());
            }
            
            if(c.types.contains(OwlNode.NodeType.CLASS)) {
                incrementCountInAncestors(c, iriToNumHierarchicalDescendants, "hierarchicalParent", graph, new HashSet<>());
            }

        }

        for (String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);

            // skip bnodes
            if (c.uri == null)
                continue;

            Integer numDescendants = iriToNumDescendants.get(c.uri);
            Integer numHierarchicalDescendants = iriToNumHierarchicalDescendants.get(c.uri);

            if (c.types.contains(OwlNode.NodeType.CLASS)
                    || c.types.contains(OwlNode.NodeType.PROPERTY)) {
                c.properties.addProperty("numDescendants",
                        PropertyValueLiteral.fromString(numDescendants != null ? numDescendants.toString() : "0"));
            }

            if (c.types.contains(OwlNode.NodeType.CLASS)) {
                c.properties.addProperty("numHierarchicalDescendants",
                        PropertyValueLiteral.fromString(
                                numHierarchicalDescendants != null ? numHierarchicalDescendants.toString() : "0"));
            }

        }

        long endTime3 = System.nanoTime();
        System.out.println("annotate hierarchy metrics: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }

    public static void incrementCountInAncestors(OwlNode node, Map<String,Integer> iriToNumDescendants, String hierarchyPredicate, OwlGraph graph, Set<String> visited) {

                List<PropertyValue> parents = node.properties.getPropertyValues(hierarchyPredicate);

                if(parents != null) {
                    for(PropertyValue parent : parents) {
                        if(parent.getType() == PropertyValue.Type.URI) {
                            String uri = ((PropertyValueURI) parent).getUri();

                            Integer existing = iriToNumDescendants.get(uri);
                            if(existing != null) {
                                iriToNumDescendants.put(uri, existing+1);
                            } else {
                                iriToNumDescendants.put(uri, 1);
                            }

                            OwlNode parentNode = graph.getNodeForPropertyValue(parent);

                            if(parentNode != null) {
                                if(!visited.contains(parentNode.uri)) { // prevent cycles
                                    Set<String> nextVisited = new HashSet<>(visited);
                                    nextVisited.add(parentNode.uri);
                                    incrementCountInAncestors(parentNode, iriToNumDescendants, hierarchyPredicate, graph, nextVisited);
                                }
                            }
                        }
                    }
                }
    }
    
}
