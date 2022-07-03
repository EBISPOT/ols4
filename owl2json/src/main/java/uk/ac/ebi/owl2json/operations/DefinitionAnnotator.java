package uk.ac.ebi.owl2json.operations;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlTranslator;

public class DefinitionAnnotator {

	public static void annotateDefinitions(OwlTranslator translator) {

		long startTime3 = System.nanoTime();


		Set<String> definitionProperties = new HashSet<String>();
		definitionProperties.add("http://www.w3.org/2000/01/rdf-schema#description");
		definitionProperties.add("http://www.w3.org/2000/01/rdf-schema#comment");
		definitionProperties.add("http://purl.obolibrary.org/obo/IAO_0000115");

		Object configDefinitionProperties = translator.config.get("definition_property");

		if(configDefinitionProperties instanceof Collection<?>) {
			definitionProperties.addAll((Collection<String>) configDefinitionProperties);
		}


		for(String id : translator.nodes.keySet()) {
		    OwlNode c = translator.nodes.get(id);
		    if (c.type == OwlNode.NodeType.CLASS ||
				c.type == OwlNode.NodeType.PROPERTY ||
				c.type == OwlNode.NodeType.NAMED_INDIVIDUAL) {

			// skip bnodes
			if(c.uri == null)
				continue;

			for(String prop : definitionProperties) {
				List<OwlNode.Property> values = c.properties.properties.get(prop);
				if(values != null) {
					for(OwlNode.Property value : values) {
						c.properties.addProperty("https://github.com/EBISPOT/owl2neo#definition", value.value);
					}
				}
			}
		    }
		}
		long endTime3 = System.nanoTime();
		System.out.println("annotate definitions: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}
}
