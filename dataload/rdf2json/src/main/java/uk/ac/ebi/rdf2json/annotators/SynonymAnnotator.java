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
		PropertyCollator.collateProperties(graph, "synonym", getSynonymProperties(graph), List.of());
	}
}
