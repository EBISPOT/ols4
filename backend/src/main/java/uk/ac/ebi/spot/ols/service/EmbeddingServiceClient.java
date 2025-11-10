package uk.ac.ebi.spot.ols.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * Client for the OLS embedding service.
 */
@Service
public class EmbeddingServiceClient {
    
    @Value("${ols.embedding.service.url}")
    private String embeddingServiceUrl;
    
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    
    /**
     * Get list of available models from the embedding service.
     * Queries the /models endpoint to get the current list.
     */
    public List<String> getAvailableModels() {

        if(embeddingServiceUrl == null || embeddingServiceUrl.isEmpty()) {
            return List.of();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(embeddingServiceUrl + "/models"))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                if (json.has("models") && json.get("models").isJsonArray()) {
                    List<String> models = new java.util.ArrayList<>();
                    json.getAsJsonArray("models").forEach(element -> {
                        if (element.isJsonPrimitive()) {
                            models.add(element.getAsString());
                        }
                    });
                    return models;
                }
            }
            // Fallback to empty list if service is unavailable
            return List.of();
        } catch (Exception e) {
            // Service unavailable, return empty list
            return List.of();
        }
    }
    
    /**
     * Embed a single text using the new embedding service.
     * @param model The model name to use for embedding
     * @param text The text to embed
     * @return The embedding vector as a float array
     */
    public float[] embedText(String model, String text) throws IOException {
        return embedTexts(model, List.of(text))[0];
    }
    
    /**
     * Embed multiple texts using the new embedding service.
     * The service returns binary blob of float32 arrays.
     * @param model The model name to use for embedding  
     * @param texts List of texts to embed
     * @return Array of embedding vectors
     */
    public float[][] embedTexts(String model, List<String> texts) throws IOException {

        if(embeddingServiceUrl == null || embeddingServiceUrl.isEmpty()) {
            throw new IOException("Embedding service URL is not configured");
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.add("text", gson.toJsonTree(texts));
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(embeddingServiceUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
            .build();
        
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            
            if (response.statusCode() == 200) {
                // Get vector dimension from header
                String dimHeader = response.headers().firstValue("x-embedding-dim").orElse(null);
                if (dimHeader == null) {
                    throw new IOException("Missing x-embedding-dim header in response");
                }
                int dimension = Integer.parseInt(dimHeader);
                
                // Parse binary blob as float32 array
                byte[] binaryData = response.body();
                int expectedBytes = texts.size() * dimension * 4; // 4 bytes per float
                
                if (binaryData.length != expectedBytes) {
                    throw new IOException("Unexpected response size: got " + binaryData.length + 
                        " bytes, expected " + expectedBytes + " bytes for " + texts.size() + 
                        " texts with dimension " + dimension);
                }
                
                float[][] embeddings = new float[texts.size()][dimension];
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(binaryData).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                
                for (int i = 0; i < texts.size(); i++) {
                    for (int j = 0; j < dimension; j++) {
                        embeddings[i][j] = buffer.getFloat();
                    }
                }
                
                return embeddings;
            } else {
                throw new IOException("Embedding service returned HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid dimension in x-embedding-dim header", e);
        }
    }
}
