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

    @Tool(description = """
        Search the Ontology Lookup Service (OLS) for ontology terms matching a query string.
        
        This tool performs a full-text search across all ontologies in OLS to find matching terms, classes, properties, and individuals.
        
        Parameters:
        - query: The search term or phrase (e.g., "cell", "diabetes", "mitochondrion")
        
        Returns: A JSON array of search results, each containing:
        - id: Unique identifier in format "ontologyId+entityIri" (e.g., "go+http://purl.obolibrary.org/obo/GO_0008150")
        - title: The primary label/name of the term
        - text: Description and additional information about the term
        - score: Relevance score for the search result
        
        Use this tool to discover relevant ontology terms when you need to find concepts related to your query.
        After finding relevant terms, use the 'fetch' tool to retrieve detailed information about a specific term.
        """)
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
    
    @Tool(description = """
        Retrieve detailed information about a specific ontology term by its ID.
        
        This tool fetches complete metadata for an ontology entity including its definition, labels, and hierarchical relationships.
        
        Parameters:
        - id: The unique identifier in format "ontologyId+entityIri" (e.g., "go+http://purl.obolibrary.org/obo/GO_0008150")
          This ID format is returned by the 'search' tool results. The ontologyId is the short identifier like "go", "efo", "uberon",
          and the entityIri is the full IRI/URL of the term.
        
        Returns: A JSON object with the following fields:
        - id: String - The identifier in "ontologyId+entityIri" format (e.g., "go+http://purl.obolibrary.org/obo/GO_0008150")
        - title: String - The CURIE and primary label concatenated (e.g., "GO:0008150 biological_process")
        - text: String - The definition of the term (single string, not a list)
        - url: String - The full IRI of the term (e.g., "http://purl.obolibrary.org/obo/GO_0008150")
        - metadata: Object - For class entities, this is an object with the following fields:
          * ontologyId: String - The ontology identifier (e.g., "go")
          * type: List<String> - The entity types (e.g., ["class"])
          * iri: String - The full IRI
          * curie: String - The compact URI (e.g., "GO:0008150")
          * label: List<String> - All labels for the term
          * definition: List<String> - All definitions for the term
          * directAncestor: List<Object> - Direct ancestors, each with fields: iri (String), definedBy (List<String>), label (List<String>)
          * directParent: List<Object> - Direct parents, each with fields: iri (String), definedBy (List<String>), label (List<String>)
          * hierarchicalParent: List<Object> - Hierarchical parents, each with fields: iri (String), definedBy (List<String>), label (List<String>)
          For non-class entities, metadata contains only: {"type": "property"} or similar.
        
        Use this tool after using 'search' to get comprehensive information about a specific term.
        The metadata field contains structured information about the term's hierarchical relationships within the ontology.
        """)
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
