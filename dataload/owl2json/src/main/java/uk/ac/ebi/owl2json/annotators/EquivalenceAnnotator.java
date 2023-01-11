package uk.ac.ebi.owl2json.annotators;

import java.util.List;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlNode.NodeType;
import uk.ac.ebi.owl2json.properties.PropertyValue;
import uk.ac.ebi.owl2json.properties.PropertyValueURI;

public class EquivalenceAnnotator {
	
	/* If    A equivalentClass B
	   then    B equivalentClass A
	   but maybe only the first one is specified. This annotator
	   creates the latter from the former.
	   */
    public static void annotateEquivalance(OwlGraph graph) {

        long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {

            OwlNode c = graph.nodes.get(id);

            // skip bnodes
            if(c.uri == null)
                continue;

            if (c.types.contains(OwlNode.NodeType.CLASS)) {

                List<PropertyValue> equivalences = c.properties.getPropertyValues("http://www.w3.org/2002/07/owl#equivalentClass");

                if(equivalences != null) {
                    for(PropertyValue equivalence : equivalences) {
                        if(equivalence.getType() == PropertyValue.Type.URI) {
				OwlNode equivalentNode = graph.getNodeForPropertyValue(equivalence);
				if(equivalentNode != null) {
					equivalentNode.properties.addProperty(
						"http://www.w3.org/2002/07/owl#equivalentClass", PropertyValueURI.fromUri(c.uri));
				}
                        }
                    }
                }

	    } else if( c.types.contains(OwlNode.NodeType.PROPERTY)) {

                List<PropertyValue> equivalences = c.properties.getPropertyValues("http://www.w3.org/2002/07/owl#equivalentProperty");

                if(equivalences != null) {
                    for(PropertyValue equivalence : equivalences) {
                        if(equivalence.getType() == PropertyValue.Type.URI) {
				OwlNode equivalentNode = graph.getNodeForPropertyValue(equivalence);
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
        System.out.println("annotate equivalence: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }
}
