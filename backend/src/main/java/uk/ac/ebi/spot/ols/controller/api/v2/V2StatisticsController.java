package uk.ac.ebi.spot.ols.controller.api.v2;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import uk.ac.ebi.spot.ols.model.v2.V2Statistics;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/stats")
public class V2StatisticsController {

    @Autowired
    OlsSolrClient solrClient;

    @RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2Statistics> getStatistics() throws ResourceNotFoundException, IOException {

        Map<String,Object> coreStatus = solrClient.getCoreStatus();
        Map<String,Object> indexStatus = (Map<String,Object>) coreStatus.get("index");
        String lastModified = (String) indexStatus.get("lastModified");

        SolrQuery query = new SolrQuery();

        query.setQuery("*:*");
        query.setFacet(true);
        query.addFacetField("type");
        query.setRows(0);

        QueryResponse qr = solrClient.runSolrQuery(query, null);

        Map<String,Integer> counts = new HashMap<>();

        for(FacetField.Count count : qr.getFacetField("type").getValues()) {
            counts.put(count.getName(), (int)count.getCount());
        }

        V2Statistics stats = new V2Statistics();
        stats.lastModified = lastModified;
        stats.numberOfOntologies = counts.containsKey("ontology") ? counts.get("ontology") : 0;
        stats.numberOfClasses = counts.containsKey("class") ? counts.get("class") : 0;
        stats.numberOfIndividuals = counts.containsKey("individual") ? counts.get("individual") : 0;
        stats.numberOfProperties = counts.containsKey("property") ? counts.get("property") : 0;

        return new ResponseEntity<>( stats, HttpStatus.OK);
    }

}
