package uk.ac.ebi.rdf2json.annotators;
import java.util.*;

import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.annotators.helpers.PropertyCollator;
import uk.ac.ebi.rdf2json.properties.PropertyValue;

public class SynonymAnnotator {

	public static Set<String> getSynonymProperties(OntologyGraph graph) {

		Set<String> synonymProperties = new TreeSet<>(
				List.of(
						"http://www.geneontology.org/formats/oboInOwl#hasExactSynonym",
						"http://www.geneontology.org/formats/oboInOwl#hasRelatedSynonym",
						"http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym",
						"http://www.geneontology.org/formats/oboInOwl#hasBroadSynonym",
						"http://www.geneontology.org/formats/oboInOwl#hasSynonym"
				)
		);

		Object configSynonymProperties = graph.config.get("synonym_property");

		if(configSynonymProperties instanceof Collection<?>) {
			synonymProperties.addAll((Collection<String>) configSynonymProperties);
		}

		return synonymProperties;
	}

	public static void annotateSynonyms(OntologyGraph graph) {
		collateProperties(graph, "synonym", getSynonymProperties(graph));
	}

	private static void collateProperties(OntologyGraph graph, String destProp, Collection<String> sourceProps) {

		for(String id : graph.nodes.keySet()) {
			OntologyNode c = graph.nodes.get(id);

			// skip bnodes
			if(c.uri == null)
				continue;

			for(String prop : sourceProps) {
				List<PropertyValue> values = c.properties.getPropertyValues(prop);
				if(values != null) {
					for(PropertyValue value : values) {
						c.properties.addProperty(destProp, value);
					}
				}
			}
		}

	}
}
