package uk.ac.ebi.spot.ols.model.v1;


import java.util.*;

import com.google.gson.Gson;
import org.springframework.hateoas.core.Relation;
import uk.ac.ebi.spot.ols.service.OntologyEntity;


@Relation(collectionRelation = "ontologies")
public class V1Ontology {


    public static Gson gson = new Gson();

    public V1Ontology(OntologyEntity node, String lang) {

        if(!node.hasType("ontology")) {
            throw new IllegalArgumentException("Node has wrong type");
        }

        OntologyEntity localizedNode = new OntologyEntity(node, lang);
        this.lang =lang;

        ontologyId = localizedNode.getString("ontologyId");

        config = new V1OntologyConfig();
        config.id = localizedNode.getString("ontologyId");
        config.versionIri = localizedNode.getString("http://www.w3.org/2002/07/owl#versionIRI");
        config.namespace = localizedNode.getString("id"); // TODO ??
        config.version = localizedNode.getString("http://www.w3.org/2002/07/owl#versionInfo");
        config.preferredPrefix = localizedNode.getString("preferredPrefix");
        config.title = localizedNode.getString("title");
        config.description = localizedNode.getString("description");
        config.homepage = localizedNode.getString("homepage");
        config.version = localizedNode.getString("version");
        config.mailingList = localizedNode.getString("mailingList");
        config.tracker = localizedNode.getString("tracker");
        config.logo = localizedNode.getString("logo");
        config.creators = localizedNode.getStrings("creators");
        config.annotations = localizedNode.asMap().get("annotations");
        config.fileLocation = localizedNode.getString("fileLocation");

        config.oboSlims = localizedNode.asMap().containsKey("oboSlims") ?
                (boolean) localizedNode.asMap().get("oboSlims") : false;

        config.labelProperty = localizedNode.getString("labelProperty");
        config.definitionProperties = localizedNode.getStrings("definitionProperties");
        config.synonymProperties = localizedNode.getStrings("synonymProperties");
        config.hierarchicalProperties = localizedNode.getStrings("hierarchicalProperties");
        config.baseUris = localizedNode.getStrings("baseUris");
        config.hiddenProperties = localizedNode.getStrings("hiddenProperties");
        config.preferredRootTerms = localizedNode.getStrings("preferredRootTerms");

        config.isSkos = localizedNode.asMap().containsKey("isSkos") ?
                (boolean) localizedNode.asMap().get("isSkos") : false;

        config.allowDownload = localizedNode.asMap().containsKey("allowDownload") ?
                (boolean) localizedNode.asMap().get("allowDownload") : true;

        config.internalMetadataProperties = localizedNode.asMap().containsKey("internalMetadataProperties") ?
                localizedNode.asMap().get("internalMetadataProperties") : new HashMap<String,Object>();

        status = "LOADED";

        numberOfTerms = Integer.parseInt(localizedNode.getString("numberOfClasses"));
        numberOfProperties = Integer.parseInt(localizedNode.getString("numberOfProperties"));
        numberOfIndividuals = Integer.parseInt(localizedNode.getString("numberOfIndividuals"));

        // TODO just setting these to the same thing for now, as we don't keep track of when ontologies change
        // there is currently no way to set updated (everything gets updated every time we index now anyway)
        //
        loaded = localizedNode.getString("loaded");
        updated = localizedNode.getString("loaded");


        message = "";
        loadAttempts = 0;


        String embeddedTitle = localizedNode.getString("http://purl.org/dc/elements/1.1/title");

        if(embeddedTitle != null) {
            config.title = embeddedTitle;
        }

        String embeddedDesc = localizedNode.getString("http://purl.org/dc/elements/1.1/description");

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
