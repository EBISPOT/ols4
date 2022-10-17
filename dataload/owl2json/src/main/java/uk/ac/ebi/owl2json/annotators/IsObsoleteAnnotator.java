package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.annotators.helpers.ClassExpressionIRIExtractor;
import uk.ac.ebi.owl2json.properties.PropertyValue;
import uk.ac.ebi.owl2json.properties.PropertyValueLiteral;

import java.util.List;
import java.util.Set;

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

			PropertyValue deprecated = c.properties.getPropertyValue("http://www.w3.org/2002/07/owl#deprecated");

			if(deprecated != null &&
					deprecated.getType() == PropertyValue.Type.LITERAL &&
					 ((PropertyValueLiteral) deprecated).getValue().equals("true")) {
				c.properties.addProperty("isObsolete", PropertyValueLiteral.fromString("true"));
				continue;
			}
		    }

			List<PropertyValue> parents = c.properties.getPropertyValues("http://www.w3.org/2000/01/rdf-schema#subClassOf");

			if(parents != null) {
				for(PropertyValue parent : parents) {

					 Set<String> uris = ClassExpressionIRIExtractor.extractIRIsFromClassExpression(graph, parent);

					 if(uris.contains("http://www.geneontology.org/formats/oboInOwl#ObsoleteClass")) {
						  c.properties.addProperty("isObsolete", PropertyValueLiteral.fromString("true"));
						  break;
					 }
				}
			}
		}
		long endTime3 = System.nanoTime();
		System.out.println("annotate isObsolete: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}
}
