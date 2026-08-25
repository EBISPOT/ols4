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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.net.URI;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
class V1TermControllerIT {

    private static final URI TERM_URI = URI.create(
            "/api/terms/http%253A%252F%252Fexample.org%252FEFO_0001");
    private static final URI DEFINING_TERM_URI = URI.create(
            "/api/terms/findByIdAndIsDefiningOntology/"
                    + "http%253A%252F%252Fexample.org%252FEFO_0001");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.V1TermRepositoryHandle repositoryHandle;
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUpApplicationPath() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createV1TermRepository(POSTGRES);

        V1TermController controller = new V1TermController();
        ReflectionTestUtils.setField(controller, "termRepository", repositoryHandle.repository());
        controller.termAssembler = new V1TermAssembler();

        HateoasPageableHandlerMethodArgumentResolver pageableResolver =
                new HateoasPageableHandlerMethodArgumentResolver();
        pageableResolver.setMaxPageSize(1000);
        PagedResourcesAssemblerArgumentResolver assemblerResolver =
                new PagedResourcesAssemblerArgumentResolver(pageableResolver);

        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .setCustomArgumentResolvers(pageableResolver, assemblerResolver)
                .setMessageConverters(halMessageConverter())
                .build();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void listsTermsThroughControllerRepositoryAndPostgres() throws Exception {
        mockMvc.perform(get("/api/terms"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/DUO_0001"))
                .andExpect(jsonPath("$._embedded.terms[1].label").value("Liver disease"))
                .andExpect(jsonPath("$._embedded.terms[3].is_obsolete").value(true));
    }

    @Test
    void getsDoubleEncodedTermThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(TERM_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.terms[0].ontology_name").value("efo"))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_0001"))
                .andExpect(jsonPath("$._embedded.terms[0].label").value("Liver disease"));
    }

    @Test
    void listsOnlyDefiningOntologyTermsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get("/api/terms/findByIdAndIsDefiningOntology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/DUO_0001"))
                .andExpect(jsonPath("$._embedded.terms[1].iri")
                        .value("http://example.org/EFO_0001"))
                .andExpect(jsonPath("$._embedded.terms[2].iri")
                        .value("http://example.org/EFO_0999"));
    }

    @Test
    void getsDoubleEncodedDefiningOntologyTermThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(DEFINING_TERM_URI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.terms[0].iri")
                        .value("http://example.org/EFO_0001"))
                .andExpect(jsonPath("$._embedded.terms[0].lang").value("fr"))
                .andExpect(jsonPath("$._embedded.terms[0].is_defining_ontology").value(true));
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
