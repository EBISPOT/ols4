package uk.ac.ebi.spot.ols.controller.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.ols.config.WebConfig;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery;
import uk.ac.ebi.spot.ols.repository.v1.V1OntologyRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(V1SelectController.class)
@ContextConfiguration(classes = {
        V1SelectController.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V1SelectControllerWIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OlsSearchClient searchClient;

    @MockitoBean
    private V1OntologyRepository ontologyRepository;

    @BeforeEach
    void stubSearch() {
        when(searchClient.searchRaw(any(OlsSearchQuery.class), anyInt(), anyInt()))
                .thenReturn(new OlsSearchClient.RawSearchResult(
                        List.of(V1SelectControllerTest.selectDocument()), 1, Map.of()));
    }

    @Test
    void returnsTheDefaultLegacySelectContract() throws Exception {
        mockMvc.perform(get("/api/select").param("q", "liver"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().encoding("UTF-8"))
                .andExpect(jsonPath("$.responseHeader.params.q").value("liver"))
                .andExpect(jsonPath("$.responseHeader.status").value(0))
                .andExpect(jsonPath("$.responseHeader.QTime").value(0))
                .andExpect(jsonPath("$.response.numFound").value(1))
                .andExpect(jsonPath("$.response.start").value(0))
                .andExpect(jsonPath("$.response.docs[0].id")
                        .value("efo+class+http://example.org/EFO_0001"))
                .andExpect(jsonPath("$.response.docs[0].iri")
                        .value("http://example.org/EFO_0001"))
                .andExpect(jsonPath("$.response.docs[0].label").value("Liver disease"))
                .andExpect(jsonPath("$.response.docs[0].description[0]")
                        .value("A disorder affecting hepatic tissue."))
                .andExpect(jsonPath("$.response.docs[0].short_form").value("EFO_0001"))
                .andExpect(jsonPath("$.response.docs[0].obo_id").value("EFO:0001"))
                .andExpect(jsonPath("$.response.docs[0].ontology_name").value("efo"))
                .andExpect(jsonPath("$.response.docs[0].ontology_prefix").value("EFO"))
                .andExpect(jsonPath("$.response.docs[0].type").value("class"))
                .andExpect(jsonPath("$.highlighting").isEmpty());

        verify(searchClient).searchRaw(any(OlsSearchQuery.class),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(10));
        OlsSearchQuery query = capturedQuery();
        assertThat(query.getSearchText()).isEqualTo("liver");
        assertThat(filterValues(query, "isObsolete")).containsExactly("false");
        assertThat(filterValuesList(query, "isDefiningOntology")).isEmpty();
    }

    @Test
    void bindsRepeatedOntologyTypeAndSlimValues() throws Exception {
        mockMvc.perform(get("/api/select")
                        .param("q", "liver")
                        .param("ontology", "efo", "duo")
                        .param("type", "class", "property")
                        .param("slim", "core", "slim"))
                .andExpect(status().isOk());

        OlsSearchQuery query = capturedQuery();
        assertThat(filterValues(query, "ontologyId")).containsExactly("efo", "duo");
        assertThat(filterValues(query, "type")).containsExactly("class", "property");
        assertThat(filterValues(query, "subset")).containsExactly("core", "slim");
    }

    @Test
    void bindsCommaSeparatedOntologyTypeAndSlimValues() throws Exception {
        mockMvc.perform(get("/api/select")
                        .param("q", "liver")
                        .param("ontology", "efo,duo")
                        .param("type", "class,property")
                        .param("slim", "core,slim"))
                .andExpect(status().isOk());

        OlsSearchQuery query = capturedQuery();
        assertThat(filterValues(query, "ontologyId")).containsExactly("efo", "duo");
        assertThat(filterValues(query, "type")).containsExactly("class", "property");
        assertThat(filterValues(query, "subset")).containsExactly("core", "slim");
    }

    @Test
    void bindsTrueLocalAndObsoleteOptions() throws Exception {
        mockMvc.perform(get("/api/select")
                        .param("q", "liver")
                        .param("local", "true")
                        .param("obsoletes", "true"))
                .andExpect(status().isOk());

        OlsSearchQuery query = capturedQuery();
        assertThat(filterValues(query, "isDefiningOntology")).containsExactly("true");
        assertThat(filterValues(query, "isObsolete")).containsExactly("true");
    }

    @Test
    void bindsExplicitFalseLocalAndObsoleteOptions() throws Exception {
        mockMvc.perform(get("/api/select")
                        .param("q", "liver")
                        .param("local", "false")
                        .param("obsoletes", "false"))
                .andExpect(status().isOk());

        OlsSearchQuery query = capturedQuery();
        assertThat(filterValuesList(query, "isDefiningOntology")).isEmpty();
        assertThat(filterValues(query, "isObsolete")).containsExactly("false");
    }

    @Test
    void bindsRepeatedEncodedHierarchyIris() throws Exception {
        mockMvc.perform(get("/api/select")
                        .param("q", "liver")
                        .param("childrenOf", "http://example.org/PARENT", "urn:test:parent")
                        .param("allChildrenOf", "http://example.org/ANCESTOR", "urn:test:ancestor"))
                .andExpect(status().isOk());

        OlsSearchQuery query = capturedQuery();
        assertThat(filterValues(query, "directAncestor"))
                .containsExactly("http://example.org/PARENT", "urn:test:parent");
        assertThat(filterValues(query, "hierarchicalAncestor"))
                .containsExactly("http://example.org/ANCESTOR", "urn:test:ancestor");
    }

    @Test
    void bindsCommaSeparatedHierarchyIris() throws Exception {
        mockMvc.perform(get("/api/select")
                        .param("q", "liver")
                        .param("childrenOf", "http://example.org/PARENT,urn:test:parent")
                        .param("allChildrenOf", "http://example.org/ANCESTOR,urn:test:ancestor"))
                .andExpect(status().isOk());

        OlsSearchQuery query = capturedQuery();
        assertThat(filterValues(query, "directAncestor"))
                .containsExactly("http://example.org/PARENT", "urn:test:parent");
        assertThat(filterValues(query, "hierarchicalAncestor"))
                .containsExactly("http://example.org/ANCESTOR", "urn:test:ancestor");
    }

    @Test
    void projectsCommaSeparatedFieldsInTheRequestedLanguage() throws Exception {
        mockMvc.perform(get("/api/select")
                        .param("q", "liver")
                        .param("fieldList", "label,synonym,is_defining_ontology")
                        .param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.docs[0].label").value("Maladie du foie"))
                .andExpect(jsonPath("$.response.docs[0].synonym[0]")
                        .value("Trouble hepatique"))
                .andExpect(jsonPath("$.response.docs[0].is_defining_ontology").value(true))
                .andExpect(jsonPath("$.response.docs[0].iri").doesNotExist());
    }

    @Test
    void projectsRepeatedFieldListValues() throws Exception {
        mockMvc.perform(get("/api/select")
                        .param("q", "liver")
                        .param("fieldList", "label", "synonym", "is_defining_ontology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.docs[0].label").value("Liver disease"))
                .andExpect(jsonPath("$.response.docs[0].synonym[0]")
                        .value("Hepatic disorder"))
                .andExpect(jsonPath("$.response.docs[0].is_defining_ontology").value(true))
                .andExpect(jsonPath("$.response.docs[0].iri").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "-1, 1",
            "1, -1"
    })
    void forwardsPaginationBoundaryValuesAndReturnsTheRequestedStart(int start, int rows)
            throws Exception {
        mockMvc.perform(get("/api/select")
                        .param("q", "liver")
                        .param("start", Integer.toString(start))
                        .param("rows", Integer.toString(rows)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.start").value(start));

        verify(searchClient).searchRaw(any(OlsSearchQuery.class),
                org.mockito.ArgumentMatchers.eq(start), org.mockito.ArgumentMatchers.eq(rows));
    }

    @Test
    void ignoresUnsupportedReservedAndDynamicStyleParametersForCompatibility() throws Exception {
        mockMvc.perform(get("/api/select")
                        .param("q", "liver")
                        .param("page", "7")
                        .param("size", "4")
                        .param("sort", "label,asc")
                        .param("facetFields", "type")
                        .param("http://example.org/category", "clinical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.numFound").value(1));

        assertThat(ReflectionTestUtils.getField(capturedQuery(), "filters").toString())
                .doesNotContain("page", "size", "sort", "facetFields", "category");
    }

    @ParameterizedTest
    @ValueSource(strings = {"obsoletes", "local"})
    void rejectsMalformedBooleanValuesWithStableErrorFields(String parameter) throws Exception {
        mockMvc.perform(get("/api/select")
                        .param("q", "liver")
                        .param(parameter, "not-a-boolean"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rows", "start"})
    void rejectsMalformedPaginationValuesWithStableErrorFields(String parameter) throws Exception {
        mockMvc.perform(get("/api/select")
                        .param("q", "liver")
                        .param(parameter, "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsMalformedOntologyIdsWithStableErrorFields() throws Exception {
        mockMvc.perform(get("/api/select")
                        .param("q", "liver")
                        .param("ontology", "efo/unsafe"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(searchClient);
    }

    @Test
    void rejectsMissingRequiredQueryWithStableErrorFields() throws Exception {
        mockMvc.perform(get("/api/select"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void returnsMethodNotAllowedForUnsupportedMethod() throws Exception {
        mockMvc.perform(post("/api/select").param("q", "liver"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private OlsSearchQuery capturedQuery() {
        ArgumentCaptor<OlsSearchQuery> captor = ArgumentCaptor.forClass(OlsSearchQuery.class);
        verify(searchClient).searchRaw(captor.capture(), anyInt(), anyInt());
        return captor.getValue();
    }

    private static Collection<String> filterValues(OlsSearchQuery query, String fieldName) {
        return filterValuesList(query, fieldName).get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<List<String>> filterValuesList(
            OlsSearchQuery query, String fieldName) {
        List<Object> filters = (List<Object>) ReflectionTestUtils.getField(query, "filters");
        return filters.stream()
                .filter(filter -> fieldName.equals(ReflectionTestUtils.getField(filter, "field")))
                .map(filter -> (Collection<String>) ReflectionTestUtils.getField(filter, "values"))
                .map(List::copyOf)
                .toList();
    }
}
