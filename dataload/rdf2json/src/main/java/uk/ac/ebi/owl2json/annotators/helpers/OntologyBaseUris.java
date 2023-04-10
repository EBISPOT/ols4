package uk.ac.ebi.rdf2json.annotators.helpers;

import uk.ac.ebi.rdf2json.OntologyGraph;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class OntologyBaseUris {

    public static Set<String> getOntologyBaseUris(OntologyGraph graph) {

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
