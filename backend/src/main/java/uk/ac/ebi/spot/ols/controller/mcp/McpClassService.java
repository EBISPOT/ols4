
package uk.ac.ebi.spot.ols.controller.mcp;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import uk.ac.ebi.spot.ols.model.mcp.McpClass;
import uk.ac.ebi.spot.ols.model.mcp.McpPage;
import uk.ac.ebi.spot.ols.repository.ClassRepository;
import uk.ac.ebi.spot.ols.repository.EntityRepository;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

@Service
public class McpClassService {

    @Autowired
    EntityRepository entityRepository;

    @Autowired
    ClassRepository classRepository;

    @Tool(description = "Search all classes in OLS for a query string")
    McpPage<McpClass> searchClasses(
        String query,
        @ToolParam(required=false) String ontologyId,
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

        var properties = new LinkedHashMap<String, Collection<String>>();
        properties.put("type", List.of("class"));

        if(ontologyId != null)
            properties.put("ontologyId", List.of(ontologyId));

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
}

