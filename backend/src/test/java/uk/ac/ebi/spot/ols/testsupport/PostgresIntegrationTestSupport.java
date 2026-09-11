package uk.ac.ebi.spot.ols.testsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import uk.ac.ebi.spot.ols.repository.EntityRepository;
import uk.ac.ebi.spot.ols.repository.ClassRepository;
import uk.ac.ebi.spot.ols.repository.IndividualRepository;
import uk.ac.ebi.spot.ols.repository.OntologyRepository;
import uk.ac.ebi.spot.ols.repository.PropertyRepository;
import uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.repository.v1.V1GraphRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1IndividualRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1JsTreeRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1OntologyRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1PropertyRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1TermRepository;
import uk.ac.ebi.spot.ols.service.EmbeddingServiceClient;
import uk.ac.ebi.spot.ols.service.PostgresClient;
import uk.ac.ebi.spot.ols.service.TextTaggerService;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

public final class PostgresIntegrationTestSupport {

    private static final String IMAGE = "pgvector/pgvector:0.8.0-pg17";
    private static final List<String> FILTER_PROPERTIES = List.of(
            "tags",
            "domain",
            "http://example.org/category",
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type");

    /**
     * Model name used by the {@code V2LLMController} integration fixture. No production schema
     * generator run in this test suite ever passes embedding parquet files (see
     * {@link #executeProductionSchema}), so no {@code embeddings_<model>}/{@code embedding_<model>}
     * vector columns exist until {@link #loadV2LlmEmbeddingFixture} adds them directly with SQL.
     * Kept within {@code OlsPostgresClient.SAFE_MODEL_NAME} (letters, digits, underscore, dot,
     * dash).
     */
    private static final String V2LLM_TEST_MODEL = "test_model";

    /**
     * Model name used by {@code OlsPostgresClientEmbeddingIT}'s dedicated embedding/vector-search
     * fixture. Deliberately distinct from {@link #V2LLM_TEST_MODEL} so the two fixtures never share
     * a column name (they never share a container either, but keeping them distinct avoids any
     * confusion when reading the two fixtures side by side). Public so the IT class can reference it
     * directly rather than duplicating the literal.
     */
    public static final String OLS_POSTGRES_CLIENT_EMBEDDING_MODEL = "olspgc_test_model";

    /**
     * A second {@code embeddings_<model>} column on {@code ols_entities}, added with no data, whose
     * name contains {@code pca16} -- used only by {@code getEmbeddingModels()}'s exclusion test.
     */
    private static final String OLS_POSTGRES_CLIENT_EMBEDDING_MODEL_PCA16 =
            OLS_POSTGRES_CLIENT_EMBEDDING_MODEL + "_pca16";

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
            loadAutosuggestFixture(connection);
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

    public static void initializeClassDatabase(PostgreSQLContainer<?> container) {
        initializeDatabase(container);
        try (Connection connection = container.createConnection("")) {
            loadClassFixture(connection);
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Failed to load the class integration fixture", e);
        }
    }

    public static void initializeIndividualDatabase(PostgreSQLContainer<?> container) {
        initializeDatabase(container);
        try (Connection connection = container.createConnection("")) {
            loadIndividualFixture(connection);
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Failed to load the individual integration fixture", e);
        }
    }

    /**
     * Loads the shared entity fixture plus the class and property fixtures (both additive to
     * {@code ols_entities}, with no id collisions between them), then adds the {@code V2LLMController}
     * embedding fixture on top. Deliberately does <em>not</em> also load
     * {@link #loadIndividualFixture}: its {@code efo+individual+http://example.org/EFO_I100} row
     * shares a primary key with the class fixture's own individual record of the same id, so the two
     * fixtures cannot coexist in one database. The class fixture's individual is used for the
     * individual-search route instead.
     */
    public static void initializeV2LLMDatabase(PostgreSQLContainer<?> container) {
        initializeDatabase(container);
        try (Connection connection = container.createConnection("")) {
            loadClassFixture(connection);
            loadPropertyFixture(connection);
            loadV2LlmEmbeddingFixture(connection);
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Failed to load the V2 LLM controller integration fixture", e);
        }
    }

    /**
     * Loads the shared ontology + entity fixture, then adds a dedicated embedding/vector-search
     * fixture for {@code OlsPostgresClientEmbeddingIT} under its own {@code embtest}/{@code embtest2}
     * (entity-level {@code embeddings_<model>} family), {@code vectest} (plain node-level
     * {@code embedding_<model>} family), and {@code vectestonto}/{@code vectestonto2}
     * (ontology-scoped node-level family) ontology ids -- each isolated from the others and from
     * every other suite's fixture (including the existing {@code V2LLMController} embedding
     * fixture). See {@link #loadOlsPostgresClientEmbeddingFixture} for exactly which row is which
     * and why.
     */
    public static void initializeOlsPostgresClientEmbeddingDatabase(PostgreSQLContainer<?> container) {
        initializeDatabase(container);
        try (Connection connection = container.createConnection("")) {
            loadOlsPostgresClientEmbeddingFixture(connection);
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Failed to load the OlsPostgresClient embedding/vector-search integration fixture", e);
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

    /**
     * Wires a bare {@link OlsPostgresClient} against disposable Postgres, with no repository layer
     * on top -- used by {@code OlsPostgresClientIT}/{@code OlsPostgresClientGraphIT}/
     * {@code OlsPostgresClientEmbeddingIT}, which exercise {@code OlsPostgresClient} directly rather
     * than through any repository.
     */
    public static OlsPostgresClientRepositoryHandle createOlsPostgresClientRepositories(PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);

        OlsPostgresClient olsPostgresClient = new OlsPostgresClient();
        ReflectionTestUtils.setField(olsPostgresClient, "postgresClient", postgresClient);

        return new OlsPostgresClientRepositoryHandle(olsPostgresClient, postgresClient);
    }

    public static HealthCheckRepositoryHandle createHealthCheckRepositories(PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        OlsSearchClient searchClient = createSearchClient(postgresClient);

        OlsPostgresClient olsPostgresClient = new OlsPostgresClient();
        ReflectionTestUtils.setField(olsPostgresClient, "postgresClient", postgresClient);

        OntologyRepository repository = new OntologyRepository();
        ReflectionTestUtils.setField(repository, "searchClient", searchClient);
        ReflectionTestUtils.setField(repository, "postgresClient", olsPostgresClient);
        return new HealthCheckRepositoryHandle(repository, olsPostgresClient, postgresClient);
    }

    public static TextTaggerRepositoryHandle createTextTaggerRepositories(PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        OlsSearchClient searchClient = createSearchClient(postgresClient);

        TextTaggerService textTaggerService = new TextTaggerService();
        ReflectionTestUtils.setField(textTaggerService, "postgresClient", postgresClient);

        return new TextTaggerRepositoryHandle(textTaggerService, searchClient, postgresClient);
    }

    /**
     * Wires {@link ClassRepository}, {@link PropertyRepository}, the real, unconfigured
     * {@link EmbeddingServiceClient} bean, and the {@link OlsPostgresClient} they all share, against
     * disposable Postgres. The embedding client is returned uninitialized (matching the
     * {@code TextTaggerRepositoryHandle} precedent): call {@code embeddingServiceClient().init()}
     * explicitly once, from the test's {@code @BeforeAll}, before using it. {@code init()} only runs
     * a synchronous {@code SELECT} against {@code ols_pca_models} (empty in this fixture) and never
     * throws, so this is safe to call exactly once per handle.
     */
    public static V2LLMRepositoryHandle createV2LLMRepositories(PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        OlsSearchClient searchClient = createSearchClient(postgresClient);

        OlsPostgresClient olsPostgresClient = new OlsPostgresClient();
        ReflectionTestUtils.setField(olsPostgresClient, "postgresClient", postgresClient);

        ClassRepository classRepository = new ClassRepository();
        ReflectionTestUtils.setField(classRepository, "searchClient", searchClient);
        ReflectionTestUtils.setField(classRepository, "postgresClient", olsPostgresClient);

        PropertyRepository propertyRepository = new PropertyRepository();
        ReflectionTestUtils.setField(propertyRepository, "searchClient", searchClient);
        ReflectionTestUtils.setField(propertyRepository, "postgresClient", olsPostgresClient);

        EmbeddingServiceClient embeddingServiceClient = new EmbeddingServiceClient();
        ReflectionTestUtils.setField(embeddingServiceClient, "postgresClient", postgresClient);

        return new V2LLMRepositoryHandle(
                classRepository, propertyRepository, embeddingServiceClient, olsPostgresClient, postgresClient);
    }

    public static V1RepositoryHandle createV1Repository(PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        OlsSearchClient searchClient = createSearchClient(postgresClient);

        V1OntologyRepository repository = new V1OntologyRepository();
        ReflectionTestUtils.setField(repository, "searchClient", searchClient);
        return new V1RepositoryHandle(repository, postgresClient);
    }

    public static SearchClientHandle createSearchClient(PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        return new SearchClientHandle(createSearchClient(postgresClient), postgresClient);
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

    public static V1PropertyRepositoryHandle createV1PropertyRepository(
            PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        OlsSearchClient searchClient = createSearchClient(postgresClient);

        OlsPostgresClient olsPostgresClient = new OlsPostgresClient();
        ReflectionTestUtils.setField(olsPostgresClient, "postgresClient", postgresClient);

        V1PropertyRepository repository = new V1PropertyRepository();
        ReflectionTestUtils.setField(repository, "searchClient", searchClient);
        ReflectionTestUtils.setField(repository, "postgresClient", olsPostgresClient);
        return new V1PropertyRepositoryHandle(repository, postgresClient);
    }

    public static ClassRepositoryHandle createClassRepository(
            PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        OlsSearchClient searchClient = createSearchClient(postgresClient);

        OlsPostgresClient olsPostgresClient = new OlsPostgresClient();
        ReflectionTestUtils.setField(olsPostgresClient, "postgresClient", postgresClient);

        ClassRepository repository = new ClassRepository();
        ReflectionTestUtils.setField(repository, "searchClient", searchClient);
        ReflectionTestUtils.setField(repository, "postgresClient", olsPostgresClient);
        return new ClassRepositoryHandle(repository, postgresClient);
    }

    public static IndividualRepositoryHandle createIndividualRepository(
            PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        OlsSearchClient searchClient = createSearchClient(postgresClient);

        OlsPostgresClient olsPostgresClient = new OlsPostgresClient();
        ReflectionTestUtils.setField(olsPostgresClient, "postgresClient", postgresClient);

        IndividualRepository repository = new IndividualRepository();
        ReflectionTestUtils.setField(repository, "searchClient", searchClient);
        ReflectionTestUtils.setField(repository, "postgresClient", olsPostgresClient);
        return new IndividualRepositoryHandle(repository, postgresClient);
    }

    public static V1IndividualRepositoryHandle createV1IndividualRepository(
            PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        OlsSearchClient searchClient = createSearchClient(postgresClient);

        OlsPostgresClient olsPostgresClient = new OlsPostgresClient();
        ReflectionTestUtils.setField(olsPostgresClient, "postgresClient", postgresClient);

        V1IndividualRepository repository = new V1IndividualRepository();
        ReflectionTestUtils.setField(repository, "searchClient", searchClient);
        ReflectionTestUtils.setField(repository, "postgresClient", olsPostgresClient);
        return new V1IndividualRepositoryHandle(repository, postgresClient);
    }

    public static V1OntologyIndividualRepositoryHandle createV1OntologyIndividualRepositories(
            PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        OlsSearchClient searchClient = createSearchClient(postgresClient);

        OlsPostgresClient olsPostgresClient = new OlsPostgresClient();
        ReflectionTestUtils.setField(olsPostgresClient, "postgresClient", postgresClient);

        V1IndividualRepository individualRepository = new V1IndividualRepository();
        ReflectionTestUtils.setField(individualRepository, "searchClient", searchClient);
        ReflectionTestUtils.setField(individualRepository, "postgresClient", olsPostgresClient);

        V1JsTreeRepository jsTreeRepository = new V1JsTreeRepository();
        ReflectionTestUtils.setField(jsTreeRepository, "postgresClient", olsPostgresClient);

        return new V1OntologyIndividualRepositoryHandle(
                individualRepository, jsTreeRepository, postgresClient);
    }

    public static V1OntologyPropertyRepositoryHandle createV1OntologyPropertyRepositories(
            PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        OlsSearchClient searchClient = createSearchClient(postgresClient);

        OlsPostgresClient olsPostgresClient = new OlsPostgresClient();
        ReflectionTestUtils.setField(olsPostgresClient, "postgresClient", postgresClient);

        V1PropertyRepository propertyRepository = new V1PropertyRepository();
        ReflectionTestUtils.setField(propertyRepository, "searchClient", searchClient);
        ReflectionTestUtils.setField(propertyRepository, "postgresClient", olsPostgresClient);

        V1JsTreeRepository jsTreeRepository = new V1JsTreeRepository();
        ReflectionTestUtils.setField(jsTreeRepository, "postgresClient", olsPostgresClient);

        return new V1OntologyPropertyRepositoryHandle(
                propertyRepository, jsTreeRepository, postgresClient);
    }

    public static V1OntologyTermRepositoryHandle createV1OntologyTermRepositories(
            PostgreSQLContainer<?> container) {
        PostgresClient postgresClient = createPostgresClient(container);
        OlsSearchClient searchClient = createSearchClient(postgresClient);

        OlsPostgresClient olsPostgresClient = new OlsPostgresClient();
        ReflectionTestUtils.setField(olsPostgresClient, "postgresClient", postgresClient);

        V1TermRepository termRepository = new V1TermRepository();
        ReflectionTestUtils.setField(termRepository, "searchClient", searchClient);
        ReflectionTestUtils.setField(termRepository, "postgresClient", olsPostgresClient);

        V1JsTreeRepository jsTreeRepository = new V1JsTreeRepository();
        ReflectionTestUtils.setField(jsTreeRepository, "postgresClient", olsPostgresClient);

        V1GraphRepository graphRepository = new V1GraphRepository();
        ReflectionTestUtils.setField(graphRepository, "postgresClient", postgresClient);

        return new V1OntologyTermRepositoryHandle(
                termRepository, jsTreeRepository, graphRepository, postgresClient);
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
                VALUES (?, 'Ontology', ?, ?, ?, ?, ?, ?, 'ontology', TRUE, ?, ?, ?)
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

    private static void loadAutosuggestFixture(Connection connection) throws IOException, SQLException {
        String sql = "INSERT INTO ols_autosuggest (ontology_id, string) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            addAutosuggestRecords(statement, "/fixtures/ontologies/ontology-fixture.json");
            addAutosuggestRecords(statement, "/fixtures/entities/entity-fixture.json");
            statement.executeBatch();
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ANALYZE ols_autosuggest");
        }
    }

    private static void addAutosuggestRecords(PreparedStatement statement, String resource)
            throws IOException, SQLException {
        JsonObject fixture;
        try (InputStream stream = PostgresIntegrationTestSupport.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Autosuggest integration fixture is missing: " + resource);
            }
            fixture = JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        for (JsonElement element : fixture.getAsJsonArray("records")) {
            JsonObject record = element.getAsJsonObject();
            Set<String> values = new HashSet<>();
            addAutosuggestValues(record.getAsJsonArray("label"), values);
            if (record.has("synonym")) {
                addAutosuggestValues(record.getAsJsonArray("synonym"), values);
            }
            for (String value : values) {
                if (!value.isEmpty()) {
                    statement.setString(1, record.get("ontologyId").getAsString());
                    statement.setString(2, value);
                    statement.addBatch();
                }
            }
        }
    }

    private static void addAutosuggestValues(JsonArray values, Set<String> destination) {
        for (JsonElement value : values) {
            destination.add(value.getAsString());
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
                    subset, related_to, direct_parents, direct_ancestors,
                    has_direct_parents, has_hierarchical_parents, label_for_suggest,
                    filter_tags, filter_domain, "filter_http://example.org/category")
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                statement.setBoolean(19, !record.getAsJsonArray("directParents").isEmpty());
                statement.setBoolean(20, false);
                statement.setString(21, record.getAsJsonArray("label").get(0).getAsString());
                statement.setArray(22, textArray(connection, record.getAsJsonArray("tags")));
                statement.setArray(23, textArray(connection, record.getAsJsonArray("domain")));
                statement.setArray(24, textArray(
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

    private static void loadClassFixture(Connection connection) throws IOException, SQLException {
        JsonObject fixture;
        try (InputStream stream = PostgresIntegrationTestSupport.class.getResourceAsStream(
                "/fixtures/classes/class-fixture.json")) {
            if (stream == null) {
                throw new IllegalStateException("Class integration fixture is missing");
            }
            fixture = JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        String sql = """
                INSERT INTO ols_entities (
                    id, type, iri, ontology_id, _json, is_obsolete, label, search_type,
                    short_form, curie, obo_id, synonym, definition, is_defining_ontology,
                    subset, related_to, direct_parents, hierarchical_parents,
                    direct_ancestors, hierarchical_ancestors,
                    has_direct_parents, has_hierarchical_parents, is_preferred_root,
                    label_for_suggest,
                    filter_tags, filter_domain, "filter_http://example.org/category")
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                statement.setArray(18, textArray(connection, record.getAsJsonArray("hierarchicalParents")));
                statement.setArray(19, textArray(connection, record.getAsJsonArray("directAncestors")));
                statement.setArray(20, textArray(connection, record.getAsJsonArray("hierarchicalAncestors")));
                statement.setBoolean(21, !record.getAsJsonArray("directParents").isEmpty());
                statement.setBoolean(22, !record.getAsJsonArray("hierarchicalParents").isEmpty());
                statement.setBoolean(23, record.has("isPreferredRoot")
                        && record.get("isPreferredRoot").getAsBoolean());
                statement.setString(24, record.getAsJsonArray("label").get(0).getAsString());
                statement.setArray(25, textArray(connection, record.getAsJsonArray("tags")));
                statement.setArray(26, textArray(connection, record.getAsJsonArray("domain")));
                statement.setArray(27, textArray(
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

    private static void loadIndividualFixture(Connection connection) throws IOException, SQLException {
        JsonObject fixture;
        try (InputStream stream = PostgresIntegrationTestSupport.class.getResourceAsStream(
                "/fixtures/individuals/individual-fixture.json")) {
            if (stream == null) {
                throw new IllegalStateException("Individual integration fixture is missing");
            }
            fixture = JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        String sql = """
                INSERT INTO ols_entities (
                    id, type, iri, ontology_id, _json, is_obsolete, label, search_type,
                    short_form, curie, obo_id, synonym, definition, is_defining_ontology,
                    subset, related_to, direct_parents, hierarchical_parents,
                    direct_ancestors, hierarchical_ancestors,
                    label_for_suggest, filter_tags, filter_domain,
                    "filter_http://example.org/category",
                    "filter_http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                statement.setArray(18, textArray(connection, record.getAsJsonArray("hierarchicalParents")));
                statement.setArray(19, textArray(connection, record.getAsJsonArray("directAncestors")));
                statement.setArray(20, textArray(connection, record.getAsJsonArray("hierarchicalAncestors")));
                statement.setString(21, record.getAsJsonArray("label").get(0).getAsString());
                statement.setArray(22, textArray(connection, record.getAsJsonArray("tags")));
                statement.setArray(23, textArray(connection, record.getAsJsonArray("domain")));
                statement.setArray(24, textArray(
                        connection,
                        record.getAsJsonArray("http://example.org/category")));
                statement.setArray(25, textArray(
                        connection,
                        record.getAsJsonArray("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")));
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ANALYZE ols_entities");
        }
    }

    /**
     * Adds the two production embedding column families documented in
     * {@code OlsPostgresClient.sanitizeEmbeddingColumnName}/{@code sanitizeEmbeddingNodeColumnName}
     * directly via SQL (bypassing {@code dataload/create_postgres_schema.py}'s parquet-driven column
     * generation entirely, which is a dataload/production concern, not something a unit fixture
     * should invoke): {@code embeddings_<model>} (plural), one vector per entity's own embedding, on
     * {@code ols_entities}; and {@code embedding_<model>} (singular), one vector per indexed
     * label/curation embedding node, on {@code ols_embedding_nodes}.
     *
     * <p>Every vector below is a simple 4-dimensional value chosen so cosine similarity against the
     * fixed query vector {@code [1,0,0,0]} (used by every fake {@code EmbeddingServiceClient} in the
     * WIT/IT suites) is an exact, hand-checkable fraction — see
     * {@code docs/backend-testing-strategy.md}'s "Implemented V2 LLM-controller baseline" section for
     * the worked cosine-distance/similarity arithmetic each assertion relies on.</p>
     */
    private static void loadV2LlmEmbeddingFixture(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "ALTER TABLE ols_entities ADD COLUMN \"embeddings_" + V2LLM_TEST_MODEL + "\" vector(4)");
            statement.execute(
                    "ALTER TABLE ols_embedding_nodes ADD COLUMN \"embedding_" + V2LLM_TEST_MODEL + "\" vector(4)");
        }

        // ols_entities."embeddings_<model>" -- one embedding per entity, read by getSimilar/
        // getSimilarity/getEmbeddingVector (the "similar to an existing entity" family).
        updateEntityEmbedding(connection, "efo+class+http://example.org/EFO_0001", "[1,0,0,0]");
        updateEntityEmbedding(connection, "efo+class+http://example.org/EFO_0002", "[0,1,0,0]");
        updateEntityEmbedding(connection, "duo+class+http://example.org/DUO_0001", "[-1,0,0,0]");
        updateEntityEmbedding(connection, "efo+property+http://example.org/EFO_0100", "[1,0,0,0]");
        updateEntityEmbedding(connection, "efo+property+http://example.org/EFO_0101", "[0,1,0,0]");

        // ols_embedding_nodes."embedding_<model>" -- one row per indexed label/curation embedding,
        // read by searchByVector/searchByVectorInOntology (the "search by an arbitrary query vector"
        // family). Vectors are distinct (not tied) against the [1,0,0,0] query so cross-type ordering
        // in the /entities/llm_search test is deterministic: EFO_0001 (sim 1.0) > EFO_I100 (sim 0.8)
        // > EFO_0100 (sim 0.6) > EFO_0002 (sim 0.0, CurationEmbedding only).
        insertEmbeddingNode(connection, "test-emb-1", "LabelEmbedding",
                "efo+class+http://example.org/EFO_0001", "[1,0,0,0]");
        insertEmbeddingNode(connection, "test-emb-2", "CurationEmbedding",
                "efo+class+http://example.org/EFO_0002", "[0,1,0,0]");
        insertEmbeddingNode(connection, "test-emb-3", "LabelEmbedding",
                "efo+property+http://example.org/EFO_0100", "[3,4,0,0]");
        insertEmbeddingNode(connection, "test-emb-4", "LabelEmbedding",
                "efo+individual+http://example.org/EFO_I100", "[4,3,0,0]");

        try (Statement statement = connection.createStatement()) {
            statement.execute("ANALYZE ols_entities");
            statement.execute("ANALYZE ols_embedding_nodes");
        }
    }

    private static void updateEntityEmbedding(Connection connection, String entityId, String vectorLiteral)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE ols_entities SET \"embeddings_" + V2LLM_TEST_MODEL + "\" = ?::vector WHERE id = ?")) {
            statement.setString(1, vectorLiteral);
            statement.setString(2, entityId);
            int updated = statement.executeUpdate();
            if (updated != 1) {
                throw new IllegalStateException(
                        "Expected exactly one ols_entities row for id " + entityId + ", updated " + updated);
            }
        }
    }

    private static void insertEmbeddingNode(
            Connection connection, String nodeId, String embeddingType, String entityId, String vectorLiteral)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ols_embedding_nodes (id, type, entity_id, \"embedding_" + V2LLM_TEST_MODEL + "\") "
                        + "VALUES (?, ?, ?, ?::vector)")) {
            statement.setString(1, nodeId);
            statement.setString(2, embeddingType);
            statement.setString(3, entityId);
            statement.setString(4, vectorLiteral);
            statement.executeUpdate();
        }
    }

