package uk.ac.ebi.spot.ols.controller.api.v1;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
class V1SuggestControllerIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.SearchClientHandle searchClientHandle;
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUpApplicationPath() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        searchClientHandle = PostgresIntegrationTestSupport.createSearchClient(POSTGRES);

        V1SuggestController controller = new V1SuggestController();
        org.springframework.test.util.ReflectionTestUtils.setField(
                controller, "searchClient", searchClientHandle.searchClient());
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterAll
    static void closeDatabaseClient() {
        searchClientHandle.close();
    }

    @Test
    void returnsSuggestionsThroughTheControllerAndPostgres() throws Exception {
        mockMvc.perform(get("/api/suggest")
                        .param("q", "LIVER")
                        .param("start", "1")
                        .param("rows", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response.numFound").value(1))
                .andExpect(jsonPath("$.response.start").value(1))
                .andExpect(jsonPath("$.response.docs[0].autosuggest").value("Liver disease"));
    }
}
