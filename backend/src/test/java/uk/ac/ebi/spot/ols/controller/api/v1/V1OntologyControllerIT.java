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

import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
class V1OntologyControllerIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.V1RepositoryHandle repositoryHandle;
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUpApplicationPath() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createV1Repository(POSTGRES);

        V1OntologyController controller = new V1OntologyController();
        ReflectionTestUtils.setField(controller, "ontologyRepository", repositoryHandle.repository());
        controller.documentAssembler = new V1OntologyAssembler();
        controller.termAssembler = new V1TermAssembler();

        HateoasPageableHandlerMethodArgumentResolver pageableResolver =
                new HateoasPageableHandlerMethodArgumentResolver();
        pageableResolver.setMaxPageSize(1000);
        PagedResourcesAssemblerArgumentResolver assemblerResolver =
                new PagedResourcesAssemblerArgumentResolver(pageableResolver);
        MappingJackson2HttpMessageConverter halConverter = halMessageConverter();

        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .setCustomArgumentResolvers(pageableResolver, assemblerResolver)
                .setMessageConverters(halConverter)
                .build();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void listsOntologiesThroughControllerRepositoryAndPostgres() throws Exception {
        mockMvc.perform(get("/api/ontologies"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$._embedded.ontologies[0].ontologyId").value("duo"))
                .andExpect(jsonPath("$._embedded.ontologies[0].config.title")
                        .value("Data Use Ontology"))
                .andExpect(jsonPath("$._embedded.ontologies[3].ontologyId")
                        .value("legacy-efo"));
    }

    @Test
    void getsOntologyByCaseInsensitiveIdThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get("/api/ontologies/EFO"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ontologyId").value("efo"))
                .andExpect(jsonPath("$.lang").value("en"))
                .andExpect(jsonPath("$.numberOfTerms").value(100))
                .andExpect(jsonPath("$.config.title").value("Experimental Factor Ontology"))
                .andExpect(jsonPath("$._links.self.href")
                        .value(endsWith("/api/ontologies/efo?lang=en")));
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
