package uk.ac.ebi.spot.ols.controller.api.v1;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V1SelectControllerPaginationTest {

    @Test
    void returnsTheRequestedStartOffsetInTheLegacyResponse() throws Exception {
        OlsSearchClient searchClient = mock(OlsSearchClient.class);
        when(searchClient.searchRaw(any(OlsSearchQuery.class), anyInt(), anyInt()))
                .thenReturn(new OlsSearchClient.RawSearchResult(List.of(), 0, Map.of()));

        V1SelectController controller = new V1SelectController();
        ReflectionTestUtils.setField(controller, "searchClient", searchClient);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.select(
                "liver", null, null, null, null, false, false,
                null, null, 2, 2, "en", response);

        assertEquals(2, JsonParser.parseString(response.getContentAsString())
                .getAsJsonObject().getAsJsonObject("response").get("start").getAsInt());
    }
}
