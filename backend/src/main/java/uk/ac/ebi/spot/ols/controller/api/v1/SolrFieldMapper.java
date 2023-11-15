package uk.ac.ebi.spot.ols.controller.api.v1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class SolrFieldMapper {
	
    // Maps OLS3 field names to the OLS4 schema
    //
    public static List<String> mapFieldsList(Collection<String> ols3FieldNames) {

        List<String> newFields = new ArrayList<>();

        for (String legacyFieldName : ols3FieldNames) {

            String prefix = "";
            String suffix = "";

            if (legacyFieldName.endsWith("_s")) {
                prefix = "lowercase_";
                legacyFieldName = legacyFieldName.substring(0, legacyFieldName.length() - 2);
            } else if (legacyFieldName.endsWith("_e")) {
                prefix = "edge_";
                legacyFieldName = legacyFieldName.substring(0, legacyFieldName.length() - 2);
            }

        if (legacyFieldName.indexOf('^') != -1) {
                suffix = legacyFieldName.substring(legacyFieldName.indexOf('^'));
                legacyFieldName = legacyFieldName.substring(0, legacyFieldName.indexOf('^'));
            }

            if (legacyFieldName.equals("iri")) {
                newFields.add(prefix + "iri" + suffix);
                continue;
            }

            if (legacyFieldName.equals("label")) {
                newFields.add(prefix + "label" + suffix);
                continue;
            }

            if (legacyFieldName.equals("synonym")) {
                newFields.add(prefix + "synonym" + suffix);
                continue;
            }

            if (legacyFieldName.equals("definition")) {
                newFields.add(prefix + "definition" + suffix);
                continue;
            }

            if (legacyFieldName.equals("description")) {
                newFields.add(prefix + "definition" + suffix);
                continue;
            }

            if (legacyFieldName.equals("short_form")) {
                newFields.add(prefix + "shortForm" + suffix);
                continue;
            }

            if (legacyFieldName.equals("obo_id")) {
                newFields.add(prefix + "curie" + suffix);
                continue;
            }

            if (legacyFieldName.equals("ontology_name")) {
                newFields.add(prefix + "ontologyId" + suffix);
                continue;
            }

            if (legacyFieldName.equals("type")) {
                newFields.add(prefix + "type" + suffix);
                continue;
            }

            if (legacyFieldName.equals("annotations_trimmed")) {
                newFields.add(prefix + "searchableAnnotationValues" + suffix);
                continue;
            }
        }

        // escape special characters in field names for solr query
        //
        newFields = newFields.stream().map(iri -> {
            return iri.replace(":", "__");
        }).collect(Collectors.toList());

        return newFields;
    }
}
