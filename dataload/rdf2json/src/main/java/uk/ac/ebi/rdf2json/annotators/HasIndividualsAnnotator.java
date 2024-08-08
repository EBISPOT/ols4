
package uk.ac.ebi.rdf2json.annotators;

import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;

import java.util.List;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

public class HasIndividualsAnnotator {

    public static void annotateHasIndividuals(OntologyGraph graph) {

        long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {
            OntologyNode c = graph.nodes.get(id);
            if (c.types.contains(OntologyNode.NodeType.INDIVIDUAL)) {

                // skip bnodes
                if(c.uri == null)
                    continue;

                List<PropertyValue> types = c.properties.getPropertyValues("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");

                if(types != null) {
                    for(PropertyValue type : types) {

                        OntologyNode typeNode = graph.getNodeForPropertyValue(type);

                        if(typeNode != null
                                && typeNode.types.contains(OntologyNode.NodeType.CLASS)
                                && typeNode.uri != null) {

                            typeNode.properties.addProperty(HAS_INDIVIDUALS.getText(), PropertyValueLiteral.fromBoolean("true"));
                        }
                    }
                }
            }


        }
        long endTime3 = System.nanoTime();
        System.out.println("annotate has individuals: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }
}
