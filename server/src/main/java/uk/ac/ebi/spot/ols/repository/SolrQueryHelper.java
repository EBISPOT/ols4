package uk.ac.ebi.spot.ols.repository;

import com.google.gson.Gson;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.service.OntologyEntity;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;


@Component
public class SolrQueryHelper {


    @NotNull
    @org.springframework.beans.factory.annotation.Value("${ols.solr.host:http://localhost:8999}")
    public String host = "http://localhost:8999";


    private Gson gson = new Gson();

    public Collection<OntologyEntity> searchSolr(SolrQuery query) {

        QueryResponse qr = runSolrQuery(query);

        return qr.getResults()
                .stream()
                .map(res -> new OntologyEntity(solrDocumentToOntologyEntity(res)))
                .collect(Collectors.toList());
    }

    public Page<OntologyEntity> searchSolrPaginated(SolrQuery query, Pageable pageable) {

        query.setStart(pageable.getOffset());
        query.setRows(pageable.getPageSize());

        QueryResponse qr = runSolrQuery(query);

        return new PageImpl<OntologyEntity>(
                qr.getResults()
                        .stream()
                        .map(res -> new OntologyEntity(solrDocumentToOntologyEntity(res)))
                        .collect(Collectors.toList()),
                pageable, qr.getResults().getNumFound());
    }

    public OntologyEntity getOne(SolrQuery query) {

        QueryResponse qr = runSolrQuery(query);

        if(qr.getResults().getNumFound() != 1) {
            throw new RuntimeException("Expected exactly 1 result for solr getOne");
        }

        return solrDocumentToOntologyEntity(qr.getResults().get(0));
    }

    private OntologyEntity solrDocumentToOntologyEntity(SolrDocument doc) {
        return new OntologyEntity(
                gson.fromJson((String) doc.get("_json"), Map.class)
        );
    }

    public QueryResponse runSolrQuery(SolrQuery query) {

        System.out.println("solr query: " + query.toQueryString());
        System.out.println("solr host: " + host);

        SolrClient mySolrClient = new HttpSolrClient.Builder(host + "/solr/ols4").build();

        QueryResponse qr = null;
        try {
            qr = mySolrClient.query(query);
        } catch (SolrServerException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return qr;
    }

    public SolrQuery createSolrQuery(String lang, String search, String searchFields) {

        SolrQuery query = new SolrQuery();

        query.set("defType", "edismax");
        query.addFilterQuery("lang:" + lang);

        if(search != null) {
            query.setQuery(search);
            query.set("qf", searchFields.replace(":", "__"));
        } else {
            query.setQuery("*:*");
        }

        return query;
    }

    public void addDynamicFilterProperties(SolrQuery query, Map<String,String> properties) {

        for(String k : properties.keySet()) {

            String value = properties.get(k);

            k = k.replace(":", "__");

            query.addFilterQuery(ClientUtils.escapeQueryChars(k) + ":" + ClientUtils.escapeQueryChars(value));
        }
    }



}
