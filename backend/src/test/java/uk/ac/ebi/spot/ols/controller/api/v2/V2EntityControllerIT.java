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
class V2EntityControllerIT {

    private static final URI ENTITY_URI = URI.create(
            "/api/v2/ontologies/efo/entities/http%253A%252F%252Fexample.org%252FEFO_0001");
    private static final URI RELATED_FROM_URI = URI.create(
            "/api/v2/ontologies/efo/entities/http%253A%252F%252Fexample.org%252FEFO_0001/relatedFrom");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.EntityRepositoryHandle repositoryHandle;
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUpApplicationPath() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createEntityRepository(POSTGRES);

        V2EntityController controller = new V2EntityController();
        controller.entityRepository = repositoryHandle.repository();
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
    void listsEntitiesThroughControllerRepositoryAndPostgres() throws Exception {
        mockMvc.perform(get("/api/v2/entities"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(4))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.elements[0].ontologyId").value("duo"))
                .andExpect(jsonPath("$.elements[3].type[1]").value("property"));
    }

    @Test
    void listsOntologyEntitiesThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/entities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.elements[0].iri")
                        .value("http://example.org/EFO_0001"))
                .andExpect(jsonPath("$.elements[2].iri")
                        .value("http://example.org/EFO_0100"));
    }

    @Test
    void getsDoubleEncodedEntityThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(ENTITY_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ontologyId").value("efo"))
                .andExpect(jsonPath("$.iri").value("http://example.org/EFO_0001"))
                .andExpect(jsonPath("$.label").value("Liver disease"));
    }

    @Test
    void getsRelatedFromEntitiesThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(RELATED_FROM_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri")
                        .value("http://example.org/EFO_0002"));
    }
}
