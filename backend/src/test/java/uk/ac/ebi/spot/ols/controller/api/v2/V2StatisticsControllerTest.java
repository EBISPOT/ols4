package uk.ac.ebi.spot.ols.controller.api.v2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import uk.ac.ebi.spot.ols.model.v2.V2Statistics;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V2StatisticsControllerTest {

    private static final String LAST_MODIFIED = "2026-08-25T00:00:00Z";

    private OlsSearchClient searchClient;
    private V2StatisticsController controller;

    @BeforeEach
    void setUp() {
        searchClient = mock(OlsSearchClient.class);
        controller = new V2StatisticsController();
        controller.searchClient = searchClient;
    }

    @Test
    void mapsSearchClientCountsIntoTheStatisticsResponse() throws Exception {
        when(searchClient.getLastModified()).thenReturn(LAST_MODIFIED);
        when(searchClient.getCountsByField("type")).thenReturn(Map.of(
                "ontology", 4L,
                "class", 4L,
                "individual", 2L,
                "property", 1L));

        var response = controller.getStatistics();
        V2Statistics statistics = response.getBody();

        assertNotNull(statistics);
        assertEquals(HttpStatus.OK,
                ((org.springframework.http.ResponseEntity<?>) response).getStatusCode());
        assertEquals(LAST_MODIFIED, statistics.lastModified);
        assertEquals(4, statistics.numberOfOntologies);
        assertEquals(4, statistics.numberOfClasses);
        assertEquals(2, statistics.numberOfIndividuals);
        assertEquals(1, statistics.numberOfProperties);
        verify(searchClient).getLastModified();
        verify(searchClient).getCountsByField("type");
    }

    @Test
    void defaultsMissingEntityTypeCountsToZero() throws Exception {
        when(searchClient.getLastModified()).thenReturn(LAST_MODIFIED);
        when(searchClient.getCountsByField("type")).thenReturn(Map.of("class", 4L));

        V2Statistics statistics = controller.getStatistics().getBody();

        assertNotNull(statistics);
        assertEquals(0, statistics.numberOfOntologies);
        assertEquals(4, statistics.numberOfClasses);
        assertEquals(0, statistics.numberOfIndividuals);
        assertEquals(0, statistics.numberOfProperties);
    }
}
