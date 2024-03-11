package uk.ac.ebi.spot.ols.controller.api.v2;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.apache.commons.collections4.map.MultiKeyMap;
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
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.ols.model.v2.V2Statistics;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.repository.v1.V1OntologyRepository;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/v2")
public class V2StatisticsController {

    @Autowired
    OlsSolrClient solrClient;

    @Autowired
    private V1OntologyRepository ontologyRepository;

    @RequestMapping(path = "/stats", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2Statistics> getStatistics() throws ResourceNotFoundException, IOException {
        return new ResponseEntity<>( computeStats("*:*"), HttpStatus.OK);
    }

    @RequestMapping(path = "/statsby", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2Statistics> getStatistics(
            @RequestParam(value = "schema", required = false) Collection<String> schemas,
            @RequestParam(value = "classification", required = false) Collection<String> classifications,
            @Parameter(description = "Set to true (default setting is false) for intersection (default behavior is union) of classifications.")
            @RequestParam(value = "exclusive", required = false, defaultValue = "false") boolean exclusive,
            @RequestParam(value = "ontologyIds", required = false) Collection<String> ontologyIds,
            @RequestParam(value = "lang", defaultValue = "en") String lang) throws ResourceNotFoundException, IOException{

        ontologyIds = ontologyRepository.filterOntologyIDs(schemas,classifications,ontologyIds,exclusive,lang);

        StringBuilder sb = new StringBuilder();
        for (String id : ontologyIds){
            sb.append("ontologyId:").append(id).append(" OR ");
        }

        String queryString = sb.toString().substring(0,sb.toString().lastIndexOf(" OR "));
        return new ResponseEntity<>( computeStats(queryString), HttpStatus.OK);
    }

    @RequestMapping(path = "/allstatsbyschema", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<MultiKeyMap> getStatisticsBySchema(
            @RequestParam(value = "schema", required = false) Collection<String> schemas,
            @RequestParam(value = "lang", defaultValue = "en") String lang

    ) throws IOException {
        MultiKeyMap summaries = new MultiKeyMap();

        Collection<String> keys = ontologyRepository.getSchemaKeys(lang);

        for (String key : keys) {
            Set<String> values = ontologyRepository.getSchemaValues(Collections.singleton(key),lang);

            for (String value : values) {
                summaries.put(key,value, getStatistics(Collections.singleton(key),Collections.singleton(value), false,Collections.emptySet(),lang));
            }
        }

        return new ResponseEntity<>( summaries, HttpStatus.OK);
    }

    private V2Statistics computeStats(String queryString) throws IOException {

        Map<String,Object> coreStatus = solrClient.getCoreStatus();
        Map<String,Object> indexStatus = (Map<String,Object>) coreStatus.get("index");
        String lastModified = (String) indexStatus.get("lastModified");

        SolrQuery query = new SolrQuery();
        query.setQuery(queryString);
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

        return stats;
    }
}
