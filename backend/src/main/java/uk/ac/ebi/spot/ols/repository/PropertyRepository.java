
package uk.ac.ebi.spot.ols.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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

import static uk.ac.ebi.ols.shared.DefinedFields.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class PropertyRepository {

    @Autowired
    OlsSolrClient solrClient;

    @Autowired
    OlsNeo4jClient neo4jClient;


    public OlsFacetedResultsPage<JsonElement> find(
            Pageable pageable, String lang, String search, String searchFields, String boostFields, boolean exactMatch, Map<String,Collection<String>> properties, JsonTransformOptions outputOpts) throws IOException {

        Validation.validateLang(lang);

        if(search != null && searchFields == null) {
            searchFields = LABEL.getText()+"^100 " + DEFINITION.getText();
        }

        OlsSolrQuery query = new OlsSolrQuery();
        query.setSearchText(search);
        query.setExactMatch(exactMatch);
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        SearchFieldsParser.addSearchFieldsToQuery(query, searchFields);
        SearchFieldsParser.addBoostFieldsToQuery(query, boostFields);
        DynamicFilterParser.addDynamicFiltersToQuery(query, properties);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public OlsFacetedResultsPage<JsonElement> findByOntologyId(
            String ontologyId, Pageable pageable, String lang, String search, String searchFields, String boostFields, boolean exactMatch,  Map<String, Collection<String>> properties, JsonTransformOptions outputOpts) throws IOException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        if(search != null && searchFields == null) {
            searchFields = LABEL.getText() + "^100 " + DEFINITION.getText();
        }

        OlsSolrQuery query = new OlsSolrQuery();
        query.setExactMatch(exactMatch);
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);
        SearchFieldsParser.addSearchFieldsToQuery(query, searchFields);
        SearchFieldsParser.addBoostFieldsToQuery(query, boostFields);
        DynamicFilterParser.addDynamicFiltersToQuery(query, properties);
        query.setSearchText(search);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public V2Entity getByOntologyIdAndIri(String ontologyId, String iri, String lang, JsonTransformOptions outputOpts) throws ResourceNotFoundException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        return new V2Entity(
            JsonTransformer.transformJson(
                solrClient.getFirst(query),
                lang,
                outputOpts));
    }

    public Page<JsonElement> getChildrenByOntologyId(String ontologyId, Pageable pageable, String iri, String lang, JsonTransformOptions outputOpts) {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        String id = ontologyId + "+property+" + iri;

        return this.neo4jClient.traverseIncomingEdges("OntologyProperty", id,
                        Arrays.asList(DIRECT_PARENT.getText()), Map.of(), Map.of(), pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public Page<JsonElement> getAncestorsByOntologyId(String ontologyId, Pageable pageable, String iri, String lang, JsonTransformOptions outputOpts) {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        String id = ontologyId + "+property+" + iri;

        return this.neo4jClient.recursivelyTraverseOutgoingEdges("OntologyProperty", id,
                        Arrays.asList(DIRECT_PARENT.getText()), Map.of(), Map.of(), pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public Page<JsonElement> getSimilarByOntologyId(String ontologyId, Pageable pageable, String iri, String lang, JsonTransformOptions outputOpts) {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        return this.neo4jClient.getSimilar("OntologyProperty", iri, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public Page<JsonElement> getSimilarByOntologyIdAndFilters(String ontologyId, Pageable pageable, String iri, Map<String, Collection<String>> properties, String lang, JsonTransformOptions outputOpts) {
        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        Page<JsonElement> similarResults = this.neo4jClient.getSimilar("OntologyProperty", iri, pageable);
        
        return this.filterResults(similarResults, properties, lang, outputOpts);
    }

    private Page<JsonElement> filterResults(Page<JsonElement> results, Map<String, Collection<String>> properties, String lang, JsonTransformOptions outputOpts) {
        // Transform the results first
        Page<JsonElement> transformedResults = results.map(e -> JsonTransformer.transformJson(e, lang, outputOpts));
        
        if (properties == null || properties.isEmpty()) {
            return transformedResults;
        }

        // Filter the results based on the properties
        List<JsonElement> filteredList = transformedResults.getContent().stream()
                .filter(element -> matchesFilters(element, properties))
                .collect(java.util.stream.Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(filteredList, results.getPageable(), filteredList.size());
    }

    private boolean matchesFilters(JsonElement element, Map<String, Collection<String>> properties) {
        if (properties == null || properties.isEmpty()) {
            return true;
        }

        for (Map.Entry<String, Collection<String>> filter : properties.entrySet()) {
            String filterKey = filter.getKey();
            Collection<String> filterValues = filter.getValue();

            if (filterValues == null || filterValues.isEmpty()) {
                continue;
            }

            JsonElement fieldValue = getFieldValue(element, filterKey);
            if (fieldValue == null || fieldValue.isJsonNull()) {
                return false; // Field doesn't exist or is null, doesn't match filter
            }

            boolean matches = false;
            for (String filterValue : filterValues) {
                if (fieldValueMatches(fieldValue, filterValue)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                return false; // None of the filter values matched
            }
        }
        return true;
    }

    private JsonElement getFieldValue(JsonElement element, String fieldPath) {
        if (!element.isJsonObject()) {
            return null;
        }

        com.google.gson.JsonObject obj = element.getAsJsonObject();
        
        // Handle nested field paths like "ontologyId" or "is_obsolete"
        String[] pathParts = fieldPath.split("\\.");
        JsonElement current = obj;
        
        for (String part : pathParts) {
            if (!current.isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject().get(part);
            if (current == null) {
                return null;
            }
        }
        
        return current;
    }

    private boolean fieldValueMatches(JsonElement fieldValue, String filterValue) {
        if (fieldValue.isJsonPrimitive()) {
            String actualValue = fieldValue.getAsString().toLowerCase();
            return actualValue.contains(filterValue.toLowerCase());
        } else if (fieldValue.isJsonArray()) {
            // Check if any array element matches
            for (JsonElement arrayElement : fieldValue.getAsJsonArray()) {
                if (arrayElement.isJsonPrimitive() && 
                    arrayElement.getAsString().toLowerCase().contains(filterValue.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

}

