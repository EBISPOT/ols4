
package uk.ac.ebi.spot.ols.model.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import org.springframework.hateoas.server.core.Relation;
import uk.ac.ebi.spot.ols.service.GenericLocalizer;
import uk.ac.ebi.spot.ols.service.OboDatabaseUrlService;
import uk.ac.ebi.spot.ols.service.OntologyEntity;
import uk.ac.ebi.spot.ols.service.V1AnnotationExtractor;

import java.util.*;

import static uk.ac.ebi.spot.ols.model.v1.V1NodePropertyNameConstants.*;
import java.util.Collection;

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


        int lastUnderscore = shortForm.lastIndexOf("_");
        if(lastUnderscore != -1) {
            oboId = shortForm.substring(0, lastUnderscore) + ":"  + shortForm.substring(lastUnderscore + 1);
        } else {
            oboId = shortForm;
        }

        label = localizedObj.getString("label");
        description = localizedObj.getStrings("definition").toArray(new String[0]);
        synonyms = localizedObj.getStrings("synonym").toArray(new String[0]);
        annotation = V1AnnotationExtractor.extractAnnotations(localizedObj);

        inSubsets = null;



        oboDefinitionCitations = V1OboDefinitionCitation.extractFromEntity(localizedObj, oboDbUrls);
        oboXrefs = V1OboXref.extractFromEntity(localizedObj, oboDbUrls);
        oboSynonyms = V1OboSynonym.extractFromEntity(localizedObj, oboDbUrls);
        isPreferredRoot = false;
        related = new HashSet<>();

        isDefiningOntology = Boolean.parseBoolean(localizedObj.getString("isDefiningOntology"));
        hasChildren = Boolean.parseBoolean(localizedObj.getString("hasChildren"));



        Map<String,Object> iriToLabels = (Map<String,Object>) localizedObj.getObject("iriToLabels");

        related = new LinkedHashSet<>();

        for(Object _relatedTo : localizedObj.getObjects("relatedTo")) {

            Map<String,Object> relatedTo = (Map<String,Object>) _relatedTo;

            String predicate = (String) relatedTo.get("property");

            Object labels = iriToLabels.get(predicate);
            String label;
            if(labels instanceof Collection) {
                label = ((Collection<String>) labels).iterator().next();
            } else {
                label = (String) labels;
            }


            V1Related relatedObj = new V1Related();
            relatedObj.iri = predicate;
            relatedObj.label = label;
            relatedObj.ontologyName = ontologyName;
            relatedObj.relatedFromIri = iri;
            relatedObj.relatedToIri = (String) relatedTo.get("value");
            related.add(relatedObj);
        }





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