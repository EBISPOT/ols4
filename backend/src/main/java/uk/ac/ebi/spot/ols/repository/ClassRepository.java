
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

        JsonElement result = solrClient.getFirst(query);
        if (result == null) {
            return null;
        }

        return JsonTransformer.transformJson(
                result,
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


    public Page<JsonElement> getSimilar(Pageable pageable, String iri, String lang, JsonTransformOptions outputOpts, String modelName) {

        Validation.validateLang(lang);

        if (modelName == null || modelName.isEmpty()) {
            modelName = "text-embedding-3-small"; // Default model
        }

        return this.neo4jClient.getSimilar("OntologyClass", iri, pageable, modelName)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    public double getSimilarity(String iri, String iri2, String modelName) {

        if (modelName == null || modelName.isEmpty()) {
            modelName = "text-embedding-3-small"; // Default model
        }

        return this.neo4jClient.getSimilarity("OntologyClass", iri, iri2, modelName);
    }

    public List<Double> getEmbeddingVector(String iri, String modelName) {

        if (modelName == null || modelName.isEmpty()) {
            modelName = "text-embedding-3-small"; // Default model
        }

        return this.neo4jClient.getEmbeddingVector("OntologyClass", iri, modelName);
    }

    /**
     * Search by vector globally (all ontologies, defining classes only) or filtered by ontology.
     * When ontologyId is provided, only returns classes defined in that ontology.
     */
    public Page<JsonElement> searchByVector(String modelName, float[] vector, Pageable pageable, String lang, String ontologyId, JsonTransformOptions outputOpts) {
        if (ontologyId != null) {
            // Delegate to ontology-specific search with isDefiningOntology=true
            return searchByVectorInOntology(ontologyId, modelName, vector, pageable, lang, true, outputOpts);
        }
        
        Validation.validateLang(lang);

        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Vector cannot be null or empty");
        }
        
        if (modelName == null || modelName.isEmpty()) {
            modelName = "text-embedding-3-small"; // Default model
        }
        
        // Convert float[] to List<Double> for Neo4j
        List<Double> vectorList = new java.util.ArrayList<>(vector.length);
        for (float f : vector) {
            vectorList.add((double) f);
        }
        
        return this.neo4jClient.searchByVector("OntologyClass", vectorList, pageable, modelName)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    /**
     * Search by vector within a specific ontology.
     * If isDefiningOntology is true, only returns classes defined in this ontology.
     * If isDefiningOntology is false, includes imported classes by matching IRI.
     */
    public Page<JsonElement> searchByVectorInOntology(String ontologyId, String modelName, float[] vector, Pageable pageable, String lang, boolean isDefiningOntology, JsonTransformOptions outputOpts) {
        Validation.validateLang(lang);
        Validation.validateOntologyId(ontologyId);

        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Vector cannot be null or empty");
        }
        
        if (modelName == null || modelName.isEmpty()) {
            modelName = "text-embedding-3-small"; // Default model
        }
        
        // Convert float[] to List<Double> for Neo4j
        List<Double> vectorList = new java.util.ArrayList<>(vector.length);
        for (float f : vector) {
            vectorList.add((double) f);
        }
        
        return this.neo4jClient.searchByVectorInOntology("OntologyClass", vectorList, pageable, modelName, ontologyId, isDefiningOntology)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts))
                ;
    }

    /**
     * Get classes that have a relatedTo relationship pointing to the specified class (i.e., relatedFrom).
     * This is the inverse of relatedTo - it returns classes that reference the given class in their definitions.
     *
     * @param ontologyId The ontology ID
     * @param iri The IRI of the class to find related-from classes for
     * @param pageable Pagination parameters
     * @param lang Language for localization
     * @param outputOpts JSON transformation options
     * @return Paginated list of classes that reference this class
     * @throws IOException If there's an error querying
     */
    public OlsFacetedResultsPage<JsonElement> getRelatedFrom(
            String ontologyId, String iri, Pageable pageable, String lang, JsonTransformOptions outputOpts) throws IOException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        // Query Solr for classes that have this class in their relatedTo field
        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);
        query.addFilter("relatedTo", List.of(iri), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(e -> JsonTransformer.transformJson(e, lang, outputOpts));
    }
}
