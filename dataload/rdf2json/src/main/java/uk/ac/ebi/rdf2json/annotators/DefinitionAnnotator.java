package uk.ac.ebi.rdf2json.annotators;

import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueList;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;

import java.util.*;

import static uk.ac.ebi.ols.shared.DefinedFields.DEFINITION;

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
		collateProperties(graph, DEFINITION.getText(), getDefinitionProperties(graph));
	}

	private static void collateProperties(OntologyGraph graph, String destProp, Collection<String> sourceProps) {


		for(String id : graph.nodes.keySet()) {
			OntologyNode c = graph.nodes.get(id);

			// skip bnodes
			if(c.uri == null)
				continue;

			List<PropertyValue> listOfValues = new ArrayList<>();
			for(String prop : sourceProps) {
				List<PropertyValue> values = c.properties.getPropertyValues(prop);
				if(values != null) {
					for(PropertyValue value : values) {
						listOfValues.add(value);
					}
				}
			}
			if (listOfValues.size() > 0)
				c.properties.addProperty(destProp, new PropertyValueList(listOfValues));
		}

	}
}
