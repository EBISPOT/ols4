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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OlsSearchClientSelectIT {

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
    void searchesAndPagesTheRawDocumentsUsedBySelect() {
        OlsSearchQuery query = new OlsSearchQuery();
        query.setSearchText("liver");
        query.addFilter("isObsolete", List.of("false"), SearchType.WHOLE_FIELD);

        OlsSearchClient.RawSearchResult result = searchClient.searchRaw(query, 1, 1);

        assertThat(result.numFound).isEqualTo(4);
        assertThat(result.jsonStrings).singleElement()
                .satisfies(json -> assertThat(iri(json))
                        .isEqualTo("http://example.org/EFO_1001"));
    }

    @Test
    void appliesOntologyTypeSlimAndLocalSelectFilters() {
        OlsSearchQuery query = new OlsSearchQuery();
        query.setSearchText("liver");
        query.addFilter("ontologyId", List.of("EFO"), SearchType.WHOLE_FIELD);
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("subset", List.of("core"), SearchType.WHOLE_FIELD);
        query.addFilter("isDefiningOntology", List.of("true"), SearchType.WHOLE_FIELD);
        query.addFilter("isObsolete", List.of("false"), SearchType.WHOLE_FIELD);

        OlsSearchClient.RawSearchResult result = searchClient.searchRaw(query, 0, 10);

        assertThat(result.numFound).isEqualTo(1);
        assertThat(result.jsonStrings).singleElement()
                .satisfies(json -> assertThat(iri(json))
                        .isEqualTo("http://example.org/EFO_0001"));
    }

    @Test
    void appliesDirectAndHierarchicalAncestorSelectFilters() {
        String parentIri = "http://example.org/EFO_0001";

        OlsSearchQuery direct = new OlsSearchQuery();
        direct.setSearchText("liver");
        direct.addFilter("directAncestor", List.of(parentIri), SearchType.WHOLE_FIELD);
        direct.addFilter("isObsolete", List.of("false"), SearchType.WHOLE_FIELD);

        OlsSearchQuery hierarchical = new OlsSearchQuery();
        hierarchical.setSearchText("liver");
        hierarchical.addFilter(
                "hierarchicalAncestor", List.of(parentIri), SearchType.WHOLE_FIELD);
        hierarchical.addFilter("isObsolete", List.of("true"), SearchType.WHOLE_FIELD);

        assertThat(searchClient.searchRaw(direct, 0, 10).jsonStrings)
                .extracting(OlsSearchClientSelectIT::iri)
                .containsExactly(
                        "http://example.org/EFO_1001",
                        "http://example.org/EFO_I100");
        assertThat(searchClient.searchRaw(hierarchical, 0, 10).jsonStrings)
                .extracting(OlsSearchClientSelectIT::iri)
                .containsExactly("http://example.org/EFO_1999");
    }

    private static String iri(String json) {
        return JsonParser.parseString(json).getAsJsonObject().get("iri").getAsString();
    }
}
