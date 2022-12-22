package uk.ac.ebi.spot.ols.service;

import java.util.*;
import java.util.stream.Collectors;

public class GenericLocalizer {

    public static Map<String,Object> localize(Map<String,Object> objMap, String lang) {
        return (Map<String,Object>) localizeObj(objMap, lang);
    }

    private static Object localizeObj(Object object, String lang) {

        if (object instanceof Collection) {

            return Arrays.asList(
                    new LinkedHashSet<Object>(
                        ((Collection<Object>) object)
                        .stream()
                        .map(obj -> GenericLocalizer.localizeObj(obj, lang))
                        .collect(Collectors.toList())
                    ).toArray()
            );

        } else if (object instanceof Map) {

            Map<String, Object> src = (Map<String, Object>) object;

            if(src.containsKey("lang")) {

               String objLang = (String) src.get("lang");

               if(objLang.equals(lang)) {
                   return localizeObj(src.get("value"), lang);
               } else {
                   return null;
               }
            }

            Map<String, Object> res = new HashMap<>();

            for (String k : src.keySet()) {

                if(k.equals("iriToLabels")) {
                    res.put(k, localizeIriToLabels(src.get(k), lang));
                } else {
                    res.put(k, localizeObj(src.get(k), lang));
                }
            }

            return res;

        } else {

            return object;

        }

    }

    private static Object localizeIriToLabels(Object object, String lang) {

        Map<String,Object> labels = (Map<String, Object>) object;
        Map<String,Object> localizedlabels = new TreeMap<>();

        for(String k : labels.keySet()) {

            if(! (k.startsWith(lang + "+"))) {
                continue;
            }

            String predicate = k.substring(lang.length() + 1);

            localizedlabels.put(predicate, labels.get(k));
        }

        return localizedlabels;
    }

}






