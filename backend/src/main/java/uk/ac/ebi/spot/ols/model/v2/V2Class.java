
package uk.ac.ebi.spot.ols.model.v2;

import org.springframework.hateoas.server.core.Relation;
import uk.ac.ebi.spot.ols.service.GenericLocalizer;

import java.util.Map;

@Relation(collectionRelation = "classes")
public class V2Class extends V2DynamicJsonResult {

    public V2Class(Map<String,Object> jsonObj, String lang) {
        super(GenericLocalizer.localize(jsonObj, lang));
    }

}

