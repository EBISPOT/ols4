package uk.ac.ebi.owl2json.operations;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.util.NodeUtils;
import org.apache.jena.util.iterator.ExtendedIterator;
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



		for(ResIterator it = translator.model.listSubjects(); it.hasNext();) {

			Resource res = it.next();

			if (!res.isURIResource())
				continue;

			for(String prop : definitionProperties) {

				List<Node> definitions = translator.graph.find(
						res.asNode(),
						NodeUtils.asNode(prop),
						Node.ANY
				).mapWith(t -> t.getObject()).toList();

				for(Node defNode : definitions) {
					translator.graph.add(Triple.create(
							res.asNode(),
							NodeUtils.asNode("definition"),
							defNode
					));
				}
		    }
		}
		long endTime3 = System.nanoTime();
		System.out.println("annotate definitions: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}
}
