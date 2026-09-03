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
import uk.ac.ebi.spot.ols.model.v1.V1Property;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class V1PropertyRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.V1OntologyPropertyRepositoryHandle
            repositoryHandle;
    private static V1PropertyRepository repository;
    private static V1JsTreeRepository jsTreeRepository;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializePropertyDatabase(POSTGRES);
        repositoryHandle =
                PostgresIntegrationTestSupport.createV1OntologyPropertyRepositories(POSTGRES);
        repository = repositoryHandle.propertyRepository();
        jsTreeRepository = repositoryHandle.jsTreeRepository();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void listsPropertiesInStableOrder() {
        Page<V1Property> page = repository.findAll("en", PageRequest.of(0, 20));

        assertThat(page).extracting(property -> property.iri).containsExactly(
                "http://example.org/DUO_0100",
                "http://example.org/EFO_0100",
                "http://example.org/EFO_0101",
                "http://example.org/EFO_0199");
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent().get(1).label).isEqualTo("has specimen");
        assertThat(page.getContent().get(1).isLocal).isTrue();
        assertThat(page.getContent().get(1).isObsolete).isFalse();
        assertThat(page.getContent().get(3).isLocal).isFalse();
        assertThat(page.getContent().get(3).isObsolete).isTrue();
    }

    @Test
    void appliesPaginationAndSupportedSorting() {
        Page<V1Property> secondPage = repository.findAll("en", PageRequest.of(1, 2));
        Page<V1Property> descending = repository.findAll(
                "en",
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "iri")));

        assertThat(secondPage).extracting(property -> property.iri).containsExactly(
                "http://example.org/EFO_0101",
                "http://example.org/EFO_0199");
        assertThat(secondPage.getTotalElements()).isEqualTo(4);
        assertThat(secondPage.getTotalPages()).isEqualTo(2);
        assertThat(descending).extracting(property -> property.iri).containsExactly(
                "http://example.org/EFO_0199",
                "http://example.org/EFO_0101",
                "http://example.org/EFO_0100",
                "http://example.org/DUO_0100");
    }

    @Test
    void rejectsUnsupportedSortFields() {
        assertThatThrownBy(() -> repository.findAll(
                "en", PageRequest.of(0, 20, Sort.by("notARealField"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported sort field");
    }

    @Test
    void findsPropertiesByIriShortFormAndOboId() {
        PageRequest pageable = PageRequest.of(0, 20);

        assertThat(repository.findAllByIri(
                "http://example.org/EFO_0100", "fr", pageable))
                .singleElement()
                .satisfies(property -> {
                    assertThat(property.label).isEqualTo("has specimen");
                    assertThat(property.lang).isEqualTo("fr");
                    assertThat(property.isLocal).isTrue();
                    assertThat(property.isObsolete).isFalse();
                    assertThat(property.description)
                            .containsExactly("Relates a study to its specimen.");
                });
        assertThat(repository.findAllByShortForm("EFO_0100", "en", pageable))
                .extracting(property -> property.iri)
                .containsExactly("http://example.org/EFO_0100");
        assertThat(repository.findAllByOboId("EFO:0100", "en", pageable))
                .extracting(property -> property.iri)
                .containsExactly("http://example.org/EFO_0100");
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
                .allSatisfy(property -> assertThat(property.isLocal).isTrue())
                .extracting(property -> property.iri)
                .containsExactly(
                        "http://example.org/DUO_0100",
                        "http://example.org/EFO_0100",
                        "http://example.org/EFO_0101");
        assertThat(repository.findAllByIriAndIsDefiningOntology(
                "http://example.org/EFO_0199", "en", pageable)).isEmpty();
        assertThat(repository.findAllByShortFormAndIsDefiningOntology(
                "EFO_0199", "en", pageable)).isEmpty();
        assertThat(repository.findAllByOboIdAndIsDefiningOntology(
                "EFO:0199", "en", pageable)).isEmpty();
        assertThat(repository.findAllByOboIdAndIsDefiningOntology(
                "EFO:0100", "en", pageable))
                .extracting(property -> property.iri)
                .containsExactly("http://example.org/EFO_0100");
    }

    @Test
    void preservesArbitraryV1LanguageWithLabelFallback() {
        Page<V1Property> page = repository.findAll("en_US", PageRequest.of(0, 1));

        assertThat(page).singleElement().satisfies(property -> {
            assertThat(property.lang).isEqualTo("en_US");
            assertThat(property.label).isEqualTo("permits sample");
        });
    }

    @Test
    void scopesListsAndEveryIdentifierToOneOntology() {
        PageRequest pageable = PageRequest.of(0, 20);

        assertThat(repository.findAllByOntology("efo", "en", pageable))
                .extracting(property -> property.iri)
                .containsExactly(
                        "http://example.org/EFO_0100",
                        "http://example.org/EFO_0101",
                        "http://example.org/EFO_0199");
        assertThat(repository.findByOntologyAndIri(
                "efo", "http://example.org/EFO_0100", "fr").lang).isEqualTo("fr");
        assertThat(repository.findByOntologyAndShortForm("efo", "EFO_0100", "fr").iri)
                .isEqualTo("http://example.org/EFO_0100");
        assertThat(repository.findByOntologyAndOboId("efo", "EFO:0100", "fr").iri)
                .isEqualTo("http://example.org/EFO_0100");
        assertThat(repository.findByOntologyAndIri(
                "duo", "http://example.org/EFO_0100", "en")).isNull();
    }

    @Test
    void returnsRootsAndHierarchyFromProductionColumns() {
        PageRequest pageable = PageRequest.of(0, 20);

        assertThat(repository.getRoots("efo", false, "en", pageable))
                .extracting(property -> property.iri)
                .containsExactly("http://example.org/EFO_0100");
        assertThat(repository.getRoots("efo", true, "en", pageable))
                .extracting(property -> property.iri)
                .containsExactly(
                        "http://example.org/EFO_0100",
                        "http://example.org/EFO_0199");
        assertThat(repository.getRoots("duo", false, "en", pageable))
                .extracting(property -> property.iri)
                .containsExactly("http://example.org/DUO_0100");

        assertThat(repository.getParents(
                "efo", "http://example.org/EFO_0101", "en", pageable))
                .extracting(property -> property.iri)
                .containsExactly("http://example.org/EFO_0100");
        assertThat(repository.getAncestors(
                "efo", "http://example.org/EFO_0101", "en", pageable))
                .extracting(property -> property.iri)
                .containsExactly("http://example.org/EFO_0100");
        assertThat(repository.getChildren(
                "efo", "http://example.org/EFO_0100", "en", pageable))
                .extracting(property -> property.iri)
                .containsExactly("http://example.org/EFO_0101");
        assertThat(repository.getDescendants(
                "efo", "http://example.org/EFO_0100", "en", pageable))
                .extracting(property -> property.iri)
                .containsExactly("http://example.org/EFO_0101");
    }

    @Test
    void buildsThePropertyJsTreeFromRealPostgresAncestors() {
        List<Map<String, Object>> tree = jsTreeRepository.getJsTreeForProperty(
                "http://example.org/EFO_0101", "efo", "en");

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0))
                .containsEntry("iri", "http://example.org/EFO_0100")
                .containsEntry("text", "has specimen")
                .containsEntry("parent", "#");
        assertThat(tree.get(1))
                .containsEntry("iri", "http://example.org/EFO_0101")
                .containsEntry("text", "has material")
                .containsEntry("children", false);
        assertThat(((Map<?, ?>) tree.get(1).get("state")).get("selected"))
                .isEqualTo(true);
    }
}
