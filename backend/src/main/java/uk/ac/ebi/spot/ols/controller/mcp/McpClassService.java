
package uk.ac.ebi.spot.ols.controller.mcp;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import uk.ac.ebi.spot.ols.model.mcp.McpClass;
import uk.ac.ebi.spot.ols.model.mcp.McpPage;
import uk.ac.ebi.spot.ols.repository.ClassRepository;
import uk.ac.ebi.spot.ols.repository.EntityRepository;
import uk.ac.ebi.spot.ols.repository.neo4j.OlsNeo4jClient;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;
import uk.ac.ebi.spot.ols.service.EmbeddingServiceClient;

@Service
public class McpClassService {

    @Autowired
    EntityRepository entityRepository;

    @Autowired
    ClassRepository classRepository;

    @Autowired
    EmbeddingServiceClient embeddingServiceClient;
    
    @Autowired
    OlsNeo4jClient neo4jClient;

    @Tool(description = "Search all classes in OLS for a query string")
    McpPage<McpClass> searchClasses(
        String query,
        @ToolParam(required=false) String ontologyId,
        @ToolParam(required=false) Integer pageNum,
        @ToolParam(required=false) Integer pageSize,
        @ToolParam(required=false) String lang,
        @ToolParam(required=false, description = "Whether to include obsolete entities in search results. Default is false.") Boolean includeObsoleteEntities
    ) throws IOException {
        var pageable = PageRequest.of(
            pageNum != null ? pageNum : 0,
            pageSize != null ? pageSize : 20
        );

        if(lang == null) {
            lang = "en";
        }

        var properties = new LinkedHashMap<String, Collection<String>>();
        properties.put("type", List.of("class"));

        if(ontologyId != null)
            properties.put("ontologyId", List.of(ontologyId));

        if(includeObsoleteEntities == null || !includeObsoleteEntities) {
            properties.put("isObsolete", List.of("false"));
        }

        JsonTransformOptions outputOpts = new JsonTransformOptions();
        outputOpts.resolveReferences = true;
        outputOpts.manchesterSyntax = true;

        var res = entityRepository.find(
            pageable,
            lang,
            query,
            null,
            null,
            null,
            false,
            null, // excludeOntologyIds
            properties,
            outputOpts
        );

        return new McpPage<>(
            res.getContent().stream().map(McpClass::fromJson).toList(),
            res.getNumber(),
            res.getSize(),
            res.getTotalElements(),
            res.getTotalPages()
        );
    }


    @Tool(description = "Get all ancestors for a class in OLS")
    McpPage<McpClass> getAncestors(
        String ontologyId,
        String classIri,
        @ToolParam(required=false) Integer pageNum,
        @ToolParam(required=false) Integer pageSize,
        @ToolParam(required=false) String lang
    ) throws IOException {
        var pageable = PageRequest.of(
            pageNum != null ? pageNum : 0,
            pageSize != null ? pageSize : 20
        );

        if(lang == null) {
            lang = "en";
        }

        JsonTransformOptions outputOpts = new JsonTransformOptions();
        outputOpts.resolveReferences = true;
        outputOpts.manchesterSyntax = true;

        var res = classRepository.getAncestorsByOntologyId(
            ontologyId, pageable, classIri, false, lang, outputOpts);

        return new McpPage<>(
            res.getContent().stream().map(McpClass::fromJson).toList(),
            res.getNumber(),
            res.getSize(),
            res.getTotalElements(),
            res.getTotalPages()
        );
    }
    
    @Tool(description = "Get all descendants of a class in OLS")
    McpPage<McpClass> getDescendants(
        String ontologyId,
        String classIri,
        @ToolParam(required=false) Integer pageNum,
        @ToolParam(required=false) Integer pageSize,
        @ToolParam(required=false) String lang
    ) throws IOException {
        var pageable = PageRequest.of(
            pageNum != null ? pageNum : 0,
            pageSize != null ? pageSize : 20
        );

        if(lang == null) {
            lang = "en";
        }

        JsonTransformOptions outputOpts = new JsonTransformOptions();
        outputOpts.resolveReferences = true;
        outputOpts.manchesterSyntax = true;

        var res = classRepository.getDescendantsByOntologyId(
            ontologyId, pageable, classIri, false, lang, outputOpts);

        return new McpPage<>(
            res.getContent().stream().map(McpClass::fromJson).toList(),
            res.getNumber(),
            res.getSize(),
            res.getTotalElements(),
            res.getTotalPages()
        );
    }

