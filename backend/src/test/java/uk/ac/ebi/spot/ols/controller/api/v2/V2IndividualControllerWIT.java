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
import uk.ac.ebi.spot.ols.repository.IndividualRepository;
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

@WebMvcTest(V2IndividualController.class)
@ContextConfiguration(classes = {
        V2IndividualController.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V2IndividualControllerWIT {

    private static final String INDIVIDUAL_IRI = "http://example.org/EFO_I100";
    private static final String CLASS_IRI = "http://example.org/EFO_0001";
    private static final URI INDIVIDUAL_URI = uri(
            "/api/v2/ontologies/efo/individuals/http%253A%252F%252Fexample.org%252FEFO_I100");
    private static final URI CLASS_INDIVIDUALS_URI = uri(
            "/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_0001/individuals");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IndividualRepository individualRepository;

    @BeforeEach
    void stubResponses() throws Exception {
        when(individualRepository.find(
                any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenReturn(individualPage());
        when(individualRepository.findByOntologyId(
                any(), any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenReturn(individualPage());
        when(individualRepository.getIndividualsOfClass(
                any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(individualPage());
    }

    @Test
    void returnsDefaultGlobalIndividualListContract() throws Exception {
        mockMvc.perform(get("/api/v2/individuals"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].ontologyId").value("efo"))
                .andExpect(jsonPath("$.elements[0].iri").value(INDIVIDUAL_IRI))
                .andExpect(jsonPath("$.elements[0].label").value("Liver specimen alpha"));

        ListCall call = captureGlobalListCall();
        assertListDefaults(call);
        assertEquals(Map.of("isObsolete", List.of("false")), call.properties());
    }

    @Test
    void bindsEveryGlobalListParameter() throws Exception {
        mockMvc.perform(get("/api/v2/individuals")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc")
                        .param("search", "specimen")
                        .param("searchFields", "label definition")
                        .param("boostFields", "label^10")
                        .param("exactMatch", "true")
                        .param("includeObsoleteEntities", "true")
                        .param("subset", "individuals", "core")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        ListCall call = captureGlobalListCall();
        assertExplicitListCall(call);
        assertEquals(Map.of("subset", List.of("individuals", "core")), call.properties());
    }

    @Test
    void forwardsGlobalCommaSeparatedAndUriNamedDynamicProperties() throws Exception {
        String property = "http://example.org/category";
        mockMvc.perform(get("/api/v2/individuals")
                        .param("subset", "individuals,core")
                        .param(property, "clinical"))
                .andExpect(status().isOk());

        Map<String, Collection<String>> properties = captureGlobalListCall().properties();
        assertEquals(List.of("individuals,core"), properties.get("subset"));
        assertEquals(List.of("clinical"), properties.get(property));
    }

    @Test
    void globalReservedParametersDoNotBecomeDynamicProperties() throws Exception {
        performListWithEveryReservedParameter("/api/v2/individuals");

        assertEquals(Map.of(), captureGlobalListCall().properties());
    }

    @Test
    void returnsDefaultOntologyIndividualListContract() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/individuals"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri").value(INDIVIDUAL_IRI));

        OntologyListCall call = captureOntologyListCall();
        assertEquals("efo", call.ontologyId());
        assertListDefaults(call.listCall());
        assertEquals(Map.of("isObsolete", List.of("false")), call.listCall().properties());
    }

    @Test
    void bindsEveryOntologyListParameter() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/individuals")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc")
                        .param("search", "specimen")
                        .param("searchFields", "label definition")
                        .param("boostFields", "label^10")
                        .param("exactMatch", "true")
                        .param("includeObsoleteEntities", "true")
                        .param("subset", "individuals", "core")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        OntologyListCall call = captureOntologyListCall();
        assertEquals("efo", call.ontologyId());
        assertExplicitListCall(call.listCall());
        assertEquals(Map.of("subset", List.of("individuals", "core")), call.listCall().properties());
    }

    @Test
    void forwardsOntologyRepeatedCommaSeparatedAndUriNamedDynamicProperties() throws Exception {
        String property = "http://example.org/category";
        mockMvc.perform(get("/api/v2/ontologies/efo/individuals")
                        .param("subset", "individuals", "core")
                        .param(property, "clinical,policy"))
                .andExpect(status().isOk());

        Map<String, Collection<String>> properties = captureOntologyListCall().listCall().properties();
        assertEquals(List.of("individuals", "core"), properties.get("subset"));
        assertEquals(List.of("clinical,policy"), properties.get(property));
    }

    @Test
    void ontologyReservedParametersDoNotBecomeDynamicProperties() throws Exception {
        performListWithEveryReservedParameter("/api/v2/ontologies/efo/individuals");

        assertEquals(Map.of(), captureOntologyListCall().listCall().properties());
    }

    @ParameterizedTest
    @CsvSource({
            "global, -1, 20, 0, 20",
            "global, 0, 0, 0, 20",
            "global, 0, -1, 0, 20",
            "global, 0, 1001, 0, 1000",
            "ontology, -1, 20, 0, 20",
            "ontology, 0, 0, 0, 20",
            "ontology, 0, -1, 0, 20",
            "ontology, 0, 1001, 0, 1000",
            "class, -1, 20, 0, 20",
            "class, 0, 0, 0, 20",
            "class, 0, -1, 0, 20",
            "class, 0, 1001, 0, 1000"
    })
    void normalizesPaginationBoundaries(
            String route,
            int requestedPage,
            int requestedSize,
            int expectedPage,
            int expectedSize) throws Exception {
        mockMvc.perform(get(routeUri(route))
                        .param("page", Integer.toString(requestedPage))
                        .param("size", Integer.toString(requestedSize)))
                .andExpect(status().isOk());

        assertPage(capturePageable(route), expectedPage, expectedSize);
    }

    @ParameterizedTest
    @CsvSource({
            "global, page, not-a-number",
            "global, size, not-a-number",
            "ontology, page, not-a-number",
            "ontology, size, not-a-number",
            "class, page, not-a-number",
            "class, size, not-a-number"
    })
    void usesPaginationDefaultsForMalformedNumericValues(
            String route, String parameter, String value) throws Exception {
        mockMvc.perform(get(routeUri(route)).param(parameter, value))
                .andExpect(status().isOk());

        assertPage(capturePageable(route), 0, 20);
    }

    @ParameterizedTest
    @CsvSource({
            "global, exactMatch",
            "global, includeObsoleteEntities",
            "global, resolveReferences",
            "global, manchesterSyntax",
            "ontology, exactMatch",
            "ontology, includeObsoleteEntities",
            "ontology, resolveReferences",
            "ontology, manchesterSyntax",
            "class, resolveReferences",
            "class, manchesterSyntax",
            "class, includeObsoleteEntities"
    })
    void rejectsMalformedTypedParameters(String route, String parameter) throws Exception {
        assertStableBadRequest(get(routeUri(route)).param(parameter, "not-a-boolean"));
    }

    @ParameterizedTest
    @CsvSource({"resolveReferences", "manchesterSyntax"})
    void rejectsMalformedSingleIndividualTransformOptions(String parameter) throws Exception {
        assertStableBadRequest(get(INDIVIDUAL_URI).param(parameter, "not-a-boolean"));
    }

    @ParameterizedTest
    @CsvSource({
            "global, asc, ASC",
            "global, desc, DESC",
            "ontology, asc, ASC",
            "ontology, desc, DESC",
            "class, asc, ASC",
            "class, desc, DESC"
    })
    void bindsSupportedSortDirections(String route, String requested, String expected)
            throws Exception {
        mockMvc.perform(get(routeUri(route)).param("sort", "iri," + requested))
                .andExpect(status().isOk());

        assertEquals("iri: " + expected, capturePageable(route).getSort().toString());
    }

    @ParameterizedTest
    @CsvSource({
            "global, notARealField,asc",
            "global, iri,sideways",
            "ontology, notARealField,asc",
            "ontology, iri,sideways",
            "class, notARealField,asc",
            "class, iri,sideways"
    })
    void returnsStableErrorForUnsupportedSort(String route, String field, String direction)
            throws Exception {
        String sort = field + "," + direction;
        IllegalArgumentException error =
                new IllegalArgumentException("Unsupported sort field: " + sort);
        if (route.equals("global")) {
            when(individualRepository.find(
                    any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                    .thenThrow(error);
        } else if (route.equals("ontology")) {
            when(individualRepository.findByOntologyId(
                    any(), any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                    .thenThrow(error);
        } else {
            when(individualRepository.getIndividualsOfClass(
                    any(), any(), any(), anyBoolean(), any(), any()))
                    .thenThrow(error);
        }

        mockMvc.perform(get(routeUri(route)).param("sort", sort))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported sort field: " + sort));
    }

    @Test
    void returnsIndividualByDoubleEncodedIriWithDefaults() throws Exception {
        when(individualRepository.getByOntologyIdAndIri(any(), any(), any(), any()))
                .thenReturn(individualEntity());

        mockMvc.perform(get(INDIVIDUAL_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ontologyId").value("efo"))
                .andExpect(jsonPath("$.iri").value(INDIVIDUAL_IRI))
                .andExpect(jsonPath("$.label").value("Liver specimen alpha"));

        IndividualCall call = captureIndividualCall();
        assertEquals("efo", call.ontologyId());
        assertEquals(INDIVIDUAL_IRI, call.iri());
        assertEquals("en", call.lang());
        assertDefaultOptions(call.outputOptions());
    }

    @Test
    void forwardsSingleIndividualLanguageAndTransformOptions() throws Exception {
        when(individualRepository.getByOntologyIdAndIri(any(), any(), any(), any()))
                .thenReturn(individualEntity());

        mockMvc.perform(get(INDIVIDUAL_URI)
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        IndividualCall call = captureIndividualCall();
        assertEquals("fr", call.lang());
        assertTrue(call.outputOptions().resolveReferences);
        assertTrue(call.outputOptions().manchesterSyntax);
    }

    @Test
    void returnsStableNotFoundContractForMissingIndividual() throws Exception {
        when(individualRepository.getByOntologyIdAndIri(any(), any(), any(), any()))
                .thenReturn(null);

        mockMvc.perform(get(INDIVIDUAL_URI))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("The requested resource was not found."));
    }

    @Test
    void returnsDefaultClassIndividualsContract() throws Exception {
        mockMvc.perform(get(CLASS_INDIVIDUALS_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri").value(INDIVIDUAL_IRI));

        ClassCall call = captureClassCall();
        assertEquals("efo", call.ontologyId());
        assertEquals(CLASS_IRI, call.classIri());
        assertPage(call.pageable(), 0, 20);
        assertTrue(call.pageable().getSort().isUnsorted());
        assertFalse(call.includeObsoleteEntities());
        assertEquals("en", call.lang());
        assertDefaultOptions(call.outputOptions());
    }

    @Test
    void bindsEveryClassIndividualsParameter() throws Exception {
        mockMvc.perform(get(CLASS_INDIVIDUALS_URI)
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc")
                        .param("includeObsoleteEntities", "true")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        ClassCall call = captureClassCall();
        assertEquals("efo", call.ontologyId());
        assertEquals(CLASS_IRI, call.classIri());
        assertPage(call.pageable(), 1, 3);
        assertEquals("iri: DESC", call.pageable().getSort().toString());
        assertTrue(call.includeObsoleteEntities());
        assertEquals("fr", call.lang());
        assertTrue(call.outputOptions().resolveReferences);
        assertTrue(call.outputOptions().manchesterSyntax);
    }

    @ParameterizedTest
    @CsvSource({"ontology", "single", "class"})
    void returnsStableBadRequestForInvalidOntologyIdentifier(String route) throws Exception {
        IllegalArgumentException error =
                new IllegalArgumentException("Invalid ontology ID: efo/unsafe");
        if (route.equals("ontology")) {
            when(individualRepository.findByOntologyId(
                    any(), any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                    .thenThrow(error);
        } else if (route.equals("single")) {
            when(individualRepository.getByOntologyIdAndIri(any(), any(), any(), any()))
                    .thenThrow(error);
        } else {
            when(individualRepository.getIndividualsOfClass(
                    any(), any(), any(), anyBoolean(), any(), any()))
                    .thenThrow(error);
        }

        URI invalid = switch (route) {
            case "ontology" -> uri("/api/v2/ontologies/efo%252Funsafe/individuals");
            case "single" -> uri("/api/v2/ontologies/efo%252Funsafe/individuals/"
                    + "http%253A%252F%252Fexample.org%252FEFO_I100");
            default -> uri("/api/v2/ontologies/efo%252Funsafe/classes/"
                    + "http%253A%252F%252Fexample.org%252FEFO_0001/individuals");
        };

        mockMvc.perform(get(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid ontology ID: efo/unsafe"));
    }

    @ParameterizedTest
    @CsvSource({"global", "ontology", "single", "class"})
    void returnsStableBadRequestForInvalidLanguage(String route) throws Exception {
        IllegalArgumentException error = new IllegalArgumentException("Invalid language: en_US");
        if (route.equals("global")) {
            when(individualRepository.find(
                    any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                    .thenThrow(error);
        } else if (route.equals("ontology")) {
            when(individualRepository.findByOntologyId(
                    any(), any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                    .thenThrow(error);
        } else if (route.equals("single")) {
            when(individualRepository.getByOntologyIdAndIri(any(), any(), any(), any()))
                    .thenThrow(error);
        } else {
            when(individualRepository.getIndividualsOfClass(
                    any(), any(), any(), anyBoolean(), any(), any()))
                    .thenThrow(error);
        }

        URI target = route.equals("single") ? INDIVIDUAL_URI : routeUri(route);
        mockMvc.perform(get(target).param("lang", "en_US"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid language: en_US"));
    }

    @Test
    void rejectsUnsupportedHttpMethodWithStableErrorFields() throws Exception {
        mockMvc.perform(post("/api/v2/individuals"))
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

    private ListCall captureGlobalListCall() throws Exception {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> search = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> searchFields = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> boostFields = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> exactMatch = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Map<String, Collection<String>>> properties = mapCaptor();
        ArgumentCaptor<JsonTransformOptions> options = ArgumentCaptor.forClass(JsonTransformOptions.class);
        verify(individualRepository).find(
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
        verify(individualRepository).findByOntologyId(
                ontologyId.capture(), pageable.capture(), lang.capture(), search.capture(),
                searchFields.capture(), boostFields.capture(), exactMatch.capture(),
                properties.capture(), options.capture());
        return new OntologyListCall(
                ontologyId.getValue(),
                new ListCall(
                        pageable.getValue(), lang.getValue(), search.getValue(), searchFields.getValue(),
                        boostFields.getValue(), exactMatch.getValue(), properties.getValue(), options.getValue()));
    }

    private IndividualCall captureIndividualCall() {
        ArgumentCaptor<String> ontologyId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> iri = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonTransformOptions> options = ArgumentCaptor.forClass(JsonTransformOptions.class);
        verify(individualRepository).getByOntologyIdAndIri(
                ontologyId.capture(), iri.capture(), lang.capture(), options.capture());
        return new IndividualCall(
                ontologyId.getValue(), iri.getValue(), lang.getValue(), options.getValue());
    }

    private ClassCall captureClassCall() throws Exception {
        ArgumentCaptor<String> ontologyId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> classIri = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<Boolean> includeObsoleteEntities = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonTransformOptions> options = ArgumentCaptor.forClass(JsonTransformOptions.class);
        verify(individualRepository).getIndividualsOfClass(
                ontologyId.capture(), classIri.capture(), pageable.capture(),
                includeObsoleteEntities.capture(), lang.capture(), options.capture());
        return new ClassCall(
                ontologyId.getValue(), classIri.getValue(), pageable.getValue(),
                includeObsoleteEntities.getValue(), lang.getValue(), options.getValue());
    }

    private Pageable capturePageable(String route) throws Exception {
        if (route.equals("global")) return captureGlobalListCall().pageable();
        if (route.equals("ontology")) return captureOntologyListCall().listCall().pageable();
        return captureClassCall().pageable();
    }

    private static URI routeUri(String route) {
        return switch (route) {
            case "global" -> uri("/api/v2/individuals");
            case "ontology" -> uri("/api/v2/ontologies/efo/individuals");
            case "class" -> CLASS_INDIVIDUALS_URI;
            default -> throw new IllegalArgumentException("Unknown route: " + route);
        };
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

    private static void assertExplicitListCall(ListCall call) {
        assertPage(call.pageable(), 1, 3);
        assertEquals("iri: DESC", call.pageable().getSort().toString());
        assertEquals("specimen", call.search());
        assertEquals("label definition", call.searchFields());
        assertEquals("label^10", call.boostFields());
        assertTrue(call.exactMatch());
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

    private static OlsFacetedResultsPage<JsonElement> individualPage() {
        return new OlsFacetedResultsPage<>(
                List.of(individualJson()), Map.of(), PageRequest.of(0, 20), 1);
    }

    private static V2Entity individualEntity() {
        return new V2Entity(individualJson());
    }

    private static JsonElement individualJson() {
        return JsonParser.parseString("""
                {
                  "type":["entity","individual"],
                  "ontologyId":"efo",
                  "iri":"http://example.org/EFO_I100",
                  "label":"Liver specimen alpha"
                }
                """);
    }

    private static URI uri(String value) {
        return URI.create(value);
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

    private record IndividualCall(
            String ontologyId,
            String iri,
            String lang,
            JsonTransformOptions outputOptions) {
    }

    private record ClassCall(
            String ontologyId,
            String classIri,
            Pageable pageable,
            boolean includeObsoleteEntities,
            String lang,
            JsonTransformOptions outputOptions) {
    }
}
