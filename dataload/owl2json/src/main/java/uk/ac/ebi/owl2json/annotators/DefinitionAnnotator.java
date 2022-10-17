package uk.ac.ebi.owl2json.annotators;
import java.util.*;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.annotators.helpers.PropertyCollator;

public class DefinitionAnnotator {

	public static Set<String> getDefinitionProperties(OwlGraph graph) {

		Set<String> definitionProperties = new TreeSet<>(
				List.of(
						"http://www.w3.org/2000/01/rdf-schema#description",
						"http://www.w3.org/2000/01/rdf-schema#comment",
						"http://purl.obolibrary.org/obo/IAO_0000115"
				)
		);

		Object configDefinitionProperties = graph.config.get("definition_property");

		if(configDefinitionProperties instanceof Collection<?>) {
			definitionProperties.addAll((Collection<String>) configDefinitionProperties);
		}

		return definitionProperties;
	}

	public static void annotateDefinitions(OwlGraph graph) {
		PropertyCollator.collateProperties(graph, "definition", getDefinitionProperties(graph));
	}
}
