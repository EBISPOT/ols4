package uk.ac.ebi.spot.ols.repository.transforms;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import uk.ac.ebi.spot.ols.repository.transforms.helpers.JsonCollectionHelper;

public class LocalizationTransform {

    public static JsonElement transform(JsonElement object, String lang) {

        if (object.isJsonArray()) {

            return JsonCollectionHelper.map(object.getAsJsonArray(), element -> transform(element, lang));

        } else if (object.isJsonObject()) {

            JsonObject obj = object.getAsJsonObject();

            if(obj.has("lang")) {

                String objLang = obj.get("lang").getAsString();

                if(objLang.equals(lang)) {
                    return transform(obj.get("value"), lang);
                } else {
                    return null;
                }
            }

            JsonObject res = new JsonObject();

            for (String k : obj.keySet()) {
		res.add(k, transform(obj.get(k), lang));
            }

            return res;

        } else {

            return object;

        }
    }


}






