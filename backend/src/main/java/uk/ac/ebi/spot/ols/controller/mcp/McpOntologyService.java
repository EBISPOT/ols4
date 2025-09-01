package uk.ac.ebi.spot.ols.controller.mcp;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import uk.ac.ebi.spot.ols.model.mcp.McpOntology;
import uk.ac.ebi.spot.ols.repository.OntologyRepository;

@Service
public class McpOntologyService {

    @Autowired
    OntologyRepository ontologyRepository;

    @Tool(description = "Get all ontologies from OLS")
    List<McpOntology> listOntologies(
        @ToolParam(required=false) String lang
    ) throws IOException {
        if(lang == null) {
            lang = "en";
        }

        var res = ontologyRepository.find(
            PageRequest.of(0, 1000),
            lang,
            null,
            null,
            null,
            false,
            null,
            false
        );

        return res.getContent().stream().map(McpOntology::fromJson).toList();
    }





    
}
