package uk.ac.ebi.rdf2json.annotators;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.OntologyNode.NodeType;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueURI;

public class EquivalenceAnnotator {
    private static final Logger logger = LoggerFactory.getLogger(EquivalenceAnnotator.class);
	
	/* If    A equivalentClass B
	   then    B equivalentClass A
	   but maybe only the first one is specified. This annotator
	   creates the latter from the former.
	   */
    public static void annotateEquivalance(OntologyGraph graph) {

        long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {

            OntologyNode c = graph.nodes.get(id);

            // skip bnodes
            if(c.uri == null)
                continue;

            if (c.types.contains(OntologyNode.NodeType.CLASS)) {

                List<PropertyValue> equivalences = c.properties.getPropertyValues("http://www.w3.org/2002/07/owl#equivalentClass");

                if(equivalences != null) {
                    for(PropertyValue equivalence : equivalences) {
                        if(equivalence.getType() == PropertyValue.Type.URI) {
				OntologyNode equivalentNode = graph.getNodeForPropertyValue(equivalence);
				if(equivalentNode != null) {
					equivalentNode.properties.addProperty(
						"http://www.w3.org/2002/07/owl#equivalentClass", PropertyValueURI.fromUri(c.uri));
				}
                        }
                    }
                }

	    } else if( c.types.contains(OntologyNode.NodeType.PROPERTY)) {

                List<PropertyValue> equivalences = c.properties.getPropertyValues("http://www.w3.org/2002/07/owl#equivalentProperty");

                if(equivalences != null) {
                    for(PropertyValue equivalence : equivalences) {
                        if(equivalence.getType() == PropertyValue.Type.URI) {
				OntologyNode equivalentNode = graph.getNodeForPropertyValue(equivalence);
				if(equivalentNode != null) {
					equivalentNode.properties.addProperty(
						"http://www.w3.org/2002/07/owl#equivalentProperty", PropertyValueURI.fromUri(c.uri));
				}
                        }
                    }
                }
	    }

	}

        long endTime3 = System.nanoTime();
        logger.info("annotate equivalence: {}", ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }
}
