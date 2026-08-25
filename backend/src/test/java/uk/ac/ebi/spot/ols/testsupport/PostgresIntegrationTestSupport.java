package uk.ac.ebi.spot.ols.testsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import uk.ac.ebi.spot.ols.repository.EntityRepository;
import uk.ac.ebi.spot.ols.repository.OntologyRepository;
import uk.ac.ebi.spot.ols.repository.PropertyRepository;
import uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.repository.v1.V1OntologyRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1TermRepository;
import uk.ac.ebi.spot.ols.service.PostgresClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public final class PostgresIntegrationTestSupport {

    private static final String IMAGE = "pgvector/pgvector:0.8.0-pg17";
    private static final List<String> FILTER_PROPERTIES = List.of(
            "tags",
            "domain",
            "http://example.org/category");

    private PostgresIntegrationTestSupport() {
    }

    public static PostgreSQLContainer<?> newContainer() {
        return new PostgreSQLContainer<>(
                DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("ols4_test")
                .withUsername("ols")
                .withPassword("ols-test");
    }

    public static void initializeDatabase(PostgreSQLContainer<?> container) {
        try (Connection connection = container.createConnection("")) {
            executeProductionSchema(connection);
            loadOntologyFixture(connection);
            loadEntityFixture(connection);
        } catch (IOException | InterruptedException | SQLException e) {
            throw new IllegalStateException("Failed to initialize the disposable OLS PostgreSQL database", e);
        }
    }

    public static void initializePropertyDatabase(PostgreSQLContainer<?> container) {
        initializeDatabase(container);
        try (Connection connection = container.createConnection("")) {
            loadPropertyFixture(connection);
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Failed to load the property integration fixture", e);
        }
    }

    public static RepositoryHandle createRepository(PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        OlsSearchClient searchClient = createSearchClient(postgresClient);

        OlsPostgresClient olsPostgresClient = new OlsPostgresClient();
        ReflectionTestUtils.setField(olsPostgresClient, "postgresClient", postgresClient);

        OntologyRepository repository = new OntologyRepository();
        ReflectionTestUtils.setField(repository, "searchClient", searchClient);
        ReflectionTestUtils.setField(repository, "postgresClient", olsPostgresClient);
        return new RepositoryHandle(repository, postgresClient);
    }

    public static V1RepositoryHandle createV1Repository(PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        OlsSearchClient searchClient = createSearchClient(postgresClient);

        V1OntologyRepository repository = new V1OntologyRepository();
        ReflectionTestUtils.setField(repository, "searchClient", searchClient);
        return new V1RepositoryHandle(repository, postgresClient);
    }

    public static EntityRepositoryHandle createEntityRepository(PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        OlsSearchClient searchClient = createSearchClient(postgresClient);

        EntityRepository repository = new EntityRepository();
        ReflectionTestUtils.setField(repository, "searchClient", searchClient);
        return new EntityRepositoryHandle(repository, postgresClient);
    }

    public static V1TermRepositoryHandle createV1TermRepository(
            PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        OlsSearchClient searchClient = createSearchClient(postgresClient);

        V1TermRepository repository = new V1TermRepository();
        ReflectionTestUtils.setField(repository, "searchClient", searchClient);
        return new V1TermRepositoryHandle(repository, postgresClient);
    }

    public static PropertyRepositoryHandle createPropertyRepository(
            PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        OlsSearchClient searchClient = createSearchClient(postgresClient);

        OlsPostgresClient olsPostgresClient = new OlsPostgresClient();
        ReflectionTestUtils.setField(olsPostgresClient, "postgresClient", postgresClient);

        PropertyRepository repository = new PropertyRepository();
        ReflectionTestUtils.setField(repository, "searchClient", searchClient);
        ReflectionTestUtils.setField(repository, "postgresClient", olsPostgresClient);
        return new PropertyRepositoryHandle(repository, postgresClient);
    }

    private static PostgresClient createPostgresClient(PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = new PostgresClient();
        ReflectionTestUtils.setField(postgresClient, "host", container.getHost());
        ReflectionTestUtils.setField(postgresClient, "port", container.getMappedPort(5432));
        ReflectionTestUtils.setField(postgresClient, "database", container.getDatabaseName());
        ReflectionTestUtils.setField(postgresClient, "user", container.getUsername());
        ReflectionTestUtils.setField(postgresClient, "password", container.getPassword());
        ReflectionTestUtils.setField(postgresClient, "schema", "public");
        ReflectionTestUtils.setField(postgresClient, "maxPoolSize", 3);
        ReflectionTestUtils.setField(postgresClient, "minIdle", 0);
        postgresClient.init();
        return postgresClient;
    }

    private static OlsSearchClient createSearchClient(PostgresClient postgresClient) {
        OlsSearchClient searchClient = new OlsSearchClient();
        ReflectionTestUtils.setField(searchClient, "postgresClient", postgresClient);
        return searchClient;
    }

    private static void executeProductionSchema(Connection connection)
            throws IOException, InterruptedException, SQLException {
        Path repositoryRoot = Path.of(System.getProperty("maven.multiModuleProjectDirectory", ".."));
        Path schemaGenerator = repositoryRoot.resolve("dataload/create_postgres_schema.py").normalize();
        if (!Files.isRegularFile(schemaGenerator)) {
            throw new IllegalStateException("Production schema generator not found: " + schemaGenerator);
        }

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command().add("python3");
        processBuilder.command().add(schemaGenerator.toString());
        for (String property : FILTER_PROPERTIES) {
            processBuilder.command().add("--filter-property");
            processBuilder.command().add(property);
        }
        Process process = processBuilder.start();
        String sql;
        String error;
        try (InputStream stdout = process.getInputStream(); InputStream stderr = process.getErrorStream()) {
            sql = new String(stdout.readAllBytes(), StandardCharsets.UTF_8);
            error = new String(stderr.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(
                    "Production schema generator exited with " + exitCode + ": " + error);
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void loadOntologyFixture(Connection connection) throws IOException, SQLException {
        JsonObject fixture;
        try (InputStream stream = PostgresIntegrationTestSupport.class.getResourceAsStream(
                "/fixtures/ontologies/ontology-fixture.json")) {
            if (stream == null) {
                throw new IllegalStateException("Ontology integration fixture is missing");
            }
            fixture = JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        String sql = """
                INSERT INTO ols_entities (
                    id, type, iri, ontology_id, _json, is_obsolete, label, definition,
                    search_type, is_defining_ontology, filter_tags, filter_domain,
                    "filter_http://example.org/category")
                VALUES (?, 'ontology', ?, ?, ?, ?, ?, ?, 'ontology', TRUE, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (JsonElement element : fixture.getAsJsonArray("records")) {
                JsonObject record = element.getAsJsonObject();
                statement.setString(1, record.get("id").getAsString());
                statement.setString(2, record.get("iri").getAsString());
                statement.setString(3, record.get("ontologyId").getAsString());
                statement.setBytes(4, gzip(record.getAsJsonObject("json").toString()));
                statement.setBoolean(5, record.get("isObsolete").getAsBoolean());
                statement.setArray(6, textArray(connection, record.getAsJsonArray("label")));
                statement.setArray(7, textArray(connection, record.getAsJsonArray("definition")));
                statement.setArray(8, textArray(connection, record.getAsJsonArray("tags")));
                statement.setArray(9, textArray(connection, record.getAsJsonArray("domain")));
                statement.setArray(10, textArray(
                        connection,
                        record.getAsJsonArray("http://example.org/category")));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void loadEntityFixture(Connection connection) throws IOException, SQLException {
        JsonObject fixture;
        try (InputStream stream = PostgresIntegrationTestSupport.class.getResourceAsStream(
                "/fixtures/entities/entity-fixture.json")) {
            if (stream == null) {
                throw new IllegalStateException("Entity integration fixture is missing");
            }
            fixture = JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        String sql = """
                INSERT INTO ols_entities (
                    id, type, iri, ontology_id, _json, is_obsolete, label, search_type,
                    short_form, curie, obo_id, synonym, definition, is_defining_ontology,
                    subset, related_to, label_for_suggest, filter_tags, filter_domain,
                    "filter_http://example.org/category")
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (JsonElement element : fixture.getAsJsonArray("records")) {
                JsonObject record = element.getAsJsonObject();
                statement.setString(1, record.get("id").getAsString());
                statement.setString(2, record.get("databaseType").getAsString());
                statement.setString(3, record.get("iri").getAsString());
                statement.setString(4, record.get("ontologyId").getAsString());
                statement.setBytes(5, gzip(record.getAsJsonObject("json").toString()));
                statement.setBoolean(6, record.get("isObsolete").getAsBoolean());
                statement.setArray(7, textArray(connection, record.getAsJsonArray("label")));
                statement.setString(8, record.get("searchType").getAsString());
                statement.setString(9, record.get("shortForm").getAsString());
                statement.setString(10, record.get("curie").getAsString());
                statement.setString(11, record.get("curie").getAsString());
                statement.setArray(12, textArray(connection, record.getAsJsonArray("synonym")));
                statement.setArray(13, textArray(connection, record.getAsJsonArray("definition")));
                statement.setBoolean(14, record.get("isDefiningOntology").getAsBoolean());
                statement.setArray(15, textArray(connection, record.getAsJsonArray("subset")));
                statement.setArray(16, textArray(connection, record.getAsJsonArray("relatedTo")));
                statement.setString(17, record.getAsJsonArray("label").get(0).getAsString());
                statement.setArray(18, textArray(connection, record.getAsJsonArray("tags")));
                statement.setArray(19, textArray(connection, record.getAsJsonArray("domain")));
                statement.setArray(20, textArray(
                        connection,
                        record.getAsJsonArray("http://example.org/category")));
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ANALYZE ols_entities");
        }
    }

    private static void loadPropertyFixture(Connection connection) throws IOException, SQLException {
        JsonObject fixture;
        try (InputStream stream = PostgresIntegrationTestSupport.class.getResourceAsStream(
                "/fixtures/properties/property-fixture.json")) {
            if (stream == null) {
                throw new IllegalStateException("Property integration fixture is missing");
            }
            fixture = JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        String sql = """
                INSERT INTO ols_entities (
                    id, type, iri, ontology_id, _json, is_obsolete, label, search_type,
                    short_form, curie, obo_id, synonym, definition, is_defining_ontology,
                    subset, related_to, direct_parents, direct_ancestors, label_for_suggest,
                    filter_tags, filter_domain, "filter_http://example.org/category")
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (JsonElement element : fixture.getAsJsonArray("records")) {
                JsonObject record = element.getAsJsonObject();
                statement.setString(1, record.get("id").getAsString());
                statement.setString(2, record.get("databaseType").getAsString());
                statement.setString(3, record.get("iri").getAsString());
                statement.setString(4, record.get("ontologyId").getAsString());
                statement.setBytes(5, gzip(record.getAsJsonObject("json").toString()));
                statement.setBoolean(6, record.get("isObsolete").getAsBoolean());
                statement.setArray(7, textArray(connection, record.getAsJsonArray("label")));
                statement.setString(8, record.get("searchType").getAsString());
                statement.setString(9, record.get("shortForm").getAsString());
                statement.setString(10, record.get("curie").getAsString());
                statement.setString(11, record.get("curie").getAsString());
                statement.setArray(12, textArray(connection, record.getAsJsonArray("synonym")));
                statement.setArray(13, textArray(connection, record.getAsJsonArray("definition")));
                statement.setBoolean(14, record.get("isDefiningOntology").getAsBoolean());
                statement.setArray(15, textArray(connection, record.getAsJsonArray("subset")));
                statement.setArray(16, textArray(connection, record.getAsJsonArray("relatedTo")));
                statement.setArray(17, textArray(connection, record.getAsJsonArray("directParents")));
                statement.setArray(18, textArray(connection, record.getAsJsonArray("directAncestors")));
                statement.setString(19, record.getAsJsonArray("label").get(0).getAsString());
                statement.setArray(20, textArray(connection, record.getAsJsonArray("tags")));
                statement.setArray(21, textArray(connection, record.getAsJsonArray("domain")));
                statement.setArray(22, textArray(
                        connection,
                        record.getAsJsonArray("http://example.org/category")));
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ANALYZE ols_entities");
        }
    }

    private static java.sql.Array textArray(Connection connection, JsonArray values) throws SQLException {
        String[] strings = new String[values.size()];
        for (int i = 0; i < values.size(); i++) {
            strings[i] = values.get(i).getAsString();
        }
        return connection.createArrayOf("text", strings);
    }

    private static byte[] gzip(String json) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    public record RepositoryHandle(
            OntologyRepository repository,
            PostgresClient postgresClient) implements AutoCloseable {

        @Override
        public void close() {
            postgresClient.close();
        }
    }

    public record V1RepositoryHandle(
            V1OntologyRepository repository,
            PostgresClient postgresClient) implements AutoCloseable {

        @Override
        public void close() {
            postgresClient.close();
        }
    }

    public record EntityRepositoryHandle(
            EntityRepository repository,
            PostgresClient postgresClient) implements AutoCloseable {

        @Override
        public void close() {
            postgresClient.close();
        }
    }

    public record V1TermRepositoryHandle(
            V1TermRepository repository,
            PostgresClient postgresClient) implements AutoCloseable {

        @Override
        public void close() {
            postgresClient.close();
        }
    }

    public record PropertyRepositoryHandle(
            PropertyRepository repository,
            PostgresClient postgresClient) implements AutoCloseable {

        @Override
        public void close() {
            postgresClient.close();
        }
    }
}
