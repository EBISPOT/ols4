import java.util.Objects;

public class LinkerReferencedOntology {

    String ontologyId;
    boolean isDefiningOntology;

    @Override
    public boolean equals(Object other) {
        return other instanceof LinkerReferencedOntology &&
                ((LinkerReferencedOntology) other).ontologyId.equals(ontologyId) &&
                ((LinkerReferencedOntology) other).isDefiningOntology == isDefiningOntology;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ontologyId, isDefiningOntology);
    }
}
