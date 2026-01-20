package uk.ac.ebi.ols.reporting;

/**
 * Represents the loading status of an ontology during the RDF2JSON process.
 */
public class OntologyLoadStatus {

    public enum Status {
        SUCCESS,          // Ontology loaded successfully
        FALLBACK,         // Ontology failed to load but fallback version used
        FAILED_NO_FALLBACK, // Ontology failed to load and no fallback available
        SKIPPED           // Ontology was skipped (e.g., marked as obsolete)
    }

    private final String ontologyId;
    private final Status status;
    private final String errorMessage;
    private final String version; // Version that was loaded (for SUCCESS and FALLBACK)

    public OntologyLoadStatus(String ontologyId, Status status, String errorMessage, String version) {
        this.ontologyId = ontologyId;
        this.status = status;
        this.errorMessage = errorMessage;
        this.version = version;
    }

    public static OntologyLoadStatus success(String ontologyId, String version) {
        return new OntologyLoadStatus(ontologyId, Status.SUCCESS, null, version);
    }

    public static OntologyLoadStatus fallback(String ontologyId, String version, String errorMessage) {
        return new OntologyLoadStatus(ontologyId, Status.FALLBACK, errorMessage, version);
    }

    public static OntologyLoadStatus failed(String ontologyId, String errorMessage) {
        return new OntologyLoadStatus(ontologyId, Status.FAILED_NO_FALLBACK, errorMessage, null);
    }

    public String getOntologyId() {
        return ontologyId;
    }

    public Status getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getVersion() {
        return version;
    }

    public boolean hasIssue() {
        return status != Status.SUCCESS && status != Status.SKIPPED;
    }
}
