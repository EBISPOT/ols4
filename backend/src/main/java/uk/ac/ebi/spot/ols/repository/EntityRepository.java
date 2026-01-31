
package uk.ac.ebi.spot.ols.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import com.google.gson.JsonElement;

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

@Component
public class EntityRepository {

    @Autowired
    OlsSolrClient solrClient;


    public OlsFacetedResultsPage<JsonElement> find(
            Pageable pageable, String lang, String search, String searchFields, String boostFields, String facetFields, boolean exactMatch, Collection<String> excludeOntologyIds, Map<String, Collection<String>> properties, JsonTransformOptions outputOpts) throws IOException {

        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();
        query.setSearchText(search);
        query.setExactMatch(exactMatch);

        // Only add the default type=entity filter if the user hasn't specified a type filter
        if (properties == null || !properties.containsKey("type")) {
            query.addFilter("type", List.of("entity"), SearchType.WHOLE_FIELD);
        }

        // Add exclude filter for ontologies if provided
        if (excludeOntologyIds != null && !excludeOntologyIds.isEmpty()) {
            query.addExcludeFilter("ontologyId", excludeOntologyIds, SearchType.CASE_INSENSITIVE_TOKENS);
        }

        SearchFieldsParser.addSearchFieldsToQuery(query, searchFields);
        SearchFieldsParser.addBoostFieldsToQuery(query, boostFields);
        SearchFieldsParser.addFacetFieldsToQuery(query, facetFields);
        DynamicFilterParser.addDynamicFiltersToQuery(query, properties);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public OlsFacetedResultsPage<JsonElement> find(
            Pageable pageable, String lang, String search, String searchFields, String boostFields, String facetFields,
            boolean exactMatch, Map<String, Collection<String>> properties,
            JsonTransformOptions outputOpts) throws IOException {

        return find(pageable, lang, search, searchFields, boostFields, facetFields, exactMatch, null, properties, outputOpts);

        }

    public OlsFacetedResultsPage<JsonElement> findByOntologyId(
            String ontologyId, Pageable pageable, String lang, String search, String searchFields, String boostFields, String facetFields, boolean exactMatch, Map<String,Collection<String>> properties, JsonTransformOptions outputOpts) throws IOException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();
        query.setSearchText(search);
        query.setExactMatch(exactMatch);

        // Only add the default type=entity filter if the user hasn't specified a type filter
        if (properties == null || !properties.containsKey("type")) {
            query.addFilter("type", List.of("entity"), SearchType.WHOLE_FIELD);
        }

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

    /**
     * Get entities that have a relatedTo relationship pointing to the specified entity (i.e., relatedFrom).
     * This is the inverse of relatedTo - it returns entities that reference the given entity in their definitions.
     *
     * @param ontologyId The ontology ID
     * @param iri The IRI of the entity to find related-from entities for
     * @param pageable Pagination parameters
     * @param lang Language for localization
     * @param outputOpts JSON transformation options
     * @return Paginated list of entities that reference this entity
     * @throws IOException If there's an error querying
     */
    public OlsFacetedResultsPage<JsonElement> getRelatedFrom(
            String ontologyId, String iri, Pageable pageable, String lang, JsonTransformOptions outputOpts) throws IOException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        // Query Solr for entities that have this entity in their relatedTo field
        // Since relatedFrom = entities that have relatedTo pointing to this entity
        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("entity"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);
        query.addFilter("relatedTo", List.of(iri), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts));
    }




}


