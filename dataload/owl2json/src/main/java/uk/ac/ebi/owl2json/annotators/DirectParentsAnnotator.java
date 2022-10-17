package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.annotators.helpers.PropertyCollator;
import uk.ac.ebi.owl2json.properties.PropertyValue;
import uk.ac.ebi.owl2json.properties.PropertyValueLiteral;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class DirectParentsAnnotator {

    public static void annotateDirectParents(OwlGraph graph) {

        long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);
            if (c.types.contains(OwlNode.NodeType.CLASS) ||
                    c.types.contains(OwlNode.NodeType.PROPERTY) ||
                    c.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {

                // skip bnodes
                if(c.uri == null)
                    continue;

                List<PropertyValue> parents = c.properties.getPropertyValues("http://www.w3.org/2000/01/rdf-schema#subClassOf");

                if(parents != null) {
                    for(PropertyValue parent : parents) {
                        if(parent.getType() == PropertyValue.Type.URI) {
                            c.properties.addProperty("directParent", parent);
                        }
                    }
                }
            }
        }
        long endTime3 = System.nanoTime();
        System.out.println("annotate direct parents: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));

    }


}
