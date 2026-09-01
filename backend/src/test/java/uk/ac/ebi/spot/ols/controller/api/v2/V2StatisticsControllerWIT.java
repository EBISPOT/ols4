package uk.ac.ebi.spot.ols.controller.api.v2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import uk.ac.ebi.spot.ols.config.WebConfig;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(V2StatisticsController.class)
@ContextConfiguration(classes = {
        V2StatisticsController.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V2StatisticsControllerWIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OlsSearchClient searchClient;

    @BeforeEach
    void stubStatistics() {
        when(searchClient.getLastModified()).thenReturn("2026-08-25T00:00:00Z");
        when(searchClient.getCountsByField("type")).thenReturn(Map.of(
                "ontology", 4L,
                "class", 4L,
                "individual", 2L,
                "property", 1L));
    }

    @Test
    void returnsTheStatisticsContract() throws Exception {
        mockMvc.perform(get("/api/v2/stats"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.lastModified").value("2026-08-25T00:00:00Z"))
                .andExpect(jsonPath("$.numberOfOntologies").value(4))
                .andExpect(jsonPath("$.numberOfClasses").value(4))
                .andExpect(jsonPath("$.numberOfIndividuals").value(2))
                .andExpect(jsonPath("$.numberOfProperties").value(1));
    }

    @Test
    void supportsTheDeclaredHalMediaType() throws Exception {
        mockMvc.perform(get("/api/v2/stats").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON))
                .andExpect(jsonPath("$.numberOfOntologies").value(4));
    }

    @Test
    void returnsMethodNotAllowedForUnsupportedMethod() throws Exception {
        mockMvc.perform(post("/api/v2/stats"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405));
    }
}
