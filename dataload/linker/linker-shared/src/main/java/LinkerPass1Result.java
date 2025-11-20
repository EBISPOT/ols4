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
}
