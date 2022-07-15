package uk.ac.ebi.owl2json.operations;

import java.util.List;

import org.apache.jena.graph.Node;

import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.util.NodeUtils;
import uk.ac.ebi.owl2json.OwlTranslator;


public class ClassExpressionEvaluator {

	// turn bnode types (Restrictions, Classes with oneOf etc) into direct edges
	//
	public static void evaluateClassExpressions(OwlTranslator translator) {

		long startTime4 = System.nanoTime();

		for(ResIterator it = translator.model.listSubjects(); it.hasNext();) {

			Resource res = it.next();

			// skip BNodes; we are looking for things with BNodes as types, not the BNodes themselves
			if (!res.isURIResource())
				continue;

			List<Node> types = translator.graph.find(
					res.asNode(),
					NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
					Node.ANY
			).mapWith(t -> t.getObject()).toList();

			for(Node type : types) {
				// Is the type a BNode?
				if(!type.isURI()) {
					evaluateTypeExpression(translator, res.asNode(), type);
				}
			}
		}



		long endTime4 = System.nanoTime();
		System.out.println("evaluate restrictions: " + ((endTime4 - startTime4) / 1000 / 1000 / 1000));
	}

    private static void evaluateTypeExpression(OwlTranslator translator, Node node, Node typeNode) {

		List<Node> typeTypes = translator.graph.find(
				typeNode,
				NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
				Node.ANY
		).mapWith(t -> t.getObject()).toList();

		if(typeTypes.contains("http://www.w3.org/2002/07/owl#Restriction")) {

			List<Node> hasValue = translator.graph.find(
					typeNode,
					NodeUtils.asNode("http://www.w3.org/2002/07/owl#hasValue"),
					Node.ANY
			).mapWith(t -> t.getObject()).toList();

			if(hasValue != null && hasValue.size() > 0) {
				evaluateTypeExpression(translator, node, hasValue.get(0));
				return;
			}

			List<Node> someValuesFrom = translator.graph.find(
					typeNode,
					NodeUtils.asNode("http://www.w3.org/2002/07/owl#someValuesFrom"),
					Node.ANY
			).mapWith(t -> t.getObject()).toList();

			if(someValuesFrom != null && someValuesFrom.size() > 0) {
				evaluateTypeExpression(translator, node, someValuesFrom.get(0));
				return;
			}

			List<Node> allValuesFrom = translator.graph.find(
					typeNode,
					NodeUtils.asNode("http://www.w3.org/2002/07/owl#allValuesFrom"),
					Node.ANY
			).mapWith(t -> t.getObject()).toList();

			if(allValuesFrom != null && allValuesFrom.size() > 0) {
				evaluateTypeExpression(translator, node, allValuesFrom.get(0));
				return;
			}

	} else if(typeTypes.contains("http://www.w3.org/2002/07/owl#Class")) {

			List<Node> oneOf = translator.graph.find(
					typeNode,
					NodeUtils.asNode("http://www.w3.org/2002/07/owl#oneOf"),
					Node.ANY
			).mapWith(t -> t.getObject()).toList();

			if(oneOf != null && oneOf.size() > 0) {
				for(Node prop : oneOf) {
					 evaluateTypeExpression(translator, node, prop);
				}
				return;
			}

	}

	// not an expression - we should recursively end up here!
	//
		translator.graph.add(Triple.create(
				node,
				NodeUtils.asNode("relatedTo"),
				typeNode
		));
    }
	
}
