package uk.ac.ebi.rdf2json.annotators;

import java.util.Set;

import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.annotators.helpers.OntologyBaseUris;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;

public class ShortFormAnnotator {

	public static void annotateShortForms(OntologyGraph graph) {

		long startTime3 = System.nanoTime();

		Set<String> ontologyBaseUris = OntologyBaseUris.getOntologyBaseUris(graph);
		String preferredPrefix = (String)graph.config.get("preferredPrefix");

		for(String id : graph.nodes.keySet()) {
		    OntologyNode c = graph.nodes.get(id);
		    if (c.types.contains(OntologyNode.NodeType.CLASS) ||
				c.types.contains(OntologyNode.NodeType.PROPERTY) ||
				c.types.contains(OntologyNode.NodeType.INDIVIDUAL)) {

			// skip bnodes
			if(c.uri == null)
				continue;


			String shortForm = extractShortForm(graph, ontologyBaseUris, preferredPrefix, c.uri);
			String curie = shortForm.replaceFirst("_", ":");

			c.properties.addProperty("shortForm", PropertyValueLiteral.fromString(shortForm));
			c.properties.addProperty("curie", PropertyValueLiteral.fromString(curie));
		    }
		}
		long endTime3 = System.nanoTime();
		System.out.println("annotate short forms: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}
	
	private static String extractShortForm(OntologyGraph graph, Set<String> ontologyBaseUris, String preferredPrefix,
			String uri) {

		if (uri.startsWith("urn:")) {
			return uri.substring(4);
		}

		// if(uri.startsWith("http://purl.obolibrary.org/obo/")) {
		// return uri.substring("http://purl.obolibrary.org/obo/".length());
		// }

		for (String baseUri : ontologyBaseUris) {
			if (uri.startsWith(baseUri) && preferredPrefix != null) {
				return preferredPrefix + "_" + uri.substring(baseUri.length());
			}
		}

		if (uri.contains("/") || uri.contains("#")) {

			return uri.substring(
					Math.max(
							uri.lastIndexOf('/'),
							uri.lastIndexOf('#')) + 1);

		} else {

			return uri;
		}
	}

}
