package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.ols.config.WebConfig;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.repository.OntologyRepository;
import uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
    private OntologyRepository ontologyRepository;

    @MockitoBean
    private OlsPostgresClient postgresClient;

    @Test
    void returnsAllSystemsOperationalWhenBothChecksPass() throws Exception {
        stubSearch(4);
        when(postgresClient.getDatabaseNodeCount()).thenReturn(10L);

        mockMvc.perform(get("/api/v2/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("All systems are operational."));
    }

    @Test
    void returns503WhenSearchHasNoResults() throws Exception {
        stubSearch(0);
        when(postgresClient.getDatabaseNodeCount()).thenReturn(10L);

        mockMvc.perform(get("/api/v2/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Search is not initialized."));
    }

    @Test
    void returns503WhenSearchThrows() throws Exception {
        when(ontologyRepository.find(
                any(Pageable.class), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenThrow(new RuntimeException("search backend unreachable"));
        when(postgresClient.getDatabaseNodeCount()).thenReturn(10L);

        mockMvc.perform(get("/api/v2/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Search is not initialized."));
    }

    @Test
    void returns503WhenSearchIsHealthyButPostgresNodeCountIsZero() throws Exception {
        stubSearch(4);
        when(postgresClient.getDatabaseNodeCount()).thenReturn(0L);

        mockMvc.perform(get("/api/v2/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Postgres is not initialized."));
    }

    @Test
    void returns503WhenSearchIsHealthyButPostgresThrows() throws Exception {
        stubSearch(4);
        when(postgresClient.getDatabaseNodeCount())
                .thenThrow(new RuntimeException("postgres unreachable"));

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
        stubSearch(4);
        when(postgresClient.getDatabaseNodeCount()).thenReturn(10L);

        mockMvc.perform(post("/api/v2/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("All systems are operational."));
    }

    private void stubSearch(long totalElements) throws Exception {
        when(ontologyRepository.find(
                any(Pageable.class), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(new OlsFacetedResultsPage<JsonElement>(
                        List.of(), Map.of(), Pageable.ofSize(20), totalElements));
    }
}
