
package uk.ac.ebi.spot.ols.repository.v1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.repository.Neo4jQueryHelper;

@Component
public class V1OntologyRepository {

    @Autowired
    Neo4jQueryHelper neo4jQueryHelper;

    public V1Ontology get(String id, String lang) {

       return new V1Ontology(this.neo4jQueryHelper.getOne("Ontology", id), lang);

    }

    public Page<V1Ontology> getAll(String lang, Pageable pageable) {

        return this.neo4jQueryHelper.getAll("Ontology", pageable)
                .map(r -> new V1Ontology(r, lang));

    }

}
