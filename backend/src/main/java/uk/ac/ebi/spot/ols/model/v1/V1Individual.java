
package uk.ac.ebi.spot.ols.model.v1;

import java.util.Set;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.hateoas.core.Relation;
import uk.ac.ebi.spot.ols.service.OntologyEntity;
import uk.ac.ebi.spot.ols.service.V1AnnotationExtractor;

import static uk.ac.ebi.spot.ols.model.v1.V1NodePropertyNameConstants.*;


@Relation(collectionRelation = "individuals")
public class V1Individual {


    public V1Individual(OntologyEntity node, String lang) {

        if(!node.hasType("individual")) {
            throw new IllegalArgumentException("Node has wrong type");
        }

        OntologyEntity localizedNode = new OntologyEntity(node, lang);

        iri = localizedNode.getString("uri");
        lang = "en";

        ontologyName = localizedNode.getString("ontologyId");
        ontologyPrefix = localizedNode.getString("ontologyPreferredPrefix");
        ontologyIri = localizedNode.getString("ontologyIri");

        label = localizedNode.getString("label");
        description = localizedNode.getStrings("definition").toArray(new String[0]);
        synonyms = localizedNode.getStrings("synonym").toArray(new String[0]);
        annotation = V1AnnotationExtractor.extractAnnotations(localizedNode);

        shortForm = localizedNode.getString("shortForm");
        oboId = shortForm.replace("_", ":");
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

    @JsonProperty(value = IN_SUBSET)
    public Set<String> inSubsets;

    public Map<String,Object> annotation;

    public V1Term[] type;
}


