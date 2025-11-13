package uk.ac.ebi.spot.ols.repository.solr;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;


@Component
public class OlsSolrClient {


    @NotNull
    @org.springframework.beans.factory.annotation.Value("${ols.solr.host:http://localhost:8983}")
    public String host = "http://localhost:8983";


    private Gson gson = new Gson();

    private static final Logger logger = LoggerFactory.getLogger(OlsSolrClient.class);

    @Value("${ols.solr.max-rows:1000}")
    private int maxRows;

    public Map<String,Object> getCoreStatus() throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(host + "/solr/admin/cores?wt=json");
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                HttpEntity entity = response.getEntity();
                if(entity == null) {
                    return null;
                }
                Map<String,Object> obj = gson.fromJson(EntityUtils.toString(entity), Map.class);
                Map<String,Object> status = (Map<String,Object>) obj.get("status");
                Map<String,Object> coreStatus = (Map<String,Object>) status.get("ols4_entities");
                response.close();
                httpClient.close();
                return coreStatus;
            }
        }
    }
    
    /**
     * Get list of embedding model names that have fields in Solr.
     * Queries the Solr schema API to find all fields starting with "embeddings_".
     * @return List of model names (without the "embeddings_" prefix)
     */
    public java.util.List<String> getEmbeddingModelsInSolr() throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(host + "/solr/ols4_entities/schema/fields?wt=json");
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                HttpEntity entity = response.getEntity();
                if(entity == null) {
                    return java.util.List.of();
                }
                
                String jsonStr = EntityUtils.toString(entity);
                com.google.gson.JsonObject jsonObj = gson.fromJson(jsonStr, com.google.gson.JsonObject.class);
                
                if (!jsonObj.has("fields")) {
                    return java.util.List.of();
                }
                
                com.google.gson.JsonArray fields = jsonObj.getAsJsonArray("fields");
                java.util.List<String> models = new java.util.ArrayList<>();
                
                for (com.google.gson.JsonElement field : fields) {
                    if (field.isJsonObject()) {
                        com.google.gson.JsonObject fieldObj = field.getAsJsonObject();
                        if (fieldObj.has("name")) {
                            String fieldName = fieldObj.get("name").getAsString();
                            if (fieldName.startsWith("embeddings_")) {
                                // Extract model name by removing "embeddings_" prefix
                                String modelName = fieldName.substring("embeddings_".length());
                                models.add(modelName);
                            }
                        }
                    }
                }
                
                response.close();
                httpClient.close();
                return models;
            }
        } catch (Exception e) {
            logger.error("Failed to get embedding models from Solr schema", e);
            return java.util.List.of();
        }
    }

    public OlsFacetedResultsPage<JsonElement> searchSolrPaginated(OlsSolrQuery query, Pageable pageable) {

        QueryResponse qr = runSolrQuery(query, pageable);

        Map<String, Map<String, Long>> facetFieldToCounts = new LinkedHashMap<>();

        if(qr.getFacetFields() != null) {
            for(FacetField facetField : qr.getFacetFields()) {

                Map<String, Long> valueToCount = new LinkedHashMap<>();

                for(FacetField.Count count : facetField.getValues()) {
                    valueToCount.put(count.getName(), count.getCount());
                }

                facetFieldToCounts.put(facetField.getName(), valueToCount);
            }
        }

       return new OlsFacetedResultsPage<>(
                qr.getResults()
                        .stream()
                        .map(res -> getOlsEntityFromSolrResult(res))
                        .collect(Collectors.toList()),
                facetFieldToCounts,
                pageable,
                qr.getResults().getNumFound());
    }

    public JsonElement getFirst(OlsSolrQuery query) {

        QueryResponse qr = runSolrQuery(query, null);

        if(qr.getResults().getNumFound() < 1) {
            logger.debug("Expected at least 1 result for solr getFirst for solr query = {}", query.constructQuery().jsonStr());
            return null;
        }

        return getOlsEntityFromSolrResult(qr.getResults().get(0));
    }

    private JsonElement getOlsEntityFromSolrResult(SolrDocument doc) {
        JsonElement json = JsonParser.parseString((String) doc.get("_json"));
        
        // Add score if available (for vector search)
        if (doc.get("score") != null && json.isJsonObject()) {
            json.getAsJsonObject().addProperty("_score", ((Number) doc.get("score")).floatValue());
        }
        
        return json;
    }

    public QueryResponse runSolrQuery(OlsSolrQuery query, Pageable pageable) {
	    return runSolrQuery(query.constructQuery(), pageable);
    }

    public QueryResponse runSolrQuery(SolrQuery query, Pageable pageable) {

        if(pageable != null) {
            query.setStart((int)pageable.getOffset());
            query.setRows(pageable.getPageSize() > maxRows ? maxRows : pageable.getPageSize());
        }

        logger.debug("solr rows: {} ", query.getRows());
        logger.debug("solr query: {} ", query.toQueryString());
        logger.debug("solr query urldecoded: {}",URLDecoder.decode(query.toQueryString()));
        logger.debug("solr host: {}", host);

        org.apache.solr.client.solrj.SolrClient mySolrClient = new HttpSolrClient.Builder(host + "/solr/ols4_entities").build();

        QueryResponse qr = null;
        try {
            qr = mySolrClient.query(query);
            logger.debug("solr query had {} result(s).", qr.getResults().getNumFound());
        } catch (SolrServerException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                mySolrClient.close();
            } catch (IOException ioe){
                logger.error("Failed to close Solr client with exception \"{}\"", ioe.getMessage());
            }
        }
        return qr;
    }

    public QueryResponse dispatchSearch(SolrQuery query, String core) throws IOException, SolrServerException {
        org.apache.solr.client.solrj.SolrClient mySolrClient = new HttpSolrClient.Builder(host + "/solr/" + core).build();
        final int rows = query.getRows().intValue() > maxRows ? maxRows : query.getRows().intValue();
        query.setRows(rows);
        QueryResponse qr = mySolrClient.query(query);
        mySolrClient.close();
        return qr;
    }
    
    /**
     * Perform a vector similarity search using Solr's dense vector search.
     * @param modelName The embedding model name (e.g., "text-embedding-3-small")
     * @param vector The query vector
     * @param topK Number of results to return
     * @param pageable Pagination info
     * @return Faceted results page with similarity scores
     */
    public OlsFacetedResultsPage<JsonElement> searchByVector(String modelName, float[] vector, int topK, Pageable pageable) {
        SolrQuery query = new SolrQuery();
        
        // Build vector string for KNN query
        StringBuilder vectorStr = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) vectorStr.append(",");
            vectorStr.append(vector[i]);
        }
        vectorStr.append("]");
        
        // Use Solr's KNN query parser
        // {!knn f=<field> topK=<k>}<vector>
        String embeddingField = "embeddings_" + modelName;
        query.setQuery("{!knn f=" + embeddingField + " topK=" + topK + "}" + vectorStr.toString());
        
        // Request all standard fields
        query.setFields("_json", "score");
        
        // Apply pagination
        if (pageable != null) {
            query.setStart((int) pageable.getOffset());
            query.setRows(pageable.getPageSize() > maxRows ? maxRows : pageable.getPageSize());
        } else {
            query.setStart(0);
            query.setRows(topK > maxRows ? maxRows : topK);
        }
        
        logger.debug("Vector search query (length: {}): {}", query.toQueryString().length(), 
            query.toQueryString().length() > 200 ? query.toQueryString().substring(0, 200) + "..." : query.toQueryString());
        
        QueryResponse qr = null;
        org.apache.solr.client.solrj.SolrClient mySolrClient = new HttpSolrClient.Builder(host + "/solr/ols4_entities").build();
        
        try {
            // Use POST method via QueryRequest to avoid URI too long errors with large vectors
            org.apache.solr.client.solrj.request.QueryRequest req = new org.apache.solr.client.solrj.request.QueryRequest(query);
            req.setMethod(org.apache.solr.client.solrj.SolrRequest.METHOD.POST);
            qr = req.process(mySolrClient);
            logger.debug("Vector search found {} result(s)", qr.getResults().getNumFound());
        } catch (SolrServerException | IOException e) {
            throw new RuntimeException("Vector search failed", e);
        } finally {
            try {
                mySolrClient.close();
            } catch (IOException ioe) {
                logger.error("Failed to close Solr client with exception \"{}\"", ioe.getMessage());
            }
        }
        
        Map<String, Map<String, Long>> facetFieldToCounts = new LinkedHashMap<>();
        
        return new OlsFacetedResultsPage<>(
            qr.getResults()
                .stream()
                .map(res -> getOlsEntityFromSolrResult(res))
                .collect(Collectors.toList()),
            facetFieldToCounts,
            pageable != null ? pageable : org.springframework.data.domain.PageRequest.of(0, topK),
            qr.getResults().getNumFound()
        );
    }
}
