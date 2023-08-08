package uk.ac.ebi.rdf2json.annotators;

import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueAncestors;
import uk.ac.ebi.rdf2json.properties.PropertyValueURI;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AncestorsAnnotator {

    public static void annotateAncestors(OntologyGraph graph) {

        long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {

            OntologyNode c = graph.nodes.get(id);

            // skip bnodes
            if(c.uri == null)
                continue;

            if (c.types.contains(OntologyNode.NodeType.CLASS)) {
                c.properties.addProperty("hierarchicalAncestor", new PropertyValueAncestors(c, "hierarchicalParent"));
            }

            c.properties.addProperty("directAncestor", new PropertyValueAncestors(c, "directParent"));
        }

        long endTime3 = System.nanoTime();
        System.out.println("annotate ancestors: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }



}
