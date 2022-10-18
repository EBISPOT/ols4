package uk.ac.ebi.spot.ols.repository.solr;

import com.google.gson.Gson;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.service.OntologyEntity;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrQuery;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;


@Component
public class OlsSolrClient {


    @NotNull
    @org.springframework.beans.factory.annotation.Value("${ols.solr.host:http://localhost:8999}")
    public String host = "http://localhost:8999";


    private Gson gson = new Gson();

    public Collection<OntologyEntity> searchSolr(OlsSolrQuery query) {

        QueryResponse qr = runSolrQuery(query, null);

        return qr.getResults()
                .stream()
                .map(res -> new OntologyEntity(solrDocumentToOntologyEntity(res)))
                .collect(Collectors.toList());
    }

    public Page<OntologyEntity> searchSolrPaginated(OlsSolrQuery query, Pageable pageable) {

        QueryResponse qr = runSolrQuery(query, pageable);

        return new PageImpl<OntologyEntity>(
                qr.getResults()
                        .stream()
                        .map(res -> new OntologyEntity(solrDocumentToOntologyEntity(res)))
                        .collect(Collectors.toList()),
                pageable, qr.getResults().getNumFound());
    }

    public OntologyEntity getOne(OlsSolrQuery query) {

        QueryResponse qr = runSolrQuery(query, null);

        if(qr.getResults().getNumFound() != 1) {
            throw new RuntimeException("Expected exactly 1 result for solr getOne, but got " + qr.getResults().getNumFound());
        }

        return solrDocumentToOntologyEntity(qr.getResults().get(0));
    }

    private OntologyEntity solrDocumentToOntologyEntity(SolrDocument doc) {
        return new OntologyEntity(
                gson.fromJson((String) doc.get("_json"), Map.class)
        );
    }

    public QueryResponse runSolrQuery(OlsSolrQuery query, Pageable pageable) {
	return runSolrQuery(query.constructQuery(), pageable);
    }

    public QueryResponse runSolrQuery(SolrQuery query, Pageable pageable) {

	if(pageable != null) {
		query.setStart(pageable.getOffset());
		query.setRows(pageable.getPageSize());
	}

        System.out.println("solr query: " + query.toQueryString());
        System.out.println("solr host: " + host);

        org.apache.solr.client.solrj.SolrClient mySolrClient = new HttpSolrClient.Builder(host + "/solr/ols4").build();

        QueryResponse qr = null;
        try {
            qr = mySolrClient.query(query);
        } catch (SolrServerException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println("solr query had " + qr.getResults().getNumFound() + " result(s)");

        return qr;
    }



}
