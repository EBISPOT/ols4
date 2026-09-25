package uk.ac.ebi.rdf2json;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Exercises the packaged CLI and its JSON/status file contract, as Nextflow does. */
public class RDF2JSONContractIT {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void validOntologyProducesDataAndSuccessStatus() throws Exception {
        Output output = run("valid.json", null);

        JsonObject ontology = onlyOntology(output.data);
        assertEquals("contract", ontology.get("ontologyId").getAsString());
        assertEquals("https://example.org/contract", ontology.get("iri").getAsString());
        assertEquals("https://example.org/contract#Root",
                ontology.getAsJsonArray("classes").get(0).getAsJsonObject().get("iri").getAsString());
        assertStatus(output.status, "SUCCESS");
    }

    @Test
    public void failedUpdateUsesPreviousOntologyAndReportsFallback() throws Exception {
        Output output = run("missing.json", "previous.json");

        JsonObject ontology = onlyOntology(output.data);
        assertEquals("contract", ontology.get("ontologyId").getAsString());
        assertEquals("https://example.org/contract#Previous",
                ontology.getAsJsonArray("classes").get(0).getAsJsonObject().get("iri").getAsString());
        assertTrue(ontology.get("is_fallback").getAsBoolean());
        assertStatus(output.status, "FALLBACK");
        assertEquals("previous-version", output.status.get("version").getAsString());
    }

    @Test
    public void failedOntologyWithoutPreviousOutputReportsFailure() throws Exception {
        Output output = run("missing.json", null);

        assertEquals(0, output.data.getAsJsonArray("ontologies").size());
        assertStatus(output.status, "FAILED_NO_FALLBACK");
        assertFalse(output.status.get("errorMessage").isJsonNull());
    }

    @Test
    public void previousOutputForAnotherOntologyIsNotAValidFallback() throws Exception {
        Output output = run("no-purl.json", "previous-other.json");

        assertEquals(0, output.data.getAsJsonArray("ontologies").size());
        assertStatus(output.status, "FAILED_NO_FALLBACK");
    }

    @Test
    public void obsoleteOntologyIsSkipped() throws Exception {
        Output output = run("obsolete.json", null);

        assertEquals(0, output.data.getAsJsonArray("ontologies").size());
        assertStatus(output.status, "SKIPPED");
    }

    @Test
    public void obsoleteOntologyStaysSkippedWhenPreviousOutputExists() throws Exception {
        Output output = run("obsolete.json", "previous.json");

        assertEquals(0, output.data.getAsJsonArray("ontologies").size());
        assertStatus(output.status, "SKIPPED");
    }

    private Output run(String configName, String previousName) throws Exception {
        Path fixtureDirectory = Path.of(getClass().getResource("/contracts/valid.ttl").toURI()).getParent();
        Path outputPath = temporaryFolder.newFile("contract.json").toPath();
        Files.delete(outputPath);

        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", System.getProperty("rdf2json.jar"),
                "--config", fixtureDirectory.resolve(configName).toString(),
                "--ontologyIds", "contract",
                "--output", outputPath.toString(),
                "--basePath", fixtureDirectory.toString(),
                "--loadLocalFiles", "--noDates"));
        if (previousName != null) {
            command.add("--mergeOutputWith");
            command.add(fixtureDirectory.resolve(previousName).toString());
        }

        Path logPath = temporaryFolder.newFile("rdf2json.log").toPath();
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(logPath.toFile())
                .start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
        }
        assertTrue("RDF2JSON did not finish within 30 seconds", completed);
        String log = Files.readString(logPath);
        assertEquals("RDF2JSON exited unsuccessfully:\n" + log, 0, process.exitValue());

        Path statusPath = outputPath.resolveSibling("contract.status.json");
        assertTrue("Missing ontology output:\n" + log, Files.exists(outputPath));
        assertTrue("Missing ontology status:\n" + log, Files.exists(statusPath));
        return new Output(readJson(outputPath), readJson(statusPath));
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject onlyOntology(JsonObject data) {
        JsonArray ontologies = data.getAsJsonArray("ontologies");
        assertEquals(1, ontologies.size());
        return ontologies.get(0).getAsJsonObject();
    }

    private static void assertStatus(JsonObject status, String expected) {
        assertEquals("contract", status.get("ontologyId").getAsString());
        assertEquals(expected, status.get("status").getAsString());
    }

    private record Output(JsonObject data, JsonObject status) {}
}
