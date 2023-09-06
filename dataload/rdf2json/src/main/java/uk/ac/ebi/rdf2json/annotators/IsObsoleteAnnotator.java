package uk.ac.ebi.rdf2json.annotators;

import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;
import uk.ac.ebi.rdf2json.properties.PropertyValueURI;

import java.util.List;

public class IsObsoleteAnnotator {

	public static void annotateIsObsolete(OntologyGraph graph) {

		long startTime3 = System.nanoTime();

		for(String id : graph.nodes.keySet()) {
		    OntologyNode c = graph.nodes.get(id);

		    if (c.types.contains(OntologyNode.NodeType.CLASS) ||
				c.types.contains(OntologyNode.NodeType.PROPERTY) ||
				c.types.contains(OntologyNode.NodeType.INDIVIDUAL)) {

			// skip bnodes
			if(c.uri == null)
				continue;
			}

			c.properties.addProperty("isObsolete",
					PropertyValueLiteral.fromString(isEntityObsolete(c) ? "true" : "false"));
		}

		long endTime3 = System.nanoTime();
		System.out.println("annotate isObsolete: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
	}

	private static boolean isEntityObsolete(OntologyNode node) {

		// 1. is owl:deprecated true?

		PropertyValue deprecated = node.properties.getPropertyValue("http://www.w3.org/2002/07/owl#deprecated");

		if(deprecated != null &&
				  deprecated.getType() == PropertyValue.Type.LITERAL &&
				  ((PropertyValueLiteral) deprecated).getValue().equals("true")) {
			 return true;
		}


		// 2. is the class a direct subClassOf oboInOwl:ObsoleteClass?

		List<PropertyValue> parents = node.properties.getPropertyValues("http://www.w3.org/2000/01/rdf-schema#subClassOf");

		if(parents != null) {
			for(PropertyValue parent : parents) {
				if(parent.getType() == PropertyValue.Type.URI &&
						((PropertyValueURI) parent).getUri().equals("http://www.geneontology.org/formats/oboInOwl#ObsoleteClass")) {
					return true;
				}
			}
		}


		return false;
	}
}
