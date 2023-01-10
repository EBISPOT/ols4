package uk.ac.ebi.spot.ols.repository.transforms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import uk.ac.ebi.spot.ols.repository.transforms.helpers.JsonCollectionHelper;

public class RemoveLiteralDatatypesTransform {

    /* Turn  e.g.
           {
              "type" : [ "literal" ],
              "value" : "Diabetes"
            }
        into just "Diabetes"
     */
    public static JsonElement transform(JsonElement object) {

        if (object.isJsonArray()) {

            return JsonCollectionHelper.map(object.getAsJsonArray(), RemoveLiteralDatatypesTransform::transform);

        } else if (object.isJsonObject()) {

            JsonObject obj = object.getAsJsonObject();

            if(obj.has("type")) {

                JsonElement type = obj.get("type");

                if(type.isJsonArray()) {

                    JsonArray types = type.getAsJsonArray();

                    if(types.contains(new JsonPrimitive("literal"))) {
                        return transform(obj.get("value"));
                    }
                }
            }

            return JsonCollectionHelper.map(obj, RemoveLiteralDatatypesTransform::transform);

        } else {

            return object;

        }
    }
}
