package uk.ac.ebi.owl2json.operations;

import java.util.List;

import org.apache.jena.graph.Node;

import uk.ac.ebi.owl2json.OwlTranslator;


public class ClassExpressionEvaluator {

	// turn bnode types (Restrictions, Classes with oneOf etc) into direct edges
	//
	public static void evaluateClassExpressions(OwlTranslator translator) {


		long startTime4 = System.nanoTime();

		for(String id : translator.nodes.keySet()) {
		OwlNode c = translator.nodes.get(id);

		// skip BNodes; we are looking for things with BNodes as types, not the BNodes themselves
		if(c.uri == null)
			continue;

			List<OwlNode.Property> types = c.properties.properties.get("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");

            if(types != null) {
                for(OwlNode.Property type : types) {
                    OwlNode typeNode = translator.nodes.get(translator.nodeId(type.value));

                    // Is the type a BNode?
                    if(typeNode != null && typeNode.uri == null) {
                        evaluateTypeExpression(translator, c, type);
                    }
                }
            }
		}



		long endTime4 = System.nanoTime();
		System.out.println("evaluate restrictions: " + ((endTime4 - startTime4) / 1000 / 1000 / 1000));
	}

    private static void evaluateTypeExpression(OwlTranslator translator, OwlNode node, OwlNode.Property typeProperty) {

	OwlNode typeNode = translator.nodes.get(translator.nodeId(typeProperty.value));

	if(typeNode != null && typeNode.type == OwlNode.NodeType.RESTRICTION) {

		List<OwlNode.Property> hasValue = typeNode.properties.properties.get("http://www.w3.org/2002/07/owl#hasValue");
		if(hasValue != null && hasValue.size() > 0) {
			evaluateTypeExpression(translator, node, hasValue.get(0));
			return;
		}

		List<OwlNode.Property> someValuesFrom = typeNode.properties.properties.get("http://www.w3.org/2002/07/owl#someValuesFrom");
		if(someValuesFrom != null && someValuesFrom.size() > 0) {
			evaluateTypeExpression(translator, node, someValuesFrom.get(0));
			return;
		}

		List<OwlNode.Property> allValuesFrom = typeNode.properties.properties.get("http://www.w3.org/2002/07/owl#allValuesFrom");
		if(allValuesFrom != null && allValuesFrom.size() > 0) {
			evaluateTypeExpression(translator, node, allValuesFrom.get(0));
			return;
		}

	} else if(typeNode != null && typeNode.type == OwlNode.NodeType.CLASS) {

		List<OwlNode.Property> oneOf = typeNode.properties.properties.get("http://www.w3.org/2002/07/owl#oneOf");
		if(oneOf != null && oneOf.size() > 0) {
			for(OwlNode.Property prop : oneOf) {
				evaluateTypeExpression(translator, node, prop);
			}
			return;
		}

	}

	// not an expression - we should recursively end up here!
	//
	node.properties.addProperty("relatedTo", typeProperty.value);
    }
	
}
