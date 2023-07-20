package uk.ac.ebi.spot.ols.repository.transforms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import uk.ac.ebi.spot.ols.repository.transforms.helpers.JsonCollectionHelper;
import uk.ac.ebi.spot.ols.repository.v1.JsonHelper;

import java.util.List;

public class LocalizationTransform {

    public static JsonElement transform(JsonElement json, String lang) {

        if(lang.equals("all")) {
            return json;
        }

        if(json.isJsonArray()) {
            return localizeArray(json.getAsJsonArray(), lang);
        } else if(json.isJsonObject()) {

            JsonObject obj = json.getAsJsonObject();

            List<String> types = JsonHelper.getStrings(obj, "type");

            if(types.contains("entity") || types.contains("ontology")) {
                return localizeEntity(json.getAsJsonObject(), lang);
            } else if(types.contains("reification")) {
                return localizeReification(json.getAsJsonObject(), lang);
            } else if(types.contains("literal")) {
                return localizeLiteral(json.getAsJsonObject(), lang);
            } else {
                // some random json object (ontology config?); not localizable
                return json;
            }
        }

//        if(lang.equals("")) {
//            return json;
//        } else {
//            return null;
//        }

        return json;
    }


    public static JsonElement localizeEntity(JsonObject obj, String lang) {

        // Attempt to localize all of the keys into the requested language.

        JsonObject res = new JsonObject();

        for (String k : obj.keySet()) {

            if(k.equals("linkedEntities")) {
                res.add(k, localizeLinkedEntities(obj.get(k).getAsJsonObject(), lang));
            } else {
                JsonElement localized = localizeValueWithFallbacks( obj.get(k), lang );

                if(localized != null)
                    res.add(k, localized);
            }
        }

        return res;
    }

    public static JsonElement localizeReification(JsonObject obj, String lang) {

        // { value: ..., axioms: ... }

        JsonObject res = new JsonObject();
        res.add("type", obj.get("type"));

        JsonElement value = transform(obj.get("value"), lang);
        if(value == null) {
            return null;
        }

        JsonArray axioms = obj.getAsJsonArray("axioms");
        JsonArray axiomsRes = new JsonArray();

        for(JsonElement axiom : axioms) {
            JsonObject axiomObj = axiom.getAsJsonObject();
            JsonObject axiomRes = new JsonObject();

            for(String k : axiomObj.keySet()) {

                JsonElement localized = localizeValueWithFallbacks( axiomObj.get(k), lang );

                if(localized != null)
                    axiomRes.add(k, localized);
            }

            axiomsRes.add(axiomRes);
        }

        res.add("value", value);
        res.add("axioms", axiomsRes);

        return res;
    }

    public static JsonElement localizeLiteral(JsonObject obj, String lang) {

        if(obj.has("lang")) {

            // This literal is a localisation { lang: ..., value: ... }

            if(lang.equals("")) {
                // If no lang is provided, we are only looking for default values (no lang).
                // So we skip this literal.
                return null;
            }

            // Only looking for values in the requested language

            String objLang = obj.get("lang").getAsString();

            if(objLang.equalsIgnoreCase(lang)) {
                if(obj.get("value").isJsonPrimitive()) {
                    return obj.get("value");
                } else {
                    return transform(obj.get("value"), lang);
                }
            } else {
                return null;
            }
        }

        // This literal is not a localisation

        if(!lang.equals("")) {
            // If a lang is provided, we are only looking for values in the lang.
            // So we skip this literal.
            return null;
        }

        JsonObject res = new JsonObject();
        for(String k : obj.keySet()) {
            if(k.equals("value")) {
                JsonElement value = transform(obj.get(k), lang);
                if(value == null) {
                    return null;
                }
                res.add("value", value);
            } else {
                res.add(k, obj.get(k));
            }
        }
        return res;
    }

    public static JsonArray localizeArray(JsonArray arr, String lang) {

        JsonArray localizedArr = JsonCollectionHelper.map(arr, element -> transform(element, lang));
        return localizedArr.size() > 0 ? localizedArr : null;
    }

    public static JsonElement localizeValueWithFallbacks(JsonElement v, String lang) {

        // 1. First try the explicitly requested language
        JsonElement localized = transform(v, lang);

        // 2. Failing that, try a default string
        if(localized == null) {
            localized = transform(v, "");
        }

        // 3. Failing THAT, try "en"
        if(localized == null) {
            localized = transform(v, "en");
        }

        if(localized == null) {
            return null;
        }

        return localized;
    }

    public static JsonObject localizeLinkedEntities(JsonObject linkedEntities, String lang) {
        JsonObject res = new JsonObject();
        for(String entityIri : linkedEntities.keySet()) {
            JsonObject entityProps = linkedEntities.getAsJsonObject(entityIri);
            JsonObject entityPropsRes = new JsonObject();
            for(String k : entityProps.keySet()) {
                var val = localizeValueWithFallbacks(entityProps.get(k), lang);
                if(val!=null)
                    entityPropsRes.add(k, val);
            }
            res.add(entityIri, entityPropsRes);
        }
        return res;
    }

}







