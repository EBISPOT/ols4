package uk.ac.ebi.spot.ols.controller.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Thin real-Postgres coverage for {@link McpSearchService}'s two {@code @Tool} methods, on top of
 * the direct unit suite in {@link McpSearchServiceTest}.
 *
 * <p>Reuses {@link PostgresIntegrationTestSupport#initializeDatabase}/
 * {@link PostgresIntegrationTestSupport#createEntityRepository} - the base shared entity fixture
 * (three active classes {@code EFO_0001}/{@code EFO_0002}/{@code DUO_0001}, one active property
 * {@code EFO_0100}, one obsolete class {@code EFO_0999}) and the same {@code EntityRepository}
 * factory {@code McpClassServiceIT} already composes for its own {@code searchClasses} coverage.
 * No new fixture mechanism is needed. This class does not re-prove {@code EntityRepository}'s own
 * search/filter internals (already covered by other repository-level suites in this programme); it
 * only proves that {@code McpSearchService}'s hardcoded fixed-argument calls really reach real
 * Postgres and that the {@code McpSearchResult}/{@code McpFetchResult}-then-{@code gson.toJson}
 * output is genuinely correct for real data, plus one real-Postgres reproduction of the
 * not-found {@code NullPointerException} documented in {@code McpSearchServiceTest} (confirming it
 * is a genuine repository-returns-null behaviour, not an artifact of the unit layer's hand-rolled
 * fake).</p>
 */
@Testcontainers
class McpSearchServiceIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.EntityRepositoryHandle entityHandle;
    private static McpSearchService service;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        entityHandle = PostgresIntegrationTestSupport.createEntityRepository(POSTGRES);

        service = new McpSearchService();
        service.entityRepository = entityHandle.repository();
        // embeddingServiceClient/postgresClient left null: McpSearchService never calls either
        // (see McpSearchServiceTest's class Javadoc).
    }

    @AfterAll
    static void closeDatabaseClient() {
        if (entityHandle != null) {
            entityHandle.close();
        }
    }

    // ------------------------------------------------------------------
    // search
    // ------------------------------------------------------------------

    @Test
    void searchFindsAnActiveEntityByFreeTextThroughRealPostgresWithGenuinelyCorrectJson() throws Exception {
        String json = service.search("permission", null);

        JsonArray results = JsonParser.parseString(json).getAsJsonArray();
        assertThat(results).hasSize(1);

        JsonObject result = results.get(0).getAsJsonObject();
        assertThat(result.get("id").getAsString()).isEqualTo("duo+http://example.org/DUO_0001");
        assertThat(result.get("url").getAsString()).isEqualTo("http://example.org/DUO_0001");
        assertThat(result.get("title").getAsString()).isEqualTo("DUO:0001 Data use permission");
        assertThat(result.get("isObsolete").getAsBoolean()).isFalse();
    }

    @Test
    void searchExcludesObsoleteEntitiesByDefaultThroughRealPostgres() throws Exception {
        String json = service.search("Legacy liver concept", null);

        JsonArray results = JsonParser.parseString(json).getAsJsonArray();
        assertThat(results).isEmpty();
    }

    @Test
    void searchCanIncludeObsoleteEntitiesThroughRealPostgres() throws Exception {
        String json = service.search("Legacy liver concept", true);

        JsonArray results = JsonParser.parseString(json).getAsJsonArray();
        assertThat(results).extracting(e -> e.getAsJsonObject().get("id").getAsString())
                .containsExactly("efo+http://example.org/EFO_0999");
    }

    // ------------------------------------------------------------------
    // fetch
    // ------------------------------------------------------------------

    @Test
    void fetchReturnsGenuinelyCorrectJsonForARealClassEntityThroughRealPostgres() throws Exception {
        String json = service.fetch("efo+http://example.org/EFO_0001");

        JsonObject result = JsonParser.parseString(json).getAsJsonObject();
        assertThat(result.get("id").getAsString()).isEqualTo("efo+http://example.org/EFO_0001");
        assertThat(result.get("url").getAsString()).isEqualTo("http://example.org/EFO_0001");
        assertThat(result.get("title").getAsString()).isEqualTo("EFO:0001 Liver disease");
        assertThat(result.get("text").getAsString()).isEqualTo("A disorder affecting hepatic tissue.");
        assertThat(result.get("isObsolete").getAsBoolean()).isFalse();
        // See McpSearchServiceTest.fetchMetadataForAClassEntityIsOnlyItsTypeNotTheFullMcpClassStructure:
        // matches the committed golden file testcases_expected_output_api/mcp/fetch.json exactly.
        assertThat(result.get("metadata").getAsJsonObject().get("type").getAsString()).isEqualTo("class");
    }

    @Test
    void fetchOfAnObsoleteEntityStillSucceedsAndReportsItAsObsolete() throws Exception {
        String json = service.fetch("efo+http://example.org/EFO_0999");

        JsonObject result = JsonParser.parseString(json).getAsJsonObject();
        assertThat(result.get("isObsolete").getAsBoolean()).isTrue();
    }

    @Test
    void fetchThrowsNullPointerExceptionWhenNoRealEntityMatchesThisOntologyIdAndIriThroughRealPostgres() {
        // Real-Postgres reproduction of the defect documented in McpSearchServiceTest -
        // EntityRepository.getByOntologyIdAndIri returns a genuine null (via
        // OlsSearchClient.getFirst) when nothing matches, and McpSearchService.fetch() passes that
        // straight into McpFetchResult.fromJson with no null check. This confirms the NPE is real
        // repository behaviour, not an artifact of the unit layer's hand-rolled fake.
        assertThrows(
                NullPointerException.class,
                () -> service.fetch("efo+http://example.org/DOES_NOT_EXIST"));
    }
}
