package uk.ac.ebi.spot.ols.model.v1;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Simon Jupp
 * @date 17/06/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
public class V1Related {

    Long id;

    String iri;
    String label;

    @JsonProperty(value = "ontology_name")
    String ontologyName;

    private V1Term relatedFrom;
    private V1Term relatedTo;

    public V1Term getRelatedFrom() {
        return relatedFrom;
    }

    public V1Term getRelatedTo() {
        return relatedTo;
    }

    public String getIri() {
        return iri;
    }

    public String getLabel() {
        return label;
    }

    public String getOntologyName() {
        return ontologyName;
    }



}
