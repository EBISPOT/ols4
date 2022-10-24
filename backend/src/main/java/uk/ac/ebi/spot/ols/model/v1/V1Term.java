
package uk.ac.ebi.spot.ols.model.v1;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

import com.google.gson.Gson;

import static uk.ac.ebi.spot.ols.model.v1.V1NodePropertyNameConstants.*;

import org.springframework.hateoas.core.Relation;
import uk.ac.ebi.spot.ols.service.V1AnnotationExtractor;
import uk.ac.ebi.spot.ols.service.OntologyEntity;

@Relation(collectionRelation = "terms")
public class V1Term {

    public static Gson gson = new Gson();

    public V1Term(OntologyEntity node, String lang) {

        if(!node.hasType("class")) {
            throw new IllegalArgumentException("Node has wrong type");
        }

        OntologyEntity localizedNode = new OntologyEntity(node, lang);

        this.lang = lang;
        iri = localizedNode.getString("uri");

        ontologyName = localizedNode.getString("ontologyId");
        ontologyPrefix = localizedNode.getString("ontologyPreferredPrefix");
        ontologyIri = localizedNode.getString("ontologyIri");


        shortForm = localizedNode.getString("shortForm");
        oboId = shortForm.replace("_", ":");

        label = localizedNode.getString("label");
        description = localizedNode.getStrings("definition").toArray(new String[0]);
        synonyms = localizedNode.getStrings("synonym").toArray(new String[0]);
        annotation = V1AnnotationExtractor.extractAnnotations(localizedNode);

        inSubsets = new HashSet<>();



        oboDefinitionCitations = V1OboDefinitionCitation.extractFromEntity(localizedNode);
        oboXrefs = V1OboXref.extractFromEntity(localizedNode);
        oboSynonyms = V1OboSynonym.extractFromEntity(localizedNode);
        isPreferredRoot = false;
        related = new HashSet<>();

        isDefiningOntology = !Boolean.parseBoolean(localizedNode.getString("imported"));


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