package uk.ac.ebi.spot.ols.controller.api.v2;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import uk.ac.ebi.spot.ols.controller.api.exception.ResourceNotFoundException;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.ols.model.v2.V2Statistics;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;

import java.io.IOException;
import java.util.Map;
@Tag(name = "V2 Stats Controller", description = "This endpoint provides statistics about the current state of the ontology index. It includes the number of ontologies, classes, individuals and properties indexed, and the last time the index was modified.")
@RestController
@RequestMapping("/api/v2/stats")
public class V2StatisticsController {

    @Autowired
    OlsSearchClient searchClient;

    @RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2Statistics> getStatistics() throws ResourceNotFoundException, IOException {

        String lastModified = searchClient.getLastModified();

        Map<String, Long> counts = searchClient.getCountsByField("type");

        V2Statistics stats = new V2Statistics();
        stats.lastModified = lastModified;
        stats.numberOfOntologies = counts.getOrDefault("ontology", 0L).intValue();
        stats.numberOfClasses = counts.getOrDefault("class", 0L).intValue();
        stats.numberOfIndividuals = counts.getOrDefault("individual", 0L).intValue();
        stats.numberOfProperties = counts.getOrDefault("property", 0L).intValue();

        return new ResponseEntity<>( stats, HttpStatus.OK);
    }

}
