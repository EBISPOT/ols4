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
class EntityRepositoryIT {

    private static final String LIVER_IRI = "http://example.org/EFO_0001";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.EntityRepositoryHandle repositoryHandle;
    private static EntityRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createEntityRepository(POSTGRES);
        repository = repositoryHandle.repository();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void listsActiveEntitiesAcrossSupportedTypesInDeterministicOrder() throws Exception {
        OlsFacetedResultsPage<JsonElement> page = find(
                PageRequest.of(0, 20), null, null, null, null, false, null,
                Map.of("isObsolete", List.of("false")), true);

        assertThat(ids(page)).containsExactly(
                "duo+class+http://example.org/DUO_0001",
                "efo+class+http://example.org/EFO_0001",
                "efo+class+http://example.org/EFO_0002",
                "efo+property+http://example.org/EFO_0100");
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    @Test
    void canIncludeObsoleteEntitiesAndRestrictEntityType() throws Exception {
        OlsFacetedResultsPage<JsonElement> all = find(
                PageRequest.of(0, 20), null, null, null, null, false, null,
                Map.of(), true);
        OlsFacetedResultsPage<JsonElement> classes = find(
                PageRequest.of(0, 20), null, null, null, null, false, null,
                Map.of("type", List.of("class"), "isObsolete", List.of("false")), true);

        assertThat(ids(all)).hasSize(5).contains("efo+class+http://example.org/EFO_0999");
        assertThat(searchTypes(classes)).containsOnly("class").hasSize(3);
    }

    @Test
    void appliesPaginationAndSupportedSorting() throws Exception {
        OlsFacetedResultsPage<JsonElement> secondPage = find(
                PageRequest.of(1, 2), null, null, null, null, false, null,
                Map.of("isObsolete", List.of("false")), true);
        OlsFacetedResultsPage<JsonElement> descending = find(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "iri")),
                null, null, null, null, false, null,
                Map.of("isObsolete", List.of("false")), true);

