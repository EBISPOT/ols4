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
class V2ClassControllerIT {

    private static final URI CLASS_URI = uri(
            "/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_0001");
    private static final URI RELATED_FROM_URI = uri(
            "/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_0001/relatedFrom");
    private static final URI CHILDREN_URI = uri(
            "/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_0001/children");
    private static final URI ANCESTORS_URI = uri(
            "/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_1001/ancestors");
    private static final URI DESCENDANTS_URI = uri(
            "/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_0001/descendants");
    private static final URI HIERARCHICAL_DESCENDANTS_URI = uri(
            "/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_0001/hierarchicalDescendants");
    private static final URI HIERARCHICAL_CHILDREN_URI = uri(
            "/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_0001/hierarchicalChildren");
    private static final URI HIERARCHICAL_ANCESTORS_URI = uri(
            "/api/v2/ontologies/efo/classes/http%253A%252F%252Fexample.org%252FEFO_1001/hierarchicalAncestors");
    private static final URI INDIVIDUAL_ANCESTORS_URI = uri(
            "/api/v2/ontologies/efo/individuals/http%253A%252F%252Fexample.org%252FEFO_I100/ancestors");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.ClassRepositoryHandle repositoryHandle;
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUpApplicationPath() {
        PostgresIntegrationTestSupport.initializeClassDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createClassRepository(POSTGRES);

        V2ClassController controller = new V2ClassController();
        controller.classRepository = repositoryHandle.repository();
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
    void listsClassesThroughControllerRepositoryAndPostgres() throws Exception {
        mockMvc.perform(get("/api/v2/classes"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.numElements").value(4))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.elements[0].iri")
                        .value("http://example.org/DUO_0001"))
                .andExpect(jsonPath("$.elements[3].iri")
                        .value("http://example.org/EFO_1001"));
    }

    @Test
    void listsOntologyClassesThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/classes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.elements[0].iri")
                        .value("http://example.org/EFO_0001"))
                .andExpect(jsonPath("$.elements[2].iri")
                        .value("http://example.org/EFO_1001"));
    }

    @Test
    void getsDoubleEncodedClassThroughTheRealDatabase() throws Exception {
        mockMvc.perform(get(CLASS_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ontologyId").value("efo"))
                .andExpect(jsonPath("$.iri").value("http://example.org/EFO_0001"))
                .andExpect(jsonPath("$.label").value("Liver disease"));
    }

    @Test
    void getsRelatedFromClassesThroughTheRealDatabase() throws Exception {
        assertSingleRelationship(RELATED_FROM_URI, "http://example.org/EFO_0002");
    }

    @Test
    void getsClassChildrenThroughTheRealDatabase() throws Exception {
        assertSingleRelationship(CHILDREN_URI, "http://example.org/EFO_1001");
    }

    @Test
    void getsClassAncestorsThroughTheRealDatabase() throws Exception {
        assertSingleRelationship(ANCESTORS_URI, "http://example.org/EFO_0001");
    }

    @Test
    void getsClassDescendantsThroughTheRealDatabase() throws Exception {
        assertSingleRelationship(DESCENDANTS_URI, "http://example.org/EFO_1001");
    }

    @Test
    void getsHierarchicalDescendantsThroughTheRealDatabase() throws Exception {
        assertSingleRelationship(HIERARCHICAL_DESCENDANTS_URI, "http://example.org/EFO_1001");
    }

    @Test
    void getsHierarchicalChildrenThroughTheRealDatabase() throws Exception {
        assertSingleRelationship(HIERARCHICAL_CHILDREN_URI, "http://example.org/EFO_1001");
    }

    @Test
    void getsHierarchicalAncestorsThroughTheRealDatabase() throws Exception {
        assertSingleRelationship(HIERARCHICAL_ANCESTORS_URI, "http://example.org/EFO_0001");
    }

    @Test
    void getsIndividualAncestorsThroughTheRealDatabase() throws Exception {
        assertSingleRelationship(INDIVIDUAL_ANCESTORS_URI, "http://example.org/EFO_0001");
    }

    private static void assertSingleRelationship(URI uri, String expectedIri) throws Exception {
        mockMvc.perform(get(uri))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numElements").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].iri").value(expectedIri));
    }

    private static URI uri(String value) {
        return URI.create(value);
    }
}
