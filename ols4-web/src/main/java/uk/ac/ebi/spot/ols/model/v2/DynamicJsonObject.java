package uk.ac.ebi.spot.ols.model.v2;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DynamicJsonObject {

    protected Gson gson = new Gson();

    @JsonIgnore
    private Map<String,Object> properties = new HashMap<>();

    public void put(String k, Object v) {
        properties.put(k, v);
    }

    @JsonAnyGetter
    public Map<String, Object> any() {
        return properties;
    }

    public String get(String predicate) {

        Object value = this.properties.get(predicate);

        if(value instanceof Collection) {
            return (String) ((Collection<Object>) value).toArray()[0];
        } else {
            return (String) value;
        }

    }
}