
package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.properties.PropertyValue;
import uk.ac.ebi.owl2json.properties.PropertyValueLiteral;

import java.util.List;

public class HasIndividualsAnnotator {

    public static void annotateHasIndividuals(OwlGraph graph) {

        long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);
            if (c.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {

                // skip bnodes
                if(c.uri == null)
                    continue;

                List<PropertyValue> types = c.properties.getPropertyValues("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");

                if(types != null) {
                    for(PropertyValue type : types) {

                        OwlNode typeNode = graph.getNodeForPropertyValue(type);

                        if(typeNode != null
                                && typeNode.types.contains(OwlNode.NodeType.CLASS)
                                && typeNode.uri != null) {

                            typeNode.properties.addProperty("hasIndividuals", PropertyValueLiteral.fromString("true"));
                        }
                    }
                }
            }


        }
        long endTime3 = System.nanoTime();
        System.out.println("annotate has individuals: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }
}
