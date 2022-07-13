package uk.ac.ebi.owl2json.operations;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;

import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.util.NodeUtils;
import uk.ac.ebi.owl2json.OwlTranslator;

public class TypesAnnotator {

	public static void annotateTypes(OwlTranslator translator) {

        Graph graph = translator.graph;
        Model model = translator.model;

		long startTime3 = System.nanoTime();

        for(ResIterator it = translator.model.listSubjects(); it.hasNext();) {

            Resource res = it.next();

            if (!res.isURIResource())
                continue;

            if(graph.contains(
                    res.asNode(),
                    NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                    NodeUtils.asNode("http://www.w3.org/2002/07/owl#Ontology")
            )) {
                graph.add(Triple.create(
                        res.asNode(),
                        NodeUtils.asNode("type"),
                        NodeUtils.asNode("ontology")
                ));
            }

            if(graph.contains(
                    res.asNode(),
                    NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                    NodeUtils.asNode("http://www.w3.org/2002/07/owl#Class")
            )) {
                graph.add(Triple.create(
                        res.asNode(),
                        NodeUtils.asNode("type"),
                        NodeUtils.asNode("class")
                ));
            }

            if(
                    graph.contains(
                        res.asNode(),
                        NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                        NodeUtils.asNode("http://www.w3.org/2002/07/owl#AnnotationProperty")
                    ) ||
                    graph.contains(
                        res.asNode(),
                        NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                        NodeUtils.asNode("http://www.w3.org/2002/07/owl#ObjectProperty")
                    ) ||
                    graph.contains(
                        res.asNode(),
                        NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                        NodeUtils.asNode("http://www.w3.org/2002/07/owl#DatatypeProperty")
                    ) ||
                    graph.contains(
                        res.asNode(),
                        NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                        NodeUtils.asNode("http://www.w3.org/2002/07/owl#OntologyProperty")
                    )
            ) {
                graph.add(Triple.create(
                        res.asNode(),
                        NodeUtils.asNode("type"),
                        NodeUtils.asNode("property")
                ));
            }

            if(graph.contains(
                    res.asNode(),
                    NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                    NodeUtils.asNode("http://www.w3.org/2002/07/owl#Individual")
            )) {
                graph.add(Triple.create(
                        res.asNode(),
                        NodeUtils.asNode("type"),
                        NodeUtils.asNode("individual")
                ));
            }
        }

        long endTime3 = System.nanoTime();
		System.out.println("annotate types: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}
}

