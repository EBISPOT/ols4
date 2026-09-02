package uk.ac.ebi.spot.ols.controller.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class V1SearchControllerTest {

    private OlsSearchClient searchClient;
    private V1SearchController controller;

    @BeforeEach
    void setUp() {
        searchClient = mock(OlsSearchClient.class);
        controller = new V1SearchController();
        ReflectionTestUtils.setField(controller, "searchClient", searchClient);
        when(searchClient.searchRaw(any(OlsSearchQuery.class), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new OlsSearchClient.RawSearchResult(List.of(), 0, Map.of()));
    }

    @Test
    void buildsTheLegacySearchQueryOwnedByTheController() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.search(
                "LIVER",
                List.of("EFO"),
                List.of("class"),
                List.of("core"),
                List.of("label"),
                List.of("description, short_form", "obo_id"),
                true,
                "iri",
                true,
                true,
                List.of("http://example.org/PARENT"),
                List.of("http://example.org/ANCESTOR"),
                false,
                true,
                5,
                2,
                "json",
                "en",
                response);

        ArgumentCaptor<OlsSearchQuery> captor = ArgumentCaptor.forClass(OlsSearchQuery.class);
        verify(searchClient).searchRaw(captor.capture(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(5), org.mockito.ArgumentMatchers.eq(true));

        OlsSearchQuery query = captor.getValue();
        assertThat(query.getSearchText()).isEqualTo("LIVER");
        assertThat(ReflectionTestUtils.getField(query, "exactMatch")).isEqualTo(true);
        assertThat((List<String>) field(query, "searchFields"))
                .isEqualTo(List.of("definition", "shortForm", "oboId"));
        assertThat(filterValues(query, "ontologyId")).containsExactly(List.of("EFO"));
        assertThat(filterValues(query, "subset")).containsExactly(List.of("core"));
        assertThat(filterValues(query, "type")).containsExactly(List.of("class"));
        assertThat(filterValues(query, "isDefiningOntology")).containsExactly(List.of("true"));
        assertThat(filterValues(query, "hasChildren")).containsExactly(List.of("false"));
        assertThat(filterValues(query, "hierarchicalAncestor"))
                .containsExactly(
                        List.of("http://example.org/PARENT"),
                        List.of("http://example.org/ANCESTOR"));
        assertThat(filterValues(query, "isObsolete")).containsExactly(List.of("true"));
        assertThat((List<String>) field(query, "facetFields")).isEqualTo(List.of(
                "ontologyId",
                "ontologyIri",
                "ontologyPreferredPrefix",
                "type",
                "isDefiningOntology",
                "isObsolete"));
    }

    @Test
    void includesRequestedHierarchyParentsThroughAlternativeFilters() throws Exception {
        controller.search(
                "liver", null, null, null, null, null, false, null,
                false, false,
                List.of("http://example.org/PARENT"),
                List.of("http://example.org/ANCESTOR"),
                true, false, 10, 0, "json", "en",
                new MockHttpServletResponse());

        ArgumentCaptor<OlsSearchQuery> captor = ArgumentCaptor.forClass(OlsSearchQuery.class);
        verify(searchClient).searchRaw(captor.capture(), anyInt(), anyInt(), anyBoolean());

        assertThat(anyFilterGroups(captor.getValue())).containsExactly(
                Map.of(
                        "iri", List.of("http://example.org/PARENT"),
                        "hierarchicalAncestor", List.of("http://example.org/PARENT")),
                Map.of(
                        "iri", List.of("http://example.org/ANCESTOR"),
                        "hierarchicalAncestor", List.of("http://example.org/ANCESTOR")));
    }

    @ParameterizedTest
    @CsvSource({
            "NULL, false",
            "'   ', false",
            "false, false",
            "FALSE, false",
            "true, true",
            "iri, true"
    })
    void preservesLegacyGroupFieldSemantics(String rawGroupField, boolean expectedGrouping)
            throws Exception {
        String groupField = "NULL".equals(rawGroupField) ? null : rawGroupField;

        controller.search(
                "liver", null, null, null, null, null, false, groupField,
                false, false, null, null, false, false, 10, 0, "json", "en",
                new MockHttpServletResponse());

        verify(searchClient).searchRaw(any(OlsSearchQuery.class),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.eq(expectedGrouping));
    }

    @Test
    void rejectsInvalidOntologyIdsBeforeSearching() {
        assertThrows(IllegalArgumentException.class, () -> controller.search(
                "liver", List.of("efo/unsafe"), null, null, null, null, false, null,
                false, false, null, null, false, false, 10, 0, "json", "en",
                new MockHttpServletResponse()));

        verifyNoInteractions(searchClient);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(OlsSearchQuery query, String name) {
        return (T) ReflectionTestUtils.getField(query, name);
    }

    @SuppressWarnings("unchecked")
    private static List<Collection<String>> filterValues(OlsSearchQuery query, String fieldName) {
        List<Object> filters = (List<Object>) ReflectionTestUtils.getField(query, "filters");
        return filters.stream()
                .filter(filter -> fieldName.equals(ReflectionTestUtils.getField(filter, "field")))
                .map(filter -> (Collection<String>) ReflectionTestUtils.getField(filter, "values"))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, List<String>>> anyFilterGroups(OlsSearchQuery query) {
        List<List<Object>> groups = field(query, "anyFilterGroups");
        return groups.stream()
                .map(group -> group.stream().collect(java.util.stream.Collectors.toMap(
                        filter -> (String) ReflectionTestUtils.getField(filter, "field"),
                        filter -> List.copyOf((Collection<String>)
                                ReflectionTestUtils.getField(filter, "values")))))
                .toList();
    }
}
