package uk.ac.ebi.spot.ols.repository.postgres;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport.OlsPostgresClientRepositoryHandle;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport.OLS_POSTGRES_CLIENT_EMBEDDING_MODEL;

/**
 * Vector search with {@code includeObsoleteEntities} on and off, on every route a search can take
 * through {@link OlsPostgresClient}, against real pgvector with the production indexes.
 *
 * <p>In this fixture ({@link PostgresIntegrationTestSupport#initializeOlsPostgresClientObsoleteVectorSearchDatabase})
 * the rows nearest the query are all obsolete, so what is being tested is that the page still fills
 * with what is behind them, not only that obsolete entities are gone.
 */
class OlsPostgresClientObsoleteVectorSearchIT {

    private static final String LIVE_A = "obsdefining+obsclass+http://example.org/LIVE_A";
    private static final String LIVE_B = "obsdefining+obsclass+http://example.org/LIVE_B";
    private static final String LIVE_P = "obsdefining+obsproperty+http://example.org/LIVE_P";
    private static final String SYN = "obsdefining+obssyn+http://example.org/SYN";
    private static final String SYN_A = "obsdefining+obssyn+http://example.org/SYN_A";
    private static final String SYN_B = "obsdefining+obssyn+http://example.org/SYN_B";
    private static final String ZERO = "obsdefining+obszero+http://example.org/ZERO";
    private static final String OBS_CURATED = "obsdefining+obsclass+http://example.org/OBS_CURATED";
    private static final String OBS_PREFIX = "obsdefining+obsclass+http://example.org/OBS_";
    private static final String IMPORTED_LIVE_A = "obsimporter+obsclass+http://example.org/LIVE_A";
    private static final String IMPORTED_LIVE_B = "obsimporter+obsclass+http://example.org/LIVE_B";
    private static final String IMPORTED_OBS_1 = "obsimporter+obsclass+http://example.org/OBS_1";

    private static final List<Double> QUERY = List.of(1.0, 0.0, 0.0, 0.0);
    private static final List<Double> OPPOSITE_QUERY = List.of(-1.0, 0.0, 0.0, 0.0);

    private static final int EXACT_SCAN = Integer.MAX_VALUE;
    private static final int INDEX_SCAN = 0;

    private static PostgreSQLContainer<?> container;
    private static OlsPostgresClientRepositoryHandle handle;
    private static OlsPostgresClient client;

