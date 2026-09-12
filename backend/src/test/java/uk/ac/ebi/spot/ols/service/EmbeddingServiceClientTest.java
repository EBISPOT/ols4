package uk.ac.ebi.spot.ols.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit coverage for {@link EmbeddingServiceClient}'s own internals: the unset-URL early
 * returns, {@code embedTextsFromService}'s binary response parsing (success, missing header, wrong
 * byte count, non-200 status, malformed header, interrupted request), the real {@code applyPca}
 * math, and {@code getAvailableModels()}'s response-parsing/PCA-inclusion-filtering logic.
 *
 * <p><b>HTTP seam.</b> {@code embeddingServiceUrl} is null/unset in every Spring test context in
 * this repo (see {@code V2LLMControllerTest}/{@code V2LLMControllerWIT}), which means the real
 * {@code embedTextsFromService}/{@code getAvailableModels()} HTTP-calling code paths are otherwise
 * unreachable without a live embedding microservice. Rather than faking the whole class (as the
 * controller layer already does — see the "V2 LLM-controller baseline" section of
 * {@code docs/backend-testing-strategy.md}) or reflecting into {@code applyPca} directly, this class
 * stands up a real {@code com.sun.net.httpserver.HttpServer} bound to {@code 127.0.0.1} on an
 * ephemeral port as a fake embedding microservice, and points the real, unmodified
 * {@link EmbeddingServiceClient} at it via {@code ReflectionTestUtils.setField} — the same
 * private-field-injection idiom already used throughout
 * {@code PostgresIntegrationTestSupport} (e.g. wiring {@code EmbeddingServiceClient.postgresClient}
 * for the V2 LLM controller-IT fixture) — rather than a new mechanism. This exercises the real HTTP
 * request/response code, the real binary little-endian float parsing, and the real {@code applyPca}
 * mean-centered dot product end to end, not a mock standing in for any of it.
 *
 * <p><b>PCA model injection.</b> {@code loadPcaModels()} itself is real, Postgres-backed logic
 * (covered separately by {@code EmbeddingServiceClientIT} against a disposable database) and is
 * deliberately not re-exercised here. To reach {@code applyPca} and the PCA branches of
 * {@code embedTexts}/{@code getAvailableModels()} without a database, this class uses plain
 * {@code java.lang.reflect} to construct the private {@code EmbeddingServiceClient.PcaModel} nested
 * type directly and insert it into the private {@code pcaModels} map — a small, explicit
 * white-box step, not a stand-in for the class's real behaviour once a model is present.
 */
class EmbeddingServiceClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // Unset embeddingServiceUrl: early returns (no HTTP seam needed)
    // ------------------------------------------------------------------

    @Test
    void getAvailableModelsReturnsEmptyListWhenUrlIsNull() {
        EmbeddingServiceClient client = new EmbeddingServiceClient();
        // embeddingServiceUrl left at its default (null): never explicitly set.

        assertEquals(List.of(), client.getAvailableModels());
    }

    @Test
    void getAvailableModelsReturnsEmptyListWhenUrlIsEmpty() {
        EmbeddingServiceClient client = new EmbeddingServiceClient();
        ReflectionTestUtils.setField(client, "embeddingServiceUrl", "");

        assertEquals(List.of(), client.getAvailableModels());
    }

    @Test
    void embedTextThrowsWhenUrlIsNull() {
        EmbeddingServiceClient client = new EmbeddingServiceClient();

        IOException exception = assertThrows(IOException.class, () -> client.embedText("model-a", "hello"));
        assertEquals("Embedding service URL is not configured", exception.getMessage());
    }

    @Test
    void embedTextsThrowsWhenUrlIsEmpty() {
        EmbeddingServiceClient client = new EmbeddingServiceClient();
        ReflectionTestUtils.setField(client, "embeddingServiceUrl", "");

        IOException exception = assertThrows(
                IOException.class, () -> client.embedTexts("model-a", List.of("hello")));
        assertEquals("Embedding service URL is not configured", exception.getMessage());
    }

    // ------------------------------------------------------------------
    // embedTextsFromService: success path (binary response parsing)
    // ------------------------------------------------------------------

    @Test
    void embedTextReturnsSingleParsedEmbedding() throws Exception {
        ScriptedEmbedHandler handler = new ScriptedEmbedHandler();
        handler.dimension = 3;
        handler.embeddings = new float[][] {{1.5f, -2.25f, 100.0f}};
        startServer(handler, null);

        EmbeddingServiceClient client = newClientPointedAt(baseUrl());

        float[] result = client.embedText("model-a", "hello");

        assertArrayEquals(new float[] {1.5f, -2.25f, 100.0f}, result, 0.0f);
        assertEquals("model-a", handler.capturedModel);
        assertEquals(1, handler.capturedTextCount);
    }

    @Test
    void embedTextsParsesMultipleRowsInOrder() throws Exception {
        ScriptedEmbedHandler handler = new ScriptedEmbedHandler();
        handler.dimension = 2;
        handler.embeddings = new float[][] {{1.0f, 2.0f}, {3.0f, 4.0f}, {-5.5f, 6.75f}};
        startServer(handler, null);

        EmbeddingServiceClient client = newClientPointedAt(baseUrl());

        float[][] result = client.embedTexts("model-a", List.of("one", "two", "three"));

        assertEquals(3, result.length);
        assertArrayEquals(new float[] {1.0f, 2.0f}, result[0], 0.0f);
        assertArrayEquals(new float[] {3.0f, 4.0f}, result[1], 0.0f);
        assertArrayEquals(new float[] {-5.5f, 6.75f}, result[2], 0.0f);
        assertEquals(3, handler.capturedTextCount);
    }

    // ------------------------------------------------------------------
    // embedTextsFromService: error paths
    // ------------------------------------------------------------------

    @Test
    void embedTextThrowsWhenDimensionHeaderIsMissing() throws Exception {
        ScriptedEmbedHandler handler = new ScriptedEmbedHandler();
        handler.dimension = null; // omit x-embedding-dim entirely
        handler.body = new byte[0];
        startServer(handler, null);

        EmbeddingServiceClient client = newClientPointedAt(baseUrl());

        IOException exception = assertThrows(IOException.class, () -> client.embedText("model-a", "hello"));
        assertEquals("Missing x-embedding-dim header in response", exception.getMessage());
    }

    @Test
    void embedTextThrowsWhenResponseByteCountDoesNotMatchDeclaredDimension() throws Exception {
        ScriptedEmbedHandler handler = new ScriptedEmbedHandler();
        handler.dimension = 4; // declares 4 floats (16 bytes) but the body only has 2 floats' worth
        handler.body = encodeFloats(1.0f, 2.0f);
        startServer(handler, null);

        EmbeddingServiceClient client = newClientPointedAt(baseUrl());

        IOException exception = assertThrows(IOException.class, () -> client.embedText("model-a", "hello"));
        assertTrue(exception.getMessage().contains("Unexpected response size"), exception.getMessage());
        assertTrue(exception.getMessage().contains("8 bytes"), exception.getMessage());
        assertTrue(exception.getMessage().contains("expected 16 bytes"), exception.getMessage());
    }

    @Test
    void embedTextThrowsWithBodyWhenServiceReturnsNon200() throws Exception {
        ScriptedEmbedHandler handler = new ScriptedEmbedHandler();
        handler.statusCode = 503;
        handler.errorBody = "embedding backend unavailable";
        startServer(handler, null);
        String url = baseUrl();

        EmbeddingServiceClient client = newClientPointedAt(url);

        IOException exception = assertThrows(IOException.class, () -> client.embedText("model-a", "hello"));
        assertTrue(exception.getMessage().contains("HTTP 503"), exception.getMessage());
        assertTrue(exception.getMessage().contains(url), exception.getMessage());
        assertTrue(exception.getMessage().contains("embedding backend unavailable"), exception.getMessage());
    }

    @Test
    void embedTextThrowsWhenDimensionHeaderIsNotANumber() throws Exception {
        ScriptedEmbedHandler handler = new ScriptedEmbedHandler();
        handler.dimensionHeaderOverride = "not-a-number";
        handler.body = new byte[0];
        startServer(handler, null);

        EmbeddingServiceClient client = newClientPointedAt(baseUrl());

        IOException exception = assertThrows(IOException.class, () -> client.embedText("model-a", "hello"));
        assertEquals("Invalid dimension in x-embedding-dim header", exception.getMessage());
        assertInstanceOf(NumberFormatException.class, exception.getCause());
    }

    @Test
    void interruptedRequestSurfacesAsIOExceptionWithInterruptedCause() throws Exception {
        ScriptedEmbedHandler handler = new ScriptedEmbedHandler();
        handler.dimension = 1;
        handler.embeddings = new float[][] {{1.0f}};
        handler.delayMillis = 3000; // keep httpClient.send() blocked long enough to interrupt it
        startServer(handler, null);

        EmbeddingServiceClient client = newClientPointedAt(baseUrl());

        AtomicReference<Throwable> captured = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                client.embedText("model-a", "hello");
            } catch (Throwable t) {
                captured.set(t);
            }
        });
        worker.start();
        Thread.sleep(500); // let the worker enter the blocking HttpClient.send() call
        worker.interrupt();
        worker.join(5000);

        assertFalse(worker.isAlive(), "worker thread should have finished after being interrupted");
        Throwable thrown = captured.get();
        assertNotNull(thrown, "expected embedText to surface an exception once interrupted");
        assertInstanceOf(IOException.class, thrown);
        assertEquals("Request interrupted", thrown.getMessage());
        assertInstanceOf(InterruptedException.class, thrown.getCause());
    }

    // ------------------------------------------------------------------
    // embedText / embedTexts delegation and PCA vs non-PCA dispatch
    // ------------------------------------------------------------------

    @Test
    void embedTextDelegatesToEmbedTextsFirstElement() throws Exception {
        ScriptedEmbedHandler handler = new ScriptedEmbedHandler();
        handler.dimension = 2;
        handler.embeddings = new float[][] {{9.0f, 8.0f}};
        startServer(handler, null);

        EmbeddingServiceClient client = newClientPointedAt(baseUrl());

        assertArrayEquals(new float[] {9.0f, 8.0f}, client.embedText("model-a", "hello"), 0.0f);
    }

    @Test
    void nonPcaModelDispatchesModelNameUnchangedAndAppliesNoTransform() throws Exception {
        ScriptedEmbedHandler handler = new ScriptedEmbedHandler();
        handler.dimension = 3;
        handler.embeddings = new float[][] {{2.0f, 5.0f, 10.0f}};
        startServer(handler, null);

        // pcaModels map has no entry for "plain-model": embedTexts must send the model name as-is
        // and return the service's raw embedding, with no PCA transform applied.
        EmbeddingServiceClient client = newClientPointedAt(baseUrl());

        float[] result = client.embedText("plain-model", "hello");

        assertEquals("plain-model", handler.capturedModel);
        assertArrayEquals(new float[] {2.0f, 5.0f, 10.0f}, result, 0.0f);
    }

    /**
     * Hand-computed PCA fixture shared by the applyPca-math and getAvailableModels-filtering tests
     * below: mean {@code [1,2,3]}, components {@code [[1,0],[0,1],[1,1]]} (3 features, 2
     * components). For a raw base-model embedding of {@code [2,5,10]}:
     * <pre>
     * j=0: (2-1)*1 + (5-2)*0 + (10-3)*1 = 1 + 0 + 7 = 8
     * j=1: (2-1)*0 + (5-2)*1 + (10-3)*1 = 0 + 3 + 7 = 10
     * </pre>
     * giving expected PCA output {@code [8, 10]}. A second row, the mean itself ({@code [1,2,3]}),
     * must transform to exactly {@code [0, 0]} (every term's {@code embedding[i] - mean[i]} is zero).
     */
    private static final double[] PCA_MEAN = {1.0, 2.0, 3.0};
    private static final double[][] PCA_COMPONENTS = {{1.0, 0.0}, {0.0, 1.0}, {1.0, 1.0}};

    @Test
    void pcaModelDispatchesBaseModelNameAndAppliesHandComputedTransform() throws Exception {
        ScriptedEmbedHandler handler = new ScriptedEmbedHandler();
        handler.dimension = 3;
        handler.embeddings = new float[][] {{2.0f, 5.0f, 10.0f}, {1.0f, 2.0f, 3.0f}};
        startServer(handler, null);

        EmbeddingServiceClient client = newClientPointedAt(baseUrl());
        putPcaModel(client, "base-model_pca2", "base-model", 2, PCA_MEAN, PCA_COMPONENTS);

        float[][] result = client.embedTexts("base-model_pca2", List.of("hello", "world"));

        // The service must have been called with the PCA model's *base* model name, not the
        // PCA-suffixed one requested by the caller.
        assertEquals("base-model", handler.capturedModel);
        assertEquals(2, handler.capturedTextCount);

        assertArrayEquals(new float[] {8.0f, 10.0f}, result[0], 1e-4f);
        assertArrayEquals(new float[] {0.0f, 0.0f}, result[1], 1e-4f);
    }

    // ------------------------------------------------------------------
    // getAvailableModels(): response parsing and PCA-inclusion filtering
    // ------------------------------------------------------------------

    @Test
    void getAvailableModelsReturnsEmptyListWhenTheServiceIsUnreachable() throws Exception {
        // embeddingServiceUrl points at a loopback port nothing is listening on, so
        // httpClient.send() throws a real IOException ("connection refused"). This exercises
        // getAvailableModels()'s outer catch(Exception) fallback -- distinct from the unset-URL
        // early return -- proving the method degrades to an empty list rather than propagating a
        // network failure. Binding then immediately closing a ServerSocket reserves an ephemeral
        // port that is guaranteed free (unlike a hardcoded port number) and guaranteed refused
        // (unlike an HttpServer created but never started, whose socket stays bound and would just
        // hang instead of refusing the connection).
        int unusedPort;
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
            unusedPort = probe.getLocalPort();
        }

        EmbeddingServiceClient client = newClientPointedAt("http://127.0.0.1:" + unusedPort);

        assertEquals(List.of(), client.getAvailableModels());
    }

    @Test
    void getAvailableModelsReturnsServiceModelsWhenNoPcaModelsAreLoaded() throws Exception {
        ScriptedModelsHandler modelsHandler = new ScriptedModelsHandler();
        modelsHandler.models = List.of("model-a", "model-b");
        startServer(null, modelsHandler);

        EmbeddingServiceClient client = newClientPointedAt(baseUrl());

        List<String> models = client.getAvailableModels();

        assertEquals(2, models.size());
        assertTrue(models.contains("model-a"));
        assertTrue(models.contains("model-b"));
    }

    @Test
    void getAvailableModelsIncludesPcaVariantWhenItsBaseModelIsAvailable() throws Exception {
        ScriptedModelsHandler modelsHandler = new ScriptedModelsHandler();
        modelsHandler.models = List.of("base-model");
        startServer(null, modelsHandler);

        EmbeddingServiceClient client = newClientPointedAt(baseUrl());
        putPcaModel(client, "base-model_pca2", "base-model", 2, PCA_MEAN, PCA_COMPONENTS);

        List<String> models = client.getAvailableModels();

        assertTrue(models.contains("base-model"));
        assertTrue(models.contains("base-model_pca2"));
    }

    @Test
    void getAvailableModelsExcludesPcaVariantWhenItsBaseModelIsUnavailable() throws Exception {
        ScriptedModelsHandler modelsHandler = new ScriptedModelsHandler();
        modelsHandler.models = List.of("some-other-model"); // does not include "unavailable-model"
        startServer(null, modelsHandler);

        EmbeddingServiceClient client = newClientPointedAt(baseUrl());
        putPcaModel(client, "unavailable-model_pca2", "unavailable-model", 2, PCA_MEAN, PCA_COMPONENTS);

        List<String> models = client.getAvailableModels();

        assertFalse(models.contains("unavailable-model_pca2"));
    }

    @Test
    void getAvailableModelsExcludesPca16VariantEvenWhenItsBaseModelIsAvailable() throws Exception {
        // Covers the first (reachable) pca16 exclusion guard: `!entry.getKey().contains("pca16")` in
        // the inclusion loop. A second, later `models.removeIf(m -> m.contains("pca16"))` exists in
        // the production code but is unreachable dead code given this guard already prevents any
        // pca16-named entry from ever being added -- see the class-level note and
        // docs/backend-testing-strategy.md's EmbeddingServiceClient baseline for the empirical basis
        // of that conclusion (not a defect; left as pre-existing per the defect workflow).
        ScriptedModelsHandler modelsHandler = new ScriptedModelsHandler();
        modelsHandler.models = List.of("base-model");
        startServer(null, modelsHandler);

        EmbeddingServiceClient client = newClientPointedAt(baseUrl());
        putPcaModel(client, "base-model_pca16", "base-model", 16, PCA_MEAN, PCA_COMPONENTS);

        List<String> models = client.getAvailableModels();

        assertTrue(models.contains("base-model"));
        assertFalse(models.contains("base-model_pca16"));
    }

    @Test
    void getAvailableModelsReturnsEmptyListWhenModelsEndpointReturnsNon200() throws Exception {
        // Distinct from getAvailableModelsReturnsEmptyListWhenTheServiceIsUnreachable: this is a
        // real HTTP response, just not status 200, so json parsing is skipped and serviceModels
        // stays empty -- no exception is thrown, this is not the catch(Exception) branch.
        ScriptedModelsHandler modelsHandler = new ScriptedModelsHandler();
        modelsHandler.statusCode = 503;
        modelsHandler.rawBody = "unavailable";
        startServer(null, modelsHandler);

        EmbeddingServiceClient client = newClientPointedAt(baseUrl());

        assertEquals(List.of(), client.getAvailableModels());
    }

    @Test
    void getAvailableModelsTreatsAMissingModelsKeyAsNoServiceModels() throws Exception {
        ScriptedModelsHandler modelsHandler = new ScriptedModelsHandler();
        modelsHandler.rawBody = "{}"; // valid JSON object, but no "models" key at all
        startServer(null, modelsHandler);

        EmbeddingServiceClient client = newClientPointedAt(baseUrl());

        assertEquals(List.of(), client.getAvailableModels());
    }

    @Test
    void getAvailableModelsTreatsANonArrayModelsFieldAsNoServiceModels() throws Exception {
        ScriptedModelsHandler modelsHandler = new ScriptedModelsHandler();
        modelsHandler.rawBody = "{\"models\": \"not-an-array\"}";
        startServer(null, modelsHandler);

        EmbeddingServiceClient client = newClientPointedAt(baseUrl());

        assertEquals(List.of(), client.getAvailableModels());
    }

    @Test
    void getAvailableModelsSkipsNonPrimitiveModelsArrayElements() throws Exception {
        ScriptedModelsHandler modelsHandler = new ScriptedModelsHandler();
        modelsHandler.rawBody = "{\"models\": [\"model-a\", {\"nested\": true}, \"model-b\"]}";
        startServer(null, modelsHandler);

        EmbeddingServiceClient client = newClientPointedAt(baseUrl());

        List<String> models = client.getAvailableModels();

        assertEquals(2, models.size());
        assertTrue(models.contains("model-a"));
        assertTrue(models.contains("model-b"));
    }

    // ------------------------------------------------------------------
    // Test infrastructure
    // ------------------------------------------------------------------

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static EmbeddingServiceClient newClientPointedAt(String url) {
        EmbeddingServiceClient client = new EmbeddingServiceClient();
        ReflectionTestUtils.setField(client, "embeddingServiceUrl", url);
        return client;
    }

    /**
     * Starts a fake embedding microservice on an ephemeral loopback port. {@code rootHandler}
     * answers {@code POST /} (the embed endpoint {@code embedTextsFromService} posts to directly at
     * {@code embeddingServiceUrl}); {@code modelsHandler} answers {@code GET /models}
     * ({@code getAvailableModels()}'s endpoint). Either may be null if the test does not need it.
     */
    private void startServer(HttpHandler rootHandler, HttpHandler modelsHandler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        if (rootHandler != null) {
            server.createContext("/", rootHandler);
        }
        if (modelsHandler != null) {
            server.createContext("/models", modelsHandler);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    private static byte[] encodeFloats(float... values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    private static byte[] encodeEmbeddings(float[][] embeddings) {
        int texts = embeddings.length;
        int dimension = embeddings[0].length;
        ByteBuffer buffer = ByteBuffer.allocate(texts * dimension * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float[] row : embeddings) {
            for (float value : row) {
                buffer.putFloat(value);
            }
        }
        return buffer.array();
    }

    /**
     * Constructs the private {@code EmbeddingServiceClient.PcaModel} nested type via reflection and
     * inserts it into the client's private {@code pcaModels} map. See the class-level Javadoc for why
     * this white-box step is needed instead of running the real (Postgres-backed) {@code
     * loadPcaModels()} in this Docker-free test class.
     */
    private static void putPcaModel(
            EmbeddingServiceClient client, String name, String baseModelName, int nComponents,
            double[] mean, double[][] components) throws ReflectiveOperationException {
        Class<?> pcaModelClass = Class.forName("uk.ac.ebi.spot.ols.service.EmbeddingServiceClient$PcaModel");
        Constructor<?> constructor = pcaModelClass.getDeclaredConstructor(
                String.class, int.class, double[].class, double[][].class);
        constructor.setAccessible(true);
        Object pcaModel = constructor.newInstance(baseModelName, nComponents, mean, components);

        Field pcaModelsField = EmbeddingServiceClient.class.getDeclaredField("pcaModels");
        pcaModelsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> pcaModels = (Map<String, Object>) pcaModelsField.get(client);
        pcaModels.put(name, pcaModel);
    }

    /**
     * Fake embed endpoint (the root {@code "/"} context) that captures the request it received and
     * answers according to whichever fields the test configured before calling
     * {@link #startServer}.
     */
    private static final class ScriptedEmbedHandler implements HttpHandler {
        volatile String capturedModel;
        volatile int capturedTextCount;

        volatile int statusCode = 200;
        /** Dimension advertised via the x-embedding-dim header; null omits the header entirely. */
        volatile Integer dimension;
        /** Overrides the literal header value sent (takes precedence over {@link #dimension}). */
        volatile String dimensionHeaderOverride;
        /** Success-path body; ignored if {@link #embeddings} is set. */
        volatile byte[] body = new byte[0];
        /** Convenience: when set, {@link #body} and {@link #dimension} are derived from it. */
        volatile float[][] embeddings;
        volatile String errorBody = "";
        volatile long delayMillis;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            String requestJson = new String(requestBytes, StandardCharsets.UTF_8);
            JsonObject request = JsonParser.parseString(requestJson).getAsJsonObject();
            capturedModel = request.get("model").getAsString();
            JsonArray texts = request.getAsJsonArray("text");
            capturedTextCount = texts != null ? texts.size() : 0;

            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            byte[] responseBytes;
            if (statusCode != 200) {
                responseBytes = errorBody.getBytes(StandardCharsets.UTF_8);
            } else if (embeddings != null) {
                responseBytes = encodeEmbeddings(embeddings);
            } else {
                responseBytes = body;
            }

            if (statusCode == 200) {
                String headerValue = dimensionHeaderOverride != null
                        ? dimensionHeaderOverride
                        : (dimension != null ? String.valueOf(dimension) : null);
                if (headerValue != null) {
                    exchange.getResponseHeaders().add("x-embedding-dim", headerValue);
                }
            }

            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream responseStream = exchange.getResponseBody()) {
                responseStream.write(responseBytes);
            }
        }
    }

    /**
     * Fake {@code GET /models} endpoint. By default answers {@code 200 {"models": [...]}} built
     * from {@link #models}; setting {@link #rawBody} sends that string verbatim instead (for
     * malformed-response-shape tests), and {@link #statusCode} controls the HTTP status.
     */
    private static final class ScriptedModelsHandler implements HttpHandler {
        volatile List<String> models = List.of();
        volatile int statusCode = 200;
        volatile String rawBody;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String body;
            if (rawBody != null) {
                body = rawBody;
            } else {
                JsonObject responseJson = new JsonObject();
                JsonArray modelsArray = new JsonArray();
                models.forEach(modelsArray::add);
                responseJson.add("models", modelsArray);
                body = responseJson.toString();
            }

            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream responseStream = exchange.getResponseBody()) {
                responseStream.write(responseBytes);
            }
        }
    }
}
