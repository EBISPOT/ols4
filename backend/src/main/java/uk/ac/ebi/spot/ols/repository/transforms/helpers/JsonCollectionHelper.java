package uk.ac.ebi.spot.ols.repository.transforms.helpers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.function.UnaryOperator;

public class JsonCollectionHelper {

    public static JsonArray map(JsonArray arr, UnaryOperator<JsonElement> mapFn) {

        JsonArray newArr = new JsonArray(arr.size());

        for (JsonElement element : arr) {
            newArr.add(mapFn.apply(element));
        }

        return newArr;
    }

    public static JsonObject map(JsonObject obj, UnaryOperator<JsonElement> mapFn) {

        JsonObject newObj = new JsonObject();

        for (String key : obj.keySet()) {
            newObj.add(key, mapFn.apply(obj.get(key)));
        }

        return newObj;
    }
}
