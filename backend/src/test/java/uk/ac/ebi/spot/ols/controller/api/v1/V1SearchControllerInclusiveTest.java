package uk.ac.ebi.spot.ols.controller.api.v1;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V1SearchControllerInclusiveTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void inclusiveHierarchySearchMatchesTheRequestedParentOrItsDescendants(
            boolean allChildren) throws Exception {
        OlsSearchClient searchClient = mock(OlsSearchClient.class);
        when(searchClient.searchRaw(any(OlsSearchQuery.class), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new OlsSearchClient.RawSearchResult(List.of(), 0, Map.of()));
        V1SearchController controller = new V1SearchController();
        ReflectionTestUtils.setField(controller, "searchClient", searchClient);

        controller.search(
                "", null, null, null, null, null, false, null,
                false, false,
                allChildren ? null : List.of("http://example.org/PARENT"),
                allChildren ? List.of("http://example.org/PARENT") : null,
                true, false, 10, 0, "json", "en", new MockHttpServletResponse());

        ArgumentCaptor<OlsSearchQuery> captor = ArgumentCaptor.forClass(OlsSearchQuery.class);
        verify(searchClient).searchRaw(captor.capture(),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.eq(false));
        OlsSearchQuery query = captor.getValue();

        assertThat(query.buildCondition(Set.of()).toString())
                .contains("iri", "hierarchical_ancestors", "http://example.org/PARENT", " or ");
    }
}
