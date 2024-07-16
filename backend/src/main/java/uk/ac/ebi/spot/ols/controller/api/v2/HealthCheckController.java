package uk.ac.ebi.spot.ols.controller.api.v2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedAndFacetedResponse;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.neo4j.OlsNeo4jClient;
import uk.ac.ebi.spot.ols.repository.v2.V2OntologyRepository;

import java.util.Map;

@RestController
@RequestMapping("/api/v2")
public class HealthCheckController {
    @Autowired
    V2OntologyRepository ontologyRepository;

    @Autowired
    OlsNeo4jClient neo4jClient;
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckController.class);

    @RequestMapping("/health")
    public ResponseEntity<String> checkHealth() {
        if (!checkSolr()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Solr is not initialized.");
        }
        if (!checkNeo4j()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Neo4j is not initialized.");
        }
        return ResponseEntity.ok("All systems are operational.");
    }

    private boolean checkNeo4j() {
        try {
            if (neo4jClient.getDatabaseNodeCount() > 0) {
                logger.info("Neo4J is initialized.");
                return true;
            } else {
                logger.error("Neo4J is not initialized yet as Neo4J node elements were less than 1.");
                return false;
            }
        } catch (Exception e) {
            logger.error("Neo4j endpoint returned an error.", e);
            return false;
        }
    }

    private boolean checkSolr() {
        Pageable pageable = Pageable.ofSize(20);
        try {
            V2PagedAndFacetedResponse<V2Entity> result = new V2PagedAndFacetedResponse<>(
                    ontologyRepository.find(pageable, "en", null, null, null,
                            false, Map.of()));
            if (result.totalElements > 0) {
                logger.info("Solr is initialized.");
                return true;
            } else {
                logger.error("Solr is not initialized yet as 'totalElements' in jsonResponse not found or less than 1.");
                return false;
            }
        } catch (Exception e) {
            logger.error("Solr health check returned an error.", e);
            return false;
        }
    }
}
