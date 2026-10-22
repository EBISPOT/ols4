package uk.ac.ebi.spot.ols.repository.postgres;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport.OlsPostgresClientRepositoryHandle;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport.MAX_POOL_SIZE;
import static uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport.OLS_POSTGRES_CLIENT_EMBEDDING_MODEL;

/**
 * {@link OlsPostgresClient#searchByVectorInOntology} for entities an ontology defines, against a
 * database whose embedding rows carry their entity's ontology and type (GitHub issue #1445).
 *
 * <p>Such a search is answered in one of the ways listed in {@link Answer}, depending on how many
 * vectors the ontology has. Every case here is run each way and must give the same page: for this
 * fixture the index walk always runs out of rows before it runs out of budget, so it is exact too.
 * The one case where the answers differ, and which therefore shows that {@link Answer#INDEX}
 * really is answered from the index, is
 * {@link #anEntityWhoseOnlyVectorIsAllZerosIsRankedLastByAnExactScanAndIsNotInTheIndex}.
 *
 * <p>The fixture ({@link PostgresIntegrationTestSupport#initializeOlsPostgresClientScopedVectorSearchDatabase})
 * puts the 60 different vectors of another ontology's classes between the query and the rows these
 * searches must find, which is more than the 40 ({@code hnsw.ef_search}) one HNSW scan goes through
 * before it stops. With {@code hnsw.iterative_scan} left off, the index cases that look for classes
 * of {@code hnswfar} fail.
 */
class OlsPostgresClientScopedVectorSearchIT {

    /** How a search is answered, and the client setting that selects each way on this fixture. */
    enum Answer {
        /** Every vector in scope is ranked. */
        EXACT(Integer.MAX_VALUE),
        /** The HNSW index is walked and the rows in scope are kept. */
        INDEX(0);

        private final int exactScanMaxVectors;

        Answer(int exactScanMaxVectors) {
            this.exactScanMaxVectors = exactScanMaxVectors;
        }
    }

    private static final String FAR_A = "hnswfar+hnswclass+http://example.org/FAR_A";
    private static final String FAR_B = "hnswfar+hnswclass+http://example.org/FAR_B";
    private static final String FAR_P = "hnswfar+hnswproperty+http://example.org/FAR_P";
    private static final String WIDE_SYN = "hnswwide+hnswclass+http://example.org/WIDE_SYN";
    private static final String WIDE_A = "hnswwide+hnswclass+http://example.org/WIDE_A";
    private static final String WIDE_B = "hnswwide+hnswclass+http://example.org/WIDE_B";
    private static final String WIDE_C = "hnswwide+hnswclass+http://example.org/WIDE_C";
    private static final String ZERO_A = "hnswzero+hnswclass+http://example.org/ZERO_A";
    private static final String ZERO_Z = "hnswzero+hnswclass+http://example.org/ZERO_Z";

    private static final List<Double> QUERY = List.of(1.0, 0.0, 0.0, 0.0);

    /** Every setting a search through the index changes for the length of one transaction. */
    private static final String SESSION_SETTINGS =
            "SELECT current_setting('hnsw.iterative_scan'), current_setting('enable_sort')";

    private static PostgreSQLContainer<?> container;
    private static OlsPostgresClientRepositoryHandle handle;
    private static OlsPostgresClient client;

