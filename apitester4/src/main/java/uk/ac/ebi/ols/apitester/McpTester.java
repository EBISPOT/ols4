package uk.ac.ebi.ols.apitester;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import com.google.gson.*;

/**
 * Tests MCP (Model Context Protocol) functionality by calling MCP tools
 * via the streamable HTTP protocol endpoint.
 */
public class McpTester {

    private final String baseUrl;
    private final String outDir;
    private final Gson gson;
    private String sessionId;
    private int requestId = 1;

    public McpTester(String baseUrl, String outDir) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.outDir = outDir;
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    public boolean test() throws IOException {
        System.out.println("Starting MCP tests...");

        boolean success = true;

        // Initialize MCP session
        if (!initializeSession()) {
            System.out.println("Failed to initialize MCP session");
            return false;
        }

        // Get available tools
        JsonElement toolsList = listTools();
        write(outDir + "/mcp/tools.json", toolsList);

        if (toolsList == null || !toolsList.isJsonObject()) {
            System.out.println("Failed to list MCP tools");
            return false;
        }

        // Test each MCP tool
        try {
            // Test listOntologies
            if (!testListOntologies()) {
                success = false;
            }

            // Test search
            if (!testSearch()) {
                success = false;
            }

            // Test searchClasses
            if (!testSearchClasses()) {
                success = false;
            }

            // Test fetch with a known entity
            if (!testFetch()) {
                success = false;
            }

            // Test getAncestors
            if (!testGetAncestors()) {
                success = false;
            }

            // Test getDescendants
            if (!testGetDescendants()) {
                success = false;
            }

            // Test listEmbeddingModels
            if (!testListEmbeddingModels()) {
                success = false;
            }

            // Test searchWithEmbeddingModel (if models available)
            if (!testSearchWithEmbeddingModel()) {
                success = false;
            }

            // Test searchClassesWithEmbeddingModel (if models available)
            if (!testSearchClassesWithEmbeddingModel()) {
                success = false;
            }

            // Test getSimilarClasses (if models available)
            if (!testGetSimilarClasses()) {
                success = false;
            }

            // Test getClassSimilarity (if models available)
            if (!testGetClassSimilarity()) {
                success = false;
            }

        } catch (Exception e) {
            System.out.println("Error during MCP tests: " + e.getMessage());
            e.printStackTrace();
            success = false;
        }

        return success;
    }

    private boolean initializeSession() throws IOException {
        System.out.println("Initializing MCP session...");

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", requestId++);
        request.addProperty("method", "initialize");

        JsonObject params = new JsonObject();
        params.addProperty("protocolVersion", "2025-03-26");

        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("name", "ols4-apitester");
        clientInfo.addProperty("version", "1.0.0");
        params.add("clientInfo", clientInfo);

        JsonObject capabilities = new JsonObject();
        params.add("capabilities", capabilities);

        request.add("params", params);

        JsonElement response = sendMcpRequest(request);

        if (response != null && response.isJsonObject()) {
            JsonObject responseObj = response.getAsJsonObject();
            if (responseObj.has("result")) {
                System.out.println("MCP session initialized successfully");

                // Send initialized notification
                JsonObject notification = new JsonObject();
                notification.addProperty("jsonrpc", "2.0");
                notification.addProperty("method", "notifications/initialized");
                sendMcpRequest(notification);

                return true;
            } else if (responseObj.has("error")) {
                System.out.println("MCP initialization error: " + responseObj.get("error"));
            }
        }
        return false;
    }

    private JsonElement listTools() throws IOException {
        System.out.println("Listing MCP tools...");

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", requestId++);
        request.addProperty("method", "tools/list");
        request.add("params", new JsonObject());

        JsonElement response = sendMcpRequest(request);
        return response;
    }

    private boolean testListOntologies() throws IOException {
        System.out.println("Testing listOntologies...");

        JsonObject args = new JsonObject();
        // lang is optional, defaults to "en"

        JsonElement result = callTool("listOntologies", args);
        write(outDir + "/mcp/listOntologies.json", result);

        if (result == null) {
            System.out.println("listOntologies returned null");
            return false;
        }

        System.out.println("listOntologies: SUCCESS");
        return true;
    }

