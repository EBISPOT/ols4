package uk.ac.ebi.ols.apitester;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import com.google.gson.*;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Tests MCP (Model Context Protocol) functionality using the official MCP Java SDK
 * client with Streamable HTTP transport.
 */
public class McpTester {

    private final String baseUrl;
    private final String outDir;
    private final Gson gson;

    public McpTester(String baseUrl, String outDir) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.outDir = outDir;
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    public boolean test() throws IOException {
        System.out.println("Starting MCP tests (SDK client)...");

        McpClientTransport transport = HttpClientStreamableHttpTransport.builder(baseUrl)
                .endpoint("/api/mcp")
                .build();

        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("ols4-apitester", "1.0.0"))
                .build();

        boolean success = true;

        try {
            // Initialize MCP session
            System.out.println("Initializing MCP session...");
            McpSchema.InitializeResult initResult = client.initialize();
            System.out.println("MCP session initialized: server=" + initResult.serverInfo().name()
                    + " version=" + initResult.serverInfo().version());

            // List tools
            System.out.println("Listing MCP tools...");
            McpSchema.ListToolsResult toolsResult = client.listTools();
            JsonElement toolsJson = toToolsJson(toolsResult);
            write(outDir + "/mcp/tools.json", toolsJson);

            if (toolsResult.tools() == null || toolsResult.tools().isEmpty()) {
                System.out.println("No tools found!");
                return false;
            }
            System.out.println("Found " + toolsResult.tools().size() + " tools");

            // Test each MCP tool
            if (!testListOntologies(client)) success = false;
            if (!testSearch(client)) success = false;
            if (!testSearchClasses(client)) success = false;
            if (!testFetch(client)) success = false;
            if (!testGetAncestors(client)) success = false;
            if (!testGetDescendants(client)) success = false;
            if (!testListEmbeddingModels(client)) success = false;
            if (!testSearchWithEmbeddingModel(client)) success = false;
            if (!testSearchClassesWithEmbeddingModel(client)) success = false;
            if (!testGetSimilarClasses(client)) success = false;
            if (!testGetClassSimilarity(client)) success = false;

        } catch (Exception e) {
            System.out.println("Error during MCP tests: " + e.getMessage());
            e.printStackTrace();
            success = false;
        } finally {
            try {
                client.close();
            } catch (Exception e) {
                // ignore
            }
        }

