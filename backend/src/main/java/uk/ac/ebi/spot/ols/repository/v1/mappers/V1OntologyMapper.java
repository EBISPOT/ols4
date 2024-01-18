package uk.ac.ebi.spot.ols.repository.v1.mappers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.model.v1.V1OntologyConfig;
import uk.ac.ebi.spot.ols.repository.transforms.LocalizationTransform;
import uk.ac.ebi.spot.ols.repository.v1.JsonHelper;

import java.util.Map;
import java.util.Objects;

public class V1OntologyMapper {

    private static final Gson gson = new Gson();

    public static V1Ontology mapOntology(JsonElement json, String lang) {

        V1Ontology ontology = new V1Ontology();

        JsonObject localizedJson = Objects.requireNonNull(LocalizationTransform.transform(json, lang))
                .getAsJsonObject();

        ontology.lang =lang;

        ontology.ontologyId = JsonHelper.getString(localizedJson, "ontologyId");
        ontology.fileHash = JsonHelper.getString(localizedJson, "fileHash");
        ontology.languages = JsonHelper.getStrings(localizedJson, "language");

        ontology.config = new V1OntologyConfig();

        ontology.config.id = JsonHelper.getString(localizedJson, "ontologyId");
        ontology.config.namespace = ontology.config.id;
        ontology.config.versionIri = JsonHelper.getString(localizedJson, "http://www.w3.org/2002/07/owl#versionIRI");
        ontology.config.version = JsonHelper.getString(localizedJson, "http://www.w3.org/2002/07/owl#versionInfo");
        ontology.config.preferredPrefix = JsonHelper.getString(localizedJson, "preferredPrefix");
        ontology.config.title = JsonHelper.getString(localizedJson, "title");
        ontology.config.description = JsonHelper.getString(localizedJson, "description");
        ontology.config.homepage = JsonHelper.getString(localizedJson, "homepage");
        ontology.config.version = JsonHelper.getString(localizedJson, "version");
        ontology.config.mailingList = JsonHelper.getString(localizedJson, "mailing_list");
        ontology.config.tracker = JsonHelper.getString(localizedJson, "tracker");
        ontology.config.logo = JsonHelper.getString(localizedJson, "logo");
        ontology.config.creators = JsonHelper.getStrings(localizedJson, "creators");
        ontology.config.collection = JsonHelper.getStrings(localizedJson, "collection");
        ontology.config.subject = JsonHelper.getStrings(localizedJson, "subject");
        ontology.config.classifications = JsonHelper.getObject(localizedJson,"classifications");
        ontology.config.annotations = gson.fromJson(localizedJson.get("annotations"), Map.class);
        ontology.config.fileLocation = JsonHelper.getString(localizedJson, "ontology_purl");
        ontology.config.oboSlims = localizedJson.has("oboSlims") && localizedJson.get("oboSlims").getAsBoolean();

        ontology.config.labelProperty = JsonHelper.getString(localizedJson, "label_property");

        if(ontology.config.labelProperty == null) {
            ontology.config.labelProperty = "http://www.w3.org/2000/01/rdf-schema#label";
        }

        ontology.config.definitionProperties = JsonHelper.getStrings(localizedJson, "definition_property");
        ontology.config.synonymProperties = JsonHelper.getStrings(localizedJson, "synonym_property");
        ontology.config.hierarchicalProperties = JsonHelper.getStrings(localizedJson, "hierarchical_property");
        ontology.config.baseUris = JsonHelper.getStrings(localizedJson, "base_uri");
        ontology.config.hiddenProperties = JsonHelper.getStrings(localizedJson, "hidden_property");
        ontology.config.preferredRootTerms = JsonHelper.getStrings(localizedJson, "preferredRootTerms");

        ontology.config.isSkos = localizedJson.has("isSkos") && localizedJson.get("isSkos").getAsBoolean();
        ontology.config.allowDownload = localizedJson.has("allowDownload") && localizedJson.get("allowDownload").getAsBoolean();


        ontology.status = "LOADED";

        ontology.numberOfTerms = Integer.parseInt(JsonHelper.getString(localizedJson, "numberOfClasses"));
        ontology.numberOfProperties = Integer.parseInt(JsonHelper.getString(localizedJson, "numberOfProperties"));
        ontology.numberOfIndividuals = Integer.parseInt(JsonHelper.getString(localizedJson, "numberOfIndividuals"));

        // TODO just setting these to the same thing for now, as we don't keep track of when ontologies change
        // there is currently no way to set updated (everything gets updated every time we index now anyway)
        //
        ontology.loaded = JsonHelper.getString(localizedJson, "loaded");
        ontology.updated = JsonHelper.getString(localizedJson, "loaded");


        ontology.version = JsonHelper.getString(localizedJson, "http://www.w3.org/2002/07/owl#versionInfo");
        ontology.config.version = ontology.version;

        ontology.message = "";
        ontology.loadAttempts = 0;


        String embeddedTitle = JsonHelper.getString(localizedJson, "http://purl.org/dc/elements/1.1/title");

        if(embeddedTitle != null) {
            ontology.config.title = embeddedTitle;
        }

        String embeddedDesc = JsonHelper.getString(localizedJson, "http://purl.org/dc/elements/1.1/description");

        if(embeddedDesc != null) {
            ontology.config.description = embeddedDesc;
        }

        return ontology;
    }
}
