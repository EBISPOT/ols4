package uk.ac.ebi.spot.ols.model.v2;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class DynamicJsonObject {

    protected Gson gson = new Gson();


    // non URI properties first for readability
    //
    final Comparator<String> keySort =
            new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {

            if(o1.contains("://") && !o2.contains("://")) {
                return 1;
            }

            if(!o1.contains("://") && o2.contains("://")) {
                return -1;
            }

            return 0;
        }
    }.thenComparing(Comparator.naturalOrder());

    @JsonIgnore
    private TreeMap<String,Object> properties = new TreeMap<>(keySort);

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