package uk.ac.ebi.owl2json.operations;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;

import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.util.NodeUtils;
import uk.ac.ebi.owl2json.OwlTranslator;

public class OntologyIdAnnotator {

	public static void annotateOntologyIds(OwlTranslator translator) {

		long startTime3 = System.nanoTime();

		String ontologyId = (String) translator.config.get("id");


		for(ResIterator res = translator.model.listSubjects(); res.hasNext(); ) {

			Resource subjRes = res.nextResource();

			if(!subjRes.isURIResource())
				continue;

			boolean isClass = translator.graph.contains(
					subjRes.asNode(), NodeUtils.asNode("type"), NodeFactory.createLiteral("class"));

			boolean isProperty = translator.graph.contains(
					subjRes.asNode(), NodeUtils.asNode("type"), NodeFactory.createLiteral("property"));

			boolean isIndividual = translator.graph.contains(
					subjRes.asNode(), NodeUtils.asNode("type"), NodeFactory.createLiteral("individual"));

			if (isClass || isProperty || isIndividual) {
				translator.graph.add(Triple.create(
						subjRes.asNode(),
						NodeUtils.asNode("ontologyId"),
						NodeFactory.createLiteral(ontologyId)
				));
			}
		}

		long endTime3 = System.nanoTime();
		System.out.println("annotate ontology IDs: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}
}
