package uk.ac.ebi.spot.ols.controller.api.v2;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.ols.controller.api.exception.ResourceNotFoundException;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import com.google.gson.Gson;

import uk.ac.ebi.spot.ols.controller.api.v2.helpers.DynamicQueryHelper;
import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedAndFacetedResponse;
import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedResponse;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.ClassRepository;
import uk.ac.ebi.spot.ols.repository.PropertyRepository;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;
import uk.ac.ebi.spot.ols.service.EmbeddingServiceClient;
import uk.ac.ebi.spot.ols.repository.neo4j.OlsNeo4jClient;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

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
                @Parameter(name = "pageable",
                        description = "Specify the size of the result you want to get in the output",
                        example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
                @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                @RequestParam(value = "model", required = true) 
                @Parameter(name = "model",
                        description = "The embedding model name to use for vector search",
                        example = "text-embedding-3-small") String model,
                JsonTransformOptions outputOpts
        ) throws ResourceNotFoundException, IOException {

                // Convert List<Double> to float[]
                float[] vectorArray = new float[vector.size()];
                for (int i = 0; i < vector.size(); i++) {
                    vectorArray[i] = vector.get(i).floatValue();
                }

                return new ResponseEntity<>(
                        new V2PagedResponse<V2Entity>(
                        classRepository.searchByVector(model, vectorArray, pageable, lang, outputOpts).map(V2Entity::new)
                        ),
                        HttpStatus.OK
                );
        }

    @RequestMapping(path = "/classes/{class}/llm_similar", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getSimilarClasses(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
                    description = "Specify the size of the result you want to get in the output",
                    example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @PathVariable("class")
            @Parameter(name = "class",
                    description = "The IRI of the class, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_1000967") String iri,
        @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
        @RequestParam(value = "model", required = false, defaultValue = "text-embedding-3-small") 
        @Parameter(name = "model",
                description = "The embedding model name to use. Defaults to text-embedding-3-small.",
                example = "text-embedding-3-small") String model,
        JsonTransformOptions outputOpts
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

    @RequestMapping(path = "/properties/{property}/llm_similar", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getSimilarProperties(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
                    description = "Specify the size of the result you want to get in the output",
                    example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @PathVariable("property")
            @Parameter(name = "property",
                    description = "The IRI of the property, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_0000742") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "model", required = false, defaultValue = "text-embedding-3-small") 
            @Parameter(name = "model",
                    description = "The embedding model name to use. Defaults to text-embedding-3-small.",
                    example = "text-embedding-3-small") String model,
            JsonTransformOptions outputOpts
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

