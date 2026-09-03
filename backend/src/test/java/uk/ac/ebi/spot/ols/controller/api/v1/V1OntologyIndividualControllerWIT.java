package uk.ac.ebi.spot.ols.controller.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.v1.V1IndividualRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1JsTreeRepository;
import uk.ac.ebi.spot.ols.service.PostgresClient;

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

@WebMvcTest(V1OntologyIndividualController.class)
@ContextConfiguration(classes = {
        V1OntologyIndividualController.class,
        V1IndividualAssembler.class,
        V1TermAssembler.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V1OntologyIndividualControllerWIT {

    private static final String INDIVIDUAL_IRI = "http://example.org/EFO_I100";
    private static final String CLASS_IRI = "http://example.org/EFO_0001";
    private static final URI INDIVIDUAL_URI = URI.create(
            "/api/ontologies/EFO/individuals/http%253A%252F%252Fexample.org%252FEFO_I100");
    private static final URI TYPES_URI = URI.create(INDIVIDUAL_URI + "/types");
    private static final URI ALL_TYPES_URI = URI.create(INDIVIDUAL_URI + "/alltypes");
    private static final URI JS_TREE_URI = URI.create(INDIVIDUAL_URI + "/jstree");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private V1IndividualRepository individualRepository;

    @MockitoBean
    private V1JsTreeRepository jsTreeRepository;

    @MockitoBean
    private PostgresClient postgresClient;

    @MockitoBean
    private EntityLinks entityLinks;

    @BeforeEach
    void stubResponses() {
        when(individualRepository.findAllByOntology(anyString(), anyString(), any()))
                .thenAnswer(invocation -> individualPage(
                        invocation.getArgument(2), invocation.getArgument(1)));
        when(individualRepository.findByOntologyAndIri(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> individual(invocation.getArgument(2)));
        when(individualRepository.findByOntologyAndShortForm(
                anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> individual(invocation.getArgument(1)));
        when(individualRepository.findByOntologyAndOboId(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> individual(invocation.getArgument(1)));
        when(individualRepository.getDirectTypes(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> termPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(individualRepository.getAllTypes(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> termPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(jsTreeRepository.getJsTreeForIndividual(anyString(), anyString(), anyString()))
                .thenReturn(List.of(Map.of(
                        "id", "individual-node",
                        "parent", "class-node",
                        "iri", INDIVIDUAL_IRI,
                        "text", "Liver specimen alpha",
                        "state", Map.of("selected", true),
                        "children", false,
                        "ontology_name", "efo")));
    }

    @Test
    void returnsDefaultOntologyIndividualListContract() throws Exception {
        mockMvc.perform(get("/api/ontologies/EFO/individuals"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$._embedded.individuals[0].iri").value(INDIVIDUAL_IRI))
                .andExpect(jsonPath("$._embedded.individuals[0].label")
                        .value("Liver specimen alpha"))
                .andExpect(jsonPath("$._embedded.individuals[0].ontology_name").value("efo"))
                .andExpect(jsonPath("$._embedded.individuals[0].lang").value("en"))
                .andExpect(jsonPath("$._embedded.individuals[0]._links.self.href")
                        .value(endsWith("/api/ontologies/efo/individuals/"
                                + "http%253A%252F%252Fexample.org%252FEFO_I100?lang=en")))
                .andExpect(jsonPath("$.page.size").value(1000))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.number").value(0));

        verify(individualRepository).findAllByOntology(
                "efo", "en", PageRequest.of(0, 1000));
    }

    @Test
    void bindsCollectionLanguagePageSizeAndSort() throws Exception {
        mockMvc.perform(get("/api/ontologies/EFO/individuals")
                        .param("lang", "fr")
                        .param("page", "2")
                        .param("size", "7")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.individuals[0].lang").value("fr"));

        verify(individualRepository).findAllByOntology(
                "efo", "fr", PageRequest.of(2, 7,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void routesEveryCollectionIdentifierAndAppliesPrecedence() throws Exception {
        mockMvc.perform(get("/api/ontologies/EFO/individuals")
                        .param("iri", INDIVIDUAL_IRI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.individuals[0].iri").value(INDIVIDUAL_IRI));
        verify(individualRepository).findByOntologyAndIri("efo", INDIVIDUAL_IRI, "fr");

        mockMvc.perform(get("/api/ontologies/EFO/individuals")
                        .param("short_form", "EFO_I100").param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.individuals[0].iri").value(INDIVIDUAL_IRI));
        verify(individualRepository).findByOntologyAndShortForm("efo", "fr", "EFO_I100");

        mockMvc.perform(get("/api/ontologies/EFO/individuals")
                        .param("obo_id", "EFO:I100").param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.individuals[0].iri").value(INDIVIDUAL_IRI));
        verify(individualRepository).findByOntologyAndOboId("efo", "fr", "EFO:I100");

        mockMvc.perform(get("/api/ontologies/EFO/individuals")
                        .param("iri", INDIVIDUAL_IRI)
                        .param("short_form", "ignored")
                        .param("obo_id", "IGNORED:1"))
                .andExpect(status().isOk());
        verify(individualRepository, never())
                .findByOntologyAndShortForm("efo", "en", "ignored");
        verify(individualRepository, never())
                .findByOntologyAndOboId("efo", "en", "IGNORED:1");
    }

    @Test
    void ignoresUnsupportedReservedAndDynamicStyleCollectionParameters() throws Exception {
        mockMvc.perform(get("/api/ontologies/EFO/individuals")
                        .param("search", "specimen")
                        .param("includeObsoleteEntities", "false")
                        .param("subset", "core", "slim")
                        .param("http://example.org/category", "clinical", "policy"))
                .andExpect(status().isOk());

        verify(individualRepository).findAllByOntology(
                "efo", "en", PageRequest.of(0, 1000));
    }

    @ParameterizedTest
    @CsvSource({
            "list, -1, 20, 0, 20",
            "list, 0, 0, 0, 1000",
            "list, 0, 1001, 0, 1000",
            "types, -1, 20, 0, 20",
            "types, 0, -1, 0, 1000",
            "types, 0, 1001, 0, 1000",
            "alltypes, -1, 20, 0, 20",
            "alltypes, 0, 0, 0, 1000",
            "alltypes, 0, 1001, 0, 1000"
    })
    void normalizesPaginationBoundariesAcrossPagedRoutes(
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
            case "list" -> verify(individualRepository)
                    .findAllByOntology("efo", "en", expected);
            case "types" -> verify(individualRepository)
                    .getDirectTypes("efo", INDIVIDUAL_IRI, "en", expected);
            case "alltypes" -> verify(individualRepository)
                    .getAllTypes("efo", INDIVIDUAL_IRI, "en", expected);
            default -> throw new IllegalArgumentException("Unknown route: " + route);
        }
    }

    @Test
    void usesPaginationDefaultsForMalformedNumericValuesAcrossPagedRoutes() throws Exception {
        Pageable defaults = PageRequest.of(0, 1000);

        mockMvc.perform(get("/api/ontologies/EFO/individuals")
                        .param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());
        mockMvc.perform(get(TYPES_URI).param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());
        mockMvc.perform(get(ALL_TYPES_URI).param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());

        verify(individualRepository).findAllByOntology("efo", "en", defaults);
        verify(individualRepository).getDirectTypes("efo", INDIVIDUAL_IRI, "en", defaults);
        verify(individualRepository).getAllTypes("efo", INDIVIDUAL_IRI, "en", defaults);
    }

    @Test
    void returnsDoubleEncodedIndividualWithDefaultAndExplicitLanguage() throws Exception {
        mockMvc.perform(get(INDIVIDUAL_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.iri").value(INDIVIDUAL_IRI))
                .andExpect(jsonPath("$.label").value("Liver specimen alpha"))
                .andExpect(jsonPath("$.ontology_name").value("efo"))
                .andExpect(jsonPath("$.lang").value("en"));
        verify(individualRepository).findByOntologyAndIri("efo", INDIVIDUAL_IRI, "en");

        mockMvc.perform(get(INDIVIDUAL_URI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("fr"));
        verify(individualRepository).findByOntologyAndIri("efo", INDIVIDUAL_IRI, "fr");
    }

    @Test
    void returnsDirectTypesWithDefaultsAndExplicitOptions() throws Exception {
        mockMvc.perform(get(TYPES_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(CLASS_IRI))
                .andExpect(jsonPath("$._embedded.terms[0].label").value("Liver disease"))
                .andExpect(jsonPath("$.page.size").value(1000));

        mockMvc.perform(get(TYPES_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("fr"));

        verify(individualRepository).getDirectTypes(
                "efo", INDIVIDUAL_IRI, "fr", PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void returnsAllTypesWithDefaultsAndExplicitOptions() throws Exception {
        mockMvc.perform(get(ALL_TYPES_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(CLASS_IRI))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        mockMvc.perform(get(ALL_TYPES_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "shortForm,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("fr"));

        verify(individualRepository).getAllTypes(
                "efo", INDIVIDUAL_IRI, "fr", PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.ASC, "shortForm"));
    }

    @Test
    void returnsDoubleEncodedIndividualJsTreeWithExplicitLanguage() throws Exception {
        mockMvc.perform(get(JS_TREE_URI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].iri").value(INDIVIDUAL_IRI))
                .andExpect(jsonPath("$[0].text").value("Liver specimen alpha"))
                .andExpect(jsonPath("$[0].state.selected").value(true));

        verify(jsTreeRepository).getJsTreeForIndividual(INDIVIDUAL_IRI, "efo", "fr");
    }

    @Test
    void preservesArbitraryV1LanguageCompatibilityAcrossRouteFamilies() throws Exception {
        mockMvc.perform(get("/api/ontologies/EFO/individuals").param("lang", "en_US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.individuals[0].lang").value("en_US"));
        mockMvc.perform(get(TYPES_URI).param("lang", "en_US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("en_US"));
        mockMvc.perform(get(JS_TREE_URI).param("lang", "en_US"))
                .andExpect(status().isOk());

        verify(individualRepository).findAllByOntology(
                "efo", "en_US", PageRequest.of(0, 1000));
        verify(individualRepository).getDirectTypes(
                "efo", INDIVIDUAL_IRI, "en_US", PageRequest.of(0, 1000));
        verify(jsTreeRepository).getJsTreeForIndividual(INDIVIDUAL_IRI, "efo", "en_US");
    }

    @Test
    void supportsLegacyHalMediaTypeOnEveryHalRoute() throws Exception {
        mockMvc.perform(get("/api/ontologies/EFO/individuals").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
        mockMvc.perform(get(INDIVIDUAL_URI).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
        mockMvc.perform(get(TYPES_URI).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
        mockMvc.perform(get(ALL_TYPES_URI).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
    }

    @Test
    void returnsStableLegacyNotFoundStatusAndMessage() throws Exception {
        when(individualRepository.findByOntologyAndIri("efo", INDIVIDUAL_IRI, "en"))
                .thenReturn(null);

        mockMvc.perform(get(INDIVIDUAL_URI))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertEquals(
                        "EntityModel not found", result.getResponse().getErrorMessage()))
                .andExpect(content().string(""));
    }

    @Test
    void returnsStableBadRequestFieldsForUnsupportedSort() throws Exception {
        Pageable pageable = PageRequest.of(
                0, 1000, org.springframework.data.domain.Sort.by("bad"));
        when(individualRepository.getDirectTypes("efo", INDIVIDUAL_IRI, "en", pageable))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: bad"));

        mockMvc.perform(get(TYPES_URI).param("sort", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported sort field: bad"));
    }

    @Test
    void rejectsUnsupportedHttpMethodWithStableErrorFields() throws Exception {
        mockMvc.perform(post("/api/ontologies/EFO/individuals"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private static URI route(String route) {
        return switch (route) {
            case "list" -> URI.create("/api/ontologies/EFO/individuals");
            case "types" -> TYPES_URI;
            case "alltypes" -> ALL_TYPES_URI;
            default -> throw new IllegalArgumentException("Unknown route: " + route);
        };
    }

    private static OlsFacetedResultsPage<V1Individual> individualPage(
            Pageable pageable,
            String lang) {
        return new OlsFacetedResultsPage<>(
                List.of(individual(lang)), Map.of(), pageable, 1);
    }

    private static PageImpl<V1Term> termPage(Pageable pageable, String lang) {
        return new PageImpl<>(List.of(term(lang)), pageable, 1);
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

    private static V1Term term(String lang) {
        V1Term term = new V1Term();
        term.iri = CLASS_IRI;
        term.lang = lang;
        term.label = "Liver disease";
        term.description = new String[]{"A disorder affecting hepatic tissue."};
        term.synonyms = new String[]{"Hepatic disorder"};
        term.ontologyName = "efo";
        term.ontologyPrefix = "EFO";
        term.ontologyIri = "http://www.ebi.ac.uk/efo";
        term.shortForm = "EFO_0001";
        term.oboId = "EFO:0001";
        term.inSubsets = List.of();
        term.annotation = Map.of();
        term.oboDefinitionCitations = List.of();
        term.oboXrefs = List.of();
        term.oboSynonyms = List.of();
        term.related = List.of();
        return term;
    }
}
