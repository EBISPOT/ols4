package uk.ac.ebi.rdf2json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Exercises RDF/XML parsing and OWL punning through the packaged RDF2JSON executable. */
public class RDF2JSONFormatsIT {
    private static final String BASE = "https://example.org/formats#";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rdfXmlInputProducesOntologyAndSuccessStatus() throws Exception {
        Output output = run();

        assertEquals("formats", output.ontology.get("ontologyId").getAsString());
        assertEquals("https://example.org/formats", output.ontology.get("iri").getAsString());
        assertEquals(1, output.ontology.getAsJsonArray("classes").size());
        assertEquals(2, output.ontology.getAsJsonArray("individuals").size());
        assertEquals("formats", output.status.get("ontologyId").getAsString());
        assertEquals("SUCCESS", output.status.get("status").getAsString());
    }

    @Test
    public void classAndIndividualWithSameIriRemainAvailableInBothCollections() throws Exception {
        JsonObject ontology = run().ontology;
        JsonObject asClass = find(ontology.getAsJsonArray("classes"), BASE + "Dual");
        JsonObject asIndividual = find(ontology.getAsJsonArray("individuals"), BASE + "Dual");

        assertTrue(hasString(asClass.getAsJsonArray("type"), "class"));
        assertTrue(hasString(asIndividual.getAsJsonArray("type"), "individual"));
        assertNotNull(find(ontology.getAsJsonArray("individuals"), BASE + "Member"));
    }

    private Output run() throws Exception {
        Path resources = Path.of(getClass().getResource("/formats/punning.owl").toURI()).getParent();
        Path output = temporaryFolder.newFile("formats.json").toPath();
        Files.delete(output);
        Path log = temporaryFolder.newFile("rdf2json.log").toPath();

        Process process = new ProcessBuilder(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", System.getProperty("rdf2json.jar"),
                "--config", resources.resolve("punning.json").toString(),
                "--ontologyIds", "formats",
                "--output", output.toString(),
                "--basePath", resources.toString(),
                "--loadLocalFiles", "--noDates"))
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        assertTrue("RDF2JSON timed out", completed);
        String logs = Files.readString(log);
        assertEquals("RDF2JSON failed:\n" + logs, 0, process.exitValue());

        JsonObject data = readJson(output);
        JsonArray ontologies = data.getAsJsonArray("ontologies");
        assertEquals(1, ontologies.size());
        return new Output(ontologies.get(0).getAsJsonObject(), readJson(output.resolveSibling("formats.status.json")));
    }

    private static JsonObject readJson(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject find(JsonArray nodes, String iri) {
        for (JsonElement element : nodes) {
            JsonObject node = element.getAsJsonObject();
            if (iri.equals(node.get("iri").getAsString())) return node;
        }
        throw new AssertionError("Missing entity: " + iri);
    }

    private static boolean hasString(JsonArray values, String expected) {
        for (JsonElement value : values) {
            if (expected.equals(value.getAsString())) return true;
        }
        return false;
    }

    private record Output(JsonObject ontology, JsonObject status) {}
}
