
package uk.ac.ebi.spot.ols.model.v2;

import org.springframework.hateoas.core.Relation;
import uk.ac.ebi.spot.ols.service.GenericLocalizer;
import uk.ac.ebi.spot.ols.service.OwlGraphNode;

@Relation(collectionRelation = "classes")
public class V2Class extends DynamicJsonObject {

    public V2Class(OwlGraphNode node, String lang) {

        if(!node.hasType("class")) {
            throw new IllegalArgumentException("Node has wrong type");
        }

        put("lang", lang);

        for(String k : node.asMap().keySet()) {
            Object v = node.asMap().get(k);
            put(k, GenericLocalizer.localize(v, lang));
        }

    }

}