    /**
     * Builds the dedicated fixture for {@code OlsPostgresClientEmbeddingIT} (the embedding/
     * similarity/vector-search family, i.e. this Tier B rollout's milestone 3). Three independent
     * groups of rows, each isolated under its own ontology id(s) so none can interfere with another:
     *
     * <ol>
     *   <li><b>{@code embtest}/{@code embtest2}, entity-level {@code embeddings_<model>}</b> --
     *       read by {@code getSimilar}/{@code getSimilarity}/{@code getEmbeddingVector}.
     *       {@code EMB_SOURCE} ({@code [1,0,0,0]}) is the fixed query point; {@code
     *       EMB_IDENTICAL_DIR} ({@code [2,0,0,0]}, same direction, cosine similarity 1),
     *       {@code EMB_DIAG} ({@code [4,3,0,0]}, cosine similarity 0.8, the same clean 3-4-5-ratio
     *       value already proven reliable by the {@code V2LLMController} baseline's fixture),
     *       {@code EMB_ORTHO} ({@code [0,1,0,0]}, cosine similarity 0), and {@code EMB_OPPOSITE}
     *       ({@code [-1,0,0,0]}, cosine similarity -1) give a deterministic, hand-checkable
     *       {@code getSimilar} ordering with scores 1.0/0.9/0.5/0.0.
     *       {@code EMB_OBSOLETE_ONLY} is obsolete with an embedding, proving the source lookup's own
     *       {@code is_obsolete = false} filter (not a filter on the result set, which this class does
     *       not apply). {@code EMB_NO_EMBEDDING} has no {@code embeddings_<model>} value at all.
     *       {@code EMB_VECTOR_PARSE} ({@code [-1.5,2,0,12]}) exists purely to stress
     *       {@code getEmbeddingVector}'s bracket-stripping/parsing with a leading negative decimal
     *       and a trailing two-digit integer -- values an off-by-one in the substring bounds would
     *       visibly corrupt. {@code EMB_DUP} exists twice with the identical IRI and type, once in
     *       each ontology ({@code embtest} non-defining, {@code embtest2} defining) -- proving
     *       {@code getSimilar}'s source lookup prefers the defining-ontology row (ordered by
     *       {@code is_defining_ontology DESC NULLS LAST}).</li>
     *   <li><b>{@code vectest}, node-level {@code embedding_<model>}</b> -- read by
     *       {@code searchByVector}. {@code VEC_CLASS_A}/{@code VEC_CLASS_B} are type
     *       {@code VecClass}; {@code VEC_PROPERTY_A} is type {@code VecProperty} with the same
     *       label-embedding vector as {@code VEC_CLASS_A}, present only to prove
     *       {@code hasConcreteEntityType}'s type filter actually excludes it when searching for
     *       {@code VecClass}. {@code VEC_CLASS_B} additionally has a {@code CurationEmbedding} row
     *       closer to the query than its own {@code LabelEmbedding} row, so {@code includeCurations}
     *       measurably changes its best-match score (0.5 excluded vs. 0.9 included) rather than
     *       just adding an otherwise-identical duplicate.</li>
     *   <li><b>{@code vectestonto}/{@code vectestonto2}, node-level {@code embedding_<model>}</b> --
     *       read by {@code searchByVectorInOntology}. Deliberately its own dedicated ontology pair
     *       and types ({@code VecOntoClass}/{@code VecOntoProperty}), distinct from the plain
     *       {@code searchByVector} fixture above, since that method's queries are not scoped by
     *       ontology at all: reusing {@code vectest}'s ontology id or {@code VecClass}/
     *       {@code VecProperty} types here would leak these rows into the plain fixture's result
     *       sets (and vice versa). {@code VEC_ONTO_CLASS} and {@code VEC_ONTO_PROPERTY} each exist
     *       in both ontologies with the same IRI+type; only the {@code vectestonto} (defining)
     *       copies have embedding nodes. {@code isDefiningOntology = true} against
     *       {@code vectestonto} finds the defining copies directly; {@code isDefiningOntology =
     *       false} against {@code vectestonto2} must instead join through the defining copy's
     *       embedding to return the <em>target</em> ({@code vectestonto2}) copy's own id/json -- a
     *       genuinely different query path, not the same rows relabelled. {@code VEC_ONTO_PROPERTY}
     *       has only a {@code CurationEmbedding} node (no {@code LabelEmbedding}), so it is found
     *       only when {@code includeCurations = true}.</li>
     * </ol>
     */
    private static void loadOlsPostgresClientEmbeddingFixture(Connection connection) throws IOException, SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE ols_entities ADD COLUMN \""
                    + "embeddings_" + OLS_POSTGRES_CLIENT_EMBEDDING_MODEL + "\" vector(4)");
            statement.execute("ALTER TABLE ols_entities ADD COLUMN \""
                    + "embeddings_" + OLS_POSTGRES_CLIENT_EMBEDDING_MODEL_PCA16 + "\" vector(4)");
            statement.execute("ALTER TABLE ols_embedding_nodes ADD COLUMN \""
                    + "embedding_" + OLS_POSTGRES_CLIENT_EMBEDDING_MODEL + "\" vector(4)");
        }

        // -- getSimilar / getSimilarity / getEmbeddingVector fixture --
        insertMinimalEntity(connection, "embtest+embclass+http://example.org/EMB_SOURCE",
                "EmbClass", "http://example.org/EMB_SOURCE", "embtest", false, true, "Emb Source");
        insertMinimalEntity(connection, "embtest+embclass+http://example.org/EMB_IDENTICAL_DIR",
                "EmbClass", "http://example.org/EMB_IDENTICAL_DIR", "embtest", false, true, "Emb Identical Direction");
        insertMinimalEntity(connection, "embtest+embclass+http://example.org/EMB_DIAG",
                "EmbClass", "http://example.org/EMB_DIAG", "embtest", false, true, "Emb Diagonal");
        insertMinimalEntity(connection, "embtest+embclass+http://example.org/EMB_ORTHO",
                "EmbClass", "http://example.org/EMB_ORTHO", "embtest", false, true, "Emb Orthogonal");
        insertMinimalEntity(connection, "embtest+embclass+http://example.org/EMB_OPPOSITE",
                "EmbClass", "http://example.org/EMB_OPPOSITE", "embtest", false, true, "Emb Opposite");
        insertMinimalEntity(connection, "embtest+embobsoletesrc+http://example.org/EMB_OBSOLETE_ONLY",
                "EmbObsoleteSrc", "http://example.org/EMB_OBSOLETE_ONLY", "embtest", true, true, "Emb Obsolete Only");
        insertMinimalEntity(connection, "embtest+embnoembedding+http://example.org/EMB_NO_EMBEDDING",
                "EmbNoEmbedding", "http://example.org/EMB_NO_EMBEDDING", "embtest", false, true, "Emb No Embedding");
        // Same type as EMB_NO_EMBEDDING (unlike EMB_SOURCE, which is type EmbClass) so
        // getSimilarity's shared `type` parameter matches both sides of the pair, isolating "the
        // other entity is missing its embedding value" from an unrelated type mismatch.
        insertMinimalEntity(connection, "embtest+embnoembedding+http://example.org/EMB_NO_EMBEDDING_PARTNER",
                "EmbNoEmbedding", "http://example.org/EMB_NO_EMBEDDING_PARTNER", "embtest", false, true,
                "Emb No Embedding Partner");
        insertMinimalEntity(connection, "embtest+embvectorparse+http://example.org/EMB_VECTOR_PARSE",
                "EmbVectorParse", "http://example.org/EMB_VECTOR_PARSE", "embtest", false, true, "Emb Vector Parse");
        insertMinimalEntity(connection, "embtest+embdup+http://example.org/EMB_DUP",
                "EmbDup", "http://example.org/EMB_DUP", "embtest", false, false, "Emb Dup Non Defining");
        insertMinimalEntity(connection, "embtest2+embdup+http://example.org/EMB_DUP",
                "EmbDup", "http://example.org/EMB_DUP", "embtest2", false, true, "Emb Dup Defining");

        updateEmbeddingTestModelVector(connection, "embtest+embclass+http://example.org/EMB_SOURCE", "[1,0,0,0]");
        updateEmbeddingTestModelVector(connection, "embtest+embclass+http://example.org/EMB_IDENTICAL_DIR", "[2,0,0,0]");
        updateEmbeddingTestModelVector(connection, "embtest+embclass+http://example.org/EMB_DIAG", "[4,3,0,0]");
        updateEmbeddingTestModelVector(connection, "embtest+embclass+http://example.org/EMB_ORTHO", "[0,1,0,0]");
        updateEmbeddingTestModelVector(connection, "embtest+embclass+http://example.org/EMB_OPPOSITE", "[-1,0,0,0]");
        updateEmbeddingTestModelVector(connection, "embtest+embobsoletesrc+http://example.org/EMB_OBSOLETE_ONLY", "[1,0,0,0]");
        // EMB_NO_EMBEDDING deliberately gets no embeddings_<model> value -- it stays NULL.
        updateEmbeddingTestModelVector(connection, "embtest+embnoembedding+http://example.org/EMB_NO_EMBEDDING_PARTNER", "[1,0,0,0]");
        updateEmbeddingTestModelVector(connection, "embtest+embvectorparse+http://example.org/EMB_VECTOR_PARSE", "[-1.5,2,0,12]");
        updateEmbeddingTestModelVector(connection, "embtest+embdup+http://example.org/EMB_DUP", "[5,5,0,0]");
        updateEmbeddingTestModelVector(connection, "embtest2+embdup+http://example.org/EMB_DUP", "[9,0,0,0]");

        // -- searchByVector fixture --
        insertMinimalEntity(connection, "vectest+vecclass+http://example.org/VEC_CLASS_A",
                "VecClass", "http://example.org/VEC_CLASS_A", "vectest", false, true, "Vec Class A");
        insertMinimalEntity(connection, "vectest+vecclass+http://example.org/VEC_CLASS_B",
                "VecClass", "http://example.org/VEC_CLASS_B", "vectest", false, true, "Vec Class B");
        insertMinimalEntity(connection, "vectest+vecproperty+http://example.org/VEC_PROPERTY_A",
                "VecProperty", "http://example.org/VEC_PROPERTY_A", "vectest", false, true, "Vec Property A");

        insertEmbeddingNodeForTestModel(connection, "vec-node-1", "LabelEmbedding",
                "vectest+vecclass+http://example.org/VEC_CLASS_A", "[1,0,0,0]");
        insertEmbeddingNodeForTestModel(connection, "vec-node-2", "LabelEmbedding",
                "vectest+vecclass+http://example.org/VEC_CLASS_B", "[0,1,0,0]");
        insertEmbeddingNodeForTestModel(connection, "vec-node-3", "LabelEmbedding",
                "vectest+vecproperty+http://example.org/VEC_PROPERTY_A", "[1,0,0,0]");
        insertEmbeddingNodeForTestModel(connection, "vec-node-4", "CurationEmbedding",
                "vectest+vecclass+http://example.org/VEC_CLASS_B", "[4,3,0,0]");

        // -- searchByVectorInOntology fixture -- a dedicated defining/target ontology pair
        // (vectestonto/vectestonto2) and dedicated types (VecOntoClass/VecOntoProperty), distinct
        // from the plain searchByVector fixture's vectest ontology and VecClass/VecProperty types
        // above: searchByVectorInOntology's isDefiningOntology=true branch filters only by
        // ontology_id (+ type, when concrete), and its "OntologyEntity" (type-filter-disabled) test
        // cases filter only by ontology_id -- so any overlap in either ontology id or type between
        // the two fixture groups would silently leak rows from one group's assertions into the
        // other's.
        insertMinimalEntity(connection, "vectestonto+vecontoclass+http://example.org/VEC_ONTO_CLASS",
                "VecOntoClass", "http://example.org/VEC_ONTO_CLASS", "vectestonto", false, true, "Vec Onto Class");
        insertMinimalEntity(connection, "vectestonto+vecontoproperty+http://example.org/VEC_ONTO_PROPERTY",
                "VecOntoProperty", "http://example.org/VEC_ONTO_PROPERTY", "vectestonto", false, true, "Vec Onto Property");
        insertMinimalEntity(connection, "vectestonto2+vecontoclass+http://example.org/VEC_ONTO_CLASS",
                "VecOntoClass", "http://example.org/VEC_ONTO_CLASS", "vectestonto2", false, false, "Vec Onto Class Target");
        insertMinimalEntity(connection, "vectestonto2+vecontoproperty+http://example.org/VEC_ONTO_PROPERTY",
                "VecOntoProperty", "http://example.org/VEC_ONTO_PROPERTY", "vectestonto2", false, false, "Vec Onto Property Target");

        insertEmbeddingNodeForTestModel(connection, "vec-node-onto-1", "LabelEmbedding",
                "vectestonto+vecontoclass+http://example.org/VEC_ONTO_CLASS", "[1,0,0,0]");
        insertEmbeddingNodeForTestModel(connection, "vec-node-onto-2", "CurationEmbedding",
                "vectestonto+vecontoproperty+http://example.org/VEC_ONTO_PROPERTY", "[1,0,0,0]");

        try (Statement statement = connection.createStatement()) {
            statement.execute("ANALYZE ols_entities");
            statement.execute("ANALYZE ols_embedding_nodes");
        }
    }

    /**
     * Inserts a minimal {@code ols_entities} row: just the columns {@code OlsPostgresClient}'s
     * embedding/vector-search family actually reads (id/type/iri/ontology_id/_json/is_obsolete/
     * is_defining_ontology/label). Every other column keeps its schema default -- there is no
     * hierarchy/search behaviour under test in this fixture, so the fuller column list other
     * loaders in this file populate (direct_parents, related_to, etc.) is unnecessary here.
     */
    private static void insertMinimalEntity(
            Connection connection, String id, String type, String iri, String ontologyId,
            boolean isObsolete, boolean isDefiningOntology, String label) throws IOException, SQLException {
        String json = "{\"id\":\"" + id + "\",\"iri\":\"" + iri + "\",\"label\":\"" + label
                + "\",\"ontologyId\":\"" + ontologyId + "\"}";
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ols_entities (id, type, iri, ontology_id, _json, is_obsolete, is_defining_ontology, label) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, type);
            statement.setString(3, iri);
            statement.setString(4, ontologyId);
            statement.setBytes(5, gzip(json));
            statement.setBoolean(6, isObsolete);
            statement.setBoolean(7, isDefiningOntology);
            statement.setArray(8, connection.createArrayOf("text", new String[] {label}));
            statement.executeUpdate();
        }
    }

    private static void updateEmbeddingTestModelVector(Connection connection, String entityId, String vectorLiteral)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE ols_entities SET \"embeddings_" + OLS_POSTGRES_CLIENT_EMBEDDING_MODEL + "\" = ?::vector WHERE id = ?")) {
            statement.setString(1, vectorLiteral);
            statement.setString(2, entityId);
            int updated = statement.executeUpdate();
            if (updated != 1) {
                throw new IllegalStateException(
                        "Expected exactly one ols_entities row for id " + entityId + ", updated " + updated);
            }
        }
    }

    private static void insertEmbeddingNodeForTestModel(
            Connection connection, String nodeId, String embeddingType, String entityId, String vectorLiteral)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ols_embedding_nodes (id, type, entity_id, \""
                        + "embedding_" + OLS_POSTGRES_CLIENT_EMBEDDING_MODEL + "\") VALUES (?, ?, ?, ?::vector)")) {
            statement.setString(1, nodeId);
            statement.setString(2, embeddingType);
            statement.setString(3, entityId);
            statement.setString(4, vectorLiteral);
            statement.executeUpdate();
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

    public record OlsPostgresClientRepositoryHandle(
            OlsPostgresClient olsPostgresClient,
            PostgresClient postgresClient) implements AutoCloseable {

        @Override
        public void close() {
            postgresClient.close();
        }
    }

    public record TextTaggerRepositoryHandle(
            TextTaggerService textTaggerService,
            OlsSearchClient searchClient,
            PostgresClient postgresClient) implements AutoCloseable {

        @Override
        public void close() {
            textTaggerService.destroy();
            postgresClient.close();
        }
    }

    public record V2LLMRepositoryHandle(
            ClassRepository classRepository,
            PropertyRepository propertyRepository,
            EmbeddingServiceClient embeddingServiceClient,
            OlsPostgresClient olsPostgresClient,
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

    public record HealthCheckRepositoryHandle(
            OntologyRepository ontologyRepository,
            OlsPostgresClient olsPostgresClient,
            PostgresClient postgresClient) implements AutoCloseable {

        @Override
        public void close() {
            postgresClient.close();
        }
    }

    public record SearchClientHandle(
            OlsSearchClient searchClient,
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

    public record V1PropertyRepositoryHandle(
            V1PropertyRepository repository,
            PostgresClient postgresClient) implements AutoCloseable {

        @Override
        public void close() {
            postgresClient.close();
        }
    }

    public record ClassRepositoryHandle(
            ClassRepository repository,
            PostgresClient postgresClient) implements AutoCloseable {

        @Override
        public void close() {
            postgresClient.close();
        }
    }

    public record IndividualRepositoryHandle(
            IndividualRepository repository,
            PostgresClient postgresClient) implements AutoCloseable {

        @Override
        public void close() {
            postgresClient.close();
        }
    }

    public record V1IndividualRepositoryHandle(
            V1IndividualRepository repository,
            PostgresClient postgresClient) implements AutoCloseable {

        @Override
        public void close() {
            postgresClient.close();
        }
    }

    public record V1OntologyIndividualRepositoryHandle(
            V1IndividualRepository individualRepository,
            V1JsTreeRepository jsTreeRepository,
            PostgresClient postgresClient) implements AutoCloseable {

        @Override
        public void close() {
            postgresClient.close();
        }
    }

    public record V1OntologyPropertyRepositoryHandle(
            V1PropertyRepository propertyRepository,
            V1JsTreeRepository jsTreeRepository,
            PostgresClient postgresClient) implements AutoCloseable {

        @Override
        public void close() {
            postgresClient.close();
        }
    }

    public record V1OntologyTermRepositoryHandle(
            V1TermRepository termRepository,
            V1JsTreeRepository jsTreeRepository,
            V1GraphRepository graphRepository,
            PostgresClient postgresClient) implements AutoCloseable {

        @Override
        public void close() {
            postgresClient.close();
        }
    }
}
