package uk.ac.ebi.rdf2json.annotators;
import java.util.*;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.annotators.helpers.PropertyCollator;

public class DefinitionAnnotator {

	public static Set<String> getDefinitionProperties(OntologyGraph graph) {

		Set<String> definitionProperties = new TreeSet<>(
				List.of(
						"http://www.w3.org/2000/01/rdf-schema#comment",
						"http://purl.obolibrary.org/obo/IAO_0000115",
						"http://purl.org/dc/terms/description",
						"http://purl.org/dc/elements/1.1/description"
				)
		);

		Object configDefinitionProperties = graph.config.get("definition_property");

		if(configDefinitionProperties instanceof Collection<?>) {
			definitionProperties.addAll((Collection<String>) configDefinitionProperties);
		}

		return definitionProperties;
	}

	public static void annotateDefinitions(OntologyGraph graph) {
		PropertyCollator.collateProperties(graph, "definition", getDefinitionProperties(graph), List.of());
	}
}
