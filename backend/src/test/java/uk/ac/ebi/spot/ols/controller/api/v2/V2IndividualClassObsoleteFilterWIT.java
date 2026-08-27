package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.ols.config.WebConfig;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.repository.IndividualRepository;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(V2IndividualController.class)
@ContextConfiguration(classes = {
        V2IndividualController.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V2IndividualClassObsoleteFilterWIT {

    private static final URI ROUTE = URI.create(
            "/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_0001/individuals");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IndividualRepository individualRepository;

    @Test
    void excludesObsoleteIndividualsByDefaultAndAllowsExplicitOptIn() throws Exception {
        when(individualRepository.getIndividualsOfClass(any(), any(), any(), any(Boolean.class), any(), any()))
                .thenAnswer(invocation -> new OlsFacetedResultsPage<JsonElement>(
                        List.of(), Map.of(), invocation.getArgument(2), 0));

        mockMvc.perform(get(ROUTE)).andExpect(status().isOk());
        ArgumentCaptor<Boolean> defaultValue = ArgumentCaptor.forClass(Boolean.class);
        verify(individualRepository).getIndividualsOfClass(
                any(), any(), any(), defaultValue.capture(), any(), any());
        assertFalse(defaultValue.getValue());

        mockMvc.perform(get(ROUTE).param("includeObsoleteEntities", "true"))
                .andExpect(status().isOk());
        ArgumentCaptor<Boolean> values = ArgumentCaptor.forClass(Boolean.class);
        verify(individualRepository, org.mockito.Mockito.times(2)).getIndividualsOfClass(
                any(), any(), any(), values.capture(), any(), any());
        assertTrue(values.getAllValues().get(1));
    }
}