        return success;
    }

    // -----------------------------------------------------------------------
    // Tool test methods
    // -----------------------------------------------------------------------

    private boolean testListOntologies(McpSyncClient client) throws IOException {
        System.out.println("Testing listOntologies...");
        JsonElement result = callToolAsJson(client, "listOntologies", Map.of());
        write(outDir + "/mcp/listOntologies.json", result);
        if (result == null) { System.out.println("listOntologies returned null"); return false; }
        System.out.println("listOntologies: SUCCESS");
        return true;
    }

    private boolean testSearch(McpSyncClient client) throws IOException {
        System.out.println("Testing search...");
        JsonElement result = callToolAsJson(client, "search", Map.of("query", "cell"));
        write(outDir + "/mcp/search.json", result);
        if (result == null) { System.out.println("search returned null"); return false; }
        System.out.println("search: SUCCESS");
        return true;
    }

    private boolean testSearchClasses(McpSyncClient client) throws IOException {
        System.out.println("Testing searchClasses...");
        JsonElement result = callToolAsJson(client, "searchClasses",
                Map.of("query", "cell", "pageSize", 10));
        write(outDir + "/mcp/searchClasses.json", result);
        if (result == null) { System.out.println("searchClasses returned null"); return false; }

        // Also test with ontologyId filter
        JsonElement resultWithOntology = callToolAsJson(client, "searchClasses",
                Map.of("query", "research", "ontologyId", "duo", "pageSize", 10));
        write(outDir + "/mcp/searchClasses_withOntology.json", resultWithOntology);

        System.out.println("searchClasses: SUCCESS");
        return true;
    }

    private boolean testFetch(McpSyncClient client) throws IOException {
        System.out.println("Testing fetch...");

        // Search first to find a valid entity ID
        JsonElement searchResult = callToolAsJson(client, "search",
                Map.of("query", "data use permission"));

        String entityId = extractEntityIdFromResult(searchResult);
        if (entityId == null) {
            entityId = "duo+http://purl.obolibrary.org/obo/DUO_0000001";
            System.out.println("Using fallback entity ID: " + entityId);
        } else {
            System.out.println("Using entity ID from search: " + entityId);
        }

        JsonElement result = callToolAsJson(client, "fetch", Map.of("id", entityId));
        write(outDir + "/mcp/fetch.json", result);
        if (result == null) { System.out.println("fetch returned null"); return false; }
        System.out.println("fetch: SUCCESS");
        return true;
    }

    private boolean testGetAncestors(McpSyncClient client) throws IOException {
        System.out.println("Testing getAncestors...");
        JsonElement result = callToolAsJson(client, "getAncestors",
                Map.of("ontologyId", "duo",
                       "classIri", "http://purl.obolibrary.org/obo/DUO_0000001",
                       "pageSize", 10));
        write(outDir + "/mcp/getAncestors.json", result);
        if (result == null) { System.out.println("getAncestors returned null"); return false; }
        System.out.println("getAncestors: SUCCESS");
        return true;
    }

    private boolean testGetDescendants(McpSyncClient client) throws IOException {
        System.out.println("Testing getDescendants...");
        JsonElement result = callToolAsJson(client, "getDescendants",
                Map.of("ontologyId", "duo",
                       "classIri", "http://purl.obolibrary.org/obo/DUO_0000001",
                       "pageSize", 10));
        write(outDir + "/mcp/getDescendants.json", result);
        if (result == null) { System.out.println("getDescendants returned null"); return false; }
        System.out.println("getDescendants: SUCCESS");
        return true;
    }

    private boolean testListEmbeddingModels(McpSyncClient client) throws IOException {
        System.out.println("Testing listEmbeddingModels...");
        JsonElement result = callToolAsJson(client, "listEmbeddingModels", Map.of());
        write(outDir + "/mcp/listEmbeddingModels.json", result);
        if (result == null) { System.out.println("listEmbeddingModels returned null"); return false; }
        System.out.println("listEmbeddingModels: SUCCESS");
        return true;
    }

    private boolean testSearchWithEmbeddingModel(McpSyncClient client) throws IOException {
        System.out.println("Testing searchWithEmbeddingModel...");

        JsonElement modelsResult = callToolAsJson(client, "listEmbeddingModels", Map.of());
        String modelName = findEmbeddableModel(modelsResult);
        if (modelName == null) {
            System.out.println("No embedding model with can_embed=true found, skipping");
            write(outDir + "/mcp/searchWithEmbeddingModel.json",
                    JsonParser.parseString("{\"skipped\": \"no embeddable model available\"}"));
            return true;
        }

        JsonElement result = callToolAsJson(client, "searchWithEmbeddingModel",
                Map.of("query", "genetic research", "model", modelName, "pageSize", 10));
        write(outDir + "/mcp/searchWithEmbeddingModel.json", result);
        if (result == null) { System.out.println("searchWithEmbeddingModel returned null"); return false; }

        // Test with ontologyId filter
        JsonElement resultWithOntology = callToolAsJson(client, "searchWithEmbeddingModel",
                Map.of("query", "data use", "model", modelName, "ontologyId", "duo", "pageSize", 10));
        write(outDir + "/mcp/searchWithEmbeddingModel_withOntology.json", resultWithOntology);

        System.out.println("searchWithEmbeddingModel: SUCCESS");
        return true;
    }

    private boolean testSearchClassesWithEmbeddingModel(McpSyncClient client) throws IOException {
        System.out.println("Testing searchClassesWithEmbeddingModel...");

        JsonElement modelsResult = callToolAsJson(client, "listEmbeddingModels", Map.of());
        String modelName = findEmbeddableModel(modelsResult);
        if (modelName == null) {
            System.out.println("No embedding model with can_embed=true found, skipping");
            write(outDir + "/mcp/searchClassesWithEmbeddingModel.json",
                    JsonParser.parseString("{\"skipped\": \"no embeddable model available\"}"));
            return true;
        }

        JsonElement result = callToolAsJson(client, "searchClassesWithEmbeddingModel",
                Map.of("query", "permission for research", "model", modelName, "pageSize", 10));
        write(outDir + "/mcp/searchClassesWithEmbeddingModel.json", result);
        if (result == null) { System.out.println("searchClassesWithEmbeddingModel returned null"); return false; }
        System.out.println("searchClassesWithEmbeddingModel: SUCCESS");
        return true;
    }

    private boolean testGetSimilarClasses(McpSyncClient client) throws IOException {
        System.out.println("Testing getSimilarClasses...");

        JsonElement modelsResult = callToolAsJson(client, "listEmbeddingModels", Map.of());
        String modelName = findAnyModel(modelsResult);
        if (modelName == null) {
            System.out.println("No embedding model found, skipping");
            write(outDir + "/mcp/getSimilarClasses.json",
                    JsonParser.parseString("{\"skipped\": \"no embedding model available\"}"));
            return true;
        }

        JsonElement result = callToolAsJson(client, "getSimilarClasses",
                Map.of("classIri", "http://purl.obolibrary.org/obo/DUO_0000001",
                       "model", modelName, "pageSize", 10));
        write(outDir + "/mcp/getSimilarClasses.json", result);
        if (result == null) { System.out.println("getSimilarClasses returned null"); return false; }
        System.out.println("getSimilarClasses: SUCCESS");
        return true;
    }

    private boolean testGetClassSimilarity(McpSyncClient client) throws IOException {
        System.out.println("Testing getClassSimilarity...");

        JsonElement modelsResult = callToolAsJson(client, "listEmbeddingModels", Map.of());
        String modelName = findAnyModel(modelsResult);
        if (modelName == null) {
            System.out.println("No embedding model found, skipping");
            write(outDir + "/mcp/getClassSimilarity.json",
                    JsonParser.parseString("{\"skipped\": \"no embedding model available\"}"));
            return true;
        }

        JsonElement result = callToolAsJson(client, "getClassSimilarity",
                Map.of("classIri1", "http://purl.obolibrary.org/obo/DUO_0000001",
                       "classIri2", "http://purl.obolibrary.org/obo/DUO_0000004",
                       "model", modelName));
        write(outDir + "/mcp/getClassSimilarity.json", result);
        if (result == null) { System.out.println("getClassSimilarity returned null"); return false; }
        System.out.println("getClassSimilarity: SUCCESS");
        return true;
    }

    // -----------------------------------------------------------------------
    // SDK helper: call a tool and convert the result to Gson JsonElement
    // -----------------------------------------------------------------------

    private JsonElement callToolAsJson(McpSyncClient client, String toolName, Map<String, Object> arguments) {
        try {
            System.out.println("  Calling tool: " + toolName);
            McpSchema.CallToolResult callResult = client.callTool(
                    new McpSchema.CallToolRequest(toolName, arguments));

            // Build a JSON structure matching the old format:
            // { "result": { "content": [ { "type": "text", "text": "..." }, ... ] } }
            JsonObject wrapper = new JsonObject();
            JsonObject result = new JsonObject();
            JsonArray contentArray = new JsonArray();

            if (callResult.content() != null) {
                for (McpSchema.Content content : callResult.content()) {
                    JsonObject contentObj = new JsonObject();
                    if (content instanceof McpSchema.TextContent tc) {
                        contentObj.addProperty("type", "text");
                        contentObj.addProperty("text", tc.text());
                    } else if (content instanceof McpSchema.ImageContent ic) {
                        contentObj.addProperty("type", "image");
                        contentObj.addProperty("data", ic.data());
                        contentObj.addProperty("mimeType", ic.mimeType());
                    } else {
                        contentObj.addProperty("type", content.type());
                    }
                    contentArray.add(contentObj);
                }
            }

            result.add("content", contentArray);
            if (callResult.isError() != null && callResult.isError()) {
                result.addProperty("isError", true);
            }
            wrapper.add("result", result);
            return wrapper;

        } catch (Exception e) {
            System.out.println("  Error calling tool " + toolName + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Convert ListToolsResult to Gson JSON for output
    // -----------------------------------------------------------------------

    private JsonElement toToolsJson(McpSchema.ListToolsResult toolsResult) {
        JsonObject wrapper = new JsonObject();
        JsonArray toolsArray = new JsonArray();

        if (toolsResult.tools() != null) {
            for (McpSchema.Tool tool : toolsResult.tools()) {
                JsonObject toolObj = new JsonObject();
                toolObj.addProperty("name", tool.name());
                toolObj.addProperty("description", tool.description());
                if (tool.inputSchema() != null) {
                    // Convert the input schema map to a JsonElement
                    String schemaJson = gson.toJson(tool.inputSchema());
                    toolObj.add("inputSchema", JsonParser.parseString(schemaJson));
                }
                toolsArray.add(toolObj);
            }
        }

        wrapper.add("tools", toolsArray);
        return wrapper;
    }

    // -----------------------------------------------------------------------
    // Helper: extract entity ID from a search result
    // -----------------------------------------------------------------------

    private String extractEntityIdFromResult(JsonElement searchResult) {
        if (searchResult == null || !searchResult.isJsonObject()) return null;
        try {
            JsonObject resultObj = searchResult.getAsJsonObject();
            JsonObject result = resultObj.getAsJsonObject("result");
            if (result == null) return null;
            JsonArray content = result.getAsJsonArray("content");
            if (content == null || content.size() == 0) return null;
            JsonObject firstContent = content.get(0).getAsJsonObject();
            String text = firstContent.get("text").getAsString();
            JsonArray items = JsonParser.parseString(text).getAsJsonArray();
            if (items.size() > 0) {
                JsonObject firstItem = items.get(0).getAsJsonObject();
                if (firstItem.has("id")) {
                    return firstItem.get("id").getAsString();
                }
            }
        } catch (Exception e) {
            // fall through
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Helpers to find embedding models from listEmbeddingModels result
    // -----------------------------------------------------------------------

    private String findEmbeddableModel(JsonElement modelsResult) {
        if (modelsResult == null || !modelsResult.isJsonObject()) return null;
        try {
            JsonArray content = modelsResult.getAsJsonObject()
                    .getAsJsonObject("result").getAsJsonArray("content");
            for (JsonElement contentItem : content) {
                JsonObject item = contentItem.getAsJsonObject();
                if (item.has("text")) {
                    JsonArray models = JsonParser.parseString(item.get("text").getAsString()).getAsJsonArray();
                    for (JsonElement modelElement : models) {
                        JsonObject model = modelElement.getAsJsonObject();
                        if (model.has("can_embed") && model.get("can_embed").getAsBoolean()) {
                            return model.get("model").getAsString();
                        }
                    }
                }
            }
        } catch (Exception e) { /* fall through */ }
        return "mock";
    }

    private String findAnyModel(JsonElement modelsResult) {
        if (modelsResult == null || !modelsResult.isJsonObject()) return "mock";
        try {
            JsonArray content = modelsResult.getAsJsonObject()
                    .getAsJsonObject("result").getAsJsonArray("content");
            for (JsonElement contentItem : content) {
                JsonObject item = contentItem.getAsJsonObject();
                if (item.has("text")) {
                    JsonArray models = JsonParser.parseString(item.get("text").getAsString()).getAsJsonArray();
                    if (models.size() > 0) {
                        return models.get(0).getAsJsonObject().get("model").getAsString();
                    }
                }
            }
        } catch (Exception e) { /* fall through */ }
        return "mock";
    }

    // -----------------------------------------------------------------------
    // Output helpers (deep sort, remove volatile fields)
    // -----------------------------------------------------------------------

    private void write(String path, JsonElement element) throws IOException {
        if (element == null) {
            element = JsonNull.INSTANCE;
        }
        Files.createDirectories(Paths.get(path).toAbsolutePath().getParent());
        JsonElement normalized = deepSort(removeVolatileFields(element));
        try (FileOutputStream os = new FileOutputStream(path)) {
            os.write(gson.toJson(normalized).getBytes(StandardCharsets.UTF_8));
        }
    }

    private JsonElement removeVolatileFields(JsonElement element) {
        if (element == null || element.isJsonNull()) return JsonNull.INSTANCE;
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            JsonArray res = new JsonArray();
            for (int i = 0; i < arr.size(); i++) res.add(removeVolatileFields(arr.get(i)));
            return res;
        } else if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            JsonObject res = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String key = entry.getKey();
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
        if (element == null || element.isJsonNull()) return JsonNull.INSTANCE;
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            JsonElement[] elems = new JsonElement[arr.size()];
            for (int i = 0; i < arr.size(); i++) elems[i] = deepSort(arr.get(i));
            Arrays.sort(elems, Comparator.comparing(elem -> gson.toJson(elem)));
            JsonArray res = new JsonArray();
            for (JsonElement elem : elems) res.add(elem);
            return res;
        } else if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            TreeSet<String> sortedKeys = new TreeSet<>(obj.keySet());
            JsonObject res = new JsonObject();
            for (String key : sortedKeys) res.add(key, deepSort(obj.get(key)));
            return res;
        }
        return element.deepCopy();
    }
}
