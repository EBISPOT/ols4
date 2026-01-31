package uk.ac.ebi.spot.ols.controller.mcp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;

import uk.ac.ebi.spot.ols.model.mcp.McpPage;
import uk.ac.ebi.spot.ols.model.mcp.McpSearchResult;
import uk.ac.ebi.spot.ols.repository.neo4j.OlsNeo4jClient;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;
import uk.ac.ebi.spot.ols.service.EmbeddingServiceClient;

@Service
public class McpEmbeddingService {

    @Autowired
    EmbeddingServiceClient embeddingServiceClient;
    
    @Autowired
    OlsNeo4jClient neo4jClient;

    Gson gson = new Gson();

    @Tool(description = "List available embedding models for LLM-based semantic search. Call this first to discover which models can be used with llmSearch and llmSearchClasses. Returns models with their names and whether they support live embedding.")
    List<Map<String, Object>> listEmbeddingModels() {
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
        
        return result;
    }

    @Tool(description = "Search OLS entities using semantic/embedding-based similarity. Uses vector embeddings to find semantically similar entities, which can find related concepts even when exact terms don't match. IMPORTANT: Call listEmbeddingModels first - only models with can_embed=true can be used for text search.")
    McpPage<McpSearchResult> searchWithEmbeddingModel(
        @ToolParam(description = "The natural language query to search for semantically similar entities") String query,
        @ToolParam(description = "The embedding model to use. Must have can_embed=true from listEmbeddingModels.") String model,
        @ToolParam(required=false, description = "Optional ontology ID to filter results") String ontologyId,
        @ToolParam(required=false) Integer pageNum,
        @ToolParam(required=false) Integer pageSize
    ) throws IOException {
        var pageable = PageRequest.of(
            pageNum != null ? pageNum : 0,
            pageSize != null ? pageSize : 20
        );

        JsonTransformOptions outputOpts = new JsonTransformOptions();
        outputOpts.resolveReferences = true;
        outputOpts.manchesterSyntax = true;

        // Embed the query text using the embedding service
        float[] vectorArray = embeddingServiceClient.embedText(model, query);
        
        // Convert float[] to List<Double> for Neo4j
        List<Double> vectorList = new ArrayList<>(vectorArray.length);
        for (float f : vectorArray) {
            vectorList.add((double) f);
        }

        // Search all entity types using Neo4j vector search
        org.springframework.data.domain.Page<com.google.gson.JsonElement> results;
        if (ontologyId != null && !ontologyId.isEmpty()) {
            results = neo4jClient.searchByVectorInOntology("OntologyEntity", vectorList, pageable, model, ontologyId, true);
        } else {
            results = neo4jClient.searchByVector("OntologyEntity", vectorList, pageable, model);
        }

        // Transform and return results
        var transformedResults = results.map(e -> 
            uk.ac.ebi.spot.ols.repository.transforms.JsonTransformer.transformJson(e, "en", outputOpts));

        return new McpPage<>(
            transformedResults.getContent().stream().map(McpSearchResult::fromJson).toList(),
            transformedResults.getNumber(),
            transformedResults.getSize(),
            transformedResults.getTotalElements(),
            transformedResults.getTotalPages()
        );
    }

}
