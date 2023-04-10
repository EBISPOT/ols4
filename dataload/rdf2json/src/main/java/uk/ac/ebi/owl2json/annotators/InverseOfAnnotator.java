package uk.ac.ebi.rdf2json.annotators;

import java.util.List;

import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.OntologyNode.NodeType;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueURI;

public class InverseOfAnnotator {
	
	/* If    A inverseOf B
	   then    B inverseOf A
	   but maybe only the first one is specified. This annotator
	   creates the latter from the former.
	   */
    public static void annotateInverseOf(OntologyGraph graph) {

        long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {

            OntologyNode c = graph.nodes.get(id);

            // skip bnodes
            if(c.uri == null)
                continue;

            if (c.types.contains(OntologyNode.NodeType.PROPERTY)) {

                List<PropertyValue> inverseOfs = c.properties.getPropertyValues("http://www.w3.org/2002/07/owl#inverseOf");

                if(inverseOfs != null) {
                    for(PropertyValue inverseOf : inverseOfs) {
                        if(inverseOf.getType() == PropertyValue.Type.URI) {
				OntologyNode equivalentNode = graph.getNodeForPropertyValue(inverseOf);
				if(equivalentNode != null) {
					equivalentNode.properties.addProperty(
						"http://www.w3.org/2002/07/owl#inverseOf", PropertyValueURI.fromUri(c.uri));
				}
                        }
                    }
                }

	    }

	}

        long endTime3 = System.nanoTime();
        System.out.println("annotate inverseOf: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }
}
