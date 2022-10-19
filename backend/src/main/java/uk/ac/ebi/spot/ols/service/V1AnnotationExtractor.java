
package uk.ac.ebi.spot.ols.service;

import uk.ac.ebi.spot.ols.model.v1.V1Ontology;

import java.util.*;

public class V1AnnotationExtractor {

    public static Map<String,Object> extractAnnotations(OntologyEntity node) {

        TreeSet<String> annotationPredicates = new TreeSet<>(node.getStrings("annotationPredicate"));

        Map<String, Object> record = node.asMap();
        Map<String, Object> annotation = new TreeMap<>();

        for(String predicate : annotationPredicates) {

            Object value = record.get(predicate);

            // All anno values must be an array in OLS API
            if(! (value instanceof List)) {
                List<Object> arrList = new ArrayList<>();
                arrList.add(value);
                value = arrList;
            }

            String localPart = predicate.substring(
                    Math.max(
                            predicate.lastIndexOf('#'),
                            predicate.lastIndexOf('/')
                    ) + 1
            );

            annotation.put(localPart, value);
        }

        return annotation;

    }

}