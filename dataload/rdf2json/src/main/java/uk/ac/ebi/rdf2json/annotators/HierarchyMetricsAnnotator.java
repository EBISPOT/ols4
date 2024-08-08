
package uk.ac.ebi.rdf2json.annotators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.helpers.AncestorsClosure;
import uk.ac.ebi.rdf2json.properties.*;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

public class HierarchyMetricsAnnotator {

    public static void annotateHierarchyMetrics(OntologyGraph graph) {

        long startTime3 = System.nanoTime();

        annotateHierarchyMetrics(graph, "directParent", NUM_DESCENDANTS.getText());
        System.gc();

        annotateHierarchyMetrics(graph, "hierarchicalParent", NUM_HIERARCHICAL_DESCENDANTS.getText());
        System.gc();

        long endTime3 = System.nanoTime();
        System.out.println("annotate hierarchy metrics: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }


    private static void annotateHierarchyMetrics(OntologyGraph graph, String hierarchyPredicate, String metricProperty) {

        Map<String, Integer> iriToNumDescendants = new HashMap<>();

        for (String id : graph.nodes.keySet()) {
            OntologyNode c = graph.nodes.get(id);

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
            OntologyNode c = graph.nodes.get(id);

            // skip bnodes
            if (c.uri == null)
                continue;

            Integer numDescendants = iriToNumDescendants.get(c.uri);

            c.properties.addProperty(metricProperty,
                    PropertyValueLiteral.fromInteger(numDescendants != null ? numDescendants.toString() : "0"));
        }
    }

}
