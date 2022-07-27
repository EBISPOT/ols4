package uk.ac.ebi.spot.ols.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Simon Jupp
 * @date 18/08/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
public class JsTreeObject {

    private String id;
    private String parent;
    private String iri;
    private String ontologyName;
    private String text;
    private boolean hasChildren;
    private Map<String, Boolean> state;
    private boolean children;
    private Map<String, String> a_attr;


    public JsTreeObject(String id, String iri, String ontologyName, String text, String relation, boolean hasChildren,
                        String parent) {
        this.id = id;
        this.iri = iri;
        this.ontologyName = ontologyName;
        this.text = text;
        this.hasChildren = hasChildren;
        this.parent = parent;
        this.state = new HashMap<>();
        state.put("opened", true);
        this.children = false;
        a_attr = new HashMap<>();
        a_attr.put("title", iri);
        a_attr.put("iri", iri);
        a_attr.put("ontology_name", ontologyName);
        a_attr.put("class", relation);


    }

    public Map<String, String> getA_attr() {
        return a_attr;
    }

    public void setA_attr(Map<String, String> a_attr) {
        this.a_attr = a_attr;
    }

    public boolean isChildren() {
        return children;
    }

    public void setChildren(boolean children) {
        this.children = children;
    }


    public void setState(Map<String, Boolean> state) {
        this.state = state;
    }

    public Map<String, Boolean> getState() {
        return state;
    }

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIri() {
        return iri;
    }

    public void setIri(String iri) {
        this.iri = iri;
    }

    @JsonProperty(value = "ontology_name")
    public String getOntologyName() {
        return ontologyName;
    }

    public void setOntologyName(String ontologyName) {
        this.ontologyName = ontologyName;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @JsonIgnore
    public boolean isHasChildren() {
        return hasChildren;
    }

    public void setIsLeaf(boolean isLeaf) {
        this.hasChildren = isLeaf;
    }

    @Override
    public String toString() {
        return "JsTreeObject{" +
                "id='" + id + '\'' +
                ", parent='" + parent + '\'' +
                ", iri='" + iri + '\'' +
                ", ontologyName='" + ontologyName + '\'' +
                ", text='" + text + '\'' +
                ", hasChildren=" + hasChildren +
                ", state=" + state +
                ", children=" + children +
                ", a_attr=" + a_attr +
                '}';
    }
}