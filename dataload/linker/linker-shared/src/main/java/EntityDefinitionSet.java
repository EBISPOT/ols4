import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class EntityDefinitionSet {
    Set<EntityDefinition> definitions = new TreeSet<>();
    Set<EntityDefinition> definingDefinitions = new TreeSet<>();
    Set<String> definingOntologyIris = new TreeSet<>();
    Set<String> definingOntologyIds = new TreeSet<>();
    Map<String, EntityDefinition> ontologyIdToDefinitions = new TreeMap<>();
}
