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
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.v1.V1GraphRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1JsTreeRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1TermRepository;
import uk.ac.ebi.spot.ols.service.PostgresClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers all of V1OntologyTermController's 23 routes: the core list/roots/preferredRoots/single/
 * parents/children/descendants/ancestors/jstree routes (milestone 1, PR #1393), plus the
 * hierarchical-variant per-term routes, /graph, the dynamic related-by-property route, and the
 * seven ontology-root-level shortcut routes (milestone 2, PR #1395).
 */
@WebMvcTest(V1OntologyTermController.class)
@ContextConfiguration(classes = {
        V1OntologyTermController.class,
        V1TermAssembler.class,
        V1PreferredRootTermAssembler.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V1OntologyTermControllerWIT {

    private static final String TERM_IRI = "http://example.org/EFO_1001";
    private static final String PARENT_IRI = "http://example.org/EFO_0001";
    private static final URI LIST_URI = URI.create("/api/ontologies/EFO/terms");
    private static final URI ROOTS_URI = URI.create("/api/ontologies/EFO/terms/roots");
    private static final URI PREFERRED_ROOTS_URI =
            URI.create("/api/ontologies/EFO/terms/preferredRoots");
    private static final URI TERM_URI = URI.create(
            "/api/ontologies/EFO/terms/http%253A%252F%252Fexample.org%252FEFO_1001");
    private static final URI PARENT_URI = URI.create(
            "/api/ontologies/EFO/terms/http%253A%252F%252Fexample.org%252FEFO_0001");
    private static final URI PARENTS_URI = URI.create(TERM_URI + "/parents");
    private static final URI CHILDREN_URI = URI.create(TERM_URI + "/children");
    private static final URI DESCENDANTS_URI = URI.create(TERM_URI + "/descendants");
    private static final URI ANCESTORS_URI = URI.create(TERM_URI + "/ancestors");
    private static final URI JS_TREE_URI = URI.create(TERM_URI + "/jstree");
    private static final URI JS_TREE_CHILDREN_URI = URI.create(
            TERM_URI + "/jstree/children/node-1");
    private static final URI HIERARCHICAL_PARENTS_URI =
            URI.create(TERM_URI + "/hierarchicalParents");
    private static final URI HIERARCHICAL_ANCESTORS_URI =
            URI.create(TERM_URI + "/hierarchicalAncestors");
    private static final URI HIERARCHICAL_CHILDREN_URI =
            URI.create(PARENT_URI + "/hierarchicalChildren");
    private static final URI HIERARCHICAL_DESCENDANTS_URI =
            URI.create(PARENT_URI + "/hierarchicalDescendants");
    private static final URI GRAPH_URI = URI.create(TERM_URI + "/graph");
    private static final URI RELATED_URI = URI.create(
            TERM_URI + "/http%253A%252F%252Fexample.org%252Frelated");
    private static final URI SHORTCUT_CHILDREN_URI = URI.create("/api/ontologies/EFO/children");
    private static final URI SHORTCUT_DESCENDANTS_URI =
            URI.create("/api/ontologies/EFO/descendants");
    private static final URI SHORTCUT_PARENTS_URI = URI.create("/api/ontologies/EFO/parents");
    private static final URI SHORTCUT_ANCESTORS_URI = URI.create("/api/ontologies/EFO/ancestors");
    private static final URI SHORTCUT_HIERARCHICAL_CHILDREN_URI =
            URI.create("/api/ontologies/EFO/hierarchicalChildren");
    private static final URI SHORTCUT_HIERARCHICAL_DESCENDANTS_URI =
            URI.create("/api/ontologies/EFO/hierarchicalDescendants");
    private static final URI SHORTCUT_HIERARCHICAL_ANCESTORS_URI =
            URI.create("/api/ontologies/EFO/hierarchicalAncestors");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private V1TermRepository termRepository;

    @MockitoBean
    private V1JsTreeRepository jsTreeRepository;

    @MockitoBean
    private V1GraphRepository graphRepository;

    @MockitoBean
    private PostgresClient postgresClient;

    @MockitoBean
    private EntityLinks entityLinks;

    @BeforeEach
    void stubResponses() {
        when(termRepository.findAllByOntology(anyString(), any(), anyString(), any()))
                .thenAnswer(invocation -> termPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(termRepository.findByOntologyAndIri(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> term(invocation.getArgument(2)));
        when(termRepository.getRoots(anyString(), anyBoolean(), anyString(), any()))
                .thenAnswer(invocation -> termPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(termRepository.getPreferredRootTerms(anyString(), anyBoolean(), anyString(), any()))
                .thenAnswer(invocation -> termPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(termRepository.getParents(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> termPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(termRepository.getChildren(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> termPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(termRepository.getDescendants(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> termPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(termRepository.getAncestors(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> termPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(termRepository.getHierarchicalParents(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> termPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(termRepository.getHierarchicalAncestors(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> termPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(termRepository.getHierarchicalChildren(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> termPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(termRepository.getHierarchicalDescendants(
                anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> termPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(termRepository.getRelated(
                anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> termPage(
                        invocation.getArgument(4), invocation.getArgument(2)));
        when(jsTreeRepository.getJsTreeForClass(anyString(), anyString(), anyString()))
                .thenReturn(List.of(Map.of(
                        "id", "term-node",
                        "parent", "#",
                        "iri", TERM_IRI,
                        "text", "Clinical liver child",
                        "state", Map.of("selected", true),
                        "children", false,
                        "ontology_name", "efo")));
        when(jsTreeRepository.getJsTreeChildrenForClass(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(Map.of(
                        "id", "child-node",
                        "parent", "term-node",
                        "iri", TERM_IRI,
                        "text", "Clinical liver child")));
        when(graphRepository.getGraphForClass(anyString(), anyString(), anyString()))
                .thenReturn(Map.of(
                        "nodes", List.of(Map.of("iri", TERM_IRI, "label", "Clinical liver child")),
                        "edges", List.of(Map.of(
                                "source", TERM_IRI, "target", PARENT_IRI, "label", "is a"))));
    }

    @Test
    void returnsDefaultOntologyTermListContract() throws Exception {
        mockMvc.perform(get(LIST_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(TERM_IRI))
                .andExpect(jsonPath("$._embedded.terms[0].label")
                        .value("Clinical liver child"))
                .andExpect(jsonPath("$._embedded.terms[0].ontology_name").value("efo"))
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("en"))
                .andExpect(jsonPath("$._embedded.terms[0]._links.self.href")
                        .value(endsWith("/api/ontologies/efo/terms/"
                                + "http%253A%252F%252Fexample.org%252FEFO_1001?lang=en")))
                .andExpect(jsonPath("$.page.size").value(1000))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.number").value(0));

        verify(termRepository).findAllByOntology("efo", null, "en", PageRequest.of(0, 1000));
    }

    @Test
    void bindsCollectionLanguagePageSizeAndSort() throws Exception {
        mockMvc.perform(get(LIST_URI)
                        .param("lang", "fr")
                        .param("page", "2")
                        .param("size", "7")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("fr"));

        verify(termRepository).findAllByOntology(
                "efo", null, "fr", PageRequest.of(2, 7,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void bindsExplicitObsoletesFilter() throws Exception {
        mockMvc.perform(get(LIST_URI).param("obsoletes", "true"))
                .andExpect(status().isOk());
        verify(termRepository).findAllByOntology(
                "efo", true, "en", PageRequest.of(0, 1000));

        mockMvc.perform(get(LIST_URI).param("obsoletes", "false"))
                .andExpect(status().isOk());
        verify(termRepository).findAllByOntology(
                "efo", false, "en", PageRequest.of(0, 1000));
    }

    @Test
    void rejectsMalformedObsoletesWithStableErrorFields() throws Exception {
        mockMvc.perform(get(LIST_URI).param("obsoletes", "not-a-boolean"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Method parameter 'obsoletes': Failed to convert value of type "
                                + "'java.lang.String' to required type 'java.lang.Boolean'; "
                                + "Invalid boolean value [not-a-boolean]"));
    }

    @Test
    void routesEveryCollectionIdentifierWithCascadeAndPrecedence() throws Exception {
        mockMvc.perform(get(LIST_URI).param("iri", TERM_IRI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(TERM_IRI));
        verify(termRepository).findByOntologyAndIri("efo", TERM_IRI, "fr");

        when(termRepository.findByOntologyAndIri("efo", "EFO_1001", "fr")).thenReturn(null);
        when(termRepository.findByOntologyAndShortForm("efo", "EFO_1001", "fr"))
                .thenReturn(term("fr"));
        mockMvc.perform(get(LIST_URI).param("short_form", "EFO_1001").param("lang", "fr"))
                .andExpect(status().isOk());
        verify(termRepository).findByOntologyAndShortForm("efo", "EFO_1001", "fr");

        when(termRepository.findByOntologyAndIri("efo", "EFO:1001", "fr")).thenReturn(null);
        when(termRepository.findByOntologyAndShortForm("efo", "EFO:1001", "fr")).thenReturn(null);
        when(termRepository.findByOntologyAndOboId("efo", "EFO:1001", "fr"))
                .thenReturn(term("fr"));
        mockMvc.perform(get(LIST_URI).param("obo_id", "EFO:1001").param("lang", "fr"))
                .andExpect(status().isOk());
        verify(termRepository).findByOntologyAndOboId("efo", "EFO:1001", "fr");

        mockMvc.perform(get(LIST_URI).param("id", "arbitrary-id").param("lang", "fr"))
                .andExpect(status().isOk());
        verify(termRepository).findByOntologyAndIri("efo", "arbitrary-id", "fr");

        mockMvc.perform(get(LIST_URI)
                        .param("id", "explicit-id")
                        .param("iri", "ignored-iri")
                        .param("short_form", "ignored")
                        .param("obo_id", "IGNORED:1")
                        .param("lang", "fr"))
                .andExpect(status().isOk());
        verify(termRepository).findByOntologyAndIri("efo", "explicit-id", "fr");
        verify(termRepository, never()).findByOntologyAndIri("efo", "ignored-iri", "fr");
    }

    @Test
    void ignoresUnsupportedReservedAndDynamicStyleCollectionParameters() throws Exception {
        mockMvc.perform(get(LIST_URI)
                        .param("search", "liver")
                        .param("includeObsoleteEntities", "false")
                        .param("subset", "core", "slim")
                        .param("http://example.org/category", "clinical", "policy"))
                .andExpect(status().isOk());

        verify(termRepository).findAllByOntology("efo", null, "en", PageRequest.of(0, 1000));
    }

    @ParameterizedTest
    @CsvSource({
            "list, -1, 20, 0, 20",
            "list, 0, 0, 0, 1000",
            "list, 0, 1001, 0, 1000",
            "roots, -1, 20, 0, 20",
            "roots, 0, 0, 0, 1000",
            "roots, 0, 1001, 0, 1000",
            "preferredRoots, -1, 20, 0, 20",
            "preferredRoots, 0, 0, 0, 1000",
            "preferredRoots, 0, 1001, 0, 1000",
            "parents, -1, 20, 0, 20",
            "parents, 0, 0, 0, 1000",
            "parents, 0, 1001, 0, 1000",
            "children, -1, 20, 0, 20",
            "children, 0, 0, 0, 1000",
            "children, 0, 1001, 0, 1000",
            "descendants, -1, 20, 0, 20",
            "descendants, 0, 0, 0, 1000",
            "descendants, 0, 1001, 0, 1000",
            "ancestors, -1, 20, 0, 20",
            "ancestors, 0, 0, 0, 1000",
            "ancestors, 0, 1001, 0, 1000"
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
            case "list" -> verify(termRepository)
                    .findAllByOntology("efo", null, "en", expected);
            case "roots" -> verify(termRepository).getRoots("efo", false, "en", expected);
            case "preferredRoots" -> verify(termRepository)
                    .getPreferredRootTerms("efo", false, "en", expected);
            case "parents" -> verify(termRepository).getParents("efo", TERM_IRI, "en", expected);
            case "children" -> verify(termRepository)
                    .getChildren("efo", TERM_IRI, "en", expected);
            case "descendants" -> verify(termRepository)
                    .getDescendants("efo", TERM_IRI, "en", expected);
            case "ancestors" -> verify(termRepository)
                    .getAncestors("efo", TERM_IRI, "en", expected);
            default -> throw new IllegalArgumentException("Unknown route: " + route);
        }
    }

    @Test
    void usesPaginationDefaultsForMalformedNumericValuesAcrossPagedRoutes() throws Exception {
        Pageable defaults = PageRequest.of(0, 1000);

        for (URI uri : List.of(
                LIST_URI, ROOTS_URI, PREFERRED_ROOTS_URI, PARENTS_URI,
                CHILDREN_URI, DESCENDANTS_URI, ANCESTORS_URI)) {
            mockMvc.perform(get(uri).param("page", "bad").param("size", "bad"))
                    .andExpect(status().isOk());
        }

        verify(termRepository).findAllByOntology("efo", null, "en", defaults);
        verify(termRepository).getRoots("efo", false, "en", defaults);
        verify(termRepository).getPreferredRootTerms("efo", false, "en", defaults);
        verify(termRepository).getParents("efo", TERM_IRI, "en", defaults);
        verify(termRepository).getChildren("efo", TERM_IRI, "en", defaults);
        verify(termRepository).getDescendants("efo", TERM_IRI, "en", defaults);
        verify(termRepository).getAncestors("efo", TERM_IRI, "en", defaults);
    }

    @Test
    void returnsDoubleEncodedTermWithDefaultAndExplicitLanguage() throws Exception {
        mockMvc.perform(get(TERM_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.iri").value(TERM_IRI))
                .andExpect(jsonPath("$.label").value("Clinical liver child"))
                .andExpect(jsonPath("$.ontology_name").value("efo"))
                .andExpect(jsonPath("$.lang").value("en"));
        verify(termRepository).findByOntologyAndIri("efo", TERM_IRI, "en");

        mockMvc.perform(get(TERM_URI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("fr"));
        verify(termRepository).findByOntologyAndIri("efo", TERM_IRI, "fr");
    }

    @Test
    void returnsRootsWithDefaultAndExplicitIncludeObsoletes() throws Exception {
        mockMvc.perform(get(ROOTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(TERM_IRI));
        verify(termRepository).getRoots("efo", false, "en", PageRequest.of(0, 1000));

        mockMvc.perform(get(ROOTS_URI).param("includeObsoletes", "true"))
                .andExpect(status().isOk());
        verify(termRepository).getRoots("efo", true, "en", PageRequest.of(0, 1000));
    }

    @Test
    void returnsPreferredRootsWithDefaultAndExplicitIncludeObsoletes() throws Exception {
        mockMvc.perform(get(PREFERRED_ROOTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(TERM_IRI));
        verify(termRepository).getPreferredRootTerms("efo", false, "en", PageRequest.of(0, 1000));

        mockMvc.perform(get(PREFERRED_ROOTS_URI).param("includeObsoletes", "true"))
                .andExpect(status().isOk());
        verify(termRepository).getPreferredRootTerms("efo", true, "en", PageRequest.of(0, 1000));
    }

    @Test
    void rejectsMalformedIncludeObsoletesOnRootsAndPreferredRootsWithStableErrorFields()
            throws Exception {
        mockMvc.perform(get(ROOTS_URI).param("includeObsoletes", "not-a-boolean"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Method parameter 'includeObsoletes': Failed to convert value of type "
                                + "'java.lang.String' to required type 'boolean'; "
                                + "Invalid boolean value [not-a-boolean]"));

        mockMvc.perform(get(PREFERRED_ROOTS_URI).param("includeObsoletes", "not-a-boolean"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Method parameter 'includeObsoletes': Failed to convert value of type "
                                + "'java.lang.String' to required type 'boolean'; "
                                + "Invalid boolean value [not-a-boolean]"));
    }

    @Test
    void returnsParentsWithDefaultsAndExplicitOptions() throws Exception {
        mockMvc.perform(get(PARENTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(TERM_IRI))
                .andExpect(jsonPath("$.page.size").value(1000));

        mockMvc.perform(get(PARENTS_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("fr"));

        verify(termRepository).getParents(
                "efo", TERM_IRI, "fr", PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void returnsChildrenWithDefaultsAndExplicitOptions() throws Exception {
        mockMvc.perform(get(CHILDREN_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(TERM_IRI));

        mockMvc.perform(get(CHILDREN_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("fr"));

        verify(termRepository).getChildren(
                "efo", TERM_IRI, "fr", PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void returnsDescendantsWithDefaultsAndExplicitOptions() throws Exception {
        mockMvc.perform(get(DESCENDANTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(TERM_IRI));

        mockMvc.perform(get(DESCENDANTS_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("fr"));

        verify(termRepository).getDescendants(
                "efo", TERM_IRI, "fr", PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void returnsAncestorsWithDefaultsAndExplicitOptions() throws Exception {
        mockMvc.perform(get(ANCESTORS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(TERM_IRI));

        mockMvc.perform(get(ANCESTORS_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("fr"));

        verify(termRepository).getAncestors(
                "efo", TERM_IRI, "fr", PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void returnsDoubleEncodedTermJsTreeWithExplicitLanguage() throws Exception {
        mockMvc.perform(get(JS_TREE_URI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].iri").value(TERM_IRI))
                .andExpect(jsonPath("$[0].text").value("Clinical liver child"))
                .andExpect(jsonPath("$[0].state.selected").value(true));

        verify(jsTreeRepository).getJsTreeForClass(TERM_IRI, "efo", "fr");
    }

    @Test
    void acceptsIgnoredSiblingsAndViewModeParametersOnJsTree() throws Exception {
        mockMvc.perform(get(JS_TREE_URI)
                        .param("siblings", "true")
                        .param("viewMode", "All"))
                .andExpect(status().isOk());

        verify(jsTreeRepository).getJsTreeForClass(TERM_IRI, "efo", "en");
    }

    @Test
    void returnsTermJsTreeChildrenForADecodedNode() throws Exception {
        mockMvc.perform(get(JS_TREE_CHILDREN_URI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].iri").value(TERM_IRI))
                .andExpect(jsonPath("$[0].text").value("Clinical liver child"));

        verify(jsTreeRepository).getJsTreeChildrenForClass(TERM_IRI, "node-1", "efo", "fr");
    }

    @Test
    void preservesArbitraryV1LanguageCompatibilityAcrossRouteFamilies() throws Exception {
        mockMvc.perform(get(LIST_URI).param("lang", "en_US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("en_US"));
        mockMvc.perform(get(PARENTS_URI).param("lang", "en_US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("en_US"));
        mockMvc.perform(get(JS_TREE_URI).param("lang", "en_US"))
                .andExpect(status().isOk());

        verify(termRepository).findAllByOntology("efo", null, "en_US", PageRequest.of(0, 1000));
        verify(termRepository).getParents("efo", TERM_IRI, "en_US", PageRequest.of(0, 1000));
        verify(jsTreeRepository).getJsTreeForClass(TERM_IRI, "efo", "en_US");
    }

    @Test
    void delegatesHierarchicalParentsWithDefaultsAndExplicitOptions() throws Exception {
        mockMvc.perform(get(HIERARCHICAL_PARENTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(TERM_IRI))
                .andExpect(jsonPath("$.page.size").value(1000));

        mockMvc.perform(get(HIERARCHICAL_PARENTS_URI)
                        .param("lang", "fr").param("page", "1").param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("fr"));

        verify(termRepository).getHierarchicalParents(
                "efo", TERM_IRI, "fr", PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void delegatesHierarchicalAncestorsWithDefaultsAndExplicitOptions() throws Exception {
        mockMvc.perform(get(HIERARCHICAL_ANCESTORS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(TERM_IRI));

        mockMvc.perform(get(HIERARCHICAL_ANCESTORS_URI)
                        .param("lang", "fr").param("page", "1").param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("fr"));

        verify(termRepository).getHierarchicalAncestors(
                "efo", TERM_IRI, "fr", PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void delegatesHierarchicalChildrenWithDefaultsAndExplicitOptions() throws Exception {
        mockMvc.perform(get(HIERARCHICAL_CHILDREN_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(TERM_IRI));

        mockMvc.perform(get(HIERARCHICAL_CHILDREN_URI)
                        .param("lang", "fr").param("page", "1").param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("fr"));

        verify(termRepository).getHierarchicalChildren(
                "efo", PARENT_IRI, "fr", PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void delegatesHierarchicalDescendantsWithDefaultsAndExplicitOptions() throws Exception {
        mockMvc.perform(get(HIERARCHICAL_DESCENDANTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(TERM_IRI));

        mockMvc.perform(get(HIERARCHICAL_DESCENDANTS_URI)
                        .param("lang", "fr").param("page", "1").param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("fr"));

        verify(termRepository).getHierarchicalDescendants(
                "efo", PARENT_IRI, "fr", PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @ParameterizedTest
    @CsvSource({
            "hierarchicalParents, -1, 20, 0, 20",
            "hierarchicalParents, 0, 0, 0, 1000",
            "hierarchicalParents, 0, 1001, 0, 1000",
            "hierarchicalAncestors, -1, 20, 0, 20",
            "hierarchicalChildren, -1, 20, 0, 20",
            "hierarchicalDescendants, -1, 20, 0, 20"
    })
    void normalizesPaginationBoundariesAcrossHierarchicalRoutes(
            String route, int requestedPage, int requestedSize, int expectedPage, int expectedSize)
            throws Exception {
        mockMvc.perform(get(route(route))
                        .param("page", Integer.toString(requestedPage))
                        .param("size", Integer.toString(requestedSize)))
                .andExpect(status().isOk());

        Pageable expected = PageRequest.of(expectedPage, expectedSize);
        switch (route) {
            case "hierarchicalParents" -> verify(termRepository)
                    .getHierarchicalParents("efo", TERM_IRI, "en", expected);
            case "hierarchicalAncestors" -> verify(termRepository)
                    .getHierarchicalAncestors("efo", TERM_IRI, "en", expected);
            case "hierarchicalChildren" -> verify(termRepository)
                    .getHierarchicalChildren("efo", PARENT_IRI, "en", expected);
            case "hierarchicalDescendants" -> verify(termRepository)
                    .getHierarchicalDescendants("efo", PARENT_IRI, "en", expected);
            default -> throw new IllegalArgumentException("Unknown route: " + route);
        }
    }

    @Test
    void returnsTheClassGraphWithDoubleEncodedIriAndExplicitLanguage() throws Exception {
        mockMvc.perform(get(GRAPH_URI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.nodes[0].iri").value(TERM_IRI))
                .andExpect(jsonPath("$.edges[0].source").value(TERM_IRI))
                .andExpect(jsonPath("$.edges[0].target").value(PARENT_IRI));

        verify(graphRepository).getGraphForClass(TERM_IRI, "efo", "fr");
    }

    @Test
    void returnsRelatedEntitiesForADoubleEncodedTermAndPropertyIri() throws Exception {
        mockMvc.perform(get(RELATED_URI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.terms[0].iri").value(TERM_IRI))
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("fr"));

        verify(termRepository).getRelated(
                "efo", TERM_IRI, "fr", "http://example.org/related", PageRequest.of(0, 1000));
    }

    @Test
    void shortcutRoutesReturnAnEmptyContractWhenNoIdentifierIsSupplied() throws Exception {
        for (URI uri : List.of(
                SHORTCUT_CHILDREN_URI, SHORTCUT_DESCENDANTS_URI, SHORTCUT_PARENTS_URI,
                SHORTCUT_ANCESTORS_URI, SHORTCUT_HIERARCHICAL_CHILDREN_URI,
                SHORTCUT_HIERARCHICAL_DESCENDANTS_URI, SHORTCUT_HIERARCHICAL_ANCESTORS_URI)) {
            mockMvc.perform(get(uri))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.totalElements").value(0));
        }

        verify(termRepository, never()).getChildren(anyString(), anyString(), anyString(), any());
        verify(termRepository, never())
                .getDescendants(anyString(), anyString(), anyString(), any());
        verify(termRepository, never()).getParents(anyString(), anyString(), anyString(), any());
        verify(termRepository, never())
                .getAncestors(anyString(), anyString(), anyString(), any());
    }

    @Test
    void shortcutRoutesResolveTheIdentifierAndReportMissingResource() throws Exception {
        when(termRepository.findByOntologyAndIri("EFO", "missing", "en")).thenReturn(null);

        // The controller's own @ExceptionHandler(ResourceNotFoundException.class) catches this
        // before GlobalExceptionHandler does, discarding the exception's own message (proven at
        // the direct-test layer) in favor of the fixed legacy servlet reason and an empty body —
        // the same stable V1 contract already covered for the single-term 404 above.
        mockMvc.perform(get(SHORTCUT_CHILDREN_URI).param("iri", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertEquals(
                        "EntityModel not found", result.getResponse().getErrorMessage()))
                .andExpect(content().string(""));
    }

    @Test
    void eachShortcutRouteResolvesTheIdentifierAndDelegatesToItsMatchingHierarchyMethod()
            throws Exception {
        mockMvc.perform(get(SHORTCUT_CHILDREN_URI).param("iri", TERM_IRI).param("lang", "fr"))
                .andExpect(status().isOk());
        verify(termRepository).getChildren("efo", TERM_IRI, "fr", PageRequest.of(0, 1000));

        mockMvc.perform(get(SHORTCUT_DESCENDANTS_URI).param("iri", TERM_IRI).param("lang", "fr"))
                .andExpect(status().isOk());
        verify(termRepository).getDescendants("efo", TERM_IRI, "fr", PageRequest.of(0, 1000));

        mockMvc.perform(get(SHORTCUT_PARENTS_URI).param("iri", TERM_IRI).param("lang", "fr"))
                .andExpect(status().isOk());
        verify(termRepository).getParents("efo", TERM_IRI, "fr", PageRequest.of(0, 1000));

        mockMvc.perform(get(SHORTCUT_ANCESTORS_URI).param("iri", TERM_IRI).param("lang", "fr"))
                .andExpect(status().isOk());
        verify(termRepository).getAncestors("efo", TERM_IRI, "fr", PageRequest.of(0, 1000));

        mockMvc.perform(get(SHORTCUT_HIERARCHICAL_CHILDREN_URI)
                        .param("iri", TERM_IRI).param("lang", "fr"))
                .andExpect(status().isOk());
        verify(termRepository)
                .getHierarchicalChildren("efo", TERM_IRI, "fr", PageRequest.of(0, 1000));

        mockMvc.perform(get(SHORTCUT_HIERARCHICAL_DESCENDANTS_URI)
                        .param("iri", TERM_IRI).param("lang", "fr"))
                .andExpect(status().isOk());
        verify(termRepository)
                .getHierarchicalDescendants("efo", TERM_IRI, "fr", PageRequest.of(0, 1000));

        mockMvc.perform(get(SHORTCUT_HIERARCHICAL_ANCESTORS_URI)
                        .param("iri", TERM_IRI).param("lang", "fr"))
                .andExpect(status().isOk());
        verify(termRepository)
                .getHierarchicalAncestors("efo", TERM_IRI, "fr", PageRequest.of(0, 1000));
    }

    @Test
    void shortcutRoutesResolveIdentifierByShortFormAndOboIdWhenIriIsAbsent() throws Exception {
        // getOneById receives the ontology id exactly as passed in the URL (here "EFO",
        // unlowered) — see OlsSearchQuery's "ontology_id" special case, which lowercases the
        // filter value itself, so this resolves correctly against real Postgres regardless.
        when(termRepository.findByOntologyAndIri("EFO", "EFO_1001", "en")).thenReturn(null);
        when(termRepository.findByOntologyAndShortForm("EFO", "EFO_1001", "en"))
                .thenReturn(term("en"));
        mockMvc.perform(get(SHORTCUT_CHILDREN_URI).param("short_form", "EFO_1001"))
                .andExpect(status().isOk());
        verify(termRepository).getChildren("efo", TERM_IRI, "en", PageRequest.of(0, 1000));

        when(termRepository.findByOntologyAndIri("EFO", "EFO:1001", "en")).thenReturn(null);
        when(termRepository.findByOntologyAndShortForm("EFO", "EFO:1001", "en")).thenReturn(null);
        when(termRepository.findByOntologyAndOboId("EFO", "EFO:1001", "en"))
                .thenReturn(term("en"));
        mockMvc.perform(get(SHORTCUT_PARENTS_URI).param("obo_id", "EFO:1001"))
                .andExpect(status().isOk());
        verify(termRepository).getParents("efo", TERM_IRI, "en", PageRequest.of(0, 1000));
    }

    @Test
    void supportsLegacyHalMediaTypeOnEveryHalRoute() throws Exception {
        for (URI uri : List.of(
                LIST_URI, ROOTS_URI, PREFERRED_ROOTS_URI, TERM_URI, PARENTS_URI,
                CHILDREN_URI, DESCENDANTS_URI, ANCESTORS_URI, JS_TREE_URI,
                HIERARCHICAL_PARENTS_URI, HIERARCHICAL_ANCESTORS_URI, HIERARCHICAL_CHILDREN_URI,
                HIERARCHICAL_DESCENDANTS_URI, RELATED_URI)) {
            mockMvc.perform(get(uri).accept(MediaTypes.HAL_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
        }
        mockMvc.perform(get(SHORTCUT_CHILDREN_URI).param("iri", TERM_IRI)
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
    }

    @Test
    void returnsStableLegacyNotFoundStatusAndMessage() throws Exception {
        when(termRepository.findByOntologyAndIri("efo", TERM_IRI, "en")).thenReturn(null);

        mockMvc.perform(get(TERM_URI))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertEquals(
                        "EntityModel not found", result.getResponse().getErrorMessage()))
                .andExpect(content().string(""));
    }

    @Test
    void returnsStableBadRequestFieldsForUnsupportedSort() throws Exception {
        Pageable pageable = PageRequest.of(
                0, 1000, org.springframework.data.domain.Sort.by("bad"));
        when(termRepository.getParents("efo", TERM_IRI, "en", pageable))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: bad"));

        mockMvc.perform(get(PARENTS_URI).param("sort", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported sort field: bad"));
    }

    @Test
    void returnsStableBadRequestFieldsForUnsupportedSortOnAHierarchicalRoute() throws Exception {
        Pageable pageable = PageRequest.of(
                0, 1000, org.springframework.data.domain.Sort.by("bad"));
        when(termRepository.getHierarchicalParents("efo", TERM_IRI, "en", pageable))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: bad"));

        mockMvc.perform(get(HIERARCHICAL_PARENTS_URI).param("sort", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported sort field: bad"));
    }

    @Test
    void rejectsUnsupportedHttpMethodWithStableErrorFields() throws Exception {
        mockMvc.perform(post(LIST_URI))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private static URI route(String route) {
        return switch (route) {
            case "list" -> LIST_URI;
            case "roots" -> ROOTS_URI;
            case "preferredRoots" -> PREFERRED_ROOTS_URI;
            case "parents" -> PARENTS_URI;
            case "children" -> CHILDREN_URI;
            case "descendants" -> DESCENDANTS_URI;
            case "ancestors" -> ANCESTORS_URI;
            case "hierarchicalParents" -> HIERARCHICAL_PARENTS_URI;
            case "hierarchicalAncestors" -> HIERARCHICAL_ANCESTORS_URI;
            case "hierarchicalChildren" -> HIERARCHICAL_CHILDREN_URI;
            case "hierarchicalDescendants" -> HIERARCHICAL_DESCENDANTS_URI;
            default -> throw new IllegalArgumentException("Unknown route: " + route);
        };
    }

    private static OlsFacetedResultsPage<V1Term> termPage(Pageable pageable, String lang) {
        return new OlsFacetedResultsPage<>(List.of(term(lang)), Map.of(), pageable, 1);
    }

    private static V1Term term(String lang) {
        V1Term term = new V1Term();
        term.iri = TERM_IRI;
        term.lang = lang;
        term.label = "Clinical liver child";
        term.description = new String[]{"A synthetic class used for hierarchy contract tests."};
        term.synonyms = new String[]{"Active hierarchy child"};
        term.ontologyName = "efo";
        term.ontologyPrefix = "EFO";
        term.ontologyIri = "http://www.ebi.ac.uk/efo";
        term.isObsolete = false;
        term.isDefiningOntology = true;
        term.hasChildren = false;
        term.isRoot = false;
        term.isPreferredRoot = false;
        term.shortForm = "EFO_1001";
        term.oboId = "EFO:1001";
        term.inSubsets = List.of("hierarchy");
        term.annotation = Map.of();
        term.oboDefinitionCitations = List.of();
        term.oboXrefs = List.of();
        term.oboSynonyms = List.of();
        term.related = List.of();
        return term;
    }
}
