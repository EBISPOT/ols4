
package uk.ac.ebi.spot.ols.model.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.JsonElement;
import org.springframework.hateoas.server.core.Relation;
import uk.ac.ebi.spot.ols.repository.transforms.LocalizationTransform;
import uk.ac.ebi.spot.ols.repository.v1.mappers.AnnotationExtractor;

import java.util.List;
import java.util.Map;

import static uk.ac.ebi.spot.ols.model.v1.V1NodePropertyNameConstants.*;


@Relation(collectionRelation = "individuals")
public class V1Individual {



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
    public List<String> inSubsets;

    public Map<String,Object> annotation;

    public V1Term[] type;
}


