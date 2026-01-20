package uk.ac.ebi.rdf2json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;

/**
 * Simple class to write ontology loading status to individual JSON files.
 * These files will be collected by the reporting service in the nextflow pipeline.
 */
public class OntologyStatusWriter {

    private static final Logger logger = LoggerFactory.getLogger(OntologyStatusWriter.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public enum Status {
        SUCCESS,
        FALLBACK,
        FAILED_NO_FALLBACK,
        SKIPPED
    }

    public static class OntologyStatus {
        private final String ontologyId;
        private final Status status;
        private final String errorMessage;
        private final String version;

        public OntologyStatus(String ontologyId, Status status, String errorMessage, String version) {
            this.ontologyId = ontologyId;
            this.status = status;
            this.errorMessage = errorMessage;
            this.version = version;
        }
    }

    /**
     * Write a status file for a successfully loaded ontology.
     */
    public static void writeSuccess(String outputPath, String ontologyId, String version) {
        writeStatus(outputPath, new OntologyStatus(ontologyId, Status.SUCCESS, null, version));
    }

    /**
     * Write a status file for an ontology that failed to load but has a fallback.
     */
    public static void writeFallback(String outputPath, String ontologyId, String version, String errorMessage) {
        writeStatus(outputPath, new OntologyStatus(ontologyId, Status.FALLBACK, errorMessage, version));
    }

    /**
     * Write a status file for an ontology that failed with no fallback.
     */
    public static void writeFailedNoFallback(String outputPath, String ontologyId, String errorMessage) {
        writeStatus(outputPath, new OntologyStatus(ontologyId, Status.FAILED_NO_FALLBACK, errorMessage, null));
    }

    /**
     * Write a status file for an ontology that was skipped (e.g., obsolete).
     */
    public static void writeSkipped(String outputPath, String ontologyId, String reason) {
        writeStatus(outputPath, new OntologyStatus(ontologyId, Status.SKIPPED, reason, null));
    }

    private static void writeStatus(String outputPath, OntologyStatus status) {
        String statusFilePath = outputPath.replace(".json", ".status.json");
        try (FileWriter writer = new FileWriter(statusFilePath)) {
            gson.toJson(status, writer);
            logger.debug("Wrote status file: {}", statusFilePath);
        } catch (IOException e) {
            logger.error("Failed to write status file {}: {}", statusFilePath, e.getMessage());
        }
    }
}
