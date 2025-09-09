
package uk.ac.ebi.spot.ols.repository;

import com.google.gson.JsonElement;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Component;

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

import static com.google.common.base.Strings.isNullOrEmpty;
import static uk.ac.ebi.ols.shared.DefinedFields.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class ClassRepository {

    @Autowired
    OlsSolrClient solrClient;

    @Autowired
    OlsNeo4jClient neo4jClient;

    public OlsFacetedResultsPage<JsonElement> find(
            Pageable pageable, String lang, String search, String searchFields, String boostFields, boolean exactMatch, Map<String,Collection<String>> properties,
            JsonTransformOptions outputOpts

            ) throws IOException {

        Validation.validateLang(lang);

        if(search != null && searchFields == null) {
            searchFields = LABEL.getText()+"^100 definition";
        }

        OlsSolrQuery query = new OlsSolrQuery();
        query.setSearchText(search);
        query.setExactMatch(exactMatch);
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        SearchFieldsParser.addSearchFieldsToQuery(query, searchFields);
        SearchFieldsParser.addBoostFieldsToQuery(query, boostFields);
        DynamicFilterParser.addDynamicFiltersToQuery(query, properties);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public OlsFacetedResultsPage<JsonElement> findByOntologyId(
            String ontologyId, Pageable pageable, String lang, String search, String searchFields, String boostFields, boolean exactMatch, Map<String, Collection<String>> properties, JsonTransformOptions outputOpts) throws IOException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        if(search != null && searchFields == null) {
            searchFields = LABEL.getText()+"^100 definition";
        }

        OlsSolrQuery query = new OlsSolrQuery();

        query.setSearchText(search);
        query.setExactMatch(exactMatch);
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);
        SearchFieldsParser.addSearchFieldsToQuery(query, searchFields);
        SearchFieldsParser.addBoostFieldsToQuery(query, boostFields);
        DynamicFilterParser.addDynamicFiltersToQuery(query, properties);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public JsonElement getByOntologyIdAndIri(String ontologyId, String iri, String lang, JsonTransformOptions outputOpts) throws ResourceNotFoundException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();

        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        return JsonTransformer.transformJson(
                solrClient.getFirst(query),
                lang,
                outputOpts);
    }

    public Page<JsonElement> getChildrenByOntologyId(String ontologyId, Pageable pageable, String iri, boolean includeObsolete, String search, String lang, JsonTransformOptions outputOpts) {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        String id = ontologyId + "+class+" + iri;

        Map<String, String> nodeProps = includeObsolete ? Map.of() : Map.of("isObsolete", "false");

        Page<JsonElement> result = isNullOrEmpty(search) ? this.neo4jClient.traverseIncomingEdges(
                "OntologyClass", id, Arrays.asList(DIRECT_PARENT.getText()), Map.of(), nodeProps, pageable) :
                this.neo4jClient.traverseIncomingEdges("OntologyClass",
                id, Arrays.asList(DIRECT_PARENT.getText()), Map.of(), nodeProps, pageable, search);

        return  result
                    .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                    ;
    }

    public Page<JsonElement> getAncestorsByOntologyId(String ontologyId, Pageable pageable, String iri, boolean includeObsolete, String lang, JsonTransformOptions outputOpts) {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        String id = ontologyId + "+class+" + iri;

        Map<String, String> nodeProps = includeObsolete ? Map.of() : Map.of("isObsolete", "false");

        return this.neo4jClient.recursivelyTraverseOutgoingEdges("OntologyClass", id,
                        Arrays.asList(DIRECT_PARENT.getText()), Map.of(), nodeProps, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public Page<JsonElement> getDescendantsByOntologyId(String ontologyId, Pageable pageable, String iri, boolean includeObsolete, String lang, JsonTransformOptions outputOpts) {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        String id = ontologyId + "+class+" + iri;

        Map<String, String> nodeProps = includeObsolete ? Map.of() : Map.of("isObsolete", "false");

        return this.neo4jClient.recursivelyTraverseIncomingEdges("OntologyClass", id,
                        Arrays.asList(DIRECT_PARENT.getText()), Map.of(), nodeProps, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }
    public Page<JsonElement> getHierarchicalDescendantsByOntologyId(String ontologyId, Pageable pageable, String iri, boolean includeObsolete, String lang, JsonTransformOptions outputOpts) {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        String id = ontologyId + "+class+" + iri;

        Map<String, String> nodeProps = includeObsolete ? Map.of() : Map.of("isObsolete", "false");

        return this.neo4jClient.recursivelyTraverseIncomingEdges("OntologyClass", id,
                        Arrays.asList(HIERARCHICAL_PARENT.getText()), Map.of(), nodeProps, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public Page<JsonElement> getHierarchicalChildrenByOntologyId(String ontologyId, Pageable pageable, String iri, boolean includeObsolete, String lang, JsonTransformOptions outputOpts) {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        String id = ontologyId + "+class+" + iri;

        Map<String, String> nodeProps = includeObsolete ? Map.of() : Map.of("isObsolete", "false");

        return this.neo4jClient.traverseIncomingEdges("OntologyClass", id, Arrays.asList(HIERARCHICAL_PARENT.getText()),
                        Map.of(), nodeProps, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public Page<JsonElement> getHierarchicalAncestorsByOntologyId(String ontologyId, Pageable pageable, String iri, boolean includeObsolete, String lang, JsonTransformOptions outputOpts) {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        String id = ontologyId + "+class+" + iri;

        Map<String, String> nodeProps = includeObsolete ? Map.of() : Map.of("isObsolete", "false");

        return this.neo4jClient.recursivelyTraverseOutgoingEdges("OntologyClass", id,
                        Arrays.asList(HIERARCHICAL_PARENT.getText()), Map.of(), nodeProps, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public Page<JsonElement> getIndividualAncestorsByOntologyId(String ontologyId, Pageable pageable, String iri, boolean includeObsolete, String lang, JsonTransformOptions outputOpts) {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        String id = ontologyId + "+individual+" + iri;

        Map<String, String> nodeProps = includeObsolete ? Map.of() : Map.of("isObsolete", "false");

        return this.neo4jClient.recursivelyTraverseOutgoingEdges("OntologyEntity", id,
                        Arrays.asList(DIRECT_PARENT.getText()), Map.of(), nodeProps, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }


    public Page<JsonElement> getSimilarByOntologyId(String ontologyId, Pageable pageable, String iri, boolean includeObsolete, String lang, JsonTransformOptions outputOpts) {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        return this.neo4jClient.getSimilar("OntologyClass", iri, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public double getSimilarityByOntologyId(String ontologyId, String iri, String iri2) {

        Validation.validateOntologyId(ontologyId);

        return this.neo4jClient.getSimilarity("OntologyClass", iri, iri2);
    }

    public List<Double> getEmbeddingVectorByOntologyId(String ontologyId, String iri) {

        Validation.validateOntologyId(ontologyId);

        return this.neo4jClient.getEmbeddingVector("OntologyClass", iri);
    }

    public Page<JsonElement> searchByVector(List<Double> vector, Pageable pageable, String lang, JsonTransformOptions outputOpts) {
        Validation.validateVector(vector);
        Validation.validateLang(lang);

        return this.neo4jClient.searchByVector("OntologyClass", vector, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }
}
