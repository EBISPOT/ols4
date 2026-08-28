package uk.ac.ebi.spot.ols.controller.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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
import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.v1.V1IndividualRepository;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(V1IndividualController.class)
@ContextConfiguration(classes = {
        V1IndividualController.class,
        V1IndividualAssembler.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V1IndividualControllerWIT {

    private static final String INDIVIDUAL_IRI = "http://example.org/EFO_I100";
    private static final URI INDIVIDUAL_URI = URI.create(
            "/api/individuals/http%253A%252F%252Fexample.org%252FEFO_I100");
    private static final URI DEFINING_INDIVIDUAL_URI = URI.create(
            "/api/individuals/findByIdAndIsDefiningOntology/"
                    + "http%253A%252F%252Fexample.org%252FEFO_I100");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private V1IndividualRepository individualRepository;

    @MockitoBean
    private EntityLinks entityLinks;

    @BeforeEach
    void stubIndividualLists() {
        when(individualRepository.findAll(anyString(), any())).thenAnswer(invocation ->
                page(invocation.getArgument(1), individual(invocation.getArgument(0))));
        when(individualRepository.findAllByIri(anyString(), anyString(), any()))
                .thenAnswer(invocation -> page(
                        invocation.getArgument(2), individual(invocation.getArgument(1))));
        when(individualRepository.findAllByShortForm(anyString(), anyString(), any()))
                .thenAnswer(invocation -> page(
                        invocation.getArgument(2), individual(invocation.getArgument(1))));
        when(individualRepository.findAllByOboId(anyString(), anyString(), any()))
                .thenAnswer(invocation -> page(
                        invocation.getArgument(2), individual(invocation.getArgument(1))));
        when(individualRepository.findAllByIsDefiningOntology(anyString(), any()))
                .thenAnswer(invocation -> page(
                        invocation.getArgument(1), individual(invocation.getArgument(0))));
        when(individualRepository.findAllByIriAndIsDefiningOntology(
                anyString(), anyString(), any())).thenAnswer(invocation -> page(
                        invocation.getArgument(2), individual(invocation.getArgument(1))));
        when(individualRepository.findAllByShortFormAndIsDefiningOntology(
                anyString(), anyString(), any())).thenAnswer(invocation -> page(
                        invocation.getArgument(2), individual(invocation.getArgument(1))));
        when(individualRepository.findAllByOboIdAndIsDefiningOntology(
                anyString(), anyString(), any())).thenAnswer(invocation -> page(
                        invocation.getArgument(2), individual(invocation.getArgument(1))));
    }

    @Test
    void returnsDefaultIndividualListContract() throws Exception {
        mockMvc.perform(get("/api/individuals"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$._embedded.individuals[0].iri").value(INDIVIDUAL_IRI))
                .andExpect(jsonPath("$._embedded.individuals[0].label")
                        .value("Liver specimen alpha"))
                .andExpect(jsonPath("$._embedded.individuals[0].ontology_name").value("efo"))
                .andExpect(jsonPath("$._embedded.individuals[0].short_form").value("EFO_I100"))
                .andExpect(jsonPath("$._embedded.individuals[0].obo_id").value("EFO:I100"))
                .andExpect(jsonPath("$._embedded.individuals[0].lang").value("en"))
                .andExpect(jsonPath("$._embedded.individuals[0]._links.self.href")
                        .value(endsWith("/api/ontologies/efo/individuals/"
                                + "http%253A%252F%252Fexample.org%252FEFO_I100?lang=en")))
                .andExpect(jsonPath("$.page.size").value(1000))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.number").value(0));

        verify(individualRepository).findAll("en", PageRequest.of(0, 1000));
    }

    @Test
    void bindsLanguagePageSizeAndSort() throws Exception {
        mockMvc.perform(get("/api/individuals")
                        .param("lang", "fr")
                        .param("page", "2")
                        .param("size", "7")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.individuals[0].lang").value("fr"));

        verify(individualRepository).findAll(
                "fr", PageRequest.of(2, 7,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @ParameterizedTest
    @CsvSource({
            "list, -1, 20, 0, 20",
            "list, 0, 0, 0, 1000",
            "list, 0, -1, 0, 1000",
            "list, 0, 1001, 0, 1000",
            "path, -1, 20, 0, 20",
            "path, 0, 0, 0, 1000",
            "path, 0, 1001, 0, 1000",
            "defining-list, -1, 20, 0, 20",
            "defining-list, 0, -1, 0, 1000",
            "defining-list, 0, 1001, 0, 1000",
            "defining-path, -1, 20, 0, 20",
            "defining-path, 0, 0, 0, 1000",
            "defining-path, 0, 1001, 0, 1000"
    })
    void normalizesPaginationBoundaries(
            String route,
            int requestedPage,
            int requestedSize,
            int expectedPage,
            int expectedSize) throws Exception {
        mockMvc.perform(get(route(route))
                        .param("page", Integer.toString(requestedPage))
                        .param("size", Integer.toString(requestedSize)))
                .andExpect(status().isOk());

        Pageable expected = PageRequest.of(expectedPage, expectedSize);
        switch (route) {
            case "list" -> verify(individualRepository).findAll("en", expected);
            case "path" -> verify(individualRepository)
                    .findAllByIri(INDIVIDUAL_IRI, "en", expected);
            case "defining-list" -> verify(individualRepository)
                    .findAllByIsDefiningOntology("en", expected);
            case "defining-path" -> verify(individualRepository)
                    .findAllByIriAndIsDefiningOntology(INDIVIDUAL_IRI, "en", expected);
            default -> throw new IllegalArgumentException("Unknown route: " + route);
        }
    }

    @Test
    void usesDefaultsForMalformedNumericPaginationOnEveryRoute() throws Exception {
        Pageable defaults = PageRequest.of(0, 1000);

        mockMvc.perform(get("/api/individuals").param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());
        mockMvc.perform(get(INDIVIDUAL_URI).param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/individuals/findByIdAndIsDefiningOntology")
                        .param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());
        mockMvc.perform(get(DEFINING_INDIVIDUAL_URI)
                        .param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());

        verify(individualRepository).findAll("en", defaults);
        verify(individualRepository).findAllByIri(INDIVIDUAL_IRI, "en", defaults);
        verify(individualRepository).findAllByIsDefiningOntology("en", defaults);
        verify(individualRepository)
                .findAllByIriAndIsDefiningOntology(INDIVIDUAL_IRI, "en", defaults);
    }

    @Test
    void routesIriShortFormAndOboIdParameters() throws Exception {
        Pageable pageable = PageRequest.of(0, 1000);

        mockMvc.perform(get("/api/individuals").param("iri", INDIVIDUAL_IRI))
                .andExpect(status().isOk());
        verify(individualRepository).findAllByIri(INDIVIDUAL_IRI, "en", pageable);

        mockMvc.perform(get("/api/individuals").param("short_form", "EFO_I100"))
                .andExpect(status().isOk());
        verify(individualRepository).findAllByShortForm("EFO_I100", "en", pageable);

        mockMvc.perform(get("/api/individuals").param("obo_id", "EFO:I100"))
                .andExpect(status().isOk());
        verify(individualRepository).findAllByOboId("EFO:I100", "en", pageable);
    }

    @Test
    void appliesIdentifierPrecedence() throws Exception {
        Pageable pageable = PageRequest.of(0, 1000);

        mockMvc.perform(get("/api/individuals")
                        .param("iri", INDIVIDUAL_IRI)
                        .param("short_form", "ignored-short-form")
                        .param("obo_id", "IGNORED:0000"))
                .andExpect(status().isOk());

        verify(individualRepository).findAllByIri(INDIVIDUAL_IRI, "en", pageable);
        verify(individualRepository, never())
                .findAllByShortForm("ignored-short-form", "en", pageable);
        verify(individualRepository, never())
                .findAllByOboId("IGNORED:0000", "en", pageable);
    }

    @Test
    void decodesDoubleEncodedIriPathAndForwardsExplicitOptions() throws Exception {
        mockMvc.perform(get(INDIVIDUAL_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "shortForm,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.individuals[0].iri").value(INDIVIDUAL_IRI));

        verify(individualRepository).findAllByIri(
                INDIVIDUAL_IRI,
                "fr",
                PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.ASC, "shortForm"));
    }

    @Test
    void ignoresUnsupportedReservedAndDynamicStyleParametersForCompatibility() throws Exception {
        mockMvc.perform(get("/api/individuals")
                        .param("search", "specimen")
                        .param("includeObsoleteEntities", "false")
                        .param("subset", "core", "slim")
                        .param("http://example.org/category", "clinical", "policy")
                        .param("domain", "biology,information"))
                .andExpect(status().isOk());

        verify(individualRepository).findAll("en", PageRequest.of(0, 1000));
    }

    @Test
    void returnsDefaultDefiningOntologyListContract() throws Exception {
        mockMvc.perform(get("/api/individuals/findByIdAndIsDefiningOntology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.individuals[0].is_defining_ontology")
                        .value(true))
                .andExpect(jsonPath("$.page.number").value(0));

        verify(individualRepository)
                .findAllByIsDefiningOntology("en", PageRequest.of(0, 1000));
    }

    @Test
    void routesEveryDefiningOntologyIdentifierParameter() throws Exception {
        Pageable pageable = PageRequest.of(0, 1000);

        mockMvc.perform(get("/api/individuals/findByIdAndIsDefiningOntology")
                        .param("iri", INDIVIDUAL_IRI))
                .andExpect(status().isOk());
        verify(individualRepository)
                .findAllByIriAndIsDefiningOntology(INDIVIDUAL_IRI, "en", pageable);

        mockMvc.perform(get("/api/individuals/findByIdAndIsDefiningOntology")
                        .param("short_form", "EFO_I100"))
                .andExpect(status().isOk());
        verify(individualRepository).findAllByShortFormAndIsDefiningOntology(
                "EFO_I100", "en", pageable);

        mockMvc.perform(get("/api/individuals/findByIdAndIsDefiningOntology")
                        .param("obo_id", "EFO:I100"))
                .andExpect(status().isOk());
        verify(individualRepository)
                .findAllByOboIdAndIsDefiningOntology("EFO:I100", "en", pageable);
    }

    @Test
    void appliesDefiningOntologyIdentifierPrecedenceAndBindsPagination() throws Exception {
        Pageable pageable = PageRequest.of(
                1, 4, org.springframework.data.domain.Sort.Direction.DESC, "shortForm");

        mockMvc.perform(get("/api/individuals/findByIdAndIsDefiningOntology")
                        .param("iri", INDIVIDUAL_IRI)
                        .param("short_form", "ignored-short-form")
                        .param("obo_id", "IGNORED:0000")
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "4")
                        .param("sort", "shortForm,desc"))
                .andExpect(status().isOk());

        verify(individualRepository)
                .findAllByIriAndIsDefiningOntology(INDIVIDUAL_IRI, "fr", pageable);
        verify(individualRepository, never()).findAllByShortFormAndIsDefiningOntology(
                "ignored-short-form", "fr", pageable);
        verify(individualRepository, never())
                .findAllByOboIdAndIsDefiningOntology("IGNORED:0000", "fr", pageable);
    }

    @Test
    void definingOntologyPathDecodesIriAndBindsAllOptions() throws Exception {
        mockMvc.perform(get(DEFINING_INDIVIDUAL_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.individuals[0].lang").value("fr"));

        verify(individualRepository).findAllByIriAndIsDefiningOntology(
                INDIVIDUAL_IRI,
                "fr",
                PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void supportsLegacyHalMediaTypeOnEveryRoute() throws Exception {
        mockMvc.perform(get("/api/individuals").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
        mockMvc.perform(get(INDIVIDUAL_URI).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
        mockMvc.perform(get("/api/individuals/findByIdAndIsDefiningOntology")
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
        mockMvc.perform(get(DEFINING_INDIVIDUAL_URI).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
    }

    @Test
    void preservesArbitraryV1LanguageCompatibility() throws Exception {
        mockMvc.perform(get("/api/individuals").param("lang", "en_US"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$._embedded.individuals[0].lang").value("en_US"));

        verify(individualRepository).findAll("en_US", PageRequest.of(0, 1000));
    }

    @Test
    void returnsStableBadRequestFieldsForUnsupportedSort() throws Exception {
        Pageable pageable = PageRequest.of(
                0, 1000, org.springframework.data.domain.Sort.by("bad"));
        when(individualRepository.findAll("en", pageable))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: bad"));

        mockMvc.perform(get("/api/individuals").param("sort", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported sort field: bad"));
    }

    @Test
    void returnsStableLegacyNotFoundStatusAndMessage() throws Exception {
        when(individualRepository.findAll("en", PageRequest.of(0, 1000)))
                .thenThrow(new org.springframework.data.rest.webmvc.ResourceNotFoundException());

        mockMvc.perform(get("/api/individuals"))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertEquals(
                        "EntityModel not found", result.getResponse().getErrorMessage()))
                .andExpect(content().string(""));
    }

    @Test
    void rejectsUnsupportedHttpMethodWithStableErrorFields() throws Exception {
        mockMvc.perform(post("/api/individuals"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private static URI route(String route) {
        return switch (route) {
            case "list" -> URI.create("/api/individuals");
            case "path" -> INDIVIDUAL_URI;
            case "defining-list" -> URI.create(
                    "/api/individuals/findByIdAndIsDefiningOntology");
            case "defining-path" -> DEFINING_INDIVIDUAL_URI;
            default -> throw new IllegalArgumentException("Unknown route: " + route);
        };
    }

    private static OlsFacetedResultsPage<V1Individual> page(
            Pageable pageable,
            V1Individual... individuals) {
        return new OlsFacetedResultsPage<>(
                List.of(individuals), Map.of(), pageable, individuals.length);
    }

    private static V1Individual individual(String lang) {
        V1Individual individual = new V1Individual();
        individual.iri = INDIVIDUAL_IRI;
        individual.lang = lang;
        individual.label = "Liver specimen alpha";
        individual.description = new String[]{
                "An individual example assigned to the liver disease class."};
        individual.synonyms = new String[]{"Clinical liver sample"};
        individual.ontologyName = "efo";
        individual.ontologyPrefix = "EFO";
        individual.ontologyIri = "http://www.ebi.ac.uk/efo";
        individual.isObsolete = false;
        individual.isLocal = true;
        individual.hasChildren = false;
        individual.isRoot = true;
        individual.shortForm = "EFO_I100";
        individual.oboId = "EFO:I100";
        individual.inSubsets = List.of("individuals");
        individual.annotation = Map.of();
        return individual;
    }
}
