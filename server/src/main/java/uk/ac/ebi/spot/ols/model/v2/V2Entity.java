

package uk.ac.ebi.spot.ols.model.v2;

import org.springframework.hateoas.core.Relation;
import uk.ac.ebi.spot.ols.service.GenericLocalizer;
import uk.ac.ebi.spot.ols.service.OntologyEntity;

@Relation(collectionRelation = "terms")
public class V2Entity extends DynamicJsonObject {

    public V2Entity(OntologyEntity node, String lang) {

        if(!node.hasType("entity")) {
            throw new IllegalArgumentException("Node has wrong type");
        }

        put("lang", lang);

        for(String k : node.asMap().keySet()) {
            Object v = node.asMap().get(k);
            put(k, GenericLocalizer.localize(v, lang));
        }

    }

}



