package uk.ac.ebi.spot.ols.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.coyote.AbstractProtocol;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ActiveProfiles("mcp-connection-lifecycle-test")
@SpringBootTest(classes = McpConnectionLifecycleTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.context-path=/ols4", "server.tomcat.max-connections=32"})
class McpConnectionLifecycleTest {
    @LocalServerPort
    int port;

    @Autowired
    ServletWebServerApplicationContext context;

    @Autowired
    AtomicInteger listeningRequests;

    private final ObjectMapper mapper = new ObjectMapper();

    @Configuration(proxyBeanMethods = false)
    @Profile("mcp-connection-lifecycle-test")
    @EnableAutoConfiguration
    @Import({McpConfig.class, McpIconFilter.class})
    static class Application {
        @Bean
        AtomicInteger listeningRequests() {
            return new AtomicInteger();
        }

        @Bean
        FilterRegistrationBean<Filter> listeningRequestTracker(AtomicInteger listeningRequests) {
            Filter tracker = (request, response, chain) -> {
                chain.doFilter(request, response);
                if ("disconnect-regression".equals(((HttpServletRequest) request).getHeader("X-OLS-MCP-Test"))) {
                    listeningRequests.incrementAndGet();
                }
            };
            return new FilterRegistrationBean<>(tracker);
        }

        @Bean
        ToolCallbackProvider testTools() {
            return MethodToolCallbackProvider.builder().toolObjects(new TestTools()).build();
        }
    }

    static class TestTools {
        @Tool(description = "Return a value without accessing a database")
        public String echo(String value) {
            return value;
        }
    }

    @Test
    void disconnectedListeningStreamsReleaseTomcatConnectionsWithoutDelete() throws Exception {
        long initialConnections = connectionCount();
        List<Socket> streams = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                Reply init = post(null, initialize(i));
                assertThat(init.status()).isEqualTo(200);
                String session = init.session();
                post(session, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
                Socket socket = new Socket("127.0.0.1", port);
                streams.add(socket);
                String sessionHeader = session == null ? "" : "Mcp-Session-Id: " + session + "\r\n";
                socket.getOutputStream().write(("GET /ols4/api/mcp HTTP/1.1\r\nHost: localhost\r\n"
                        + "Accept: text/event-stream\r\nX-OLS-MCP-Test: disconnect-regression\r\n" + sessionHeader
                        + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
            }
            // Wait until every GET has reached the transport before peers send FIN.
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                    assertThat(listeningRequests.get()).isEqualTo(8));
        } finally {
            for (Socket socket : streams) {
                socket.close();
            }
        }
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                // Include the acceptor's reserved slot and any idle SDK client connection
                // already present before this test, rather than assuming a zero baseline.
                assertThat(connectionCount()).as("connections retained after clients disconnect")
                        .isLessThanOrEqualTo(initialConnections));
    }

    @Test
    void normalClientsCanInitializeListAndCallTools() throws Exception {
        Reply init = post(null, initialize(1));
        assertThat(init.status()).isEqualTo(200);
        assertThat(init.session()).isNull();
        JsonNode initialized = mapper.readTree(init.body());
        assertThat(initialized.path("result").path("protocolVersion").asText()).isEqualTo("2025-03-26");
        assertThat(initialized.path("result").path("serverInfo").path("icons").isArray()).isTrue();
        assertThat(post(init.session(), "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}").status())
                .isEqualTo(202);
        Reply tools = post(init.session(), "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");
        assertThat(tools.status()).isEqualTo(200);
        assertThat(jsonRpc(tools.body()).path("result").path("tools").get(0).path("name").asText()).isEqualTo("echo");
        Reply call = post(init.session(), "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"echo\",\"arguments\":{\"value\":\"connection-check\"}}}");
        assertThat(call.status()).isEqualTo(200);
        assertThat(jsonRpc(call.body()).path("result").path("content").get(0).path("text").asText())
                .contains("connection-check");
    }

    @Test
    void listeningGetReturnsMethodNotAllowed() throws Exception {
        var connection = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + port + "/ols4/api/mcp").toURL().openConnection();
        connection.setRequestProperty("Connection", "close");
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        try {
            assertThat(connection.getResponseCode()).isEqualTo(405);
        } finally {
            connection.disconnect();
        }
    }

    @Test
    void existingSdkClientCanListAndCallTools() {
        var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                .endpoint("/ols4/api/mcp").build();
        var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(5)).build();
        try {
            assertThat(client.initialize().serverInfo()).isNotNull();
            assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name).contains("echo");
            assertThat(client.callTool(new McpSchema.CallToolRequest("echo", Map.of("value", "sdk-check")))
                    .isError()).isFalse();
        } finally {
            client.close();
        }
    }

    private long connectionCount() {
        TomcatWebServer server = (TomcatWebServer) context.getWebServer();
        return ((AbstractProtocol<?>) server.getTomcat().getConnector().getProtocolHandler()).getConnectionCount();
    }

    private String initialize(int id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"initialize\",\"params\":{"
                + "\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"connection-lifecycle-test\",\"version\":\"1.0\"}}}";
    }

    private Reply post(String session, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + port + "/ols4/api/mcp").toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        connection.setRequestProperty("Connection", "close");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json, text/event-stream");
        connection.setRequestProperty("MCP-Protocol-Version", "2025-03-26");
        if (session != null) {
            connection.setRequestProperty("Mcp-Session-Id", session);
        }
        try {
            connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            int status = connection.getResponseCode();
            var input = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            String response = input == null ? "" : new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return new Reply(status, connection.getHeaderField("Mcp-Session-Id"), response);
        } finally {
            connection.disconnect();
        }
    }

    private JsonNode jsonRpc(String body) throws Exception {
        if (body.stripLeading().startsWith("{")) {
            return mapper.readTree(body);
        }
        for (String line : body.split("\n")) {
            if (line.startsWith("data:")) {
                return mapper.readTree(line.substring(5).strip());
            }
        }
        throw new AssertionError("No JSON-RPC response");
    }

    private record Reply(int status, String session, String body) {}
}
