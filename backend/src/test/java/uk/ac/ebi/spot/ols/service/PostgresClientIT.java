package uk.ac.ebi.spot.ols.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport.SearchClientHandle;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-Postgres coverage for {@link PostgresClient}: the HikariCP/jOOQ connection-pool wiring
 * that every other real-Postgres test in this programme has been indirectly exercising all along
 * via {@code PostgresIntegrationTestSupport}'s {@code createPostgresClient} factory, but which had
 * no dedicated test of its own until now (see docs/backend-testing-strategy.md's "Implemented
 * PostgresClient baseline" section).
 *
 * <p>Covers {@code getConnection()}/{@code dsl()}/{@code returnNodeCount()} end-to-end against a
 * real database, the {@code ResultSet}-taking {@code decompressJson} overloads against a real
 * {@code _json} BYTEA column, and {@code init()}'s {@code currentSchema} JDBC URL construction
 * across all three of its branches. {@code decompressJson(byte[])}'s own null/valid/corrupt/
 * multi-chunk logic is covered without Postgres in {@code PostgresClientTest}.
 */
@Testcontainers
class PostgresClientIT {

    private static final String LIVER_ENTITY_ID = "efo+class+http://example.org/EFO_0001";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static SearchClientHandle searchClientHandle;
    private static PostgresClient postgresClient;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        // The minimal existing factory that hands back a bare, already-init()ed PostgresClient
        // (schema="public", the shared factory's one fixed schema value) rather than reinventing
        // one; OlsSearchClient itself is unused here, only its companion PostgresClient is.
        searchClientHandle = PostgresIntegrationTestSupport.createSearchClient(POSTGRES);
        postgresClient = searchClientHandle.postgresClient();
    }

    @AfterAll
    static void closeDatabaseClient() {
        searchClientHandle.close();
    }

    // --- getConnection() / dsl() / returnNodeCount() against real Postgres -----------------

    @Test
    void getConnectionAndDslProvideAGenuineWorkingPostgresConnection() throws Exception {
        try (Connection connection = postgresClient.getConnection()) {
            assertThat(connection.isValid(2)).isTrue();

            DSLContext dsl = postgresClient.dsl(connection);
            Long count = dsl.selectCount()
                    .from(DSL.table(DSL.name("ols_entities")))
                    .fetchOne(0, Long.class);

            assertThat(count).isEqualTo(9L);
        }
    }

    @Test
    void returnNodeCountReturnsTheKnownFixtureRowCount() {
        // 4 ontology rows (ontology-fixture.json) + 5 entity rows (entity-fixture.json), both
        // loaded into the shared ols_entities table by initializeDatabase().
        assertThat(postgresClient.returnNodeCount()).isEqualTo(9L);
    }

    @Test
    void returnNodeCountWrapsAGenuineSqlExceptionOnceThePoolIsClosed() {
        // A dedicated, standalone client (not the shared one other tests in this class use) so
        // closing its pool early can't affect any other test: getConnection() on an already-closed
        // HikariDataSource genuinely throws SQLException("HikariDataSource has been closed."),
        // driving returnNodeCount()'s catch block for real rather than via a faked collaborator.
        PostgresClient client = newStandaloneClient("public", 1);
        client.close();

        assertThatThrownBy(client::returnNodeCount)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to count nodes")
                .hasCauseInstanceOf(SQLException.class);
    }

    // --- decompressJson(ResultSet, ...) against a real _json BYTEA column ------------------

    @Test
    void decompressJsonByColumnNameRoundTripsTheRealJsonColumn() throws Exception {
        String expectedJson = expectedJsonFor(LIVER_ENTITY_ID);

        try (Connection connection = postgresClient.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, _json FROM ols_entities WHERE id = ?")) {
            statement.setString(1, LIVER_ENTITY_ID);
            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(PostgresClient.decompressJson(rs, "_json")).isEqualTo(expectedJson);
            }
        }
    }

    @Test
    void decompressJsonByColumnIndexRoundTripsTheRealJsonColumn() throws Exception {
        String expectedJson = expectedJsonFor(LIVER_ENTITY_ID);

        try (Connection connection = postgresClient.getConnection();
             // id, _json -> _json is column index 2.
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, _json FROM ols_entities WHERE id = ?")) {
            statement.setString(1, LIVER_ENTITY_ID);
            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(PostgresClient.decompressJson(rs, 2)).isEqualTo(expectedJson);
            }
        }
    }

    /**
     * Reads the same classpath fixture {@code PostgresIntegrationTestSupport.loadEntityFixture}
     * used to populate {@code _json} (via {@code gzip(record.getAsJsonObject("json").toString())})
     * and re-derives the expected string the same way, rather than hand-copying a brittle JSON
     * literal into this test.
     */
    private static String expectedJsonFor(String entityId) throws Exception {
        try (InputStream stream = PostgresClientIT.class.getResourceAsStream(
                "/fixtures/entities/entity-fixture.json")) {
            JsonObject fixture = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement element : fixture.getAsJsonArray("records")) {
                JsonObject record = element.getAsJsonObject();
                if (record.get("id").getAsString().equals(entityId)) {
                    return record.getAsJsonObject("json").toString();
                }
            }
        }
        throw new IllegalStateException("Fixture record not found: " + entityId);
    }

    // --- init()'s currentSchema JDBC URL construction, per schema variant ------------------
    //
    // The shared createPostgresClient factory always hard-codes schema="public" (confirmed by
    // reading it directly), so it alone can't exercise the other two branches; these tests
    // construct additional standalone PostgresClient instances against the same running
    // container instead, following this repo's ReflectionTestUtils white-box idiom (the same
    // technique the shared factory itself already uses to set these very fields).

    @Test
    void initOmitsCurrentSchemaParamWhenNoSchemaIsConfigured() {
        assertSchemaProducesJdbcUrl("", baseJdbcUrl());
    }

    @Test
    void initOmitsCurrentSchemaParamWhenSchemaIsNull() {
        assertSchemaProducesJdbcUrl(null, baseJdbcUrl());
    }

    @Test
    void initSetsCurrentSchemaToJustPublicWhenSchemaIsPublic() {
        // Not "public,public": currentSchema replaces search_path outright rather than appending
        // to it, so public must still be listed explicitly even when it's the only entry -
        // appending it a second time would be redundant, not wrong, which is exactly why this
        // case needs its own assertion distinct from the "any other schema" case below.
        assertSchemaProducesJdbcUrl("public", baseJdbcUrl() + "?currentSchema=public");
    }

    @Test
    void initAppendsPublicToAnyNonPublicSchemaSoDefaultExtensionsStillResolve() {
        // Per the production code's own comment: currentSchema replaces search_path outright, so
        // a non-public schema must list public explicitly or pg_trgm/pgvector functions installed
        // there by default stop resolving unqualified.
        assertSchemaProducesJdbcUrl("myschema", baseJdbcUrl() + "?currentSchema=myschema,public");
    }

    private static String baseJdbcUrl() {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432)
                + "/" + POSTGRES.getDatabaseName();
    }

    private static void assertSchemaProducesJdbcUrl(String schema, String expectedUrl) {
        PostgresClient client = newStandaloneClient(schema, 1);
        try {
            HikariDataSource dataSource =
                    (HikariDataSource) ReflectionTestUtils.getField(client, "dataSource");
            assertThat(dataSource).isNotNull();
            assertThat(dataSource.getJdbcUrl()).isEqualTo(expectedUrl);
        } finally {
            client.close();
        }
    }

    /**
     * Builds and {@code init()}s an additional, standalone {@link PostgresClient} against the same
     * running container, following the {@code ReflectionTestUtils.setField} idiom
     * {@code PostgresIntegrationTestSupport}'s own (schema-fixed) factory already uses. Needed here
     * because that shared factory only ever exercises {@code schema="public"}; the caller owns the
     * returned client's lifecycle and must {@code close()} it.
     */
    private static PostgresClient newStandaloneClient(String schema, int maxPoolSize) {
        PostgresClient client = new PostgresClient();
        ReflectionTestUtils.setField(client, "host", POSTGRES.getHost());
        ReflectionTestUtils.setField(client, "port", POSTGRES.getMappedPort(5432));
        ReflectionTestUtils.setField(client, "database", POSTGRES.getDatabaseName());
        ReflectionTestUtils.setField(client, "user", POSTGRES.getUsername());
        ReflectionTestUtils.setField(client, "password", POSTGRES.getPassword());
        ReflectionTestUtils.setField(client, "schema", schema);
        ReflectionTestUtils.setField(client, "maxPoolSize", maxPoolSize);
        ReflectionTestUtils.setField(client, "minIdle", 0);
        client.init();
        return client;
    }
}
