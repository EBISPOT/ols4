package uk.ac.ebi.spot.ols.controller.api.v2;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.ols.controller.api.exception.ResourceNotFoundException;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import com.google.gson.Gson;

import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedResponse;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.ClassRepository;
import uk.ac.ebi.spot.ols.repository.PropertyRepository;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;
import uk.ac.ebi.spot.ols.service.EmbeddingServiceClient;
import uk.ac.ebi.spot.ols.repository.neo4j.OlsNeo4jClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

@Tag(
        name = "V2 LLM Controller"
)
@RestController
@RequestMapping("/api/v2")
public class V2LLMController {

    Gson gson = new Gson();

    @Autowired
    ClassRepository classRepository;

    @Autowired
    PropertyRepository propertyRepository;

    @Autowired
    EmbeddingServiceClient embeddingServiceClient;
    
    @Autowired
    OlsNeo4jClient neo4jClient;

    @RequestMapping(path = "/llm_models", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    @Parameter(name = "llm_models",
            description = "Returns a list of embedding models, indicating which can be used for embedding (via the embedding service) and which only have pre-computed embeddings stored in Solr")
    public HttpEntity<List<Map<String, Object>>> getLLMModels() throws IOException {
        // Get models from embedding service (for determining which can do live embedding)
        List<String> embeddingServiceModels = embeddingServiceClient.getAvailableModels();
        Set<String> canEmbedModels = new HashSet<>(embeddingServiceModels);
        
        // Get models from Neo4j (only these are usable for similarity search)
        List<String> neo4jModels = neo4jClient.getEmbeddingModelsInNeo4j();
        
        // Build response - only include models that exist in Neo4j
        List<Map<String, Object>> result = new ArrayList<>();
        for (String model : neo4jModels) {
            Map<String, Object> modelInfo = new HashMap<>();
            modelInfo.put("model", model);
            modelInfo.put("can_embed", canEmbedModels.contains(model));
            result.add(modelInfo);
        }
        
        // Sort by model name for consistent output
        result.sort((a, b) -> ((String)a.get("model")).compareTo((String)b.get("model")));
        
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @RequestMapping(path = "/classes/llm_embedding", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.POST)
    public HttpEntity<V2PagedResponse<V2Entity>> searchClassesByVector(
                @RequestBody List<Double> vector,
                @PageableDefault(size = 20, page = 0)
                @ParameterObject Pageable pageable,
                @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                @RequestParam(value = "model", required = true) 
                @Parameter(name = "model",
                        description = "The embedding model name to use for vector search",
                        example = "text-embedding-3-small") String model,
                @RequestParam(value = "ontologyId", required = false)
                @Parameter(name = "ontologyId",
                        description = "Optional ontology ID to filter results. If specified only returns classes defined in this ontology (not imported classes).",
                        example = "efo") String ontologyId,
                @RequestParam(value = "includeCurations", required = false, defaultValue = "true")
                @Parameter(name = "includeCurations",
                        description = "If true (default), include curated text-to-term mapping embeddings in the search. If false, only search label embeddings.") boolean includeCurations,
                @ParameterObject JsonTransformOptions outputOpts
        ) throws ResourceNotFoundException, IOException {

                // Convert List<Double> to float[]
                float[] vectorArray = new float[vector.size()];
                for (int i = 0; i < vector.size(); i++) {
                    vectorArray[i] = vector.get(i).floatValue();
                }

                return new ResponseEntity<>(
                        new V2PagedResponse<V2Entity>(
                        classRepository.searchByVector(model, vectorArray, pageable, lang, ontologyId, outputOpts, includeCurations).map(V2Entity::new)
                        ),
                        HttpStatus.OK
                );
        }

    @RequestMapping(path = "/ontologies/{onto}/classes/llm_embedding", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.POST)
    public HttpEntity<V2PagedResponse<V2Entity>> searchClassesByVectorInOntology(
                @PathVariable("onto")
                @Parameter(name = "onto",
                        description = "The ontology ID to filter results.",
                        example = "efo") String ontologyId,
                @RequestBody List<Double> vector,
                @PageableDefault(size = 20, page = 0)
                @ParameterObject Pageable pageable,
                @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                @RequestParam(value = "model", required = true) 
                @Parameter(name = "model",
                        description = "The embedding model name to use for vector search",
                        example = "text-embedding-3-small") String model,
                @RequestParam(value = "isDefiningOntology", required = false, defaultValue = "false")
                @Parameter(name = "isDefiningOntology",
                        description = "If true, only return classes defined in this ontology. If false (default), include imported classes too.",
                        example = "false") boolean isDefiningOntology,
                @RequestParam(value = "includeCurations", required = false, defaultValue = "true")
                @Parameter(name = "includeCurations",
                        description = "If true (default), include curated text-to-term mapping embeddings in the search. If false, only search label embeddings.") boolean includeCurations,
                @ParameterObject JsonTransformOptions outputOpts
        ) throws ResourceNotFoundException, IOException {

                // Convert List<Double> to float[]
                float[] vectorArray = new float[vector.size()];
                for (int i = 0; i < vector.size(); i++) {
                    vectorArray[i] = vector.get(i).floatValue();
                }

                return new ResponseEntity<>(
                        new V2PagedResponse<V2Entity>(
                        classRepository.searchByVectorInOntology(ontologyId, model, vectorArray, pageable, lang, isDefiningOntology, outputOpts, includeCurations).map(V2Entity::new)
                        ),
                        HttpStatus.OK
                );
        }

    @RequestMapping(path = "/entities/llm_search", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> searchEntitiesByText(
                @RequestParam(value = "q", required = true)
                @Parameter(name = "q",
                        description = "The text query to search for using semantic similarity",
                        example = "heart disease") String query,
                @PageableDefault(size = 20, page = 0)
                @ParameterObject Pageable pageable,
                @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                @RequestParam(value = "model", required = true) 
                @Parameter(name = "model",
                        description = "The embedding model name to use for vector search",
                        example = "text-embedding-3-small") String model,
                @RequestParam(value = "ontologyId", required = false)
                @Parameter(name = "ontologyId",
                        description = "Optional ontology ID to filter results. If specified only returns entities defined in this ontology (not imported).",
                        example = "efo") String ontologyId,
                @RequestParam(value = "includeCurations", required = false, defaultValue = "true")
                @Parameter(name = "includeCurations",
                        description = "If true (default), include curated text-to-term mapping embeddings in the search. If false, only search label embeddings.") boolean includeCurations,
                @ParameterObject JsonTransformOptions outputOpts
        ) throws ResourceNotFoundException, IOException {

                // Embed the query text using the embedding service
                float[] vectorArray = embeddingServiceClient.embedText(model, query);
                
                // Convert float[] to List<Double> for Neo4j
                List<Double> vectorList = new java.util.ArrayList<>(vectorArray.length);
                for (float f : vectorArray) {
                    vectorList.add((double) f);
                }

                // Search all entity types using OntologyEntity (no type filtering)
                org.springframework.data.domain.Page<com.google.gson.JsonElement> results;
                if (ontologyId != null && !ontologyId.isEmpty()) {
                    results = neo4jClient.searchByVectorInOntology("OntologyEntity", vectorList, pageable, model, ontologyId, true, includeCurations);
                } else {
                    results = neo4jClient.searchByVector("OntologyEntity", vectorList, pageable, model, includeCurations);
                }

                return new ResponseEntity<>(
                        new V2PagedResponse<V2Entity>(
                        results.map(e -> uk.ac.ebi.spot.ols.repository.transforms.JsonTransformer.transformJson(e, lang, outputOpts)).map(V2Entity::new)
                        ),
                        HttpStatus.OK
                );
        }

    @RequestMapping(path = "/classes/llm_search", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> searchClassesByText(
                @RequestParam(value = "q", required = true)
                @Parameter(name = "q",
                        description = "The text query to search for using semantic similarity",
                        example = "heart disease") String query,
                @PageableDefault(size = 20, page = 0)
                @ParameterObject Pageable pageable,
                @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                @RequestParam(value = "model", required = true) 
                @Parameter(name = "model",
                        description = "The embedding model name to use for vector search",
                        example = "text-embedding-3-small") String model,
                @RequestParam(value = "ontologyId", required = false)
                @Parameter(name = "ontologyId",
                        description = "Optional ontology ID to filter results. If specified only returns classes defined in this ontology (not imported classes).",
                        example = "efo") String ontologyId,
                @RequestParam(value = "includeCurations", required = false, defaultValue = "true")
                @Parameter(name = "includeCurations",
                        description = "If true (default), include curated text-to-term mapping embeddings in the search. If false, only search label embeddings.") boolean includeCurations,
                @ParameterObject JsonTransformOptions outputOpts
        ) throws ResourceNotFoundException, IOException {

                // Embed the query text using the embedding service
                float[] vectorArray = embeddingServiceClient.embedText(model, query);

                return new ResponseEntity<>(
                        new V2PagedResponse<V2Entity>(
                        classRepository.searchByVector(model, vectorArray, pageable, lang, ontologyId, outputOpts, includeCurations).map(V2Entity::new)
                        ),
                        HttpStatus.OK
                );
        }

    @RequestMapping(path = "/ontologies/{onto}/classes/llm_search", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> searchClassesByTextInOntology(
                @PathVariable("onto")
                @Parameter(name = "onto",
                        description = "The ontology ID to filter results.",
                        example = "efo") String ontologyId,
                @RequestParam(value = "q", required = true)
                @Parameter(name = "q",
                        description = "The text query to search for using semantic similarity",
                        example = "heart disease") String query,
                @PageableDefault(size = 20, page = 0)
                @ParameterObject Pageable pageable,
                @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                @RequestParam(value = "model", required = true) 
                @Parameter(name = "model",
                        description = "The embedding model name to use for vector search",
                        example = "text-embedding-3-small") String model,
                @RequestParam(value = "isDefiningOntology", required = false, defaultValue = "false")
                @Parameter(name = "isDefiningOntology",
                        description = "If true, only return classes defined in this ontology. If false (default), include imported classes too.",
                        example = "false") boolean isDefiningOntology,
                @RequestParam(value = "includeCurations", required = false, defaultValue = "true")
                @Parameter(name = "includeCurations",
                        description = "If true (default), include curated text-to-term mapping embeddings in the search. If false, only search label embeddings.") boolean includeCurations,
                @ParameterObject JsonTransformOptions outputOpts
        ) throws ResourceNotFoundException, IOException {

                // Embed the query text using the embedding service
                float[] vectorArray = embeddingServiceClient.embedText(model, query);

                return new ResponseEntity<>(
                        new V2PagedResponse<V2Entity>(
                        classRepository.searchByVectorInOntology(ontologyId, model, vectorArray, pageable, lang, isDefiningOntology, outputOpts, includeCurations).map(V2Entity::new)
                        ),
                        HttpStatus.OK
                );
        }

    @RequestMapping(path = "/classes/{class}/llm_similar", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getSimilarClasses(
            @PageableDefault(size = 20, page = 0)
            @ParameterObject Pageable pageable,
            @PathVariable("class")
            @Parameter(name = "class",
                    description = "The IRI of the class, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_1000967") String iri,
        @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
        @RequestParam(value = "model", required = false, defaultValue = "text-embedding-3-small") 
        @Parameter(name = "model",
                description = "The embedding model name to use. Defaults to text-embedding-3-small.",
                example = "text-embedding-3-small") String model,
        @ParameterObject JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException, IOException {

        iri = UriUtils.decode(iri, "UTF-8");

        return new ResponseEntity<>(
            new V2PagedResponse<V2Entity>(
                classRepository.getSimilar(pageable, iri, lang, outputOpts, model).map(V2Entity::new)
            ),
            HttpStatus.OK
        );
    }

    @RequestMapping(path = "/classes/{class}/llm_embedding", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<String> getClassEmbedding(
            @PathVariable("class")
            @Parameter(name = "class",
                    description = "The IRI of the class, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_1000967") String iri,
            @RequestParam(value = "model", required = false, defaultValue = "text-embedding-3-small") 
            @Parameter(name = "model",
                    description = "The embedding model name to use. Defaults to text-embedding-3-small.",
                    example = "text-embedding-3-small") String model
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        return new ResponseEntity<>(
                gson.toJson( classRepository.getEmbeddingVector(iri, model) ),
                HttpStatus.OK
        );
    }

    @RequestMapping(path = "/classes/{class}/llm_similarity/{otherclass}", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<String> getClassSimilarity(
            @PathVariable("class")
            @Parameter(name = "class",
                    description = "The IRI of the class, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_1000967") String iri,
            @PathVariable("otherclass")
            @Parameter(name = "otherclass",
                    description = "The IRI of the other class, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_1000967") String iri2,
            @RequestParam(value = "model", required = false, defaultValue = "text-embedding-3-small") 
            @Parameter(name = "model",
                    description = "The embedding model name to use. Defaults to text-embedding-3-small.",
                    example = "text-embedding-3-small") String model
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");
        iri2 = UriUtils.decode(iri2, "UTF-8");

        return new ResponseEntity<>(
                Double.toString( classRepository.getSimilarity(iri, iri2, model) ),
                HttpStatus.OK
        );
    }

    @RequestMapping(path = "/properties/llm_search", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> searchPropertiesByText(
                @RequestParam(value = "q", required = true)
                @Parameter(name = "q",
                        description = "The text query to search for using semantic similarity",
                        example = "part of") String query,
                @PageableDefault(size = 20, page = 0)
                @ParameterObject Pageable pageable,
                @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                @RequestParam(value = "model", required = true) 
                @Parameter(name = "model",
                        description = "The embedding model name to use for vector search",
                        example = "text-embedding-3-small") String model,
                @RequestParam(value = "ontologyId", required = false)
                @Parameter(name = "ontologyId",
                        description = "Optional ontology ID to filter results. If specified only returns properties defined in this ontology.",
                        example = "efo") String ontologyId,
                @RequestParam(value = "includeCurations", required = false, defaultValue = "true")
                @Parameter(name = "includeCurations",
                        description = "If true (default), include curated text-to-term mapping embeddings in the search. If false, only search label embeddings.") boolean includeCurations,
                @ParameterObject JsonTransformOptions outputOpts
        ) throws ResourceNotFoundException, IOException {

                // Embed the query text using the embedding service
                float[] vectorArray = embeddingServiceClient.embedText(model, query);
                
                // Convert float[] to List<Double> for Neo4j
                List<Double> vectorList = new java.util.ArrayList<>(vectorArray.length);
                for (float f : vectorArray) {
                    vectorList.add((double) f);
                }

                // Search properties using OntologyProperty type
                org.springframework.data.domain.Page<com.google.gson.JsonElement> results;
                if (ontologyId != null && !ontologyId.isEmpty()) {
                    results = neo4jClient.searchByVectorInOntology("OntologyProperty", vectorList, pageable, model, ontologyId, true, includeCurations);
                } else {
                    results = neo4jClient.searchByVector("OntologyProperty", vectorList, pageable, model, includeCurations);
                }

                return new ResponseEntity<>(
                        new V2PagedResponse<V2Entity>(
                        results.map(e -> uk.ac.ebi.spot.ols.repository.transforms.JsonTransformer.transformJson(e, lang, outputOpts)).map(V2Entity::new)
                        ),
                        HttpStatus.OK
                );
        }

    @RequestMapping(path = "/individuals/llm_search", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> searchIndividualsByText(
                @RequestParam(value = "q", required = true)
                @Parameter(name = "q",
                        description = "The text query to search for using semantic similarity",
                        example = "human") String query,
                @PageableDefault(size = 20, page = 0)
                @ParameterObject Pageable pageable,
                @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                @RequestParam(value = "model", required = true) 
                @Parameter(name = "model",
                        description = "The embedding model name to use for vector search",
                        example = "text-embedding-3-small") String model,
                @RequestParam(value = "ontologyId", required = false)
                @Parameter(name = "ontologyId",
                        description = "Optional ontology ID to filter results. If specified only returns individuals defined in this ontology.",
                        example = "efo") String ontologyId,
                @RequestParam(value = "includeCurations", required = false, defaultValue = "true")
                @Parameter(name = "includeCurations",
                        description = "If true (default), include curated text-to-term mapping embeddings in the search. If false, only search label embeddings.") boolean includeCurations,
                @ParameterObject JsonTransformOptions outputOpts
        ) throws ResourceNotFoundException, IOException {

                // Embed the query text using the embedding service
                float[] vectorArray = embeddingServiceClient.embedText(model, query);
                
                // Convert float[] to List<Double> for Neo4j
                List<Double> vectorList = new java.util.ArrayList<>(vectorArray.length);
                for (float f : vectorArray) {
                    vectorList.add((double) f);
                }

                // Search individuals using OntologyIndividual type
                org.springframework.data.domain.Page<com.google.gson.JsonElement> results;
                if (ontologyId != null && !ontologyId.isEmpty()) {
                    results = neo4jClient.searchByVectorInOntology("OntologyIndividual", vectorList, pageable, model, ontologyId, true, includeCurations);
                } else {
                    results = neo4jClient.searchByVector("OntologyIndividual", vectorList, pageable, model, includeCurations);
                }

                return new ResponseEntity<>(
                        new V2PagedResponse<V2Entity>(
                        results.map(e -> uk.ac.ebi.spot.ols.repository.transforms.JsonTransformer.transformJson(e, lang, outputOpts)).map(V2Entity::new)
                        ),
                        HttpStatus.OK
                );
        }

    @RequestMapping(path = "/properties/{property}/llm_similar", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getSimilarProperties(
            @PageableDefault(size = 20, page = 0)
            @ParameterObject Pageable pageable,
            @PathVariable("property")
            @Parameter(name = "property",
                    description = "The IRI of the property, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_0000742") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "model", required = false, defaultValue = "text-embedding-3-small") 
            @Parameter(name = "model",
                    description = "The embedding model name to use. Defaults to text-embedding-3-small.",
                    example = "text-embedding-3-small") String model,
            @ParameterObject JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        return new ResponseEntity<>(
                new V2PagedResponse<V2Entity>(
                        propertyRepository.getSimilar(pageable, iri, lang, outputOpts, model)
                        .map(V2Entity::new)
                ),
                HttpStatus.OK
        );
    }
}