    private boolean testSearch() throws IOException {
        System.out.println("Testing search...");

        JsonObject args = new JsonObject();
        args.addProperty("query", "cell");

        JsonElement result = callTool("search", args);
        write(outDir + "/mcp/search.json", result);

        if (result == null) {
            System.out.println("search returned null");
            return false;
        }

        System.out.println("search: SUCCESS");
        return true;
    }

    private boolean testSearchClasses() throws IOException {
        System.out.println("Testing searchClasses...");

        JsonObject args = new JsonObject();
        args.addProperty("query", "cell");
        args.addProperty("pageSize", 10);

        JsonElement result = callTool("searchClasses", args);
        write(outDir + "/mcp/searchClasses.json", result);

        if (result == null) {
            System.out.println("searchClasses returned null");
            return false;
        }

        // Also test with ontologyId filter
        JsonObject argsWithOntology = new JsonObject();
        argsWithOntology.addProperty("query", "research");
        argsWithOntology.addProperty("ontologyId", "duo");
        argsWithOntology.addProperty("pageSize", 10);

        JsonElement resultWithOntology = callTool("searchClasses", argsWithOntology);
        write(outDir + "/mcp/searchClasses_withOntology.json", resultWithOntology);

        System.out.println("searchClasses: SUCCESS");
        return true;
    }

    private boolean testFetch() throws IOException {
        System.out.println("Testing fetch...");

        // First, do a search to get a valid entity ID
        JsonObject searchArgs = new JsonObject();
        searchArgs.addProperty("query", "data use permission");

        JsonElement searchResult = callTool("search", searchArgs);

        String entityId = null;
        if (searchResult != null && searchResult.isJsonObject()) {
            JsonObject resultObj = searchResult.getAsJsonObject();
            if (resultObj.has("result")) {
                JsonObject result = resultObj.get("result").getAsJsonObject();
                if (result.has("content") && result.get("content").isJsonArray()) {
                    JsonArray content = result.get("content").getAsJsonArray();
                    if (content.size() > 0) {
                        JsonObject firstContent = content.get(0).getAsJsonObject();
                        if (firstContent.has("text")) {
                            // Parse the text content to extract an ID
                            String text = firstContent.get("text").getAsString();
                            JsonArray items = JsonParser.parseString(text).getAsJsonArray();
                            if (items.size() > 0) {
                                JsonObject firstItem = items.get(0).getAsJsonObject();
                                if (firstItem.has("id")) {
                                    entityId = firstItem.get("id").getAsString();
                                }
                            }
                        }
                    }
                }
            }
        }

        if (entityId == null) {
            // Fallback to a known DUO entity
            entityId = "duo+http://purl.obolibrary.org/obo/DUO_0000001";
            System.out.println("Using fallback entity ID: " + entityId);
        } else {
            System.out.println("Using entity ID from search: " + entityId);
        }

        JsonObject args = new JsonObject();
        args.addProperty("id", entityId);

        JsonElement result = callTool("fetch", args);
        write(outDir + "/mcp/fetch.json", result);

        if (result == null) {
            System.out.println("fetch returned null");
            return false;
        }

        System.out.println("fetch: SUCCESS");
        return true;
    }

    private boolean testGetAncestors() throws IOException {
        System.out.println("Testing getAncestors...");

        JsonObject args = new JsonObject();
        args.addProperty("ontologyId", "duo");
        args.addProperty("classIri", "http://purl.obolibrary.org/obo/DUO_0000001");
        args.addProperty("pageSize", 10);

        JsonElement result = callTool("getAncestors", args);
        write(outDir + "/mcp/getAncestors.json", result);

        if (result == null) {
            System.out.println("getAncestors returned null");
            return false;
        }

        System.out.println("getAncestors: SUCCESS");
        return true;
    }

    private boolean testGetDescendants() throws IOException {
        System.out.println("Testing getDescendants...");

        JsonObject args = new JsonObject();
        args.addProperty("ontologyId", "duo");
        args.addProperty("classIri", "http://purl.obolibrary.org/obo/DUO_0000001");
        args.addProperty("pageSize", 10);

        JsonElement result = callTool("getDescendants", args);
        write(outDir + "/mcp/getDescendants.json", result);

        if (result == null) {
            System.out.println("getDescendants returned null");
            return false;
        }

        System.out.println("getDescendants: SUCCESS");
        return true;
    }

