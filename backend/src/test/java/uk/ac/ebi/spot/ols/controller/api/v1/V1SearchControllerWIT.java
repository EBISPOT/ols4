package uk.ac.ebi.spot.ols.controller.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(V1SearchController.class)
@ContextConfiguration(classes = {
        V1SearchController.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V1SearchControllerWIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OlsSearchClient searchClient;

    @MockitoBean
    private V1OntologyRepository ontologyRepository;

    @BeforeEach
    void stubSearch() {
        Map<String, Map<String, Long>> facets = new LinkedHashMap<>();
        facets.put("ontologyId", new LinkedHashMap<>(Map.of("efo", 1L)));
        facets.put("type", new LinkedHashMap<>(Map.of("class", 1L)));
        when(searchClient.searchRaw(any(OlsSearchQuery.class), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new OlsSearchClient.RawSearchResult(
                        List.of(searchDocument()), 1, facets));
    }

    @Test
    void returnsTheDefaultLegacySearchContract() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "liver"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().encoding("UTF-8"))
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
                .andExpect(jsonPath("$.response.docs[0].exact_synonyms[0]")
                        .value("Hepatic disorder"))
                .andExpect(jsonPath("$.response.docs[0].related_synonyms[0]")
                        .value("Liver condition"))
                .andExpect(jsonPath("$.response.docs[0].narrow_synonyms[0]")
                        .value("Inherited liver disease"))
                .andExpect(jsonPath("$.response.docs[0].broad_synonyms[0]")
                        .value("Hepatic disease"))
                .andExpect(jsonPath("$.facet_counts.facet_fields.ontologyId[0]").value("efo"))
                .andExpect(jsonPath("$.facet_counts.facet_fields.ontologyId[1]").value("1"));

        verify(searchClient).searchRaw(any(OlsSearchQuery.class),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.eq(false));
        OlsSearchQuery query = capturedQuery();
        assertThat(query.getSearchText()).isEqualTo("liver");
        assertThat(filterValues(query, "isObsolete")).containsExactly("false");
    }

    @Test
    void bindsAndTranslatesRepeatedCommaAndSpaceSeparatedQueryFields() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "Liver disease")
                        .param("queryFields", "description,short_form", "obo_id label")
                        .param("exact", "true"))
                .andExpect(status().isOk());

        OlsSearchQuery query = capturedQuery();
        assertThat((List<String>) field(query, "searchFields"))
                .isEqualTo(List.of("definition", "shortForm", "oboId", "label"));
        assertThat((Boolean) field(query, "exactMatch")).isEqualTo(true);
    }

    @Test
    void bindsRepeatedOntologyTypeAndSlimValues() throws Exception {
        mockMvc.perform(get("/api/search")
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
        mockMvc.perform(get("/api/search")
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
    void bindsLocalLeafObsoleteAndGroupOptions() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "liver")
                        .param("local", "true")
                        .param("isLeaf", "true")
                        .param("obsoletes", "true")
                        .param("groupField", "iri"))
                .andExpect(status().isOk());

        OlsSearchQuery query = capturedQuery();
        assertThat(filterValues(query, "isDefiningOntology")).containsExactly("true");
        assertThat(filterValues(query, "hasChildren")).containsExactly("false");
        assertThat(filterValues(query, "isObsolete")).containsExactly("true");
        verify(searchClient).searchRaw(any(OlsSearchQuery.class),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void bindsEncodedHierarchyIrisAsInclusiveAlternatives() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "liver")
                        .param("childrenOf", "http://example.org/PARENT")
                        .param("allChildrenOf", "http://example.org/ANCESTOR")
                        .param("inclusive", "true"))
                .andExpect(status().isOk());

        OlsSearchQuery query = capturedQuery();
        assertThat(anyFilterGroups(query)).containsExactly(
                Map.of(
                        "iri", List.of("http://example.org/PARENT"),
                        "hierarchicalAncestor", List.of("http://example.org/PARENT")),
                Map.of(
                        "iri", List.of("http://example.org/ANCESTOR"),
                        "hierarchicalAncestor", List.of("http://example.org/ANCESTOR")));
    }

    @Test
    void bindsEncodedHierarchyIrisAsExclusiveFilters() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "liver")
                        .param("childrenOf", "http://example.org/PARENT")
                        .param("allChildrenOf", "http://example.org/ANCESTOR")
                        .param("inclusive", "false"))
                .andExpect(status().isOk());

        OlsSearchQuery query = capturedQuery();
        assertThat(filterValuesList(query, "hierarchicalAncestor"))
                .containsExactly(
                        List.of("http://example.org/PARENT"),
                        List.of("http://example.org/ANCESTOR"));
        assertThat(anyFilterGroups(query)).isEmpty();
    }

    @Test
    void projectsRequestedFieldsAnnotationsAndRequestedLanguage() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "liver")
                        .param("fieldList",
                                "label,synonym,subset,ontology_iri,is_defining_ontology,comment_annotation")
                        .param("format", "json")
                        .param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.docs[0].label").value("Maladie du foie"))
                .andExpect(jsonPath("$.response.docs[0].synonym[0]").value("Trouble hepatique"))
                .andExpect(jsonPath("$.response.docs[0].subset[0]").value("core"))
                .andExpect(jsonPath("$.response.docs[0].ontology_iri")
                        .value("http://www.ebi.ac.uk/efo"))
                .andExpect(jsonPath("$.response.docs[0].is_defining_ontology").value(true))
                .andExpect(jsonPath("$.response.docs[0].comment_annotation[0]")
                        .value("Commentaire francais"))
                .andExpect(jsonPath("$.response.docs[0].iri").doesNotExist());
    }

    @Test
    void forwardsPaginationBoundaryValuesAndReturnsTheRequestedStart() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "liver")
                        .param("start", "0")
                        .param("rows", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.start").value(0));

        verify(searchClient).searchRaw(any(OlsSearchQuery.class),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void ignoresUnsupportedReservedAndDynamicStyleParametersForCompatibility() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "liver")
                        .param("page", "7")
                        .param("size", "4")
                        .param("facetFields", "type")
                        .param("http://example.org/category", "clinical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.numFound").value(1));

        OlsSearchQuery query = capturedQuery();
        assertThat(field(query, "filters").toString())
                .doesNotContain("page", "size", "facetFields", "category");
    }

    @ParameterizedTest
    @ValueSource(strings = {"exact", "obsoletes", "local", "inclusive", "isLeaf"})
    void rejectsMalformedBooleanValuesWithStableErrorFields(String parameter) throws Exception {
        mockMvc.perform(get("/api/search")
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
        mockMvc.perform(get("/api/search")
                        .param("q", "liver")
                        .param(parameter, "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsMalformedOntologyIdsWithStableErrorFields() throws Exception {
        mockMvc.perform(get("/api/search")
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
        mockMvc.perform(get("/api/search"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void returnsMethodNotAllowedForUnsupportedMethod() throws Exception {
        mockMvc.perform(post("/api/search").param("q", "liver"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private OlsSearchQuery capturedQuery() {
        ArgumentCaptor<OlsSearchQuery> captor = ArgumentCaptor.forClass(OlsSearchQuery.class);
        verify(searchClient).searchRaw(captor.capture(), anyInt(), anyInt(), anyBoolean());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(OlsSearchQuery query, String name) {
        return (T) ReflectionTestUtils.getField(query, name);
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

    private static String searchDocument() {
        return """
                {
                  "id": "efo+class+http://example.org/EFO_0001",
                  "type": ["entity", "class"],
                  "ontologyId": "efo",
                  "ontologyPreferredPrefix": "EFO",
                  "ontologyIri": ["http://www.ebi.ac.uk/efo"],
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
                  "isDefiningOntology": "true",
                  "http://www.geneontology.org/formats/oboInOwl#hasExactSynonym": ["Hepatic disorder"],
                  "http://www.geneontology.org/formats/oboInOwl#hasRelatedSynonym": ["Liver condition"],
                  "http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym": ["Inherited liver disease"],
                  "http://www.geneontology.org/formats/oboInOwl#hasBroadSynonym": ["Hepatic disease"],
                  "http://www.geneontology.org/formats/oboInOwl#inSubset": ["core"],
                  "http://www.w3.org/2000/01/rdf-schema#comment": [
                    {"type": ["literal"], "lang": "en", "value": "English comment"},
                    {"type": ["literal"], "lang": "fr", "value": "Commentaire francais"}
                  ],
                  "definitionProperty": [],
                  "synonymProperty": ["http://www.geneontology.org/formats/oboInOwl#hasExactSynonym"],
                  "hierarchicalProperty": [],
                  "linkedEntities": {}
                }
                """;
    }
}
