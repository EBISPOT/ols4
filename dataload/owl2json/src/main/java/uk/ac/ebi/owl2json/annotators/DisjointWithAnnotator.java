package uk.ac.ebi.owl2json.annotators;
import java.util.*;
import java.util.stream.Collectors;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlNode.NodeType;
import uk.ac.ebi.owl2json.annotators.helpers.PropertyCollator;
import uk.ac.ebi.owl2json.helpers.RdfListEvaluator;
import uk.ac.ebi.owl2json.properties.PropertyValue;
import uk.ac.ebi.owl2json.properties.PropertyValueURI;

public class DisjointWithAnnotator {

	public static void annotateDisjointWith(OwlGraph graph) {

		long startTime3 = System.nanoTime();

		for(String id : graph.nodes.keySet()) {
			OwlNode c = graph.nodes.get(id);

			if (c.types.contains(OwlNode.NodeType.ALL_DISJOINT_CLASSES)) {

				PropertyValue membersList  = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#members");
				List<PropertyValue> members = RdfListEvaluator.evaluateRdfList(graph.getNodeForPropertyValue(membersList), graph);

				List<OwlNode> classNodes = members.stream().map(val -> graph.getNodeForPropertyValue(val)).collect(Collectors.toList());

				for(OwlNode classNodeA : classNodes) {
					for(OwlNode classNodeB : classNodes) {
						if(classNodeB.uri != classNodeA.uri) {
							classNodeA.properties.addProperty("http://www.w3.org/2002/07/owl#disjointWith",
								PropertyValueURI.fromUri(classNodeB.uri));
						}
					}
				}

			} else if (c.types.contains(OwlNode.NodeType.ALL_DISJOINT_PROPERTIES)) {

				PropertyValue membersList  = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#members");
				List<PropertyValue> members = RdfListEvaluator.evaluateRdfList(graph.getNodeForPropertyValue(membersList), graph);

				List<OwlNode> propertyNodes = members.stream().map(val -> graph.getNodeForPropertyValue(val)).collect(Collectors.toList());

				for(OwlNode propertyNodeA : propertyNodes) {
					for(OwlNode propertyNodeB : propertyNodes) {
						if(propertyNodeB.uri != propertyNodeA.uri) {
							propertyNodeA.properties.addProperty("http://www.w3.org/2002/07/owl#propertyDisjointWith",
								PropertyValueURI.fromUri(propertyNodeB.uri));
						}
					}
				}
			
			} else if (c.types.contains(OwlNode.NodeType.ALL_DIFFERENT)) {

				PropertyValue membersList  = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#distinctMembers");
				List<PropertyValue> members = RdfListEvaluator.evaluateRdfList(graph.getNodeForPropertyValue(membersList), graph);

				List<OwlNode> individualNodes = members.stream()
					.map(val -> graph.getNodeForPropertyValue(val))
					.filter(val -> val != null)
					.collect(Collectors.toList());

				for(OwlNode individualNodeA : individualNodes) {
					for(OwlNode individualNodeB : individualNodes) {
						if(individualNodeB.uri != individualNodeA.uri) {
							individualNodeA.properties.addProperty("http://www.w3.org/2002/07/owl#differentFrom",
								PropertyValueURI.fromUri(individualNodeB.uri));
						}
					}
				}
			}
		}

		long endTime3 = System.nanoTime();
		System.out.println("annotate disjointWith: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));

	}
}
