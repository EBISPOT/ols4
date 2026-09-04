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
