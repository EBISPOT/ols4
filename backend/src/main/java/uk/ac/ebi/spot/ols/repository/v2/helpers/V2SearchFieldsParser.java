package uk.ac.ebi.spot.ols.repository.v2.helpers;

import uk.ac.ebi.spot.ols.repository.solr.SearchType;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class V2SearchFieldsParser {

    public static void addSearchFieldsToQuery(OlsSolrQuery query, String searchFields) {

        if(searchFields == null) {
            query.addSearchField("iri", 1, SearchType.WHITESPACE_EDGES);
            query.addSearchField("ontologyId", 1, SearchType.WHITESPACE_EDGES);
            query.addSearchField("curie", 1, SearchType.WHITESPACE_EDGES);
            query.addSearchField("shortForm", 1, SearchType.WHITESPACE_EDGES);
            query.addSearchField("label", 1, SearchType.WHITESPACE_EDGES);
            query.addSearchField("id", 1, SearchType.WHITESPACE_EDGES);
            query.addSearchField("oboId", 1, SearchType.WHITESPACE_EDGES);
            query.addSearchField("synonym", 1, SearchType.WHITESPACE_EDGES);
            query.addSearchField("searchableAnnotationValues", 1, SearchType.WHITESPACE_EDGES);
        } else {
            for (ParsedField field : parseFieldsString(searchFields)) {
                query.addSearchField(field.property, field.weight, SearchType.CASE_INSENSITIVE_TOKENS);
            }
        }
    }

    public static void addBoostFieldsToQuery(OlsSolrQuery query, String boostFields) {

        if(boostFields == null) {
            query.addBoostField("type", "ontology", 10, SearchType.WHOLE_FIELD);
            query.addBoostField("isDefiningOntology", "true", 1000, SearchType.WHOLE_FIELD);
            query.addBoostField("label", query.getSearchText(), 1000, SearchType.WHOLE_FIELD);
            query.addBoostField("label", query.getSearchText(), 500, SearchType.EDGES);
            query.addBoostField("curie", query.getSearchText(), 500, SearchType.EDGES);
            query.addBoostField("shortForm", query.getSearchText(), 500, SearchType.EDGES);
            query.addBoostField("synonym", query.getSearchText(), 500, SearchType.WHOLE_FIELD);
//            query.addBoostField("synonym", query.getSearchText(), 100, SearchType.EDGES);
        } else {
            for (ParsedField field : parseFieldsString(boostFields)) {
                query.addBoostField(field.property, field.value, field.weight, SearchType.CASE_INSENSITIVE_TOKENS);
            }
        }
    }

    public static void addFacetFieldsToQuery(OlsSolrQuery query, String facetFields) {

        if(facetFields != null) {
            for(ParsedField field : parseFieldsString(facetFields)) {
                query.addFacetField(field.property);
            }
        }
    }

    private static Collection<ParsedField> parseFieldsString(String searchFields) {

        List<ParsedField> parsed = new ArrayList<>();

        for(String fieldSpecification : searchFields.split("\\s")) {

            String[] propertyAndWeight = fieldSpecification.split("\\^");

            if(propertyAndWeight.length == 1) {

                // not weighted

                String[] propertyAndMaybeValue = propertyAndWeight[0].split(":");
                if(propertyAndMaybeValue.length == 2) {
                    parsed.add(new ParsedField(propertyAndMaybeValue[0], propertyAndMaybeValue[1], 1));
                } else {
                    parsed.add(new ParsedField(propertyAndMaybeValue[0], null, 1));
                }

            } else if(propertyAndWeight.length == 2) {

                // weighted

                String property = propertyAndWeight[0];
                int weight = Integer.parseInt(propertyAndWeight[1]);

                String[] propertyAndMaybeValue = propertyAndWeight[0].split(":");
                if(propertyAndMaybeValue.length == 2) {
                    parsed.add(new ParsedField(propertyAndMaybeValue[0], propertyAndMaybeValue[1], 1));
                } else {
                    parsed.add(new ParsedField(propertyAndMaybeValue[0], null, 1));
                }

            } else {
                throw new IllegalArgumentException("invalid search field specification");
            }
        }

        return parsed;
    }

    private static class ParsedField {
        String property;
        String value;
        int weight;

        public ParsedField(String property, String value, int weight) {
            this.property = property;
            this.value = value;
            this.weight = weight;
        }
    }



}
