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
class V1OntologyIndividualControllerIT {

    private static final URI INDIVIDUAL_URI = URI.create(
            "/api/ontologies/EFO/individuals/http%253A%252F%252Fexample.org%252FEFO_I100");
    private static final URI TYPES_URI = URI.create(INDIVIDUAL_URI + "/types");
    private static final URI ALL_TYPES_URI = URI.create(INDIVIDUAL_URI + "/alltypes");
    private static final URI JS_TREE_URI = URI.create(INDIVIDUAL_URI + "/jstree");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.V1OntologyIndividualRepositoryHandle
            repositoryHandle;
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUpApplicationPath() {
        PostgresIntegrationTestSupport.initializeIndividualDatabase(POSTGRES);
        repositoryHandle =
                PostgresIntegrationTestSupport.createV1OntologyIndividualRepositories(POSTGRES);

        V1OntologyIndividualController controller = new V1OntologyIndividualController();
        ReflectionTestUtils.setField(
                controller, "individualRepository", repositoryHandle.individualRepository());
        ReflectionTestUtils.setField(
                controller, "jsTreeRepository", repositoryHandle.jsTreeRepository());
        controller.individualAssembler = new V1IndividualAssembler();
        controller.termAssembler = new V1TermAssembler();

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
    void listsOntologyIndividualsThroughControllerRepositoryAndPostgres() throws Exception {
        mockMvc.perform(get("/api/ontologies/EFO/individuals"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$._embedded.individuals[0].iri")
                        .value("http://example.org/EFO_I100"))
                .andExpect(jsonPath("$._embedded.individuals[1].is_defining_ontology")
                        .value(false))
                .andExpect(jsonPath("$._embedded.individuals[2].is_obsolete").value(true));
    }

    @Test
    void getsDoubleEncodedOntologyIndividualThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(INDIVIDUAL_URI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ontology_name").value("efo"))
                .andExpect(jsonPath("$.iri").value("http://example.org/EFO_I100"))
                .andExpect(jsonPath("$.label").value("Liver specimen alpha"))
                .andExpect(jsonPath("$.lang").value("fr"));
    }

    @Test
    void getsDirectIndividualTypesThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(TYPES_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_0001"))
                .andExpect(jsonPath("$._embedded.terms[0].label").value("Liver disease"));
    }

    @Test
    void getsAllIndividualTypesThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(ALL_TYPES_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_0001"));
    }

    @Test
    void getsIndividualJsTreeThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(JS_TREE_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].iri").value("http://example.org/EFO_0001"))
                .andExpect(jsonPath("$[0].text").value("Liver disease"))
                .andExpect(jsonPath("$[0].parent").value("#"))
                .andExpect(jsonPath("$[1].iri").value("http://example.org/EFO_I100"))
                .andExpect(jsonPath("$[1].text").value("Liver specimen alpha"))
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
