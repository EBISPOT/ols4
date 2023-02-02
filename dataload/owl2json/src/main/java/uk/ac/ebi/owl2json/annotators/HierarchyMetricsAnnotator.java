
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

            if(c.types.contains(OwlNode.NodeType.CLASS)) {

                List<PropertyValue> ancestors = c.properties.getPropertyValues("hierarchicalAncestor");
                if(ancestors != null) {
                    for(PropertyValue ancestor : ancestors) {
                        String uri = ((PropertyValueURI) ancestor).getUri();
                        if(!uri.equals("http://www.w3.org/2002/07/owl#Thing")) {
                            Integer existing = iriToNumDescendants.get(uri);
                            iriToNumHierarchicalDescendants.put(uri, existing != null ? existing + 1 : 1);
                        }
                    }
                }

            }

            List<PropertyValue> ancestors = c.properties.getPropertyValues("directAncestor");
            if (ancestors != null) {
                for (PropertyValue ancestor : ancestors) {
                    String uri = ((PropertyValueURI) ancestor).getUri();
                    if(!uri.equals("http://www.w3.org/2002/07/owl#Thing")) {
                        Integer existing = iriToNumDescendants.get(uri);
                        iriToNumDescendants.put(uri, existing != null ? existing + 1 : 1);
                    }
                }
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

}
