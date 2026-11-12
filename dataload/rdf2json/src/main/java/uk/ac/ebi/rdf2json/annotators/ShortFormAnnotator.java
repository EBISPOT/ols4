package uk.ac.ebi.rdf2json.annotators;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
		Pattern shortFormPattern = compileShortFormPattern((String)graph.config.get("shortFormExtractionPattern"));

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

				String shortForm;
				String curie;
				var matcher = shortFormPattern == null || c.uri.startsWith("urn:") ? null : shortFormPattern.matcher(c.uri);
				if (matcher != null && matcher.groupCount() > 0 && matcher.matches()) {
					String localPart = matcher.group(1);
					shortForm = preferredPrefix + "_" + localPart;
					curie = preferredPrefix + ":" + localPart;
				} else {
					// Unmatched terms, including imports, use ordinary identifier extraction.
					shortForm = extractShortForm(ontologyBaseUris, preferredPrefix, c.uri);
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
	
	private static Pattern compileShortFormPattern(String pattern) {
		if (pattern == null || pattern.isEmpty()) {
			return null;
		}
		try {
			return Pattern.compile(pattern);
		} catch (PatternSyntaxException e) {
			logger.warn("Invalid shortFormExtractionPattern '{}': {}", pattern, e.getMessage());
			return null;
		}
	}

	private static String extractShortForm(Set<String> ontologyBaseUris, String preferredPrefix, String uri) {
		if (uri.startsWith("urn:")) {
			return uri.substring(4);
		}

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
