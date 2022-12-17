package uk.ac.ebi.spot.ols.model.v1;


import com.google.gson.Gson;
import org.springframework.hateoas.server.core.Relation;
import uk.ac.ebi.spot.ols.service.GenericLocalizer;
import uk.ac.ebi.spot.ols.service.OntologyEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


@Relation(collectionRelation = "ontologies")
public class V1Ontology {


    public static Gson gson = new Gson();

    public V1Ontology(Map<String,Object> jsonObj, String lang) {

        OntologyEntity localizedObj = new OntologyEntity(GenericLocalizer.localize(jsonObj, lang));
        this.lang =lang;

        ontologyId = localizedObj.getString("ontologyId");
        fileHash = localizedObj.getString("fileHash");

        config = new V1OntologyConfig();

        config.id = localizedObj.getString("ontologyId");
        config.namespace = config.id;
        config.versionIri = localizedObj.getString("http://www.w3.org/2002/07/owl#versionIRI");
        config.version = localizedObj.getString("http://www.w3.org/2002/07/owl#versionInfo");
        config.preferredPrefix = localizedObj.getString("preferredPrefix");
        config.title = localizedObj.getString("title");
        config.description = localizedObj.getString("description");
        config.homepage = localizedObj.getString("homepage");
        config.version = localizedObj.getString("version");
        config.mailingList = localizedObj.getString("mailing_list");
        config.tracker = localizedObj.getString("tracker");
        config.logo = localizedObj.getString("logo");
        config.creators = localizedObj.getStrings("creators");
        config.annotations = localizedObj.asMap().get("annotations");
        config.fileLocation = localizedObj.getString("ontology_purl");

        config.oboSlims = localizedObj.asMap().containsKey("oboSlims") ?
                (boolean) localizedObj.asMap().get("oboSlims") : false;

        config.labelProperty = localizedObj.getString("label_property");

        if(config.labelProperty == null) {
            config.labelProperty = "http://www.w3.org/2000/01/rdf-schema#label";
        }

        config.definitionProperties = localizedObj.getStrings("definition_property");
        config.synonymProperties = localizedObj.getStrings("synonym_property");
        config.hierarchicalProperties = localizedObj.getStrings("hierarchical_property");
        config.baseUris = localizedObj.getStrings("base_uri");
        config.hiddenProperties = localizedObj.getStrings("hidden_property");
        config.preferredRootTerms = localizedObj.getStrings("preferredRootTerms");

        config.isSkos = localizedObj.asMap().containsKey("isSkos") ?
                (boolean) localizedObj.asMap().get("isSkos") : false;

        config.allowDownload = localizedObj.asMap().containsKey("allowDownload") ?
                (boolean) localizedObj.asMap().get("allowDownload") : true;

        status = "LOADED";

        numberOfTerms = Integer.parseInt(localizedObj.getString("numberOfClasses"));
        numberOfProperties = Integer.parseInt(localizedObj.getString("numberOfProperties"));
        numberOfIndividuals = Integer.parseInt(localizedObj.getString("numberOfIndividuals"));

        // TODO just setting these to the same thing for now, as we don't keep track of when ontologies change
        // there is currently no way to set updated (everything gets updated every time we index now anyway)
        //
        loaded = localizedObj.getString("loaded");
        updated = localizedObj.getString("loaded");


        version = localizedObj.getString("http://www.w3.org/2002/07/owl#versionInfo");
        config.version = version;

        message = "";
        loadAttempts = 0;


        String embeddedTitle = localizedObj.getString("http://purl.org/dc/elements/1.1/title");

        if(embeddedTitle != null) {
            config.title = embeddedTitle;
        }

        String embeddedDesc = localizedObj.getString("http://purl.org/dc/elements/1.1/description");

        if(embeddedDesc != null) {
            config.description = embeddedDesc;
        }
    }

    public String lang;

    public String ontologyId;

    public String loaded;

    public String updated;

    public String status;

    public String message;

    public String version;

    public String fileHash = null;

    public int loadAttempts;

    public int numberOfTerms;
    public int numberOfProperties;
    public int numberOfIndividuals;

    public V1OntologyConfig config;

    public Set<String> getBaseUris() {

        if(config.baseUris != null) {
            return new HashSet(config.baseUris);
        }

        Set<String> baseUris = new HashSet<String>();

        if(config.preferredPrefix != null) {
            baseUris.add("http://purl.obolibrary.org/obo/" + config.preferredPrefix + "_");
        }

        return baseUris;
    }



}
