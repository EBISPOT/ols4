package uk.ac.ebi.spot.ols.controller.api.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.HateoasPageableHandlerMethodArgumentResolver;
import org.springframework.data.web.PagedResourcesAssemblerArgumentResolver;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.mediatype.MessageResolver;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.mediatype.hal.Jackson2HalModule;
import org.springframework.hateoas.server.core.AnnotationLinkRelationProvider;
import org.springframework.hateoas.server.core.DelegatingLinkRelationProvider;
import org.springframework.hateoas.server.core.EvoInflectorLinkRelationProvider;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers all of V1OntologyTermController's routes against real Postgres: the core
 * list/roots/preferredRoots/single/parents/children/descendants/ancestors/jstree routes
 * (milestone 1, PR #1393), plus the hierarchical-variant routes, /graph, /related and the
 * ontology-root-level shortcut routes (milestone 2, PR #1395).
 */
@Testcontainers
class V1OntologyTermControllerIT {

    private static final URI TERM_URI = URI.create(
            "/api/ontologies/EFO/terms/http%253A%252F%252Fexample.org%252FEFO_1001");
    private static final URI PARENT_URI = URI.create(
            "/api/ontologies/EFO/terms/http%253A%252F%252Fexample.org%252FEFO_0001");
    private static final URI LIST_URI = URI.create("/api/ontologies/EFO/terms");
    private static final URI ROOTS_URI = URI.create("/api/ontologies/EFO/terms/roots");
    private static final URI PREFERRED_ROOTS_URI =
            URI.create("/api/ontologies/EFO/terms/preferredRoots");
    private static final URI PARENTS_URI = URI.create(TERM_URI + "/parents");
    private static final URI CHILDREN_URI = URI.create(PARENT_URI + "/children");
    private static final URI DESCENDANTS_URI = URI.create(PARENT_URI + "/descendants");
    private static final URI ANCESTORS_URI = URI.create(TERM_URI + "/ancestors");
    private static final URI JS_TREE_URI = URI.create(TERM_URI + "/jstree");
    // Base64 encoding of "http://example.org/EFO_0001" — the opaque node id a client obtains
    // from a prior /jstree response and echoes back to expand that node's children.
    private static final URI JS_TREE_CHILDREN_URI = URI.create(
            PARENT_URI + "/jstree/children/aHR0cDovL2V4YW1wbGUub3JnL0VGT18wMDAx");
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
            "/api/ontologies/EFO/terms/http%253A%252F%252Fexample.org%252FEFO_0002"
                    + "/http%253A%252F%252Fexample.org%252Frelated");
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

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.V1OntologyTermRepositoryHandle repositoryHandle;
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUpApplicationPath() {
        PostgresIntegrationTestSupport.initializeClassDatabase(POSTGRES);
        repositoryHandle =
                PostgresIntegrationTestSupport.createV1OntologyTermRepositories(POSTGRES);

        V1OntologyTermController controller = new V1OntologyTermController();
        ReflectionTestUtils.setField(
                controller, "termRepository", repositoryHandle.termRepository());
        controller.jsTreeRepository = repositoryHandle.jsTreeRepository();
        controller.graphRepository = repositoryHandle.graphRepository();
        controller.termAssembler = new V1TermAssembler();
        controller.preferredRootTermAssembler = new V1PreferredRootTermAssembler();

        HateoasPageableHandlerMethodArgumentResolver pageableResolver =
                new HateoasPageableHandlerMethodArgumentResolver();
        pageableResolver.setMaxPageSize(1000);
        PagedResourcesAssemblerArgumentResolver assemblerResolver =
                new PagedResourcesAssemblerArgumentResolver(pageableResolver);

        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .setCustomArgumentResolvers(pageableResolver, assemblerResolver)
                .setMessageConverters(
                        new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        halMessageConverter())
                .build();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void listsOntologyTermsThroughControllerRepositoryAndPostgres() throws Exception {
        mockMvc.perform(get(LIST_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_0001"));
    }

    @Test
    void getsDoubleEncodedOntologyTermThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(TERM_URI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ontology_name").value("efo"))
                .andExpect(jsonPath("$.iri").value("http://example.org/EFO_1001"))
                .andExpect(jsonPath("$.label").value("Clinical liver child"))
                .andExpect(jsonPath("$.lang").value("fr"));
    }

    @Test
    void getsRootsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(ROOTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_0001"));
    }

    @Test
    void getsPreferredRootsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(PREFERRED_ROOTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_1001"));
    }

    @Test
    void getsDirectTermParentsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(PARENTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_0001"));
    }

    @Test
    void getsDirectTermChildrenThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(CHILDREN_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_1001"));
    }

    @Test
    void getsTermDescendantsThroughTheRealDatabase() throws Exception {
        // Includes the fixture's synthetic individual (EFO_I100), which shares EFO_0001 as an
        // ancestor — confirmed against the committed system-regression baseline that V1's
        // /descendants route intentionally includes individuals asserted into a class.
        mockMvc.perform(get(DESCENDANTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_1001"));
    }

    @Test
    void getsTermAncestorsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(ANCESTORS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_0001"));
    }

    @Test
    void getsTermJsTreeThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(JS_TREE_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].iri").value("http://example.org/EFO_0001"))
                .andExpect(jsonPath("$[0].text").value("Liver disease"))
                .andExpect(jsonPath("$[0].parent").value("#"))
                .andExpect(jsonPath("$[1].iri").value("http://example.org/EFO_1001"))
                .andExpect(jsonPath("$[1].text").value("Clinical liver child"))
                .andExpect(jsonPath("$[1].state.selected").value(true));
    }

    @Test
    void getsTermJsTreeChildrenThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(JS_TREE_CHILDREN_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].iri").value("http://example.org/EFO_1001"))
                .andExpect(jsonPath("$[0].text").value("Clinical liver child"));
    }

    @Test
    void getsHierarchicalParentsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(HIERARCHICAL_PARENTS_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_0001"));
    }

    @Test
    void getsHierarchicalAncestorsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(HIERARCHICAL_ANCESTORS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_0001"));
    }

    @Test
    void getsHierarchicalChildrenThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(HIERARCHICAL_CHILDREN_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_1001"));
    }

    @Test
    void getsHierarchicalDescendantsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(HIERARCHICAL_DESCENDANTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_1001"));
    }

    @Test
    void getsTheClassGraphThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(GRAPH_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.nodes[*].iri").isArray())
                .andExpect(jsonPath("$.edges[0].source")
                        .value("http://example.org/EFO_1001"))
                .andExpect(jsonPath("$.edges[0].target")
                        .value("http://example.org/EFO_0001"))
                .andExpect(jsonPath("$.edges[0].uri")
                        .value("http://www.w3.org/2000/01/rdf-schema#subClassOf"));
    }

    @Test
    void getsRelatedEntitiesThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(RELATED_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_0001"));
    }

    @Test
    void getsShortcutChildrenThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(SHORTCUT_CHILDREN_URI).param("iri", "http://example.org/EFO_0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_1001"));
    }

    @Test
    void getsShortcutDescendantsThroughTheRealDatabase() throws Exception {
        // Delegates to the same getDescendants used by the direct /terms/{iri}/descendants route
        // above, which includes the fixture's synthetic individual (EFO_I100) alongside the two
        // classes — confirmed intended V1 behavior there, not a defect.
        mockMvc.perform(get(SHORTCUT_DESCENDANTS_URI)
                        .param("iri", "http://example.org/EFO_0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_1001"));
    }

    @Test
    void getsShortcutParentsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(SHORTCUT_PARENTS_URI).param("iri", "http://example.org/EFO_1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_0001"));
    }

    @Test
    void getsShortcutAncestorsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(SHORTCUT_ANCESTORS_URI).param("iri", "http://example.org/EFO_1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_0001"));
    }

    @Test
    void getsShortcutHierarchicalChildrenThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(SHORTCUT_HIERARCHICAL_CHILDREN_URI)
                        .param("iri", "http://example.org/EFO_0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_1001"));
    }

    @Test
    void getsShortcutHierarchicalDescendantsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(SHORTCUT_HIERARCHICAL_DESCENDANTS_URI)
                        .param("iri", "http://example.org/EFO_0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_1001"));
    }

    @Test
    void getsShortcutHierarchicalAncestorsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(SHORTCUT_HIERARCHICAL_ANCESTORS_URI)
                        .param("iri", "http://example.org/EFO_1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_0001"));
    }

    private static MappingJackson2HttpMessageConverter halMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jackson2HalModule());
        objectMapper.setHandlerInstantiator(new Jackson2HalModule.HalHandlerInstantiator(
                new DelegatingLinkRelationProvider(
                        new AnnotationLinkRelationProvider(),
                        new EvoInflectorLinkRelationProvider()),
                CurieProvider.NONE,
                MessageResolver.DEFAULTS_ONLY));

        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(objectMapper);
        converter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON, MediaTypes.HAL_JSON));
        return converter;
    }
}
