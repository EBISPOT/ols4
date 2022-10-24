
package uk.ac.ebi.spot.ols.service;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;
import java.util.stream.Collectors;

public class OntologyEntity {

    public OntologyEntity(Map<String,Object> record) {
        this.record = record;
    }

    Map<String, Object> record;

    public Map<String, Object> any() {
        return record;
    }

    public Map<String, Object> asMap() {
        return record;
    }


    public boolean containsKey(String k) {
        return record.containsKey(k);
    }

    public String getString(String predicate) {
        return objectToString( this.record.get(predicate) );
    }

    public List<String> getStrings(String predicate) {
        return getObjects(predicate).stream().map(obj -> objectToString(obj)).collect(Collectors.toList());
    }

    public Object getObject(String predicate) {
        return this.record.get(predicate);
    }

    public List<Object> getObjects(String predicate) {

        Object value = this.record.get(predicate);

        if(value == null) {
            return new ArrayList<>();
        }

        if(value instanceof List) {
            return (List<Object>) value;
        }

        return List.of(value);
    }

    private static String objectToString(Object value) {

        if (value instanceof Collection) {
            return objectToString(  ((Collection<Object>) value).toArray()[0] );
        } else if(value instanceof Map) {
            return objectToString( ((Map<String,Object>) value).get("value") );
        } else {
            return (String) value;
        }
    }

    public boolean hasType(String type) {

        Object types = this.record.get("type");

        if(types instanceof Collection) {
            return ((Collection<String>)types).contains(type);
        } else {
            return type.equals((String)types);
        }
    }




}
