package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.PropertyRepository;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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

@WebMvcTest(V2PropertyController.class)
@ContextConfiguration(classes = {
        V2PropertyController.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V2PropertyControllerWIT {

    private static final String PROPERTY_IRI = "http://example.org/EFO_0100";
    private static final URI PROPERTY_URI = URI.create(
            "/api/v2/ontologies/efo/properties/http%253A%252F%252Fexample.org%252FEFO_0100");
    private static final URI CHILDREN_URI = URI.create(
            "/api/v2/ontologies/efo/properties/http%253A%252F%252Fexample.org%252FEFO_0100/children");
    private static final URI ANCESTORS_URI = URI.create(
            "/api/v2/ontologies/efo/properties/http%253A%252F%252Fexample.org%252FEFO_0101/ancestors");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PropertyRepository propertyRepository;

    @BeforeEach
    void stubResponses() throws Exception {
        when(propertyRepository.find(
                any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenReturn(propertyPage());
        when(propertyRepository.findByOntologyId(
                any(), any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenReturn(propertyPage());
        when(propertyRepository.getChildrenByOntologyId(any(), any(), any(), any(), any()))
                .thenReturn(hierarchyPage());
        when(propertyRepository.getAncestorsByOntologyId(any(), any(), any(), any(), any()))
                .thenReturn(hierarchyPage());
    }

    @Test
    void returnsDefaultGlobalPropertyListContract() throws Exception {
        mockMvc.perform(get("/api/v2/properties"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].ontologyId").value("efo"))
                .andExpect(jsonPath("$.elements[0].iri").value(PROPERTY_IRI))
                .andExpect(jsonPath("$.elements[0].label").value("has specimen"));

        ListCall call = captureGlobalListCall();
        assertListDefaults(call);
        assertEquals(Map.of("isObsolete", List.of("false")), call.properties());
    }

    @Test
    void bindsEveryGlobalListParameter() throws Exception {
        mockMvc.perform(get("/api/v2/properties")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc")
                        .param("search", "specimen")
                        .param("searchFields", "label definition")
                        .param("boostFields", "label^10")
                        .param("exactMatch", "true")
                        .param("includeObsoleteEntities", "true")
                        .param("subset", "relations", "core")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        ListCall call = captureGlobalListCall();
        assertEquals(1, call.pageable().getPageNumber());
        assertEquals(3, call.pageable().getPageSize());
        assertEquals("iri: DESC", call.pageable().getSort().toString());
        assertEquals("specimen", call.search());
        assertEquals("label definition", call.searchFields());
        assertEquals("label^10", call.boostFields());
        assertTrue(call.exactMatch());
        assertEquals(Map.of("subset", List.of("relations", "core")), call.properties());
        assertEquals("fr", call.lang());
        assertTrue(call.outputOptions().resolveReferences);
        assertTrue(call.outputOptions().manchesterSyntax);
    }

    @Test
    void forwardsGlobalCommaSeparatedAndUriNamedDynamicProperties() throws Exception {
        String property = "http://example.org/category";

        mockMvc.perform(get("/api/v2/properties")
                        .param("subset", "relations,core")
                        .param(property, "research"))
                .andExpect(status().isOk());

        Map<String, Collection<String>> properties = captureGlobalListCall().properties();
        assertEquals(List.of("relations,core"), properties.get("subset"));
        assertEquals(List.of("research"), properties.get(property));
    }

    @Test
    void globalReservedParametersDoNotBecomeDynamicProperties() throws Exception {
        performListWithEveryReservedParameter("/api/v2/properties");

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
        mockMvc.perform(get("/api/v2/properties")
                        .param("page", Integer.toString(requestedPage))
                        .param("size", Integer.toString(requestedSize)))
                .andExpect(status().isOk());

        assertPage(captureGlobalListCall().pageable(), expectedPage, expectedSize);
    }

    @ParameterizedTest
    @CsvSource({"page, not-a-number", "size, not-a-number"})
    void usesGlobalPaginationDefaultsForMalformedNumericValues(String parameter, String value)
            throws Exception {
        mockMvc.perform(get("/api/v2/properties").param(parameter, value))
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
        assertStableBadRequest(get("/api/v2/properties").param(parameter, value));
    }

    @ParameterizedTest
    @CsvSource({"notARealField,asc", "iri,sideways"})
    void returnsStableErrorForUnsupportedGlobalSort(String sort) throws Exception {
        when(propertyRepository.find(
                any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: " + sort));

        assertUnsupportedSort(get("/api/v2/properties").param("sort", sort), sort);
    }

    @Test
    void returnsDefaultOntologyPropertyListContract() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/properties"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.elements[0].ontologyId").value("efo"));

        OntologyListCall call = captureOntologyListCall();
        assertEquals("efo", call.ontologyId());
        assertListDefaults(call.listCall());
        assertEquals(Map.of("isObsolete", List.of("false")), call.listCall().properties());
    }

    @Test
    void bindsEveryOntologyListParameter() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/properties")
                        .param("page", "2")
                        .param("size", "4")
                        .param("sort", "iri,asc")
                        .param("search", "specimen")
                        .param("searchFields", "label")
                        .param("boostFields", "label^5")
                        .param("exactMatch", "true")
                        .param("includeObsoleteEntities", "true")
                        .param("subset", "relations", "core")
                        .param("lang", "de")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        OntologyListCall ontologyCall = captureOntologyListCall();
        ListCall call = ontologyCall.listCall();
        assertEquals("efo", ontologyCall.ontologyId());
        assertPage(call.pageable(), 2, 4);
        assertEquals("iri: ASC", call.pageable().getSort().toString());
        assertEquals("specimen", call.search());
        assertEquals("label", call.searchFields());
        assertEquals("label^5", call.boostFields());
        assertTrue(call.exactMatch());
        assertEquals(Map.of("subset", List.of("relations", "core")), call.properties());
        assertEquals("de", call.lang());
        assertTrue(call.outputOptions().resolveReferences);
        assertTrue(call.outputOptions().manchesterSyntax);
    }

    @Test
    void forwardsOntologyRepeatedCommaSeparatedAndUriNamedDynamicProperties() throws Exception {
        String property = "http://example.org/category";

        mockMvc.perform(get("/api/v2/ontologies/efo/properties")
                        .param("subset", "relations", "core")
                        .param("domain", "biology,information")
                        .param(property, "research"))
                .andExpect(status().isOk());

        Map<String, Collection<String>> properties = captureOntologyListCall().listCall().properties();
        assertEquals(List.of("relations", "core"), properties.get("subset"));
        assertEquals(List.of("biology,information"), properties.get("domain"));
        assertEquals(List.of("research"), properties.get(property));
    }

    @Test
    void ontologyReservedParametersDoNotBecomeDynamicProperties() throws Exception {
        performListWithEveryReservedParameter("/api/v2/ontologies/efo/properties");

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
        mockMvc.perform(get("/api/v2/ontologies/efo/properties")
                        .param("page", Integer.toString(requestedPage))
                        .param("size", Integer.toString(requestedSize)))
                .andExpect(status().isOk());

        assertPage(captureOntologyListCall().listCall().pageable(), expectedPage, expectedSize);
    }

    @ParameterizedTest
    @CsvSource({"page, not-a-number", "size, not-a-number"})
    void usesOntologyPaginationDefaultsForMalformedNumericValues(String parameter, String value)
            throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/properties").param(parameter, value))
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
    void rejectsMalformedOntologyListTypedParameters(String parameter, String value)
            throws Exception {
        assertStableBadRequest(
                get("/api/v2/ontologies/efo/properties").param(parameter, value));
    }

    @ParameterizedTest
    @CsvSource({"notARealField,asc", "iri,sideways"})
    void returnsStableErrorForUnsupportedOntologySort(String sort) throws Exception {
        when(propertyRepository.findByOntologyId(
                any(), any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: " + sort));

        assertUnsupportedSort(
                get("/api/v2/ontologies/efo/properties").param("sort", sort), sort);
    }

    @Test
    void returnsStableBadRequestForInvalidOntologyListIdentifier() throws Exception {
        when(propertyRepository.findByOntologyId(
                any(), any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenThrow(new IllegalArgumentException("Invalid ontology ID: efo/unsafe"));

        mockMvc.perform(get("/api/v2/ontologies/efo%252Funsafe/properties"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid ontology ID: efo/unsafe"));
    }

    @Test
    void returnsPropertyByDoubleEncodedIriWithDefaults() throws Exception {
        when(propertyRepository.getByOntologyIdAndIri(any(), any(), any(), any()))
                .thenReturn(propertyEntity());

        mockMvc.perform(get(PROPERTY_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ontologyId").value("efo"))
                .andExpect(jsonPath("$.iri").value(PROPERTY_IRI))
                .andExpect(jsonPath("$.label").value("has specimen"));

        PropertyCall call = capturePropertyCall();
        assertEquals("efo", call.ontologyId());
        assertEquals(PROPERTY_IRI, call.iri());
        assertEquals("en", call.lang());
        assertDefaultOptions(call.outputOptions());
    }

    @Test
    void forwardsSinglePropertyLanguageAndTransformOptions() throws Exception {
        when(propertyRepository.getByOntologyIdAndIri(any(), any(), any(), any()))
                .thenReturn(propertyEntity());

        mockMvc.perform(get(PROPERTY_URI)
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        PropertyCall call = capturePropertyCall();
        assertEquals("fr", call.lang());
        assertTrue(call.outputOptions().resolveReferences);
        assertTrue(call.outputOptions().manchesterSyntax);
    }

    @Test
    void returnsStableNotFoundContractForMissingProperty() throws Exception {
        when(propertyRepository.getByOntologyIdAndIri(any(), any(), any(), any()))
                .thenReturn(null);

        mockMvc.perform(get(PROPERTY_URI))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("The requested resource was not found."));
    }

    @Test
    void returnsStableBadRequestForInvalidSinglePropertyIdentifier() throws Exception {
        when(propertyRepository.getByOntologyIdAndIri(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid ontology ID: efo/unsafe"));

        mockMvc.perform(get(URI.create(
                        "/api/v2/ontologies/efo%252Funsafe/properties/http%253A%252F%252Fexample.org%252FEFO_0100")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid ontology ID: efo/unsafe"));
    }

    @Test
    void returnsDefaultChildrenContract() throws Exception {
        mockMvc.perform(get(CHILDREN_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri").value("http://example.org/EFO_0101"));

        HierarchyCall call = captureChildrenCall();
        assertHierarchyDefaults(call, PROPERTY_IRI);
    }

    @Test
    void bindsEveryChildrenParameter() throws Exception {
        mockMvc.perform(get(CHILDREN_URI)
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        assertExplicitHierarchyCall(captureChildrenCall(), PROPERTY_IRI);
    }

    @Test
    void returnsDefaultAncestorsContract() throws Exception {
        mockMvc.perform(get(ANCESTORS_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri").value("http://example.org/EFO_0101"));

        HierarchyCall call = captureAncestorsCall();
        assertHierarchyDefaults(call, "http://example.org/EFO_0101");
    }

    @Test
    void bindsEveryAncestorsParameter() throws Exception {
        mockMvc.perform(get(ANCESTORS_URI)
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        assertExplicitHierarchyCall(
                captureAncestorsCall(), "http://example.org/EFO_0101");
    }

    @ParameterizedTest
    @CsvSource({
            "children, -1, 20, 0, 20",
            "children, 0, 0, 0, 20",
            "children, 0, 1001, 0, 1000",
            "ancestors, -1, 20, 0, 20",
            "ancestors, 0, -1, 0, 20",
            "ancestors, 0, 1001, 0, 1000"
    })
    void normalizesHierarchyPaginationBoundaries(
            String route,
            int requestedPage,
            int requestedSize,
            int expectedPage,
            int expectedSize) throws Exception {
        URI uri = route.equals("children") ? CHILDREN_URI : ANCESTORS_URI;
        mockMvc.perform(get(uri)
                        .param("page", Integer.toString(requestedPage))
                        .param("size", Integer.toString(requestedSize)))
                .andExpect(status().isOk());

        Pageable pageable = route.equals("children")
                ? captureChildrenCall().pageable()
                : captureAncestorsCall().pageable();
        assertPage(pageable, expectedPage, expectedSize);
    }

    @ParameterizedTest
    @CsvSource({
            "children, page, not-a-number",
            "children, size, not-a-number",
            "ancestors, page, not-a-number",
            "ancestors, size, not-a-number"
    })
    void usesHierarchyPaginationDefaultsForMalformedNumericValues(
            String route, String parameter, String value) throws Exception {
        URI uri = route.equals("children") ? CHILDREN_URI : ANCESTORS_URI;
        mockMvc.perform(get(uri).param(parameter, value))
                .andExpect(status().isOk());

        Pageable pageable = route.equals("children")
                ? captureChildrenCall().pageable()
                : captureAncestorsCall().pageable();
        assertPage(pageable, 0, 20);
    }

    @ParameterizedTest
    @CsvSource({
            "children, resolveReferences",
            "children, manchesterSyntax",
            "ancestors, resolveReferences",
            "ancestors, manchesterSyntax"
    })
    void rejectsMalformedHierarchyTransformOptions(String route, String parameter)
            throws Exception {
        URI uri = route.equals("children") ? CHILDREN_URI : ANCESTORS_URI;
        assertStableBadRequest(get(uri).param(parameter, "not-a-boolean"));
    }

    @ParameterizedTest
    @CsvSource({"notARealField,asc", "iri,sideways"})
    void returnsStableErrorForUnsupportedChildrenSort(String sort) throws Exception {
        when(propertyRepository.getChildrenByOntologyId(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: " + sort));

        assertUnsupportedSort(get(CHILDREN_URI).param("sort", sort), sort);
    }

    @ParameterizedTest
    @CsvSource({"notARealField,asc", "iri,sideways"})
    void returnsStableErrorForUnsupportedAncestorsSort(String sort) throws Exception {
        when(propertyRepository.getAncestorsByOntologyId(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: " + sort));

        assertUnsupportedSort(get(ANCESTORS_URI).param("sort", sort), sort);
    }

    @ParameterizedTest
    @CsvSource({"children", "ancestors"})
    void returnsStableBadRequestForInvalidHierarchyOntologyIdentifier(String route)
            throws Exception {
        when(propertyRepository.getChildrenByOntologyId(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid ontology ID: efo/unsafe"));
        when(propertyRepository.getAncestorsByOntologyId(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid ontology ID: efo/unsafe"));

        URI uri = route.equals("children")
                ? URI.create("/api/v2/ontologies/efo%252Funsafe/properties/"
                        + "http%253A%252F%252Fexample.org%252FEFO_0100/children")
                : URI.create("/api/v2/ontologies/efo%252Funsafe/properties/"
                        + "http%253A%252F%252Fexample.org%252FEFO_0101/ancestors");

        mockMvc.perform(get(uri))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid ontology ID: efo/unsafe"));
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v2/properties, resolveReferences",
            "/api/v2/ontologies/efo/properties, manchesterSyntax",
            "/api/v2/ontologies/efo/properties/http%253A%252F%252Fexample.org%252FEFO_0100, resolveReferences"
    })
    void rejectsMalformedTransformOptionsAcrossNonHierarchyRoutes(String path, String parameter)
            throws Exception {
        assertStableBadRequest(get(URI.create(path)).param(parameter, "not-a-boolean"));
    }

    @Test
    void rejectsUnsupportedHttpMethodWithStableErrorFields() throws Exception {
        mockMvc.perform(post("/api/v2/properties"))
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
                        .param("search", "specimen")
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

        verify(propertyRepository).find(
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

        verify(propertyRepository).findByOntologyId(
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

    private PropertyCall capturePropertyCall() {
        ArgumentCaptor<String> ontologyId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> iri = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonTransformOptions> options = ArgumentCaptor.forClass(JsonTransformOptions.class);
        verify(propertyRepository).getByOntologyIdAndIri(
                ontologyId.capture(), iri.capture(), lang.capture(), options.capture());
        return new PropertyCall(
                ontologyId.getValue(), iri.getValue(), lang.getValue(), options.getValue());
    }

    private HierarchyCall captureChildrenCall() {
        return captureHierarchyCall(true);
    }

    private HierarchyCall captureAncestorsCall() {
        return captureHierarchyCall(false);
    }

    private HierarchyCall captureHierarchyCall(boolean children) {
        ArgumentCaptor<String> ontologyId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<String> iri = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonTransformOptions> options = ArgumentCaptor.forClass(JsonTransformOptions.class);
        if (children) {
            verify(propertyRepository).getChildrenByOntologyId(
                    ontologyId.capture(), pageable.capture(), iri.capture(), lang.capture(), options.capture());
        } else {
            verify(propertyRepository).getAncestorsByOntologyId(
                    ontologyId.capture(), pageable.capture(), iri.capture(), lang.capture(), options.capture());
        }
        return new HierarchyCall(
                ontologyId.getValue(), pageable.getValue(), iri.getValue(), lang.getValue(), options.getValue());
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

    private static void assertHierarchyDefaults(HierarchyCall call, String iri) {
        assertEquals("efo", call.ontologyId());
        assertPage(call.pageable(), 0, 20);
        assertTrue(call.pageable().getSort().isUnsorted());
        assertEquals(iri, call.iri());
        assertEquals("en", call.lang());
        assertDefaultOptions(call.outputOptions());
    }

    private static void assertExplicitHierarchyCall(HierarchyCall call, String iri) {
        assertEquals("efo", call.ontologyId());
        assertPage(call.pageable(), 1, 3);
        assertEquals("iri: DESC", call.pageable().getSort().toString());
        assertEquals(iri, call.iri());
        assertEquals("fr", call.lang());
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

    private static OlsFacetedResultsPage<JsonElement> propertyPage() {
        return new OlsFacetedResultsPage<>(
                List.of(propertyJson()), Map.of(), PageRequest.of(0, 20), 1);
    }

    private static PageImpl<JsonElement> hierarchyPage() {
        return new PageImpl<>(
                List.of(JsonParser.parseString("""
                        {
                          "type":["entity","property"],
                          "ontologyId":"efo",
                          "iri":"http://example.org/EFO_0101",
                          "label":"has sample"
                        }
                        """)),
                PageRequest.of(0, 20),
                1);
    }

    private static V2Entity propertyEntity() {
        return new V2Entity(propertyJson());
    }

    private static JsonElement propertyJson() {
        return JsonParser.parseString("""
                {
                  "type":["entity","property"],
                  "ontologyId":"efo",
                  "iri":"http://example.org/EFO_0100",
                  "label":"has specimen"
                }
                """);
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

    private record PropertyCall(
            String ontologyId,
            String iri,
            String lang,
            JsonTransformOptions outputOptions) {
    }

    private record HierarchyCall(
            String ontologyId,
            Pageable pageable,
            String iri,
            String lang,
            JsonTransformOptions outputOptions) {
    }
}
