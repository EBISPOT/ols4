package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uk.ac.ebi.spot.ols.config.WebConfig;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.repository.ClassRepository;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(V2ClassController.class)
@ContextConfiguration(classes = {
        V2ClassController.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V2ClassControllerWIT {

    private static final String CLASS_IRI = "http://example.org/EFO_0001";
    private static final String CHILD_IRI = "http://example.org/EFO_1001";
    private static final String INDIVIDUAL_IRI = "http://example.org/EFO_I100";
    private static final URI CLASS_URI = URI.create(
            "/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_0001");
    private static final URI RELATED_FROM_URI = URI.create(
            "/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_0001/relatedFrom");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClassRepository classRepository;

    @BeforeEach
    void stubResponses() throws Exception {
        when(classRepository.find(
                any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenReturn(classPage());
        when(classRepository.findByOntologyId(
                any(), any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenReturn(classPage());
        when(classRepository.getByOntologyIdAndIri(any(), any(), any(), any()))
                .thenReturn(classJson());
        when(classRepository.getRelatedFrom(any(), any(), any(), any(), any()))
                .thenReturn(classPage());
        when(classRepository.getChildrenByOntologyId(
                any(), any(), any(), anyBoolean(), any(), any(), any()))
                .thenReturn(hierarchyPage());
        when(classRepository.getAncestorsByOntologyId(
                any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(hierarchyPage());
        when(classRepository.getDescendantsByOntologyId(
                any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(hierarchyPage());
        when(classRepository.getHierarchicalDescendantsByOntologyId(
                any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(hierarchyPage());
        when(classRepository.getHierarchicalChildrenByOntologyId(
                any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(hierarchyPage());
        when(classRepository.getHierarchicalAncestorsByOntologyId(
                any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(hierarchyPage());
        when(classRepository.getIndividualAncestorsByOntologyId(
                any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(hierarchyPage());
    }

    @Test
    void returnsDefaultGlobalClassListContract() throws Exception {
        mockMvc.perform(get("/api/v2/classes"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].ontologyId").value("efo"))
                .andExpect(jsonPath("$.elements[0].iri").value(CLASS_IRI))
                .andExpect(jsonPath("$.elements[0].label").value("Liver disease"));

        ListCall call = captureGlobalListCall();
        assertListDefaults(call);
        assertEquals(Map.of("isObsolete", List.of("false")), call.properties());
    }

    @Test
    void bindsEveryGlobalListParameter() throws Exception {
        mockMvc.perform(get("/api/v2/classes")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc")
                        .param("search", "liver")
                        .param("searchFields", "label definition")
                        .param("boostFields", "label^10")
                        .param("exactMatch", "true")
                        .param("includeObsoleteEntities", "true")
                        .param("subset", "disease", "core")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        ListCall call = captureGlobalListCall();
        assertExplicitList(call);
        assertEquals(Map.of("subset", List.of("disease", "core")), call.properties());
    }

    @Test
    void forwardsRepeatedGlobalDynamicPropertyValues() throws Exception {
        mockMvc.perform(get("/api/v2/classes").param("subset", "disease", "core"))
                .andExpect(status().isOk());

        assertEquals(List.of("disease", "core"), captureGlobalListCall().properties().get("subset"));
    }

    @Test
    void forwardsCommaSeparatedAndUriNamedGlobalDynamicProperties() throws Exception {
        String property = "http://example.org/category";
        mockMvc.perform(get("/api/v2/classes")
                        .param("subset", "disease,core")
                        .param(property, "clinical"))
                .andExpect(status().isOk());

        Map<String, Collection<String>> properties = captureGlobalListCall().properties();
        assertEquals(List.of("disease,core"), properties.get("subset"));
        assertEquals(List.of("clinical"), properties.get(property));
    }

    @Test
    void globalReservedParametersDoNotBecomeDynamicProperties() throws Exception {
        performListWithEveryReservedParameter("/api/v2/classes");

        assertEquals(Map.of(), captureGlobalListCall().properties());
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20, 0, 20",
            "0, 0, 0, 20",
            "0, -1, 0, 20",
            "0, 1001, 0, 1000"
    })
    void normalizesGlobalPaginationBoundaries(
            int requestedPage, int requestedSize, int expectedPage, int expectedSize) throws Exception {
        mockMvc.perform(get("/api/v2/classes")
                        .param("page", Integer.toString(requestedPage))
                        .param("size", Integer.toString(requestedSize)))
                .andExpect(status().isOk());

        assertPage(captureGlobalListCall().pageable(), expectedPage, expectedSize);
    }

    @ParameterizedTest
    @CsvSource({"page, not-a-number", "size, not-a-number"})
    void usesGlobalPaginationDefaultsForMalformedNumericValues(String parameter, String value)
            throws Exception {
        mockMvc.perform(get("/api/v2/classes").param(parameter, value))
                .andExpect(status().isOk());

        assertPage(captureGlobalListCall().pageable(), 0, 20);
    }

    @ParameterizedTest
    @CsvSource({
            "exactMatch, not-a-boolean",
            "includeObsoleteEntities, not-a-boolean",
            "resolveReferences, not-a-boolean",
            "manchesterSyntax, not-a-boolean"
    })
    void rejectsMalformedGlobalTypedParameters(String parameter, String value) throws Exception {
        assertStableBadRequest(get("/api/v2/classes").param(parameter, value));
    }

    @ParameterizedTest
    @CsvSource({"notARealField,asc", "iri,sideways"})
    void returnsStableErrorForUnsupportedGlobalSort(String sort) throws Exception {
        when(classRepository.find(
                any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: " + sort));

        assertUnsupportedSort(get("/api/v2/classes").param("sort", sort), sort);
    }

    @Test
    void returnsDefaultOntologyClassListContract() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/classes"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].ontologyId").value("efo"))
                .andExpect(jsonPath("$.elements[0].iri").value(CLASS_IRI));

        OntologyListCall call = captureOntologyListCall();
        assertEquals("efo", call.ontologyId());
        assertListDefaults(call.listCall());
        assertEquals(Map.of("isObsolete", List.of("false")), call.listCall().properties());
    }

    @Test
    void bindsEveryOntologyListParameter() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/classes")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc")
                        .param("search", "liver")
                        .param("searchFields", "label definition")
                        .param("boostFields", "label^10")
                        .param("exactMatch", "true")
                        .param("includeObsoleteEntities", "true")
                        .param("subset", "disease", "core")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        OntologyListCall call = captureOntologyListCall();
        assertEquals("efo", call.ontologyId());
        assertExplicitList(call.listCall());
        assertEquals(Map.of("subset", List.of("disease", "core")), call.listCall().properties());
    }

    @Test
    void forwardsOntologyRepeatedCommaSeparatedAndUriNamedDynamicProperties() throws Exception {
        String property = "http://example.org/category";
        mockMvc.perform(get("/api/v2/ontologies/efo/classes")
                        .param("subset", "disease", "core")
                        .param("domain", "biology,information")
                        .param(property, "clinical"))
                .andExpect(status().isOk());

        Map<String, Collection<String>> properties = captureOntologyListCall().listCall().properties();
        assertEquals(List.of("disease", "core"), properties.get("subset"));
        assertEquals(List.of("biology,information"), properties.get("domain"));
        assertEquals(List.of("clinical"), properties.get(property));
    }

    @Test
    void ontologyReservedParametersDoNotBecomeDynamicProperties() throws Exception {
        performListWithEveryReservedParameter("/api/v2/ontologies/efo/classes");

        assertEquals(Map.of(), captureOntologyListCall().listCall().properties());
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20, 0, 20",
            "0, 0, 0, 20",
            "0, -1, 0, 20",
            "0, 1001, 0, 1000"
    })
    void normalizesOntologyPaginationBoundaries(
            int requestedPage, int requestedSize, int expectedPage, int expectedSize) throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/classes")
                        .param("page", Integer.toString(requestedPage))
                        .param("size", Integer.toString(requestedSize)))
                .andExpect(status().isOk());

        assertPage(captureOntologyListCall().listCall().pageable(), expectedPage, expectedSize);
    }

    @ParameterizedTest
    @CsvSource({"page, not-a-number", "size, not-a-number"})
    void usesOntologyPaginationDefaultsForMalformedNumericValues(String parameter, String value)
            throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/classes").param(parameter, value))
                .andExpect(status().isOk());

        assertPage(captureOntologyListCall().listCall().pageable(), 0, 20);
    }

    @ParameterizedTest
    @CsvSource({
            "exactMatch, not-a-boolean",
            "includeObsoleteEntities, not-a-boolean",
            "resolveReferences, not-a-boolean",
            "manchesterSyntax, not-a-boolean"
    })
    void rejectsMalformedOntologyTypedParameters(String parameter, String value) throws Exception {
        assertStableBadRequest(get("/api/v2/ontologies/efo/classes").param(parameter, value));
    }

    @ParameterizedTest
    @CsvSource({"notARealField,asc", "iri,sideways"})
    void returnsStableErrorForUnsupportedOntologySort(String sort) throws Exception {
        when(classRepository.findByOntologyId(
                any(), any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: " + sort));

        assertUnsupportedSort(
                get("/api/v2/ontologies/efo/classes").param("sort", sort), sort);
    }

    @Test
    void returnsStableBadRequestForInvalidOntologyListIdentifier() throws Exception {
        when(classRepository.findByOntologyId(
                any(), any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenThrow(new IllegalArgumentException("Invalid ontology id"));

        assertStableBadRequest(get("/api/v2/ontologies/efo%252Funsafe/classes"));
    }

    @Test
    void returnsClassByDoubleEncodedIriWithDefaults() throws Exception {
        mockMvc.perform(get(CLASS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ontologyId").value("efo"))
                .andExpect(jsonPath("$.iri").value(CLASS_IRI))
                .andExpect(jsonPath("$.label").value("Liver disease"));

        ClassCall call = captureClassCall();
        assertEquals("efo", call.ontologyId());
        assertEquals(CLASS_IRI, call.iri());
        assertEquals("en", call.lang());
        assertDefaultOptions(call.outputOptions());
    }

    @Test
    void forwardsSingleClassLanguageAndTransformOptions() throws Exception {
        mockMvc.perform(get(CLASS_URI)
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        ClassCall call = captureClassCall();
        assertEquals("fr", call.lang());
        assertTrue(call.outputOptions().resolveReferences);
        assertTrue(call.outputOptions().manchesterSyntax);
    }

    @Test
    void returnsStableNotFoundContractForMissingClass() throws Exception {
        when(classRepository.getByOntologyIdAndIri(any(), any(), any(), any())).thenReturn(null);

        mockMvc.perform(get(CLASS_URI))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("The requested resource was not found."));
    }

    @Test
    void returnsStableBadRequestForInvalidSingleClassIdentifier() throws Exception {
        when(classRepository.getByOntologyIdAndIri(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid ontology id"));

        assertStableBadRequest(get("/api/v2/ontologies/efo%252Funsafe/classes/anything"));
    }

    @ParameterizedTest
    @CsvSource({"resolveReferences, not-a-boolean", "manchesterSyntax, not-a-boolean"})
    void rejectsMalformedSingleClassTransformOptions(String parameter, String value)
            throws Exception {
        assertStableBadRequest(get(CLASS_URI).param(parameter, value));
    }

    @Test
    void returnsDefaultRelatedFromContract() throws Exception {
        mockMvc.perform(get(RELATED_FROM_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri").value(CLASS_IRI));

        RelatedCall call = captureRelatedCall();
        assertEquals("efo", call.ontologyId());
        assertEquals(CLASS_IRI, call.iri());
        assertPage(call.pageable(), 0, 20);
        assertEquals("en", call.lang());
        assertDefaultOptions(call.outputOptions());
    }

    @Test
    void bindsEveryRelatedFromParameter() throws Exception {
        mockMvc.perform(get(RELATED_FROM_URI)
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        RelatedCall call = captureRelatedCall();
        assertPage(call.pageable(), 1, 3);
        assertEquals("iri: DESC", call.pageable().getSort().toString());
        assertEquals("fr", call.lang());
        assertTrue(call.outputOptions().resolveReferences);
        assertTrue(call.outputOptions().manchesterSyntax);
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20, 0, 20",
            "0, 0, 0, 20",
            "0, -1, 0, 20",
            "0, 1001, 0, 1000"
    })
    void normalizesRelatedFromPaginationBoundaries(
            int requestedPage, int requestedSize, int expectedPage, int expectedSize) throws Exception {
        mockMvc.perform(get(RELATED_FROM_URI)
                        .param("page", Integer.toString(requestedPage))
                        .param("size", Integer.toString(requestedSize)))
                .andExpect(status().isOk());

        assertPage(captureRelatedCall().pageable(), expectedPage, expectedSize);
    }

    @ParameterizedTest
    @CsvSource({"page, not-a-number", "size, not-a-number"})
    void usesRelatedFromPaginationDefaultsForMalformedNumericValues(String parameter, String value)
            throws Exception {
        mockMvc.perform(get(RELATED_FROM_URI).param(parameter, value))
                .andExpect(status().isOk());

        assertPage(captureRelatedCall().pageable(), 0, 20);
    }

    @ParameterizedTest
    @CsvSource({"notARealField,asc", "iri,sideways"})
    void returnsStableErrorForUnsupportedRelatedFromSort(String sort) throws Exception {
        when(classRepository.getRelatedFrom(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: " + sort));

        assertUnsupportedSort(get(RELATED_FROM_URI).param("sort", sort), sort);
    }

    @ParameterizedTest
    @CsvSource({"resolveReferences, not-a-boolean", "manchesterSyntax, not-a-boolean"})
    void rejectsMalformedRelatedFromTransformOptions(String parameter, String value)
            throws Exception {
        assertStableBadRequest(get(RELATED_FROM_URI).param(parameter, value));
    }

    @Test
    void returnsStableBadRequestForInvalidRelatedFromOntologyIdentifier() throws Exception {
        when(classRepository.getRelatedFrom(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid ontology id"));

        assertStableBadRequest(get(
                "/api/v2/ontologies/efo%252Funsafe/classes/anything/relatedFrom"));
    }

    @ParameterizedTest
    @MethodSource("hierarchyRoutes")
    void returnsDefaultContractForEveryHierarchyRoute(HierarchyRoute route) throws Exception {
        mockMvc.perform(get(route.uri))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].ontologyId").value("efo"))
                .andExpect(jsonPath("$.elements[0].iri").value(CHILD_IRI));

        HierarchyCall call = captureHierarchyCall(route);
        assertEquals("efo", call.ontologyId());
        assertPage(call.pageable(), 0, 20);
        assertEquals(route.decodedIri, call.iri());
        assertFalse(call.includeObsolete());
        assertNull(call.searchQuery());
        assertEquals("en", call.lang());
        assertDefaultOptions(call.outputOptions());
    }

    @ParameterizedTest
    @MethodSource("hierarchyRoutes")
    void bindsEverySharedHierarchyParameter(HierarchyRoute route) throws Exception {
        mockMvc.perform(get(route.uri)
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc")
                        .param("includeObsoleteEntities", "true")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        HierarchyCall call = captureHierarchyCall(route);
        assertPage(call.pageable(), 1, 3);
        assertEquals("iri: DESC", call.pageable().getSort().toString());
        assertTrue(call.includeObsolete());
        assertEquals("fr", call.lang());
        assertTrue(call.outputOptions().resolveReferences);
        assertTrue(call.outputOptions().manchesterSyntax);
    }

    @Test
    void bindsChildrenSearchQuery() throws Exception {
        mockMvc.perform(get(HierarchyRoute.CHILDREN.uri).param("searchQuery", "clinical"))
                .andExpect(status().isOk());

        assertEquals("clinical", captureHierarchyCall(HierarchyRoute.CHILDREN).searchQuery());
    }

    @ParameterizedTest
    @MethodSource("hierarchyPaginationBoundaries")
    void normalizesPaginationBoundariesAcrossEveryHierarchyRoute(
            HierarchyRoute route,
            int requestedPage,
            int requestedSize,
            int expectedPage,
            int expectedSize) throws Exception {
        mockMvc.perform(get(route.uri)
                        .param("page", Integer.toString(requestedPage))
                        .param("size", Integer.toString(requestedSize)))
                .andExpect(status().isOk());

        assertPage(captureHierarchyCall(route).pageable(), expectedPage, expectedSize);
    }

    @ParameterizedTest
    @MethodSource("hierarchyMalformedPagination")
    void usesPaginationDefaultsForMalformedNumericValuesAcrossEveryHierarchyRoute(
            HierarchyRoute route, String parameter) throws Exception {
        mockMvc.perform(get(route.uri).param(parameter, "not-a-number"))
                .andExpect(status().isOk());

        assertPage(captureHierarchyCall(route).pageable(), 0, 20);
    }

    @ParameterizedTest
    @MethodSource("hierarchyRoutes")
    void rejectsMalformedObsoleteSelectionAcrossEveryHierarchyRoute(HierarchyRoute route)
            throws Exception {
        assertStableBadRequest(
                get(route.uri).param("includeObsoleteEntities", "not-a-boolean"));
    }

    @ParameterizedTest
    @MethodSource("hierarchyMalformedTransforms")
    void rejectsMalformedTransformOptionsAcrossEveryHierarchyRoute(
            HierarchyRoute route, String parameter) throws Exception {
        assertStableBadRequest(get(route.uri).param(parameter, "not-a-boolean"));
    }

    @ParameterizedTest
    @MethodSource("hierarchyUnsupportedSorts")
    void returnsStableErrorForUnsupportedSortAcrossEveryHierarchyRoute(
            HierarchyRoute route, String sort) throws Exception {
        stubHierarchyFailure(route, new IllegalArgumentException("Unsupported sort field: " + sort));

        assertUnsupportedSort(get(route.uri).param("sort", sort), sort);
    }

    @ParameterizedTest
    @MethodSource("hierarchyRoutes")
    void returnsStableBadRequestForInvalidOntologyAcrossEveryHierarchyRoute(HierarchyRoute route)
            throws Exception {
        stubHierarchyFailure(route, new IllegalArgumentException("Invalid ontology id"));

        assertStableBadRequest(get(route.invalidOntologyPath));
    }

    @Test
    void rejectsUnsupportedHttpMethodWithStableErrorFields() throws Exception {
        mockMvc.perform(post("/api/v2/classes"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private void performListWithEveryReservedParameter(String path) throws Exception {
        mockMvc.perform(get(path)
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "iri,desc")
                        .param("search", "liver")
                        .param("searchFields", "label")
                        .param("boostFields", "label^10")
                        .param("facetFields", "type")
                        .param("exactMatch", "true")
                        .param("includeObsoleteEntities", "true")
                        .param("excludeOntologyId", "duo")
                        .param("includeTotal", "false")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true")
                        .param("model", "test"))
                .andExpect(status().isOk());
    }

    private void assertStableBadRequest(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private void assertUnsupportedSort(MockHttpServletRequestBuilder request, String sort)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported sort field: " + sort));
    }

    private ListCall captureGlobalListCall() throws Exception {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> search = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> searchFields = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> boostFields = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> exactMatch = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Map<String, Collection<String>>> properties = mapCaptor();
        ArgumentCaptor<JsonTransformOptions> options = ArgumentCaptor.forClass(JsonTransformOptions.class);

        verify(classRepository).find(
                pageable.capture(), lang.capture(), search.capture(), searchFields.capture(),
                boostFields.capture(), exactMatch.capture(), properties.capture(), options.capture());

        return new ListCall(
                pageable.getValue(), lang.getValue(), search.getValue(), searchFields.getValue(),
                boostFields.getValue(), exactMatch.getValue(), properties.getValue(), options.getValue());
    }

    private OntologyListCall captureOntologyListCall() throws Exception {
        ArgumentCaptor<String> ontologyId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> search = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> searchFields = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> boostFields = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> exactMatch = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Map<String, Collection<String>>> properties = mapCaptor();
        ArgumentCaptor<JsonTransformOptions> options = ArgumentCaptor.forClass(JsonTransformOptions.class);

        verify(classRepository).findByOntologyId(
                ontologyId.capture(), pageable.capture(), lang.capture(), search.capture(),
                searchFields.capture(), boostFields.capture(), exactMatch.capture(),
                properties.capture(), options.capture());

        return new OntologyListCall(
                ontologyId.getValue(),
                new ListCall(
                        pageable.getValue(), lang.getValue(), search.getValue(),
                        searchFields.getValue(), boostFields.getValue(), exactMatch.getValue(),
                        properties.getValue(), options.getValue()));
    }

    private ClassCall captureClassCall() {
        ArgumentCaptor<String> ontologyId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> iri = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonTransformOptions> options = ArgumentCaptor.forClass(JsonTransformOptions.class);
        verify(classRepository).getByOntologyIdAndIri(
                ontologyId.capture(), iri.capture(), lang.capture(), options.capture());
        return new ClassCall(
                ontologyId.getValue(), iri.getValue(), lang.getValue(), options.getValue());
    }

    private RelatedCall captureRelatedCall() throws Exception {
        ArgumentCaptor<String> ontologyId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> iri = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonTransformOptions> options = ArgumentCaptor.forClass(JsonTransformOptions.class);
        verify(classRepository).getRelatedFrom(
                ontologyId.capture(), iri.capture(), pageable.capture(), lang.capture(), options.capture());
        return new RelatedCall(
                ontologyId.getValue(), iri.getValue(), pageable.getValue(), lang.getValue(),
                options.getValue());
    }

    private HierarchyCall captureHierarchyCall(HierarchyRoute route) {
        ArgumentCaptor<String> ontologyId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<String> iri = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> includeObsolete = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<String> searchQuery = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonTransformOptions> options = ArgumentCaptor.forClass(JsonTransformOptions.class);

        switch (route) {
            case CHILDREN -> verify(classRepository).getChildrenByOntologyId(
                    ontologyId.capture(), pageable.capture(), iri.capture(),
                    includeObsolete.capture(), searchQuery.capture(), lang.capture(), options.capture());
            case ANCESTORS -> verify(classRepository).getAncestorsByOntologyId(
                    ontologyId.capture(), pageable.capture(), iri.capture(),
                    includeObsolete.capture(), lang.capture(), options.capture());
            case DESCENDANTS -> verify(classRepository).getDescendantsByOntologyId(
                    ontologyId.capture(), pageable.capture(), iri.capture(),
                    includeObsolete.capture(), lang.capture(), options.capture());
            case HIERARCHICAL_DESCENDANTS -> verify(classRepository)
                    .getHierarchicalDescendantsByOntologyId(
                            ontologyId.capture(), pageable.capture(), iri.capture(),
                            includeObsolete.capture(), lang.capture(), options.capture());
            case HIERARCHICAL_CHILDREN -> verify(classRepository)
                    .getHierarchicalChildrenByOntologyId(
                            ontologyId.capture(), pageable.capture(), iri.capture(),
                            includeObsolete.capture(), lang.capture(), options.capture());
            case HIERARCHICAL_ANCESTORS -> verify(classRepository)
                    .getHierarchicalAncestorsByOntologyId(
                            ontologyId.capture(), pageable.capture(), iri.capture(),
                            includeObsolete.capture(), lang.capture(), options.capture());
            case INDIVIDUAL_ANCESTORS -> verify(classRepository)
                    .getIndividualAncestorsByOntologyId(
                            ontologyId.capture(), pageable.capture(), iri.capture(),
                            includeObsolete.capture(), lang.capture(), options.capture());
        }

        return new HierarchyCall(
                ontologyId.getValue(), pageable.getValue(), iri.getValue(),
                includeObsolete.getValue(),
                route == HierarchyRoute.CHILDREN ? searchQuery.getValue() : null,
                lang.getValue(), options.getValue());
    }

    private void stubHierarchyFailure(HierarchyRoute route, RuntimeException failure) {
        switch (route) {
            case CHILDREN -> when(classRepository.getChildrenByOntologyId(
                    any(), any(), any(), anyBoolean(), any(), any(), any())).thenThrow(failure);
            case ANCESTORS -> when(classRepository.getAncestorsByOntologyId(
                    any(), any(), any(), anyBoolean(), any(), any())).thenThrow(failure);
            case DESCENDANTS -> when(classRepository.getDescendantsByOntologyId(
                    any(), any(), any(), anyBoolean(), any(), any())).thenThrow(failure);
            case HIERARCHICAL_DESCENDANTS -> when(
                    classRepository.getHierarchicalDescendantsByOntologyId(
                            any(), any(), any(), anyBoolean(), any(), any())).thenThrow(failure);
            case HIERARCHICAL_CHILDREN -> when(
                    classRepository.getHierarchicalChildrenByOntologyId(
                            any(), any(), any(), anyBoolean(), any(), any())).thenThrow(failure);
            case HIERARCHICAL_ANCESTORS -> when(
                    classRepository.getHierarchicalAncestorsByOntologyId(
                            any(), any(), any(), anyBoolean(), any(), any())).thenThrow(failure);
            case INDIVIDUAL_ANCESTORS -> when(
                    classRepository.getIndividualAncestorsByOntologyId(
                            any(), any(), any(), anyBoolean(), any(), any())).thenThrow(failure);
        }
    }

    private static void assertListDefaults(ListCall call) {
        assertPage(call.pageable(), 0, 20);
        assertTrue(call.pageable().getSort().isUnsorted());
        assertEquals("en", call.lang());
        assertNull(call.search());
        assertNull(call.searchFields());
        assertNull(call.boostFields());
        assertFalse(call.exactMatch());
        assertDefaultOptions(call.outputOptions());
    }

    private static void assertExplicitList(ListCall call) {
        assertPage(call.pageable(), 1, 3);
        assertEquals("iri: DESC", call.pageable().getSort().toString());
        assertEquals("fr", call.lang());
        assertEquals("liver", call.search());
        assertEquals("label definition", call.searchFields());
        assertEquals("label^10", call.boostFields());
        assertTrue(call.exactMatch());
        assertTrue(call.outputOptions().resolveReferences);
        assertTrue(call.outputOptions().manchesterSyntax);
    }

    private static void assertPage(Pageable pageable, int page, int size) {
        assertEquals(page, pageable.getPageNumber());
        assertEquals(size, pageable.getPageSize());
    }

    private static void assertDefaultOptions(JsonTransformOptions options) {
        assertFalse(options.resolveReferences);
        assertFalse(options.manchesterSyntax);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Collection<String>>> mapCaptor() {
        return ArgumentCaptor.forClass(
                (Class<Map<String, Collection<String>>>) (Class<?>) Map.class);
    }

    private static OlsFacetedResultsPage<JsonElement> classPage() {
        return new OlsFacetedResultsPage<>(
                List.of(classJson()), Map.of(), PageRequest.of(0, 20), 1);
    }

    private static PageImpl<JsonElement> hierarchyPage() {
        return new PageImpl<>(
                List.of(JsonParser.parseString("""
                        {
                          "type":["entity","class"],
                          "ontologyId":"efo",
                          "iri":"http://example.org/EFO_1001",
                          "label":"Clinical liver child"
                        }
                        """)),
                PageRequest.of(0, 20),
                1);
    }

    private static JsonElement classJson() {
        return JsonParser.parseString("""
                {
                  "type":["entity","class"],
                  "ontologyId":"efo",
                  "iri":"http://example.org/EFO_0001",
                  "label":"Liver disease"
                }
                """);
    }

    private static Stream<HierarchyRoute> hierarchyRoutes() {
        return Stream.of(HierarchyRoute.values());
    }

    private static Stream<Arguments> hierarchyPaginationBoundaries() {
        int[][] cases = {
                {-1, 20, 0, 20},
                {0, 0, 0, 20},
                {0, -1, 0, 20},
                {0, 1001, 0, 1000}
        };
        return hierarchyRoutes().flatMap(route -> Stream.of(cases)
                .map(values -> Arguments.of(
                        route, values[0], values[1], values[2], values[3])));
    }

    private static Stream<Arguments> hierarchyMalformedPagination() {
        return hierarchyRoutes().flatMap(route ->
                Stream.of("page", "size").map(parameter -> Arguments.of(route, parameter)));
    }

    private static Stream<Arguments> hierarchyMalformedTransforms() {
        return hierarchyRoutes().flatMap(route ->
                Stream.of("resolveReferences", "manchesterSyntax")
                        .map(parameter -> Arguments.of(route, parameter)));
    }

    private static Stream<Arguments> hierarchyUnsupportedSorts() {
        return hierarchyRoutes().flatMap(route ->
                Stream.of("notARealField,asc", "iri,sideways")
                        .map(sort -> Arguments.of(route, sort)));
    }

    private enum HierarchyRoute {
        CHILDREN(
                URI.create("/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_0001/children"),
                CLASS_IRI,
                "/api/v2/ontologies/efo%252Funsafe/classes/anything/children"),
        ANCESTORS(
                URI.create("/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_1001/ancestors"),
                CHILD_IRI,
                "/api/v2/ontologies/efo%252Funsafe/classes/anything/ancestors"),
        DESCENDANTS(
                URI.create("/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_0001/descendants"),
                CLASS_IRI,
                "/api/v2/ontologies/efo%252Funsafe/classes/anything/descendants"),
        HIERARCHICAL_DESCENDANTS(
                URI.create("/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_0001/hierarchicalDescendants"),
                CLASS_IRI,
                "/api/v2/ontologies/efo%252Funsafe/classes/anything/hierarchicalDescendants"),
        HIERARCHICAL_CHILDREN(
                URI.create("/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_0001/hierarchicalChildren"),
                CLASS_IRI,
                "/api/v2/ontologies/efo%252Funsafe/classes/anything/hierarchicalChildren"),
        HIERARCHICAL_ANCESTORS(
                URI.create("/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_1001/hierarchicalAncestors"),
                CHILD_IRI,
                "/api/v2/ontologies/efo%252Funsafe/classes/anything/hierarchicalAncestors"),
        INDIVIDUAL_ANCESTORS(
                URI.create("/api/v2/ontologies/efo/individuals/http%253A%252F%252Fexample.org%252FEFO_I100/ancestors"),
                INDIVIDUAL_IRI,
                "/api/v2/ontologies/efo%252Funsafe/individuals/anything/ancestors");

        private final URI uri;
        private final String decodedIri;
        private final String invalidOntologyPath;

        HierarchyRoute(URI uri, String decodedIri, String invalidOntologyPath) {
            this.uri = uri;
            this.decodedIri = decodedIri;
            this.invalidOntologyPath = invalidOntologyPath;
        }
    }

    private record ListCall(
            Pageable pageable,
            String lang,
            String search,
            String searchFields,
            String boostFields,
            boolean exactMatch,
            Map<String, Collection<String>> properties,
            JsonTransformOptions outputOptions) {
    }

    private record OntologyListCall(String ontologyId, ListCall listCall) {
    }

    private record ClassCall(
            String ontologyId,
            String iri,
            String lang,
            JsonTransformOptions outputOptions) {
    }

    private record RelatedCall(
            String ontologyId,
            String iri,
            Pageable pageable,
            String lang,
            JsonTransformOptions outputOptions) {
    }

    private record HierarchyCall(
            String ontologyId,
            Pageable pageable,
            String iri,
            boolean includeObsolete,
            String searchQuery,
            String lang,
            JsonTransformOptions outputOptions) {
    }
}
