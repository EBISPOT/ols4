
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
import uk.ac.ebi.spot.ols.service.EmbeddingServiceClient;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class EntityRepository {

    @Autowired
    OlsSolrClient solrClient;

    @Autowired
    OlsNeo4jClient neo4jClient;
    
    @Autowired
    EmbeddingServiceClient embeddingServiceClient;


    public OlsFacetedResultsPage<JsonElement> find(
            Pageable pageable, String lang, String search, String searchFields, String boostFields, String facetFields, boolean exactMatch, Map<String, Collection<String>> properties, String model, JsonTransformOptions outputOpts) throws IOException {

        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();
        
        // Choose between vector search or text search based on model parameter
        if (model != null && !model.isEmpty()) {
            // Vector search: embed the query text
            float[] embeddings = embeddingServiceClient.embedText(model, search);
            query.setEmbeddingVector(embeddings, model);
            query.setTopK(pageable.getPageSize());
        } else {
            // Text search
            query.setSearchText(search);
            query.setExactMatch(exactMatch);
        }
        
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
            String ontologyId, Pageable pageable, String lang, String search, String searchFields, String boostFields, String facetFields, boolean exactMatch, Map<String,Collection<String>> properties, String model, JsonTransformOptions outputOpts) throws IOException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();
        
        // Choose between vector search or text search based on model parameter
        if (model != null && !model.isEmpty()) {
            // Vector search: embed the query text
            float[] embeddings = embeddingServiceClient.embedText(model, search);
            query.setEmbeddingVector(embeddings, model);
            query.setTopK(pageable.getPageSize());
        } else {
            // Text search
            query.setSearchText(search);
            query.setExactMatch(exactMatch);
        }

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




}


