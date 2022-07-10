package uk.ac.ebi.owl2json.operations;
import java.util.List;

import org.apache.jena.graph.Node;

import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlTranslator;

public class AxiomEvaluator {

	public static void evaluateAxioms(OwlTranslator translator) {

		long startTime3 = System.nanoTime();
		for(String id : translator.nodes.keySet()) {
		    OwlNode c = translator.nodes.get(id);
		    if (c.type == OwlNode.NodeType.AXIOM) {

	
			List<OwlNode.Property> sourceProp = c.properties.properties.get("http://www.w3.org/2002/07/owl#annotatedSource");
			assert(sourceProp.size() == 1);
			Node source = sourceProp.get(0).value;

                //System.out.println("sourceProp " + source);
	
			List<OwlNode.Property> propertyProp = c.properties.properties.get("http://www.w3.org/2002/07/owl#annotatedProperty");
			assert(propertyProp.size() == 1);
			String property = propertyProp.get(0).value.toString();

			// Why is this null when it's a BNode?
			List<OwlNode.Property> targetProp = c.properties.properties.get("http://www.w3.org/2002/07/owl#annotatedTarget");
			assert(targetProp.size() == 1);
			Node target = targetProp.get(0).value;
	
			OwlNode sourceNode = translator.nodes.get(translator.nodeId(source));
	
			for(String p2 : c.properties.properties.keySet()) {
			    List<OwlNode.Property> v2 = c.properties.properties.get(p2);
			    for(OwlNode.Property prop : v2) {
				if(!p2.equals("http://www.w3.org/2002/07/owl#annotatedSource")
					&& !p2.equals("http://www.w3.org/2002/07/owl#annotatedProperty")
					&& !p2.equals("http://www.w3.org/2002/07/owl#annotatedTarget")) {
				    sourceNode.properties.annotateProperty(property, target, p2, prop.value);
				}
			    }
			}
		    }
		}
		long endTime3 = System.nanoTime();
		System.out.println("reification: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000) + " - now have " + translator.nodes.size() + " nodes");

	}
	
}
