package uk.ac.ebi.spot.ols.controller.mcp;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.model.mcp.McpClass;
import uk.ac.ebi.spot.ols.model.mcp.McpPage;
import uk.ac.ebi.spot.ols.service.EmbeddingServiceClient;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres coverage of every {@link McpClassService} {@code @Tool} method. Only
 * {@code embeddingServiceClient.embedText(...)} is faked (same hand-rolled-fake idiom as
 * {@code V2LLMControllerIT}'s {@code FixedVectorEmbeddingServiceClient}) — every other
 * collaborator (real {@code EntityRepository}, real {@code ClassRepository}, real
 * {@code OlsPostgresClient}) runs against a real disposable Postgres, so search, hierarchy, and
 * embedding-backed similarity are all genuinely exercised end-to-end.
 *
 * <p>Reuses {@link PostgresIntegrationTestSupport#initializeV2LLMDatabase} /
 * {@link PostgresIntegrationTestSupport#createV2LLMRepositories} — the same fixture and factory the
 * {@code V2LLMController} baseline already established, which is a superset of every fixture this
 * class needs: the base entity fixture ({@code EFO_0001}/{@code EFO_0002}/{@code DUO_0001} classes),
 * the class hierarchy fixture ({@code EFO_1001} as a direct/hierarchical child of {@code EFO_0001},
 * {@code EFO_1999} as its obsolete sibling), and the {@code test_model} embedding fixture. It does
 * not, however, provide an {@link uk.ac.ebi.spot.ols.repository.EntityRepository} (needed by
 * {@code searchClasses}, which {@code ClassRepository}-only callers like {@code V2LLMController}
 * never touch), so this class additionally wires one directly via
 * {@link PostgresIntegrationTestSupport#createEntityRepository} against the same container —
 * composing two existing factory handles rather than adding a new one, since together they already
 * cover every collaborator {@link McpClassService} declares.</p>
 */
@Testcontainers
class McpClassServiceIT {

    private static final String EFO_0001 = "http://example.org/EFO_0001";
    private static final String EFO_0002 = "http://example.org/EFO_0002";
    private static final String DUO_0001 = "http://example.org/DUO_0001";
    private static final String EFO_0999 = "http://example.org/EFO_0999";
    private static final String EFO_1001 = "http://example.org/EFO_1001";
    private static final String EFO_1999 = "http://example.org/EFO_1999";
    private static final String MODEL = "test_model";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.V2LLMRepositoryHandle llmHandle;
    private static PostgresIntegrationTestSupport.EntityRepositoryHandle entityHandle;
    private static McpClassService service;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializeV2LLMDatabase(POSTGRES);

        llmHandle = PostgresIntegrationTestSupport.createV2LLMRepositories(POSTGRES);
        llmHandle.embeddingServiceClient().init();
        entityHandle = PostgresIntegrationTestSupport.createEntityRepository(POSTGRES);

        service = new McpClassService();
        service.entityRepository = entityHandle.repository();
        service.classRepository = llmHandle.classRepository();
        service.postgresClient = llmHandle.olsPostgresClient();
        // The only faked piece: a real embedding microservice is unavailable in this test
        // environment, so embedText() is hand-rolled to return a fixed vector, matching this
        // repo's V2LLMControllerIT idiom. Every downstream Postgres vector search is still real.
        service.embeddingServiceClient = new FixedVectorEmbeddingServiceClient(new float[] {1.0f, 0.0f, 0.0f, 0.0f});
    }

    @AfterAll
    static void closeDatabaseClients() {
        if (entityHandle != null) {
            entityHandle.close();
        }
        if (llmHandle != null) {
            llmHandle.close();
        }
    }

    // ------------------------------------------------------------------
    // searchClasses
    // ------------------------------------------------------------------

    @Test
    void searchClassesFindsActiveClassesByFreeTextAcrossOntologiesThroughRealPostgres() throws IOException {
        McpPage<McpClass> result = service.searchClasses("permission", null, null, null, null, null);

        assertThat(iris(result)).containsExactly(DUO_0001);
    }

    @Test
    void searchClassesScopesToOntologyIdAndExcludesObsoleteByDefaultThroughRealPostgres() throws IOException {
        McpPage<McpClass> result = service.searchClasses(null, "efo", null, null, null, null);

        assertThat(iris(result)).containsExactlyInAnyOrder(EFO_0001, EFO_0002, EFO_1001);
    }

    @Test
    void searchClassesCanIncludeObsoleteClassesThroughRealPostgres() throws IOException {
        McpPage<McpClass> result = service.searchClasses(null, "efo", null, null, null, true);

        assertThat(iris(result)).containsExactlyInAnyOrder(EFO_0001, EFO_0002, EFO_1001, EFO_0999, EFO_1999);
    }

    @Test
    void searchClassesReturnsFullyMappedFieldsThroughRealPostgres() throws IOException {
        McpPage<McpClass> result = service.searchClasses(null, "efo", null, null, null, null);

        McpClass liverDisease = result.items().stream()
                .filter(c -> EFO_0001.equals(c.iri))
                .findFirst()
                .orElseThrow();

        assertThat(liverDisease.curie).isEqualTo("EFO:0001");
        assertThat(liverDisease.type).contains("entity", "class");
        assertThat(liverDisease.isObsolete).isFalse();
        assertThat(liverDisease.label).containsExactly("Liver disease");
        assertThat(result.pageNum()).isZero();
        assertThat(result.pageSize()).isEqualTo(20);
    }

    // ------------------------------------------------------------------
    // getAncestors
    // ------------------------------------------------------------------

    @Test
    void getAncestorsReturnsTheRootClassForItsDirectChildThroughRealPostgres() throws IOException {
        McpPage<McpClass> result = service.getAncestors("efo", EFO_1001, null, null, null);

        assertThat(iris(result)).containsExactly(EFO_0001);
    }

    // ------------------------------------------------------------------
    // getChildren
    // ------------------------------------------------------------------

    @Test
    void getChildrenReturnsOnlyTheActiveDirectChildThroughRealPostgres() throws IOException {
        McpPage<McpClass> result = service.getChildren("efo", EFO_0001, null, null, null);

        assertThat(iris(result)).containsExactly(EFO_1001);
    }

    // ------------------------------------------------------------------
    // getDescendants
    // ------------------------------------------------------------------

    @Test
    void getDescendantsReturnsOnlyTheActiveDescendantThroughRealPostgres() throws IOException {
        McpPage<McpClass> result = service.getDescendants("efo", EFO_0001, null, null, null);

        assertThat(iris(result)).containsExactly(EFO_1001);
    }

    // ------------------------------------------------------------------
    // searchClassesWithEmbeddingModel
    // ------------------------------------------------------------------

    @Test
    void searchClassesWithEmbeddingModelFindsBothLabelAndCurationEmbeddingsThroughRealPostgres() throws IOException {
        McpPage<McpClass> result =
                service.searchClassesWithEmbeddingModel("liver", MODEL, null, null, null, null, null);

        assertThat(iris(result)).containsExactly(EFO_0001, EFO_0002);
    }

    @Test
    void searchClassesWithEmbeddingModelExcludesCurationsWhenRequestedThroughRealPostgres() throws IOException {
        McpPage<McpClass> result =
                service.searchClassesWithEmbeddingModel("liver", MODEL, null, false, null, null, null);

        assertThat(iris(result)).containsExactly(EFO_0001);
    }

    @Test
    void searchClassesWithEmbeddingModelScopesToOntologyIdThroughRealPostgres() throws IOException {
        McpPage<McpClass> result =
                service.searchClassesWithEmbeddingModel("liver", MODEL, "efo", null, null, null, null);

        assertThat(iris(result)).containsExactly(EFO_0001, EFO_0002);
    }

    @Test
    void searchClassesWithEmbeddingModelTreatsAnEmptyOntologyIdAsAbsentThroughRealPostgres() throws IOException {
        McpPage<McpClass> result =
                service.searchClassesWithEmbeddingModel("liver", MODEL, "", null, null, null, null);

        assertThat(iris(result)).containsExactly(EFO_0001, EFO_0002);
    }

    // ------------------------------------------------------------------
    // getSimilarClasses
    // ------------------------------------------------------------------

    @Test
    void getSimilarClassesRanksByExactCosineSimilarityThroughRealPostgres() {
        McpPage<McpClass> result = service.getSimilarClasses(EFO_0001, MODEL, null, null, null);

        assertThat(iris(result)).containsExactly(EFO_0002, DUO_0001);
    }

    // ------------------------------------------------------------------
    // getClassSimilarity
    // ------------------------------------------------------------------

    @Test
    void getClassSimilarityComputesExactCosineSimilarityThroughRealPostgres() {
        double similarity = service.getClassSimilarity(EFO_0001, EFO_0002, MODEL);

        assertThat(similarity).isEqualTo(0.5);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static List<String> iris(McpPage<McpClass> page) {
        return page.items().stream().map(c -> c.iri).toList();
    }

    /**
     * Hand-rolled fake (this repo's idiom; see {@code V2LLMControllerIT}) that returns a fixed
     * vector regardless of model/text, so the real Postgres nearest-neighbor search after it is
     * genuinely exercised end-to-end.
     */
    private static final class FixedVectorEmbeddingServiceClient extends EmbeddingServiceClient {
        private final float[] vector;

        private FixedVectorEmbeddingServiceClient(float[] vector) {
            this.vector = vector;
        }

        @Override
        public float[] embedText(String model, String text) throws IOException {
            return vector;
        }
    }
}
