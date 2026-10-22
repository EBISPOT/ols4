package uk.ac.ebi.spot.ols.controller.api.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;

/** Release identity is built into the image and never looked up from GitHub at runtime. */
@Tag(name = "V2 Releases", description = "Software release deployed in this backend and its release history. Ontology data updates are independent.")
@RestController
public class V2ReleaseController {
    private final JsonNode releases;

    @Autowired
    public V2ReleaseController(ObjectMapper mapper) throws IOException {
        this(mapper, new ClassPathResource("ols-release.json"));
    }

    V2ReleaseController(ObjectMapper mapper, Resource resource) throws IOException {
        try (InputStream input = resource.getInputStream()) {
            releases = mapper.readTree(input);
        }
    }

    @GetMapping(value = "/api/v2/releases", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getReleases() {
        return releases;
    }
}
