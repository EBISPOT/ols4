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
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class V1TermRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.V1TermRepositoryHandle repositoryHandle;
    private static V1TermRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createV1TermRepository(POSTGRES);
        repository = repositoryHandle.repository();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void listsClassTermsInStableOrder() {
        Page<V1Term> page = repository.findAll("en", PageRequest.of(0, 20));

        assertThat(page).extracting(term -> term.iri).containsExactly(
                "http://example.org/DUO_0001",
                "http://example.org/EFO_0001",
                "http://example.org/EFO_0002",
                "http://example.org/EFO_0999");
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent().get(1).label).isEqualTo("Liver disease");
    }

    @Test
    void appliesPaginationAndSupportedSorting() {
        Page<V1Term> secondPage = repository.findAll("en", PageRequest.of(1, 2));
        Page<V1Term> descending = repository.findAll(
                "en",
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "iri")));

        assertThat(secondPage).extracting(term -> term.iri).containsExactly(
                "http://example.org/EFO_0002",
                "http://example.org/EFO_0999");
        assertThat(secondPage.getTotalElements()).isEqualTo(4);
        assertThat(secondPage.getTotalPages()).isEqualTo(2);
        assertThat(descending).extracting(term -> term.iri).containsExactly(
                "http://example.org/EFO_0999",
                "http://example.org/EFO_0002",
                "http://example.org/EFO_0001",
                "http://example.org/DUO_0001");
    }

    @Test
    void rejectsUnsupportedSortFields() {
        assertThatThrownBy(() -> repository.findAll(
                "en", PageRequest.of(0, 20, Sort.by("notARealField"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported sort field");
    }

    @Test
    void findsTermsByIriShortFormAndOboId() {
        PageRequest pageable = PageRequest.of(0, 20);

        assertThat(repository.findAllByIri(
                "http://example.org/EFO_0001", "fr", pageable))
                .singleElement()
                .satisfies(term -> {
                    assertThat(term.label).isEqualTo("Liver disease");
                    assertThat(term.lang).isEqualTo("fr");
                    assertThat(term.description)
                            .containsExactly("A disorder affecting hepatic tissue.");
                });
        assertThat(repository.findAllByShortForm("EFO_0001", "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly("http://example.org/EFO_0001");
        assertThat(repository.findAllByOboId("EFO:0001", "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly("http://example.org/EFO_0001");
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
                .extracting(term -> term.iri)
                .containsExactly(
                        "http://example.org/DUO_0001",
                        "http://example.org/EFO_0001",
                        "http://example.org/EFO_0999");
        assertThat(repository.findAllByIriAndIsDefiningOntology(
                "http://example.org/EFO_0002", "en", pageable)).isEmpty();
        assertThat(repository.findAllByShortFormAndIsDefiningOntology(
                "EFO_0002", "en", pageable)).isEmpty();
        assertThat(repository.findAllByOboIdAndIsDefiningOntology(
                "EFO:0002", "en", pageable)).isEmpty();
        assertThat(repository.findAllByOboIdAndIsDefiningOntology(
                "EFO:0001", "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly("http://example.org/EFO_0001");
    }

    @Test
    void preservesArbitraryV1LanguageWithLabelFallback() {
        Page<V1Term> page = repository.findAll("en_US", PageRequest.of(0, 1));

        assertThat(page).singleElement().satisfies(term -> {
            assertThat(term.lang).isEqualTo("en_US");
            assertThat(term.label).isEqualTo("Data use permission");
        });
    }
}
