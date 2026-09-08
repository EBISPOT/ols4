package uk.ac.ebi.spot.ols.controller.api.v2;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.service.EmbeddingServiceClient;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.io.IOException;
import java.net.URI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin full-stack controller-IT coverage for every {@link V2LLMController} route against a real
 * disposable Postgres. See "Implemented V2 LLM-controller baseline" in
 * {@code docs/backend-testing-strategy.md} for the full mock/real boundary; summarized here:
 *
 * <ul>
 *   <li>The six raw-vector/lookup routes (both {@code llm_embedding} routes, {@code llm_similar} x2,
 *       {@code llm_embedding} GET, {@code llm_similarity}) need no {@link EmbeddingServiceClient}
 *       involvement at all and run against {@link #mockMvc}, wired with the real
 *       {@code ClassRepository}/{@code PropertyRepository}/{@code OlsPostgresClient} stack.</li>
 *   <li>{@code GET /llm_models} needs the real, <em>unconfigured</em> {@code EmbeddingServiceClient}
 *       bean (see {@link #mockMvcWithRealEmbeddingClient}): {@code getAvailableModels()} degrades to
 *       an empty list without throwing, which is itself the genuine, correct integration behaviour
 *       for a deployment with no embedding microservice configured.</li>
 *   <li>The five text-search routes call {@code embeddingServiceClient.embedText(...)} before doing a
 *       real Postgres vector search. They run against {@link #mockMvc}, which wires a hand-rolled
 *       fake {@code EmbeddingServiceClient} subclass (same idiom as
 *       {@code HealthCheckControllerTest}/{@code V2TextTaggerControllerTest}) that returns a fixed
 *       canned vector, so the real nearest-neighbor Postgres search after it is genuinely exercised
 *       end-to-end. One of the five ({@code /entities/llm_search}) additionally gets a second case
 *       against the real, unconfigured bean on {@link #mockMvcWithRealEmbeddingClient}, proving the
 *       real error-propagation behaviour empirically rather than asserting it from reading the source
 *       alone — the other four routes' identical unavailable-branch behaviour is already fully proven
 *       at the unit/WIT layer.</li>
 * </ul>
 */
@Testcontainers
class V2LLMControllerIT {

    private static final String EFO_0001 = "http://example.org/EFO_0001";
    private static final String EFO_0002 = "http://example.org/EFO_0002";
    private static final String DUO_0001 = "http://example.org/DUO_0001";
    private static final String EFO_0100 = "http://example.org/EFO_0100";
    private static final String EFO_0101 = "http://example.org/EFO_0101";
    private static final String EFO_I100 = "http://example.org/EFO_I100";
    private static final String MODEL = "test_model";

    // Query vector used by the fake EmbeddingServiceClient: chosen to exactly equal the fixture's
    // EFO_0001/EFO_0100/EFO_I100 LabelEmbedding vectors' direction along the first axis, giving
    // hand-checkable cosine similarities (see PostgresIntegrationTestSupport.loadV2LlmEmbeddingFixture
    // for the full worked fixture and docs/backend-testing-strategy.md for the arithmetic).
    private static final String QUERY_VECTOR_JSON = "[1.0,0.0,0.0,0.0]";

    private static final URI CLASS_EMBEDDING_URI =
            uri("/api/v2/classes/http%253A%252F%252Fexample.org%252FEFO_0001/llm_embedding");
    private static final URI CLASS_SIMILAR_URI =
            uri("/api/v2/classes/http%253A%252F%252Fexample.org%252FEFO_0001/llm_similar");
    private static final URI CLASS_SIMILARITY_URI = uri(
            "/api/v2/classes/http%253A%252F%252Fexample.org%252FEFO_0001"
                    + "/llm_similarity/http%253A%252F%252Fexample.org%252FEFO_0002");
    private static final URI PROPERTY_SIMILAR_URI =
            uri("/api/v2/properties/http%253A%252F%252Fexample.org%252FEFO_0100/llm_similar");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.V2LLMRepositoryHandle repositoryHandle;

    /** Wired with a hand-rolled fake {@code EmbeddingServiceClient} for the five text-search routes. */
    private static MockMvc mockMvc;

    /** Wired with the real, unconfigured {@code EmbeddingServiceClient} bean. */
    private static MockMvc mockMvcWithRealEmbeddingClient;

    @BeforeAll
    static void setUpApplicationPath() {
        PostgresIntegrationTestSupport.initializeV2LLMDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createV2LLMRepositories(POSTGRES);
        repositoryHandle.embeddingServiceClient().init();

        PageableHandlerMethodArgumentResolver pageableResolver = new PageableHandlerMethodArgumentResolver();
        pageableResolver.setMaxPageSize(1000);

        V2LLMController controllerWithFakeEmbeddingClient = new V2LLMController();
        controllerWithFakeEmbeddingClient.classRepository = repositoryHandle.classRepository();
        controllerWithFakeEmbeddingClient.propertyRepository = repositoryHandle.propertyRepository();
        controllerWithFakeEmbeddingClient.postgresClient = repositoryHandle.olsPostgresClient();
        controllerWithFakeEmbeddingClient.embeddingServiceClient =
                new FixedVectorEmbeddingServiceClient(new float[] {1.0f, 0.0f, 0.0f, 0.0f});
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controllerWithFakeEmbeddingClient)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(pageableResolver)
                .build();

        V2LLMController controllerWithRealEmbeddingClient = new V2LLMController();
        controllerWithRealEmbeddingClient.classRepository = repositoryHandle.classRepository();
        controllerWithRealEmbeddingClient.propertyRepository = repositoryHandle.propertyRepository();
        controllerWithRealEmbeddingClient.postgresClient = repositoryHandle.olsPostgresClient();
        controllerWithRealEmbeddingClient.embeddingServiceClient = repositoryHandle.embeddingServiceClient();
        mockMvcWithRealEmbeddingClient = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controllerWithRealEmbeddingClient)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(pageableResolver)
                .build();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    // ------------------------------------------------------------------
    // GET /llm_models -- real, unconfigured EmbeddingServiceClient + real Postgres
    // ------------------------------------------------------------------

    @Test
    void llmModelsListsThePostgresModelWithCanEmbedFalseThroughTheRealUnconfiguredService() throws Exception {
        // No other fixture in this suite adds an embeddings_<model> column, and each IT class gets
        // its own fresh disposable Postgres, so MODEL is the only model getEmbeddingModels() finds.
        mockMvcWithRealEmbeddingClient.perform(get("/api/v2/llm_models"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].model").value(MODEL))
                .andExpect(jsonPath("$[0].can_embed").value(false));
    }

    // ------------------------------------------------------------------
    // POST /classes/llm_embedding -- real Postgres only
    // ------------------------------------------------------------------

    @Test
    void searchClassesByVectorFindsBothLabelAndCurationEmbeddingsThroughRealPostgres() throws Exception {
        mockMvc.perform(post("/api/v2/classes/llm_embedding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUERY_VECTOR_JSON)
                        .param("model", MODEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(2))
                .andExpect(jsonPath("$.elements[0].iri").value(EFO_0001))
                .andExpect(jsonPath("$.elements[0].score").value(1.0))
                .andExpect(jsonPath("$.elements[1].iri").value(EFO_0002))
                .andExpect(jsonPath("$.elements[1].score").value(0.5));
    }

    @Test
    void searchClassesByVectorExcludesCurationsWhenRequested() throws Exception {
        mockMvc.perform(post("/api/v2/classes/llm_embedding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUERY_VECTOR_JSON)
                        .param("model", MODEL)
                        .param("includeCurations", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri").value(EFO_0001));
    }

    // ------------------------------------------------------------------
    // POST /ontologies/{onto}/classes/llm_embedding -- real Postgres only
    // ------------------------------------------------------------------

    @Test
    void searchClassesByVectorInOntologyScopesResultsToTheGivenOntologyThroughRealPostgres() throws Exception {
        mockMvc.perform(post("/api/v2/ontologies/efo/classes/llm_embedding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUERY_VECTOR_JSON)
                        .param("model", MODEL)
                        .param("isDefiningOntology", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(2))
                .andExpect(jsonPath("$.elements[0].iri").value(EFO_0001))
                .andExpect(jsonPath("$.elements[1].iri").value(EFO_0002));
    }

    // ------------------------------------------------------------------
    // GET /entities/llm_search -- fake EmbeddingServiceClient + real Postgres search across all types
    // ------------------------------------------------------------------

    @Test
    void searchEntitiesByTextFindsResultsAcrossEveryEntityTypeThroughRealPostgres() throws Exception {
        mockMvc.perform(get("/api/v2/entities/llm_search")
                        .param("q", "liver")
                        .param("model", MODEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(4))
                .andExpect(jsonPath("$.elements[0].iri").value(EFO_0001))
                .andExpect(jsonPath("$.elements[0].score").value(1.0))
                .andExpect(jsonPath("$.elements[1].iri").value(EFO_I100))
                .andExpect(jsonPath("$.elements[1].score").value(0.9))
                .andExpect(jsonPath("$.elements[2].iri").value(EFO_0100))
                .andExpect(jsonPath("$.elements[2].score").value(0.8))
                .andExpect(jsonPath("$.elements[3].iri").value(EFO_0002))
                .andExpect(jsonPath("$.elements[3].score").value(0.5));
    }

    @Test
    void searchEntitiesByTextPropagatesTheRealUnconfiguredEmbeddingServiceFailure() throws Exception {
        // Representative real-unconfigured-EmbeddingServiceClient error-propagation case (the handoff
        // scoping only requires one of the five text-search routes to carry this case): embedText()
        // throws IOException("Embedding service URL is not configured") against the real unconfigured
        // bean; V2LLMController declares `throws IOException` and does not catch it, so it reaches
        // GlobalExceptionHandler's catch-all, which has no bespoke IOException branch and falls through
        // to HTTP 500 with the exception's own message -- confirmed empirically by this test, not
        // assumed from reading the source.
        mockMvcWithRealEmbeddingClient.perform(get("/api/v2/entities/llm_search")
                        .param("q", "liver")
                        .param("model", MODEL))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Embedding service URL is not configured"));
    }

    // ------------------------------------------------------------------
    // GET /classes/llm_search -- fake EmbeddingServiceClient + real Postgres
    // ------------------------------------------------------------------

    @Test
    void searchClassesByTextFindsBothLabelAndCurationEmbeddingsThroughRealPostgres() throws Exception {
        mockMvc.perform(get("/api/v2/classes/llm_search")
                        .param("q", "liver disease")
                        .param("model", MODEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(2))
                .andExpect(jsonPath("$.elements[0].iri").value(EFO_0001))
                .andExpect(jsonPath("$.elements[1].iri").value(EFO_0002));
    }

    // ------------------------------------------------------------------
    // GET /ontologies/{onto}/classes/llm_search -- fake EmbeddingServiceClient + real Postgres
    // ------------------------------------------------------------------

    @Test
    void searchClassesByTextInOntologyScopesResultsToTheGivenOntologyThroughRealPostgres() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/classes/llm_search")
                        .param("q", "liver disease")
                        .param("model", MODEL)
                        .param("isDefiningOntology", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(2))
                .andExpect(jsonPath("$.elements[0].iri").value(EFO_0001));
    }

    // ------------------------------------------------------------------
    // GET /classes/{class}/llm_similar -- real Postgres only
    // ------------------------------------------------------------------

    @Test
    void getSimilarClassesRanksByExactCosineSimilarityThroughRealPostgres() throws Exception {
        mockMvc.perform(get(CLASS_SIMILAR_URI).param("model", MODEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(2))
                .andExpect(jsonPath("$.elements[0].iri").value(EFO_0002))
                .andExpect(jsonPath("$.elements[0].score").value(0.5))
                .andExpect(jsonPath("$.elements[1].iri").value(DUO_0001))
                .andExpect(jsonPath("$.elements[1].score").value(0.0));
    }

    // ------------------------------------------------------------------
    // GET /classes/{class}/llm_embedding -- real Postgres only
    // ------------------------------------------------------------------

    @Test
    void getClassEmbeddingReturnsTheStoredVectorThroughRealPostgres() throws Exception {
        mockMvc.perform(get(CLASS_EMBEDDING_URI).param("model", MODEL))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("[1.0,0.0,0.0,0.0]"));
    }

    // ------------------------------------------------------------------
    // GET /classes/{class}/llm_similarity/{otherclass} -- real Postgres only
    // ------------------------------------------------------------------

    @Test
    void getClassSimilarityComputesExactCosineSimilarityThroughRealPostgres() throws Exception {
        mockMvc.perform(get(CLASS_SIMILARITY_URI).param("model", MODEL))
                .andExpect(status().isOk())
                .andExpect(content().string("0.5"));
    }

    // ------------------------------------------------------------------
    // GET /properties/llm_search -- fake EmbeddingServiceClient + real Postgres
    // ------------------------------------------------------------------

    @Test
    void searchPropertiesByTextFindsTheLabelEmbeddingThroughRealPostgres() throws Exception {
        mockMvc.perform(get("/api/v2/properties/llm_search")
                        .param("q", "specimen")
                        .param("model", MODEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri").value(EFO_0100))
                .andExpect(jsonPath("$.elements[0].score").value(0.8));
    }

    // ------------------------------------------------------------------
    // GET /individuals/llm_search -- fake EmbeddingServiceClient + real Postgres
    // ------------------------------------------------------------------

    @Test
    void searchIndividualsByTextFindsTheLabelEmbeddingThroughRealPostgres() throws Exception {
        mockMvc.perform(get("/api/v2/individuals/llm_search")
                        .param("q", "liver specimen")
                        .param("model", MODEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri").value(EFO_I100))
                .andExpect(jsonPath("$.elements[0].score").value(0.9));
    }

    // ------------------------------------------------------------------
    // GET /properties/{property}/llm_similar -- real Postgres only
    // ------------------------------------------------------------------

    @Test
    void getSimilarPropertiesRanksByExactCosineSimilarityThroughRealPostgres() throws Exception {
        mockMvc.perform(get(PROPERTY_SIMILAR_URI).param("model", MODEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri").value(EFO_0101))
                .andExpect(jsonPath("$.elements[0].score").value(0.5));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static URI uri(String value) {
        return URI.create(value);
    }

    /**
     * Hand-rolled fake (this repo's idiom; see {@code HealthCheckControllerTest}/
     * {@code V2TextTaggerControllerTest}) that returns a fixed vector regardless of model/text, so the
     * real Postgres nearest-neighbor search after it is genuinely exercised end-to-end.
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
