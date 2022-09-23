
package uk.ac.ebi.spot.ols.repository.v2;

import org.apache.solr.client.solrj.SolrQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.Neo4jQueryHelper;
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

        if(search != null && searchFields == null) {
            searchFields = "http://www.w3.org/2000/01/rdf-schema#label^100 definition";
        }

        SolrQuery query = solrQueryHelper.createSolrQuery(lang, search, searchFields);
        query.addFilterQuery("str_type:entity");
        solrQueryHelper.addDynamicFilterProperties(query, properties);

        return this.solrQueryHelper.searchSolrPaginated(query, pageable)
                .map(result -> new V2Entity(result, lang));
    }

    public Page<V2Entity> findByOntologyId(
            String ontologyId, Pageable pageable, String lang, String search, String searchFields, Map<String,String> properties) throws IOException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        if(search != null && searchFields == null) {
            searchFields = "http://www.w3.org/2000/01/rdf-schema#label^100 definition";
        }

        SolrQuery query = solrQueryHelper.createSolrQuery(lang, search, searchFields);
        query.addFilterQuery("str_type:entity");
        query.addFilterQuery("str_ontologyId:" + ontologyId);
        solrQueryHelper.addDynamicFilterProperties(query, properties);

        return this.solrQueryHelper.searchSolrPaginated(query, pageable)
                .map(result -> new V2Entity(result, lang));
    }

    public V2Entity getByOntologyIdAndUri(String ontologyId, String uri, String lang) throws ResourceNotFoundException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        // TODO: change to query by ontologyid and uri separately instead of by id because we don't know the type to
        // make an id string. there may be multiple results tho??

        throw new RuntimeException("not implemented rn");
//        String id = ontologyId + "+" + uri;
//
//        return new V2Term(this.neo4jQueryHelper.getOne("OntologyTerm", "id", id), lang);

    }


}


