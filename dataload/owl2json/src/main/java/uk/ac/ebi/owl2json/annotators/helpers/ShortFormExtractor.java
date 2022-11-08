package uk.ac.ebi.owl2json.annotators.helpers;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;

import java.util.Set;

public class ShortFormExtractor {

    public static String extractShortForm(OwlGraph graph, Set<String> ontologyBaseUris, String preferredPrefix, String uri) {

        if(uri.startsWith("urn:")) {
            return uri.substring(4);
        }

        if(uri.startsWith("http://purl.obolibrary.org/obo/")) {
            return uri.substring("http://purl.obolibrary.org/obo/".length());
        }

        for (String baseUri :ontologyBaseUris) {
            if (uri.startsWith(baseUri) && preferredPrefix != null) {
                return preferredPrefix + "_" + uri.substring(baseUri.length());
            }
        }

        int lastHash = uri.lastIndexOf('#');
        if(lastHash != -1) {
            return uri.substring(lastHash + 1);
        }

        int lastSlash = uri.lastIndexOf('/');
        if(lastSlash != -1) {
            return uri.substring(lastSlash + 1);
        }

        return uri;
    }


}
