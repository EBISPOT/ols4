
package uk.ac.ebi.spot.ols.service;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;

public class OntologyEntity {

    final static String rdfsPrefix = "http__//www.w3.org/2000/01/rdf-schema#";

    public OntologyEntity(OntologyEntity graphNode) {
        this.record = new HashMap<>(graphNode.record);
    }

    public OntologyEntity(OntologyEntity graphNode, String lang) {
        this.record = new HashMap<>();
        for (String k : graphNode.record.keySet()) {
            this.record.put(k, GenericLocalizer.localize(graphNode.record.get(k), lang));
        }
    }


    @JsonIgnore
    Map<String, Object> record;

    @JsonAnyGetter
    public Map<String, Object> any() {
        return record;
    }


    public OntologyEntity(Map<String, Object> record) {
        this.record = record;
    }


    public Map<String, Object> asMap() {
        return record;
    }


    public String getString(String predicate) {
        return objectToString( this.record.get(predicate) );
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
