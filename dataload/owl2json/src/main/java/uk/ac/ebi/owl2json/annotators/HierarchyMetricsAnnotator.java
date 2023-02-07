
package uk.ac.ebi.owl2json.annotators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.helpers.AncestorsClosure;
import uk.ac.ebi.owl2json.properties.*;

public class HierarchyMetricsAnnotator {

    public static void annotateHierarchyMetrics(OwlGraph graph) {

        long startTime3 = System.nanoTime();

        annotateHierarchyMetrics(graph, "directParent", "numDirectDescendants");
        System.gc();

        annotateHierarchyMetrics(graph, "hierarchicalParent", "numHierarchicalDescendants");
        System.gc();

        long endTime3 = System.nanoTime();
        System.out.println("annotate hierarchy metrics: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }


    private static void annotateHierarchyMetrics(OwlGraph graph, String hierarchyPredicate, String metricProperty) {

        Map<String, Integer> iriToNumDescendants = new HashMap<>();

        for (String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);

            // skip bnodes
            if (c.uri == null)
                continue;

            Set<String> ancestors = AncestorsClosure.getAncestors(c, hierarchyPredicate, graph);
            for(String uri : ancestors) {
                Integer existing = iriToNumDescendants.get(uri);
                iriToNumDescendants.put(uri, existing != null ? existing + 1 : 1);
            }
        }

        for (String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);

            // skip bnodes
            if (c.uri == null)
                continue;

            Integer numDescendants = iriToNumDescendants.get(c.uri);

            c.properties.addProperty(metricProperty,
                    PropertyValueLiteral.fromString(numDescendants != null ? numDescendants.toString() : "0"));
        }
    }

}
