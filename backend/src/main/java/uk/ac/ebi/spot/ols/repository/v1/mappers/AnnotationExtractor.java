
package uk.ac.ebi.spot.ols.repository.v1.mappers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import uk.ac.ebi.spot.ols.repository.v1.JsonHelper;

import java.util.*;
import java.util.stream.Collectors;

public class AnnotationExtractor {

    private static Gson gson = new Gson();

    public static Map<String,Object> extractAnnotations(JsonObject json) {

        TreeSet<String> definitionProperties = new TreeSet<>(JsonHelper.getStrings(json, "definitionProperty"));
        TreeSet<String> synonymProperties = new TreeSet<>(JsonHelper.getStrings(json, "synonymProperty"));
        TreeSet<String> hierarchicalProperties = new TreeSet<>(JsonHelper.getStrings(json, "hierarchicalProperty"));

        JsonObject labels = json.get("iriToLabels").getAsJsonObject();

        Map<String, Object> annotation = new TreeMap<>();

        for(String predicate : json.keySet()) {

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

            // flatten values (removing annotations on the annotations) for OLS3
            //
            JsonArray flattenedValues = new JsonArray();

            for(JsonElement entry : JsonHelper.getValues(json, predicate)) {

                while(entry.isJsonObject()) {
                    JsonElement embeddedValue = entry.getAsJsonObject().get("value");
                    if(embeddedValue == null)
                        break;
                    entry = embeddedValue;
                }

                flattenedValues.add(entry);
            }

            JsonElement labelObj = labels.get(predicate);

            String label = null;

            if(labelObj != null) {
                if(labelObj.isJsonPrimitive()) {
                    label = labelObj.getAsString();
                } else if(labelObj.isJsonArray()) {
                    label = labelObj.getAsJsonArray().get(0).getAsString();
                }
            }

            if(label == null) {
                label = predicate.substring(
                        Math.max(
                                predicate.lastIndexOf('#'),
                                predicate.lastIndexOf('/')
                        ) + 1
                );
            }

            Set<Object> annos = (Set<Object>) annotation.get(label);

            if(annos == null) {
                annos = new LinkedHashSet<>();
                annotation.put(label, annos);
            }

            for(Object obj : gson.fromJson(flattenedValues, List.class)) {
                annos.add(obj);
            }

        }

        return annotation;

    }

    public static List<String> extractSubsets(JsonObject json) {

        TreeSet<String> subsetUris = new TreeSet<>(
                JsonHelper.getStrings(json, "http://www.geneontology.org/formats/oboInOwl#inSubset"));

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