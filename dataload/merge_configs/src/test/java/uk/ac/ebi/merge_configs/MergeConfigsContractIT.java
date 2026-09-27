package uk.ac.ebi.merge_configs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Exercises the packaged CLI at the file boundary used by Nextflow. */
public class MergeConfigsContractIT {
    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void laterConfigOverridesFieldsForTheSameIdRegardlessOfCase() throws Exception {
        JsonArray ontologies = mergeFixtures();

        assertEquals(3, ontologies.size());
        JsonObject example = ontologies.get(0).getAsJsonObject();
        assertEquals("example", example.get("id").getAsString());
        assertEquals("updated", example.get("title").getAsString());
        assertFalse(example.get("is_obsolete").getAsBoolean());
        assertEquals("retained", example.get("baseOnly").getAsString());
        assertEquals("second", ontologies.get(1).getAsJsonObject().get("id").getAsString());
        assertEquals("third", ontologies.get(2).getAsJsonObject().get("id").getAsString());
    }

    @Test
    public void eachOntologyGetsDefaultFilterAndConfiguredFiltersAreDeduplicated() throws Exception {
        JsonArray ontologies = mergeFixtures();

        assertEquals(List.of(RDF_TYPE, "urn:example:extra"), strings(ontologies.get(0).getAsJsonObject().getAsJsonArray("filterProperty")));
        assertEquals(List.of(RDF_TYPE), strings(ontologies.get(1).getAsJsonObject().getAsJsonArray("filterProperty")));
        assertEquals(List.of(RDF_TYPE), strings(ontologies.get(2).getAsJsonObject().getAsJsonArray("filterProperty")));
    }

    private JsonArray mergeFixtures() throws Exception {
        Path base = Path.of(getClass().getResource("/contracts/base.json").toURI());
        Path override = Path.of(getClass().getResource("/contracts/override.json").toURI());
        Path output = temporaryFolder.getRoot().toPath().resolve("merged.json");
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Path testClasses = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        Path jar = testClasses.getParent().resolve("merge_configs-1.0-SNAPSHOT.jar");
        Process process = new ProcessBuilder(java.toString(), "-jar", jar.toString(),
                "--config", base + "," + override, "--output", output.toString())
                .redirectErrorStream(true)
                .start();
        String log = new String(process.getInputStream().readAllBytes());
        assertEquals("merge_configs output:\n" + log, 0, process.waitFor());
        assertTrue("merge_configs did not create its output", Files.isRegularFile(output));
        JsonObject merged = JsonParser.parseString(Files.readString(output)).getAsJsonObject();
        return merged.getAsJsonArray("ontologies");
    }

    private List<String> strings(JsonArray values) {
        return values.asList().stream().map(value -> value.getAsString()).toList();
    }
}
