import java.util.*;

/**
 * Information about an ontology extracted from the manifest.
 * This replaces the old OntologyScanner.Result class.
 */
public class OntologyManifestInfo {
    
    public enum NodeType {
        ONTOLOGY, CLASS, PROPERTY, INDIVIDUAL
    }
    
    public String ontologyId;
    public String ontologyUri;
    public Set<String> allOntologyProperties = new HashSet<>();
    public Set<String> allClassProperties = new HashSet<>();
    public Set<String> allPropertyProperties = new HashSet<>();
    public Set<String> allIndividualProperties = new HashSet<>();
    public Set<String> allEdgeProperties = new HashSet<>();
    public Map<String, Set<NodeType>> uriToTypes = new HashMap<>();
}
