package uk.ac.ebi.spot.ols.repository.v1;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class JsonHelper {

    public static String getString(JsonObject json, String key) {
        return objectToString(json.get(key));
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

        } else if(value.isJsonObject()) {
            return objectToString(value.getAsJsonObject().get("value"));
        } else {
            return value.getAsString();
        }
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

    public static JsonObject getObject(JsonObject json, String predicate) {
        return getValues(json, predicate).stream().map(v -> v.getAsJsonObject()).findFirst().get();
    }

    public static List<JsonObject> getObjects(JsonObject json, String predicate) {
        return getValues(json, predicate).stream().map(v -> v.getAsJsonObject()).collect(Collectors.toList());
    }

    private static List<JsonElement> getArrayElements(JsonArray arr) {
        return Lists.newArrayList(arr.iterator());
    }

}
