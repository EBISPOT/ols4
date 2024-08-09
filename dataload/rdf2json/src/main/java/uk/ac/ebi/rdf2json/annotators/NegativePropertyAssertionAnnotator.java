package uk.ac.ebi.rdf2json.annotators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueURI;

public class NegativePropertyAssertionAnnotator {
	private static final Logger logger = LoggerFactory.getLogger(NegativePropertyAssertionAnnotator.class);

    public static void annotateNegativePropertyAssertions(OntologyGraph graph) {

		long startTime3 = System.nanoTime();
		for (String id : graph.nodes.keySet()) {
			OntologyNode c = graph.nodes.get(id);
			if (c.types.contains(OntologyNode.NodeType.NEGATIVE_PROPERTY_ASSERTION)) {

				PropertyValue sourceIndividual = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#sourceIndividual");
				PropertyValue assertionProperty = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#assertionProperty");
				PropertyValue targetIndividual = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#targetIndividual");
				PropertyValue targetValue = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#targetValue");

				if(! (assertionProperty instanceof PropertyValueURI))
					continue;

				if(! (sourceIndividual instanceof PropertyValueURI))
					continue;

				OntologyNode sourceIndividualNode = graph.getNodeForPropertyValue(sourceIndividual);

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
		logger.info("annotate negative property assertions: {}", ((endTime3 - startTime3) / 1000 / 1000 / 1000));
	}
	
}
