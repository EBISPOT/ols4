package uk.ac.ebi.spot.ols.controller.api.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class V2ReleaseControllerWIT {
    @Test
    void servesBundledReleaseIdentityAndHistoryWithoutADatabase() throws Exception {
        var fixture = """
                {"current":{"version":"4.1.0","commit":"abc123","releasedAt":"2026-10-02T00:00:00Z",
                "doi":"10.5281/zenodo.123","releaseUrl":"https://github.com/EBISPOT/ols4/releases/tag/v4.1.0",
                "notes":"### Fixed\\n* Better search"},"conceptDoi":"10.5281/zenodo.122",
                "releases":[{"version":"4.1.0"},{"version":"4.0.1"}]}
                """;
        var controller = new V2ReleaseController(new ObjectMapper(),
                new ByteArrayResource(fixture.getBytes(StandardCharsets.UTF_8)));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/v2/releases"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.current.version").value("4.1.0"))
                .andExpect(jsonPath("$.current.doi").value("10.5281/zenodo.123"))
                .andExpect(jsonPath("$.conceptDoi").value("10.5281/zenodo.122"))
                .andExpect(jsonPath("$.releases[1].version").value("4.0.1"));
        mvc.perform(post("/api/v2/releases")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void ordinaryBuildHasNoInventedStableVersion() throws Exception {
        var controller = new V2ReleaseController(new ObjectMapper());
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/v2/releases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").isEmpty())
                .andExpect(jsonPath("$.conceptDoi").isEmpty())
                .andExpect(jsonPath("$.releases").isArray())
                .andExpect(jsonPath("$.releases").isEmpty());
    }
}
