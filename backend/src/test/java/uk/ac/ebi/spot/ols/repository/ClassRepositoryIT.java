package uk.ac.ebi.spot.ols.repository;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class ClassRepositoryIT {

    private static final String ROOT_IRI = "http://example.org/EFO_0001";
    private static final String CHILD_IRI = "http://example.org/EFO_1001";
    private static final String OBSOLETE_CHILD_IRI = "http://example.org/EFO_1999";
    private static final String INDIVIDUAL_IRI = "http://example.org/EFO_I100";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.ClassRepositoryHandle repositoryHandle;
    private static ClassRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializeClassDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createClassRepository(POSTGRES);
        repository = repositoryHandle.repository();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void returnsDirectChildrenAndAncestorsFromProductionHierarchyColumns() {
        Page<JsonElement> children = repository.getChildrenByOntologyId(
                "efo", PageRequest.of(0, 20), ROOT_IRI, false, null, "en",
                new JsonTransformOptions());
        Page<JsonElement> ancestors = repository.getAncestorsByOntologyId(
                "efo", PageRequest.of(0, 20), CHILD_IRI, false, "en",
                new JsonTransformOptions());

        assertThat(iris(children)).containsExactly(CHILD_IRI);
        assertThat(children.getTotalElements()).isEqualTo(1);
        assertThat(iris(ancestors)).containsExactly(ROOT_IRI);
        assertThat(ancestors.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listsActiveClassesAndCanIncludeObsoleteClasses() throws Exception {
        OlsFacetedResultsPage<JsonElement> active = find(
                PageRequest.of(0, 20), null, null, null, false,
                Map.of("isObsolete", List.of("false")));
        OlsFacetedResultsPage<JsonElement> all = find(
                PageRequest.of(0, 20), null, null, null, false, Map.of());

        assertThat(iris(active)).containsExactly(
                "http://example.org/DUO_0001",
                ROOT_IRI,
                "http://example.org/EFO_0002",
                CHILD_IRI);
        assertThat(active.getTotalElements()).isEqualTo(4);
        assertThat(iris(all)).hasSize(6)
                .contains("http://example.org/EFO_0999", OBSOLETE_CHILD_IRI);
    }

    @Test
    void appliesPaginationAndSupportedSorting() throws Exception {
        OlsFacetedResultsPage<JsonElement> secondPage = find(
                PageRequest.of(1, 2), null, null, null, false,
                Map.of("isObsolete", List.of("false")));
        OlsFacetedResultsPage<JsonElement> descending = find(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "iri")),
                null, null, null, false, Map.of("isObsolete", List.of("false")));

        assertThat(iris(secondPage)).containsExactly("http://example.org/EFO_0002", CHILD_IRI);
        assertThat(secondPage.getTotalElements()).isEqualTo(4);
        assertThat(secondPage.getTotalPages()).isEqualTo(2);
        assertThat(iris(descending)).containsExactly(
                CHILD_IRI,
                "http://example.org/EFO_0002",
                ROOT_IRI,
                "http://example.org/DUO_0001");
    }

    @Test
    void supportsFreeTextFieldRestrictedExactAndBoostedSearch() throws Exception {
        OlsFacetedResultsPage<JsonElement> freeText = find(
                PageRequest.of(0, 20), "permission", null, null, false,
                Map.of("isObsolete", List.of("false")));
        OlsFacetedResultsPage<JsonElement> exactLabel = find(
                PageRequest.of(0, 20), "Liver disease", "label", null, true,
                Map.of("isObsolete", List.of("false")));
        OlsFacetedResultsPage<JsonElement> restrictedMiss = find(
                PageRequest.of(0, 20), "biomedical", "label", null, false,
                Map.of("isObsolete", List.of("false")));
        OlsFacetedResultsPage<JsonElement> boosted = find(
                PageRequest.of(0, 20), "liver", null, "definition^1000", false,
                Map.of("isObsolete", List.of("false")));

        assertThat(iris(freeText)).containsExactly("http://example.org/DUO_0001");
        assertThat(iris(exactLabel)).containsExactly(ROOT_IRI);
        assertThat(restrictedMiss).isEmpty();
        assertThat(iris(boosted).get(0)).isEqualTo("http://example.org/EFO_0002");
    }

    @Test
    void supportsRepeatedCommaSeparatedAndUriNamedDynamicValues() throws Exception {
        OlsFacetedResultsPage<JsonElement> repeated = find(
                PageRequest.of(0, 20), null, null, null, false,
                Map.of("http://example.org/category", List.of("clinical", "policy")));
        OlsFacetedResultsPage<JsonElement> commaSeparated = find(
                PageRequest.of(0, 20), null, null, null, false,
                Map.of("http://example.org/category", List.of("clinical,policy")));
        OlsFacetedResultsPage<JsonElement> unknown = find(
                PageRequest.of(0, 20), null, null, null, false,
                Map.of("unknown-property", List.of("anything")));

        assertThat(iris(repeated)).containsExactly("http://example.org/DUO_0001", ROOT_IRI);
        assertThat(iris(commaSeparated)).containsExactly("http://example.org/DUO_0001", ROOT_IRI);
        assertThat(unknown).isEmpty();
    }

    @Test
    void scopesToOneOntologyAndGetsExistingMissingAndRelatedClasses() throws Exception {
        OlsFacetedResultsPage<JsonElement> efo = repository.findByOntologyId(
                "EFO", PageRequest.of(0, 20), "en", null, null, null, false,
                Map.of("isObsolete", List.of("false")), new JsonTransformOptions());
        JsonElement existing = repository.getByOntologyIdAndIri(
                "efo", ROOT_IRI, "en", new JsonTransformOptions());
        JsonElement missing = repository.getByOntologyIdAndIri(
                "efo", "http://example.org/missing", "en", new JsonTransformOptions());
        OlsFacetedResultsPage<JsonElement> related = repository.getRelatedFrom(
                "efo", ROOT_IRI, PageRequest.of(0, 20), "en", new JsonTransformOptions());

        assertThat(iris(efo)).containsExactly(ROOT_IRI, "http://example.org/EFO_0002", CHILD_IRI);
        assertThat(existing.getAsJsonObject().get("label").getAsString()).isEqualTo("Liver disease");
        assertThat(missing).isNull();
        assertThat(iris(related)).containsExactly("http://example.org/EFO_0002");
    }

    @Test
    void filtersObsoleteChildrenAndSupportsChildLabelSearch() {
        Page<JsonElement> active = repository.getChildrenByOntologyId(
                "efo", PageRequest.of(0, 20), ROOT_IRI, false, null, "en",
                new JsonTransformOptions());
        Page<JsonElement> all = repository.getChildrenByOntologyId(
                "efo", PageRequest.of(0, 20), ROOT_IRI, true, null, "en",
                new JsonTransformOptions());
        Page<JsonElement> searched = repository.getChildrenByOntologyId(
                "efo", PageRequest.of(0, 20), ROOT_IRI, false, "clinical", "en",
                new JsonTransformOptions());

        assertThat(iris(active)).containsExactly(CHILD_IRI);
        assertThat(iris(all)).containsExactly(CHILD_IRI, OBSOLETE_CHILD_IRI);
        assertThat(iris(searched)).containsExactly(CHILD_IRI);
    }

    @Test
    void returnsEveryDirectAndHierarchicalRelationshipFromProductionColumns() {
        JsonTransformOptions options = new JsonTransformOptions();
        PageRequest page = PageRequest.of(0, 20);

        assertThat(iris(repository.getDescendantsByOntologyId(
                "efo", page, ROOT_IRI, false, "en", options))).containsExactly(CHILD_IRI);
        assertThat(iris(repository.getHierarchicalChildrenByOntologyId(
                "efo", page, ROOT_IRI, false, "en", options))).containsExactly(CHILD_IRI);
        assertThat(iris(repository.getHierarchicalAncestorsByOntologyId(
                "efo", page, CHILD_IRI, false, "en", options))).containsExactly(ROOT_IRI);
        assertThat(iris(repository.getHierarchicalDescendantsByOntologyId(
                "efo", page, ROOT_IRI, false, "en", options))).containsExactly(CHILD_IRI);
        assertThat(iris(repository.getIndividualAncestorsByOntologyId(
                "efo", page, INDIVIDUAL_IRI, false, "en", options))).containsExactly(ROOT_IRI);
    }

    @Test
    void validatesLanguageAndOntologyIdentifiersForEveryRepositoryRoute() {
        PageRequest page = PageRequest.of(0, 20);
        JsonTransformOptions options = new JsonTransformOptions();

        assertThatThrownBy(() -> find(page, null, null, null, false, Map.of(), "en_US"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.findByOntologyId(
                "efo/unsafe", page, "en", null, null, null, false, Map.of(), options))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.getByOntologyIdAndIri(
                "efo/unsafe", ROOT_IRI, "en", options))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.getRelatedFrom(
                "efo/unsafe", ROOT_IRI, page, "en", options))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.getChildrenByOntologyId(
                "efo", page, ROOT_IRI, false, null, "en_US", options))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.getAncestorsByOntologyId(
                "efo/unsafe", page, CHILD_IRI, false, "en", options))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.getDescendantsByOntologyId(
                "efo/unsafe", page, ROOT_IRI, false, "en", options))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.getHierarchicalChildrenByOntologyId(
                "efo/unsafe", page, ROOT_IRI, false, "en", options))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.getHierarchicalAncestorsByOntologyId(
                "efo/unsafe", page, CHILD_IRI, false, "en", options))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.getHierarchicalDescendantsByOntologyId(
                "efo/unsafe", page, ROOT_IRI, false, "en", options))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.getIndividualAncestorsByOntologyId(
                "efo/unsafe", page, INDIVIDUAL_IRI, false, "en", options))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OlsFacetedResultsPage<JsonElement> find(
            PageRequest pageable,
            String search,
            String searchFields,
            String boostFields,
            boolean exactMatch,
            Map<String, java.util.Collection<String>> properties) throws Exception {
        return find(pageable, search, searchFields, boostFields, exactMatch, properties, "en");
    }

    private static OlsFacetedResultsPage<JsonElement> find(
            PageRequest pageable,
            String search,
            String searchFields,
            String boostFields,
            boolean exactMatch,
            Map<String, java.util.Collection<String>> properties,
            String lang) throws Exception {
        return repository.find(
                pageable, lang, search, searchFields, boostFields, exactMatch, properties,
                new JsonTransformOptions());
    }

    private static List<String> iris(Page<JsonElement> page) {
        return page.getContent().stream()
                .map(element -> element.getAsJsonObject().get("iri").getAsString())
                .toList();
    }
}
