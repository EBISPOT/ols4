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
import uk.ac.ebi.spot.ols.repository.OntologyRepository;
import uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import java.util.Map;

@RestController
@RequestMapping("/api/v2")
public class HealthCheckController {
    @Autowired
    OntologyRepository ontologyRepository;

    @Autowired
    OlsPostgresClient postgresClient;
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckController.class);

    @RequestMapping("/health")
    public ResponseEntity<String> checkHealth() {
        if (!checkSearch()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Search is not initialized.");
        }
        if (!checkPostgres()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Postgres is not initialized.");
        }
        return ResponseEntity.ok("All systems are operational.");
    }

    private boolean checkPostgres() {
        try {
            if (postgresClient.getDatabaseNodeCount() > 0) {
                logger.debug("Postgres is initialized.");
                return true;
            } else {
                logger.error("Postgres is not initialized yet as entity count was less than 1.");
                return false;
            }
        } catch (Exception e) {
            logger.error("Postgres endpoint returned an error.", e);
            return false;
        }
    }

    private boolean checkSearch() {
        Pageable pageable = Pageable.ofSize(20);
        try {
            V2PagedAndFacetedResponse<V2Entity> result = new V2PagedAndFacetedResponse<V2Entity>(
                    ontologyRepository.find(pageable, "en", null, null, null,
                            false, Map.of(), new JsonTransformOptions())
                    .map(V2Entity::new)
                            );
            if (result.totalElements > 0) {
                logger.debug("Search is initialized.");
                return true;
            } else {
                logger.error("Search is not initialized yet as 'totalElements' in response not found or less than 1.");
                return false;
            }
        } catch (Exception e) {
            logger.error("Search health check returned an error.", e);
            return false;
        }
    }
}
