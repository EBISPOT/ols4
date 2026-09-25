package uk.ac.ebi.ols.reporting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportingServiceIT {

    @TempDir
    Path tempDir;

    @Test
    void reportCountsEveryOntologyAndDetailsOnlyFallbackAndFailure() throws Exception {
        Path config = copyFixture("config.json", tempDir);
        Path statusDir = Files.createDirectory(tempDir.resolve("statuses"));
        for (String name : List.of("alpha", "beta", "gamma", "delta")) {
            copyFixture(name + ".status.json", statusDir);
        }
        // Files without the status suffix are not ontology outcomes.
        Files.writeString(statusDir.resolve("other.json"), "not a status file");

        Path reportFile = tempDir.resolve("report.txt");
        Path outputFile = tempDir.resolve("process-output.txt");
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", System.getProperty("reporting.jar"),
                "--config", config.toString(),
                "--statusDir", statusDir.toString(),
                "--reportFile", reportFile.toString())
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start();

        boolean finished = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        assertTrue(finished, "Reporting CLI timed out");
        assertEquals(0, process.exitValue(), Files.readString(outputFile));
        assertTrue(Files.exists(reportFile), "Reporting CLI did not write the requested report");

        List<String> lines = Files.readAllLines(reportFile);
        assertEquals(4, valueFor(lines, "Total Ontologies Processed: "));
        assertEquals(2, valueFor(lines, "Issues Found: "));

        Map<String, Map<String, String>> issues = parseIssueSections(lines);
        assertEquals(Set.of("beta", "gamma"), issues.keySet());
        assertEquals(Map.of(
                "Status", "FALLBACK",
                "Error", "Current ontology could not be parsed",
                "Version", "2026-08-01"), issues.get("beta"));
        assertEquals(Map.of(
                "Status", "FAILED_NO_FALLBACK",
                "Error", "Invalid RDF"), issues.get("gamma"));
    }

    private static Path copyFixture(String name, Path destinationDir) throws IOException {
        Path destination = destinationDir.resolve(name);
        try (InputStream input = Objects.requireNonNull(
                ReportingServiceIT.class.getResourceAsStream("/contracts/" + name), name)) {
            Files.copy(input, destination);
        }
        return destination;
    }

    private static int valueFor(List<String> lines, String prefix) {
        return lines.stream()
                .filter(line -> line.startsWith(prefix))
                .map(line -> Integer.parseInt(line.substring(prefix.length())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing report field: " + prefix));
    }

    private static Map<String, Map<String, String>> parseIssueSections(List<String> lines) {
        Map<String, Map<String, String>> issues = new HashMap<>();
        Map<String, String> current = null;
        for (String line : lines) {
            if (line.startsWith("Ontology ID: ")) {
                String id = line.substring("Ontology ID: ".length());
                current = new HashMap<>();
                issues.put(id, current);
            } else if (current != null) {
                for (String field : List.of("Status", "Error", "Version")) {
                    String prefix = field + ": ";
                    if (line.startsWith(prefix)) {
                        current.put(field, line.substring(prefix.length()));
                    }
                }
            }
        }
        return issues;
    }
}
