
package uk.ac.ebi.spot.ols.model.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import org.springframework.hateoas.server.core.Relation;
import uk.ac.ebi.spot.ols.service.GenericLocalizer;
import uk.ac.ebi.spot.ols.service.OboDatabaseUrlService;
import uk.ac.ebi.spot.ols.service.OntologyEntity;
import uk.ac.ebi.spot.ols.service.V1AnnotationExtractor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static uk.ac.ebi.spot.ols.model.v1.V1NodePropertyNameConstants.*;

@Relation(collectionRelation = "terms")
public class V1Term {

    public static Gson gson = new Gson();

    public V1Term(Map<String,Object> jsonObj, String lang, OboDatabaseUrlService oboDbUrls) {

        OntologyEntity localizedObj = new OntologyEntity(GenericLocalizer.localize(jsonObj, lang));

        this.lang = lang;
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

        inSubsets = new HashSet<>();



        oboDefinitionCitations = V1OboDefinitionCitation.extractFromEntity(localizedObj, oboDbUrls);
        oboXrefs = V1OboXref.extractFromEntity(localizedObj, oboDbUrls);
        oboSynonyms = V1OboSynonym.extractFromEntity(localizedObj, oboDbUrls);
        isPreferredRoot = false;
        related = new HashSet<>();

        isDefiningOntology = !Boolean.parseBoolean(localizedObj.getString("imported"));


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

    @JsonProperty(value = TERM_REPLACED_BY)
    public String termReplacedBy;

    @JsonProperty(value = IS_DEFINING_ONTOLOGY)
    public boolean isDefiningOntology;

    @JsonProperty(value = HAS_CHILDREN)
    public boolean hasChildren;

    @JsonProperty(value = IS_ROOT)
    public boolean isRoot;

    @JsonProperty(value = SHORT_FORM)
    public String shortForm;

    @JsonProperty(value = OBO_ID)
    public String oboId;

    @JsonProperty(value = IN_SUBSET)
    public Set<String> inSubsets;

    public Map<String,Object> annotation;

    @JsonProperty(value = OBO_DEFINITION_CITATION)
    public List<V1OboDefinitionCitation> oboDefinitionCitations;

    @JsonProperty(value = OBO_XREF)
    public List<V1OboXref> oboXrefs;

    @JsonProperty(value = OBO_SYNONYM)
    public List<V1OboSynonym> oboSynonyms;

    @JsonProperty(value = IS_PREFERRED_ROOT)
    public boolean isPreferredRoot;

    @JsonIgnore
    public Set<V1Related> related;
}