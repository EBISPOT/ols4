package uk.ac.ebi.rdf2json.annotators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.PropertyValueAncestors;

public class AncestorsAnnotator {
    private static final Logger logger = LoggerFactory.getLogger(AncestorsAnnotator.class);

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
        logger.info("annotate ancestors: {}", ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }

}
