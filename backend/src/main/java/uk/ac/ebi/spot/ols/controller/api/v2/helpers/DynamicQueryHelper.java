package uk.ac.ebi.spot.ols.controller.api.v2.helpers;

import org.springframework.web.util.UriUtils;

import java.util.HashMap;
import java.util.Map;

public class DynamicQueryHelper {

    public static Map<String,String> filterProperties(Map<String,String> properties) {

        Map<String,String> newProps = new HashMap<>();

        for(String k : properties.keySet()) {

            String value = properties.get(k);

            k = UriUtils.decode(k, "UTF-8");

            if(k.equals("lang") || k.equals("search") || k.equals("searchFields")
                    || k.equals("boostFields") || k.equals("page") || k.equals("size") || k.equals("exactMatch")
                        || k.equals("includeObsoleteEntities"))
                continue;

            newProps.put(k, value);
        }

        return newProps;
    }




}
