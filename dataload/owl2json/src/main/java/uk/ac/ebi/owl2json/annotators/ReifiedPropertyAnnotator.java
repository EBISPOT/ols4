package uk.ac.ebi.owl2json.annotators;
import java.util.List;

import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.properties.*;

public class ReifiedPropertyAnnotator {

	public static void annotateReifiedProperties(OwlGraph graph) {

		long startTime3 = System.nanoTime();
		for(String id : graph.nodes.keySet()) {
		    OwlNode c = graph.nodes.get(id);
			if (c.types.contains(OwlNode.NodeType.AXIOM)) {

				PropertyValue source = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#annotatedSource");
				PropertyValue property = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#annotatedProperty");
				PropertyValue target = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#annotatedTarget");

				String propertyUri = ((PropertyValueURI) property).getUri();

				OwlNode sourceNode = graph.nodes.get(graph.nodeIdFromPropertyValue(source));

				PropertySet axiom = new PropertySet();

				for (String p2 : c.properties.getPropertyPredicates()) {
					List<PropertyValue> v2 = c.properties.getPropertyValues(p2);
					for (PropertyValue prop : v2) {
						if (!p2.equals("http://www.w3.org/2002/07/owl#annotatedSource")
								&& !p2.equals("http://www.w3.org/2002/07/owl#annotatedProperty")
								&& !p2.equals("http://www.w3.org/2002/07/owl#annotatedTarget")
								&& !p2.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")) {
							axiom.addProperty(p2, prop);
						}
					}
				}

				sourceNode.properties.annotatePropertyWithAxiom(propertyUri, target, axiom, graph);
			}
		}
		long endTime3 = System.nanoTime();
		System.out.println("reification: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000) + " - now have " + graph.nodes.size() + " nodes");

	}
	
}
