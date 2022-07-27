
package uk.ac.ebi.spot.ols.service;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;

public class OwlGraphNode {

    final static String rdfsPrefix = "http__//www.w3.org/2000/01/rdf-schema#";

    public OwlGraphNode(OwlGraphNode graphNode) {
        this.record = new HashMap<>(graphNode.record);
    }

    public OwlGraphNode(OwlGraphNode graphNode, String lang) {
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


    public OwlGraphNode(Map<String, Object> record) {
        this.record = record;
    }


    public Map<String, Object> asMap() {
        return record;
    }


    public String get(String predicate) {

        Object value = this.record.get(predicate);

        if (value instanceof Collection) {
            return (String) ((Collection<Object>) value).toArray()[0];
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
