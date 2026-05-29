
package uk.ac.ebi.spot.ols.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import com.google.gson.JsonElement;

import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient;
import uk.ac.ebi.spot.ols.repository.search.SearchType;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformer;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.repository.helpers.DynamicFilterParser;
import uk.ac.ebi.spot.ols.repository.helpers.SearchFieldsParser;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;

import java.io.IOException;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

@Component
public class OntologyRepository {

    @Autowired
    OlsSearchClient searchClient;

    @Autowired
    OlsPostgresClient postgresClient;


    public OlsFacetedResultsPage<JsonElement> find(
            Pageable pageable, String lang, String search, String searchFields, String boostFields, boolean exactMatch, Map<String, Collection<String>> properties,
            JsonTransformOptions outputOpts) throws IOException {

        Validation.validateLang(lang);

        if(search != null && searchFields == null) {
            searchFields = LABEL.getText() + "^100 ontologyId^100 " + DEFINITION.getText();
        }

        OlsSearchQuery query = new OlsSearchQuery();

        query.setSearchText(search);
        query.setExactMatch(exactMatch);
        query.addFilter("type", List.of("ontology"), SearchType.WHOLE_FIELD);
        SearchFieldsParser.addSearchFieldsToQuery(query, searchFields);
        SearchFieldsParser.addBoostFieldsToQuery(query, boostFields);
        DynamicFilterParser.addDynamicFiltersToQuery(query, properties);

        return searchClient.searchPaginated(query, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public Map<String, List<V2Entity>> getGroupedByField(
            String fieldName, String lang, JsonTransformOptions outputOpts) throws IOException {

        Validation.validateLang(lang);

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("ontology"), SearchType.WHOLE_FIELD);
        query.addFacetField(fieldName);

        // Fetch all ontologies
        OlsFacetedResultsPage<JsonElement> page = searchClient.searchPaginated(
                query, org.springframework.data.domain.PageRequest.of(0, 1000));

        // Group ontologies by the field values
        Map<String, List<V2Entity>> grouped = new LinkedHashMap<>();

        // First, populate keys from facet counts to get proper ordering
        Map<String, Long> facetCounts = page.facetFieldToCounts.get(fieldName);
        if (facetCounts != null) {
            for (String key : facetCounts.keySet()) {
                grouped.put(key, new ArrayList<>());
            }
        }

        for (JsonElement element : page.getContent()) {
            JsonElement transformed = JsonTransformer.transformJson(element, lang, outputOpts);
            JsonObject obj = transformed.getAsJsonObject();

            if (obj.has(fieldName)) {
                JsonArray values;
                if (obj.get(fieldName).isJsonArray()) {
                    values = obj.getAsJsonArray(fieldName);
                } else {
                    values = new JsonArray();
                    values.add(obj.get(fieldName));
                }
                for (int i = 0; i < values.size(); i++) {
                    String value = values.get(i).getAsString();
                    grouped.computeIfAbsent(value, k -> new ArrayList<>()).add(new V2Entity(transformed));
                }
            }
        }

        return grouped;
    }

    public V2Entity getById(String ontologyId, String lang,
            JsonTransformOptions outputOpts) throws ResourceNotFoundException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        OlsSearchQuery query = new OlsSearchQuery();

        query.addFilter("type", List.of("ontology"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);

        JsonElement result = searchClient.getFirst(query);
        if (result == null) {
            return null;
        }

        return new V2Entity(
            JsonTransformer.transformJson(
                result,
                lang,
                outputOpts));
    }


}



