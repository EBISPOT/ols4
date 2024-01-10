package uk.ac.ebi.spot.ols.model.v1;

import com.google.gson.annotations.SerializedName;

import java.util.Collection;
import java.util.HashSet;

public class V1OntologyConfig {

    // ontology IRI
    public  String id;
    // ontology version IRI
    public  String versionIri;
    public  String namespace;
    public  String preferredPrefix;
    public  String title;

    public String description;
    public String homepage;
    public String version;
    public String mailingList;
    public String tracker;
    public String logo;
    public Collection<String> creators;

    public Collection<String> collection;
    public Collection<String> subject;
    //public Map<String, Collection<String>> annotations;
    public Object annotations;

    public  String fileLocation;

    public  boolean oboSlims;
    public  String labelProperty;

    @SerializedName("definition_property")
    public  Collection<String> definitionProperties;

    @SerializedName("synonym_property")
    public  Collection<String> synonymProperties;

    @SerializedName("hierarchical_property")
    public  Collection<String> hierarchicalProperties;

    @SerializedName("base_iri")
    public  Collection<String> baseUris;


    public  Collection<String> hiddenProperties;
    public  Collection<String> preferredRootTerms = new HashSet<>();
    public boolean isSkos;

    public boolean allowDownload;
}
