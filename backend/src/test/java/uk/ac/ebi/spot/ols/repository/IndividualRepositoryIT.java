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
class IndividualRepositoryIT {

    private static final String EFO_INDIVIDUAL = "http://example.org/EFO_I100";
    private static final String SECOND_EFO_INDIVIDUAL = "http://example.org/EFO_I200";
    private static final String OBSOLETE_EFO_INDIVIDUAL = "http://example.org/EFO_I999";
    private static final String DUO_INDIVIDUAL = "http://example.org/DUO_I100";
    private static final String LIVER_CLASS = "http://example.org/EFO_0001";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.IndividualRepositoryHandle repositoryHandle;
    private static IndividualRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializeIndividualDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createIndividualRepository(POSTGRES);
        repository = repositoryHandle.repository();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void listsOnlyActiveIndividualsInStableOrder() throws Exception {
        OlsFacetedResultsPage<JsonElement> page = find(
                PageRequest.of(0, 20), null, null, null, false,
                Map.of("isObsolete", List.of("false")));

        assertThat(iris(page)).containsExactly(
                DUO_INDIVIDUAL, EFO_INDIVIDUAL, SECOND_EFO_INDIVIDUAL);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void includesObsoleteIndividualsAndSupportsPaginationAndSorting() throws Exception {
        OlsFacetedResultsPage<JsonElement> all = find(
                PageRequest.of(0, 20), null, null, null, false, Map.of());
        OlsFacetedResultsPage<JsonElement> secondPage = find(
                PageRequest.of(1, 2), null, null, null, false,
                Map.of("isObsolete", List.of("false")));
        OlsFacetedResultsPage<JsonElement> descending = find(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "iri")),
                null, null, null, false,
                Map.of("isObsolete", List.of("false")));

        assertThat(iris(all)).containsExactly(
                DUO_INDIVIDUAL, EFO_INDIVIDUAL, SECOND_EFO_INDIVIDUAL,
                OBSOLETE_EFO_INDIVIDUAL);
        assertThat(iris(secondPage)).containsExactly(SECOND_EFO_INDIVIDUAL);
        assertThat(secondPage.getTotalElements()).isEqualTo(3);
        assertThat(secondPage.getTotalPages()).isEqualTo(2);
        assertThat(iris(descending)).containsExactly(
                SECOND_EFO_INDIVIDUAL, EFO_INDIVIDUAL, DUO_INDIVIDUAL);
    }

    @Test
    void supportsFreeTextFieldRestrictedExactAndBoostedSearch() throws Exception {
        OlsFacetedResultsPage<JsonElement> freeText = find(
                PageRequest.of(0, 20), "permission", null, null, false,
                Map.of("isObsolete", List.of("false")));
        OlsFacetedResultsPage<JsonElement> exactLabel = find(
                PageRequest.of(0, 20), "Liver specimen alpha", "label", null, true,
                Map.of("isObsolete", List.of("false")));
        OlsFacetedResultsPage<JsonElement> restrictedMiss = find(
                PageRequest.of(0, 20), "biomedical", "label", null, false,
                Map.of("isObsolete", List.of("false")));
        OlsFacetedResultsPage<JsonElement> baseline = find(
                PageRequest.of(0, 20), "specimen", null, null, false,
                Map.of("isObsolete", List.of("false")));
        OlsFacetedResultsPage<JsonElement> boosted = find(
                PageRequest.of(0, 20), "specimen", null, "definition^1000", false,
                Map.of("isObsolete", List.of("false")));

        assertThat(iris(freeText)).containsExactly(DUO_INDIVIDUAL);
        assertThat(iris(exactLabel)).containsExactly(EFO_INDIVIDUAL);
        assertThat(restrictedMiss).isEmpty();
        assertThat(iris(baseline).get(0)).isEqualTo(EFO_INDIVIDUAL);
        assertThat(iris(boosted).get(0)).isEqualTo(SECOND_EFO_INDIVIDUAL);
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

        assertThat(iris(repeated)).containsExactly(DUO_INDIVIDUAL, EFO_INDIVIDUAL);
        assertThat(iris(commaSeparated)).containsExactly(DUO_INDIVIDUAL, EFO_INDIVIDUAL);
        assertThat(unknown).isEmpty();
    }

    @Test
    void scopesToOneOntologyAndGetsExistingAndMissingIndividuals() throws Exception {
        OlsFacetedResultsPage<JsonElement> efo = repository.findByOntologyId(
                "EFO", PageRequest.of(0, 20), "en", null, null, null, false,
                Map.of("isObsolete", List.of("false")), new JsonTransformOptions());
        var existing = repository.getByOntologyIdAndIri(
                "efo", EFO_INDIVIDUAL, "en", new JsonTransformOptions());
        var missing = repository.getByOntologyIdAndIri(
                "efo", "http://example.org/missing", "en", new JsonTransformOptions());

        assertThat(iris(efo)).containsExactly(EFO_INDIVIDUAL, SECOND_EFO_INDIVIDUAL);
        assertThat(existing.any().get("label")).isEqualTo("Liver specimen alpha");
        assertThat(missing).isNull();
    }

    @Test
    void returnsActiveClassIndividualsAndCanIncludeObsoleteMembers() throws Exception {
        OlsFacetedResultsPage<JsonElement> active = repository.getIndividualsOfClass(
                "efo", LIVER_CLASS, PageRequest.of(0, 20), false, "en",
                new JsonTransformOptions());
        OlsFacetedResultsPage<JsonElement> all = repository.getIndividualsOfClass(
                "efo", LIVER_CLASS, PageRequest.of(0, 20), true, "en",
                new JsonTransformOptions());

        assertThat(iris(active)).containsExactly(EFO_INDIVIDUAL);
        assertThat(iris(all)).containsExactly(EFO_INDIVIDUAL, OBSOLETE_EFO_INDIVIDUAL);
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
                "efo/unsafe", EFO_INDIVIDUAL, "en", options))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.getIndividualsOfClass(
                "efo", LIVER_CLASS, page, false, "en_US", options))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OlsFacetedResultsPage<JsonElement> find(
            PageRequest pageable,
            String search,
            String searchFields,
            String boostFields,
            boolean exactMatch,
            Map<String, Collection<String>> properties) throws Exception {
        return find(pageable, search, searchFields, boostFields, exactMatch, properties, "en");
    }

    private static OlsFacetedResultsPage<JsonElement> find(
            PageRequest pageable,
            String search,
            String searchFields,
            String boostFields,
            boolean exactMatch,
            Map<String, Collection<String>> properties,
            String lang) throws Exception {
        return repository.find(
                pageable, lang, search, searchFields, boostFields, exactMatch,
                properties, new JsonTransformOptions());
    }

    private static List<String> iris(OlsFacetedResultsPage<JsonElement> page) {
        return page.getContent().stream()
                .map(element -> element.getAsJsonObject().get("iri").getAsString())
                .toList();
    }
}
