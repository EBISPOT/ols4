package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.properties.PropertyValue;
import uk.ac.ebi.owl2json.properties.PropertyValueAncestors;
import uk.ac.ebi.owl2json.properties.PropertyValueURI;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AncestorsAnnotator {

    public static void annotateAncestors(OwlGraph graph) {

        long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {

            OwlNode c = graph.nodes.get(id);

            // skip bnodes
            if(c.uri == null)
                continue;

            if (c.types.contains(OwlNode.NodeType.CLASS)) {
                c.properties.addProperty("hierarchicalAncestor", new PropertyValueAncestors(c, "hierarchicalParent"));
            }

            c.properties.addProperty("directAncestor", new PropertyValueAncestors(c, "directParent"));
        }

        long endTime3 = System.nanoTime();
        System.out.println("annotate ancestors: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }



}
