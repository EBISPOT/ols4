package uk.ac.ebi.owl2json.transforms;
import java.util.List;

import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlTranslator;
import uk.ac.ebi.owl2json.properties.*;

public class AxiomEvaluator {

	public static void evaluateAxioms(OwlTranslator translator) {

		long startTime3 = System.nanoTime();
		for(String id : translator.nodes.keySet()) {
		    OwlNode c = translator.nodes.get(id);
			if (c.types.contains(OwlNode.NodeType.AXIOM)) {

				PropertyValue source = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#annotatedSource");
				PropertyValue property = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#annotatedProperty");
				PropertyValue target = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#annotatedTarget");

				String propertyUri = ((PropertyValueURI) property).getUri();

				OwlNode sourceNode = translator.nodes.get(translator.nodeIdFromPropertyValue(source));

				for (String p2 : c.properties.getPropertyPredicates()) {
					List<PropertyValue> v2 = c.properties.getPropertyValues(p2);
					for (PropertyValue prop : v2) {
						if (!p2.equals("http://www.w3.org/2002/07/owl#annotatedSource")
								&& !p2.equals("http://www.w3.org/2002/07/owl#annotatedProperty")
								&& !p2.equals("http://www.w3.org/2002/07/owl#annotatedTarget")) {
							sourceNode.properties.annotateProperty(propertyUri, target, p2, prop, translator);
						}
					}
				}
			}
		}
		long endTime3 = System.nanoTime();
		System.out.println("reification: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000) + " - now have " + translator.nodes.size() + " nodes");

	}
	
}
