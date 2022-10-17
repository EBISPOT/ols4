package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.properties.PropertyValueLiteral;

public class OntologyIdAnnotator {

	public static void annotateOntologyIds(OwlGraph graph) {

		long startTime3 = System.nanoTime();


		String ontologyId = (String) graph.config.get("id");


		for(String id : graph.nodes.keySet()) {
		    OwlNode c = graph.nodes.get(id);
		    if (c.types.contains(OwlNode.NodeType.CLASS) ||
				c.types.contains(OwlNode.NodeType.PROPERTY) ||
				c.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {

			// skip bnodes
			if(c.uri == null)
				continue;
	
			c.properties.addProperty(
				"ontologyId",
					PropertyValueLiteral.fromString(
						ontologyId.toLowerCase()
					));
		    }
		}
		long endTime3 = System.nanoTime();
		System.out.println("annotate ontology IDs: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}
}
