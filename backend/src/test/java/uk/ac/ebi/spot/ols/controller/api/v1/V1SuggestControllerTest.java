package uk.ac.ebi.spot.ols.controller.api.v1;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class V1SuggestControllerTest {

    private OlsSearchClient searchClient;
    private V1SuggestController controller;

    @BeforeEach
    void setUp() {
        searchClient = mock(OlsSearchClient.class);
        controller = new V1SuggestController();
        ReflectionTestUtils.setField(controller, "searchClient", searchClient);
    }

    @Test
    void lowercasesAndForwardsSuggestionArgumentsAndRendersTheLegacyEnvelope() throws Exception {
        List<String> labels = List.of("Liver disease", "Liver ailment");
        when(searchClient.suggestLabels("liv", List.of("efo", "duo"), 2, 2))
                .thenReturn(labels);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.suggest("LIV", List.of("efo", "duo"), 2, 2, response);

        verify(searchClient).suggestLabels("liv", List.of("efo", "duo"), 2, 2);
        JsonObject body = JsonParser.parseString(response.getContentAsString()).getAsJsonObject();
        assertEquals(0, body.getAsJsonObject("responseHeader").get("status").getAsInt());
        assertEquals(2, body.getAsJsonObject("response").get("numFound").getAsInt());
        assertEquals(2, body.getAsJsonObject("response").get("start").getAsInt());
        assertEquals("Liver disease",
                body.getAsJsonObject("response").getAsJsonArray("docs")
                        .get(0).getAsJsonObject().get("autosuggest").getAsString());
        assertEquals("Liver ailment",
                body.getAsJsonObject("response").getAsJsonArray("docs")
                        .get(1).getAsJsonObject().get("autosuggest").getAsString());
    }

    @Test
    void rejectsInvalidOntologyIdsBeforeCallingTheSearchClient() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(IllegalArgumentException.class,
                () -> controller.suggest("liv", List.of("efo/unsafe"), 0, 10, response));

        verifyNoInteractions(searchClient);
    }
}