    @Tool(description = "Search classes using semantic/embedding-based similarity. Uses vector embeddings to find semantically similar classes, which can find related concepts even when exact terms don't match. IMPORTANT: Call listEmbeddingModels first - only models with can_embed=true can be used for text search.")
    McpPage<McpClass> searchClassesWithEmbeddingModel(
        @ToolParam(description = "The natural language query to search for semantically similar classes") String query,
        @ToolParam(description = "The embedding model to use. Must have can_embed=true from listEmbeddingModels.") String model,
        @ToolParam(required=false, description = "Optional ontology ID to filter results") String ontologyId,
        @ToolParam(required=false) Integer pageNum,
        @ToolParam(required=false) Integer pageSize,
        @ToolParam(required=false) String lang
    ) throws IOException {
        var pageable = PageRequest.of(
            pageNum != null ? pageNum : 0,
            pageSize != null ? pageSize : 20
        );

        final String effectiveLang = (lang == null) ? "en" : lang;

        JsonTransformOptions outputOpts = new JsonTransformOptions();
        outputOpts.resolveReferences = true;
        outputOpts.manchesterSyntax = true;

        // Embed the query text using the embedding service
        float[] vectorArray = embeddingServiceClient.embedText(model, query);
        
        // Convert float[] to List<Double> for Neo4j
        List<Double> vectorList = new java.util.ArrayList<>(vectorArray.length);
        for (float f : vectorArray) {
            vectorList.add((double) f);
        }

        // Search classes using Neo4j vector search
        org.springframework.data.domain.Page<com.google.gson.JsonElement> results;
        if (ontologyId != null && !ontologyId.isEmpty()) {
            results = neo4jClient.searchByVectorInOntology("OntologyClass", vectorList, pageable, model, ontologyId, true);
        } else {
            results = neo4jClient.searchByVector("OntologyClass", vectorList, pageable, model);
        }

        // Transform results
        var transformedResults = results.map(e -> 
            uk.ac.ebi.spot.ols.repository.transforms.JsonTransformer.transformJson(e, effectiveLang, outputOpts));

        return new McpPage<>(
            transformedResults.getContent().stream().map(McpClass::fromJson).toList(),
            transformedResults.getNumber(),
            transformedResults.getSize(),
            transformedResults.getTotalElements(),
            transformedResults.getTotalPages()
        );
    }

    @Tool(description = "Find classes similar to a given class by IRI using pre-computed embeddings. Unlike searchClassesWithEmbeddingModel, this uses stored embeddings so any model from listEmbeddingModels can be used (can_embed not required).")
    McpPage<McpClass> getSimilarClasses(
        @ToolParam(description = "The IRI of the class to find similar classes for") String classIri,
        @ToolParam(description = "The embedding model to use. Any model from listEmbeddingModels works.") String model,
        @ToolParam(required=false) Integer pageNum,
        @ToolParam(required=false) Integer pageSize,
        @ToolParam(required=false) String lang
    ) {
        var pageable = PageRequest.of(
            pageNum != null ? pageNum : 0,
            pageSize != null ? pageSize : 20
        );

        final String effectiveLang = (lang == null) ? "en" : lang;

        JsonTransformOptions outputOpts = new JsonTransformOptions();
        outputOpts.resolveReferences = true;
        outputOpts.manchesterSyntax = true;

        var res = classRepository.getSimilar(pageable, classIri, effectiveLang, outputOpts, model);

        return new McpPage<>(
            res.getContent().stream().map(McpClass::fromJson).toList(),
            res.getNumber(),
            res.getSize(),
            res.getTotalElements(),
            res.getTotalPages()
        );
    }

    @Tool(description = "Calculate the similarity score between two classes using their embeddings. Returns a value between 0 and 1, where 1 means identical. Any model from listEmbeddingModels can be used (can_embed not required).")
    double getClassSimilarity(
        @ToolParam(description = "The IRI of the first class") String classIri1,
        @ToolParam(description = "The IRI of the second class") String classIri2,
        @ToolParam(description = "The embedding model to use. Any model from listEmbeddingModels works.") String model
    ) {
        return classRepository.getSimilarity(classIri1, classIri2, model);
    }
}

