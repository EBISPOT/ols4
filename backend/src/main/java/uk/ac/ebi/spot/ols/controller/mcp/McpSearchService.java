package uk.ac.ebi.spot.ols.controller.mcp;

import java.io.IOException;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;

import uk.ac.ebi.spot.ols.model.mcp.McpFetchResult;
import uk.ac.ebi.spot.ols.model.mcp.McpSearchResult;
import uk.ac.ebi.spot.ols.repository.EntityRepository;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

@Service
public class McpSearchService {

    @Autowired
    EntityRepository entityRepository;

    Gson gson = new Gson();

    // OpenAI compatibility tools
    // Specific params and result format to match OpenAI requirements:
    // https://platform.openai.com/docs/mcp#create-an-mcp-server

    @Tool(description = "OpenAI compliant tool to search OLS for a query string")
    String search(
        String query
    ) throws IOException {
        var pageable = PageRequest.of(0, 20);

        JsonTransformOptions outputOpts = new JsonTransformOptions();
        outputOpts.resolveReferences = true;
        outputOpts.manchesterSyntax = true;

        var res = entityRepository.find(
            pageable,
            "en",
            query,
            null,
            null,
            null,
            false,
            Map.of(),
            outputOpts
        );

        return gson.toJson( res.getContent().stream().map(McpSearchResult::fromJson).toList() );
    }
    
    @Tool(description = "OpenAI compliant tool to retrieve an entity from OLS by ID returned from the search tool. The ID must be of the format ontologyid+entityIri, e.g. go+http://purl.obolibrary.org/obo/GO_0008150. IDs in this format are returned by the OpenAI compliant 'search' tool.")
    String fetch(
        String id
    ) throws IOException {

        JsonTransformOptions outputOpts = new JsonTransformOptions();
        outputOpts.resolveReferences = true;
        outputOpts.manchesterSyntax = true;

        var tokens = id.split("\\+");
        if (tokens.length != 2) {
            throw new IllegalArgumentException("ID must be of the format ontologyid+entityIri, e.g. go+http://purl.obolibrary.org/obo/GO_0008150");
        }

        var res = entityRepository.getByOntologyIdAndIri(
            tokens[0],
            tokens[1],
            "en",
            outputOpts
        );

        return gson.toJson( McpFetchResult.fromJson(res) );
    }

}
