package uk.ac.ebi.owl2json.operations;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.jena.graph.NodeFactory;

import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlTranslator;

public class OntologyIdAnnotator {

	public static void annotateOntologyIds(OwlTranslator translator) {

		long startTime3 = System.nanoTime();


		String ontologyId = (String) translator.config.get("id");


		for(String id : translator.nodes.keySet()) {
		    OwlNode c = translator.nodes.get(id);
		    if (c.type == OwlNode.NodeType.CLASS ||
				c.type == OwlNode.NodeType.PROPERTY ||
				c.type == OwlNode.NodeType.NAMED_INDIVIDUAL) {

			// skip bnodes
			if(c.uri == null)
				continue;
	
			c.properties.addProperty(
				"ontologyId",
					NodeFactory.createLiteral(
						ontologyId
					));
		    }
		}
		long endTime3 = System.nanoTime();
		System.out.println("annotate ontology IDs: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}
}
