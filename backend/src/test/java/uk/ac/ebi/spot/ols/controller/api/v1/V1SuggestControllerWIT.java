package uk.ac.ebi.spot.ols.controller.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.ols.config.WebConfig;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(V1SuggestController.class)
@ContextConfiguration(classes = {
        V1SuggestController.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V1SuggestControllerWIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OlsSearchClient searchClient;

    @BeforeEach
    void stubSuggestions() {
        when(searchClient.suggestLabels("liver", null, 0, 10))
                .thenReturn(List.of("Liver disease", "Liver ailment"));
        when(searchClient.suggestLabels("liv", List.of("efo", "duo"), 0, 10))
                .thenReturn(List.of("Liver disease"));
        when(searchClient.suggestLabels("liv", List.of("efo", "duo"), 2, 1))
                .thenReturn(List.of("Liver disease"));
    }

    @Test
    void returnsTheDefaultLegacySuggestionContract() throws Exception {
        mockMvc.perform(get("/api/suggest").param("q", "LIVER"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.responseHeader.status").value(0))
                .andExpect(jsonPath("$.responseHeader.QTime").value(0))
                .andExpect(jsonPath("$.response.numFound").value(2))
                .andExpect(jsonPath("$.response.start").value(0))
                .andExpect(jsonPath("$.response.docs[0].autosuggest").value("Liver disease"))
                .andExpect(jsonPath("$.response.docs[1].autosuggest").value("Liver ailment"));

        verify(searchClient).suggestLabels("liver", null, 0, 10);
    }

    @Test
    void bindsRepeatedOntologyValuesAndPaginationAndReturnsTheRequestedStart() throws Exception {
        mockMvc.perform(get("/api/suggest")
                        .param("q", "LIV")
                        .param("ontology", "efo", "duo")
                        .param("start", "2")
                        .param("rows", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response.numFound").value(1))
                .andExpect(jsonPath("$.response.start").value(2))
                .andExpect(jsonPath("$.response.docs[0].autosuggest").value("Liver disease"));

        verify(searchClient).suggestLabels("liv", List.of("efo", "duo"), 2, 1);
    }

    @Test
    void bindsCommaSeparatedOntologyValues() throws Exception {
        mockMvc.perform(get("/api/suggest")
                        .param("q", "LIV")
                        .param("ontology", "efo,duo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.docs[0].autosuggest").value("Liver disease"));

        verify(searchClient).suggestLabels("liv", List.of("efo", "duo"), 0, 10);
    }

    @Test
    void ignoresUnsupportedReservedAndDynamicStyleParametersForCompatibility() throws Exception {
        mockMvc.perform(get("/api/suggest")
                        .param("q", "LIVER")
                        .param("exactMatch", "false")
                        .param("includeObsoleteEntities", "false"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response.numFound").value(2))
                .andExpect(jsonPath("$.response.docs[0].autosuggest").value("Liver disease"));

        verify(searchClient).suggestLabels("liver", null, 0, 10);
    }

    @Test
    void rejectsMalformedOntologyIdsWithStableErrorFields() throws Exception {
        mockMvc.perform(get("/api/suggest")
                        .param("q", "liv")
                        .param("ontology", "efo/unsafe"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(searchClient);
    }

    @ParameterizedTest
    @CsvSource({
            "rows, not-a-number",
            "start, not-a-number"
    })
    void rejectsMalformedPaginationValuesWithStableErrorFields(String parameter, String value)
            throws Exception {
        mockMvc.perform(get("/api/suggest").param("q", "liv").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsMissingRequiredQueryWithStableErrorFields() throws Exception {
        mockMvc.perform(get("/api/suggest"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void returnsMethodNotAllowedForUnsupportedMethod() throws Exception {
        mockMvc.perform(post("/api/suggest"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
