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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

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
    private static final URI HIERARCHICAL_CHILDREN_URI = uri(
            "/api/v2/ontologies/efo/individuals/http%253A%252F%252Fexample.org%252FEFO_I100/hierarchicalChildren");
    private static final URI HIERARCHICAL_CHILDREN_WITH_OBSOLETE_URI = uri(
            "/api/v2/ontologies/efo/individuals/http%253A%252F%252Fexample.org%252FEFO_I100/hierarchicalChildren?includeObsoleteEntities=true");
    private static final URI HIERARCHICAL_ANCESTORS_URI = uri(
            "/api/v2/ontologies/efo/individuals/http%253A%252F%252Fexample.org%252FEFO_I200/hierarchicalAncestors");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.IndividualRepositoryHandle repositoryHandle;
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUpApplicationPath() throws SQLException {
        PostgresIntegrationTestSupport.initializeIndividualDatabase(POSTGRES);
        // A hierarchical property (e.g. COHO isSubCohortOf) makes EFO_I200 and the
        // obsolete EFO_I999 hierarchical children of EFO_I100.
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE ols_entities
                    SET hierarchical_parents = ARRAY['http://example.org/EFO_I100'],
                        hierarchical_ancestors = ARRAY['http://example.org/EFO_I100']
                    WHERE id IN (
                        'efo+individual+http://example.org/EFO_I200',
                        'efo+individual+http://example.org/EFO_I999')
                    """);
        }
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

    @Test
    void getsIndividualHierarchicalChildrenThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(HIERARCHICAL_CHILDREN_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri").value("http://example.org/EFO_I200"));
    }

    @Test
    void includesObsoleteIndividualHierarchicalChildrenWhenRequested() throws Exception {
        mockMvc.perform(get(HIERARCHICAL_CHILDREN_WITH_OBSOLETE_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.elements[0].iri").value("http://example.org/EFO_I200"))
                .andExpect(jsonPath("$.elements[1].iri").value("http://example.org/EFO_I999"));
    }

    @Test
    void getsIndividualHierarchicalAncestorsThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(HIERARCHICAL_ANCESTORS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri").value("http://example.org/EFO_I100"));
    }

    private static URI uri(String value) {
        return URI.create(value);
    }
}
