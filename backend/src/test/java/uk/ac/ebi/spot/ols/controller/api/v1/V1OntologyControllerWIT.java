package uk.ac.ebi.spot.ols.controller.api.v1;

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
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.ols.config.WebConfig;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.model.v1.V1OntologyConfig;
import uk.ac.ebi.spot.ols.repository.v1.V1OntologyRepository;

import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(V1OntologyController.class)
@ContextConfiguration(classes = {
        V1OntologyController.class,
        V1OntologyAssembler.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V1OntologyControllerWIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private V1OntologyRepository ontologyRepository;

    @MockitoBean
    private V1TermAssembler termAssembler;

    @MockitoBean
    private EntityLinks entityLinks;

    @BeforeEach
    void stubOntologyList() {
        when(ontologyRepository.getAll(anyString(), any()))
                .thenReturn(new PageImpl<>(
                        List.of(ontology("efo", "en", "Experimental Factor Ontology")),
                        PageRequest.of(0, 20),
                        1));
    }

    @Test
    void returnsDefaultOntologyListContract() throws Exception {
        mockMvc.perform(get("/api/ontologies"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$._embedded.ontologies[0].ontologyId").value("efo"))
                .andExpect(jsonPath("$._embedded.ontologies[0].lang").value("en"))
                .andExpect(jsonPath("$._embedded.ontologies[0].status").value("LOADED"))
                .andExpect(jsonPath("$._embedded.ontologies[0].config.title")
                        .value("Experimental Factor Ontology"))
                .andExpect(jsonPath("$._embedded.ontologies[0]._links.self.href")
                        .value(endsWith("/api/ontologies/efo?lang=en")))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.number").value(0));

        ListCall call = captureListCall();
        assertEquals("en", call.lang());
        assertEquals(0, call.pageable().getPageNumber());
        assertEquals(20, call.pageable().getPageSize());
        assertTrue(call.pageable().getSort().isUnsorted());
    }

    @Test
    void bindsLanguagePageAndSize() throws Exception {
        mockMvc.perform(get("/api/ontologies")
                        .param("lang", "fr")
                        .param("page", "2")
                        .param("size", "7"))
                .andExpect(status().isOk());

        ListCall call = captureListCall();
        assertEquals("fr", call.lang());
        assertEquals(2, call.pageable().getPageNumber());
        assertEquals(7, call.pageable().getPageSize());
    }

    @ParameterizedTest
    @CsvSource({
            "asc, ASC",
            "desc, DESC"
    })
    void bindsSortDirection(String requestedDirection, String expectedDirection) throws Exception {
        mockMvc.perform(get("/api/ontologies")
                        .param("sort", "ontologyId," + requestedDirection))
                .andExpect(status().isOk());

        assertEquals(
                "ontologyId: " + expectedDirection,
                captureListCall().pageable().getSort().toString());
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20, 0, 20",
            "0, 0, 0, 20",
            "0, -1, 0, 20",
            "0, 1001, 0, 1000"
    })
    void preservesPaginationBoundaryCompatibility(
            int requestedPage,
            int requestedSize,
            int expectedPage,
            int expectedSize) throws Exception {
        mockMvc.perform(get("/api/ontologies")
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
    void usesPaginationDefaultsForMalformedNumericValues(String parameter, String value)
            throws Exception {
        mockMvc.perform(get("/api/ontologies").param(parameter, value))
                .andExpect(status().isOk());

        ListCall call = captureListCall();
        assertEquals(0, call.pageable().getPageNumber());
        assertEquals(20, call.pageable().getPageSize());
    }

    @Test
    void ignoresUnrelatedReservedAndDynamicStyleParametersForBackwardCompatibility()
            throws Exception {
        mockMvc.perform(get("/api/ontologies")
                        .param("search", "factor")
                        .param("domain", "biology", "health")
                        .param("tags", "biomedical,clinical")
                        .param("http://example.org/category", "curated")
                        .param("includeObsoleteEntities", "false"))
                .andExpect(status().isOk());

        ListCall call = captureListCall();
        assertEquals("en", call.lang());
        assertEquals(0, call.pageable().getPageNumber());
        assertEquals(20, call.pageable().getPageSize());
        assertTrue(call.pageable().getSort().isUnsorted());
    }

    @Test
    void supportsTheLegacyHalMediaType() throws Exception {
        mockMvc.perform(get("/api/ontologies").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
    }

    @Test
    void returnsOntologyByCaseInsensitiveIdWithDefaultLanguage() throws Exception {
        V1Ontology ontology = ontology("efo", "en", "Experimental Factor Ontology");
        when(ontologyRepository.get("efo", "en")).thenReturn(ontology);

        mockMvc.perform(get("/api/ontologies/EFO"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ontologyId").value("efo"))
                .andExpect(jsonPath("$.lang").value("en"))
                .andExpect(jsonPath("$.status").value("LOADED"))
                .andExpect(jsonPath("$.config.title").value("Experimental Factor Ontology"))
                .andExpect(jsonPath("$._links.terms.href")
                        .value(endsWith("/api/ontologies/efo/terms")));

        verify(ontologyRepository).get("efo", "en");
    }

    @Test
    void forwardsExplicitLanguageForSingleOntology() throws Exception {
        V1Ontology ontology = ontology("efo", "fr", "Ontologie des facteurs expérimentaux");
        when(ontologyRepository.get("efo", "fr")).thenReturn(ontology);

        mockMvc.perform(get("/api/ontologies/efo").param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("fr"));

        verify(ontologyRepository).get("efo", "fr");
    }

    @Test
    void returnsStableLegacyNotFoundStatusAndMessage() throws Exception {
        when(ontologyRepository.get("missing", "en")).thenReturn(null);

        mockMvc.perform(get("/api/ontologies/MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertEquals(
                        "EntityModel not found",
                        result.getResponse().getErrorMessage()));
    }

    @Test
    void returnsStableBadRequestFieldsForInvalidRepositoryInput() throws Exception {
        when(ontologyRepository.get("efo", "en_US"))
                .thenThrow(new IllegalArgumentException("Invalid language: en_US"));

        mockMvc.perform(get("/api/ontologies/efo").param("lang", "en_US"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid language: en_US"));
    }

    @Test
    void rejectsUnsupportedHttpMethodWithStableErrorFields() throws Exception {
        mockMvc.perform(post("/api/ontologies"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private ListCall captureListCall() {
        ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(ontologyRepository).getAll(lang.capture(), pageable.capture());
        return new ListCall(lang.getValue(), pageable.getValue());
    }

    private static V1Ontology ontology(String ontologyId, String lang, String title) {
        V1Ontology ontology = new V1Ontology();
        ontology.ontologyId = ontologyId;
        ontology.lang = lang;
        ontology.languages = List.of("en", "fr");
        ontology.status = "LOADED";
        ontology.message = "";
        ontology.loaded = "2026-08-24T00:00:00Z";
        ontology.updated = ontology.loaded;
        ontology.numberOfTerms = 42;
        ontology.numberOfProperties = 7;
        ontology.numberOfIndividuals = 0;
        ontology.config = new V1OntologyConfig();
        ontology.config.id = ontologyId;
        ontology.config.namespace = ontologyId;
        ontology.config.preferredPrefix = ontologyId.toUpperCase();
        ontology.config.title = title;
        ontology.config.baseUris = List.of();
        ontology.config.definitionProperties = List.of();
        ontology.config.synonymProperties = List.of();
        ontology.config.hierarchicalProperties = List.of();
        ontology.config.hiddenProperties = List.of();
        return ontology;
    }

    private record ListCall(String lang, Pageable pageable) {
    }
}
