
package uk.ac.ebi.spot.ols.service;

import uk.ac.ebi.spot.ols.model.v1.V1Ontology;

import java.util.*;
import java.util.stream.Collectors;

public class V1AnnotationExtractor {

    public static Map<String,Object> extractAnnotations(OntologyEntity node) {

        TreeSet<String> definitionProperties = new TreeSet<>(node.getStrings("definitionProperty"));
        TreeSet<String> synonymProperties = new TreeSet<>(node.getStrings("synonymProperty"));
        TreeSet<String> hierarchicalProperties = new TreeSet<>(node.getStrings("hierarchicalProperty"));

        Map<String, Object> record = node.asMap();
        Map<String ,Object> labels = (Map<String,Object>) record.get("iriToLabels");

        Map<String, Object> annotation = new TreeMap<>();

        for(String predicate : node.asMap().keySet()) {

            // properties without an IRI are things that were added by owl2json so should not
            // be included as annotations
            if(!predicate.contains("://"))
                continue;

            // If the value was already interpreted as definition/synonym/hierarchical, do
            // not include it as an annotation
            if (definitionProperties.contains(predicate) ||
                    synonymProperties.contains(predicate) ||
                    hierarchicalProperties.contains(predicate)) {
                continue;
            }

            // anything in the rdf, rdfs, owl namespaces aren't considered annotations...
            if(predicate.startsWith("http://www.w3.org/2000/01/rdf-schema#") ||
                    predicate.startsWith("http://www.w3.org/1999/02/22-rdf-syntax-ns#") ||
                    predicate.startsWith("http://www.w3.org/2002/07/owl#")) {

                // ...apart from these ones
                if(!predicate.equals("http://www.w3.org/2000/01/rdf-schema#comment")
                        && !predicate.equals("http://www.w3.org/2000/01/rdf-schema#seeAlso")) {
                    continue;
                }
            }

            // while in general oboInOwl namespace properties are annotations, inSubset is not
            //
            if(predicate.equals("http://www.geneontology.org/formats/oboInOwl#inSubset")) {
                continue;
            }

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

            Object labelObj = labels.get(predicate);

            String label = null;
            if(labelObj instanceof String) {
                label = (String) labelObj;
            } else if(labelObj instanceof Collection) {
                label = ((Collection<String>) labelObj).iterator().next();
            }

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

    public static List<String> extractSubsets(OntologyEntity node) {

        TreeSet<String> subsetUris = new TreeSet<>(
                node.getStrings("http://www.geneontology.org/formats/oboInOwl#inSubset"));

        if(subsetUris.size() == 0) {
            return null;
        }

        return subsetUris.stream().map(subsetUri -> {

            return subsetUri.substring(
                    Math.max(
                            subsetUri.lastIndexOf('#'),
                            subsetUri.lastIndexOf('/')
                    ) + 1
            );

        }).collect(Collectors.toList());

    }

}