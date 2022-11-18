
package uk.ac.ebi.spot.ols.model.v2;

import org.springframework.hateoas.server.core.Relation;
import uk.ac.ebi.spot.ols.service.GenericLocalizer;

import java.util.Map;

@Relation(collectionRelation = "individuals")
public class V2Individual extends V2DynamicJsonResult {

    public V2Individual(Map<String,Object> jsonObj, String lang) {
        super(GenericLocalizer.localize(jsonObj, lang));
    }

}


