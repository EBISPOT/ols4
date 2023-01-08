
package uk.ac.ebi.spot.ols.repository.v1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.repository.solr.Fuzziness;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrQuery;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.repository.Validation;
import uk.ac.ebi.spot.ols.repository.v1.mappers.V1OntologyMapper;

@Component
public class V1OntologyRepository {

    @Autowired
    OlsSolrClient solrClient;

    public V1Ontology get(String ontologyId, String lang) {

        Validation.validateLang(lang);
        Validation.validateOntologyId(ontologyId);

        OlsSolrQuery query = new OlsSolrQuery();
	query.addFilter("lang", lang, Fuzziness.EXACT);
	query.addFilter("type", "ontology", Fuzziness.EXACT);
	query.addFilter("ontologyId", ontologyId, Fuzziness.EXACT);

        return V1OntologyMapper.mapOntology(solrClient.getOne(query), lang);
    }

    public Page<V1Ontology> getAll(String lang, Pageable pageable) {

        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();
	query.addFilter("lang", lang, Fuzziness.EXACT);
	query.addFilter("type", "ontology", Fuzziness.EXACT);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1OntologyMapper.mapOntology(result, lang));
    }
}
