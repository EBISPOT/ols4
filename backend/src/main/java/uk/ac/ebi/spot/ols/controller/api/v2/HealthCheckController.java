package uk.ac.ebi.spot.ols.controller.api.v2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.ols.service.PostgresClient;

import java.sql.SQLException;

@RestController
@RequestMapping("/api/v2")
public class HealthCheckController {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckController.class);
    private final PostgresClient postgresClient;

    public HealthCheckController(PostgresClient postgresClient) {
        this.postgresClient = postgresClient;
    }

    @RequestMapping("/health")
    public ResponseEntity<String> checkHealth() {
        try {
            if (postgresClient.hasLoadedOntologyData()) {
                return ResponseEntity.ok("All systems are operational.");
            }
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Search is not initialized.");
        } catch (SQLException | RuntimeException e) {
            logger.warn("PostgreSQL readiness check failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Postgres is not initialized.");
        }
    }
}
