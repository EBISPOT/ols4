package uk.ac.ebi.spot.ols.repository.solr;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Component
public class OlsSolrClient {


    @NotNull
    @org.springframework.beans.factory.annotation.Value("${ols.solr.host:http://localhost:8999}")
    public String host = "http://localhost:8999";


    private Gson gson = new Gson();

    private static final Logger logger = LoggerFactory.getLogger(OlsSolrClient.class);
    public static final int MAX_ROWS = 1000;

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
            //return new JsonObject();
            throw new RuntimeException("Expected at least 1 result for solr getFirst");
        }

        return getOlsEntityFromSolrResult(qr.getResults().get(0));
    }

    public JsonElement getByIndex(OlsSolrQuery query, int i) {

        QueryResponse qr = runSolrQuery(query, null);

        if(qr.getResults().getNumFound() < 1) {
            logger.debug("Expected at least 1 result for solr getFirst for solr query = {}", query.constructQuery().jsonStr());
            throw new RuntimeException("Expected at least 1 result for solr getFirst");
        }

        return getOlsEntityFromSolrResult(qr.getResults().get(i));
    }

    public Set<JsonElement> getSet(OlsSolrQuery query){
        Set<JsonElement> tempSet = new HashSet<>();
        QueryResponse qr = runSolrQuery(query, null);
        for (int i = 0; i<qr.getResults().size();i++){
            tempSet.add(getOlsEntityFromSolrResult(qr.getResults().get(i)));
        }
        return tempSet;
    }

    private JsonElement getOlsEntityFromSolrResult(SolrDocument doc) {
        return JsonParser.parseString((String) doc.get("_json"));
    }

    public QueryResponse runSolrQuery(OlsSolrQuery query, Pageable pageable) {
	    return runSolrQuery(query.constructQuery(), pageable);
    }

    public QueryResponse runSolrQuery(SolrQuery query, Pageable pageable) {

        if(pageable != null) {
            query.setStart((int)pageable.getOffset());
            query.setRows(pageable.getPageSize() > MAX_ROWS ? MAX_ROWS : pageable.getPageSize());
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
        final int rows = query.getRows().intValue() > MAX_ROWS ? MAX_ROWS : query.getRows().intValue();
        query.setRows(rows);
        QueryResponse qr = mySolrClient.query(query);
        mySolrClient.close();
        return qr;
    }
}
