
package uk.ac.ebi.spot.ols.repository.v1;

import org.apache.solr.client.solrj.SolrQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.model.v2.V2Ontology;
import uk.ac.ebi.spot.ols.repository.Neo4jQueryHelper;
import uk.ac.ebi.spot.ols.repository.SolrQueryHelper;
import uk.ac.ebi.spot.ols.repository.Validation;

@Component
public class V1OntologyRepository {

    @Autowired
    SolrQueryHelper solrQueryHelper;

    public V1Ontology get(String id, String lang) {

        Validation.validateLang(lang);
        Validation.validateOntologyId(id);

        SolrQuery query = SolrQueryHelper.createSolrQuery(lang, null, null);
        query.addFilterQuery("type:ontology");
        query.addFilterQuery("ontologyId:" + id);

        return new V1Ontology(solrQueryHelper.getOne(query), lang);
    }

    public Page<V1Ontology> getAll(String lang, Pageable pageable) {

        Validation.validateLang(lang);

        SolrQuery query = SolrQueryHelper.createSolrQuery(lang, null, null);
        query.addFilterQuery("type:ontology");

        return solrQueryHelper.searchSolrPaginated(query, pageable)
                .map(res -> new V1Ontology(res, lang));
    }
}
