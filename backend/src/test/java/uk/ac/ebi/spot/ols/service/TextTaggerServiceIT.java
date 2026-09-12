package uk.ac.ebi.spot.ols.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres coverage for {@link TextTaggerService#downloadTextTaggerDb()} -- the Large-Object
 * download logic behind the service's {@code @PostConstruct init()}. The method is {@code
 * private}, so it is invoked directly via reflection against disposable Postgres, reusing
 * {@link PostgresIntegrationTestSupport#createTextTaggerRepositories}.
 *
 * <p>{@code V2TextTaggerControllerIT} already proves the no-row/{@code isAvailable() == false}
 * path indirectly, through the whole controller. This class targets {@code
 * downloadTextTaggerDb()} directly and, in addition, exercises the previously-untested populated
 * path: a real gzip-compressed Postgres Large Object inserted via the real {@code
 * LargeObjectManager} API (see {@link PostgresIntegrationTestSupport#insertTextTaggerLargeObject}),
 * matching production's own write-side contract, with the returned temp file's decompressed
 * content asserted byte-for-byte against what was inserted.
 *
 * <p>{@code @TestMethodOrder} is a deliberate, documented exception to this repo's usual
 * per-class-container idiom: both tests share one static container, and {@code
 * downloadTextTaggerDb()}'s query is {@code LIMIT 1} against a table that may hold at most the one
 * row the second test inserts (there is no delete/reset between tests). The empty-table case must
 * therefore run first, before that row exists.</p>
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TextTaggerServiceIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.TextTaggerRepositoryHandle handle;

    @BeforeAll
    static void setUp() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        handle = PostgresIntegrationTestSupport.createTextTaggerRepositories(POSTGRES);
    }

    @AfterAll
    static void tearDown() {
        handle.close();
    }

    @Test
    @Order(1)
    void downloadTextTaggerDbReturnsNullWhenTheTableHasNoRow() throws Exception {
        // This is almost certainly the only case V2TextTaggerControllerIT's fixture ever exercises
        // (confirmed by reading that fixture: no test anywhere in the committed suite inserts a
        // row into ols_text_tagger) -- here it is proven directly against downloadTextTaggerDb()
        // itself, rather than only inferred from isAvailable() == false at the controller layer.
        Path result = downloadTextTaggerDb(handle.textTaggerService());

        assertThat(result).isNull();
    }

    @Test
    @Order(2)
    void downloadTextTaggerDbReturnsDecompressedContentMatchingTheStoredLargeObject() throws Exception {
        String expectedContent = "{\"entities\":[{\"start\":0,\"end\":7,\"term_label\":\"insulin\","
                + "\"term_iri\":\"http://purl.obolibrary.org/obo/CHEBI_5931\",\"ontology_id\":\"chebi\"}]}\n";
        PostgresIntegrationTestSupport.insertTextTaggerLargeObject(handle.postgresClient(), expectedContent);

        Path result = downloadTextTaggerDb(handle.textTaggerService());

        try {
            assertThat(result).isNotNull();
            assertThat(Files.exists(result)).isTrue();
            String actualContent = Files.readString(result, StandardCharsets.UTF_8);
            assertThat(actualContent).isEqualTo(expectedContent);
        } finally {
            if (result != null) {
                Files.deleteIfExists(result);
            }
        }
    }

    private static Path downloadTextTaggerDb(TextTaggerService service) throws Exception {
        Method method = TextTaggerService.class.getDeclaredMethod("downloadTextTaggerDb");
        method.setAccessible(true);
        try {
            return (Path) method.invoke(service);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }
}
