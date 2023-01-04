package uk.ac.ebi.spot.ols.service;

import org.apache.solr.common.StringUtils;

public class ShortFormExtractor {

    public static String extractShortForm(String iri) {

        // special case for URN schemes: https://www.w3.org/Addressing/URL/URI_URN.html
        if (iri.startsWith("urn:")) {
            return iri.substring(4);
        }

        return iri.substring(
                Math.max(
                        iri.lastIndexOf('#'),
                        iri.lastIndexOf('/')
                ) + 1
        );
    }

}
