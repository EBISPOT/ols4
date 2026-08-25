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
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class PropertyRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.PropertyRepositoryHandle repositoryHandle;
    private static PropertyRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializePropertyDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createPropertyRepository(POSTGRES);
        repository = repositoryHandle.repository();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void listsActivePropertiesThroughTheRealSearchPath() throws Exception {
        OlsFacetedResultsPage<JsonElement> page = repository.find(
                PageRequest.of(0, 20),
                "en",
                null,
                null,
                null,
                false,
                Map.of("isObsolete", List.of("false")),
                new JsonTransformOptions());

        assertThat(iris(page)).containsExactly(
                "http://example.org/DUO_0100",
                "http://example.org/EFO_0100",
                "http://example.org/EFO_0101");
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void appliesObsoleteSelectionPaginationAndSupportedSorting() throws Exception {
        OlsFacetedResultsPage<JsonElement> all = find(
                PageRequest.of(0, 20), null, null, null, false, Map.of());
        OlsFacetedResultsPage<JsonElement> secondPage = find(
                PageRequest.of(1, 1), null, null, null, false,
                Map.of("isObsolete", List.of("false")));
        OlsFacetedResultsPage<JsonElement> descending = find(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "iri")),
                null, null, null, false, Map.of("isObsolete", List.of("false")));

        assertThat(iris(all)).hasSize(4).contains("http://example.org/EFO_0199");
        assertThat(iris(secondPage)).containsExactly("http://example.org/EFO_0100");
        assertThat(secondPage.getTotalElements()).isEqualTo(3);
        assertThat(secondPage.getTotalPages()).isEqualTo(3);
        assertThat(iris(descending)).containsExactly(
                "http://example.org/EFO_0101",
                "http://example.org/EFO_0100",
                "http://example.org/DUO_0100");
    }

    @Test
    void supportsFreeTextFieldRestrictedExactAndBoostedSearch() throws Exception {
        OlsFacetedResultsPage<JsonElement> freeText = find(
                PageRequest.of(0, 20), "material", null, null, false,
                Map.of("isObsolete", List.of("false")));
        OlsFacetedResultsPage<JsonElement> exactLabel = find(
                PageRequest.of(0, 20), "has material", "label", null, true,
                Map.of("isObsolete", List.of("false")));
        OlsFacetedResultsPage<JsonElement> restrictedMiss = find(
                PageRequest.of(0, 20), "permissions", "label", null, false,
                Map.of("isObsolete", List.of("false")));
        OlsFacetedResultsPage<JsonElement> boosted = find(
                PageRequest.of(0, 20), "material", null, "definition^1000", false,
                Map.of("isObsolete", List.of("false")));

        assertThat(iris(freeText).get(0)).isEqualTo("http://example.org/EFO_0101");
        assertThat(iris(exactLabel)).containsExactly("http://example.org/EFO_0101");
        assertThat(restrictedMiss).isEmpty();
        assertThat(iris(boosted).get(0)).isEqualTo("http://example.org/DUO_0100");
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

        assertThat(iris(repeated)).containsExactly(
                "http://example.org/DUO_0100", "http://example.org/EFO_0101");
        assertThat(iris(commaSeparated)).containsExactly(
                "http://example.org/DUO_0100", "http://example.org/EFO_0101");
        assertThat(unknown).isEmpty();
    }

    @Test
    void scopesToOneOntologyAndGetsExistingOrMissingProperties() throws Exception {
        OlsFacetedResultsPage<JsonElement> efo = repository.findByOntologyId(
                "EFO",
                PageRequest.of(0, 20),
                "en",
                null,
                null,
                null,
                false,
                Map.of("isObsolete", List.of("false")),
                new JsonTransformOptions());

        var existing = repository.getByOntologyIdAndIri(
                "efo", "http://example.org/EFO_0100", "en", new JsonTransformOptions());
        var missing = repository.getByOntologyIdAndIri(
                "efo", "http://example.org/missing", "en", new JsonTransformOptions());

        assertThat(iris(efo)).containsExactly(
                "http://example.org/EFO_0100", "http://example.org/EFO_0101");
        assertThat(existing.any().get("label")).isEqualTo("has specimen");
        assertThat(missing).isNull();
    }

    @Test
    void returnsDirectChildrenAndAncestorsFromProductionHierarchyColumns() {
        Page<JsonElement> children = repository.getChildrenByOntologyId(
                "efo",
                PageRequest.of(0, 20),
                "http://example.org/EFO_0100",
                "en",
                new JsonTransformOptions());
        Page<JsonElement> ancestors = repository.getAncestorsByOntologyId(
                "efo",
                PageRequest.of(0, 20),
                "http://example.org/EFO_0101",
                "en",
                new JsonTransformOptions());

        assertThat(iris(children)).containsExactly("http://example.org/EFO_0101");
        assertThat(children.getTotalElements()).isEqualTo(1);
        assertThat(iris(ancestors)).containsExactly("http://example.org/EFO_0100");
        assertThat(ancestors.getTotalElements()).isEqualTo(1);
    }

    @Test
    void validatesLanguageAndOntologyIdentifiersForEveryRepositoryRoute() {
        assertThatThrownBy(() -> find(
                PageRequest.of(0, 20), null, null, null, false, Map.of(), "en_US"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.findByOntologyId(
                "efo/unsafe", PageRequest.of(0, 20), "en", null, null, null,
                false, Map.of(), new JsonTransformOptions()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.getByOntologyIdAndIri(
                "efo/unsafe", "http://example.org/EFO_0100", "en",
                new JsonTransformOptions()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.getChildrenByOntologyId(
                "efo", PageRequest.of(0, 20), "http://example.org/EFO_0100", "en_US",
                new JsonTransformOptions()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.getAncestorsByOntologyId(
                "efo/unsafe", PageRequest.of(0, 20), "http://example.org/EFO_0101", "en",
                new JsonTransformOptions()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OlsFacetedResultsPage<JsonElement> find(
            PageRequest pageable,
            String search,
            String searchFields,
            String boostFields,
            boolean exactMatch,
            Map<String, java.util.Collection<String>> properties) throws Exception {
        return find(
                pageable, search, searchFields, boostFields, exactMatch, properties, "en");
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
                pageable,
                lang,
                search,
                searchFields,
                boostFields,
                exactMatch,
                properties,
                new JsonTransformOptions());
    }

    private static List<String> iris(OlsFacetedResultsPage<JsonElement> page) {
        return iris((Page<JsonElement>) page);
    }

    private static List<String> iris(Page<JsonElement> page) {
        return page.getContent().stream()
                .map(element -> element.getAsJsonObject().get("iri").getAsString())
                .toList();
    }
}
