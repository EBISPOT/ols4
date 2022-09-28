
package uk.ac.ebi.spot.ols.repository.v2;

import org.apache.solr.client.solrj.SolrQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.Neo4jQueryHelper;
import uk.ac.ebi.spot.ols.repository.OlsSolrQuery;
import uk.ac.ebi.spot.ols.repository.SolrQueryHelper;
import uk.ac.ebi.spot.ols.repository.Validation;

import java.io.IOException;
import java.util.Map;

@Component
public class V2EntityRepository {

    @Autowired
    SolrQueryHelper solrQueryHelper;

    @Autowired
    Neo4jQueryHelper neo4jQueryHelper;


    public Page<V2Entity> find(
            Pageable pageable, String lang, String search, String searchFields, Map<String,String> properties) throws IOException {

        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();
	query.setSearch(search, searchFields);
	query.addFilter("lang", lang, true);
	query.addFilter("type", "entity", true);
        query.addDynamicFilterProperties(properties);

        return solrQueryHelper.searchSolrPaginated(query, pageable)
                .map(result -> new V2Entity(result, lang));
    }

    public Page<V2Entity> findByOntologyId(
            String ontologyId, Pageable pageable, String lang, String search, String searchFields, Map<String,String> properties) throws IOException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();
	query.setSearch(search, searchFields);
	query.addFilter("lang", lang, true);
	query.addFilter("type", "entity", true);
	query.addFilter("ontologyId", ontologyId, true);
        query.addDynamicFilterProperties(properties);

        return solrQueryHelper.searchSolrPaginated(query, pageable)
                .map(result -> new V2Entity(result, lang));
    }

    public V2Entity getByOntologyIdAndUri(String ontologyId, String uri, String lang) throws ResourceNotFoundException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();
	query.addFilter("lang", lang, true);
	query.addFilter("type", "entity", true);
	query.addFilter("ontologyId", ontologyId, true);
	query.addFilter("uri", uri, true);

        return new V2Entity(solrQueryHelper.getOne(query), lang);

    }


}


