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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
class V2OntologyControllerIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.RepositoryHandle repositoryHandle;
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUpApplicationPath() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createRepository(POSTGRES);

        V2OntologyController controller = new V2OntologyController();
        controller.ontologyRepository = repositoryHandle.repository();
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void listsActiveOntologiesThroughControllerRepositoryAndPostgres() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.elements[0].ontologyId").value("duo"))
                .andExpect(jsonPath("$.elements[1].ontologyId").value("efo"))
                .andExpect(jsonPath("$.elements[2].ontologyId").value("efo-atlas"));
    }

    @Test
    void groupsOntologiesByTagThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/by-tag"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biomedical.length()").value(2))
                .andExpect(jsonPath("$.clinical[0].ontologyId").value("duo"));
    }

    @Test
    void groupsOntologiesByDomainThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/by-domain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.information[0].ontologyId").value("duo"))
                .andExpect(jsonPath("$.biology.length()").value(3));
    }

    @Test
    void getsOntologyByIdThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ontologyId").value("efo"))
                .andExpect(jsonPath("$.title").value("Experimental Factor Ontology"));
    }
}
