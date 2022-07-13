package uk.ac.ebi.owl2json.operations;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.util.NodeUtils;
import uk.ac.ebi.owl2json.OwlTranslator;

public class SynonymAnnotator {

	public static void annotateSynonyms(OwlTranslator translator) {

		Graph graph = translator.graph;
		Model model = translator.model;

		long startTime3 = System.nanoTime();


		Set<String> synonymProperties = new HashSet<String>();
		synonymProperties.add("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym");


		Object configSynonymProperties = translator.config.get("synonym_property");

		if(configSynonymProperties instanceof Collection<?>) {
			synonymProperties.addAll((Collection<String>) configSynonymProperties);
		}


		for(ResIterator it = translator.model.listSubjects(); it.hasNext();) {

			Resource res = it.next();

			if (!res.isURIResource())
				continue;

			for(String prop : synonymProperties) {

				var values =  graph.find(
						res.asNode(),
						NodeUtils.asNode(prop),
						Node.ANY
				)
					.mapWith(t -> t.getObject())
					.toList();


				for(Node value : values) {

					graph.add(Triple.create(
							res.asNode(),
							NodeUtils.asNode("synonym"),
							value
					));
				}
			}
		}

		long endTime3 = System.nanoTime();
		System.out.println("annotate synonyms: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}
}
