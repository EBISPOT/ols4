import java.util.List;
import java.util.Map;
import java.util.Set;

public class EntityDefinitionSet {
    Set<EntityDefinition> definitions;
    Set<EntityDefinition> definingDefinitions; // where isDefiningOntology=true
    Map<String, EntityDefinition> ontologyIdToDefinitions;
}
