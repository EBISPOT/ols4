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
import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class V1IndividualRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.V1IndividualRepositoryHandle repositoryHandle;
    private static V1IndividualRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializeIndividualDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createV1IndividualRepository(POSTGRES);
        repository = repositoryHandle.repository();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void listsIndividualsInStableOrderAndMapsPublicFlags() {
        Page<V1Individual> page = repository.findAll("en", PageRequest.of(0, 20));

        assertThat(page).extracting(individual -> individual.iri).containsExactly(
                "http://example.org/DUO_I100",
                "http://example.org/EFO_I100",
                "http://example.org/EFO_I200",
                "http://example.org/EFO_I999");
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent().get(1).label).isEqualTo("Liver specimen alpha");
        assertThat(page.getContent().get(1).isLocal).isTrue();
        assertThat(page.getContent().get(1).isObsolete).isFalse();
        assertThat(page.getContent().get(2).isLocal).isFalse();
        assertThat(page.getContent().get(3).isObsolete).isTrue();
    }

    @Test
    void appliesPaginationAndSupportedSorting() {
        Page<V1Individual> secondPage = repository.findAll("en", PageRequest.of(1, 2));
        Page<V1Individual> descending = repository.findAll(
                "en",
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "iri")));

        assertThat(secondPage).extracting(individual -> individual.iri).containsExactly(
                "http://example.org/EFO_I200",
                "http://example.org/EFO_I999");
        assertThat(secondPage.getTotalElements()).isEqualTo(4);
        assertThat(secondPage.getTotalPages()).isEqualTo(2);
        assertThat(descending).extracting(individual -> individual.iri).containsExactly(
                "http://example.org/EFO_I999",
                "http://example.org/EFO_I200",
                "http://example.org/EFO_I100",
                "http://example.org/DUO_I100");
    }

    @Test
    void rejectsUnsupportedSortFields() {
        assertThatThrownBy(() -> repository.findAll(
                "en", PageRequest.of(0, 20, Sort.by("notARealField"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported sort field");
    }

    @Test
    void findsIndividualsByIriShortFormAndOboId() {
        PageRequest pageable = PageRequest.of(0, 20);

        assertThat(repository.findAllByIri(
                "http://example.org/EFO_I100", "fr", pageable))
                .singleElement()
                .satisfies(individual -> {
                    assertThat(individual.label).isEqualTo("Liver specimen alpha");
                    assertThat(individual.lang).isEqualTo("fr");
                    assertThat(individual.isLocal).isTrue();
                    assertThat(individual.isObsolete).isFalse();
                    assertThat(individual.description).containsExactly(
                            "An individual example assigned to the liver disease class.");
                });
        assertThat(repository.findAllByShortForm("EFO_I100", "en", pageable))
                .extracting(individual -> individual.iri)
                .containsExactly("http://example.org/EFO_I100");
        assertThat(repository.findAllByOboId("EFO:I100", "en", pageable))
                .extracting(individual -> individual.iri)
                .containsExactly("http://example.org/EFO_I100");
    }

    @Test
    void returnsEmptyPagesForUnknownIdentifiers() {
        PageRequest pageable = PageRequest.of(0, 20);

        assertThat(repository.findAllByIri("http://example.org/missing", "en", pageable))
                .isEmpty();
        assertThat(repository.findAllByShortForm("MISSING", "en", pageable)).isEmpty();
        assertThat(repository.findAllByOboId("MISSING:0000", "en", pageable)).isEmpty();
    }

    @Test
    void restrictsEveryIdentifierRepresentationToDefiningOntologies() {
        PageRequest pageable = PageRequest.of(0, 20);

        assertThat(repository.findAllByIsDefiningOntology("en", pageable))
                .allSatisfy(individual -> assertThat(individual.isLocal).isTrue())
                .extracting(individual -> individual.iri)
                .containsExactly(
                        "http://example.org/DUO_I100",
                        "http://example.org/EFO_I100",
                        "http://example.org/EFO_I999");
        assertThat(repository.findAllByIriAndIsDefiningOntology(
                "http://example.org/EFO_I200", "en", pageable)).isEmpty();
        assertThat(repository.findAllByShortFormAndIsDefiningOntology(
                "EFO_I200", "en", pageable)).isEmpty();
        assertThat(repository.findAllByOboIdAndIsDefiningOntology(
                "EFO:I200", "en", pageable)).isEmpty();
        assertThat(repository.findAllByOboIdAndIsDefiningOntology(
                "EFO:I100", "en", pageable))
                .extracting(individual -> individual.iri)
                .containsExactly("http://example.org/EFO_I100");
    }

    @Test
    void preservesArbitraryV1LanguageWithLabelFallback() {
        Page<V1Individual> page = repository.findAll("en_US", PageRequest.of(0, 1));

        assertThat(page).singleElement().satisfies(individual -> {
            assertThat(individual.lang).isEqualTo("en_US");
            assertThat(individual.label).isEqualTo("Permission instance");
        });
    }
}
