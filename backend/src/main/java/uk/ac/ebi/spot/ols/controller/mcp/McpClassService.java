
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

    @Tool(description = """
        Search for ontology classes in OLS matching a query string.
        
        This tool searches specifically for classes (types/concepts) across ontologies, filtering out properties and individuals.
        Classes represent the main conceptual entities in ontologies (e.g., diseases, anatomical structures, cell types).
        
        Parameters:
        - query: Search term or phrase (required, e.g., "heart disease", "neuron", "protein binding")
        - ontologyId: Limit search to a specific ontology (optional, e.g., "go" for Gene Ontology, "efo" for Experimental Factor Ontology)
        - pageNum: Page number for pagination, starting from 0 (optional, default: 0)
        - pageSize: Number of results per page (optional, default: 20, max recommended: 100)
        - lang: Language code for labels (optional, default: "en" for English)
        
        Returns: A paginated list of classes with:
        - content: Array of class objects, each containing:
          * iri: The full IRI/URL identifier of the class
          * label: Primary name/label of the class
          * description: Definition and description
          * ontology_name: Name of the ontology containing this class
          * ontology_id: Short identifier of the ontology
          * synonyms: Alternative names for the class
        - page: Current page number
        - pageSize: Number of results per page
        - totalElements: Total number of matching results
        - totalPages: Total number of pages available
        
        Use this tool when you need to find specific types/classes in ontologies, optionally restricted to a particular ontology.
        For hierarchical information about a class, use 'getAncestors' or 'getDescendants' tools.
        """)
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


    @Tool(description = """
        Get all ancestor (parent) classes of a specific class in the ontology hierarchy.
        
        This tool retrieves all classes that are hierarchically above (more general than) the specified class.
        Ancestors represent broader, more general concepts. For example, ancestors of "heart" might include
        "organ", "anatomical structure", "entity".
        
        Parameters:
        - ontologyId: The ontology identifier (required, e.g., "go", "efo", "uberon", "hp")
        - classIri: The full IRI/URL of the class (required, e.g., "http://purl.obolibrary.org/obo/GO_0008150")
        - pageNum: Page number for pagination, starting from 0 (optional, default: 0)
        - pageSize: Number of results per page (optional, default: 20)
        - lang: Language code for labels (optional, default: "en")
        
        Returns: A paginated list of ancestor classes with:
        - content: Array of ancestor class objects, each containing:
          * iri: The full IRI/URL of the ancestor class
          * label: Name of the ancestor class
          * description: Definition of the ancestor class
          * ontology_name: Name of the ontology
          * ontology_id: Ontology identifier
        - page: Current page number
        - pageSize: Results per page
        - totalElements: Total number of ancestors
        - totalPages: Total pages available
        
        Use this tool to understand the broader context and classification of a class by exploring its parent classes.
        This helps answer questions like "What is this a type of?" or "What broader categories does this belong to?"
        """)
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
    
    @Tool(description = """
        Get all descendant (child) classes of a specific class in the ontology hierarchy.
        
        This tool retrieves all classes that are hierarchically below (more specific than) the specified class.
        Descendants represent narrower, more specific concepts. For example, descendants of "disease" might include
        "genetic disease", "infectious disease", "cardiovascular disease", and all their subtypes.
        
        Parameters:
        - ontologyId: The ontology identifier (required, e.g., "go", "efo", "uberon", "mondo")
        - classIri: The full IRI/URL of the class (required, e.g., "http://purl.obolibrary.org/obo/MONDO_0000001")
        - pageNum: Page number for pagination, starting from 0 (optional, default: 0)
        - pageSize: Number of results per page (optional, default: 20)
        - lang: Language code for labels (optional, default: "en")
        
        Returns: A paginated list of descendant classes with:
        - content: Array of descendant class objects, each containing:
          * iri: The full IRI/URL of the descendant class
          * label: Name of the descendant class
          * description: Definition of the descendant class
          * ontology_name: Name of the ontology
          * ontology_id: Ontology identifier
        - page: Current page number
        - pageSize: Results per page
        - totalElements: Total number of descendants
        - totalPages: Total pages available
        
        Use this tool to explore the subtypes and more specific instances within a category.
        This helps answer questions like "What are the specific types of X?" or "What subcategories exist under this concept?"
        """)
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

