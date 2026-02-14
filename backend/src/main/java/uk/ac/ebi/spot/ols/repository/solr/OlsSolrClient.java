package uk.ac.ebi.spot.ols.repository.solr;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;


@Component
public class OlsSolrClient {


    @NotNull
    @Value("${ols.solr.host:http://localhost:8983}")
    private String host;

    @Value("${ols.solr.max-rows:1000}")
    private int maxRows;

    @Value("${ols.solr.max-connections:60}")
    private int maxConnections;

    @Value("${ols.solr.max-connections-per-route:30}")
    private int maxConnectionsPerRoute;

    @Value("${ols.solr.connection-timeout:5000}")
    private int connectionTimeout;

    @Value("${ols.solr.socket-timeout:30000}")
    private int socketTimeout;

    private final Gson gson = new Gson();
    private static final Logger logger = LoggerFactory.getLogger(OlsSolrClient.class);

    // Reusable SolrClient and HttpClient instances
    private HttpSolrClient entitiesSolrClient;
    private CloseableHttpClient httpClient;

    @PostConstruct
    public void init() {
        logger.info("Initializing OLS Solr client with connection pooling - maxConnections: {}, maxPerRoute: {}",
            maxConnections, maxConnectionsPerRoute);

        // Configure connection pooling for high-volume traffic
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(maxConnections);
        connManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);

        this.httpClient = HttpClients.custom()
            .setConnectionManager(connManager)
            .setConnectionManagerShared(true)
            .build();

        // Create reusable SolrClient with connection pooling
        this.entitiesSolrClient = new HttpSolrClient.Builder(host + "/solr/ols4_entities")
            .withHttpClient(httpClient)
            .withConnectionTimeout(connectionTimeout)
            .withSocketTimeout(socketTimeout)
            .build();

        logger.info("OLS Solr client initialized successfully");
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down OLS Solr client");
        try {
            if (entitiesSolrClient != null) {
                entitiesSolrClient.close();
            }
            if (httpClient != null) {
                httpClient.close();
            }
            logger.info("OLS Solr client shut down successfully");
        } catch (IOException e) {
            logger.error("Error closing Solr clients", e);
        }
    }

    public Map<String,Object> getCoreStatus() throws IOException {
        // Reuse the shared httpClient
        HttpGet request = new HttpGet(host + "/solr/admin/cores?wt=json");
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if(entity == null) {
                return null;
            }
            Map<String,Object> obj = gson.fromJson(EntityUtils.toString(entity), Map.class);
            Map<String,Object> status = (Map<String,Object>) obj.get("status");
            return (Map<String,Object>) status.get("ols4_entities");
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
        // Log memory before query
        Runtime runtime = Runtime.getRuntime();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();
        long maxMem = runtime.maxMemory();
        logger.debug("SOLR QUERY START - Memory before: {}MB / {}MB ({}% used)",
                memBefore / 1024 / 1024, maxMem / 1024 / 1024, (memBefore * 100) / maxMem);

        logger.debug("solr rows: {} ", query.getRows());
        logger.info("solr query: {} ", query.toQueryString());
        logger.debug("solr query urldecoded: {}",URLDecoder.decode(query.toQueryString()));
        logger.debug("solr host: {}", host);

        QueryResponse qr;
        long startTime = System.currentTimeMillis();
        try {
            // Reuse the singleton client instead of creating new one
            qr = entitiesSolrClient.query(query);
            long duration = System.currentTimeMillis() - startTime;

            // Calculate response size
            long responseSize = 0;
            for(SolrDocument doc : qr.getResults()) {
                String json = (String) doc.get("_json");
                if(json != null) {
                    responseSize += json.length();
                }
            }

            // Log memory after query
            long memAfter = runtime.totalMemory() - runtime.freeMemory();
            long memDelta = memAfter - memBefore;

            logger.debug("SOLR QUERY COMPLETE - Results: {}, ResponseSize: {}MB, Duration: {}ms, MemoryDelta: {}MB, MemoryAfter: {}MB / {}MB ({}% used)",
                    qr.getResults().getNumFound(),
                    responseSize / 1024 / 1024,
                    duration,
                    memDelta / 1024 / 1024,
                    memAfter / 1024 / 1024,
                    maxMem / 1024 / 1024,
                    (memAfter * 100) / maxMem);

            if(responseSize > 100 * 1024 * 1024) {
                logger.warn("LARGE SOLR RESPONSE - {}MB - Query: {}", responseSize / 1024 / 1024, query.toQueryString());
            }
            logger.debug("solr query had {} result(s).", qr.getResults().getNumFound());
            return qr;
        } catch (SolrServerException | IOException e) {
            throw new RuntimeException("Solr query failed", e);
        }
    }

    public QueryResponse dispatchSearch(SolrQuery query, String core) throws IOException, SolrServerException {
        // Log memory before query
        Runtime runtime = Runtime.getRuntime();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();
        long maxMem = runtime.maxMemory();

        logger.debug("SOLR DISPATCH START - Core: {}, Memory before: {}MB / {}MB ({}% used)",
                core, memBefore / 1024 / 1024, maxMem / 1024 / 1024, (memBefore * 100) / maxMem);

        final int rows = query.getRows().intValue() > maxRows ? maxRows : query.getRows().intValue();
        query.setRows(rows);

        logger.debug("solr dispatch - core: {}, rows: {}, query: {}", core, rows, query.toQueryString());

        try (HttpSolrClient client = new HttpSolrClient.Builder(host + "/solr/" + core)
                .withHttpClient(httpClient)
                .withConnectionTimeout(connectionTimeout)
                .withSocketTimeout(socketTimeout)
                .build()) {
            long startTime = System.currentTimeMillis();
            QueryResponse qr = client.query(query);
            long duration = System.currentTimeMillis() - startTime;

            // Calculate response size
            long responseSize = 0;
            for(SolrDocument doc : qr.getResults()) {
                String json = (String) doc.get("_json");
                if(json != null) {
                    responseSize += json.length();
                }
            }

            // Log memory after query
            long memAfter = runtime.totalMemory() - runtime.freeMemory();
            long memDelta = memAfter - memBefore;

            logger.info("SOLR DISPATCH COMPLETE - Core: {}, Results: {}, ResponseSize: {}MB, Duration: {}ms, MemoryDelta: {}MB, MemoryAfter: {}MB / {}MB ({}% used)",
                    core,
                    qr.getResults().getNumFound(),
                    responseSize / 1024 / 1024,
                    duration,
                    memDelta / 1024 / 1024,
                    memAfter / 1024 / 1024,
                    maxMem / 1024 / 1024,
                    (memAfter * 100) / maxMem);

            if(responseSize > 100 * 1024 * 1024) {
                logger.warn("LARGE SOLR DISPATCH RESPONSE - {}MB - Core: {}, Query: {}", responseSize / 1024 / 1024, core, query.toQueryString());
            }
            return qr;
        }
    }
}
