package uk.ac.ebi.spot.ols.controller.mcp;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.model.mcp.McpPage;
import uk.ac.ebi.spot.ols.model.mcp.McpSearchResult;
import uk.ac.ebi.spot.ols.service.EmbeddingServiceClient;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres coverage of both {@link McpEmbeddingService} {@code @Tool} methods.
 *
 * <p>Reuses {@link PostgresIntegrationTestSupport#initializeV2LLMDatabase} /
 * {@link PostgresIntegrationTestSupport#createV2LLMRepositories} -- the same fixture and factory the
 * {@code V2LLMController}/{@code McpClassService} baselines already established. It provides
 * everything this class's two collaborators need: the real {@link uk.ac.ebi.spot.ols.repository
 * .postgres.OlsPostgresClient} (base entity fixture -- {@code EFO_0001}/{@code EFO_0002} classes,
 * {@code EFO_0100} property -- plus the class fixture's {@code EFO_I100} individual, and the
 * {@code test_model} embedding fixture on both {@code ols_entities} and
 * {@code ols_embedding_nodes}) and the real {@link EmbeddingServiceClient} bean.</p>
 *
 * <p><b>Mock/real boundary.</b> {@code listEmbeddingModels()} needs no fake at all for one of its two
 * cases: the real, unconfigured {@code EmbeddingServiceClient} bean genuinely degrades
 * {@code getAvailableModels()} to {@code List.of()} (no embedding-service URL is ever configured in
 * this test environment), which is exercised for real below to prove the "service unconfigured -&gt;
 * every Postgres model shows can_embed=false" contract with no fake standing in for it. A second
 * case additionally proves the can_embed=true path against real Postgres, using a hand-rolled fake
 * that overrides only {@code getAvailableModels()} (a real embedding microservice advertising a
 * specific model is not reachable in this test environment, so that one piece is faked; everything
 * downstream -- the real {@code OlsPostgresClient.getEmbeddingModels()} introspection of
 * {@code information_schema.columns} -- is genuine). {@code searchWithEmbeddingModel(...)} fakes only
 * {@code embeddingServiceClient.embedText(...)}, the same hand-rolled-fake idiom as
 * {@code McpClassServiceIT}'s {@code FixedVectorEmbeddingServiceClient} -- every subsequent Postgres
 * nearest-neighbor search is genuine.</p>
 */
@Testcontainers
class McpEmbeddingServiceIT {

    private static final String MODEL = "test_model";
    private static final String EFO_0001 = "http://example.org/EFO_0001"; // class, LabelEmbedding
    private static final String EFO_0002 = "http://example.org/EFO_0002"; // class, CurationEmbedding only
    private static final String EFO_0100 = "http://example.org/EFO_0100"; // property, LabelEmbedding
    private static final String EFO_I100 = "http://example.org/EFO_I100"; // individual, LabelEmbedding

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.V2LLMRepositoryHandle llmHandle;

    /** Real Postgres, embedText faked to a fixed vector -- used for searchWithEmbeddingModel. */
    private static McpEmbeddingService service;

    /** Real Postgres, the real unconfigured EmbeddingServiceClient bean -- used for listEmbeddingModels'
     * degrade-gracefully case. */
    private static McpEmbeddingService serviceWithRealEmbeddingClient;

    /** Real Postgres, embedding-service model list faked -- used for listEmbeddingModels' can_embed=true
     * case (a real embedding microservice advertising "test_model" is unreachable here). */
    private static McpEmbeddingService serviceWithAvailableModel;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializeV2LLMDatabase(POSTGRES);

        llmHandle = PostgresIntegrationTestSupport.createV2LLMRepositories(POSTGRES);
        llmHandle.embeddingServiceClient().init();

        service = new McpEmbeddingService();
        service.postgresClient = llmHandle.olsPostgresClient();
        service.embeddingServiceClient =
                new FixedVectorEmbeddingServiceClient(new float[] {1.0f, 0.0f, 0.0f, 0.0f});

        serviceWithRealEmbeddingClient = new McpEmbeddingService();
        serviceWithRealEmbeddingClient.postgresClient = llmHandle.olsPostgresClient();
        serviceWithRealEmbeddingClient.embeddingServiceClient = llmHandle.embeddingServiceClient();

        serviceWithAvailableModel = new McpEmbeddingService();
        serviceWithAvailableModel.postgresClient = llmHandle.olsPostgresClient();
        serviceWithAvailableModel.embeddingServiceClient =
                new FixedAvailableModelsEmbeddingServiceClient(List.of(MODEL));
    }

    @AfterAll
    static void closeDatabaseClients() {
        if (llmHandle != null) {
            llmHandle.close();
        }
    }

    // ------------------------------------------------------------------
    // listEmbeddingModels
    // ------------------------------------------------------------------

    @Test
    void listEmbeddingModelsMarksThePostgresModelUnableToEmbedWhenTheRealEmbeddingServiceIsUnconfigured() {
        // Genuine integration coverage of the degrade-gracefully contract: no fake stands in for
        // EmbeddingServiceClient here at all, it is the real bean with no URL configured.
        List<Map<String, Object>> result = serviceWithRealEmbeddingClient.listEmbeddingModels();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("model", MODEL).containsEntry("can_embed", false);
    }

    @Test
    void listEmbeddingModelsMarksThePostgresModelAbleToEmbedWhenTheEmbeddingServiceOffersItThroughRealPostgres() {
        List<Map<String, Object>> result = serviceWithAvailableModel.listEmbeddingModels();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("model", MODEL).containsEntry("can_embed", true);
    }

    // ------------------------------------------------------------------
    // searchWithEmbeddingModel
    // ------------------------------------------------------------------

    @Test
    void searchWithEmbeddingModelSearchesAcrossEveryEntityTypeNotJustClassesThroughRealPostgres() throws IOException {
        // Same fixture and same fixed query vector [1,0,0,0] that McpClassServiceIT's
        // searchClassesWithEmbeddingModelFindsBothLabelAndCurationEmbeddingsThroughRealPostgres uses
        // with "OntologyClass" (which finds only EFO_0001/EFO_0002). Here, "OntologyEntity" disables
        // the type filter entirely (see OlsPostgresClient.hasConcreteEntityType), so the property
        // (EFO_0100) and individual (EFO_I100) candidates are found too -- this is the concrete,
        // observable proof of the "OntologyClass" vs "OntologyEntity" difference.
        McpPage<McpSearchResult> result = service.searchWithEmbeddingModel("liver", MODEL, null, null, null, null);

        // Ranked by exact cosine similarity against [1,0,0,0]: EFO_0001 (1.0) > EFO_I100 (0.8) >
        // EFO_0100 (0.6) > EFO_0002 (0.0, CurationEmbedding only) -- see
        // PostgresIntegrationTestSupport.loadV2LlmEmbeddingFixture's own worked arithmetic.
        assertThat(urls(result)).containsExactly(EFO_0001, EFO_I100, EFO_0100, EFO_0002);
    }

    @Test
    void searchWithEmbeddingModelExcludesCurationsWhenRequestedThroughRealPostgres() throws IOException {
        McpPage<McpSearchResult> result = service.searchWithEmbeddingModel("liver", MODEL, null, false, null, null);

        // EFO_0002 is reachable only via its CurationEmbedding row (test-emb-2); excluding curations
        // drops it, leaving the three LabelEmbedding candidates in similarity order.
        assertThat(urls(result)).containsExactly(EFO_0001, EFO_I100, EFO_0100);
    }

    @Test
    void searchWithEmbeddingModelScopesToOntologyIdThroughRealPostgres() throws IOException {
        // Every candidate in this fixture already belongs to "efo" (there is no non-efo row in
        // ols_embedding_nodes for this model), so scoping to "efo" genuinely exercises
        // searchByVectorInOntology's real Postgres query and returns the identical set -- the same
        // characteristic McpClassServiceIT's own ontology-scoped case already has.
        McpPage<McpSearchResult> result =
                service.searchWithEmbeddingModel("liver", MODEL, "efo", null, null, null);

        assertThat(urls(result)).containsExactly(EFO_0001, EFO_I100, EFO_0100, EFO_0002);
    }

    @Test
    void searchWithEmbeddingModelTreatsAnEmptyOntologyIdAsAbsentThroughRealPostgres() throws IOException {
        McpPage<McpSearchResult> result = service.searchWithEmbeddingModel("liver", MODEL, "", null, null, null);

        assertThat(urls(result)).containsExactly(EFO_0001, EFO_I100, EFO_0100, EFO_0002);
    }

    @Test
    void searchWithEmbeddingModelReturnsFullyMappedFieldsThroughRealPostgres() throws IOException {
        McpPage<McpSearchResult> result = service.searchWithEmbeddingModel("liver", MODEL, null, null, null, null);

        McpSearchResult top = result.items().get(0);
        assertThat(top.url).isEqualTo(EFO_0001);
        assertThat(top.id).isEqualTo("efo+" + EFO_0001);
        assertThat(top.title).isEqualTo("EFO:0001 Liver disease");
        assertThat(top.isObsolete).isFalse();
        assertThat(result.pageNum()).isZero();
        assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(4);
    }

    @Test
    void searchWithEmbeddingModelMapsThePropertyAndIndividualResultsFieldsThroughRealPostgresToo()
            throws IOException {
        McpPage<McpSearchResult> result = service.searchWithEmbeddingModel("liver", MODEL, null, null, null, null);

        McpSearchResult property = result.items().stream().filter(r -> EFO_0100.equals(r.url)).findFirst()
                .orElseThrow();
        assertThat(property.title).isEqualTo("EFO:0100 has specimen");

        McpSearchResult individual = result.items().stream().filter(r -> EFO_I100.equals(r.url)).findFirst()
                .orElseThrow();
        assertThat(individual.title).isEqualTo("EFO:I100 Example liver individual");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static List<String> urls(McpPage<McpSearchResult> page) {
        return page.items().stream().map(r -> r.url).toList();
    }

    /**
     * Hand-rolled fake (this repo's idiom; see {@code McpClassServiceIT}) that returns a fixed
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

    /**
     * Hand-rolled fake that overrides only {@code getAvailableModels()} -- a real embedding
     * microservice advertising a specific model is unreachable in this test environment, so this is
     * the one piece faked to prove listEmbeddingModels' can_embed=true path against real Postgres.
     */
    private static final class FixedAvailableModelsEmbeddingServiceClient extends EmbeddingServiceClient {
        private final List<String> models;

        private FixedAvailableModelsEmbeddingServiceClient(List<String> models) {
            this.models = models;
        }

        @Override
        public List<String> getAvailableModels() {
            return models;
        }
    }
}
