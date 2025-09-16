package uk.ac.ebi.spot.ols.repository.transforms;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import uk.ac.ebi.spot.ols.repository.transforms.helpers.JsonCollectionHelper;

public class ResolveReferencesTransform {

    /* Turn  e.g.
      "directParent": [
          "http://www.ebi.ac.uk/efo/EFO_0001641"
        ],
      "linkedEntities": {
          "http://www.ebi.ac.uk/efo/EFO_0001641": {
              "label": [ "epithelial cell derived cell line" ], ...
          }

    into

        "directParent": [
            {
              "label": [ "epithelial cell derived cell line" ], ...
            }
        ],
     */
    public static JsonElement transform(JsonElement object) {
        return transformWithLinkedEntities(object, null);
    }

    private static JsonElement transformWithLinkedEntities(JsonElement object, JsonObject linkedEntities) {
    
        if (object.isJsonArray()) {

            final var _linked = linkedEntities;

            return JsonCollectionHelper.map(object.getAsJsonArray(), e -> transformWithLinkedEntities(e, _linked));

        } else if (object.isJsonObject()) {

            JsonObject obj = object.getAsJsonObject();

            if(linkedEntities == null && obj.has("linkedEntities")) {
                linkedEntities = obj.getAsJsonObject("linkedEntities");
            }

            final var _linked = linkedEntities;

            JsonObject newObj = new JsonObject();

            for (var entry : obj.entrySet()) {
                if(entry.getKey().equals("linkedEntities")
                        || entry.getKey().equals("iri")
                        || entry.getKey().equals("curie")
                        || entry.getKey().equals("shortForm")
                        ) {
                    newObj.add(entry.getKey(), entry.getValue());
                } else {
                    JsonElement res = transformWithLinkedEntities(entry.getValue(), _linked);
                    if(res != null) {
                        newObj.add(entry.getKey(), res);
                    } else {
                        newObj.add(entry.getKey(), entry.getValue());
                    }
                }
            }

            return newObj;
        } else if(object.isJsonPrimitive() && object.getAsJsonPrimitive().isString() && linkedEntities != null) {

            String maybeIri = object.getAsString();

            if(linkedEntities.has(maybeIri)) {
                JsonObject linked = linkedEntities.getAsJsonObject(maybeIri);
                if(!linked.has("iri")) {
                    linked.addProperty("iri", maybeIri);
                }
                return linked;
            } else {
                return object;
            }

        } else {
            return object;
        }
    }
}
