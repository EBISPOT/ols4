import com.google.gson.JsonElement;

import java.util.Objects;
import java.util.Set;

public class EntityDefinition implements Comparable<EntityDefinition> {

    String ontologyId;
    Set<String> entityTypes;
    boolean isDefiningOntology;
    JsonElement label;
    JsonElement curie;
    boolean isObsolete;

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

    @Override
    public int compareTo(EntityDefinition other) {
        int cmp = this.ontologyId.compareTo(other.ontologyId);
        if (cmp != 0) return cmp;
        return this.entityTypes.toString().compareTo(other.entityTypes.toString());
    }
}
