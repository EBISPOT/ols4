package uk.ac.ebi.spot.ols.controller.mcp;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.model.mcp.McpOntology;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thin real-Postgres coverage for {@link McpOntologyService#listOntologies}, on top of the direct
 * unit suite in {@link McpOntologyServiceTest}.
 *
 * <p>{@code OntologyRepository} itself already has full dedicated real-Postgres coverage in
 * {@code OntologyRepositoryIT} (search, filtering, sorting, pagination, boost fields, dynamic
 * properties) against the same shared fixture used here
 * ({@link PostgresIntegrationTestSupport#initializeDatabase}, which loads four ontologies — three
 * active: {@code duo}, {@code efo}, {@code efo-atlas}; one obsolete: {@code legacy-efo}). This
 * class deliberately does not re-prove any of that: it only adds value {@code McpOntologyServiceTest}
 * cannot, by exercising the real {@code McpOntologyService} bean wired to the real
 * {@code OntologyRepository}/Postgres stack rather than a hand-rolled fake, confirming two things
 * only observable end-to-end: (1) {@code listOntologies}'s hardcoded fixed-argument call really does
 * reach real Postgres and return genuine data through {@link McpOntology#fromJson} unchanged, and
 * (2) passing no {@code properties} filter (the fifth hardcoded {@code null} argument) really does
 * mean obsolete ontologies come back too — a materially different, easy-to-regress contract from
 * every other MCP {@code @Tool} method in this programme (e.g. {@code McpClassService.searchClasses},
 * which always filters {@code isObsolete=false}). Two cases are enough to prove this; anything more
 * would duplicate {@code OntologyRepositoryIT}.</p>
 */
@Testcontainers
class McpOntologyServiceIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.RepositoryHandle repositoryHandle;
    private static McpOntologyService service;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createRepository(POSTGRES);

        service = new McpOntologyService();
        service.ontologyRepository = repositoryHandle.repository();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void listOntologiesReturnsEveryOntologyIncludingObsoleteWithDefaultLang() throws Exception {
        List<McpOntology> result = service.listOntologies(null);

        assertThat(result)
                .extracting(ontology -> ontology.ontologyId)
                .containsExactlyInAnyOrder("duo", "efo", "efo-atlas", "legacy-efo");
    }

    @Test
    void listOntologiesMapsRealOntologyFieldsThroughMcpOntologyUnchanged() throws Exception {
        List<McpOntology> result = service.listOntologies("en");

        McpOntology efo = result.stream()
                .filter(ontology -> "efo".equals(ontology.ontologyId))
                .findFirst()
                .orElseThrow();

        assertThat(efo.ontologyId).isEqualTo("efo");
        // The shared fixture's raw entity JSON carries the ontology's metadata under "title"/
        // "description" (proven separately by OntologyRepositoryIT), not literal "label"/
        // "definition" annotation values — so McpOntology.fromJson correctly resolves both to an
        // empty list here rather than null, matching real production behaviour for ontologies with
        // no rdfs:label triple on their owl:Ontology declaration (see the committed golden file
        // testcases_expected_output_api/mcp/listOntologies.json, where most real ontologies -
        // e.g. "duo", "edam" - show the same empty-label/empty-definition shape; only a handful
        // like "skos"/"owl"/"rdfs" carry a genuine rdfs:label on their ontology header).
        assertThat(efo.label).isEmpty();
        assertThat(efo.definition).isEmpty();
    }
}
