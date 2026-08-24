package uk.ac.ebi.spot.ols.repository;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class OntologyRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.RepositoryHandle repositoryHandle;
    private static OntologyRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createRepository(POSTGRES);
        repository = repositoryHandle.repository();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void listsActiveOntologiesInDeterministicOrder() throws Exception {
        OlsFacetedResultsPage<JsonElement> page = find(
                PageRequest.of(0, 20),
                null,
                null,
                false,
                Map.of("isObsolete", List.of("false")));

        assertThat(ontologyIds(page)).containsExactly("duo", "efo", "efo-atlas");
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void canIncludeObsoleteOntologies() throws Exception {
        OlsFacetedResultsPage<JsonElement> page = find(
                PageRequest.of(0, 20), null, null, false, Map.of());

        assertThat(ontologyIds(page))
                .containsExactly("duo", "efo", "efo-atlas", "legacy-efo");
    }

    @Test
    void paginatesWithStableTotals() throws Exception {
        OlsFacetedResultsPage<JsonElement> page = find(
                PageRequest.of(1, 2),
                null,
                null,
                false,
                Map.of("isObsolete", List.of("false")));

        assertThat(ontologyIds(page)).containsExactly("efo-atlas");
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void appliesSupportedSortAndRejectsUnknownSortFields() throws Exception {
        OlsFacetedResultsPage<JsonElement> descending = find(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "ontologyId")),
                null,
                null,
                false,
                Map.of());

        assertThat(ontologyIds(descending))
                .containsExactly("legacy-efo", "efo-atlas", "efo", "duo");
        assertThatThrownBy(() -> find(
                PageRequest.of(0, 20, Sort.by("notARealField")),
                null,
                null,
                false,
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported sort field");
    }

    @Test
    void supportsFreeTextAndFieldRestrictedExactSearch() throws Exception {
        OlsFacetedResultsPage<JsonElement> freeText = find(
                PageRequest.of(0, 20),
                "permissions",
                null,
                false,
                Map.of("isObsolete", List.of("false")));
        OlsFacetedResultsPage<JsonElement> exactLabel = find(
                PageRequest.of(0, 20),
                "Experimental Factor Ontology",
                "label",
                true,
                Map.of("isObsolete", List.of("false")));
        OlsFacetedResultsPage<JsonElement> restrictedMiss = find(
                PageRequest.of(0, 20),
                "permissions",
                "label",
                false,
                Map.of("isObsolete", List.of("false")));

        assertThat(ontologyIds(freeText)).containsExactly("duo");
        assertThat(ontologyIds(exactLabel)).containsExactly("efo");
        assertThat(restrictedMiss).isEmpty();
    }

    @Test
    void appliesExplicitBoostFieldsToRanking() throws Exception {
        OlsFacetedResultsPage<JsonElement> baseline = repository.find(
                PageRequest.of(0, 20),
                "en",
                "experimental",
                null,
                null,
                false,
                Map.of("isObsolete", List.of("false")),
                new JsonTransformOptions());
        OlsFacetedResultsPage<JsonElement> boosted = repository.find(
                PageRequest.of(0, 20),
                "en",
                "experimental",
                null,
                "definition^1000",
                false,
                Map.of("isObsolete", List.of("false")),
                new JsonTransformOptions());

        assertThat(ontologyIds(baseline).get(0)).isNotEqualTo("duo");
        assertThat(ontologyIds(boosted).get(0)).isEqualTo("duo");
    }

    @Test
    void filtersKnownAndUriNamedDynamicProperties() throws Exception {
        OlsFacetedResultsPage<JsonElement> byDomain = find(
                PageRequest.of(0, 20),
                null,
                null,
                false,
                Map.of("domain", List.of("information")));
        OlsFacetedResultsPage<JsonElement> byUri = find(
                PageRequest.of(0, 20),
                null,
                null,
                false,
                Map.of("http://example.org/category", List.of("derived")));
        OlsFacetedResultsPage<JsonElement> unknown = find(
                PageRequest.of(0, 20),
                null,
                null,
                false,
                Map.of("unknown-property", List.of("anything")));

        assertThat(ontologyIds(byDomain)).containsExactly("duo");
        assertThat(ontologyIds(byUri)).containsExactly("efo-atlas");
        assertThat(unknown).isEmpty();
    }

    @Test
    void treatsRepeatedAndCommaSeparatedDynamicValuesAsAlternatives() throws Exception {
        OlsFacetedResultsPage<JsonElement> repeated = find(
                PageRequest.of(0, 20),
                null,
                null,
                false,
                Map.of("domain", List.of("biology", "information")));
        OlsFacetedResultsPage<JsonElement> commaSeparated = find(
                PageRequest.of(0, 20),
                null,
                null,
                false,
                Map.of("domain", List.of("biology,information")));

        assertThat(ontologyIds(repeated))
                .containsExactly("duo", "efo", "efo-atlas", "legacy-efo");
        assertThat(ontologyIds(commaSeparated))
                .containsExactly("duo", "efo", "efo-atlas", "legacy-efo");
    }

    @Test
    void groupsOntologiesByTagsAndDomains() throws Exception {
        Map<String, List<uk.ac.ebi.spot.ols.model.v2.V2Entity>> byTag =
                repository.getGroupedByField("tags", "en", new JsonTransformOptions());
        Map<String, List<uk.ac.ebi.spot.ols.model.v2.V2Entity>> byDomain =
                repository.getGroupedByField("domain", "en", new JsonTransformOptions());

        assertThat(byTag.keySet()).contains("biomedical", "experimental", "clinical", "atlas");
        assertThat(byTag.get("biomedical"))
                .extracting(entity -> entity.any().get("ontologyId"))
                .containsExactlyInAnyOrder("duo", "efo");
        assertThat(byDomain.keySet()).contains("biology", "information");
    }

    @Test
    void getsExistingOntologyAndReturnsNullForMissingOntology() {
        var existing = repository.getById("efo", "en", new JsonTransformOptions());
        var missing = repository.getById("missing", "en", new JsonTransformOptions());

        assertThat(existing.any().get("title"))
                .isEqualTo("Experimental Factor Ontology");
        assertThat(missing).isNull();
    }

    @Test
    void validatesLanguageAndOntologyIdentifiers() {
        assertThatThrownBy(() -> repository.getById("efo", "en_US", new JsonTransformOptions()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.getById("efo/unsafe", "en", new JsonTransformOptions()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OlsFacetedResultsPage<JsonElement> find(
            PageRequest pageable,
            String search,
            String searchFields,
            boolean exactMatch,
            Map<String, Collection<String>> properties) throws Exception {
        return repository.find(
                pageable,
                "en",
                search,
                searchFields,
                null,
                exactMatch,
                properties,
                new JsonTransformOptions());
    }

    private static List<String> ontologyIds(OlsFacetedResultsPage<JsonElement> page) {
        return page.getContent().stream()
                .map(element -> element.getAsJsonObject().get("ontologyId").getAsString())
                .toList();
    }
}
