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
            query.addSearchField("label", 5, Fuzziness.CASE_INSENSITIVE_SUBSTRING);
            query.addSearchField("synonym", 3, Fuzziness.CASE_INSENSITIVE_SUBSTRING);
            query.addSearchField("definition", 1, Fuzziness.CASE_INSENSITIVE_SUBSTRING);
            query.addSearchField("shortForm", 2, Fuzziness.CASE_INSENSITIVE_SUBSTRING);
            query.addSearchField("iri", 1, Fuzziness.CASE_INSENSITIVE_SUBSTRING);
        } else {
            for (ParsedField field : parseFieldsString(searchFields)) {
                query.addSearchField(field.property, field.weight, Fuzziness.CASE_INSENSITIVE_SUBSTRING);
            }
        }
    }

    public static void addBoostFieldsToQuery(OlsSolrQuery query, String boostFields) {

        if(boostFields == null) {
            query.addBoostField("type", "ontology", 10, Fuzziness.CASE_INSENSITIVE_SUBSTRING);
            query.addBoostField("isDefiningOntology", "true", 100, Fuzziness.CASE_INSENSITIVE_SUBSTRING);
            query.addBoostField("label", query.getSearchText(), 5, Fuzziness.EXACT);
            query.addBoostField("synonym", query.getSearchText(), 3, Fuzziness.EXACT);
        } else {
            for (ParsedField field : parseFieldsString(boostFields)) {
                query.addBoostField(field.property, field.value, field.weight, Fuzziness.CASE_INSENSITIVE_SUBSTRING);
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
