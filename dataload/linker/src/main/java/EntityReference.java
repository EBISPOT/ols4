import java.util.Objects;

public class EntityReference implements Comparable<EntityReference> {

    String ontologyId;
    String entityType;
    boolean isDefiningOntology;

    @Override
    public boolean equals(Object other) {
        return other instanceof EntityReference &&
                ((EntityReference) other).ontologyId.equals(ontologyId) &&
                ((EntityReference) other).entityType.equals(entityType) &&
                ((EntityReference) other).isDefiningOntology == isDefiningOntology;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ontologyId, entityType, isDefiningOntology);
    }

    @Override
    public int compareTo(EntityReference o) {
        return ontologyId.compareTo(o.ontologyId);
    }
}
