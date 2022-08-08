
package uk.ac.ebi.spot.ols.repository.v2;

import com.google.gson.Gson;
import org.apache.solr.client.solrj.SolrQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import uk.ac.ebi.spot.ols.model.v2.V2Ontology;
import uk.ac.ebi.spot.ols.model.v2.V2Ontology;
import uk.ac.ebi.spot.ols.repository.Neo4jQueryHelper;
import uk.ac.ebi.spot.ols.repository.SolrQueryHelper;
import uk.ac.ebi.spot.ols.repository.Validation;

import javax.validation.Valid;
import java.io.IOException;

@Component
public class V2OntologyRepository {

    @Autowired
    SolrQueryHelper solrQueryHelper;

    @Autowired
    Neo4jQueryHelper neo4jQueryHelper;


    public Page<V2Ontology> find(
            Pageable pageable, String lang, String search, String searchFields) throws IOException {

        Validation.validateLang(lang);

        if(search != null && searchFields == null) {
            searchFields = "http://www.w3.org/2000/01/rdf-schema#label^100 definition";
        }

        SolrQuery query = solrQueryHelper.createSolrQuery(lang, search, searchFields);
        query.addFilterQuery("type:ontology");

        return solrQueryHelper.searchSolrPaginated(query, pageable)
                .map(result -> new V2Ontology(result, lang));
    }

    public V2Ontology getById(String ontologyId, String lang) throws ResourceNotFoundException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        SolrQuery query = solrQueryHelper.createSolrQuery(lang, null, null);
        query.addFilterQuery("type:ontology");
        query.addFilterQuery("ontologyId:" + ontologyId);

        return new V2Ontology(this.solrQueryHelper.getOne(query), lang);
    }


}



