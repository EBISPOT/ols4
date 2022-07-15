package uk.ac.ebi.owl2json.operations;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;

import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.util.NodeUtils;
import uk.ac.ebi.owl2json.OwlTranslator;

public class HierarchyAnnotator {

	public static void annotateHierarchy(OwlTranslator translator) {

		Set<String> hierarchicalProperties = getHierarchicalProperties(translator);


		long startTime3 = System.nanoTime();

		for(ResIterator it = translator.model.listSubjects(); it.hasNext();) {

			Resource res = it.next();

			if (!res.isURIResource())
				continue;

//			var types = translator.graph.find(
//					res.asNode(),
//					NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
//					Node.ANY
//			)
//					.mapWith(t -> t.getObject())
//					.filterKeep(o -> o.isURI())
//					.mapWith(o -> o.getURI())
//					.toSet();
//
//			Set<String> parents = new HashSet<>();
//			Set<String> hierarchicalParents = new HashSet<>();
//
//			for(String hierarchicalProperty : hierarchicalProperties) {
//				hierarchicalParents.addAll(
//						translator.graph.find(
//										res.asNode(),
//										NodeUtils.asNode(hierarchicalProperty),
//										Node.ANY
//								).mapWith(t -> t.getObject())
//								.filterKeep(o -> o.isURI())
//								.mapWith(o -> o.getURI())
//								.toSet()
//				);
//			}
//
//			parents.addAll(
//					translator.graph.find(
//									res.asNode(),
//									NodeUtils.asNode("http://www.w3.org/2000/01/rdf-schema#subClassOf"),
//									Node.ANY
//							).mapWith(t -> t.getObject())
//							.filterKeep(o -> o.isURI())
//							.mapWith(o -> o.getURI())
//							.toSet()
//			);

			boolean hasParents = translator.graph.contains(
					res.asNode(),
					NodeUtils.asNode("http://www.w3.org/2000/01/rdf-schema#subClassOf"),
					Node.ANY
			);

			boolean hasChildren = translator.graph.contains(
					Node.ANY,
					NodeUtils.asNode("http://www.w3.org/2000/01/rdf-schema#subClassOf"),
					res.asNode()
			);

			translator.graph.add(Triple.create(
					res.asNode(),
					NodeUtils.asNode("isRoot"),
					NodeFactory.createLiteral(hasParents ? "false" : "true")
			));

			translator.graph.add(Triple.create(
					res.asNode(),
					NodeUtils.asNode("hasChildren"),
					NodeFactory.createLiteral(hasChildren ? "true" : "false")
			));

		}


		long endTime3 = System.nanoTime();
		System.out.println("annotate hierarchy flags: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}

	private static Set<String> getHierarchicalProperties(OwlTranslator translator) {

		Set<String> hierachicalProperties = new HashSet<String>();

		Object configHierarchicalProperties = translator.config.get("hierarchical_property");

		if(configHierarchicalProperties instanceof Collection<?>) {
			hierachicalProperties.addAll((Collection<String>) configHierarchicalProperties);
		}

		return hierachicalProperties;
	}
}

