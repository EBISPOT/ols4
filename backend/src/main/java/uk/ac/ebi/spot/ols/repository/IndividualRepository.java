
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

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import static uk.ac.ebi.ols.shared.DefinedFields.*;

@Component
public class IndividualRepository {

    @Autowired
    OlsSearchClient searchClient;

    @Autowired
    OlsPostgresClient postgresClient;


    public OlsFacetedResultsPage<JsonElement> find(
            Pageable pageable, String lang, String search, String searchFields, String boostFields, boolean exactMatch, Map<String,Collection<String>> properties, JsonTransformOptions outputOpts) throws IOException {

        Validation.validateLang(lang);

        if(search != null && searchFields == null) {
            searchFields = LABEL.getText()+"^100 definition";
        }

        OlsSearchQuery query = new OlsSearchQuery();
        query.setSearchText(search);
        query.setExactMatch(exactMatch);
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
        SearchFieldsParser.addSearchFieldsToQuery(query, searchFields);
        SearchFieldsParser.addBoostFieldsToQuery(query, boostFields);
        DynamicFilterParser.addDynamicFiltersToQuery(query, properties);

        return searchClient.searchPaginated(query, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public OlsFacetedResultsPage<JsonElement> findByOntologyId(
            String ontologyId, Pageable pageable, String lang, String search, String searchFields, String boostFields, boolean exactMatch, Map<String, Collection<String>> properties, JsonTransformOptions outputOpts) throws IOException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        if(search != null && searchFields == null) {
            searchFields = LABEL.getText() + "^100 definition";
        }

        OlsSearchQuery query = new OlsSearchQuery();
        query.setSearchText(search);
        query.setExactMatch(exactMatch);
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);
        SearchFieldsParser.addSearchFieldsToQuery(query, searchFields);
        SearchFieldsParser.addBoostFieldsToQuery(query, boostFields);
        DynamicFilterParser.addDynamicFiltersToQuery(query, properties);

        return searchClient.searchPaginated(query, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;

    }

    public V2Entity getByOntologyIdAndIri(String ontologyId, String iri, String lang, JsonTransformOptions outputOpts) throws ResourceNotFoundException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
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

    public OlsFacetedResultsPage<JsonElement> getIndividualsOfClass(
            String ontologyId, String classIri, Pageable pageable, boolean includeObsoleteEntities,
            String lang, JsonTransformOptions outputOpts) throws IOException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);
        query.addFilter("http__//www.w3.org/1999/02/22-rdf-syntax-ns#type", List.of(classIri), SearchType.WHOLE_FIELD);
        if (!includeObsoleteEntities) {
            query.addFilter(IS_OBSOLETE.getText(), List.of("false"), SearchType.WHOLE_FIELD);
        }

        return searchClient.searchPaginated(query, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;

    }



}
