
package uk.ac.ebi.spot.ols.model.v2;

import org.springframework.hateoas.server.core.Relation;
import uk.ac.ebi.spot.ols.service.GenericLocalizer;

import java.util.Map;

@Relation(collectionRelation = "ontologies")
public class V2Ontology extends V2DynamicJsonResult {

    public V2Ontology(Map<String,Object> jsonObj, String lang) {
        super(GenericLocalizer.localize(jsonObj, lang));
    }

}