        assertThat(ids(secondPage)).containsExactly(
                "efo+class+http://example.org/EFO_0002",
                "efo+property+http://example.org/EFO_0100");
        assertThat(secondPage.getTotalElements()).isEqualTo(4);
        assertThat(secondPage.getTotalPages()).isEqualTo(2);
        assertThat(iris(descending)).containsExactly(
                "http://example.org/EFO_0100",
                "http://example.org/EFO_0002",
                "http://example.org/EFO_0001",
                "http://example.org/DUO_0001");
    }

    @Test
    void supportsFreeTextFieldRestrictedAndExactSearch() throws Exception {
        OlsFacetedResultsPage<JsonElement> freeText = find(
                PageRequest.of(0, 20), "permission", null, null, null, false, null,
                Map.of("isObsolete", List.of("false")), true);
        OlsFacetedResultsPage<JsonElement> exactLabel = find(
                PageRequest.of(0, 20), "Liver disease", "label", null, null, true, null,
                Map.of("isObsolete", List.of("false")), true);
        OlsFacetedResultsPage<JsonElement> restrictedMiss = find(
                PageRequest.of(0, 20), "biomedical", "label", null, null, false, null,
                Map.of("isObsolete", List.of("false")), true);

        assertThat(iris(freeText)).containsExactly("http://example.org/DUO_0001");
        assertThat(iris(exactLabel)).containsExactly(LIVER_IRI);
        assertThat(restrictedMiss).isEmpty();
    }

    @Test
    void appliesExplicitBoostFieldsToRanking() throws Exception {
        OlsFacetedResultsPage<JsonElement> baseline = find(
                PageRequest.of(0, 20), "liver", null, null, null, false, null,
                Map.of("isObsolete", List.of("false")), true);
        OlsFacetedResultsPage<JsonElement> boosted = find(
                PageRequest.of(0, 20), "liver", null, "definition^1000", null, false, null,
                Map.of("isObsolete", List.of("false")), true);

        assertThat(iris(baseline).get(0)).isEqualTo(LIVER_IRI);
        assertThat(iris(boosted).get(0)).isEqualTo("http://example.org/EFO_0002");
    }

    @Test
    void returnsFacetCountsAndExcludesRequestedOntologies() throws Exception {
        OlsFacetedResultsPage<JsonElement> page = find(
                PageRequest.of(0, 20), null, null, null, "ontologyId type", false,
                List.of("efo"), Map.of("isObsolete", List.of("false")), true);

        assertThat(iris(page)).containsExactly("http://example.org/DUO_0001");
        assertThat(page.facetFieldToCounts.get("ontologyId")).containsEntry("duo", 1L);
        assertThat(page.facetFieldToCounts.get("type")).containsEntry("class", 1L);
    }

    @Test
    void canSkipTheExactTotalCount() throws Exception {
        OlsFacetedResultsPage<JsonElement> page = find(
                PageRequest.of(0, 2), null, null, null, null, false, null,
                Map.of("isObsolete", List.of("false")), false);

        assertThat(page).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(1);
    }

    @Test
    void supportsRepeatedCommaSeparatedAndUriNamedDynamicValues() throws Exception {
        OlsFacetedResultsPage<JsonElement> repeated = find(
                PageRequest.of(0, 20), null, null, null, null, false, null,
                Map.of("http://example.org/category", List.of("clinical", "policy")), true);
        OlsFacetedResultsPage<JsonElement> commaSeparated = find(
                PageRequest.of(0, 20), null, null, null, null, false, null,
                Map.of("http://example.org/category", List.of("clinical,policy")), true);
        OlsFacetedResultsPage<JsonElement> unknown = find(
                PageRequest.of(0, 20), null, null, null, null, false, null,
                Map.of("unknown-property", List.of("anything")), true);

        assertThat(iris(repeated)).containsExactly(
                "http://example.org/DUO_0001", LIVER_IRI);
        assertThat(iris(commaSeparated)).containsExactly(
                "http://example.org/DUO_0001", LIVER_IRI);
        assertThat(unknown).isEmpty();
    }

    @Test
    void scopesSearchToOneOntology() throws Exception {
        OlsFacetedResultsPage<JsonElement> page = repository.findByOntologyId(
                "EFO",
                PageRequest.of(0, 20),
                "en",
                null,
                null,
                null,
                "type",
                false,
                Map.of("isObsolete", List.of("false")),
                new JsonTransformOptions());

        assertThat(iris(page)).containsExactly(
                LIVER_IRI,
                "http://example.org/EFO_0002",
                "http://example.org/EFO_0100");
        assertThat(page.facetFieldToCounts.get("type"))
                .containsEntry("class", 2L)
                .containsEntry("property", 1L);
    }

    @Test
    void getsExistingEntityAndReturnsNullForMissingEntity() {
        JsonElement existing = repository.getByOntologyIdAndIri(
                "efo", LIVER_IRI, "en", new JsonTransformOptions());
        JsonElement missing = repository.getByOntologyIdAndIri(
                "efo", "http://example.org/missing", "en", new JsonTransformOptions());

        assertThat(existing.getAsJsonObject().get("label").getAsString()).isEqualTo("Liver disease");
        assertThat(missing).isNull();
    }

    @Test
    void returnsEntitiesThatReferenceTheRequestedEntity() throws Exception {
        OlsFacetedResultsPage<JsonElement> page = repository.getRelatedFrom(
                "efo",
                LIVER_IRI,
                PageRequest.of(0, 20),
                "en",
                new JsonTransformOptions());

        assertThat(iris(page)).containsExactly("http://example.org/EFO_0002");
    }

    @Test
    void validatesLanguageAndOntologyIdentifiersForEveryRepositoryRoute() {
        assertThatThrownBy(() -> find(
                PageRequest.of(0, 20), null, null, null, null, false, null,
                Map.of(), true, "en_US"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.findByOntologyId(
                "efo/unsafe", PageRequest.of(0, 20), "en", null, null, null, null,
                false, Map.of(), new JsonTransformOptions()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.getByOntologyIdAndIri(
                "efo/unsafe", LIVER_IRI, "en", new JsonTransformOptions()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.getRelatedFrom(
                "efo", LIVER_IRI, PageRequest.of(0, 20), "en_US", new JsonTransformOptions()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OlsFacetedResultsPage<JsonElement> find(
            PageRequest pageable,
            String search,
            String searchFields,
            String boostFields,
            String facetFields,
            boolean exactMatch,
            Collection<String> excludeOntologyIds,
            Map<String, Collection<String>> properties,
            boolean includeTotal) throws Exception {
        return find(
                pageable, search, searchFields, boostFields, facetFields, exactMatch,
                excludeOntologyIds, properties, includeTotal, "en");
    }

    private static OlsFacetedResultsPage<JsonElement> find(
            PageRequest pageable,
            String search,
            String searchFields,
            String boostFields,
            String facetFields,
            boolean exactMatch,
            Collection<String> excludeOntologyIds,
            Map<String, Collection<String>> properties,
            boolean includeTotal,
            String lang) throws Exception {
        return repository.find(
                pageable,
                lang,
                search,
                searchFields,
                boostFields,
                facetFields,
                exactMatch,
                excludeOntologyIds,
                properties,
                new JsonTransformOptions(),
                includeTotal);
    }

    private static List<String> ids(OlsFacetedResultsPage<JsonElement> page) {
        return values(page, "id");
    }

    private static List<String> iris(OlsFacetedResultsPage<JsonElement> page) {
        return values(page, "iri");
    }

    private static List<String> searchTypes(OlsFacetedResultsPage<JsonElement> page) {
        return page.getContent().stream()
                .map(element -> element.getAsJsonObject().getAsJsonArray("type").get(1).getAsString())
                .toList();
    }

    private static List<String> values(OlsFacetedResultsPage<JsonElement> page, String field) {
        return page.getContent().stream()
                .map(element -> element.getAsJsonObject().get(field).getAsString())
                .toList();
    }
}
