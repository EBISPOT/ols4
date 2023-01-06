package uk.ac.ebi.spot.ols.repository.transforms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import uk.ac.ebi.spot.ols.repository.transforms.helpers.JsonCollectionHelper;

public class RemoveReificationTransform {

    /* Turn  e.g.
      "http://www.geneontology.org/formats/oboInOwl#hasDbXref" : [ {
            "type" : [ "reification" ],
            "value" : {
              "type" : [ "literal" ],
              "value" : "MEDDRA:10048222"
            },
            "axioms" : [ { ....

        into
              "http://www.geneontology.org/formats/oboInOwl#hasDbXref" : [ {
                  "type" : [ "literal" ],
                  "value" : "MEDDRA:10048222"
                }
     */
    public static JsonElement transform(JsonElement object) {

        if (object.isJsonArray()) {

            return JsonCollectionHelper.map(object.getAsJsonArray(), RemoveLiteralDatatypesTransform::transform);

        } else if (object.isJsonObject()) {

            JsonObject obj = object.getAsJsonObject();

            if(obj.has("type")) {

                JsonArray types = obj.get("type").getAsJsonArray();

                if(types.contains(new JsonPrimitive("reification"))) {
                    return transform(obj.get("value"));
                }
            }

            return JsonCollectionHelper.map(obj, RemoveLiteralDatatypesTransform::transform);

        } else {

            return object;

        }
    }
}
