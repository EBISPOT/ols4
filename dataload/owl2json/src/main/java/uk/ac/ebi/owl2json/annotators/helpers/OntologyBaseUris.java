package uk.ac.ebi.owl2json.annotators.helpers;

import uk.ac.ebi.owl2json.OwlGraph;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class OntologyBaseUris {

    public static Set<String> getOntologyBaseUris(OwlGraph graph) {

        Set<String> ontologyBaseUris = new HashSet<String>();

        Object configBaseUris = graph.config.get("baseUris");

        if(configBaseUris instanceof Collection<?>) {
            ontologyBaseUris.addAll((Collection<String>) configBaseUris);
        }

        String preferredPrefix = (String)graph.config.get("preferredPrefix");

        if(preferredPrefix != null) {
            ontologyBaseUris.add("http://purl.obolibrary.org/obo/" + preferredPrefix + "_");
        }

        return ontologyBaseUris;
    }
}