    private boolean testListEmbeddingModels() throws IOException {
        System.out.println("Testing listEmbeddingModels...");

        JsonObject args = new JsonObject();

        JsonElement result = callTool("listEmbeddingModels", args);
        write(outDir + "/mcp/listEmbeddingModels.json", result);

        if (result == null) {
            System.out.println("listEmbeddingModels returned null");
            return false;
        }

        System.out.println("listEmbeddingModels: SUCCESS");
        return true;
    }

    private boolean testSearchWithEmbeddingModel() throws IOException {
        System.out.println("Testing searchWithEmbeddingModel...");

        // First, get available models
        JsonObject listArgs = new JsonObject();
        JsonElement modelsResult = callTool("listEmbeddingModels", listArgs);

        String modelName = findEmbeddableModel(modelsResult);

        if (modelName == null) {
            System.out.println("No embedding model with can_embed=true found, skipping searchWithEmbeddingModel test");
            write(outDir + "/mcp/searchWithEmbeddingModel.json",
                    JsonParser.parseString("{\"skipped\": \"no embeddable model available\"}"));
            return true; // Not a failure, just skipped
        }

        JsonObject args = new JsonObject();
        args.addProperty("query", "genetic research");
        args.addProperty("model", modelName);
        args.addProperty("pageSize", 10);

        JsonElement result = callTool("searchWithEmbeddingModel", args);
        write(outDir + "/mcp/searchWithEmbeddingModel.json", result);

        if (result == null) {
            System.out.println("searchWithEmbeddingModel returned null");
            return false;
        }

        // Test with ontologyId filter
        JsonObject argsWithOntology = new JsonObject();
        argsWithOntology.addProperty("query", "data use");
        argsWithOntology.addProperty("model", modelName);
        argsWithOntology.addProperty("ontologyId", "duo");
        argsWithOntology.addProperty("pageSize", 10);

        JsonElement resultWithOntology = callTool("searchWithEmbeddingModel", argsWithOntology);
        write(outDir + "/mcp/searchWithEmbeddingModel_withOntology.json", resultWithOntology);

        System.out.println("searchWithEmbeddingModel: SUCCESS");
        return true;
    }

    private boolean testSearchClassesWithEmbeddingModel() throws IOException {
        System.out.println("Testing searchClassesWithEmbeddingModel...");

        // First, get available models
        JsonObject listArgs = new JsonObject();
        JsonElement modelsResult = callTool("listEmbeddingModels", listArgs);

        String modelName = findEmbeddableModel(modelsResult);

        if (modelName == null) {
            System.out.println("No embedding model with can_embed=true found, skipping searchClassesWithEmbeddingModel test");
            write(outDir + "/mcp/searchClassesWithEmbeddingModel.json",
                    JsonParser.parseString("{\"skipped\": \"no embeddable model available\"}"));
            return true; // Not a failure, just skipped
        }

        JsonObject args = new JsonObject();
        args.addProperty("query", "permission for research");
        args.addProperty("model", modelName);
        args.addProperty("pageSize", 10);

        JsonElement result = callTool("searchClassesWithEmbeddingModel", args);
        write(outDir + "/mcp/searchClassesWithEmbeddingModel.json", result);

        if (result == null) {
            System.out.println("searchClassesWithEmbeddingModel returned null");
            return false;
        }

        System.out.println("searchClassesWithEmbeddingModel: SUCCESS");
        return true;
    }

    private boolean testGetSimilarClasses() throws IOException {
        System.out.println("Testing getSimilarClasses...");

        // First, get available models
        JsonObject listArgs = new JsonObject();
        JsonElement modelsResult = callTool("listEmbeddingModels", listArgs);

        String modelName = findAnyModel(modelsResult);

        if (modelName == null) {
            System.out.println("No embedding model found, skipping getSimilarClasses test");
            write(outDir + "/mcp/getSimilarClasses.json",
                    JsonParser.parseString("{\"skipped\": \"no embedding model available\"}"));
            return true; // Not a failure, just skipped
        }

        JsonObject args = new JsonObject();
        args.addProperty("classIri", "http://purl.obolibrary.org/obo/DUO_0000001");
        args.addProperty("model", modelName);
        args.addProperty("pageSize", 10);

        JsonElement result = callTool("getSimilarClasses", args);
        write(outDir + "/mcp/getSimilarClasses.json", result);

        if (result == null) {
            System.out.println("getSimilarClasses returned null");
            return false;
        }

        System.out.println("getSimilarClasses: SUCCESS");
        return true;
    }

