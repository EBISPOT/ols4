package uk.ac.ebi.spot.ols.repository.v1;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JsonHelper {

    public static String getString(JsonObject json, String key) {
        return objectToString(json.get(key));
    }

    public static boolean getBoolean(JsonObject json, String key) {
        return json.getAsJsonPrimitive(key).getAsBoolean();
    }

    public static String objectToString(JsonElement value) {

        if(value == null) {
            return null;
        }

        if (value.isJsonArray()) {

            JsonArray arr = value.getAsJsonArray();

            /* We only want 1 string if we call this method. For example, we call
             * this method with "label" but there are multiple labels, yet we only
             * want 1 label. There is no correct behaviour in this situation because
             * there is no metadata telling us which label is preferred.
             *
             * To make this semi-deterministic, we alphabetically sort the values if
             * they are all strings before returning the first one.
             */
            List<String> elements =
                    getArrayElements(arr).stream().map(JsonHelper::objectToString).sorted().collect(Collectors.toList());

            return elements.get(0);

        } else if(value.isJsonObject() && value.getAsJsonObject().get("value") != null) {
            return objectToString(value.getAsJsonObject().get("value"));

       /* This is a special case for the OLS API. If the value is a nested JsonObject like this
       an example from OIO ontology:
        {
        "http://www.geneontology.org/formats/oboInOwl#hasURI": {
           "type": [
           "literal"
          ],
           "datatype": "http://www.w3.org/2001/XMLSchema#anyURI",
           "value": "http://www.obofoundry.org/wiki/index.php/Definitions"
          },
          "http://www.w3.org/1999/02/22-rdf-syntax-ns#type": "http://www.geneontology.org/formats/oboInOwl#DbXref",
          "http://www.w3.org/2000/01/rdf-schema#label": {
              "type": [
                "literal"
              ],
          "value": "URL:http://www.obofoundry.org/wiki/index.php/Definitions"
       },
       "isObsolete": false
        }

         *   For this type of JsonObject we need to iterate through the entries and find value key.
         *   For sake of simplicity I've returned the first value which is found as we don't have any
         *   mechanism to judge which value to prefer over the other.
         */

        } else if (value.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                JsonElement element = entry.getValue();
                if (element.isJsonObject()) {
                    JsonObject obj = element.getAsJsonObject();
                    if (obj.has("value")) {
                        return obj.get("value").getAsString();
                    }
                }
            }
        } else {
            return value.getAsString();
        }
        return value.getAsString();
    }

    public static List<JsonElement> getValues(JsonObject json, String predicate) {

        JsonElement value = json.get(predicate);

        if(value == null) {
            return List.of();
        }

        if(value.isJsonArray()) {
            return getArrayElements(value.getAsJsonArray());
        }

        return List.of(value);
    }

    public static List<String> getStrings(JsonObject json, String predicate) {
        return getValues(json, predicate).stream().map(JsonHelper::objectToString).collect(Collectors.toList());
    }

    /**
     * This methi
     * @param json
     * @param predicate
     * @return
     */
    public static String getType(JsonObject json, String predicate){
        List<String> types = getValues(json, predicate).stream().map(JsonHelper::objectToString).collect(Collectors.toList());
        types.remove("entity");
        return types.get(0);
    }

    public static List<JsonObject> getObjects(JsonObject json, String predicate) {
        return getValues(json, predicate).stream().map(v -> v.getAsJsonObject()).collect(Collectors.toList());
    }

    private static List<JsonElement> getArrayElements(JsonArray arr) {
        return Lists.newArrayList(arr.iterator());
    }

}
