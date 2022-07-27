package uk.ac.ebi.spot.ols.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class GenericLocalizer {

    public static Object localize(Object object, String lang) {

        if (object instanceof Collection) {

            return ((Collection<Object>) object)
                    .stream()
                    .map(obj -> GenericLocalizer.localize(obj, lang))
                    .collect(Collectors.toList());

        } else if (object instanceof Map) {

            Map<String, Object> src = (Map<String, Object>) object;

            if(src.containsKey("lang")) {

               String objLang = (String) src.get("lang");

               if(objLang.equals(lang)) {
                   return src.get("value");
               } else {
                   return null;
               }
            }

            Map<String, Object> res = new HashMap<>();

            for (String k : src.keySet()) {
                res.put(k, localize(src.get(k), lang));
            }

            return res;

        } else {

            return object;

        }

    }

}






