
package uk.ac.ebi.spot.ols.service;

import uk.ac.ebi.spot.ols.model.v1.V1Ontology;

import java.util.*;

public class MetadataExtractor {

    final static String rdfsPrefix = "http__//www.w3.org/2000/01/rdf-schema#";

    public static Map<String,Object> extractAnnotations(OntologyEntity node, V1Ontology ontology) {

        Map<String, Object> record = node.asMap();

        Map<String, Object> annotation = new HashMap<>();

        for(String predicate : record.keySet()) {

            if(ontology.config.hierarchicalProperties != null
                    && ontology.config.hierarchicalProperties.contains(predicate))
                continue;
            if(ontology.config.synonymProperties != null
                    && ontology.config.synonymProperties.contains(predicate))
                continue;
            if(ontology.config.definitionProperties != null
                    && ontology.config.definitionProperties.contains(predicate))
                continue;

            if(predicate.startsWith(rdfsPrefix)) {
                String localPart = predicate.substring(rdfsPrefix.length());

                if(localPart.equals("label")) {
                    // used as label
                    continue;
                }
                if(localPart.equals("comment")) {
                    // used as description
                    continue;
                }
                if(localPart.equals("subClassOf")) {
                    // used as parents
                    continue;
                }

                Object value = record.get(predicate);

                // All anno values must be an array in OLS API
                if(! (value instanceof List)) {
                    List<Object> arrList = new ArrayList<>();
                    arrList.add(value);
                    value = arrList;
                }

                annotation.put(localPart, value);
                continue;
            }

        }

        return annotation;

    }

    public static String[] extractDescriptions(OntologyEntity node, V1Ontology ontology) {

        Set<String> descriptionProperties = new HashSet<String>();
        descriptionProperties.add("http__//www.w3.org/2000/01/rdf-schema#description");
        descriptionProperties.add("http__//www.w3.org/2000/01/rdf-schema#comment");
        descriptionProperties.add("http__//purl.obolibrary.org/obo/IAO_0000115");

        if(ontology.config.definitionProperties != null)
            descriptionProperties.addAll(ontology.config.definitionProperties);

        return extract(node, descriptionProperties);
    }

    public static String[] extractSynonyms(OntologyEntity node, V1Ontology ontology) {

        Set<String> synonymProperties = new HashSet<String>();
        synonymProperties.add("http__//www.geneontology.org/formats/oboInOwl#hasExactSynonym");

        if(ontology.config.synonymProperties != null)
            synonymProperties.addAll(ontology.config.synonymProperties);

        return extract(node, synonymProperties);
    }

    private static String[] extract(OntologyEntity node, Set<String> predicates) {

        List<String> res = new ArrayList<>();

        for(String prop : predicates) {
            Object val = node.getString(prop);
            if(val != null) {
                if (val instanceof List) {
                    for(String synonym : (List<String>) val) {
                        res.add(synonym);
                    }
                } else {
                    res.add((String) val);
                }
            }
        }

        return res.toArray(new String[0]);
    }



}