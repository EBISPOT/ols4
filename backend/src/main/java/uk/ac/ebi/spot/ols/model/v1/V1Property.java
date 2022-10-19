package uk.ac.ebi.spot.ols.model.v1;

import java.util.Map;

import org.springframework.hateoas.core.Relation;
import uk.ac.ebi.spot.ols.service.V1AnnotationExtractor;
import uk.ac.ebi.spot.ols.service.OntologyEntity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;

import static uk.ac.ebi.spot.ols.model.v1.V1NodePropertyNameConstants.*;

@Relation(collectionRelation = "properties")
public class V1Property {

    public static Gson gson = new Gson();

    public V1Property(OntologyEntity node, String lang) {

        if(!node.hasType("property")) {
            throw new IllegalArgumentException("Node has wrong type");
        }

        OntologyEntity localizedNode = new OntologyEntity(node, lang);

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
