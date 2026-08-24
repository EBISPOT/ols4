package uk.ac.ebi.spot.ols.repository.v1;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class V1OntologyRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.V1RepositoryHandle repositoryHandle;
    private static V1OntologyRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createV1Repository(POSTGRES);
        repository = repositoryHandle.repository();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void listsAndMapsAllOntologiesInStableOrder() {
        Page<V1Ontology> page = repository.getAll("en", PageRequest.of(0, 20));

        assertThat(page).extracting(ontology -> ontology.ontologyId)
                .containsExactly("duo", "efo", "efo-atlas", "legacy-efo");
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(1);

        V1Ontology duo = page.getContent().get(0);
        assertThat(duo.lang).isEqualTo("en");
        assertThat(duo.languages).containsExactly("en");
        assertThat(duo.status).isEqualTo("LOADED");
        assertThat(duo.config.title).isEqualTo("Data Use Ontology");
        assertThat(duo.config.preferredPrefix).isEqualTo("DUO");
        assertThat(duo.numberOfTerms).isEqualTo(45);
        assertThat(duo.numberOfProperties).isEqualTo(7);
        assertThat(duo.numberOfIndividuals).isZero();
    }

    @Test
    void appliesPaginationAndSupportedSorting() {
        Page<V1Ontology> secondPage = repository.getAll("en", PageRequest.of(1, 2));
        Page<V1Ontology> descending = repository.getAll(
                "en",
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "ontologyId")));

        assertThat(secondPage).extracting(ontology -> ontology.ontologyId)
                .containsExactly("efo-atlas", "legacy-efo");
        assertThat(secondPage.getTotalElements()).isEqualTo(4);
        assertThat(secondPage.getTotalPages()).isEqualTo(2);
        assertThat(descending).extracting(ontology -> ontology.ontologyId)
                .containsExactly("legacy-efo", "efo-atlas", "efo", "duo");
    }

    @Test
    void rejectsUnsupportedSortFields() {
        assertThatThrownBy(() -> repository.getAll(
                "en",
                PageRequest.of(0, 20, Sort.by("notARealField"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported sort field");
    }

    @Test
    void getsAnOntologyAndMapsTheRequestedLanguage() {
        V1Ontology ontology = repository.get("efo", "fr");

        assertThat(ontology.ontologyId).isEqualTo("efo");
        assertThat(ontology.lang).isEqualTo("fr");
        assertThat(ontology.config.title).isEqualTo("Experimental Factor Ontology");
        assertThat(ontology.numberOfTerms).isEqualTo(100);
    }

    @Test
    void returnsNullForMissingOntology() {
        assertThat(repository.get("missing", "en")).isNull();
    }

    @Test
    void validatesLanguageAndOntologyIdentifiers() {
        assertThatThrownBy(() -> repository.getAll("en_US", PageRequest.of(0, 20)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.get("efo/unsafe", "en"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
