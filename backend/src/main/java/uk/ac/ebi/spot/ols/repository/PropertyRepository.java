
package uk.ac.ebi.spot.ols.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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

import static uk.ac.ebi.ols.shared.DefinedFields.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class PropertyRepository {

    @Autowired
    OlsSearchClient searchClient;

    @Autowired
    OlsPostgresClient postgresClient;


    public OlsFacetedResultsPage<JsonElement> find(
            Pageable pageable, String lang, String search, String searchFields, String boostFields, boolean exactMatch, Map<String,Collection<String>> properties, JsonTransformOptions outputOpts) throws IOException {

        Validation.validateLang(lang);

        if(search != null && searchFields == null) {
            searchFields = LABEL.getText()+"^100 " + DEFINITION.getText();
        }

        OlsSearchQuery query = new OlsSearchQuery();
        query.setSearchText(search);
        query.setExactMatch(exactMatch);
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        SearchFieldsParser.addSearchFieldsToQuery(query, searchFields);
        SearchFieldsParser.addBoostFieldsToQuery(query, boostFields);
        DynamicFilterParser.addDynamicFiltersToQuery(query, properties);

        return searchClient.searchPaginated(query, pageable)
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

        OlsSearchQuery query = new OlsSearchQuery();
        query.setExactMatch(exactMatch);
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);
        SearchFieldsParser.addSearchFieldsToQuery(query, searchFields);
        SearchFieldsParser.addBoostFieldsToQuery(query, boostFields);
        DynamicFilterParser.addDynamicFiltersToQuery(query, properties);
        query.setSearchText(search);

        return searchClient.searchPaginated(query, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public V2Entity getByOntologyIdAndIri(String ontologyId, String iri, String lang, JsonTransformOptions outputOpts) throws ResourceNotFoundException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

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

    public Page<JsonElement> getChildrenByOntologyId(String ontologyId, Pageable pageable, String iri, String lang, JsonTransformOptions outputOpts) {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        String id = ontologyId + "+property+" + iri;

        return this.postgresClient.getDirectChildren(id, Map.of(), pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public Page<JsonElement> getAncestorsByOntologyId(String ontologyId, Pageable pageable, String iri, String lang, JsonTransformOptions outputOpts) {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        String id = ontologyId + "+property+" + iri;

        return this.postgresClient.getAncestors(id, Map.of(), pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public Page<JsonElement> getSimilar(Pageable pageable, String iri, String lang, JsonTransformOptions outputOpts, String modelName) {

        Validation.validateLang(lang);

        if (modelName == null || modelName.isEmpty()) {
            modelName = "text-embedding-3-small"; // Default model
        }

        return this.postgresClient.getSimilar("OntologyProperty", iri, pageable, modelName)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

}

