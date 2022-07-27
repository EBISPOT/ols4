package uk.ac.ebi.spot.ols.repository;

import com.google.gson.Gson;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.config.SearchConfiguration;
import uk.ac.ebi.spot.ols.service.OwlGraphNode;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;


@Component
public class SolrQueryHelper {

    @Autowired
    SearchConfiguration searchConfiguration;

    private Gson gson = new Gson();

    public Collection<OwlGraphNode> searchSolr(SolrQuery query) throws IOException {

        QueryResponse qr = runSolrQuery(query);

        return qr.getResults()
                .stream()
                .map(res -> new OwlGraphNode(gson.fromJson((String) res.get("_json"), Map.class)))
                .collect(Collectors.toList());
    }

    public Page<OwlGraphNode> searchSolrPaginated(SolrQuery query, Pageable pageable) throws IOException {

        query.setStart(pageable.getOffset());
        query.setRows(pageable.getPageSize());

        QueryResponse qr = runSolrQuery(query);

        return new PageImpl<OwlGraphNode>(
                qr.getResults()
                        .stream()
                        .map(res -> new OwlGraphNode(gson.fromJson((String) res.get("_json"), Map.class)))
                        .collect(Collectors.toList()),
                pageable, qr.getResults().getNumFound());
    }

    public QueryResponse runSolrQuery(SolrQuery query) throws IOException {

        System.out.println("solr query: " + query.toQueryString());

        SolrClient mySolrClient = new HttpSolrClient.Builder(this.searchConfiguration.getOlsSearchServer() + "/solr/ols4").build();

        QueryResponse qr = null;
        try {
            qr = mySolrClient.query(query);
        } catch (SolrServerException e) {
            throw new IOException(e);
        }

        return qr;
    }

    public static SolrQuery createSolrQuery(String lang, String search, String searchFields) {

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

    public static void addDynamicFilterProperties(SolrQuery query, Map<String,String> properties) {

        for(String k : properties.keySet()) {

            String value = properties.get(k);

            k = k.replace(":", "__");

            query.addFilterQuery(ClientUtils.escapeQueryChars(k) + ":" + ClientUtils.escapeQueryChars(value));
        }
    }



}
