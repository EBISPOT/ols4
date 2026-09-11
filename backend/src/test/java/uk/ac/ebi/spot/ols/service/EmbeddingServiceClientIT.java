package uk.ac.ebi.spot.ols.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport.EmbeddingServiceClientRepositoryHandle;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Real-Postgres coverage of {@link EmbeddingServiceClient#init()}/{@code loadPcaModels()} — the one
 * part of this class that is genuinely Postgres-backed (see
 * {@code EmbeddingServiceClientTest}'s class-level Javadoc for the HTTP-seam-based unit coverage of
 * everything else: {@code applyPca}, response parsing, and {@code getAvailableModels()} filtering,
 * all of which are pure logic once a {@code PcaModel} is present and need no database).
 *
 * <p>{@link PostgresIntegrationTestSupport#initializeEmbeddingServiceClientDatabase} loads two rows
 * into {@code ols_pca_models}: {@code embedding_service_client_test_model_pca2} (matches
 * {@code PCA_PATTERN}) and {@code embedding_service_client_test_model_full} (does not). This class
 * proves both the regex-match and regex-no-match branches of {@code loadPcaModels()}'s per-row loop
 * by reflecting into the resulting private {@code pcaModels} map after a single real
 * {@code init()} call — there is no public getter, so a small, explicit white-box read is used
 * rather than duplicating one in production code purely for testability.
 */
class EmbeddingServiceClientIT {

    private static PostgreSQLContainer<?> container;
    private static EmbeddingServiceClientRepositoryHandle handle;

    @BeforeAll
    static void setUpDatabase() {
        container = PostgresIntegrationTestSupport.newContainer();
        container.start();
        PostgresIntegrationTestSupport.initializeEmbeddingServiceClientDatabase(container);
        handle = PostgresIntegrationTestSupport.createEmbeddingServiceClientRepositories(container);
    }

    @AfterAll
    static void tearDownDatabase() {
        if (handle != null) {
            handle.close();
        }
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void loadsPcaModelMatchingTheNamingPattern() throws Exception {
        Object pcaModel = pcaModelFor("embedding_service_client_test_model_pca2");
        assertNotNull(pcaModel, "expected a loaded PcaModel for the _pca2-suffixed fixture row");

        assertEquals("embedding_service_client_test_model", readPrivateField(pcaModel, "baseModelName"));
        assertEquals(2, readPrivateField(pcaModel, "nComponents"));
        assertEquals(3, ((double[]) readPrivateField(pcaModel, "mean")).length);
    }

    @Test
    void skipsRowsWhoseNameDoesNotMatchTheNamingPattern() throws Exception {
        assertNull(
                pcaModelFor("embedding_service_client_test_model_full"),
                "a name without a _pca<digits> suffix must never become a loaded PcaModel");

        assertEquals(
                1, pcaModels().size(),
                "only the one matching fixture row should have produced a loaded PCA model");
    }

    /**
     * {@code loadPcaModels()} wraps its entire query+parse loop in one {@code try/catch(Exception)}
     * (not a per-row one), so a row whose {@code model} bytes are not valid JSON aborts the whole
     * load. Proves that failure is caught and logged rather than propagating out of
     * {@code @PostConstruct init()} -- which would otherwise fail application startup -- and that no
     * partial/corrupt model is left in the map. Uses its own disposable container, started and
     * stopped within the test, rather than the class-shared one: poisoning the shared container's
     * single valid {@code _pca2} row would break the two tests above.
     */
    @Test
    void initSwallowsAMalformedModelRowWithoutThrowing() throws Exception {
        PostgreSQLContainer<?> brokenContainer = PostgresIntegrationTestSupport.newContainer();
        brokenContainer.start();
        try {
            PostgresIntegrationTestSupport.initializeDatabase(brokenContainer);
            try (Connection connection = brokenContainer.createConnection("");
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO ols_pca_models (name, model) VALUES (?, ?)")) {
                statement.setString(1, "broken_model_pca4");
                statement.setBytes(2, "not valid json".getBytes(StandardCharsets.UTF_8));
                statement.executeUpdate();
            }

            // createEmbeddingServiceClientRepositories() calls init() -> loadPcaModels() internally;
            // reaching the assertion below at all (no exception propagated up through this test)
            // already proves the malformed row's Gson parse failure was caught, not thrown.
            EmbeddingServiceClientRepositoryHandle brokenHandle =
                    PostgresIntegrationTestSupport.createEmbeddingServiceClientRepositories(brokenContainer);
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> pcaModels = (Map<String, Object>)
                        readPrivateField(brokenHandle.embeddingServiceClient(), "pcaModels");
                assertEquals(0, pcaModels.size(), "a malformed row must leave no model loaded");
            } finally {
                brokenHandle.close();
            }
        } finally {
            brokenContainer.stop();
        }
    }

    private static Object pcaModelFor(String name) throws ReflectiveOperationException {
        return pcaModels().get(name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pcaModels() throws ReflectiveOperationException {
        return (Map<String, Object>) readPrivateField(handle.embeddingServiceClient(), "pcaModels");
    }

    private static Object readPrivateField(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
