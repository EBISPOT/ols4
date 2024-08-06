
package uk.ac.ebi.spot.ols.repository.v2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.neo4j.OlsNeo4jClient;
import uk.ac.ebi.spot.ols.repository.solr.SearchType;
import uk.ac.ebi.spot.ols.repository.solr.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrQuery;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.repository.Validation;
import uk.ac.ebi.spot.ols.repository.transforms.LocalizationTransform;
import uk.ac.ebi.spot.ols.repository.transforms.RemoveLiteralDatatypesTransform;
import uk.ac.ebi.spot.ols.repository.v2.helpers.V2DynamicFilterParser;
import uk.ac.ebi.spot.ols.repository.v2.helpers.V2SearchFieldsParser;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class V2IndividualRepository {

    @Autowired
    OlsSolrClient solrClient;

    @Autowired
    OlsNeo4jClient neo4jClient;


    public OlsFacetedResultsPage<V2Entity> find(
            Pageable pageable, String lang, String search, String searchFields, String boostFields, boolean exactMatch, Map<String,Collection<String>> properties) throws IOException {

        Validation.validateLang(lang);

        if(search != null && searchFields == null) {
            searchFields = "label^100 definition";
        }

        OlsSolrQuery query = new OlsSolrQuery();
        query.setSearchText(search);
        query.setExactMatch(exactMatch);
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
        V2SearchFieldsParser.addSearchFieldsToQuery(query, searchFields);
        V2SearchFieldsParser.addBoostFieldsToQuery(query, boostFields);
        V2DynamicFilterParser.addDynamicFiltersToQuery(query, properties);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(e -> LocalizationTransform.transform(e, lang))
                .map(RemoveLiteralDatatypesTransform::transform)
                .map(V2Entity::new);
    }

    public OlsFacetedResultsPage<V2Entity> findByOntologyId(
            String ontologyId, Pageable pageable, String lang, String search, String searchFields, String boostFields, boolean exactMatch, Map<String, Collection<String>> properties) throws IOException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        if(search != null && searchFields == null) {
            searchFields = "label^100 definition";
        }

        OlsSolrQuery query = new OlsSolrQuery();
        query.setSearchText(search);
        query.setExactMatch(exactMatch);
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);
        V2SearchFieldsParser.addSearchFieldsToQuery(query, searchFields);
        V2SearchFieldsParser.addBoostFieldsToQuery(query, boostFields);
        V2DynamicFilterParser.addDynamicFiltersToQuery(query, properties);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(e -> LocalizationTransform.transform(e, lang))
                .map(RemoveLiteralDatatypesTransform::transform)
                .map(V2Entity::new);

    }

    public V2Entity getByOntologyIdAndIri(String ontologyId, String iri, String lang) throws ResourceNotFoundException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        return new V2Entity(
                RemoveLiteralDatatypesTransform.transform(
                        LocalizationTransform.transform(
                                solrClient.getFirst(query),
                                lang
                        )
                )
        );
    }

    public OlsFacetedResultsPage<V2Entity> getIndividualsOfClass(
            String ontologyId, String classIri, Pageable pageable, String lang) throws IOException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);
        query.addFilter("http__//www.w3.org/1999/02/22-rdf-syntax-ns#type", List.of(classIri), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(e -> LocalizationTransform.transform(e, lang))
                .map(RemoveLiteralDatatypesTransform::transform)
                .map(V2Entity::new);

    }



}

