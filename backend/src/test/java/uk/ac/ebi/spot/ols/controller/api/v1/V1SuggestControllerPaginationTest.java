package uk.ac.ebi.spot.ols.controller.api.v1;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V1SuggestControllerPaginationTest {

    @Test
    void returnsTheRequestedStartOffsetInTheLegacyResponse() throws Exception {
        OlsSearchClient searchClient = mock(OlsSearchClient.class);
        when(searchClient.suggestLabels("liv", null, 2, 2)).thenReturn(List.of());

        V1SuggestController controller = new V1SuggestController();
        ReflectionTestUtils.setField(controller, "searchClient", searchClient);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.suggest("LIV", null, 2, 2, response);

        assertEquals(2, JsonParser.parseString(response.getContentAsString())
                .getAsJsonObject().getAsJsonObject("response").get("start").getAsInt());
    }
}
