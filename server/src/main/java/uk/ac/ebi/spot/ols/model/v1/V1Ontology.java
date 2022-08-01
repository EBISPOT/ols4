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

        Map<String,Object> ontologyConfig = (Map<String,Object>)
                localizedNode.asMap().get("ontologyConfig");

        config = new V1OntologyConfig();
        config.id = localizedNode.getString("ontologyId");
        config.versionIri = localizedNode.getString("http://www.w3.org/2002/07/owl#versionIRI");
        config.namespace = localizedNode.getString("id"); // TODO ??
        config.version = localizedNode.getString("http://www.w3.org/2002/07/owl#versionInfo");
        config.preferredPrefix = (String)ontologyConfig.get("preferredPrefix");
        config.title = (String)ontologyConfig.get("title");
        config.description = (String)ontologyConfig.get("description");
        config.homepage = (String)ontologyConfig.get("homepage");
        config.version = (String)ontologyConfig.get("version");
        config.mailingList = (String)ontologyConfig.get("mailingList");
        config.tracker = (String)ontologyConfig.get("tracker");
        config.logo = (String)ontologyConfig.get("logo");
        config.creators = (Collection<String>) ontologyConfig.get("creators");
        config.annotations = ontologyConfig.get("annotations");
        config.fileLocation = (String)ontologyConfig.get("fileLocation");

        config.oboSlims = ontologyConfig.containsKey ("oboSlims") ?
                (boolean) ontologyConfig.get("oboSlims") : false;

        config.labelProperty = (String)ontologyConfig.get("labelProperty");
        config.definitionProperties = (Collection<String>) ontologyConfig.get("definitionProperties");
        config.synonymProperties = (Collection<String>) ontologyConfig.get("synonymProperties");
        config.hierarchicalProperties = (Collection<String>) ontologyConfig.get("hierarchicalProperties");
        config.baseUris = (Collection<String>) ontologyConfig.get("baseUris");
        config.hiddenProperties = (Collection<String>) ontologyConfig.get("hiddenProperties");
        config.preferredRootTerms = (Collection<String>) ontologyConfig.get("preferredRootTerms");

        config.isSkos = ontologyConfig.containsKey("isSkos") ?
                (boolean) ontologyConfig.get("isSkos") : false;

        config.allowDownload = ontologyConfig.containsKey("allowDownload") ?
                (boolean) ontologyConfig.get("allowDownload") : true;

        config.internalMetadataProperties = ontologyConfig.containsKey("internalMetadataProperties") ?
                ontologyConfig.get("internalMetadataProperties") : new HashMap<String,Object>();

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