    private boolean testGetClassSimilarity() throws IOException {
        System.out.println("Testing getClassSimilarity...");

        // First, get available models
        JsonObject listArgs = new JsonObject();
        JsonElement modelsResult = callTool("listEmbeddingModels", listArgs);

        String modelName = findAnyModel(modelsResult);

        if (modelName == null) {
            System.out.println("No embedding model found, skipping getClassSimilarity test");
            write(outDir + "/mcp/getClassSimilarity.json",
                    JsonParser.parseString("{\"skipped\": \"no embedding model available\"}"));
            return true; // Not a failure, just skipped
        }

        JsonObject args = new JsonObject();
        args.addProperty("classIri1", "http://purl.obolibrary.org/obo/DUO_0000001");
        args.addProperty("classIri2", "http://purl.obolibrary.org/obo/DUO_0000004");
        args.addProperty("model", modelName);

        JsonElement result = callTool("getClassSimilarity", args);
        write(outDir + "/mcp/getClassSimilarity.json", result);

        if (result == null) {
            System.out.println("getClassSimilarity returned null");
            return false;
        }

        System.out.println("getClassSimilarity: SUCCESS");
        return true;
    }

    private String findEmbeddableModel(JsonElement modelsResult) {
        if (modelsResult == null || !modelsResult.isJsonObject()) {
            return null;
        }

        JsonObject resultObj = modelsResult.getAsJsonObject();
        if (!resultObj.has("result")) {
            return null;
        }

        JsonObject result = resultObj.get("result").getAsJsonObject();
        if (!result.has("content") || !result.get("content").isJsonArray()) {
            return null;
        }

        JsonArray content = result.get("content").getAsJsonArray();
        for (JsonElement contentItem : content) {
            if (contentItem.isJsonObject()) {
                JsonObject item = contentItem.getAsJsonObject();
                if (item.has("text")) {
                    try {
                        JsonArray models = JsonParser.parseString(item.get("text").getAsString()).getAsJsonArray();
                        for (JsonElement modelElement : models) {
                            JsonObject model = modelElement.getAsJsonObject();
                            if (model.has("can_embed") && model.get("can_embed").getAsBoolean()) {
                                return model.get("model").getAsString();
                            }
                        }
                    } catch (Exception e) {
                        // Continue if parsing fails
                    }
                }
            }
        }

        // Try looking for mock model as fallback for testing
        return "mock";
    }

    private String findAnyModel(JsonElement modelsResult) {
        if (modelsResult == null || !modelsResult.isJsonObject()) {
            return "mock"; // Fallback
        }

        JsonObject resultObj = modelsResult.getAsJsonObject();
        if (!resultObj.has("result")) {
            return "mock";
        }

        JsonObject result = resultObj.get("result").getAsJsonObject();
        if (!result.has("content") || !result.get("content").isJsonArray()) {
            return "mock";
        }

        JsonArray content = result.get("content").getAsJsonArray();
        for (JsonElement contentItem : content) {
            if (contentItem.isJsonObject()) {
                JsonObject item = contentItem.getAsJsonObject();
                if (item.has("text")) {
                    try {
                        JsonArray models = JsonParser.parseString(item.get("text").getAsString()).getAsJsonArray();
                        if (models.size() > 0) {
                            // Return the first available model
                            return models.get(0).getAsJsonObject().get("model").getAsString();
                        }
                    } catch (Exception e) {
                        // Continue if parsing fails
                    }
                }
            }
        }

        return "mock"; // Fallback to mock for testing
    }

