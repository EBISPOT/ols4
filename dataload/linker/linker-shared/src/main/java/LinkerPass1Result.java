import java.util.*;

public class LinkerPass1Result {

    // entity IRI -> all definitions of that IRI from ontologies
    Map<String, EntityDefinitionSet> iriToDefinitions = new HashMap<>();

    // ontology IRI -> IDs for that ontology (usually only 1)
    Map<String, Set<String>> ontologyIriToOntologyIds = new HashMap<>();

    // preferred prefix -> ontology IDs with that prefix (usually only 1)
    Map<String, Set<String>> preferredPrefixToOntologyIds = new HashMap<>();

    // ontology id -> defined base URIs for that ontology
    Map<String, Set<String>> ontologyIdToBaseUris = new HashMap<>();

    // ontology id -> IDs of ontologies that import at least 1 term from the ontology
    Map<String, List<String>> ontologyIdToImportingOntologyIds = new HashMap<>();

    // ontology id -> IDs of ontologies it imports at least 1 term from
    Map<String, List<String>> ontologyIdToImportedOntologyIds = new HashMap<>();

    // Per-ontology property sets (formerly from OntologyScanner)
    // ontology id -> set of properties found in ontology metadata
    Map<String, Set<String>> ontologyIdToOntologyProperties = new HashMap<>();
    
    // ontology id -> set of properties found in classes
    Map<String, Set<String>> ontologyIdToClassProperties = new HashMap<>();
    
    // ontology id -> set of properties found in properties
    Map<String, Set<String>> ontologyIdToPropertyProperties = new HashMap<>();
    
    // ontology id -> set of properties found in individuals
    Map<String, Set<String>> ontologyIdToIndividualProperties = new HashMap<>();
    
    // ontology id -> set of properties found on edges
    Map<String, Set<String>> ontologyIdToEdgeProperties = new HashMap<>();
    
    // ontology id -> URI -> set of node types for that URI in that ontology
    Map<String, Map<String, Set<String>>> ontologyIdToUriToTypes = new HashMap<>();
}
