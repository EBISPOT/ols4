package uk.ac.ebi.rdf2json.annotators;

import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.annotators.helpers.OntologyBaseUris;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;

public class ShortFormAnnotator {
	private static final Logger logger = LoggerFactory.getLogger(ShortFormAnnotator.class);

	public static void annotateShortForms(OntologyGraph graph) {

		long startTime3 = System.nanoTime();

		Set<String> ontologyBaseUris = OntologyBaseUris.getOntologyBaseUris(graph);
		String preferredPrefix = (String)graph.config.get("preferredPrefix");

		for(String id : graph.nodes.keySet()) {
		    OntologyNode c = graph.nodes.get(id);
		    if (c.types.contains(OntologyNode.NodeType.CLASS) ||
				c.types.contains(OntologyNode.NodeType.PROPERTY) ||
				c.types.contains(OntologyNode.NodeType.INDIVIDUAL) ||
				c.types.contains(OntologyNode.NodeType.DATATYPE)
				) {

			// skip bnodes
			if(c.uri == null)
				continue;

			if (preferredPrefix == null || preferredPrefix.isEmpty()) {
				preferredPrefix = graph.config.get("id").toString().toUpperCase();
			}

			String shortForm = extractShortForm(graph, ontologyBaseUris, preferredPrefix, c.uri);

			/*
			CURIEs are formed by following rules:
			If there is only one underscore "_" AND the characters before the underscore are PreferredPrefix then replace the underscore with colon ":"
			If there is only one underscore "_" AND the characters after the underscore are numbers then replace the underscore with colon ":"
			If there is only one underscore "_" and the characters after the underscore are not just numbers then just keep the curie same as shortform
			If there are multiple underscore but has only digits after the last underscore then the code replaces the last underscore with a colon
			*/
			String curie;
			// Pattern for: single underscore, prefix matches preferredPrefix
			String preferredPrefixPattern = "^(?:" + Pattern.quote(preferredPrefix) + ")_([^_]+)$";
			// Pattern for: single underscore, suffix is all digits
			String singleUnderscoreDigitsPattern = "^[^_]+_(\\d+)$";
			// Pattern for: multiple underscores, suffix is all digits
			String multipleUnderscoresDigitsPattern = "^(.*)_(\\d+)$";
			if (shortForm.matches(preferredPrefixPattern)) {
				curie = shortForm.replaceFirst("_", ":");
			} else if (shortForm.matches(singleUnderscoreDigitsPattern)) {
				curie = shortForm.replaceFirst("_", ":");
			} else if (shortForm.matches(multipleUnderscoresDigitsPattern)) {
				// Multiple underscores, suffix is digits
				// Replace the last underscore with a colon
				curie = shortForm.replaceFirst("_(?=\\d+$)", ":");
			} else {
				// No transformation needed
				curie = shortForm;
			}

			c.properties.addProperty("shortForm", PropertyValueLiteral.fromString(shortForm));
			c.properties.addProperty("curie", PropertyValueLiteral.fromString(curie));
		}
	}
		long endTime3 = System.nanoTime();
		logger.info("annotate short forms: {}", ((endTime3 - startTime3) / 1000 / 1000 / 1000));
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