    private JsonElement callTool(String toolName, JsonObject arguments) throws IOException {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", requestId++);
        request.addProperty("method", "tools/call");

        JsonObject params = new JsonObject();
        params.addProperty("name", toolName);
        params.add("arguments", arguments);
        request.add("params", params);

        return sendMcpRequest(request);
    }

    private JsonElement sendMcpRequest(JsonObject request) throws IOException {
        String mcpEndpoint = baseUrl + "/api/mcp";

        System.out.println("POST " + mcpEndpoint + " - " + request.get("method"));

        HttpURLConnection conn = (HttpURLConnection) new URL(mcpEndpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json, text/event-stream");
        conn.setDoOutput(true);

        // Add session ID if we have one
        if (sessionId != null) {
            conn.setRequestProperty("Mcp-Session-Id", sessionId);
        }

        // Write request body
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = gson.toJson(request).getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        // Get response
        int responseCode = conn.getResponseCode();

        // Extract session ID from response headers
        String newSessionId = conn.getHeaderField("Mcp-Session-Id");
        if (newSessionId != null) {
            this.sessionId = newSessionId;
        }

        InputStream is;
        if (responseCode >= 200 && responseCode < 300) {
            is = conn.getInputStream();
        } else {
            is = conn.getErrorStream();
            if (is == null) {
                System.out.println("Error: HTTP " + responseCode);
                return null;
            }
        }

        String contentType = conn.getContentType();

        // Handle SSE (Server-Sent Events) responses
        if (contentType != null && contentType.contains("text/event-stream")) {
            return parseSSEResponse(is);
        }

        // Handle regular JSON response
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    private JsonElement parseSSEResponse(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder eventData = new StringBuilder();
        String line;
        JsonElement lastResult = null;

        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data: ")) {
                String data = line.substring(6);
                try {
                    JsonElement parsed = JsonParser.parseString(data);
                    if (parsed.isJsonObject()) {
                        JsonObject obj = parsed.getAsJsonObject();
                        if (obj.has("result") || obj.has("error")) {
                            lastResult = parsed;
                        }
                    }
                } catch (JsonSyntaxException e) {
                    // Continue reading
                }
            } else if (line.isEmpty() && eventData.length() > 0) {
                // End of event
                eventData.setLength(0);
            }
        }

        return lastResult;
    }

    private void write(String path, JsonElement element) throws IOException {
        if (element == null) {
            element = JsonNull.INSTANCE;
        }

        Files.createDirectories(Paths.get(path).toAbsolutePath().getParent());

        // Apply normalization (similar to Ols4ApiTester)
        JsonElement normalized = deepSort(removeVolatileFields(element));

        try (FileOutputStream os = new FileOutputStream(path)) {
            os.write(gson.toJson(normalized).getBytes(StandardCharsets.UTF_8));
        }
    }

    private JsonElement removeVolatileFields(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return JsonNull.INSTANCE;
        }

        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            JsonArray res = new JsonArray();
            for (int i = 0; i < arr.size(); i++) {
                res.add(removeVolatileFields(arr.get(i)));
            }
            return res;
        } else if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            JsonObject res = new JsonObject();

            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String key = entry.getKey();

                // Skip volatile fields
                if (key.equals("loaded") || key.equals("updated") ||
                    key.equals("sourceFileTimestamp") || key.equals("score")) {
                    res.add(key, new JsonPrimitive("<" + key + ">"));
                    continue;
                }

                res.add(key, removeVolatileFields(entry.getValue()));
            }
            return res;
        }

        return element.deepCopy();
    }

    private JsonElement deepSort(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return JsonNull.INSTANCE;
        }

        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            JsonElement[] elems = new JsonElement[arr.size()];

            for (int i = 0; i < arr.size(); i++) {
                elems[i] = deepSort(arr.get(i));
            }

            Arrays.sort(elems, Comparator.comparing(elem -> gson.toJson(elem)));

            JsonArray res = new JsonArray();
            for (JsonElement elem : elems) {
                res.add(elem);
            }
            return res;
        } else if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            TreeSet<String> sortedKeys = new TreeSet<>(obj.keySet());
            JsonObject res = new JsonObject();

            for (String key : sortedKeys) {
                res.add(key, deepSort(obj.get(key)));
            }
            return res;
        }

        return element.deepCopy();
    }
}
