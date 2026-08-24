package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.config.WebConfig;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.repository.OntologyRepository;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import java.util.Collection;
import java.util.List;
import java.util.Map;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.ArgumentCaptor;

@WebMvcTest(V2OntologyController.class)
@ContextConfiguration(classes = {
        V2OntologyController.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V2OntologyControllerWIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OntologyRepository ontologyRepository;

    @BeforeEach
    void stubListResponse() throws Exception {
        when(ontologyRepository.find(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyBoolean(),
                anyMap(),
                any()))
                .thenReturn(ontologyPage());
    }

    @Test
    void returnsDefaultOntologyListContract() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].ontologyId").value("efo-test"))
                .andExpect(jsonPath("$.elements[0].title").value("Experimental Factor Ontology"))
                .andExpect(jsonPath("$.facetFieldsToCounts.domain.biology").value(1));

        ListCall call = captureListCall();
        assertEquals(0, call.pageable().getPageNumber());
        assertEquals(20, call.pageable().getPageSize());
        assertEquals("en", call.lang());
        assertNull(call.search());
        assertNull(call.searchFields());
        assertNull(call.boostFields());
        assertFalse(call.exactMatch());
        assertEquals(Map.of("isObsolete", List.of("false")), call.properties());
        assertFalse(call.outputOptions().resolveReferences);
        assertFalse(call.outputOptions().manchesterSyntax);
    }

    @Test
    void bindsPageAndSize() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies").param("page", "2").param("size", "7"))
                .andExpect(status().isOk());

        ListCall call = captureListCall();
        assertEquals(2, call.pageable().getPageNumber());
        assertEquals(7, call.pageable().getPageSize());
    }

    @Test
    void bindsSortDirection() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies").param("sort", "ontologyId,desc"))
                .andExpect(status().isOk());

        assertEquals(
                "ontologyId: DESC",
                captureListCall().pageable().getSort().toString());
    }

    @Test
    void bindsSearch() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies").param("search", "factor"))
                .andExpect(status().isOk());

        assertEquals("factor", captureListCall().search());
    }

    @Test
    void bindsSearchFields() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies").param("searchFields", "title ontologyId"))
                .andExpect(status().isOk());

        assertEquals("title ontologyId", captureListCall().searchFields());
    }

    @Test
    void bindsBoostFields() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies").param("boostFields", "title^10 ontologyId^5"))
                .andExpect(status().isOk());

        assertEquals("title^10 ontologyId^5", captureListCall().boostFields());
    }

    @Test
    void bindsExactMatch() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies").param("exactMatch", "true"))
                .andExpect(status().isOk());

        assertTrue(captureListCall().exactMatch());
    }

    @Test
    void bindsExplicitFalseExactMatch() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies").param("exactMatch", "false"))
                .andExpect(status().isOk());

        assertFalse(captureListCall().exactMatch());
    }

    @Test
    void includesObsoleteOntologiesWhenRequested() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies").param("includeObsoleteEntities", "true"))
                .andExpect(status().isOk());

        assertFalse(captureListCall().properties().containsKey("isObsolete"));
    }

    @Test
    void excludesObsoleteOntologiesWhenExplicitlyRequested() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies").param("includeObsoleteEntities", "false"))
                .andExpect(status().isOk());

        assertEquals(List.of("false"), captureListCall().properties().get("isObsolete"));
    }

    @Test
    void bindsLanguage() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies").param("lang", "fr"))
                .andExpect(status().isOk());

        assertEquals("fr", captureListCall().lang());
    }

    @Test
    void bindsTransformOptions() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk());

        JsonTransformOptions options = captureListCall().outputOptions();
        assertTrue(options.resolveReferences);
        assertTrue(options.manchesterSyntax);
    }

    @Test
    void forwardsRepeatedDynamicPropertyValues() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies").param("domain", "biology", "health"))
                .andExpect(status().isOk());

        assertEquals(List.of("biology", "health"), captureListCall().properties().get("domain"));
    }

    @Test
    void forwardsCommaSeparatedDynamicPropertyValues() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies").param("domain", "biology,health"))
                .andExpect(status().isOk());

        assertEquals(List.of("biology,health"), captureListCall().properties().get("domain"));
    }

    @Test
    void forwardsUriNamedDynamicProperty() throws Exception {
        String property = "http://example.org/category";

        mockMvc.perform(get("/api/v2/ontologies").param(property, "experimental"))
                .andExpect(status().isOk());

        assertEquals(List.of("experimental"), captureListCall().properties().get(property));
    }

    @Test
    void reservedParametersDoNotBecomeDynamicProperties() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies")
                        .param("page", "1")
                        .param("size", "5")
                        .param("search", "factor")
                        .param("searchFields", "title")
                        .param("boostFields", "title^10")
                        .param("exactMatch", "true")
                        .param("includeObsoleteEntities", "true")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true")
                        .param("model", "test")
                        .param("includeTotal", "false")
                        .param("excludeOntologyId", "other"))
                .andExpect(status().isOk());

        assertTrue(captureListCall().properties().isEmpty());
    }

    @Test
    void combinesSearchFieldsAndExactMatch() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies")
                        .param("search", "Experimental Factor Ontology")
                        .param("searchFields", "title")
                        .param("exactMatch", "true"))
                .andExpect(status().isOk());

        ListCall call = captureListCall();
        assertEquals("Experimental Factor Ontology", call.search());
        assertEquals("title", call.searchFields());
        assertTrue(call.exactMatch());
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20, 0, 20",
            "0, 0, 0, 20",
            "0, 1001, 0, 1000"
    })
    void normalizesPaginationBoundaries(
            int requestedPage,
            int requestedSize,
            int expectedPage,
            int expectedSize) throws Exception {
        mockMvc.perform(get("/api/v2/ontologies")
                        .param("page", Integer.toString(requestedPage))
                        .param("size", Integer.toString(requestedSize)))
                .andExpect(status().isOk());

        ListCall call = captureListCall();
        assertEquals(expectedPage, call.pageable().getPageNumber());
        assertEquals(expectedSize, call.pageable().getPageSize());
    }

    @ParameterizedTest
    @CsvSource({
            "page, not-a-number",
            "size, not-a-number"
    })
    void usesPaginationDefaultsForNonNumericValues(String parameter, String value) throws Exception {
        mockMvc.perform(get("/api/v2/ontologies").param(parameter, value))
                .andExpect(status().isOk());

        ListCall call = captureListCall();
        assertEquals(0, call.pageable().getPageNumber());
        assertEquals(20, call.pageable().getPageSize());
    }

    @Test
    void returnsOntologiesGroupedByTag() throws Exception {
        when(ontologyRepository.getGroupedByField(any(), any(), any()))
                .thenReturn(Map.of("experimental", List.of(entity("efo-test"))));

        mockMvc.perform(get("/api/v2/ontologies/by-tag"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experimental[0].ontologyId").value("efo-test"));

        verify(ontologyRepository).getGroupedByField("tags", "en", new JsonTransformOptionsMatcher(false, false));
    }

    @Test
    void returnsOntologiesGroupedByDomainWithOptions() throws Exception {
        when(ontologyRepository.getGroupedByField(any(), any(), any()))
                .thenReturn(Map.of("biology", List.of(entity("efo-test"))));

        mockMvc.perform(get("/api/v2/ontologies/by-domain")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biology[0].ontologyId").value("efo-test"));

        verify(ontologyRepository).getGroupedByField("domain", "fr", new JsonTransformOptionsMatcher(true, true));
    }

    @Test
    void returnsOntologyById() throws Exception {
        when(ontologyRepository.getById(any(), any(), any())).thenReturn(entity("efo-test"));

        mockMvc.perform(get("/api/v2/ontologies/efo-test")
                        .param("lang", "fr")
                        .param("resolveReferences", "true")
                        .param("manchesterSyntax", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ontologyId").value("efo-test"));

        verify(ontologyRepository).getById("efo-test", "fr", new JsonTransformOptionsMatcher(true, true));
    }

    @Test
    void returnsStableNotFoundContract() throws Exception {
        when(ontologyRepository.getById(any(), any(), any())).thenReturn(null);

        mockMvc.perform(get("/api/v2/ontologies/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("The requested resource was not found."));
    }

    @Test
    void rejectsUnsupportedHttpMethod() throws Exception {
        mockMvc.perform(post("/api/v2/ontologies"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "exactMatch, not-a-boolean",
            "includeObsoleteEntities, not-a-boolean"
    })
    void rejectsMalformedTypedParameters(String parameter, String value) throws Exception {
        mockMvc.perform(get("/api/v2/ontologies").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v2/ontologies/by-tag, resolveReferences",
            "/api/v2/ontologies/by-domain, manchesterSyntax",
            "/api/v2/ontologies/efo-test, resolveReferences"
    })
    void rejectsMalformedTransformOptions(String path, String parameter) throws Exception {
        mockMvc.perform(get(path).param(parameter, "not-a-boolean"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsUnsupportedSortFieldWithStableErrorContract() throws Exception {
        when(ontologyRepository.find(
                any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: notARealField"));

        mockMvc.perform(get("/api/v2/ontologies").param("sort", "notARealField,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported sort field: notARealField"));
    }

    private ListCall captureListCall() throws Exception {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> search = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> searchFields = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> boostFields = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> exactMatch = ArgumentCaptor.forClass(Boolean.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Collection<String>>> properties =
                ArgumentCaptor.forClass((Class<Map<String, Collection<String>>>) (Class<?>) Map.class);
        ArgumentCaptor<JsonTransformOptions> outputOptions = ArgumentCaptor.forClass(JsonTransformOptions.class);

        verify(ontologyRepository).find(
                pageable.capture(),
                lang.capture(),
                search.capture(),
                searchFields.capture(),
                boostFields.capture(),
                exactMatch.capture(),
                properties.capture(),
                outputOptions.capture());

        return new ListCall(
                pageable.getValue(),
                lang.getValue(),
                search.getValue(),
                searchFields.getValue(),
                boostFields.getValue(),
                exactMatch.getValue(),
                properties.getValue(),
                outputOptions.getValue());
    }

    private static OlsFacetedResultsPage<com.google.gson.JsonElement> ontologyPage() {
        return new OlsFacetedResultsPage<>(
                List.of(JsonParser.parseString("""
                        {"ontologyId":"efo-test","title":"Experimental Factor Ontology"}
                        """)),
                Map.of("domain", Map.of("biology", 1L)),
                PageRequest.of(0, 20),
                1);
    }

    private static V2Entity entity(String ontologyId) {
        return new V2Entity(JsonParser.parseString("{\"ontologyId\":\"" + ontologyId + "\"}"));
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

    private static class JsonTransformOptionsMatcher extends JsonTransformOptions {
        private final boolean expectedResolveReferences;
        private final boolean expectedManchesterSyntax;

        private JsonTransformOptionsMatcher(boolean resolveReferences, boolean manchesterSyntax) {
            this.expectedResolveReferences = resolveReferences;
            this.expectedManchesterSyntax = manchesterSyntax;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof JsonTransformOptions options
                    && options.resolveReferences == expectedResolveReferences
                    && options.manchesterSyntax == expectedManchesterSyntax;
        }
    }
}
