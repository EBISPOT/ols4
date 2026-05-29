
package uk.ac.ebi.spot.ols.repository.v1;

import com.google.gson.JsonElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.repository.search.SearchType;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.repository.Validation;
import uk.ac.ebi.spot.ols.repository.v1.mappers.V1OntologyMapper;

import java.util.List;

@Component
public class V1OntologyRepository {

    @Autowired
    OlsSearchClient searchClient;

    public V1Ontology get(String ontologyId, String lang) {

        Validation.validateLang(lang);
        Validation.validateOntologyId(ontologyId);

        OlsSearchQuery query = new OlsSearchQuery();
	query.addFilter("type", List.of("ontology"), SearchType.WHOLE_FIELD);
	query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);

        JsonElement result = searchClient.getFirst(query);
        if (result == null) {
            return null;
        }
        return V1OntologyMapper.mapOntology(result, lang);
    }

    public Page<V1Ontology> getAll(String lang, Pageable pageable) {

        Validation.validateLang(lang);

        OlsSearchQuery query = new OlsSearchQuery();
	query.addFilter("type", List.of("ontology"), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1OntologyMapper.mapOntology(result, lang));
    }
}
