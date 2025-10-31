
package uk.ac.ebi.spot.ols.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import com.google.gson.JsonElement;

import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.neo4j.OlsNeo4jClient;
import uk.ac.ebi.spot.ols.repository.solr.SearchType;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformer;
import uk.ac.ebi.spot.ols.repository.solr.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrQuery;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.repository.helpers.DynamicFilterParser;
import uk.ac.ebi.spot.ols.repository.helpers.SearchFieldsParser;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

@Component
public class EntityRepository {

    @Autowired
    OlsSolrClient solrClient;

    @Autowired
    OlsNeo4jClient neo4jClient;


    public OlsFacetedResultsPage<JsonElement> find(
            Pageable pageable, String lang, String search, String searchFields, String boostFields, String facetFields, boolean exactMatch, Map<String, Collection<String>> properties, JsonTransformOptions outputOpts) throws IOException {

        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();
        query.setSearchText(search);
        query.setExactMatch(exactMatch);
        query.addFilter("type", List.of("entity"), SearchType.WHOLE_FIELD);
        SearchFieldsParser.addSearchFieldsToQuery(query, searchFields);
        SearchFieldsParser.addBoostFieldsToQuery(query, boostFields);
        SearchFieldsParser.addFacetFieldsToQuery(query, facetFields);
        DynamicFilterParser.addDynamicFiltersToQuery(query, properties);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public OlsFacetedResultsPage<JsonElement> findByOntologyId(
            String ontologyId, Pageable pageable, String lang, String search, String searchFields, String boostFields, String facetFields, boolean exactMatch, Map<String,Collection<String>> properties, JsonTransformOptions outputOpts) throws IOException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();
        query.setSearchText(search);
        query.setExactMatch(exactMatch);

        query.addFilter("type", List.of("entity"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);
        SearchFieldsParser.addSearchFieldsToQuery(query, searchFields);
        SearchFieldsParser.addBoostFieldsToQuery(query, boostFields);
        SearchFieldsParser.addFacetFieldsToQuery(query, facetFields);
        DynamicFilterParser.addDynamicFiltersToQuery(query, properties);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public JsonElement getByOntologyIdAndIri(String ontologyId, String iri, String lang, JsonTransformOptions outputOpts) throws ResourceNotFoundException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();

        query.addFilter("type", List.of("entity"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        JsonElement result = solrClient.getFirst(query);
        if (result == null) {
            return null;
        }

        return JsonTransformer.transformJson(
                result,
                lang,
                outputOpts);
    }

    // New method for issue #1042: Get entities that reference the specified entity in their relatedTo field
    // This effectively computes relatedFrom on-demand by querying relatedTo in reverse
    public OlsFacetedResultsPage<JsonElement> getRelatedFromByOntologyId(String ontologyId, Pageable pageable, String iri, boolean includeObsolete, String lang, JsonTransformOptions outputOpts) {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("entity"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);
        // Search for entities where relatedTo.value contains the target IRI
        query.addFilter("relatedTo.value", List.of(iri), SearchType.WHOLE_FIELD);

        if (!includeObsolete) {
            query.addFilter(IS_OBSOLETE.getText(), List.of("false"), SearchType.WHOLE_FIELD);
        }

        try {
            return solrClient.searchSolrPaginated(query, pageable)
                    .map(e -> JsonTransformer.transformJson(e, lang, outputOpts));
        } catch (IOException e) {
            throw new RuntimeException("Error querying Solr for relatedFrom entities", e);
        }
    }

}


