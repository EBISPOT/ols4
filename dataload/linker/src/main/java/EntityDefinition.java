import com.google.gson.JsonElement;

import java.util.Objects;
import java.util.Set;

public class EntityDefinition {

    String ontologyId;
    Set<String> entityTypes;
    boolean isDefiningOntology;
    JsonElement label;
    JsonElement curie;

    @Override
    public boolean equals(Object other) {
        return other instanceof EntityDefinition &&
                ((EntityDefinition) other).ontologyId.equals(ontologyId) &&
                ((EntityDefinition) other).entityTypes.equals(entityTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ontologyId, entityTypes, isDefiningOntology);
    }
}
