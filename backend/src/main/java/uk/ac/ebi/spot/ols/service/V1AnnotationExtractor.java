
package uk.ac.ebi.spot.ols.service;

import uk.ac.ebi.spot.ols.model.v1.V1Ontology;

import java.util.*;
import java.util.stream.Collectors;

public class V1AnnotationExtractor {

    public static Map<String,Object> extractAnnotations(OntologyEntity node) {

        TreeSet<String> annotationPredicates = new TreeSet<>(node.getStrings("annotationPredicate"));

        Map<String, Object> record = node.asMap();
        Map<String,String> propertyLabels = (Map<String,String>) record.get("propertyLabels");

        Map<String, Object> annotation = new TreeMap<>();

        for(String predicate : annotationPredicates) {

            Object value = record.get(predicate);

            // All anno values must be an array in OLS API
            if(! (value instanceof List)) {
                value = List.of(value);
            }

            // flatten values (removing annotations on the annotations) for OLS3
            //
            List<Object> flattenedValues = ((List<Object>) value).stream().map(entry -> {

                while(entry instanceof Map) {
                    Map<String,Object> entryAsMap = (Map<String,Object>) entry;
                    Object embeddedValue = entryAsMap.get("value");
                    if(embeddedValue == null)
                        break;
                    entry = embeddedValue;
                }

                return entry;

            }).collect(Collectors.toList());

            String label = propertyLabels.get(predicate);

            if(label == null) {
                label = predicate.substring(
                        Math.max(
                                predicate.lastIndexOf('#'),
                                predicate.lastIndexOf('/')
                        ) + 1
                );
            }

            annotation.put(label, flattenedValues);
        }

        return annotation;

    }

}