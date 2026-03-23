package uk.ac.ebi.spot.ols.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client for the OLS embedding service.
 * 
 * Handles PCA transformations locally: when a PCA model name is requested
 * (e.g. "model_pca512"), the client calls the embedding service with the
 * base model name ("model") and applies the PCA transform using a JSON
 * file loaded from the configured PCA models directory.
 */
@Service
public class EmbeddingServiceClient {

    @Value("${ols.embedding.service.url:#{null}}")
    private String embeddingServiceUrl;

    @Value("${ols.embedding.pca.models.dir:#{null}}")
    private String pcaModelsDir;
    
    private final HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    private final Gson gson = new Gson();

    // PCA model name (e.g. "model_pca512") -> PcaModel
    private final Map<String, PcaModel> pcaModels = new ConcurrentHashMap<>();

    private static final Pattern PCA_PATTERN = Pattern.compile("^(.+)_pca(\\d+)$");

    private static class PcaModel {
        final String baseModelName;
        final int nComponents;
        final double[] mean;        // length = n_features
        final double[][] components; // shape = (n_features, n_components)

        PcaModel(String baseModelName, int nComponents, double[] mean, double[][] components) {
            this.baseModelName = baseModelName;
            this.nComponents = nComponents;
            this.mean = mean;
            this.components = components;
        }
    }

    @PostConstruct
    public void init() {
        loadPcaModels();
    }

    private void loadPcaModels() {
        if (pcaModelsDir == null || pcaModelsDir.isEmpty()) {
            return;
        }
        Path dir = Paths.get(pcaModelsDir);
        if (!Files.isDirectory(dir)) {
            System.err.println("PCA models directory does not exist: " + pcaModelsDir);
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*_pca*.json")) {
            for (Path file : stream) {
                String filename = file.getFileName().toString();
                // Expected format: {base_model}_pca{n}.json
                String stem = filename.replaceFirst("\\.json$", "");
                Matcher m = PCA_PATTERN.matcher(stem);
                if (!m.matches()) continue;

                String baseModelName = m.group(1);
                int nComponents = Integer.parseInt(m.group(2));
                String pcaModelName = stem;

                System.err.println("Loading PCA model: " + pcaModelName + " from " + file);

                try (Reader reader = Files.newBufferedReader(file)) {
                    JsonObject json = gson.fromJson(reader, JsonObject.class);

                    double[] mean = toDoubleArray(json.getAsJsonArray("mean"));
                    double[][] components = toDoubleArray2D(json.getAsJsonArray("components"));

                    pcaModels.put(pcaModelName, new PcaModel(baseModelName, nComponents, mean, components));
                    System.err.println("Loaded PCA model: " + pcaModelName +
                            " (base=" + baseModelName + ", components=" + nComponents +
                            ", features=" + mean.length + ")");
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading PCA models from " + pcaModelsDir + ": " + e.getMessage());
        }
    }

    private static double[] toDoubleArray(JsonArray arr) {
        double[] result = new double[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            result[i] = arr.get(i).getAsDouble();
        }
        return result;
    }

    private static double[][] toDoubleArray2D(JsonArray arr) {
        double[][] result = new double[arr.size()][];
        for (int i = 0; i < arr.size(); i++) {
            result[i] = toDoubleArray(arr.get(i).getAsJsonArray());
        }
        return result;
    }

    /**
     * Apply PCA transform: (x - mean) @ components
     */
    private float[] applyPca(float[] embedding, PcaModel pca) {
        int nFeatures = pca.mean.length;
        int nComponents = pca.nComponents;
        float[] result = new float[nComponents];

        for (int j = 0; j < nComponents; j++) {
            double sum = 0.0;
            for (int i = 0; i < nFeatures; i++) {
                sum += ((double) embedding[i] - pca.mean[i]) * pca.components[i][j];
            }
            result[j] = (float) sum;
        }
        return result;
    }
    
    /**
     * Get list of available models from the embedding service.
     * Includes PCA model variants loaded from JSON files.
     */
    public List<String> getAvailableModels() {

        if(embeddingServiceUrl == null || embeddingServiceUrl.isEmpty()) {
            return List.of();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(embeddingServiceUrl + "/models"))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            Set<String> serviceModels = new java.util.HashSet<String>();
            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                if (json.has("models") && json.get("models").isJsonArray()) {
                    json.getAsJsonArray("models").forEach(element -> {
                        if (element.isJsonPrimitive()) {
                            serviceModels.add(element.getAsString());
                        }
                    });
                }
            }

            List<String> models = new java.util.ArrayList<>(serviceModels);

            // Only include PCA models whose base model is available in the service
            for (var entry : pcaModels.entrySet()) {
                if (serviceModels.contains(entry.getValue().baseModelName) && !entry.getKey().contains("pca16")) {
                    models.add(entry.getKey());
                }
            }

            // Filter out any models with pca16 in the name
            models.removeIf(m -> m.contains("pca16"));

            return models;
        } catch (Exception e) {
            return List.of();
        }
    }
    
    /**
     * Embed a single text. If the model name is a PCA model (e.g. "model_pca512"),
     * embeds with the base model and applies the PCA transform locally.
     */
    public float[] embedText(String model, String text) throws IOException {
        return embedTexts(model, List.of(text))[0];
    }
    
    /**
     * Embed multiple texts. If the model name is a PCA model, embeds with the
     * base model and applies the PCA transform locally.
     */
    public float[][] embedTexts(String model, List<String> texts) throws IOException {

        PcaModel pca = pcaModels.get(model);
        String serviceModel = (pca != null) ? pca.baseModelName : model;

        float[][] embeddings = embedTextsFromService(serviceModel, texts);

        if (pca != null) {
            for (int i = 0; i < embeddings.length; i++) {
                embeddings[i] = applyPca(embeddings[i], pca);
            }
        }

        return embeddings;
    }

    private float[][] embedTextsFromService(String model, List<String> texts) throws IOException {

        if(embeddingServiceUrl == null || embeddingServiceUrl.isEmpty()) {
            throw new IOException("Embedding service URL is not configured");
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.add("text", gson.toJsonTree(texts));
        
        String requestBodyJson = gson.toJson(requestBody);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(embeddingServiceUrl))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
            .build();
        
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            
            if (response.statusCode() == 200) {
                String dimHeader = response.headers().firstValue("x-embedding-dim").orElse(null);
                if (dimHeader == null) {
                    throw new IOException("Missing x-embedding-dim header in response");
                }
                int dimension = Integer.parseInt(dimHeader);
                
                byte[] binaryData = response.body();
                int expectedBytes = texts.size() * dimension * 4;
                
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
                String responseBody = response.body() != null ? new String(response.body()) : "(empty)";
                throw new IOException("Embedding service returned HTTP " + response.statusCode() + 
                    " for URL: " + embeddingServiceUrl + 
                    ". Response body: " + responseBody);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid dimension in x-embedding-dim header", e);
        }
    }
}
