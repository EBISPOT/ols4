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
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

@Service
public class McpOntologyService {

    @Autowired
    OntologyRepository ontologyRepository;

    @Tool(description = """
        List all available ontologies in the Ontology Lookup Service (OLS).
        
        This tool retrieves metadata about all ontologies currently loaded in OLS, providing an overview
        of available biomedical and scientific knowledge resources.
        
        Parameters:
        - lang: Language code for ontology titles and descriptions (optional, default: "en" for English)
        
        Returns: A list of ontology objects, each containing:
        - ontology_id: Short identifier for the ontology (e.g., "go", "efo", "hp", "mondo")
        - title: Full name of the ontology (e.g., "Gene Ontology", "Experimental Factor Ontology")
        - description: Detailed description of the ontology's scope and purpose
        - namespace: The namespace/prefix used in the ontology
        - homepage: URL to the ontology's homepage or documentation
        - version: Current version of the ontology loaded in OLS
        - number_of_terms: Count of terms/classes in the ontology
        - status: Status of the ontology (e.g., "LOADED", "LOADING", "FAILED")
        
        Common ontologies include:
        - GO (Gene Ontology): Biological processes, cellular components, molecular functions
        - EFO (Experimental Factor Ontology): Experimental variables and factors
        - HP (Human Phenotype Ontology): Human phenotypic abnormalities
        - MONDO (Monarch Disease Ontology): Diseases and medical conditions
        - UBERON: Anatomical structures across species
        - CL (Cell Ontology): Cell types
        - CHEBI: Chemical entities of biological interest
        
        Use this tool to discover what ontologies are available before searching for specific terms.
        """)
    List<McpOntology> listOntologies(
        @ToolParam(required=false) String lang
    ) throws IOException {
        if(lang == null) {
            lang = "en";
        }

        JsonTransformOptions outputOpts = new JsonTransformOptions();
        outputOpts.resolveReferences = true;
        outputOpts.manchesterSyntax = true;

        var res = ontologyRepository.find(
            PageRequest.of(0, 1000),
            lang,
            null,
            null,
            null,
            false,
            null,
            outputOpts
        );

        return res.getContent().stream().map(McpOntology::fromJson).toList();
    }





    
}
