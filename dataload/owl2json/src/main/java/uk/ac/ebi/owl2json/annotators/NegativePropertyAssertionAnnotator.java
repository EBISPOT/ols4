package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.properties.PropertyValue;
import uk.ac.ebi.owl2json.properties.PropertyValueURI;

public class NegativePropertyAssertionAnnotator {

    public static void annotateNegativePropertyAssertions(OwlGraph graph) {

		long startTime3 = System.nanoTime();
		for (String id : graph.nodes.keySet()) {
			OwlNode c = graph.nodes.get(id);
			if (c.types.contains(OwlNode.NodeType.NEGATIVE_PROPERTY_ASSERTION)) {

				PropertyValue sourceIndividual = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#sourceIndividual");
				PropertyValue assertionProperty = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#assertionProperty");
				PropertyValue targetIndividual = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#targetIndividual");
				PropertyValue targetValue = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#targetValue");

				if(! (assertionProperty instanceof PropertyValueURI))
					continue;

				if(! (sourceIndividual instanceof PropertyValueURI))
					continue;

				OwlNode sourceIndividualNode = graph.getNodeForPropertyValue(sourceIndividual);

				if(sourceIndividualNode == null)
					continue;

				if(targetIndividual != null) {
					sourceIndividualNode.properties.addProperty(
						"negativePropertyAssertion+" + ((PropertyValueURI) assertionProperty).getUri(),
						targetIndividual);
				} else if(targetValue != null) {
					sourceIndividualNode.properties.addProperty(
						"negativePropertyAssertion+" + ((PropertyValueURI) assertionProperty).getUri(),
						targetValue);
				}
			}
		}
		long endTime3 = System.nanoTime();
		System.out.println("annotate negative property assertions: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
	}
	
}
