package uk.ac.ebi.spot.ols.controller.api.v2.helpers;

import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.web.util.UriUtils;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class DynamicQueryHelper {

    public static Map<String,String> filterProperties(Map<String,String> properties) {

        Map<String,String> newProps = new HashMap<>();

        for(String k : properties.keySet()) {

            String value = properties.get(k);

            try {
                k = UriUtils.decode(k, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new ResourceNotFoundException();
            }

            if(k.equals("lang") || k.equals("search") || k.equals("searchFields") || k.equals("boostFields") || k.equals("page") || k.equals("size"))
                continue;

            newProps.put(k, value);
        }

        return newProps;
    }




}
