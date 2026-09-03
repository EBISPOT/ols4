package uk.ac.ebi.spot.ols.controller.api.v1;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class V1SelectControllerTest {

    private OlsSearchClient searchClient;
    private V1SelectController controller;

    @BeforeEach
    void setUp() {
        searchClient = mock(OlsSearchClient.class);
        controller = new V1SelectController();
        ReflectionTestUtils.setField(controller, "searchClient", searchClient);
        when(searchClient.searchRaw(any(OlsSearchQuery.class), anyInt(), anyInt()))
                .thenReturn(new OlsSearchClient.RawSearchResult(List.of(), 0, Map.of()));
    }

    @Test
    void buildsTheLegacySelectQueryOwnedByTheController() throws Exception {
        controller.select(
                "LIVER",
                List.of("EFO"),
                List.of("class", "property"),
                List.of("core"),
                List.of("label"),
                true,
                true,
                List.of("http://example.org/PARENT"),
                List.of("http://example.org/ANCESTOR"),
                5,
                2,
                "en",
                new MockHttpServletResponse());

        ArgumentCaptor<OlsSearchQuery> captor = ArgumentCaptor.forClass(OlsSearchQuery.class);
        verify(searchClient).searchRaw(captor.capture(),
                org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.eq(5));

        OlsSearchQuery query = captor.getValue();
        assertThat(query.getSearchText()).isEqualTo("LIVER");
        assertThat(filterValues(query, "ontologyId")).containsExactly(List.of("EFO"));
        assertThat(filterValues(query, "type"))
                .containsExactly(List.of("class", "property"));
        assertThat(filterValues(query, "subset")).containsExactly(List.of("core"));
        assertThat(filterValues(query, "isDefiningOntology"))
                .containsExactly(List.of("true"));
        assertThat(filterValues(query, "directAncestor"))
                .containsExactly(List.of("http://example.org/PARENT"));
        assertThat(filterValues(query, "hierarchicalAncestor"))
                .containsExactly(List.of("http://example.org/ANCESTOR"));
        assertThat(filterValues(query, "isObsolete")).containsExactly(List.of("true"));
    }

    @Test
    void rendersTheDefaultLocalizedLegacyProjection() throws Exception {
        when(searchClient.searchRaw(any(OlsSearchQuery.class), anyInt(), anyInt()))
                .thenReturn(new OlsSearchClient.RawSearchResult(
                        List.of(selectDocument()), 1, Map.of()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.select(
                "liver", null, null, null, null, false, false,
                null, null, 10, 0, "fr", response);

        JsonObject body = JsonParser.parseString(response.getContentAsString()).getAsJsonObject();
        JsonObject header = body.getAsJsonObject("responseHeader");
        JsonObject result = body.getAsJsonObject("response");
        JsonObject document = result.getAsJsonArray("docs").get(0).getAsJsonObject();

        assertThat(header.get("status").getAsInt()).isZero();
        assertThat(header.get("QTime").getAsInt()).isZero();
        assertThat(header.getAsJsonObject("params").get("q").getAsString()).isEqualTo("liver");
        assertThat(result.get("numFound").getAsLong()).isEqualTo(1);
        assertThat(result.get("start").getAsInt()).isZero();
        assertThat(document.keySet()).containsExactlyInAnyOrder(
                "id", "iri", "short_form", "obo_id", "label", "ontology_name",
                "ontology_prefix", "description", "type");
        assertThat(document.get("label").getAsString()).isEqualTo("Maladie du foie");
        assertThat(document.getAsJsonArray("description").get(0).getAsString())
                .isEqualTo("A disorder affecting hepatic tissue.");
        assertThat(document.get("type").getAsString()).isEqualTo("class");
        assertThat(body.getAsJsonObject("highlighting").keySet()).isEmpty();
    }

    @Test
    void projectsOnlyTheExplicitlyRequestedOptionalFields() throws Exception {
        when(searchClient.searchRaw(any(OlsSearchQuery.class), anyInt(), anyInt()))
                .thenReturn(new OlsSearchClient.RawSearchResult(
                        List.of(selectDocument()), 1, Map.of()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.select(
                "liver", null, null, null,
                List.of("label", "synonym", "is_defining_ontology"),
                false, false, null, null, 10, 0, "en", response);

        JsonObject document = JsonParser.parseString(response.getContentAsString())
                .getAsJsonObject().getAsJsonObject("response")
                .getAsJsonArray("docs").get(0).getAsJsonObject();
        assertThat(document.keySet())
                .containsExactlyInAnyOrder("label", "synonym", "is_defining_ontology");
        assertThat(document.get("label").getAsString()).isEqualTo("Liver disease");
        assertThat(document.getAsJsonArray("synonym").get(0).getAsString())
                .isEqualTo("Hepatic disorder");
        assertThat(document.get("is_defining_ontology").getAsBoolean()).isTrue();
    }

    @Test
    void rejectsInvalidOntologyIdsBeforeSearching() {
        assertThrows(IllegalArgumentException.class, () -> controller.select(
                "liver", List.of("efo/unsafe"), null, null, null, false, false,
                null, null, 10, 0, "en", new MockHttpServletResponse()));

        verifyNoInteractions(searchClient);
    }

    @SuppressWarnings("unchecked")
    private static List<Collection<String>> filterValues(
            OlsSearchQuery query, String fieldName) {
        List<Object> filters = (List<Object>) ReflectionTestUtils.getField(query, "filters");
        return filters.stream()
                .filter(filter -> fieldName.equals(ReflectionTestUtils.getField(filter, "field")))
                .map(filter -> (Collection<String>) ReflectionTestUtils.getField(filter, "values"))
                .toList();
    }

    static String selectDocument() {
        return """
                {
                  "id": "efo+class+http://example.org/EFO_0001",
                  "type": ["entity", "class"],
                  "ontologyId": "efo",
                  "ontologyPreferredPrefix": "EFO",
                  "iri": "http://example.org/EFO_0001",
                  "label": [
                    {"type": ["literal"], "lang": "en", "value": "Liver disease"},
                    {"type": ["literal"], "lang": "fr", "value": "Maladie du foie"}
                  ],
                  "definition": ["A disorder affecting hepatic tissue."],
                  "synonym": [
                    {"type": ["literal"], "lang": "en", "value": "Hepatic disorder"},
                    {"type": ["literal"], "lang": "fr", "value": "Trouble hepatique"}
                  ],
                  "shortForm": "EFO_0001",
                  "curie": "EFO:0001",
                  "isDefiningOntology": "true"
                }
                """;
    }
}
