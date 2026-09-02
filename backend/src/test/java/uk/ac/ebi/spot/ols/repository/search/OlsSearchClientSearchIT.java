package uk.ac.ebi.spot.ols.repository.search;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OlsSearchClientSearchIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.SearchClientHandle searchClientHandle;
    private static OlsSearchClient searchClient;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializeClassDatabase(POSTGRES);
        searchClientHandle = PostgresIntegrationTestSupport.createSearchClient(POSTGRES);
        searchClient = searchClientHandle.searchClient();
    }

    @AfterAll
    static void closeDatabaseClient() {
        searchClientHandle.close();
    }

    @Test
    void searchesAndPagesRawJsonWithLegacyFacetCounts() {
        OlsSearchQuery query = new OlsSearchQuery();
        query.setSearchText("liver");
        query.addFilter("isObsolete", List.of("false"), SearchType.WHOLE_FIELD);
        query.addFacetField("ontologyId");
        query.addFacetField("type");

        OlsSearchClient.RawSearchResult result = searchClient.searchRaw(query, 0, 1, false);

        assertThat(result.numFound).isEqualTo(4);
        assertThat(result.jsonStrings).hasSize(1);
        assertThat(label(result.jsonStrings.get(0))).isEqualTo("Liver disease");
        assertThat(result.facets.get("ontologyId")).containsEntry("efo", 4L);
        assertThat(result.facets.get("type"))
                .containsEntry("class", 3L)
                .containsEntry("individual", 1L);
    }

    @Test
    void appliesExactFieldOntologyTypeSlimAndLocalFilters() {
        OlsSearchQuery query = new OlsSearchQuery();
        query.setSearchText("Liver disease");
        query.setExactMatch(true);
        query.setSearchFields(List.of("label"));
        query.addFilter("ontologyId", List.of("EFO"), SearchType.WHOLE_FIELD);
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("subset", List.of("core"), SearchType.WHOLE_FIELD);
        query.addFilter("isDefiningOntology", List.of("true"), SearchType.WHOLE_FIELD);
        query.addFilter("isObsolete", List.of("false"), SearchType.WHOLE_FIELD);

        OlsSearchClient.RawSearchResult result = searchClient.searchRaw(query, 0, 10, false);

        assertThat(result.numFound).isEqualTo(1);
        assertThat(result.jsonStrings).singleElement()
                .satisfies(json -> assertThat(label(json)).isEqualTo("Liver disease"));
    }

    @Test
    void searchesACompleteIriWithoutTokenisingIt() {
        OlsSearchQuery query = new OlsSearchQuery();
        query.setSearchText("HTTP://EXAMPLE.ORG/EFO_0001");
        query.addFilter("isObsolete", List.of("false"), SearchType.WHOLE_FIELD);

        OlsSearchClient.RawSearchResult result = searchClient.searchRaw(query, 0, 10, false);

        assertThat(result.numFound).isEqualTo(1);
        assertThat(result.jsonStrings).singleElement()
                .satisfies(json -> assertThat(iri(json)).isEqualTo("http://example.org/EFO_0001"));
    }

    @Test
    void executesTheGroupedRawSearchPathWithDeterministicPagination() {
        OlsSearchQuery query = new OlsSearchQuery();
        query.setSearchText("liver");
        query.addFilter("isObsolete", List.of("false"), SearchType.WHOLE_FIELD);

        OlsSearchClient.RawSearchResult result = searchClient.searchRaw(query, 1, 1, true);

        assertThat(result.numFound).isEqualTo(4);
        assertThat(result.jsonStrings).hasSize(1);
        assertThat(iri(result.jsonStrings.get(0))).isEqualTo("http://example.org/EFO_1001");
    }

    @Test
    void includesTheRequestedParentWithItsPostgresBackedDescendants() {
        String parentIri = "http://example.org/EFO_0001";

        OlsSearchQuery exclusive = new OlsSearchQuery();
        exclusive.addFilter("isObsolete", List.of("false"), SearchType.WHOLE_FIELD);
        exclusive.addFilter("hierarchicalAncestor", List.of(parentIri), SearchType.WHOLE_FIELD);

        OlsSearchQuery inclusive = new OlsSearchQuery();
        inclusive.addFilter("isObsolete", List.of("false"), SearchType.WHOLE_FIELD);
        inclusive.addAnyFilter(Map.of(
                "iri", List.of(parentIri),
                "hierarchicalAncestor", List.of(parentIri)), SearchType.WHOLE_FIELD);

        assertThat(searchClient.searchRaw(exclusive, 0, 10, false).jsonStrings)
                .extracting(OlsSearchClientSearchIT::iri)
                .containsExactly("http://example.org/EFO_1001");
        assertThat(searchClient.searchRaw(inclusive, 0, 10, false).jsonStrings)
                .extracting(OlsSearchClientSearchIT::iri)
                .containsExactly(
                        "http://example.org/EFO_0001",
                        "http://example.org/EFO_1001");
    }

    private static String label(String json) {
        return JsonParser.parseString(json).getAsJsonObject().get("label").getAsString();
    }

    private static String iri(String json) {
        return JsonParser.parseString(json).getAsJsonObject().get("iri").getAsString();
    }
}
