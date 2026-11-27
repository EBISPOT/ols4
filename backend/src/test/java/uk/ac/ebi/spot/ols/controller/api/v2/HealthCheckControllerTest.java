package uk.ac.ebi.spot.ols.controller.api.v2;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import uk.ac.ebi.spot.ols.service.PostgresClient;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthCheckControllerTest {

    private final PostgresClient postgresClient = mock(PostgresClient.class);
    private final HealthCheckController controller = new HealthCheckController(postgresClient);

    @Test
    void reportsReadyWhenOntologiesAreLoaded() throws SQLException {
        when(postgresClient.hasLoadedOntologyData()).thenReturn(true);

        var response = controller.checkHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("All systems are operational.");
    }

    @Test
    void reportsUnavailableWhenOntologiesAreMissing() throws SQLException {
        when(postgresClient.hasLoadedOntologyData()).thenReturn(false);

        var response = controller.checkHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo("Search is not initialized.");
    }

    @Test
    void reportsUnavailableWhenPostgresFails() throws SQLException {
        when(postgresClient.hasLoadedOntologyData()).thenThrow(new SQLException("postgres unreachable"));

        var response = controller.checkHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo("Postgres is not initialized.");
    }
}
