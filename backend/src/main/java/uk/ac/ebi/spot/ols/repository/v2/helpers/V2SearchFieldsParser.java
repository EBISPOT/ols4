package uk.ac.ebi.spot.ols.repository.v2.helpers;

import uk.ac.ebi.spot.ols.repository.solr.Fuzziness;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class V2SearchFieldsParser {

    public static void addSearchFieldsToQuery(OlsSolrQuery query, String searchFields) {

        if(searchFields == null) {
            searchFields = "label^5 synonym^3 definition shortForm^2 iri"; // TODO check with OLS3
        }

        for(ParsedField field : parseFieldsString(searchFields)) {
            query.addSearchField(field.property, field.weight, Fuzziness.CASE_INSENSITIVE_SUBSTRING);
        }
    }

    public static void addBoostFieldsToQuery(OlsSolrQuery query, String boostFields) {

        if(boostFields == null) {
            boostFields = "type:ontology^10 isDefiningOntology:true^100"; // TODO check with OLS3
        }

        for(ParsedField field : parseFieldsString(boostFields)) {
            query.addBoostField(field.property, field.weight, Fuzziness.CASE_INSENSITIVE_SUBSTRING);
        }
    }

    private static Collection<ParsedField> parseFieldsString(String searchFields) {

        List<ParsedField> parsed = new ArrayList<>();

        for(String fieldSpecification : searchFields.split("\\s")) {

            String[] propertyAndWeight = fieldSpecification.split("\\^");

            if(propertyAndWeight.length == 1) {

                // just a property

                String property = propertyAndWeight[0];
                parsed.add(new ParsedField(property, 1));

            } else if(propertyAndWeight.length == 2) {

                // property and weight

                String property = propertyAndWeight[0];
                int weight = Integer.parseInt(propertyAndWeight[1]);

                parsed.add(new ParsedField(property, weight));

            } else {
                throw new IllegalArgumentException("invalid search field specification");
            }
        }

        return parsed;
    }

    private static class ParsedField {
        String property;
        int weight;

        public ParsedField(String property, int weight) {
            this.property = property;
            this.weight = weight;
        }
    }



}
