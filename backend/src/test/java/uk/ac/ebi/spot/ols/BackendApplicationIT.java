package uk.ac.ebi.spot.ols;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises application auto-configuration that standalone controller tests do not load. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.context-path=/ols4", "server.address=127.0.0.1"})
class BackendApplicationIT {
    @Container
    static final PostgreSQLContainer POSTGRES = PostgresIntegrationTestSupport.newContainer();

    private static final JsonMapper JSON = new JsonMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("ols.postgres.host", POSTGRES::getHost);
        properties.add("ols.postgres.port", () -> POSTGRES.getMappedPort(5432));
        properties.add("ols.postgres.db", POSTGRES::getDatabaseName);
        properties.add("ols.postgres.user", POSTGRES::getUsername);
        properties.add("ols.postgres.password", POSTGRES::getPassword);
        properties.add("ols.postgres.max-pool-size", () -> 3);
        properties.add("ols.postgres.min-idle", () -> 0);
    }

    @BeforeAll
    static void loadFixture() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
    }

    @Test
    void servesDatabaseAndReleaseApisThroughTheApplication() throws Exception {
        assertThat(get("/api/v2/health").statusCode()).isEqualTo(200);
        JsonNode ontology = json(get("/api/ontologies/efo"));
        assertThat(ontology.path("ontologyId").asText()).isEqualTo("efo");
        assertThat(ontology.path("_links").path("terms").path("href").asText())
                .endsWith("/ols4/api/ontologies/efo/terms");
        assertThat(json(get("/api/v2/releases")).has("releases")).isTrue();
    }

    @Test
    void servesOpenApiAndSwaggerUiWithTheAlignedDependencies() throws Exception {
        JsonNode api = json(get("/v3/api-docs"));
        assertThat(api.path("paths").has("/api/v2/classes")).isTrue();
        assertThat(api.path("paths").has("/api/ontologies")).isTrue();
        HttpResponse<String> redirect = get("/swagger-ui-ols4.html");
        assertThat(redirect.statusCode()).isEqualTo(302);
        assertThat(redirect.headers().firstValue("Location").orElseThrow())
                .endsWith("/ols4/swagger-ui/index.html");
        HttpResponse<String> ui = get("/swagger-ui/index.html");
        assertThat(ui.statusCode()).isEqualTo(200);
        assertThat(ui.body()).contains("swagger-ui-bundle.js");
        HttpResponse<String> initializer = get("/swagger-ui/swagger-initializer.js");
        assertThat(initializer.statusCode()).isEqualTo(200);
        assertThat(initializer.body()).contains("SwaggerUIBundle", "/ols4/v3/api-docs/swagger-config");
    }

    @Test
    void initializesListsAndCallsStatelessMcpToolsAgainstTheDatabase() throws Exception {
        HttpResponse<String> initialize = post("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                "protocolVersion":"2025-03-26","capabilities":{},
                "clientInfo":{"name":"ols-application-test","version":"1"}}}
                """);
        JsonNode result = json(initialize).path("result");
        assertThat(initialize.headers().firstValue("Mcp-Session-Id")).isEmpty();
        assertThat(result.path("protocolVersion").asText()).isEqualTo("2025-03-26");
        assertThat(result.path("serverInfo").path("icons").isArray()).isTrue();
        assertThat(get("/api/mcp").statusCode()).isEqualTo(405);
        JsonNode tools = json(post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """)).path("result").path("tools");
        var toolNames = new ArrayList<String>();
        for (JsonNode tool : tools) {
            toolNames.add(tool.path("name").asText());
        }
        assertThat(toolNames).contains("listOntologies");
        JsonNode call = json(post("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
                "name":"listOntologies","arguments":{"lang":"en"}}}
                """)).path("result");
        assertThat(call.path("isError").asBoolean()).isFalse();
        assertThat(call.path("content").get(0).path("text").asText())
                .contains("efo", "duo", "legacy-efo");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String body) throws Exception {
        return http.send(HttpRequest.newBuilder(uri("/api/mcp")).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/ols4" + path);
    }

    private JsonNode json(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }
}
