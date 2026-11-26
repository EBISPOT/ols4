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

import java.sql.Connection;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
class HealthCheckControllerIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.SearchClientHandle repositoryHandle;
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUpApplicationPath() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createSearchClient(POSTGRES);

        HealthCheckController controller = new HealthCheckController(repositoryHandle.postgresClient());
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
    void readinessReturns503PromptlyWhenTheOntologyTableIsBlocked() throws Exception {
        try (Connection lock = repositoryHandle.postgresClient().getConnection()) {
            lock.setAutoCommit(false);
            try {
                repositoryHandle.postgresClient().dsl(lock).execute("LOCK TABLE ols_entities IN ACCESS EXCLUSIVE MODE");

                assertTimeoutPreemptively(Duration.ofSeconds(4), () -> mockMvc.perform(get("/api/v2/health"))
                        .andExpect(status().isServiceUnavailable()));
            } finally {
                lock.rollback();
            }
        }

        mockMvc.perform(get("/api/v2/health")).andExpect(status().isOk());
    }

    @Test
    void readinessReturns503PromptlyWhenThePoolIsFull() throws Exception {
        var client = repositoryHandle.postgresClient();
        try (Connection first = client.getConnection();
             Connection second = client.getConnection();
             Connection third = client.getConnection()) {
            assertTimeoutPreemptively(Duration.ofSeconds(4), () -> mockMvc.perform(get("/api/v2/health"))
                    .andExpect(status().isServiceUnavailable()));
        }

        mockMvc.perform(get("/api/v2/health")).andExpect(status().isOk());
    }

    @Test
    void reportsAllSystemsOperationalThroughTheControllerAndPostgres() throws Exception {
        mockMvc.perform(get("/api/v2/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("All systems are operational."));
    }
}