    @BeforeAll
    static void setUpDatabase() {
        container = PostgresIntegrationTestSupport.newContainer();
        container.start();
        PostgresIntegrationTestSupport.initializeOlsPostgresClientScopedVectorSearchDatabase(container);
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

    private static Page<JsonElement> search(Answer answer, String type, int pageSize, String ontologyId,
            boolean includeCurations) {
        client.exactScanMaxVectors = answer.exactScanMaxVectors;
        return client.searchByVectorInOntology(type, QUERY, PageRequest.of(0, pageSize),
                OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, ontologyId, true, includeCurations);
    }

    private static List<String> ids(Page<JsonElement> page) {
        return page.getContent().stream()
                .map(e -> e.getAsJsonObject().get("id").getAsString())
                .toList();
    }

    private static List<Double> scores(Page<JsonElement> page) {
        return page.getContent().stream()
                .map(e -> e.getAsJsonObject().get("score").getAsDouble())
                .toList();
    }

    /**
     * The settings in {@link #SESSION_SETTINGS} as a session has them. The vector type is used
     * first, because {@code hnsw.iterative_scan} only exists in a session once pgvector is loaded.
     */
    private static List<String> sessionSettings(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT '[1]'::vector");
            try (ResultSet settings = statement.executeQuery(SESSION_SETTINGS)) {
                settings.next();
                return List.of(settings.getString(1), settings.getString(2));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Answer.class)
    void findsTheOntologysEntitiesBehindMoreNearerRowsOfOtherOntologiesThanOneIndexScanReturns(Answer answer) {
        Page<JsonElement> page = search(answer, "HnswClass", 10, "hnswfar", false);

        assertThat(ids(page)).containsExactly(FAR_A, FAR_B);
        assertThat(scores(page)).containsExactly(0.8, 0.5);
    }

    @ParameterizedTest
    @EnumSource(Answer.class)
    void ranksAnEntityByItsCurationEmbeddingWhenThatIsNearerThanItsLabel(Answer answer) {
        Page<JsonElement> page = search(answer, "HnswClass", 10, "hnswfar", true);

        assertThat(ids(page)).containsExactly(FAR_B, FAR_A);
        assertThat(scores(page)).containsExactly(0.9, 0.8);
    }

    @ParameterizedTest
    @EnumSource(Answer.class)
    void theGenericEntityTypeSearchesEveryEntityTypeOfTheOntology(Answer answer) {
        Page<JsonElement> page = search(answer, "OntologyEntity", 10, "hnswfar", true);

        assertThat(ids(page)).containsExactly(FAR_P, FAR_B, FAR_A);
        assertThat(scores(page)).containsExactly(1.0, 0.9, 0.8);
    }

    @ParameterizedTest
    @EnumSource(Answer.class)
    void aConcreteEntityTypeExcludesTheOntologysOtherTypes(Answer answer) {
        Page<JsonElement> page = search(answer, "HnswProperty", 10, "hnswfar", true);

        assertThat(ids(page)).containsExactly(FAR_P);
    }

    @ParameterizedTest
    @EnumSource(Answer.class)
    void fillsThePageWhenTheNearestRowsAllBelongToOneEntity(Answer answer) {
        // WIDE_SYN's 50 label rows are the 50 nearest rows of hnswwide: more than the rows first
        // asked of the index for a page of three.
        Page<JsonElement> page = search(answer, "HnswClass", 3, "hnswwide", false);

        assertThat(ids(page)).containsExactly(WIDE_SYN, WIDE_A, WIDE_B);
        assertThat(scores(page)).containsExactly(1.0, 0.8, 0.5);
    }

    @ParameterizedTest
    @EnumSource(Answer.class)
    void returnsEachEntityOnceHoweverManyOfItsEmbeddingsMatch(Answer answer) {
        Page<JsonElement> page = search(answer, "OntologyEntity", 10, "hnswwide", true);

        assertThat(ids(page)).containsExactly(WIDE_SYN, WIDE_A, WIDE_B, WIDE_C);
        assertThat(scores(page)).containsExactly(1.0, 0.8, 0.5, 0.0);
    }

    @ParameterizedTest
    @EnumSource(Answer.class)
    void matchesTheOntologyIdCaseInsensitively(Answer answer) {
        Page<JsonElement> page = search(answer, "HnswClass", 10, "HnswFar", false);

        assertThat(ids(page)).containsExactly(FAR_A, FAR_B);
    }

    @ParameterizedTest
    @EnumSource(Answer.class)
    void anOntologyWithNoEmbeddingsGivesAnEmptyPage(Answer answer) {
        Page<JsonElement> page = search(answer, "OntologyEntity", 10, "efo", true);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void anEntityWhoseOnlyVectorIsAllZerosIsRankedLastByAnExactScanAndIsNotInTheIndex() {
        // The cosine distance to a zero vector is NaN, which sorts after every number and scores 0.
        // pgvector leaves zero vectors out of a cosine HNSW index, so an index walk never meets one.
        Page<JsonElement> exact = search(Answer.EXACT, "HnswClass", 10, "hnswzero", false);

        assertThat(ids(exact)).containsExactly(ZERO_A, ZERO_Z);
        assertThat(scores(exact)).containsExactly(0.8, 0.0);

        Page<JsonElement> fromTheIndex = search(Answer.INDEX, "HnswClass", 10, "hnswzero", false);

        assertThat(ids(fromTheIndex)).containsExactly(ZERO_A);
        assertThat(scores(fromTheIndex)).containsExactly(0.8);
    }

    @Test
    void theAnswerIsChosenByTheNumberOfVectorsInScopeNotInTheTable() {
        // hnswzero has two class label vectors, and the two answers differ on it (see above). The
        // table holds over a hundred label vectors of other ontologies.
        client.exactScanMaxVectors = 2;
        Page<JsonElement> atTheLimit = client.searchByVectorInOntology("HnswClass", QUERY, PageRequest.of(0, 10),
                OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, "hnswzero", true, false);
        client.exactScanMaxVectors = 1;
        Page<JsonElement> overTheLimit = client.searchByVectorInOntology("HnswClass", QUERY, PageRequest.of(0, 10),
                OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, "hnswzero", true, false);

        assertThat(ids(atTheLimit)).containsExactly(ZERO_A, ZERO_Z);
        assertThat(ids(overTheLimit)).containsExactly(ZERO_A);
    }

    @Test
    void aSearchThroughTheIndexLeavesEveryPooledConnectionWithTheSettingsItHad() throws SQLException {
        List<String> untouched;
        try (Connection fresh = container.createConnection("")) {
            untouched = sessionSettings(fresh);
        }

        assertThat(search(Answer.INDEX, "HnswClass", 10, "hnswfar", true).getContent()).isNotEmpty();

        // Holding as many connections as the pool can have is holding all of them, the one the
        // search ran on included.
        List<Connection> pooled = new ArrayList<>();
        try {
            for (int i = 0; i < MAX_POOL_SIZE; i++) {
                pooled.add(handle.postgresClient().getConnection());
            }
            for (Connection connection : pooled) {
                assertThat(sessionSettings(connection)).isEqualTo(untouched);
                assertThat(connection.getAutoCommit()).isTrue();
            }
        } finally {
            for (Connection connection : pooled) {
                connection.close();
            }
        }
    }
}
