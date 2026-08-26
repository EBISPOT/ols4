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
class V1PropertyControllerIT {

    private static final URI PROPERTY_URI = URI.create(
            "/api/properties/http%253A%252F%252Fexample.org%252FEFO_0100");
    private static final URI DEFINING_PROPERTY_URI = URI.create(
            "/api/properties/findByIdAndIsDefiningOntology/"
                    + "http%253A%252F%252Fexample.org%252FEFO_0100");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.V1PropertyRepositoryHandle repositoryHandle;
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUpApplicationPath() {
        PostgresIntegrationTestSupport.initializePropertyDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createV1PropertyRepository(POSTGRES);

        V1PropertyController controller = new V1PropertyController();
        ReflectionTestUtils.setField(
                controller, "propertyRepository", repositoryHandle.repository());
        controller.termAssembler = new V1PropertyAssembler();

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
    void listsPropertiesThroughControllerRepositoryAndPostgres() throws Exception {
        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$._embedded.properties[0].iri")
                        .value("http://example.org/DUO_0100"))
                .andExpect(jsonPath("$._embedded.properties[1].label").value("has specimen"))
                .andExpect(jsonPath("$._embedded.properties[1].is_defining_ontology").value(true))
                .andExpect(jsonPath("$._embedded.properties[1].is_obsolete").value(false))
                .andExpect(jsonPath("$._embedded.properties[3].iri")
                        .value("http://example.org/EFO_0199"))
                .andExpect(jsonPath("$._embedded.properties[3].is_defining_ontology").value(false))
                .andExpect(jsonPath("$._embedded.properties[3].is_obsolete").value(true));
    }

    @Test
    void getsDoubleEncodedPropertyThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(PROPERTY_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.properties[0].ontology_name").value("efo"))
                .andExpect(jsonPath("$._embedded.properties[0].iri")
                        .value("http://example.org/EFO_0100"))
                .andExpect(jsonPath("$._embedded.properties[0].label").value("has specimen"))
                .andExpect(jsonPath("$._embedded.properties[0].is_defining_ontology").value(true))
                .andExpect(jsonPath("$._embedded.properties[0].is_obsolete").value(false));
    }

    @Test
    void listsOnlyDefiningOntologyPropertiesThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get("/api/properties/findByIdAndIsDefiningOntology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$._embedded.properties[0].iri")
                        .value("http://example.org/DUO_0100"))
                .andExpect(jsonPath("$._embedded.properties[0].is_defining_ontology").value(true))
                .andExpect(jsonPath("$._embedded.properties[1].iri")
                        .value("http://example.org/EFO_0100"))
                .andExpect(jsonPath("$._embedded.properties[1].is_defining_ontology").value(true))
                .andExpect(jsonPath("$._embedded.properties[2].iri")
                        .value("http://example.org/EFO_0101"))
                .andExpect(jsonPath("$._embedded.properties[2].is_defining_ontology").value(true));
    }

    @Test
    void getsDoubleEncodedDefiningOntologyPropertyThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(DEFINING_PROPERTY_URI).param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._embedded.properties[0].iri")
                        .value("http://example.org/EFO_0100"))
                .andExpect(jsonPath("$._embedded.properties[0].is_defining_ontology").value(true))
                .andExpect(jsonPath("$._embedded.properties[0].is_obsolete").value(false))
                .andExpect(jsonPath("$._embedded.properties[0].lang").value("fr"));
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
