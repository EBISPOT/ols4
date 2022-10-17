package uk.ac.ebi.owl2json.annotators.helpers;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.properties.PropertyValue;


public class ClassExpressionIRIExtractor {

    // Very dumb evaluator to extract IRIs from class expressions
	//
    // Currently kind of duplicated in the flattener, need to decide whether
	// this is a problem
    //
    public static Set<String> extractIRIsFromClassExpression(OwlGraph graph, OwlNode node) {

	if(node.types.contains(OwlNode.NodeType.RESTRICTION)) {

		List<PropertyValue> hasValue = node.properties.getPropertyValues("http://www.w3.org/2002/07/owl#hasValue");
		if(hasValue != null && hasValue.size() > 0) {
			return extractIRIsFromClassExpression(graph, hasValue.get(0));
		}

		List<PropertyValue> someValuesFrom = node.properties.getPropertyValues("http://www.w3.org/2002/07/owl#someValuesFrom");
		if(someValuesFrom != null && someValuesFrom.size() > 0) {
			return extractIRIsFromClassExpression(graph, someValuesFrom.get(0));
		}

		List<PropertyValue> allValuesFrom = node.properties.getPropertyValues("http://www.w3.org/2002/07/owl#allValuesFrom");
		if(allValuesFrom != null && allValuesFrom.size() > 0) {
			return extractIRIsFromClassExpression(graph, allValuesFrom.get(0));
		}

	} else if(node.types.contains(OwlNode.NodeType.CLASS)) {

		List<PropertyValue> oneOf = node.properties.getPropertyValues("http://www.w3.org/2002/07/owl#oneOf");
		if(oneOf != null && oneOf.size() > 0) {
			LinkedHashSet<String> uris = new LinkedHashSet<>();
			for(PropertyValue prop : oneOf) {
				uris.addAll( extractIRIsFromClassExpression(graph, prop) );
			}
			return uris;
		}

		List<PropertyValue> intersectionOf = node.properties.getPropertyValues("http://www.w3.org/2002/07/owl#intersectionOf");
		if(intersectionOf != null && intersectionOf.size() > 0) {
			LinkedHashSet<String> uris = new LinkedHashSet<>();
			for(PropertyValue prop : intersectionOf) {
				uris.addAll( extractIRIsFromClassExpression(graph, prop) );
			}
			return uris;
		}

		List<PropertyValue> unionOf = node.properties.getPropertyValues("http://www.w3.org/2002/07/owl#unionOf");
		if(unionOf != null && unionOf.size() > 0) {
			LinkedHashSet<String> uris = new LinkedHashSet<>();
			for(PropertyValue prop : unionOf) {
				uris.addAll( extractIRIsFromClassExpression(graph, prop) );
			}
			return uris;
		}
 
	}

        // TODO
	// Could be: cardinality, complementOf, any others we don't deal with yet...
        //

	// not an expression - we should recursively end up here!
	//
	return node.uri != null ? Set.of(node.uri) : Set.of();
    }
    
    public static Set<String> extractIRIsFromClassExpression(OwlGraph graph, PropertyValue propVal) {

	switch(propVal.getType()) {
		case BNODE:
		case URI:
			return extractIRIsFromClassExpression(graph, graph.nodes.get(graph.nodeIdFromPropertyValue(propVal)) );
		case ID:
		case LITERAL:
		default:
			return Set.of();

	}
    }
	
}
