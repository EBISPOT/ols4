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
import uk.ac.ebi.spot.ols.model.v1.V1Property;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.v1.V1JsTreeRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1PropertyRepository;
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

@WebMvcTest(V1OntologyPropertyController.class)
@ContextConfiguration(classes = {
        V1OntologyPropertyController.class,
        V1PropertyAssembler.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V1OntologyPropertyControllerWIT {

    private static final String PROPERTY_IRI = "http://example.org/EFO_0101";
    private static final URI LIST_URI = URI.create("/api/ontologies/EFO/properties");
    private static final URI ROOTS_URI = URI.create("/api/ontologies/EFO/properties/roots");
    private static final URI PROPERTY_URI = URI.create(
            "/api/ontologies/EFO/properties/http%253A%252F%252Fexample.org%252FEFO_0101");
    private static final URI PARENTS_URI = URI.create(PROPERTY_URI + "/parents");
    private static final URI CHILDREN_URI = URI.create(PROPERTY_URI + "/children");
    private static final URI DESCENDANTS_URI = URI.create(PROPERTY_URI + "/descendants");
    private static final URI ANCESTORS_URI = URI.create(PROPERTY_URI + "/ancestors");
    private static final URI JS_TREE_URI = URI.create(PROPERTY_URI + "/jstree");
    private static final URI JS_TREE_CHILDREN_URI = URI.create(
            PROPERTY_URI + "/jstree/children/node-1");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private V1PropertyRepository propertyRepository;

    @MockitoBean
    private V1JsTreeRepository jsTreeRepository;

    @MockitoBean
    private PostgresClient postgresClient;

    @MockitoBean
    private EntityLinks entityLinks;

    @BeforeEach
    void stubResponses() {
        when(propertyRepository.findAllByOntology(anyString(), anyString(), any()))
                .thenAnswer(invocation -> propertyPage(
                        invocation.getArgument(2), invocation.getArgument(1)));
        when(propertyRepository.findByOntologyAndIri(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> property(invocation.getArgument(2)));
        when(propertyRepository.findByOntologyAndShortForm(
                anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> property(invocation.getArgument(2)));
        when(propertyRepository.findByOntologyAndOboId(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> property(invocation.getArgument(2)));
        when(propertyRepository.getRoots(anyString(), anyBoolean(), anyString(), any()))
                .thenAnswer(invocation -> propertyPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(propertyRepository.getParents(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> propertyPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(propertyRepository.getChildren(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> propertyPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(propertyRepository.getDescendants(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> propertyPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(propertyRepository.getAncestors(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> propertyPage(
                        invocation.getArgument(3), invocation.getArgument(2)));
        when(jsTreeRepository.getJsTreeForProperty(anyString(), anyString(), anyString()))
                .thenReturn(List.of(Map.of(
                        "id", "property-node",
                        "parent", "#",
                        "iri", PROPERTY_IRI,
                        "text", "has material",
                        "state", Map.of("selected", true),
                        "children", false,
                        "ontology_name", "efo")));
        when(jsTreeRepository.getJsTreeChildrenForProperty(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(Map.of(
                        "id", "child-node",
                        "parent", "property-node",
                        "iri", PROPERTY_IRI,
                        "text", "has material")));
    }

    @Test
    void returnsDefaultOntologyPropertyListContract() throws Exception {
        mockMvc.perform(get(LIST_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$._embedded.properties[0].iri").value(PROPERTY_IRI))
                .andExpect(jsonPath("$._embedded.properties[0].label").value("has material"))
                .andExpect(jsonPath("$._embedded.properties[0].ontology_name").value("efo"))
                .andExpect(jsonPath("$._embedded.properties[0].lang").value("en"))
                .andExpect(jsonPath("$._embedded.properties[0]._links.self.href")
                        .value(endsWith("/api/ontologies/efo/properties/"
                                + "http%253A%252F%252Fexample.org%252FEFO_0101?lang=en")))
                .andExpect(jsonPath("$.page.size").value(1000))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.number").value(0));

        verify(propertyRepository).findAllByOntology("efo", "en", PageRequest.of(0, 1000));
    }

    @Test
    void bindsCollectionLanguagePageSizeAndSort() throws Exception {
        mockMvc.perform(get(LIST_URI)
                        .param("lang", "fr")
                        .param("page", "2")
                        .param("size", "7")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].lang").value("fr"));

        verify(propertyRepository).findAllByOntology(
                "efo", "fr", PageRequest.of(2, 7,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void routesEveryCollectionIdentifierAndAppliesPrecedence() throws Exception {
        mockMvc.perform(get(LIST_URI).param("iri", PROPERTY_IRI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].iri").value(PROPERTY_IRI));
        verify(propertyRepository).findByOntologyAndIri("efo", PROPERTY_IRI, "fr");

        mockMvc.perform(get(LIST_URI).param("short_form", "EFO_0101").param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].iri").value(PROPERTY_IRI));
        verify(propertyRepository).findByOntologyAndShortForm("efo", "EFO_0101", "fr");

        mockMvc.perform(get(LIST_URI).param("obo_id", "EFO:0101").param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].iri").value(PROPERTY_IRI));
        verify(propertyRepository).findByOntologyAndOboId("efo", "EFO:0101", "fr");

        mockMvc.perform(get(LIST_URI)
                        .param("iri", PROPERTY_IRI)
                        .param("short_form", "ignored")
                        .param("obo_id", "IGNORED:1"))
                .andExpect(status().isOk());
        verify(propertyRepository, never())
                .findByOntologyAndShortForm("efo", "ignored", "en");
        verify(propertyRepository, never())
                .findByOntologyAndOboId("efo", "IGNORED:1", "en");
    }

    @Test
    void ignoresUnsupportedReservedAndDynamicStyleCollectionParameters() throws Exception {
        mockMvc.perform(get(LIST_URI)
                        .param("search", "material")
                        .param("includeObsoleteEntities", "false")
                        .param("subset", "core", "slim")
                        .param("http://example.org/category", "clinical", "policy"))
                .andExpect(status().isOk());

        verify(propertyRepository).findAllByOntology("efo", "en", PageRequest.of(0, 1000));
    }

    @ParameterizedTest
    @CsvSource({
            "list, -1, 20, 0, 20",
            "list, 0, 0, 0, 1000",
            "list, 0, 1001, 0, 1000",
            "roots, -1, 20, 0, 20",
            "roots, 0, 0, 0, 1000",
            "roots, 0, 1001, 0, 1000",
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
            case "list" -> verify(propertyRepository).findAllByOntology("efo", "en", expected);
            case "roots" -> verify(propertyRepository)
                    .getRoots("efo", false, "en", expected);
            case "parents" -> verify(propertyRepository)
                    .getParents("efo", PROPERTY_IRI, "en", expected);
            case "children" -> verify(propertyRepository)
                    .getChildren("efo", PROPERTY_IRI, "en", expected);
            case "descendants" -> verify(propertyRepository)
                    .getDescendants("efo", PROPERTY_IRI, "en", expected);
            case "ancestors" -> verify(propertyRepository)
                    .getAncestors("efo", PROPERTY_IRI, "en", expected);
            default -> throw new IllegalArgumentException("Unknown route: " + route);
        }
    }

    @Test
    void usesPaginationDefaultsForMalformedNumericValuesAcrossPagedRoutes() throws Exception {
        Pageable defaults = PageRequest.of(0, 1000);

        mockMvc.perform(get(LIST_URI).param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());
        mockMvc.perform(get(ROOTS_URI).param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());
        mockMvc.perform(get(PARENTS_URI).param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());
        mockMvc.perform(get(CHILDREN_URI).param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());
        mockMvc.perform(get(DESCENDANTS_URI).param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());
        mockMvc.perform(get(ANCESTORS_URI).param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());

        verify(propertyRepository).findAllByOntology("efo", "en", defaults);
        verify(propertyRepository).getRoots("efo", false, "en", defaults);
        verify(propertyRepository).getParents("efo", PROPERTY_IRI, "en", defaults);
        verify(propertyRepository).getChildren("efo", PROPERTY_IRI, "en", defaults);
        verify(propertyRepository).getDescendants("efo", PROPERTY_IRI, "en", defaults);
        verify(propertyRepository).getAncestors("efo", PROPERTY_IRI, "en", defaults);
    }

    @Test
    void returnsDoubleEncodedPropertyWithDefaultAndExplicitLanguage() throws Exception {
        mockMvc.perform(get(PROPERTY_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.iri").value(PROPERTY_IRI))
                .andExpect(jsonPath("$.label").value("has material"))
                .andExpect(jsonPath("$.ontology_name").value("efo"))
                .andExpect(jsonPath("$.lang").value("en"));
        verify(propertyRepository).findByOntologyAndIri("efo", PROPERTY_IRI, "en");

        mockMvc.perform(get(PROPERTY_URI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("fr"));
        verify(propertyRepository).findByOntologyAndIri("efo", PROPERTY_IRI, "fr");
    }

    @Test
    void returnsRootsWithDefaultAndExplicitIncludeObsoletes() throws Exception {
        mockMvc.perform(get(ROOTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].iri").value(PROPERTY_IRI));
        verify(propertyRepository).getRoots("efo", false, "en", PageRequest.of(0, 1000));

        mockMvc.perform(get(ROOTS_URI).param("includeObsoletes", "true"))
                .andExpect(status().isOk());
        verify(propertyRepository).getRoots("efo", true, "en", PageRequest.of(0, 1000));

        mockMvc.perform(get(ROOTS_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].lang").value("fr"));
        verify(propertyRepository).getRoots(
                "efo", false, "fr", PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void rejectsMalformedIncludeObsoletesWithStableErrorFields() throws Exception {
        mockMvc.perform(get(ROOTS_URI).param("includeObsoletes", "not-a-boolean"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Method parameter 'includeObsoletes': Failed to convert value of type "
                                + "'java.lang.String' to required type 'boolean'; "
                                + "Invalid boolean value [not-a-boolean]"));
    }

    @Test
    void rejectsMalformedSiblingsWithStableErrorFields() throws Exception {
        mockMvc.perform(get(JS_TREE_URI).param("siblings", "not-a-boolean"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Method parameter 'siblings': Failed to convert value of type "
                                + "'java.lang.String' to required type 'boolean'; "
                                + "Invalid boolean value [not-a-boolean]"));
    }

    @Test
    void returnsParentsWithDefaultsAndExplicitOptions() throws Exception {
        mockMvc.perform(get(PARENTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].iri").value(PROPERTY_IRI))
                .andExpect(jsonPath("$.page.size").value(1000));

        mockMvc.perform(get(PARENTS_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].lang").value("fr"));

        verify(propertyRepository).getParents(
                "efo", PROPERTY_IRI, "fr", PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void returnsChildrenWithDefaultsAndExplicitOptions() throws Exception {
        mockMvc.perform(get(CHILDREN_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].iri").value(PROPERTY_IRI));

        mockMvc.perform(get(CHILDREN_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].lang").value("fr"));

        verify(propertyRepository).getChildren(
                "efo", PROPERTY_IRI, "fr", PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void returnsDescendantsWithDefaultsAndExplicitOptions() throws Exception {
        mockMvc.perform(get(DESCENDANTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].iri").value(PROPERTY_IRI));

        mockMvc.perform(get(DESCENDANTS_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].lang").value("fr"));

        verify(propertyRepository).getDescendants(
                "efo", PROPERTY_IRI, "fr", PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void returnsAncestorsWithDefaultsAndExplicitOptions() throws Exception {
        mockMvc.perform(get(ANCESTORS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].iri").value(PROPERTY_IRI));

        mockMvc.perform(get(ANCESTORS_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].lang").value("fr"));

        verify(propertyRepository).getAncestors(
                "efo", PROPERTY_IRI, "fr", PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void returnsDoubleEncodedPropertyJsTreeWithExplicitLanguage() throws Exception {
        mockMvc.perform(get(JS_TREE_URI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].iri").value(PROPERTY_IRI))
                .andExpect(jsonPath("$[0].text").value("has material"))
                .andExpect(jsonPath("$[0].state.selected").value(true));

        verify(jsTreeRepository).getJsTreeForProperty(PROPERTY_IRI, "efo", "fr");
    }

    @Test
    void acceptsIgnoredSiblingsAndViewModeParametersOnJsTree() throws Exception {
        mockMvc.perform(get(JS_TREE_URI)
                        .param("siblings", "true")
                        .param("viewMode", "All"))
                .andExpect(status().isOk());

        verify(jsTreeRepository).getJsTreeForProperty(PROPERTY_IRI, "efo", "en");
    }

    @Test
    void returnsPropertyJsTreeChildrenForADecodedNode() throws Exception {
        mockMvc.perform(get(JS_TREE_CHILDREN_URI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].iri").value(PROPERTY_IRI))
                .andExpect(jsonPath("$[0].text").value("has material"));

        verify(jsTreeRepository).getJsTreeChildrenForProperty(
                PROPERTY_IRI, "node-1", "efo", "fr");
    }

    @Test
    void preservesArbitraryV1LanguageCompatibilityAcrossRouteFamilies() throws Exception {
        mockMvc.perform(get(LIST_URI).param("lang", "en_US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].lang").value("en_US"));
        mockMvc.perform(get(PARENTS_URI).param("lang", "en_US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].lang").value("en_US"));
        mockMvc.perform(get(JS_TREE_URI).param("lang", "en_US"))
                .andExpect(status().isOk());

        verify(propertyRepository).findAllByOntology("efo", "en_US", PageRequest.of(0, 1000));
        verify(propertyRepository).getParents(
                "efo", PROPERTY_IRI, "en_US", PageRequest.of(0, 1000));
        verify(jsTreeRepository).getJsTreeForProperty(PROPERTY_IRI, "efo", "en_US");
    }

    @Test
    void supportsLegacyHalMediaTypeOnEveryHalRoute() throws Exception {
        for (URI uri : List.of(
                LIST_URI, ROOTS_URI, PROPERTY_URI, PARENTS_URI,
                CHILDREN_URI, DESCENDANTS_URI, ANCESTORS_URI, JS_TREE_URI)) {
            mockMvc.perform(get(uri).accept(MediaTypes.HAL_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
        }
    }

    @Test
    void returnsStableLegacyNotFoundStatusAndMessage() throws Exception {
        when(propertyRepository.findByOntologyAndIri("efo", PROPERTY_IRI, "en"))
                .thenReturn(null);

        mockMvc.perform(get(PROPERTY_URI))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertEquals(
                        "EntityModel not found", result.getResponse().getErrorMessage()))
                .andExpect(content().string(""));
    }

    @Test
    void returnsStableBadRequestFieldsForUnsupportedSort() throws Exception {
        Pageable pageable = PageRequest.of(
                0, 1000, org.springframework.data.domain.Sort.by("bad"));
        when(propertyRepository.getParents("efo", PROPERTY_IRI, "en", pageable))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: bad"));

        mockMvc.perform(get(PARENTS_URI).param("sort", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
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
            case "parents" -> PARENTS_URI;
            case "children" -> CHILDREN_URI;
            case "descendants" -> DESCENDANTS_URI;
            case "ancestors" -> ANCESTORS_URI;
            default -> throw new IllegalArgumentException("Unknown route: " + route);
        };
    }

    private static OlsFacetedResultsPage<V1Property> propertyPage(
            Pageable pageable,
            String lang) {
        return new OlsFacetedResultsPage<>(
                List.of(property(lang)), Map.of(), pageable, 1);
    }

    private static V1Property property(String lang) {
        V1Property property = new V1Property();
        property.iri = PROPERTY_IRI;
        property.lang = lang;
        property.label = "has material";
        property.description = new String[]{"A specimen relation used in hierarchy tests."};
        property.synonyms = new String[]{"material relation"};
        property.ontologyName = "efo";
        property.ontologyPrefix = "EFO";
        property.ontologyIri = "http://www.ebi.ac.uk/efo";
        property.isObsolete = false;
        property.isLocal = true;
        property.hasChildren = false;
        property.isRoot = false;
        property.shortForm = "EFO_0101";
        property.oboId = "EFO:0101";
        property.annotation = Map.of();
        return property;
    }
}