    @BeforeAll
    static void setUpDatabase() {
        container = PostgresIntegrationTestSupport.newContainer();
        container.start();
        PostgresIntegrationTestSupport.initializeOlsPostgresClientObsoleteVectorSearchDatabase(container);
        handle = PostgresIntegrationTestSupport.createOlsPostgresClientRepositories(container);
        client = handle.olsPostgresClient();
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

    @AfterEach
    void forgetTheFakedPgvectorVersion() {
        ReflectionTestUtils.setField(client, "hnswIterativeScans", null);
    }

    private static Page<JsonElement> searchEverywhere(String type, int pageSize, boolean includeCurations,
            boolean includeObsoleteEntities) {
        return client.searchByVector(type, QUERY, PageRequest.of(0, pageSize),
                OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, includeCurations, includeObsoleteEntities);
    }

    private static Page<JsonElement> searchOntology(int exactScanMaxVectors, String type, int pageSize,
            boolean includeCurations, boolean includeObsoleteEntities) {
        client.exactScanMaxVectors = exactScanMaxVectors;
        return client.searchByVectorInOntology(type, QUERY, PageRequest.of(0, pageSize),
                OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, "obsdefining", true, includeCurations, includeObsoleteEntities);
    }

    private static List<String> ids(Page<JsonElement> page) {
        return page.getContent().stream()
                .map(e -> e.getAsJsonObject().get("id").getAsString())
                .toList();
    }

    // ------------------------------------------------------------------
    // Across all ontologies
    // ------------------------------------------------------------------

    @Test
    void aSearchEverywhereFillsThePageFromBehindTheObsoleteRowsNearestTheQuery() {
        assertThat(ids(searchEverywhere("OntologyEntity", 4, true, false)))
                .containsExactly(LIVE_A, LIVE_P, LIVE_B, SYN_A);
    }

    @Test
    void aSearchEverywhereReturnsObsoleteEntitiesWhenAsked() {
        List<String> found = ids(searchEverywhere("OntologyEntity", 4, true, true));

        assertThat(found).hasSize(4).first().isEqualTo(OBS_CURATED);
        assertThat(found.subList(1, 4)).allMatch(id -> id.startsWith(OBS_PREFIX));
    }

    @Test
    void aSearchEverywhereForOneTypeLeavesOutObsoleteEntitiesOfThatType() {
        assertThat(ids(searchEverywhere("ObsClass", 10, true, false))).containsExactly(LIVE_A, LIVE_B);
    }

    @Test
    void anObsoleteEntityFoundOnlyByItsCurationIsLeftOutToo() {
        assertThat(ids(searchEverywhere("ObsClass", 1, true, true))).containsExactly(OBS_CURATED);
        assertThat(ids(searchEverywhere("ObsClass", 1, false, true))).first().asString().startsWith(OBS_PREFIX);
        assertThat(ids(searchEverywhere("ObsClass", 1, true, false))).containsExactly(LIVE_A);
    }

    @Test
    void theOverloadsThatDoNotSayLeaveObsoleteEntitiesOut() {
        assertThat(ids(client.searchByVector("ObsClass", QUERY, PageRequest.of(0, 10), OLS_POSTGRES_CLIENT_EMBEDDING_MODEL)))
                .containsExactly(LIVE_A, LIVE_B);
        assertThat(ids(client.searchByVector("ObsClass", QUERY, PageRequest.of(0, 10), OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, true)))
                .containsExactly(LIVE_A, LIVE_B);
        assertThat(ids(client.searchByVectorInOntology("ObsClass", QUERY, PageRequest.of(0, 10),
                OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, "obsdefining", true)))
                .containsExactly(LIVE_A, LIVE_B);
        assertThat(ids(client.searchByVectorInOntology("ObsClass", QUERY, PageRequest.of(0, 10),
                OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, "obsdefining", true, true)))
                .containsExactly(LIVE_A, LIVE_B);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aSearchEverywhereFillsThePageWhenTheNearestRowsAllBelongToOneEntity(boolean includeObsoleteEntities) {
        // SYN's 60 label rows are the 60 nearest rows: more than the rows first asked of the index
        // for a page of three.
        Page<JsonElement> page = client.searchByVector("ObsSyn", OPPOSITE_QUERY, PageRequest.of(0, 3),
                OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, false, includeObsoleteEntities);

        assertThat(ids(page)).containsExactly(SYN, SYN_B, SYN_A);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aSearchEverywhereIsAnsweredFromTheIndex(boolean includeObsoleteEntities) {
        // Ranking every vector would find ZERO, at the bottom. Its vector is not in the index.
        assertThat(ids(searchEverywhere("ObsZero", 10, false, includeObsoleteEntities))).doesNotContain(ZERO);
    }

    @Test
    void aSearchEverywhereStillFillsThePageWithoutIterativeScans() {
        ReflectionTestUtils.setField(client, "hnswIterativeScans", false);

        assertThat(ids(searchEverywhere("OntologyEntity", 4, true, false)))
                .containsExactly(LIVE_A, LIVE_P, LIVE_B, SYN_A);
    }

    // ------------------------------------------------------------------
    // The entities one ontology defines, by each way of answering
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {EXACT_SCAN, INDEX_SCAN})
    void aSearchOfAnOntologyFillsThePageFromBehindItsObsoleteRows(int exactScanMaxVectors) {
        assertThat(ids(searchOntology(exactScanMaxVectors, "OntologyEntity", 4, true, false)))
                .containsExactly(LIVE_A, LIVE_P, LIVE_B, SYN_A);
    }

    @ParameterizedTest
    @ValueSource(ints = {EXACT_SCAN, INDEX_SCAN})
    void aSearchOfAnOntologyReturnsObsoleteEntitiesWhenAsked(int exactScanMaxVectors) {
        List<String> found = ids(searchOntology(exactScanMaxVectors, "OntologyEntity", 4, true, true));

        assertThat(found).hasSize(4).first().isEqualTo(OBS_CURATED);
        assertThat(found.subList(1, 4)).allMatch(id -> id.startsWith(OBS_PREFIX));
    }

    @ParameterizedTest
    @ValueSource(ints = {EXACT_SCAN, INDEX_SCAN})
    void aSearchOfAnOntologyForOneTypeLeavesOutObsoleteEntitiesOfThatType(int exactScanMaxVectors) {
        assertThat(ids(searchOntology(exactScanMaxVectors, "ObsClass", 10, true, false)))
                .containsExactly(LIVE_A, LIVE_B);
    }

    // ------------------------------------------------------------------
    // Through ols_entities: imported terms, and any database from before GitHub issue #1445
    // ------------------------------------------------------------------

    @Test
    void anImportingOntologyIsJudgedByItsOwnCopyOfTheTerm() {
        // Its copy of LIVE_B is obsolete there although the defining term is not.
        Page<JsonElement> withoutObsolete = client.searchByVectorInOntology("ObsClass", QUERY, PageRequest.of(0, 10),
                OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, "obsimporter", false, true, false);
        Page<JsonElement> withObsolete = client.searchByVectorInOntology("ObsClass", QUERY, PageRequest.of(0, 10),
                OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, "obsimporter", false, true, true);

        assertThat(ids(withoutObsolete)).containsExactly(IMPORTED_LIVE_A);
        assertThat(ids(withObsolete)).containsExactly(IMPORTED_OBS_1, IMPORTED_LIVE_A, IMPORTED_LIVE_B);
    }

    @Test
    void aDatabaseWithoutScopeColumnsOnItsEmbeddingRowsLeavesOutObsoleteEntitiesToo() {
        try (PostgreSQLContainer<?> legacy = PostgresIntegrationTestSupport.newContainer()) {
            legacy.start();
            PostgresIntegrationTestSupport.initializeOlsPostgresClientObsoleteVectorSearchDatabase(legacy);
            PostgresIntegrationTestSupport.dropEmbeddingNodeScopeColumns(legacy);
            try (OlsPostgresClientRepositoryHandle legacyHandle =
                         PostgresIntegrationTestSupport.createOlsPostgresClientRepositories(legacy)) {
                OlsPostgresClient legacyClient = legacyHandle.olsPostgresClient();

                assertThat(ids(legacyClient.searchByVectorInOntology("ObsClass", QUERY, PageRequest.of(0, 10),
                        OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, "obsdefining", true, true, false)))
                        .containsExactly(LIVE_A, LIVE_B);
                assertThat(ids(legacyClient.searchByVectorInOntology("ObsClass", QUERY, PageRequest.of(0, 1),
                        OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, "obsdefining", true, true, true)))
                        .containsExactly(OBS_CURATED);
            }
        }
    }
}
