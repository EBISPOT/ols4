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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class V1TermRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.V1OntologyTermRepositoryHandle repositoryHandle;
    private static V1TermRepository repository;
    private static V1JsTreeRepository jsTreeRepository;
    private static V1GraphRepository graphRepository;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializeClassDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createV1OntologyTermRepositories(POSTGRES);
        repository = repositoryHandle.termRepository();
        jsTreeRepository = repositoryHandle.jsTreeRepository();
        graphRepository = repositoryHandle.graphRepository();
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
                "http://example.org/EFO_0999",
                "http://example.org/EFO_1001",
                "http://example.org/EFO_1999");
        assertThat(page.getTotalElements()).isEqualTo(6);
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
        assertThat(secondPage.getTotalElements()).isEqualTo(6);
        assertThat(secondPage.getTotalPages()).isEqualTo(3);
        assertThat(descending).extracting(term -> term.iri).containsExactly(
                "http://example.org/EFO_1999",
                "http://example.org/EFO_1001",
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
                        "http://example.org/EFO_0999",
                        "http://example.org/EFO_1001",
                        "http://example.org/EFO_1999");
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

    @Test
    void scopesListsAndEveryIdentifierToOneOntology() {
        PageRequest pageable = PageRequest.of(0, 20);

        assertThat(repository.findAllByOntology("efo", null, "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly(
                        "http://example.org/EFO_0001",
                        "http://example.org/EFO_0002",
                        "http://example.org/EFO_0999",
                        "http://example.org/EFO_1001",
                        "http://example.org/EFO_1999");
        assertThat(repository.findAllByOntology("efo", false, "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly(
                        "http://example.org/EFO_0001",
                        "http://example.org/EFO_0002",
                        "http://example.org/EFO_1001");
        assertThat(repository.findAllByOntology("efo", true, "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly(
                        "http://example.org/EFO_0999",
                        "http://example.org/EFO_1999");

        assertThat(repository.findByOntologyAndIri(
                "efo", "http://example.org/EFO_1001", "fr").lang).isEqualTo("fr");
        assertThat(repository.findByOntologyAndShortForm("efo", "EFO_1001", "fr").iri)
                .isEqualTo("http://example.org/EFO_1001");
        assertThat(repository.findByOntologyAndOboId("efo", "EFO:1001", "fr").iri)
                .isEqualTo("http://example.org/EFO_1001");
        assertThat(repository.findByOntologyAndIri(
                "duo", "http://example.org/EFO_1001", "en")).isNull();
    }

    @Test
    void returnsRootsPreferredRootsAndHierarchyFromProductionColumns() {
        PageRequest pageable = PageRequest.of(0, 20);

        assertThat(repository.getRoots("efo", false, "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly(
                        "http://example.org/EFO_0001",
                        "http://example.org/EFO_0002");
        assertThat(repository.getRoots("efo", true, "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly(
                        "http://example.org/EFO_0001",
                        "http://example.org/EFO_0002",
                        "http://example.org/EFO_0999");
        assertThat(repository.getRoots("duo", false, "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly("http://example.org/DUO_0001");

        assertThat(repository.getPreferredRootTerms("efo", false, "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly("http://example.org/EFO_1001");
        assertThat(repository.getPreferredRootTerms("duo", false, "en", pageable)).isEmpty();

        assertThat(repository.getParents(
                "efo", "http://example.org/EFO_1001", "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly("http://example.org/EFO_0001");
        assertThat(repository.getAncestors(
                "efo", "http://example.org/EFO_1001", "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly("http://example.org/EFO_0001");
        assertThat(repository.getChildren(
                "efo", "http://example.org/EFO_0001", "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly(
                        "http://example.org/EFO_1001",
                        "http://example.org/EFO_1999");
        // getDescendants has no type filter (unlike V2's ClassRepository, see PR #1373):
        // any entity whose direct_ancestors array references EFO_0001 matches, regardless of
        // its own type. EFO_I100 is a synthetic individual (added for a different, class-fixture
        // suite's purpose) whose direct_ancestors already includes EFO_0001, so it is legitimately
        // returned here too. Confirmed against the committed system-regression baseline
        // (testcases_expected_output_api/ontologies/owl2primer-class-assertion) that V1's
        // /terms/{iri}/children and /descendants routes intentionally include individuals
        // asserted into a class — this is documented V1 legacy behavior, not a defect.
        assertThat(repository.getDescendants(
                "efo", "http://example.org/EFO_0001", "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly(
                        "http://example.org/EFO_1001",
                        "http://example.org/EFO_1999",
                        "http://example.org/EFO_I100");
    }

    @Test
    void buildsTheClassJsTreeFromRealPostgresAncestors() {
        List<Map<String, Object>> tree = jsTreeRepository.getJsTreeForClass(
                "http://example.org/EFO_1001", "efo", "en");

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0))
                .containsEntry("iri", "http://example.org/EFO_0001")
                .containsEntry("text", "Liver disease")
                .containsEntry("parent", "#");
        assertThat(tree.get(1))
                .containsEntry("iri", "http://example.org/EFO_1001")
                .containsEntry("text", "Clinical liver child");
        assertThat(((Map<?, ?>) tree.get(1).get("state")).get("selected"))
                .isEqualTo(true);
    }

    @Test
    void buildsTheClassJsTreeChildrenFromRealPostgresDirectChildren() {
        String parentNodeId = java.util.Base64.getEncoder().encodeToString(
                "http://example.org/EFO_0001".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        List<Map<String, Object>> children = jsTreeRepository.getJsTreeChildrenForClass(
                "http://example.org/EFO_0001", parentNodeId, "efo", "en");

        assertThat(children).extracting(child -> (String) child.get("iri")).containsExactly(
                "http://example.org/EFO_1001",
                "http://example.org/EFO_1999");
        assertThat(children.get(0)).containsEntry("parent", parentNodeId);
    }

    @Test
    void returnsHierarchicalRelationshipsFromProductionColumns() {
        PageRequest pageable = PageRequest.of(0, 20);

        // Unlike direct parents/ancestors (see V1TermRepositoryHierarchyTypeFilterIT, and PR #1392's
        // reverted fix), the hierarchical_parents/hierarchical_ancestors columns are populated only
        // for genuine subClassOf relationships in this fixture, not for the individual's
        // instance-of-style direct ancestor — so no equivalent cross-type leak arises here.
        assertThat(repository.getHierarchicalParents(
                "efo", "http://example.org/EFO_1001", "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly("http://example.org/EFO_0001");
        assertThat(repository.getHierarchicalAncestors(
                "efo", "http://example.org/EFO_1001", "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly("http://example.org/EFO_0001");
        assertThat(repository.getHierarchicalChildren(
                "efo", "http://example.org/EFO_0001", "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly(
                        "http://example.org/EFO_1001",
                        "http://example.org/EFO_1999");
        assertThat(repository.getHierarchicalDescendants(
                "efo", "http://example.org/EFO_0001", "en", pageable))
                .extracting(term -> term.iri)
                .containsExactly(
                        "http://example.org/EFO_1001",
                        "http://example.org/EFO_1999");
    }

    @Test
    void returnsRelatedEntitiesRegardlessOfTheRequestedPropertyIri() {
        PageRequest pageable = PageRequest.of(0, 20);

        // getRelated's `relation` (property IRI) parameter is not used to filter the query — it
        // returns every related_to target regardless of which specific relation was requested.
        // Documented here as observed behavior; no committed system-regression baseline exercises
        // this route, so there is no evidence either way of the intended contract.
        assertThat(repository.getRelated(
                "efo", "http://example.org/EFO_0002", "en",
                "http://www.w3.org/2000/01/rdf-schema#seeAlso", pageable))
                .extracting(term -> term.iri)
                .containsExactly("http://example.org/EFO_0001");
        assertThat(repository.getRelated(
                "efo", "http://example.org/EFO_0002", "en",
                "http://purl.obolibrary.org/obo/RO_0000052", pageable))
                .extracting(term -> term.iri)
                .containsExactly("http://example.org/EFO_0001");
    }

    @Test
    void buildsTheClassGraphFromRealPostgresRelationships() {
        Map<String, Object> parentGraph = graphRepository.getGraphForClass(
                "http://example.org/EFO_1001", "efo", "en");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parentNodes =
                (List<Map<String, Object>>) parentGraph.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parentEdges =
                (List<Map<String, Object>>) parentGraph.get("edges");

        assertThat(parentNodes).extracting(node -> node.get("iri")).containsExactlyInAnyOrder(
                "http://example.org/EFO_1001", "http://example.org/EFO_0001");
        assertThat(parentEdges).singleElement().satisfies(edge -> {
            assertThat(edge.get("source")).isEqualTo("http://example.org/EFO_1001");
            assertThat(edge.get("target")).isEqualTo("http://example.org/EFO_0001");
            assertThat(edge.get("uri")).isEqualTo("http://www.w3.org/2000/01/rdf-schema#subClassOf");
        });

        Map<String, Object> relatedGraph = graphRepository.getGraphForClass(
                "http://example.org/EFO_0002", "efo", "en");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relatedEdges =
                (List<Map<String, Object>>) relatedGraph.get("edges");

        assertThat(relatedEdges).singleElement().satisfies(edge -> {
            assertThat(edge.get("source")).isEqualTo("http://example.org/EFO_0002");
            assertThat(edge.get("target")).isEqualTo("http://example.org/EFO_0001");
            assertThat(edge.get("label")).isEqualTo("related to");
            assertThat(edge).doesNotContainKey("uri");
        });
    }
}
