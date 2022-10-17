package uk.ac.ebi.owl2json.annotators;
import java.util.*;

import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.annotators.helpers.PropertyCollator;
import uk.ac.ebi.owl2json.properties.PropertyValue;

public class SynonymAnnotator {

	public static Set<String> getSynonymProperties(OwlGraph graph) {

		Set<String> synonymProperties = new TreeSet<>(
				List.of(
						"http://www.geneontology.org/formats/oboInOwl#hasExactSynonym"
				)
		);

		Object configSynonymProperties = graph.config.get("definition_property");

		if(configSynonymProperties instanceof Collection<?>) {
			synonymProperties.addAll((Collection<String>) configSynonymProperties);
		}

		return synonymProperties;
	}

	public static void annotateSynonyms(OwlGraph graph) {
		PropertyCollator.collateProperties(graph, "synonym", getSynonymProperties(graph));
	}
}
