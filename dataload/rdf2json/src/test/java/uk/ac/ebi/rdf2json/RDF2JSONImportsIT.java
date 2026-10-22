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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Packaged-CLI contract for a local OWL import, with no network dependency. */
public class RDF2JSONImportsIT {
    private static final String BASE = "https://example.org/imports#";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void importedClassesContributeAnnotationsAndRetainProvenance() throws Exception {
        Path fixtures = Path.of(getClass().getResource("/edge-contracts/imports.json").toURI()).getParent();
        Path imported = fixtures.resolve("imports-child.ttl");
        String main = Files.readString(fixtures.resolve("imports-main.ttl.template"))
                .replace("__IMPORT_URI__", imported.toUri().toString());
        Path workingDirectory = temporaryFolder.getRoot().toPath();
        Files.writeString(workingDirectory.resolve("imports-main.ttl"), main);
        Path output = workingDirectory.resolve("imports.json");
        Path log = workingDirectory.resolve("rdf2json.log");

        Process process = new ProcessBuilder(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", System.getProperty("rdf2json.jar"),
                "--config", fixtures.resolve("imports.json").toString(),
                "--ontologyIds", "imports",
                "--output", output.toString(),
                "--basePath", workingDirectory.toString(),
                "--loadLocalFiles", "--noDates"))
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        assertTrue("RDF2JSON timed out", completed);
        String logs = Files.readString(log);
        assertEquals("RDF2JSON failed:\n" + logs, 0, process.exitValue());

        JsonObject status = readJson(output.resolveSibling("imports.status.json"));
        assertEquals("SUCCESS", status.get("status").getAsString());
        JsonArray ontologies = readJson(output).getAsJsonArray("ontologies");
        assertEquals(1, ontologies.size());
        JsonArray classes = ontologies.get(0).getAsJsonObject().getAsJsonArray("classes");
        JsonObject mainClass = find(classes, "Main");
        JsonObject importedClass = find(classes, "Imported");
        JsonObject sharedClass = find(classes, "Shared");

        assertFalse(mainClass.get("imported").getAsBoolean());
        assertTrue(importedClass.get("imported").getAsBoolean());
        assertFalse(sharedClass.get("imported").getAsBoolean());
        assertEquals(BASE + "Main", importedClass.get("http://www.w3.org/2000/01/rdf-schema#subClassOf").getAsString());
        assertEquals(BASE + "Main", importedClass.getAsJsonArray("directParent").get(0).getAsString());
        assertEquals("Imported class", importedClass.getAsJsonArray("label").get(0).getAsJsonObject().get("value").getAsString());
        assertEquals("Shared class", sharedClass.getAsJsonArray("label").get(0).getAsJsonObject().get("value").getAsString());
    }

    private static JsonObject readJson(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject find(JsonArray nodes, String name) {
        for (JsonElement candidate : nodes) {
            JsonObject node = candidate.getAsJsonObject();
            if ((BASE + name).equals(node.get("iri").getAsString())) return node;
        }
        throw new AssertionError("Missing class " + BASE + name);
    }
}
