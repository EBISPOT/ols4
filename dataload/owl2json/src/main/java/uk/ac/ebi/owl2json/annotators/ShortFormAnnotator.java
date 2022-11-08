package uk.ac.ebi.owl2json.annotators;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.annotators.helpers.OntologyBaseUris;
import uk.ac.ebi.owl2json.annotators.helpers.ShortFormExtractor;
import uk.ac.ebi.owl2json.properties.PropertyValueLiteral;

public class ShortFormAnnotator {

	public static void annotateShortForms(OwlGraph graph) {

		long startTime3 = System.nanoTime();

		Set<String> ontologyBaseUris = OntologyBaseUris.getOntologyBaseUris(graph);
		String preferredPrefix = (String)graph.config.get("preferredPrefix");

		for(String id : graph.nodes.keySet()) {
		    OwlNode c = graph.nodes.get(id);
		    if (c.types.contains(OwlNode.NodeType.CLASS) ||
				c.types.contains(OwlNode.NodeType.PROPERTY) ||
				c.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {

			// skip bnodes
			if(c.uri == null)
				continue;
	
			c.properties.addProperty(
				"shortForm",
					PropertyValueLiteral.fromString(
						ShortFormExtractor.extractShortForm(graph, ontologyBaseUris, preferredPrefix, c.uri)
					));
		    }
		}
		long endTime3 = System.nanoTime();
		System.out.println("annotate short forms: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}
	

}
