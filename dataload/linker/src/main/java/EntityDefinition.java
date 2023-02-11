import com.google.gson.JsonElement;

import java.util.Objects;

public class EntityDefinition {

    String ontologyId;
    String entityType;
    boolean isDefiningOntology;
    JsonElement label;

    @Override
    public boolean equals(Object other) {
        return other instanceof EntityDefinition &&
                ((EntityDefinition) other).ontologyId.equals(ontologyId) &&
                ((EntityDefinition) other).entityType.equals(entityType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ontologyId, entityType, isDefiningOntology);
    }
}
