package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.properties.PropertyValue;
import uk.ac.ebi.owl2json.properties.PropertyValueLiteral;
import uk.ac.ebi.owl2json.properties.PropertyValueURI;

import java.util.List;

public class IsObsoleteAnnotator {

	public static void annotateIsObsolete(OwlGraph graph) {

		long startTime3 = System.nanoTime();

		for(String id : graph.nodes.keySet()) {
		    OwlNode c = graph.nodes.get(id);

		    if (c.types.contains(OwlNode.NodeType.CLASS) ||
				c.types.contains(OwlNode.NodeType.PROPERTY) ||
				c.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {

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

	private static boolean isEntityObsolete(OwlNode node) {

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
