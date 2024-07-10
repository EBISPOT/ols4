package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v2")
public class HealthCheckController {

    @Value("${solr.url}")
    private String solrUrl;

    @Value("${neo4j.url}")
    private String neo4jUrl;

    private final RestTemplate restTemplate;

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckController.class);

    public HealthCheckController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @RequestMapping("/health")
    public ResponseEntity<String> checkHealth() {
        if (!checkNeo4j()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Neo4j is not initialized.");
        }

        if (!checkSolr()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Solr is not initialized.");
        }

        return ResponseEntity.ok("All systems are operational.");
    }

    private boolean checkNeo4j() {
        try {
            String decodedUrl = UriUtils.decode(neo4jUrl, StandardCharsets.UTF_8);
            ResponseEntity<String> response = restTemplate.getForEntity(decodedUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonObject jsonResponse = JsonParser.parseString(response.getBody()).getAsJsonObject();
                if (!jsonResponse.has("nodes") || jsonResponse.getAsJsonArray("nodes").size() == 0 ||
                        !jsonResponse.has("edges") || jsonResponse.getAsJsonArray("edges").size() == 0) {
                    logger.error("Neo4j is not initialized yet as 'nodes' or 'edges' object is empty");
                    return false;
                }
                logger.info("Neo4J is initialized.");
                return true;
            }
        } catch (Exception e) {
            logger.error("Neo4j endpoint returned an error.", e);
        }
        return false;
    }

    private boolean checkSolr() {
        try {
            String decodedUrl = UriUtils.decode(solrUrl, StandardCharsets.UTF_8);
            ResponseEntity<String> response = restTemplate.getForEntity(decodedUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonObject jsonResponse = JsonParser.parseString(response.getBody()).getAsJsonObject();
                if (jsonResponse.has("page") && jsonResponse.getAsJsonObject("page").get("totalElements").getAsInt() > 1) {
                    logger.info("Solr is initialized.");
                    return true;
                } else {
                    logger.error("Solr is not initialized yet as 'totalElements' in 'page' object is not greater than 1");
                    return false;
                }
            } else {
                logger.error("Solr endpoint returned status code " + response.getStatusCodeValue());
                return false;
            }
        } catch (Exception e) {
            logger.error("Solr endpoint returned an error.", e);
            return false;
        }
    }
}
