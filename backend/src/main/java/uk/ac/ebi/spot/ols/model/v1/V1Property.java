package uk.ac.ebi.spot.ols.model.v1;

import java.util.Map;

import org.springframework.hateoas.core.Relation;
import uk.ac.ebi.spot.ols.service.GenericLocalizer;
import uk.ac.ebi.spot.ols.service.V1AnnotationExtractor;
import uk.ac.ebi.spot.ols.service.OntologyEntity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;

import static uk.ac.ebi.spot.ols.model.v1.V1NodePropertyNameConstants.*;

@Relation(collectionRelation = "properties")
public class V1Property {

    public static Gson gson = new Gson();

    public V1Property(Map<String,Object> jsonObj, String lang) {

        OntologyEntity localizedObj = new OntologyEntity(GenericLocalizer.localize(jsonObj, lang));

        iri = localizedObj.getString("iri");

        ontologyName = localizedObj.getString("ontologyId");
        ontologyPrefix = localizedObj.getString("ontologyPreferredPrefix");
        ontologyIri = localizedObj.getString("ontologyIri");

        shortForm = localizedObj.getString("shortForm");
        oboId = shortForm.replace("_", ":");

        label = localizedObj.getString("label");
        description = localizedObj.getStrings("definition").toArray(new String[0]);
        synonyms = localizedObj.getStrings("synonym").toArray(new String[0]);
        annotation = V1AnnotationExtractor.extractAnnotations(localizedObj);
    }

    public String iri;

    public String lang;

    @JsonProperty(value = LABEL)
    public String label;

    public String[] description;
    public String[] synonyms;

    @JsonProperty(value = ONTOLOGY_NAME)
    public String ontologyName;

    @JsonProperty(value = ONTOLOGY_PREFIX)
    public String ontologyPrefix;

    @JsonProperty(value = ONTOLOGY_IRI)
    public String ontologyIri;

    @JsonProperty(value = IS_OBSOLETE)
    public boolean isObsolete;

    @JsonProperty(value = IS_DEFINING_ONTOLOGY)
    public boolean isLocal;

    @JsonProperty(value = HAS_CHILDREN)
    public boolean hasChildren;

    @JsonProperty(value = IS_ROOT)
    public boolean isRoot;

    @JsonProperty(value = SHORT_FORM)
    public String shortForm;

    @JsonProperty(value = OBO_ID)
    public String oboId;

    public Map<String,Object> annotation;
}
