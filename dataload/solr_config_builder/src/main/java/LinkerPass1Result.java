import java.util.*;

/**
 * Result from LinkerPass1 - simplified version for SolrConfigBuilder.
 * Only includes the property maps needed for Solr config generation.
 */
public class LinkerPass1Result {

    // Per-ontology property sets
    // ontology id -> set of properties found in ontology metadata
    Map<String, Set<String>> ontologyIdToOntologyProperties = new TreeMap<>();
    
    // ontology id -> set of properties found in classes
    Map<String, Set<String>> ontologyIdToClassProperties = new TreeMap<>();
    
    // ontology id -> set of properties found in properties
    Map<String, Set<String>> ontologyIdToPropertyProperties = new TreeMap<>();
    
    // ontology id -> set of properties found in individuals
    Map<String, Set<String>> ontologyIdToIndividualProperties = new TreeMap<>();
    
    // ontology id -> set of properties found on edges
    Map<String, Set<String>> ontologyIdToEdgeProperties = new TreeMap<>();
}
