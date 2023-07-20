package uk.ac.ebi.rdf2json.annotators;
import java.util.*;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.annotators.helpers.PropertyCollator;
import uk.ac.ebi.rdf2json.properties.PropertyValue;

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
		collateProperties(graph, "definition", getDefinitionProperties(graph));
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
