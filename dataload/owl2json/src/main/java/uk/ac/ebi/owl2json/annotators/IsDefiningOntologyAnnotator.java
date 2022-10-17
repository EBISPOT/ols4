package uk.ac.ebi.owl2json.annotators;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.properties.PropertyValueLiteral;

public class IsDefiningOntologyAnnotator {

    public static void annotateIsDefiningOntology(OwlGraph graph) {

        long startTime3 = System.nanoTime();


        Set<String> ontologyBaseUris = new HashSet<String>();

        Object configBaseUris = graph.config.get("baseUris");

        if(configBaseUris instanceof Collection<?>) {
            ontologyBaseUris.addAll((Collection<String>) configBaseUris);
        }

        String preferredPrefix = (String)graph.config.get("preferredPrefix");

        if(preferredPrefix != null) {
            ontologyBaseUris.add("http://purl.obolibrary.org/obo/" + preferredPrefix + "_");
        }

        for(String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);
            if (c.types.contains(OwlNode.NodeType.CLASS) ||
                    c.types.contains(OwlNode.NodeType.PROPERTY) ||
                    c.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {

                // skip bnodes
                if(c.uri == null)
                    continue;

                boolean isDefining = false;

                for(String baseUri : ontologyBaseUris) {
                    if(c.uri.startsWith(baseUri)) {
                        isDefining = true;
                    }
                }
                c.properties.addProperty(
                        "isDefiningOntology",
                        PropertyValueLiteral.fromString(
                               isDefining ? "true" : "false"
                        ));
            }
        }
        long endTime3 = System.nanoTime();
        System.out.println("annotate isDefiningOntology: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


    }


}
