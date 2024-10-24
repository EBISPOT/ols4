
package uk.ac.ebi.spot.ols.repository.v1.mappers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import uk.ac.ebi.spot.ols.repository.v1.JsonHelper;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static uk.ac.ebi.ols.shared.DefinedFields.*;

public class AnnotationExtractor {

    private static Gson gson = new Gson();

    private static final Pattern namePattern = Pattern.compile( "^([A-z]+)\\+(.+)$" );

    public static Map<String,Object> extractAnnotations(JsonObject json) {

        TreeSet<String> definitionProperties = new TreeSet<>(JsonHelper.getStrings(json, "definitionProperty"));
        TreeSet<String> synonymProperties = new TreeSet<>(JsonHelper.getStrings(json, "synonymProperty"));
        TreeSet<String> hierarchicalProperties = new TreeSet<>(JsonHelper.getStrings(json, "hierarchicalProperty"));

        JsonObject linkedEntities = json.get("linkedEntities").getAsJsonObject();

        Map<String, Object> annotation = new TreeMap<>();

        for(String predicate : json.keySet()) {

            // properties without an IRI are things that were added by rdf2json so should not
            // be included as annotations
            if(!predicate.contains("://"))
                continue;

		/* We have some added (by rdf2json) ols4 properties like
			relatedTo+http://....
			negativePropertyAssertion+http://...
		these are useless to ols3
		*/
		if(namePattern.matcher(predicate).matches())
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

	    String label = predicate.substring(
		Math.max(
			predicate.lastIndexOf('#'),
			predicate.lastIndexOf('/')
		) + 1
	); 

            JsonElement linkedEntityObj = linkedEntities.get(predicate);

	    if(linkedEntityObj != null) {

		String definedLabel = JsonHelper.getString(linkedEntityObj.getAsJsonObject(), LABEL.getText());

		if(definedLabel != null) {
			label = definedLabel;
		}
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