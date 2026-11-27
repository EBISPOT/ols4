package uk.ac.ebi.spot.ols.controller.api.v2;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.ols.config.WebConfig;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.service.PostgresClient;

import java.sql.SQLException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthCheckController.class)
@ContextConfiguration(classes = {
        HealthCheckController.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class HealthCheckControllerWIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostgresClient postgresClient;

    @Test
    void returnsAllSystemsOperationalWhenOntologiesAreLoaded() throws Exception {
        when(postgresClient.hasLoadedOntologyData()).thenReturn(true);

        mockMvc.perform(get("/api/v2/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("All systems are operational."));
    }

    @Test
    void returns503WhenSearchHasNoResults() throws Exception {
        when(postgresClient.hasLoadedOntologyData()).thenReturn(false);

        mockMvc.perform(get("/api/v2/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Search is not initialized."));
    }

    @Test
    void returns503WhenPostgresThrows() throws Exception {
        when(postgresClient.hasLoadedOntologyData()).thenThrow(new SQLException("postgres unreachable"));

        mockMvc.perform(get("/api/v2/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Postgres is not initialized."));
    }

    @Test
    void respondsToNonGetHttpMethodsBecauseTheMappingIsMethodAgnostic() throws Exception {
        // The route is declared with a bare @RequestMapping("/health") and no explicit
        // method element, so Spring MVC matches every HTTP method rather than only GET.
        // This is a real, observed contract detail (unlike the sibling V2 controllers,
        // which use @GetMapping and reject other verbs with 405), not an assumption.
        when(postgresClient.hasLoadedOntologyData()).thenReturn(true);

        mockMvc.perform(post("/api/v2/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("All systems are operational."));
    }
}
