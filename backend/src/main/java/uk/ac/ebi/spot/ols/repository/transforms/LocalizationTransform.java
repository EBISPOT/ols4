package uk.ac.ebi.spot.ols.repository.transforms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import uk.ac.ebi.spot.ols.repository.transforms.helpers.JsonCollectionHelper;

public class LocalizationTransform {

    public static JsonElement transform(JsonElement object, String lang) {

        if (object.isJsonArray()) {

            JsonArray localizedArr = JsonCollectionHelper.map(object.getAsJsonArray(), element -> transform(element, lang));

            if(localizedArr.size() == 0) {
                localizedArr = JsonCollectionHelper.map(object.getAsJsonArray(), element -> transform(element, "en"));
            }

            return localizedArr;

        } else if (object.isJsonObject()) {

            JsonObject obj = object.getAsJsonObject();

            if(obj.has("lang")) {

                String objLang = obj.get("lang").getAsString();

                if(objLang.equals(lang) || lang.equalsIgnoreCase("all")) {
                    return transform(obj.get("value"), lang);
                } else {
                    return null;
                }
            }

            JsonObject res = new JsonObject();

            for (String k : obj.keySet()) {
                JsonElement localized = transform(obj.get(k), lang);
                if(localized != null) {
                    res.add(k, localized);
                }
            }

            if(res.size() == 0) {
                for (String k : obj.keySet()) {
                    JsonElement localized = transform(obj.get(k), "en");
                    if(localized != null) {
                        res.add(k, localized);
                    }
                }
            }

            return res;

        } else {

            return object;

        }
    }


}






