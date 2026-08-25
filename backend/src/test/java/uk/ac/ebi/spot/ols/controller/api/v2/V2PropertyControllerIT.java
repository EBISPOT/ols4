package uk.ac.ebi.spot.ols.controller.api.v2;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.net.URI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
class V2PropertyControllerIT {

    private static final URI PROPERTY_URI = URI.create(
            "/api/v2/ontologies/efo/properties/http%253A%252F%252Fexample.org%252FEFO_0100");
    private static final URI CHILDREN_URI = URI.create(
            "/api/v2/ontologies/efo/properties/http%253A%252F%252Fexample.org%252FEFO_0100/children");
    private static final URI ANCESTORS_URI = URI.create(
            "/api/v2/ontologies/efo/properties/http%253A%252F%252Fexample.org%252FEFO_0101/ancestors");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.PropertyRepositoryHandle repositoryHandle;
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUpApplicationPath() {
        PostgresIntegrationTestSupport.initializePropertyDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createPropertyRepository(POSTGRES);

        V2PropertyController controller = new V2PropertyController();
        controller.propertyRepository = repositoryHandle.repository();
        PageableHandlerMethodArgumentResolver pageableResolver =
                new PageableHandlerMethodArgumentResolver();
        pageableResolver.setMaxPageSize(1000);
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(pageableResolver)
                .build();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void listsPropertiesThroughControllerRepositoryAndPostgres() throws Exception {
        mockMvc.perform(get("/api/v2/properties"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.elements[0].iri")
                        .value("http://example.org/DUO_0100"))
                .andExpect(jsonPath("$.elements[2].iri")
                        .value("http://example.org/EFO_0101"));
    }

    @Test
    void listsOntologyPropertiesThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.elements[0].iri")
                        .value("http://example.org/EFO_0100"))
                .andExpect(jsonPath("$.elements[1].iri")
                        .value("http://example.org/EFO_0101"));
    }

    @Test
    void getsDoubleEncodedPropertyThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(PROPERTY_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ontologyId").value("efo"))
                .andExpect(jsonPath("$.iri").value("http://example.org/EFO_0100"))
                .andExpect(jsonPath("$.label").value("has specimen"));
    }

    @Test
    void getsPropertyChildrenThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(CHILDREN_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri")
                        .value("http://example.org/EFO_0101"));
    }

    @Test
    void getsPropertyAncestorsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(ANCESTORS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri")
                        .value("http://example.org/EFO_0100"));
    }
}
