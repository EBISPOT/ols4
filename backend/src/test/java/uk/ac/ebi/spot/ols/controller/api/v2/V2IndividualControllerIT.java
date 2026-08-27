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
class V2IndividualControllerIT {

    private static final URI INDIVIDUAL_URI = uri(
            "/api/v2/ontologies/efo/individuals/http%253A%252F%252Fexample.org%252FEFO_I100");
    private static final URI CLASS_INDIVIDUALS_URI = uri(
            "/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_0001/individuals");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.IndividualRepositoryHandle repositoryHandle;
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUpApplicationPath() {
        PostgresIntegrationTestSupport.initializeIndividualDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createIndividualRepository(POSTGRES);

        V2IndividualController controller = new V2IndividualController();
        controller.individualRepository = repositoryHandle.repository();
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
    void listsIndividualsThroughControllerRepositoryAndPostgres() throws Exception {
        mockMvc.perform(get("/api/v2/individuals"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.elements[0].iri").value("http://example.org/DUO_I100"))
                .andExpect(jsonPath("$.elements[2].iri").value("http://example.org/EFO_I200"));
    }

    @Test
    void listsOntologyIndividualsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/individuals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.elements[0].iri").value("http://example.org/EFO_I100"))
                .andExpect(jsonPath("$.elements[1].iri").value("http://example.org/EFO_I200"));
    }

    @Test
    void getsDoubleEncodedIndividualThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(INDIVIDUAL_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ontologyId").value("efo"))
                .andExpect(jsonPath("$.iri").value("http://example.org/EFO_I100"))
                .andExpect(jsonPath("$.label").value("Liver specimen alpha"));
    }

    @Test
    void listsActiveClassIndividualsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(CLASS_INDIVIDUALS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri").value("http://example.org/EFO_I100"));
    }

    private static URI uri(String value) {
        return URI.create(value);
    }
}
