package uk.ac.ebi.spot.ols.controller.api.v2.helpers;

import org.springframework.web.util.UriUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;

public class DynamicQueryHelper {

    public static Map<String,Collection<String>> filterProperties(Map<String,Collection<String>> properties) {

        Map<String,Collection<String>> newProps = new HashMap<>();

        for(String k : properties.keySet()) {

            k = UriUtils.decode(k, "UTF-8");

            if(k.equals("lang") || k.equals("search") || k.equals("searchFields")
                    || k.equals("facetFields")
                    || k.equals("boostFields") || k.equals("page") || k.equals("size") || k.equals("sort")
                    || k.equals("exactMatch")
                        || k.equals("includeObsoleteEntities")
                        || k.equals("excludeOntologyId")
                        || k.equals("resolveReferences")
                        || k.equals("manchesterSyntax")
                        || k.equals("model")
                        || k.equals("includeTotal")
                        )
                continue;

            newProps.put(k, properties.get(k));
        }

        return newProps;
    }




}
