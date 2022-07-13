package uk.ac.ebi.owl2json.operations;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.NodeFactory;

import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlTranslator;

public class TypesAnnotator {

	public static void annotateTypes(OwlTranslator translator) {

		long startTime3 = System.nanoTime();


		for(String id : translator.nodes.keySet()) {
		    OwlNode c = translator.nodes.get(id);
		    if ( c.type == OwlNode.NodeType.ONTOLOGY) {
                c.properties.addProperty(
                        "type",
                        NodeFactory.createLiteral("ontology"));
            }
            else if ( c.type == OwlNode.NodeType.CLASS) {
                c.properties.addProperty(
                        "type",
                        NodeFactory.createLiteral("class"));
            }
            else if ( c.type == OwlNode.NodeType.PROPERTY) {
                c.properties.addProperty(
                        "type",
                        NodeFactory.createLiteral("property"));
            }
            else if ( c.type == OwlNode.NodeType.NAMED_INDIVIDUAL) {
                c.properties.addProperty(
                        "type",
                        NodeFactory.createLiteral("individual"));
            }
		}

		long endTime3 = System.nanoTime();
		System.out.println("annotate types: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}
}

