
package uk.ac.ebi.spot.ols.model.v1;

import java.util.Set;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.hateoas.core.Relation;
import uk.ac.ebi.spot.ols.repository.v1.V1PropertyRepository;
import uk.ac.ebi.spot.ols.service.OwlGraphNode;

import static uk.ac.ebi.spot.ols.model.v1.V1NodePropertyNameConstants.*;


@Relation(collectionRelation = "individuals")
public class V1Individual {


    public V1Individual(OwlGraphNode node, V1Ontology ontology, String lang) {

        if(!node.hasType("individual")) {
            throw new IllegalArgumentException("Node has wrong type");
        }

        OwlGraphNode localizedNode = new OwlGraphNode(node, lang);

        iri = localizedNode.get("uri");
        lang = "en";

        ontologyName = localizedNode.get("ontologyId");
        ontologyPrefix = ontology.config.preferredPrefix;
        ontologyIri = ontology.config.id;

        label = localizedNode.get("http://www.w3.org/2000/01/rdf-schema#label");

        shortForm = localizedNode.get("shortForm");
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


