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
import uk.ac.ebi.spot.ols.config.WebConfig;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.repository.EntityRepository;
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

@WebMvcTest(V2EntityController.class)
@ContextConfiguration(classes = {
        V2EntityController.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V2EntityControllerWIT {

    private static final String ENTITY_IRI = "http://example.org/EFO_0001";
    private static final URI ENTITY_URI = URI.create(
            "/api/v2/ontologies/efo/entities/http%253A%252F%252Fexample.org%252FEFO_0001");
    private static final URI RELATED_FROM_URI = URI.create(
            "/api/v2/ontologies/efo/entities/http%253A%252F%252Fexample.org%252FEFO_0001/relatedFrom");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EntityRepository entityRepository;

    @BeforeEach
    void stubResponses() throws Exception {
        when(entityRepository.find(
                any(), any(), any(), any(), any(), any(), anyBoolean(), any(), anyMap(), any(), anyBoolean()))
                .thenReturn(entityPage());
        when(entityRepository.findByOntologyId(
                any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenReturn(entityPage());
        when(entityRepository.getRelatedFrom(any(), any(), any(), any(), any()))
                .thenReturn(entityPage());
    }

    @Test
    void returnsDefaultGlobalEntityListContract() throws Exception {
        mockMvc.perform(get("/api/v2/entities"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].ontologyId").value("efo"))
                .andExpect(jsonPath("$.elements[0].iri").value(ENTITY_IRI))
                .andExpect(jsonPath("$.elements[0].label").value("Liver disease"))
                .andExpect(jsonPath("$.facetFieldsToCounts.type.class").value(1));

        GlobalCall call = captureGlobalCall();
        assertEquals(0, call.pageable().getPageNumber());
        assertEquals(20, call.pageable().getPageSize());
        assertTrue(call.pageable().getSort().isUnsorted());
        assertEquals("en", call.lang());
        assertNull(call.search());
        assertNull(call.searchFields());
        assertNull(call.boostFields());
        assertNull(call.facetFields());
        assertFalse(call.exactMatch());
        assertNull(call.excludeOntologyIds());
        assertEquals(Map.of("isObsolete", List.of("false")), call.properties());
        assertTrue(call.includeTotal());
        assertFalse(call.outputOptions().resolveReferences);
        assertFalse(call.outputOptions().manchesterSyntax);
    }

    @Test
    void bindsGlobalPageAndSize() throws Exception {
        mockMvc.perform(get("/api/v2/entities").param("page", "2").param("size", "7"))
                .andExpect(status().isOk());

        GlobalCall call = captureGlobalCall();
        assertEquals(2, call.pageable().getPageNumber());
        assertEquals(7, call.pageable().getPageSize());
    }

    @ParameterizedTest
    @CsvSource({"asc, ASC", "desc, DESC"})
    void bindsGlobalSortDirection(String requested, String expected) throws Exception {
        mockMvc.perform(get("/api/v2/entities").param("sort", "iri," + requested))
                .andExpect(status().isOk());

        assertEquals("iri: " + expected, captureGlobalCall().pageable().getSort().toString());
    }

    @Test
    void bindsGlobalSearchFacetAndRankingParameters() throws Exception {
        mockMvc.perform(get("/api/v2/entities")
                        .param("search", "liver")
                        .param("searchFields", "label definition")
                        .param("boostFields", "label^10 curie^5")
                        .param("facetFields", "ontologyId type"))
                .andExpect(status().isOk());

        GlobalCall call = captureGlobalCall();
        assertEquals("liver", call.search());
        assertEquals("label definition", call.searchFields());
        assertEquals("label^10 curie^5", call.boostFields());
        assertEquals("ontologyId type", call.facetFields());
    }

    @ParameterizedTest
    @CsvSource({"true, true", "false, false"})
    void bindsExplicitGlobalExactMatch(String requested, boolean expected) throws Exception {
        mockMvc.perform(get("/api/v2/entities").param("exactMatch", requested))
                .andExpect(status().isOk());

        assertEquals(expected, captureGlobalCall().exactMatch());
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true"})
    void bindsExplicitGlobalObsoleteSelection(String requested, boolean expectsFilter) throws Exception {
        mockMvc.perform(get("/api/v2/entities").param("includeObsoleteEntities", requested))
                .andExpect(status().isOk());

        assertEquals(expectsFilter, captureGlobalCall().properties().containsKey("isObsolete"));
    }

    @Test
    void splitsExcludedOntologyIds() throws Exception {
        mockMvc.perform(get("/api/v2/entities").param("excludeOntologyId", "ncit,snomed"))
                .andExpect(status().isOk());

        assertEquals(List.of("ncit", "snomed"), captureGlobalCall().excludeOntologyIds());
    }

    @ParameterizedTest
    @CsvSource({"true, true", "false, false"})
    void bindsExplicitIncludeTotal(String requested, boolean expected) throws Exception {
        mockMvc.perform(get("/api/v2/entities").param("includeTotal", requested))
                .andExpect(status().isOk());

        assertEquals(expected, captureGlobalCall().includeTotal());
    }

    @Test
    void bindsGlobalLanguageAndTransformOptions() throws Exception {
        mockMvc.perform(get("/api/v2/entities")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true")
                        .param("model", "test"))
                .andExpect(status().isOk());

        GlobalCall call = captureGlobalCall();
        assertEquals("fr", call.lang());
        assertTrue(call.outputOptions().resolveReferences);
        assertTrue(call.outputOptions().manchesterSyntax);
    }

    @Test
    void forwardsSingleDynamicPropertyValue() throws Exception {
        mockMvc.perform(get("/api/v2/entities").param("subset", "slim"))
                .andExpect(status().isOk());

        assertEquals(List.of("slim"), captureGlobalCall().properties().get("subset"));
    }

    @Test
    void forwardsRepeatedDynamicPropertyValues() throws Exception {
        mockMvc.perform(get("/api/v2/entities").param("subset", "slim", "core"))
                .andExpect(status().isOk());

        assertEquals(List.of("slim", "core"), captureGlobalCall().properties().get("subset"));
    }

    @Test
    void forwardsCommaSeparatedDynamicPropertyValues() throws Exception {
        mockMvc.perform(get("/api/v2/entities").param("subset", "slim,core"))
                .andExpect(status().isOk());

        assertEquals(List.of("slim,core"), captureGlobalCall().properties().get("subset"));
    }

    @Test
    void decodesUriNamedDynamicProperty() throws Exception {
        String property = "http://example.org/category";

        mockMvc.perform(get("/api/v2/entities").param(property, "clinical"))
                .andExpect(status().isOk());

        assertEquals(List.of("clinical"), captureGlobalCall().properties().get(property));
    }

    @Test
    void reservedGlobalParametersDoNotBecomeDynamicProperties() throws Exception {
        mockMvc.perform(get("/api/v2/entities")
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

        assertEquals(Map.of(), captureGlobalCall().properties());
    }

    @Test
    void combinesSearchFieldsExactMatchFacetingAndDynamicFiltering() throws Exception {
        mockMvc.perform(get("/api/v2/entities")
                        .param("search", "Liver disease")
                        .param("searchFields", "label")
                        .param("exactMatch", "true")
                        .param("facetFields", "ontologyId")
                        .param("type", "class"))
                .andExpect(status().isOk());

        GlobalCall call = captureGlobalCall();
        assertEquals("Liver disease", call.search());
        assertEquals("label", call.searchFields());
        assertTrue(call.exactMatch());
        assertEquals("ontologyId", call.facetFields());
        assertEquals(List.of("class"), call.properties().get("type"));
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
        mockMvc.perform(get("/api/v2/entities")
                        .param("page", Integer.toString(requestedPage))
                        .param("size", Integer.toString(requestedSize)))
                .andExpect(status().isOk());

        GlobalCall call = captureGlobalCall();
        assertEquals(expectedPage, call.pageable().getPageNumber());
        assertEquals(expectedSize, call.pageable().getPageSize());
    }

    @ParameterizedTest
    @CsvSource({"page, not-a-number", "size, not-a-number"})
    void usesGlobalPaginationDefaultsForMalformedNumericValues(String parameter, String value)
            throws Exception {
        mockMvc.perform(get("/api/v2/entities").param(parameter, value))
                .andExpect(status().isOk());

        GlobalCall call = captureGlobalCall();
        assertEquals(0, call.pageable().getPageNumber());
        assertEquals(20, call.pageable().getPageSize());
    }

    @ParameterizedTest
    @CsvSource({
            "exactMatch, not-a-boolean",
            "includeObsoleteEntities, not-a-boolean",
            "includeTotal, not-a-boolean",
            "resolveReferences, not-a-boolean",
            "manchesterSyntax, not-a-boolean"
    })
    void rejectsMalformedGlobalTypedParameters(String parameter, String value) throws Exception {
        assertStableBadRequest(get("/api/v2/entities").param(parameter, value));
    }

    @ParameterizedTest
    @CsvSource({"notARealField,asc", "iri,sideways"})
    void returnsStableErrorForUnsupportedGlobalSort(String sort) throws Exception {
        when(entityRepository.find(
                any(), any(), any(), any(), any(), any(), anyBoolean(), any(), anyMap(), any(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: " + sort));

        mockMvc.perform(get("/api/v2/entities").param("sort", sort))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported sort field: " + sort));
    }

    @Test
    void returnsDefaultOntologyEntityListContract() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/entities"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.elements[0].ontologyId").value("efo"));

        OntologyCall call = captureOntologyCall();
        assertEquals("efo", call.ontologyId());
        assertEquals(0, call.pageable().getPageNumber());
        assertEquals(20, call.pageable().getPageSize());
        assertEquals("en", call.lang());
        assertNull(call.search());
        assertNull(call.searchFields());
        assertNull(call.boostFields());
        assertNull(call.facetFields());
        assertFalse(call.exactMatch());
        assertEquals(Map.of("isObsolete", List.of("false")), call.properties());
        assertFalse(call.outputOptions().resolveReferences);
        assertFalse(call.outputOptions().manchesterSyntax);
    }

    @Test
    void bindsEveryOntologyListParameter() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/entities")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc")
                        .param("search", "liver")
                        .param("searchFields", "label definition")
                        .param("boostFields", "label^10")
                        .param("facetFields", "type")
                        .param("exactMatch", "true")
                        .param("includeObsoleteEntities", "true")
                        .param("subset", "slim", "core")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        OntologyCall call = captureOntologyCall();
        assertEquals(1, call.pageable().getPageNumber());
        assertEquals(3, call.pageable().getPageSize());
        assertEquals("iri: DESC", call.pageable().getSort().toString());
        assertEquals("liver", call.search());
        assertEquals("label definition", call.searchFields());
        assertEquals("label^10", call.boostFields());
        assertEquals("type", call.facetFields());
        assertTrue(call.exactMatch());
        assertEquals(Map.of("subset", List.of("slim", "core")), call.properties());
        assertEquals("fr", call.lang());
        assertTrue(call.outputOptions().resolveReferences);
        assertTrue(call.outputOptions().manchesterSyntax);
    }

    @Test
    void forwardsCommaSeparatedAndUriNamedOntologyDynamicProperties() throws Exception {
        String property = "http://example.org/category";

        mockMvc.perform(get("/api/v2/ontologies/efo/entities")
                        .param("subset", "slim,core")
                        .param(property, "clinical"))
                .andExpect(status().isOk());

        Map<String, Collection<String>> properties = captureOntologyCall().properties();
        assertEquals(List.of("slim,core"), properties.get("subset"));
        assertEquals(List.of("clinical"), properties.get(property));
    }

    @ParameterizedTest
    @CsvSource({
            "includeObsoleteEntities, not-a-boolean",
            "exactMatch, not-a-boolean",
            "resolveReferences, not-a-boolean",
            "manchesterSyntax, not-a-boolean"
    })
    void rejectsMalformedOntologyListTypedParameters(String parameter, String value)
            throws Exception {
        assertStableBadRequest(get("/api/v2/ontologies/efo/entities").param(parameter, value));
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20, 0, 20",
            "0, 0, 0, 20",
            "0, -1, 0, 20",
            "0, 1001, 0, 1000"
    })
    void normalizesOntologyListPaginationBoundaries(
            int requestedPage, int requestedSize, int expectedPage, int expectedSize) throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/entities")
                        .param("page", Integer.toString(requestedPage))
                        .param("size", Integer.toString(requestedSize)))
                .andExpect(status().isOk());

        OntologyCall call = captureOntologyCall();
        assertEquals(expectedPage, call.pageable().getPageNumber());
        assertEquals(expectedSize, call.pageable().getPageSize());
    }

    @ParameterizedTest
    @CsvSource({"page, not-a-number", "size, not-a-number"})
    void usesOntologyListPaginationDefaultsForMalformedNumericValues(String parameter, String value)
            throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/entities").param(parameter, value))
                .andExpect(status().isOk());

        OntologyCall call = captureOntologyCall();
        assertEquals(0, call.pageable().getPageNumber());
        assertEquals(20, call.pageable().getPageSize());
    }

    @ParameterizedTest
    @CsvSource({"notARealField,asc", "iri,sideways"})
    void returnsStableErrorForUnsupportedOntologyListSort(String sort) throws Exception {
        when(entityRepository.findByOntologyId(
                any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: " + sort));

        mockMvc.perform(get("/api/v2/ontologies/efo/entities").param("sort", sort))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported sort field: " + sort));
    }

    @Test
    void returnsStableBadRequestForInvalidOntologyListIdentifier() throws Exception {
        when(entityRepository.findByOntologyId(
                any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenThrow(new IllegalArgumentException("Invalid ontology ID: efo/unsafe"));

        mockMvc.perform(get("/api/v2/ontologies/efo%252Funsafe/entities"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid ontology ID: efo/unsafe"));
    }

    @Test
    void returnsEntityByDoubleEncodedIriWithDefaults() throws Exception {
        when(entityRepository.getByOntologyIdAndIri(any(), any(), any(), any()))
                .thenReturn(entity());

        mockMvc.perform(get(ENTITY_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ontologyId").value("efo"))
                .andExpect(jsonPath("$.iri").value(ENTITY_IRI))
                .andExpect(jsonPath("$.label").value("Liver disease"));

        EntityCall call = captureEntityCall();
        assertEquals("efo", call.ontologyId());
        assertEquals(ENTITY_IRI, call.iri());
        assertEquals("en", call.lang());
        assertFalse(call.outputOptions().resolveReferences);
        assertFalse(call.outputOptions().manchesterSyntax);
    }

    @Test
    void forwardsSingleEntityLanguageAndTransformOptions() throws Exception {
        when(entityRepository.getByOntologyIdAndIri(any(), any(), any(), any()))
                .thenReturn(entity());

        mockMvc.perform(get(ENTITY_URI)
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        EntityCall call = captureEntityCall();
        assertEquals("fr", call.lang());
        assertTrue(call.outputOptions().resolveReferences);
        assertTrue(call.outputOptions().manchesterSyntax);
    }

    @Test
    void returnsStableNotFoundContractForMissingEntity() throws Exception {
        when(entityRepository.getByOntologyIdAndIri(any(), any(), any(), any()))
                .thenReturn(null);

        mockMvc.perform(get(ENTITY_URI))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("The requested resource was not found."));
    }

    @Test
    void returnsStableBadRequestForInvalidSingleEntityIdentifier() throws Exception {
        when(entityRepository.getByOntologyIdAndIri(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid ontology ID: efo/unsafe"));

        mockMvc.perform(get(URI.create(
                        "/api/v2/ontologies/efo%252Funsafe/entities/http%253A%252F%252Fexample.org%252FEFO_0001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid ontology ID: efo/unsafe"));
    }

    @ParameterizedTest
    @CsvSource({"resolveReferences, not-a-boolean", "manchesterSyntax, not-a-boolean"})
    void rejectsMalformedSingleEntityTransformOptions(String parameter, String value)
            throws Exception {
        assertStableBadRequest(get(ENTITY_URI).param(parameter, value));
    }

    @Test
    void returnsDefaultRelatedFromContract() throws Exception {
        mockMvc.perform(get(RELATED_FROM_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri").value(ENTITY_IRI));

        RelatedCall call = captureRelatedCall();
        assertEquals("efo", call.ontologyId());
        assertEquals(ENTITY_IRI, call.iri());
        assertEquals(0, call.pageable().getPageNumber());
        assertEquals(20, call.pageable().getPageSize());
        assertEquals("en", call.lang());
        assertFalse(call.outputOptions().resolveReferences);
        assertFalse(call.outputOptions().manchesterSyntax);
    }

    @Test
    void bindsRelatedFromPaginationSortLanguageAndTransformOptions() throws Exception {
        mockMvc.perform(get(RELATED_FROM_URI)
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        RelatedCall call = captureRelatedCall();
        assertEquals(1, call.pageable().getPageNumber());
        assertEquals(3, call.pageable().getPageSize());
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

        RelatedCall call = captureRelatedCall();
        assertEquals(expectedPage, call.pageable().getPageNumber());
        assertEquals(expectedSize, call.pageable().getPageSize());
    }

    @ParameterizedTest
    @CsvSource({"page, not-a-number", "size, not-a-number"})
    void usesRelatedFromPaginationDefaultsForMalformedNumericValues(String parameter, String value)
            throws Exception {
        mockMvc.perform(get(RELATED_FROM_URI).param(parameter, value))
                .andExpect(status().isOk());

        RelatedCall call = captureRelatedCall();
        assertEquals(0, call.pageable().getPageNumber());
        assertEquals(20, call.pageable().getPageSize());
    }

    @ParameterizedTest
    @CsvSource({"notARealField,asc", "iri,sideways"})
    void returnsStableErrorForUnsupportedRelatedFromSort(String sort) throws Exception {
        when(entityRepository.getRelatedFrom(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: " + sort));

        mockMvc.perform(get(RELATED_FROM_URI).param("sort", sort))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported sort field: " + sort));
    }

    @ParameterizedTest
    @CsvSource({"resolveReferences, not-a-boolean", "manchesterSyntax, not-a-boolean"})
    void rejectsMalformedRelatedFromTransformOptions(String parameter, String value)
            throws Exception {
        assertStableBadRequest(get(RELATED_FROM_URI).param(parameter, value));
    }

    @Test
    void rejectsUnsupportedHttpMethodWithStableErrorFields() throws Exception {
        mockMvc.perform(post("/api/v2/entities"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private void assertStableBadRequest(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private GlobalCall captureGlobalCall() throws Exception {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> search = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> searchFields = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> boostFields = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> facetFields = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> exactMatch = ArgumentCaptor.forClass(Boolean.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> exclusions =
                ArgumentCaptor.forClass((Class<Collection<String>>) (Class<?>) Collection.class);
        ArgumentCaptor<Map<String, Collection<String>>> properties = mapCaptor();
        ArgumentCaptor<JsonTransformOptions> options = ArgumentCaptor.forClass(JsonTransformOptions.class);
        ArgumentCaptor<Boolean> includeTotal = ArgumentCaptor.forClass(Boolean.class);

        verify(entityRepository).find(
                pageable.capture(),
                lang.capture(),
                search.capture(),
                searchFields.capture(),
                boostFields.capture(),
                facetFields.capture(),
                exactMatch.capture(),
                exclusions.capture(),
                properties.capture(),
                options.capture(),
                includeTotal.capture());

        return new GlobalCall(
                pageable.getValue(), lang.getValue(), search.getValue(), searchFields.getValue(),
                boostFields.getValue(), facetFields.getValue(), exactMatch.getValue(),
                exclusions.getValue(), properties.getValue(), options.getValue(), includeTotal.getValue());
    }

    private OntologyCall captureOntologyCall() throws Exception {
        ArgumentCaptor<String> ontologyId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> search = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> searchFields = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> boostFields = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> facetFields = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> exactMatch = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Map<String, Collection<String>>> properties = mapCaptor();
        ArgumentCaptor<JsonTransformOptions> options = ArgumentCaptor.forClass(JsonTransformOptions.class);

        verify(entityRepository).findByOntologyId(
                ontologyId.capture(), pageable.capture(), lang.capture(), search.capture(),
                searchFields.capture(), boostFields.capture(), facetFields.capture(), exactMatch.capture(),
                properties.capture(), options.capture());

        return new OntologyCall(
                ontologyId.getValue(), pageable.getValue(), lang.getValue(), search.getValue(),
                searchFields.getValue(), boostFields.getValue(), facetFields.getValue(),
                exactMatch.getValue(), properties.getValue(), options.getValue());
    }

    private EntityCall captureEntityCall() {
        ArgumentCaptor<String> ontologyId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> iri = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonTransformOptions> options = ArgumentCaptor.forClass(JsonTransformOptions.class);
        verify(entityRepository).getByOntologyIdAndIri(
                ontologyId.capture(), iri.capture(), lang.capture(), options.capture());
        return new EntityCall(
                ontologyId.getValue(), iri.getValue(), lang.getValue(), options.getValue());
    }

    private RelatedCall captureRelatedCall() throws Exception {
        ArgumentCaptor<String> ontologyId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> iri = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonTransformOptions> options = ArgumentCaptor.forClass(JsonTransformOptions.class);
        verify(entityRepository).getRelatedFrom(
                ontologyId.capture(), iri.capture(), pageable.capture(), lang.capture(), options.capture());
        return new RelatedCall(
                ontologyId.getValue(), iri.getValue(), pageable.getValue(), lang.getValue(), options.getValue());
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Collection<String>>> mapCaptor() {
        return ArgumentCaptor.forClass(
                (Class<Map<String, Collection<String>>>) (Class<?>) Map.class);
    }

    private static OlsFacetedResultsPage<JsonElement> entityPage() {
        return new OlsFacetedResultsPage<>(
                List.of(entity()),
                Map.of("type", Map.of("class", 1L)),
                PageRequest.of(0, 20),
                1);
    }

    private static JsonElement entity() {
        return JsonParser.parseString("""
                {
                  "type":["entity","class"],
                  "ontologyId":"efo",
                  "iri":"http://example.org/EFO_0001",
                  "label":"Liver disease"
                }
                """);
    }

    private record GlobalCall(
            Pageable pageable,
            String lang,
            String search,
            String searchFields,
            String boostFields,
            String facetFields,
            boolean exactMatch,
            Collection<String> excludeOntologyIds,
            Map<String, Collection<String>> properties,
            JsonTransformOptions outputOptions,
            boolean includeTotal) {
    }

    private record OntologyCall(
            String ontologyId,
            Pageable pageable,
            String lang,
            String search,
            String searchFields,
            String boostFields,
            String facetFields,
            boolean exactMatch,
            Map<String, Collection<String>> properties,
            JsonTransformOptions outputOptions) {
    }

    private record EntityCall(
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
}
