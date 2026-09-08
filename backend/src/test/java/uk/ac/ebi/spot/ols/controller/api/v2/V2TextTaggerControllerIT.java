package uk.ac.ebi.spot.ols.controller.api.v2;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin controller-IT coverage for {@link V2TextTaggerController} against a real, unconfigured
 * {@code TextTaggerService} bean and a real disposable Postgres.
 *
 * <p>See the "Implemented V2 text-tagger-controller baseline" section of
 * {@code docs/backend-testing-strategy.md} for the scoping decision this suite follows: the
 * {@code ols_text_tagger} table exists in the production schema but no fixture here inserts a row
 * into it, so {@code TextTaggerService.downloadTextTaggerDb()} genuinely returns {@code null} and
 * {@code isAvailable()} genuinely settles to {@code false} &ndash; this is real behaviour of the
 * real bean against a real (empty-of-this-one-table) database, not a mock standing in for it.</p>
 *
 * <p>Timing note: {@code available} is a field that defaults to {@code false} at construction and
 * is only ever set {@code true} inside {@code startProcess()}, which {@code downloadTextTaggerDb()}
 * never reaches when the table has no row. So {@code isAvailable()} is already correct the instant
 * the bean is constructed, before {@link uk.ac.ebi.spot.ols.service.TextTaggerService#init()}'s
 * background thread even runs, let alone finishes &ndash; there is no async race for these
 * assertions to be sensitive to. {@code init()} is still invoked below so this suite exercises the
 * bean's real lifecycle, not just its field defaults.</p>
 */
@Testcontainers
class V2TextTaggerControllerIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.TextTaggerRepositoryHandle repositoryHandle;
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUpApplicationPath() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createTextTaggerRepositories(POSTGRES);
        repositoryHandle.textTaggerService().init();

        V2TextTaggerController controller = new V2TextTaggerController();
        controller.textTaggerService = repositoryHandle.textTaggerService();
        controller.searchClient = repositoryHandle.searchClient();
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void tagTextReturns503ThroughTheRealUnconfiguredService() throws Exception {
        mockMvc.perform(post("/api/v2/tag_text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"insulin resistance\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Text tagger service is not available"))
                .andExpect(jsonPath("$.message").value(
                        "The text tagger database has not been configured or the binary is not on the PATH"));
    }

    @Test
    void tagTextStatusReportsUnavailableThroughTheRealService() throws Exception {
        mockMvc.perform(get("/api/v2/tag_text"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void curationSourcesReturnsTheRealSearchClientResultThroughPostgres() throws Exception {
        mockMvc.perform(get("/api/v2/curation_sources"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
