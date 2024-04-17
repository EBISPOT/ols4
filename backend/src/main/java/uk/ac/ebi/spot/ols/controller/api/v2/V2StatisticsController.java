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
import uk.ac.ebi.spot.ols.model.FilterOption;
import uk.ac.ebi.spot.ols.model.v2.V2Statistics;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.repository.v2.V2OntologyRepository;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/v2")
public class V2StatisticsController {

    @Autowired
    OlsSolrClient solrClient;

    @Autowired
    private V2OntologyRepository ontologyRepository;

    @Operation(description = "Get Whole System Statistics. Components in all ontologies are taken into consideration")
    @RequestMapping(path = "/stats", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2Statistics> getStatistics() throws ResourceNotFoundException, IOException {
        return new ResponseEntity<>( computeStats("*:*"), HttpStatus.OK);
    }

    @Operation(description = "Get Schema and Classification based Statistics. Possible schema keys and possible classification values of particular keys can be inquired with /api/ontologies/schemakeys and /api/ontologies/schemavalues methods respectively.")
    @RequestMapping(path = "/statsby", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2Statistics> getStatistics(
            @RequestParam(value = "schema", required = false) Collection<String> schemas,
            @RequestParam(value = "classification", required = false) Collection<String> classifications,
            @Parameter(description = "Set to true (default setting is false) for intersection (default behavior is union) of classifications.")
            @RequestParam(value = "ontologyIds", required = false) Collection<String> ontologyIds,
            @RequestParam(value = "exclusive", required = false, defaultValue = "false") boolean exclusive,
            @Parameter(description = "Use License option to filter based on license.label, license.logo and license.url variables. " +
                    "Use Composite Option to filter based on the objects (i.e. collection, subject) within the classifications variable. " +
                    "Use Linear option to filter based on String and Collection<String> based variables.")
            @RequestParam(value = "option", required = false, defaultValue = "LINEAR") FilterOption filterOption,
            @RequestParam(value = "lang", defaultValue = "en") String lang) throws ResourceNotFoundException, IOException{

        ontologyIds = ontologyRepository.filterOntologyIDs(schemas,classifications,ontologyIds,exclusive,filterOption,lang);
        StringBuilder sb = new StringBuilder();
        String queryString = "none";
        if(ontologyIds != null){
            for (String id : ontologyIds){
                sb.append("ontologyId:").append(id).append(" OR ");
            }
            queryString = sb.toString().substring(0,sb.toString().lastIndexOf(" OR "));
        }
        return new ResponseEntity<>( computeStats(queryString), HttpStatus.OK);
    }
    @Operation(description = "Get Composite Schema based Statistics. All schemas with their respective classifications under the classifications variable will be computed.")
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
                summaries.put(key,value, getStatistics(Collections.singleton(key),Collections.singleton(value), Collections.emptySet(),false,FilterOption.LINEAR,lang));
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
