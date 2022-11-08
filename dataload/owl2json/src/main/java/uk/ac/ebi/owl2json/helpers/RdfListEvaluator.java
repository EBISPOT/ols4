package uk.ac.ebi.owl2json.helpers;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.properties.PropertyValue;
import uk.ac.ebi.owl2json.properties.PropertyValueURI;

import java.util.ArrayList;
import java.util.List;

public class RdfListEvaluator {

    public static List<PropertyValue> evaluateRdfList(OwlNode listNode, OwlGraph graph) {

        List<PropertyValue> res = new ArrayList<>();

        for(OwlNode cur = listNode;;) {

            PropertyValue first = cur.properties.getPropertyValue("http://www.w3.org/1999/02/22-rdf-syntax-ns#first");
            res.add(first);

            PropertyValue rest = cur.properties.getPropertyValue("http://www.w3.org/1999/02/22-rdf-syntax-ns#rest");

            if(rest.getType() == PropertyValue.Type.URI &&
                    ((PropertyValueURI) rest).getUri().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#nil")) {
                break;
            }

            cur = graph.nodes.get(graph.nodeIdFromPropertyValue(rest));
        }

        return res;
    }
}
