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
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.v1.V1TermRepository;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

@WebMvcTest(V1TermController.class)
@ContextConfiguration(classes = {
        V1TermController.class,
        V1TermAssembler.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V1TermControllerWIT {

    private static final String TERM_IRI = "http://example.org/EFO_0001";
    private static final URI TERM_URI = URI.create(
            "/api/terms/http%253A%252F%252Fexample.org%252FEFO_0001");
    private static final URI DEFINING_TERM_URI = URI.create(
            "/api/terms/findByIdAndIsDefiningOntology/"
                    + "http%253A%252F%252Fexample.org%252FEFO_0001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private V1TermRepository termRepository;

    @MockitoBean
    private EntityLinks entityLinks;

    @BeforeEach
    void stubTermLists() {
        when(termRepository.findAll(anyString(), any())).thenAnswer(invocation ->
                page(invocation.getArgument(1), term(invocation.getArgument(0))));
        when(termRepository.findAllByIri(anyString(), anyString(), any())).thenAnswer(invocation ->
                page(invocation.getArgument(2), term(invocation.getArgument(1))));
        when(termRepository.findAllByShortForm(anyString(), anyString(), any())).thenAnswer(invocation ->
                page(invocation.getArgument(2), term(invocation.getArgument(1))));
        when(termRepository.findAllByOboId(anyString(), anyString(), any())).thenAnswer(invocation ->
                page(invocation.getArgument(2), term(invocation.getArgument(1))));
        when(termRepository.findAllByIsDefiningOntology(anyString(), any())).thenAnswer(invocation ->
                page(invocation.getArgument(1), term(invocation.getArgument(0))));
        when(termRepository.findAllByIriAndIsDefiningOntology(anyString(), anyString(), any()))
                .thenAnswer(invocation -> page(
                        invocation.getArgument(2), term(invocation.getArgument(1))));
        when(termRepository.findAllByShortFormAndIsDefiningOntology(
                anyString(), anyString(), any())).thenAnswer(invocation -> page(
                        invocation.getArgument(2), term(invocation.getArgument(1))));
        when(termRepository.findAllByOboIdAndIsDefiningOntology(anyString(), anyString(), any()))
                .thenAnswer(invocation -> page(
                        invocation.getArgument(2), term(invocation.getArgument(1))));
    }

    @Test
    void returnsDefaultTermListContract() throws Exception {
        mockMvc.perform(get("/api/terms"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(TERM_IRI))
                .andExpect(jsonPath("$._embedded.terms[0].label").value("Liver disease"))
                .andExpect(jsonPath("$._embedded.terms[0].ontology_name").value("efo"))
                .andExpect(jsonPath("$._embedded.terms[0].short_form").value("EFO_0001"))
                .andExpect(jsonPath("$._embedded.terms[0].obo_id").value("EFO:0001"))
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("en"))
                .andExpect(jsonPath("$._embedded.terms[0]._links.self.href")
                        .value(endsWith("/api/ontologies/efo/terms/"
                                + "http%253A%252F%252Fexample.org%252FEFO_0001?lang=en")))
                .andExpect(jsonPath("$.page.size").value(1000))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.number").value(0));

        verify(termRepository).findAll("en", PageRequest.of(0, 1000));
    }

    @Test
    void bindsLanguagePageSizeAndSort() throws Exception {
        mockMvc.perform(get("/api/terms")
                        .param("lang", "fr")
                        .param("page", "2")
                        .param("size", "7")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("fr"));

        verify(termRepository).findAll(
                "fr", PageRequest.of(2, 7, org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20, 0, 20",
            "0, 0, 0, 1000",
            "0, -1, 0, 1000",
            "0, 1001, 0, 1000"
    })
    void preservesPaginationBoundaryCompatibility(
            int requestedPage,
            int requestedSize,
            int expectedPage,
            int expectedSize) throws Exception {
        mockMvc.perform(get("/api/terms")
                        .param("page", Integer.toString(requestedPage))
                        .param("size", Integer.toString(requestedSize)))
                .andExpect(status().isOk());

        verify(termRepository).findAll("en", PageRequest.of(expectedPage, expectedSize));
    }

    @ParameterizedTest
    @CsvSource({"page, not-a-number", "size, not-a-number"})
    void usesPaginationDefaultsForMalformedNumericValues(String parameter, String value)
            throws Exception {
        mockMvc.perform(get("/api/terms").param(parameter, value))
                .andExpect(status().isOk());

        verify(termRepository).findAll("en", PageRequest.of(0, 1000));
    }

    @Test
    void normalizesPaginationBoundariesAcrossEveryRoute() throws Exception {
        Pageable normalized = PageRequest.of(0, 1000);

        mockMvc.perform(get("/api/terms").param("page", "-1").param("size", "1001"))
                .andExpect(status().isOk());
        mockMvc.perform(get(TERM_URI).param("page", "-1").param("size", "1001"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/terms/findByIdAndIsDefiningOntology")
                        .param("page", "-1").param("size", "1001"))
                .andExpect(status().isOk());
        mockMvc.perform(get(DEFINING_TERM_URI)
                        .param("page", "-1").param("size", "1001"))
                .andExpect(status().isOk());

        verify(termRepository).findAll("en", normalized);
        verify(termRepository).findAllByIri(TERM_IRI, "en", normalized);
        verify(termRepository).findAllByIsDefiningOntology("en", normalized);
        verify(termRepository).findAllByIriAndIsDefiningOntology(TERM_IRI, "en", normalized);
    }

    @Test
    void usesMalformedPaginationDefaultsAcrossEveryPathShape() throws Exception {
        Pageable defaults = PageRequest.of(0, 1000);

        mockMvc.perform(get(TERM_URI).param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/terms/findByIdAndIsDefiningOntology")
                        .param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());
        mockMvc.perform(get(DEFINING_TERM_URI).param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());

        verify(termRepository).findAllByIri(TERM_IRI, "en", defaults);
        verify(termRepository).findAllByIsDefiningOntology("en", defaults);
        verify(termRepository).findAllByIriAndIsDefiningOntology(TERM_IRI, "en", defaults);
    }

    @Test
    void routesIriShortFormAndOboIdParameters() throws Exception {
        mockMvc.perform(get("/api/terms").param("iri", TERM_IRI))
                .andExpect(status().isOk());
        verify(termRepository).findAllByIri(TERM_IRI, "en", PageRequest.of(0, 1000));

        mockMvc.perform(get("/api/terms").param("short_form", "EFO_0001"))
                .andExpect(status().isOk());
        verify(termRepository).findAllByShortForm("EFO_0001", "en", PageRequest.of(0, 1000));

        mockMvc.perform(get("/api/terms").param("obo_id", "EFO:0001"))
                .andExpect(status().isOk());
        verify(termRepository).findAllByOboId("EFO:0001", "en", PageRequest.of(0, 1000));
    }

    @Test
    void genericIdOverridesSpecificIdentifiersAndFallsBackByRepresentation() throws Exception {
        Pageable pageable = PageRequest.of(0, 1000);
        when(termRepository.findAllByIri("EFO:0001", "en", pageable))
                .thenReturn(page(pageable));
        when(termRepository.findAllByShortForm("EFO:0001", "en", pageable))
                .thenReturn(page(pageable));

        mockMvc.perform(get("/api/terms")
                        .param("iri", "ignored-iri")
                        .param("short_form", "ignored-short-form")
                        .param("obo_id", "ignored-obo-id")
                        .param("id", "EFO:0001"))
                .andExpect(status().isOk());

        verify(termRepository).findAllByIri("EFO:0001", "en", pageable);
        verify(termRepository).findAllByShortForm("EFO:0001", "en", pageable);
        verify(termRepository).findAllByOboId("EFO:0001", "en", pageable);
        verify(termRepository, never()).findAllByIri("ignored-iri", "en", pageable);
    }

    @Test
    void decodesDoubleEncodedIriPathAndForwardsExplicitOptions() throws Exception {
        mockMvc.perform(get(TERM_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "shortForm,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(TERM_IRI));

        verify(termRepository).findAllByIri(
                TERM_IRI,
                "fr",
                PageRequest.of(1, 3, org.springframework.data.domain.Sort.Direction.ASC, "shortForm"));
    }

    @Test
    void ignoresUnsupportedReservedAndDynamicStyleParametersForCompatibility() throws Exception {
        mockMvc.perform(get("/api/terms")
                        .param("search", "liver")
                        .param("includeObsoleteEntities", "false")
                        .param("subset", "core", "slim")
                        .param("http://example.org/category", "clinical"))
                .andExpect(status().isOk());

        verify(termRepository).findAll("en", PageRequest.of(0, 1000));
    }

    @Test
    void returnsDefaultDefiningOntologyListContract() throws Exception {
        mockMvc.perform(get("/api/terms/findByIdAndIsDefiningOntology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].is_defining_ontology").value(true))
                .andExpect(jsonPath("$.page.number").value(0));

        verify(termRepository).findAllByIsDefiningOntology("en", PageRequest.of(0, 1000));
    }

    @Test
    void routesEveryDefiningOntologyIdentifierParameter() throws Exception {
        Pageable pageable = PageRequest.of(0, 1000);

        mockMvc.perform(get("/api/terms/findByIdAndIsDefiningOntology")
                        .param("iri", TERM_IRI))
                .andExpect(status().isOk());
        verify(termRepository).findAllByIriAndIsDefiningOntology(TERM_IRI, "en", pageable);

        mockMvc.perform(get("/api/terms/findByIdAndIsDefiningOntology")
                        .param("short_form", "EFO_0001"))
                .andExpect(status().isOk());
        verify(termRepository).findAllByShortFormAndIsDefiningOntology(
                "EFO_0001", "en", pageable);

        mockMvc.perform(get("/api/terms/findByIdAndIsDefiningOntology")
                        .param("obo_id", "EFO:0001"))
                .andExpect(status().isOk());
        verify(termRepository).findAllByOboIdAndIsDefiningOntology("EFO:0001", "en", pageable);
    }

    @Test
    void definingOntologyGenericIdFallsBackAndBindsPagination() throws Exception {
        Pageable pageable = PageRequest.of(
                1, 4, org.springframework.data.domain.Sort.Direction.DESC, "shortForm");
        when(termRepository.findAllByIriAndIsDefiningOntology("EFO:0001", "fr", pageable))
                .thenReturn(page(pageable));
        when(termRepository.findAllByShortFormAndIsDefiningOntology(
                "EFO:0001", "fr", pageable)).thenReturn(page(pageable));

        mockMvc.perform(get("/api/terms/findByIdAndIsDefiningOntology")
                        .param("id", "EFO:0001")
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "4")
                        .param("sort", "shortForm,desc"))
                .andExpect(status().isOk());

        verify(termRepository).findAllByIriAndIsDefiningOntology("EFO:0001", "fr", pageable);
        verify(termRepository).findAllByShortFormAndIsDefiningOntology("EFO:0001", "fr", pageable);
        verify(termRepository).findAllByOboIdAndIsDefiningOntology("EFO:0001", "fr", pageable);
    }

    @Test
    void definingOntologyPathDecodesIriAndBindsAllOptions() throws Exception {
        mockMvc.perform(get(DEFINING_TERM_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("fr"));

        verify(termRepository).findAllByIriAndIsDefiningOntology(
                TERM_IRI,
                "fr",
                PageRequest.of(1, 3, org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void supportsLegacyHalMediaTypeOnEveryRouteFamily() throws Exception {
        mockMvc.perform(get("/api/terms").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
        mockMvc.perform(get(TERM_URI).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
        mockMvc.perform(get("/api/terms/findByIdAndIsDefiningOntology")
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
        mockMvc.perform(get(DEFINING_TERM_URI).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
    }

    @Test
    void returnsStableLegacyNotFoundStatusAndMessage() throws Exception {
        when(termRepository.findAll("en", PageRequest.of(0, 1000))).thenReturn(null);

        mockMvc.perform(get("/api/terms"))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertEquals(
                        "EntityModel not found", result.getResponse().getErrorMessage()));
    }

    @Test
    void preservesArbitraryV1LanguageCompatibility() throws Exception {
        mockMvc.perform(get("/api/terms").param("lang", "en_US"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("en_US"));

        verify(termRepository).findAll("en_US", PageRequest.of(0, 1000));
    }

    @Test
    void returnsStableBadRequestFieldsForUnsupportedSort() throws Exception {
        Pageable pageable = PageRequest.of(0, 1000, org.springframework.data.domain.Sort.by("bad"));
        when(termRepository.findAll("en", pageable))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: bad"));

        mockMvc.perform(get("/api/terms").param("sort", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported sort field: bad"));
    }

    @Test
    void rejectsUnsupportedHttpMethodWithStableErrorFields() throws Exception {
        mockMvc.perform(post("/api/terms"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private static PageImpl<V1Term> page(Pageable pageable, V1Term... terms) {
        return new PageImpl<>(List.of(terms), pageable, terms.length);
    }

    private static V1Term term(String lang) {
        V1Term term = new V1Term();
        term.iri = TERM_IRI;
        term.lang = lang;
        term.label = "Liver disease";
        term.description = new String[]{"A disorder affecting hepatic tissue."};
        term.synonyms = new String[]{"Hepatic disorder"};
        term.ontologyName = "efo";
        term.ontologyPrefix = "EFO";
        term.ontologyIri = "http://www.ebi.ac.uk/efo";
        term.isObsolete = false;
        term.isDefiningOntology = true;
        term.hasChildren = false;
        term.isRoot = true;
        term.shortForm = "EFO_0001";
        term.oboId = "EFO:0001";
        term.inSubsets = List.of("core");
        term.annotation = Map.of();
        term.related = List.of();
        return term;
    }
}
