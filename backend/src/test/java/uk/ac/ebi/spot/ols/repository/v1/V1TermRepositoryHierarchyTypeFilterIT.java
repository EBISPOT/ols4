package uk.ac.ebi.spot.ols.repository.v1;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.service.PostgresClient;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The committed class fixture also carries a synthetic individual (EFO_I100) whose
 * direct_ancestors array points at the same root class (EFO_0001) used for hierarchy tests —
 * modelling a real RDF pattern where an individual has a transitive class ancestor. V2's
 * {@code ClassRepository} was patched in PR #1373 to restrict hierarchy lookups to
 * {@code type = OntologyClass} after this exact cross-type leak was found. V1TermRepository's
 * getParents/getChildren/getDescendants/getAncestors never received that fix: they call the raw
 * Postgres hierarchy lookups with an empty node-property filter, so a class's children/descendants
 * can include individuals (or properties) that merely share the same ancestor IRI.
 */
@Testcontainers
class V1TermRepositoryHierarchyTypeFilterIT {

    private static final String ROOT_IRI = "http://example.org/EFO_0001";
    private static final String CHILD_IRI = "http://example.org/EFO_1001";
    private static final String INDIVIDUAL_IRI = "http://example.org/EFO_I100";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    @Test
    void hierarchyRoutesDoNotLeakNonClassEntitiesSharingTheSameAncestorIri() {
        PostgresIntegrationTestSupport.initializeClassDatabase(POSTGRES);

        PostgresClient postgresClient = new PostgresClient();
        ReflectionTestUtils.setField(postgresClient, "host", POSTGRES.getHost());
        ReflectionTestUtils.setField(postgresClient, "port", POSTGRES.getMappedPort(5432));
        ReflectionTestUtils.setField(postgresClient, "database", POSTGRES.getDatabaseName());
        ReflectionTestUtils.setField(postgresClient, "user", POSTGRES.getUsername());
        ReflectionTestUtils.setField(postgresClient, "password", POSTGRES.getPassword());
        ReflectionTestUtils.setField(postgresClient, "schema", "public");
        ReflectionTestUtils.setField(postgresClient, "maxPoolSize", 3);
        ReflectionTestUtils.setField(postgresClient, "minIdle", 0);
        postgresClient.init();

        OlsSearchClient searchClient = new OlsSearchClient();
        ReflectionTestUtils.setField(searchClient, "postgresClient", postgresClient);

        OlsPostgresClient olsPostgresClient = new OlsPostgresClient();
        ReflectionTestUtils.setField(olsPostgresClient, "postgresClient", postgresClient);

        V1TermRepository repository = new V1TermRepository();
        ReflectionTestUtils.setField(repository, "searchClient", searchClient);
        ReflectionTestUtils.setField(repository, "postgresClient", olsPostgresClient);

        try {
            PageRequest pageable = PageRequest.of(0, 20);

            Page<V1Term> children = repository.getChildren("efo", ROOT_IRI, "en", pageable);
            Page<V1Term> descendants = repository.getDescendants("efo", ROOT_IRI, "en", pageable);

            assertThat(children).extracting(term -> term.iri)
                    .containsExactly(CHILD_IRI, "http://example.org/EFO_1999")
                    .doesNotContain(INDIVIDUAL_IRI);
            assertThat(descendants).extracting(term -> term.iri)
                    .containsExactly(CHILD_IRI, "http://example.org/EFO_1999")
                    .doesNotContain(INDIVIDUAL_IRI);
        } finally {
            postgresClient.close();
        }
    }
}
