package uk.ac.ebi.spot.ols.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.spot.ols.service.TextTaggerService.TaggedEntity;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real external-process coverage for {@link TextTaggerService}: {@code startProcess}/
 * {@code stopProcess}/{@code ensureRunning}, and {@code tagText}'s full process-interaction loop
 * (write one line to stdin, read one JSON line back from stdout).
 *
 * <p>This suite spawns the <em>real</em> {@code ols_text_tagger} CLI binary, built from this
 * repo's own {@code text_tagger/} Rust source (not a hand-rolled stand-in script). The service's
 * own javadoc describes graceful degradation as the expected norm when the binary is missing, so
 * every test here is conditional on the real binary actually being resolvable on {@code PATH}
 * when this JVM started (see {@link #binaryIsAvailable()}); if it is not, the whole class is
 * skipped via {@code Assumptions}, not failed -- this environment does not ship the binary or a
 * Rust toolchain by default, and this class deliberately never invokes {@code cargo} itself, to
 * keep routine test runs decoupled from a Rust build. To run this suite for real:
 * <pre>
 *   cd text_tagger &amp;&amp; cargo build --release
 *   export PATH="$(pwd)/target/release:$PATH"
 *   cd ../backend &amp;&amp; mvn -q -o verify -Dsurefire.skip=true -Dapi.version=1.44
 * </pre>
 * See the "Implemented TextTaggerService baseline" section of
 * {@code docs/backend-testing-strategy.md} for why this is opportunistic rather than a guaranteed,
 * always-on part of this suite.</p>
 */
class TextTaggerServiceProcessIT {

    private static Path databaseFile;

    private TextTaggerService service;

    @BeforeAll
    static void buildTinyDatabase() throws Exception {
        Assumptions.assumeTrue(binaryIsAvailable(),
                "ols_text_tagger not found on PATH -- skipping real-process coverage (see class javadoc)");

        databaseFile = Files.createTempFile("ols_text_tagger_process_it_", ".bin");
        ProcessBuilder pb = new ProcessBuilder(
                "ols_text_tagger", "build", "--output", databaseFile.toString(), "--min-len", "3");
        pb.redirectErrorStream(true);
        Process build = pb.start();
        build.getOutputStream().write((
                "ontology_id\tlabel\tiri\tstring_type\tcurated_from_source\n"
                        + "chebi\tinsulin\thttp://purl.obolibrary.org/obo/CHEBI_5931\texact\tsssom-mappings\n"
                        + "hp\tresistance\thttp://example.org/HP_0000001\t\t\n"
        ).getBytes(StandardCharsets.UTF_8));
        build.getOutputStream().close();
        String buildLog = new String(build.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = build.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("Failed to build the tiny test tagger database:\n" + buildLog);
        }
    }

    @AfterAll
    static void deleteDatabase() throws IOException {
        if (databaseFile != null) {
            Files.deleteIfExists(databaseFile);
        }
    }

    @BeforeEach
    void setUpService() {
        service = new TextTaggerService();
        ReflectionTestUtils.setField(service, "dbPath", databaseFile.toString());
    }

    @AfterEach
    void tearDownService() {
        service.destroy();
    }

    @Test
    void tagTextReturnsRealTaggingResultsThroughTheRealProcess() throws Exception {
        startProcess(service, null);

        // includeSubstrings=false exercises tagText's own removeSubstrings(...) call site (its
        // branch logic is already covered directly via reflection in TextTaggerServiceTest; this
        // proves it is also genuinely wired into the real end-to-end call path). Neither match here
        // is a substring of the other, so the result is unaffected either way.
        List<TaggedEntity> result = service.tagText("insulin resistance", null, null, null, 3, false);

        assertThat(result).extracting(e -> e.termLabel).containsExactly("insulin", "resistance");
        assertThat(result).extracting(e -> e.ontologyId).containsExactly("chebi", "hp");
        assertThat(result.get(0).start).isEqualTo(0);
        assertThat(result.get(0).end).isEqualTo(7);
        assertThat(result.get(1).start).isEqualTo(8);
        assertThat(result.get(1).end).isEqualTo(18);
    }

    @Test
    void ensureRunningRestartsTheProcessWhenDelimitersChange() throws Exception {
        startProcess(service, null);
        Process firstProcess = (Process) ReflectionTestUtils.getField(service, "process");
        long firstPid = firstProcess.pid();

        // Different delimiters than the currently-running process was started with -- tagText's
        // internal ensureRunning() call must detect the change and restart before tagging. The
        // real CLI's --delimiters flag restricts matches to occur only at those boundary
        // characters (confirmed empirically), so the input text uses "|" -- the new delimiter --
        // as its word boundary instead of a space: this proves both that a restart happened
        // (different pid) *and* that the restarted process is honouring the new delimiters, not
        // just that some process is running.
        List<TaggedEntity> result = service.tagText("insulin|resistance", null, null, "|", 3, true);

        Process secondProcess = (Process) ReflectionTestUtils.getField(service, "process");
        assertThat(secondProcess.pid()).isNotEqualTo(firstPid);
        assertThat(result).extracting(e -> e.termLabel).containsExactly("insulin", "resistance");
    }

    @Test
    void ensureRunningRestartsWhenTheProcessHasDied() throws Exception {
        startProcess(service, null);
        Process firstProcess = (Process) ReflectionTestUtils.getField(service, "process");
        long firstPid = firstProcess.pid();

        // Kill the process out from under the service, exactly as an external crash would --
        // tagText's ensureRunning() call must detect process.isAlive() == false and restart
        // rather than trying (and failing) to write to a dead process's stdin.
        firstProcess.destroyForcibly();
        firstProcess.waitFor();

        List<TaggedEntity> result = service.tagText("insulin resistance", null, null, null, 3, true);

        Process secondProcess = (Process) ReflectionTestUtils.getField(service, "process");
        assertThat(secondProcess.pid()).isNotEqualTo(firstPid);
        assertThat(secondProcess.isAlive()).isTrue();
        assertThat(result).extracting(e -> e.termLabel).containsExactly("insulin", "resistance");
    }

    @Test
    void isAvailableBecomesTrueOnlyAfterStartProcessSucceeds() throws Exception {
        assertThat(service.isAvailable()).isFalse();

        startProcess(service, null);

        assertThat(service.isAvailable()).isTrue();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static boolean binaryIsAvailable() {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            File candidate = new File(dir, "ols_text_tagger");
            if (candidate.isFile() && candidate.canExecute()) {
                return true;
            }
        }
        return false;
    }

    private static void startProcess(TextTaggerService svc, String delimiters) throws Exception {
        Method m = TextTaggerService.class.getDeclaredMethod("startProcess", String.class);
        m.setAccessible(true);
        try {
            m.invoke(svc, (Object) delimiters);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            if (e.getCause() instanceof IOException ioe) {
                throw ioe;
            }
            throw e;
        }
    }
}
