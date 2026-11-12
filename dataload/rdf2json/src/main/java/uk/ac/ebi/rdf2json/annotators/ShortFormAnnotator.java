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
		String shortFormPattern = (String)graph.config.get("shortFormExtractionPattern");

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

				ExtractedShortForm extracted = extractShortForm(graph, ontologyBaseUris, preferredPrefix, shortFormPattern, c.uri);
				String shortForm = extracted.value();

			/*
			CURIEs are formed by following rules:
			If shortFormExtractionPattern matches the IRI, join PreferredPrefix and the captured local part with a colon.
			If there is only one underscore "_" AND the characters before the underscore are PreferredPrefix then replace the underscore with colon ":"
			If there is only one underscore "_" AND the characters after the underscore are numbers then replace the underscore with colon ":"
			If there is only one underscore "_" and the characters after the underscore are not just numbers then just keep the curie same as shortform
			If there are multiple underscore but has only digits after the last underscore then the code replaces the last underscore with a colon
			*/
				String curie;

				// A configured pattern can miss imported IRIs, which still use the default rules.
				if (extracted.customPatternMatched()) {
					curie = preferredPrefix + ":" + shortForm.substring(preferredPrefix.length() + 1);
				} else {
					// Default CURIE generation logic
					// Pattern for: single underscore, prefix matches preferredPrefix
					String preferredPrefixPattern = "^(?:" + Pattern.quote(preferredPrefix) + ")_([^_]+)$";
					// Pattern for: single underscore, suffix is all digits
					String singleUnderscoreDigitsPattern = "^[^_]+_(\\d+)$";
					// Pattern for: multiple underscores, suffix is all digits
					String multipleUnderscoresDigitsPattern = "^(.*)_(\\d+)$";
					if (shortForm.matches(preferredPrefixPattern)) {
						curie = preferredPrefix + ":" + shortForm.substring(preferredPrefix.length() + 1);
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
				}

			c.properties.addProperty("shortForm", PropertyValueLiteral.fromString(shortForm));
			c.properties.addProperty("curie", PropertyValueLiteral.fromString(curie));
		}
	}
		long endTime3 = System.nanoTime();
		logger.info("annotate short forms: {}", ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}
	
	private record ExtractedShortForm(String value, boolean customPatternMatched) {}

	private static ExtractedShortForm extractShortForm(OntologyGraph graph, Set<String> ontologyBaseUris, String preferredPrefix,
										   String shortFormPattern, String uri) {

		if (uri.startsWith("urn:")) {
			return new ExtractedShortForm(uri.substring(4), false);
		}

		// Check if there's a custom short form extraction pattern in config
		if (shortFormPattern != null && !shortFormPattern.isEmpty()) {
			try {
				Pattern pattern = Pattern.compile(shortFormPattern);
				java.util.regex.Matcher matcher = pattern.matcher(uri);
				if (matcher.matches() && matcher.groupCount() > 0) {
					String localPart = matcher.group(1);
					if (preferredPrefix != null) {
						return new ExtractedShortForm(preferredPrefix + "_" + localPart, true);
					} else {
						return new ExtractedShortForm(localPart, false);
					}
				}
			} catch (Exception e) {
				logger.warn("Failed to apply shortFormExtractionPattern '{}' to URI '{}': {}",
						shortFormPattern, uri, e.getMessage());
				// Fall through to default behavior
			}
		}

		// if(uri.startsWith("http://purl.obolibrary.org/obo/")) {
		// return uri.substring("http://purl.obolibrary.org/obo/".length());
		// }

		for (String baseUri : ontologyBaseUris) {
			if (uri.startsWith(baseUri) && preferredPrefix != null) {
				return new ExtractedShortForm(preferredPrefix + "_" + uri.substring(baseUri.length()), false);
			}
		}

		if (uri.contains("/") || uri.contains("#")) {

			return new ExtractedShortForm(uri.substring(
					Math.max(
							uri.lastIndexOf('/'),
							uri.lastIndexOf('#')) + 1), false);

		} else {

			return new ExtractedShortForm(uri, false);
		}
	}

}
