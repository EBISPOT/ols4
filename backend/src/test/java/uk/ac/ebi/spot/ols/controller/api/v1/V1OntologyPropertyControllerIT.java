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
class V1OntologyPropertyControllerIT {

    private static final URI PROPERTY_URI = URI.create(
            "/api/ontologies/EFO/properties/http%253A%252F%252Fexample.org%252FEFO_0101");
    private static final URI ROOTS_URI = URI.create("/api/ontologies/EFO/properties/roots");
    private static final URI PARENT_URI = URI.create(
            "/api/ontologies/EFO/properties/http%253A%252F%252Fexample.org%252FEFO_0100");
    private static final URI PARENTS_URI = URI.create(PROPERTY_URI + "/parents");
    private static final URI CHILDREN_URI = URI.create(PARENT_URI + "/children");
    private static final URI DESCENDANTS_URI = URI.create(PARENT_URI + "/descendants");
    private static final URI ANCESTORS_URI = URI.create(PROPERTY_URI + "/ancestors");
    private static final URI JS_TREE_URI = URI.create(PROPERTY_URI + "/jstree");
    // Base64 encoding of "http://example.org/EFO_0100" — the opaque node id a client obtains
    // from a prior /jstree response and echoes back to expand that node's children.
    private static final URI JS_TREE_CHILDREN_URI = URI.create(
            PARENT_URI + "/jstree/children/aHR0cDovL2V4YW1wbGUub3JnL0VGT18wMTAw");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.V1OntologyPropertyRepositoryHandle
            repositoryHandle;
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUpApplicationPath() {
        PostgresIntegrationTestSupport.initializePropertyDatabase(POSTGRES);
        repositoryHandle =
                PostgresIntegrationTestSupport.createV1OntologyPropertyRepositories(POSTGRES);

        V1OntologyPropertyController controller = new V1OntologyPropertyController();
        ReflectionTestUtils.setField(
                controller, "propertyRepository", repositoryHandle.propertyRepository());
        ReflectionTestUtils.setField(
                controller, "jsTreeRepository", repositoryHandle.jsTreeRepository());
        controller.termAssembler = new V1PropertyAssembler();

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
    void listsOntologyPropertiesThroughControllerRepositoryAndPostgres() throws Exception {
        mockMvc.perform(get("/api/ontologies/EFO/properties"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$._embedded.properties[0].iri")
                        .value("http://example.org/EFO_0100"))
                .andExpect(jsonPath("$._embedded.properties[2].is_obsolete").value(true));
    }

    @Test
    void getsDoubleEncodedOntologyPropertyThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(PROPERTY_URI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ontology_name").value("efo"))
                .andExpect(jsonPath("$.iri").value("http://example.org/EFO_0101"))
                .andExpect(jsonPath("$.label").value("has material"))
                .andExpect(jsonPath("$.lang").value("fr"));
    }

    @Test
    void getsRootsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(ROOTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.properties[0].iri")
                        .value("http://example.org/EFO_0100"));
    }

    @Test
    void getsDirectPropertyParentsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(PARENTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.properties[0].iri")
                        .value("http://example.org/EFO_0100"));
    }

    @Test
    void getsDirectPropertyChildrenThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(CHILDREN_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.properties[0].iri")
                        .value("http://example.org/EFO_0101"));
    }

    @Test
    void getsPropertyDescendantsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(DESCENDANTS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.properties[0].iri")
                        .value("http://example.org/EFO_0101"));
    }

    @Test
    void getsPropertyAncestorsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(ANCESTORS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.properties[0].iri")
                        .value("http://example.org/EFO_0100"));
    }

    @Test
    void getsPropertyJsTreeChildrenThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(JS_TREE_CHILDREN_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].iri").value("http://example.org/EFO_0101"))
                .andExpect(jsonPath("$[0].text").value("has material"));
    }

    @Test
    void getsPropertyJsTreeThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(JS_TREE_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].iri").value("http://example.org/EFO_0100"))
                .andExpect(jsonPath("$[0].text").value("has specimen"))
                .andExpect(jsonPath("$[0].parent").value("#"))
                .andExpect(jsonPath("$[1].iri").value("http://example.org/EFO_0101"))
                .andExpect(jsonPath("$[1].text").value("has material"))
                .andExpect(jsonPath("$[1].state.selected").value(true));
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
