package uk.ac.ebi.rdf2json.annotators;

import java.util.Set;
import java.util.regex.Pattern;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.annotators.helpers.OntologyBaseUris;
import uk.ac.ebi.rdf2json.properties.PropertyValueJson;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;

public class CurieAnnotator {
	private static final Logger logger = LoggerFactory.getLogger(CurieAnnotator.class);

	static Gson gson = new Gson();

	public static void annotateCuries(OntologyGraph graph) {

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

			String curie = extractCurie(graph, ontologyBaseUris, preferredPrefix, c.uri);
			c.properties.addProperty("curie", new PropertyValueJson(gson.toJsonTree(curie)));
		}
	}
		long endTime3 = System.nanoTime();
		logger.info("annotate curies: {}", ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}
	
	private static String extractCurie(OntologyGraph graph, Set<String> ontologyBaseUris, String preferredPrefix,
			String uri) {

		if (uri.startsWith("urn:")) {
			return uri.substring(4);
		}

		for (String baseUri : ontologyBaseUris) {
			if (uri.startsWith(baseUri) && preferredPrefix != null) {
				return preferredPrefix + ":" + uri.substring(baseUri.length());
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
