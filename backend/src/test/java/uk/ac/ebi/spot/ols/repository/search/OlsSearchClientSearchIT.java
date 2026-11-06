package uk.ac.ebi.spot.ols.repository.search;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jooq.impl.DSL;
import org.jooq.SQLDialect;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.util.List;
import java.util.Map;
import java.sql.Connection;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.inSubsetTree;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OlsSearchClientSearchIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.SearchClientHandle searchClientHandle;
    private static OlsSearchClient searchClient;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        PostgresIntegrationTestSupport.initializeClassDatabase(POSTGRES);
        loadTreeCountFixture();
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

    // Cohort -> location individual -> Country -> Geographic Area. The location is imported;
    // import/obsolete/type filters apply to members, not to intermediate path context.
    private static void loadTreeCountFixture() throws Exception {
        try (Connection conn = POSTGRES.createConnection("")) {
            var dsl = DSL.using(conn, SQLDialect.POSTGRES);
            try (var out = new ByteArrayOutputStream()) {
                try (var gzip = new GZIPOutputStream(out)) { gzip.write("{}".getBytes()); }
                record Row(String ontology, String iri, String type, boolean nativeMember, boolean obsolete,
                           String[] subsets, String[] direct, String[] hierarchical) {}
                String[] empty = {};
                for (Row row : List.of(
                        new Row("treecounts", "area", "class", true, false, empty, empty, empty),
                        new Row("treecounts", "country", "class", true, false, empty, new String[]{"area"}, empty),
                        new Row("treecounts", "target", "class", true, false, empty, empty, empty),
                        new Row("treecounts", "location", "individual", false, false, empty, new String[]{"country", "area"}, empty),
                        new Row("treecounts", "cohort", "individual", true, false, new String[]{"s1", "s2"}, empty, new String[]{"location"}),
                        new Row("treecounts", "direct", "individual", true, false, new String[]{"s1"}, new String[]{"target"}, empty),
                        new Row("treecounts", "both", "individual", true, false, new String[]{"s1", "s2"}, new String[]{"target"}, new String[]{"target"}),
                        new Row("treecounts", "hierarchical", "individual", true, false, new String[]{"s2"}, empty, new String[]{"target"}),
                        new Row("treecounts", "imported", "individual", false, false, new String[]{"s1"}, new String[]{"target"}, empty),
                        new Row("treecounts", "obsolete", "individual", true, true, new String[]{"s1"}, new String[]{"target"}, empty),
                        new Row("treecounts", "class-member", "class", true, false, new String[]{"s1"}, new String[]{"target"}, empty),
                        new Row("treecounts", "self", "individual", true, false, new String[]{"s1"}, new String[]{"self"}, new String[]{"self"}),
                        new Row("othercounts", "location", "individual", true, false, empty, new String[]{"foreign-area"}, empty),
                        new Row("othercounts", "cohort", "individual", true, false, new String[]{"s1"}, new String[]{"target"}, new String[]{"location"})
                )) {
                    dsl.execute("INSERT INTO ols_entities (id, ontology_id, iri, type, search_type, _json, "
                                    + "is_defining_ontology, is_obsolete, subset, direct_ancestors, hierarchical_ancestors) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::text[], ?::text[], ?::text[])",
                            row.ontology + "+" + row.iri, row.ontology, row.iri, row.type, row.type, out.toByteArray(),
                            row.nativeMember, row.obsolete, row.subsets, row.direct, row.hierarchical);
                }
            }
        }
    }

    private static OlsSearchQuery treeCountQuery() {
        var query = new OlsSearchQuery();
        query.addFilter("ontologyId", List.of("treecounts"), SearchType.WHOLE_FIELD);
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
        query.addFilter("subset", List.of("s1", "s2"), SearchType.WHOLE_FIELD);
        query.addFilter("isObsolete", List.of("false"), SearchType.WHOLE_FIELD);
        query.addFacetField("treeAncestor");
        return query;
    }

    private static Map<String, Long> treeCounts(OlsSearchQuery query) {
        return searchClient.searchPaginated(query, PageRequest.of(0, 1)).facetFieldToCounts.get("treeAncestor");
    }

    @Test
    void treeCountsFollowLocationClassAncestryAndAgreeWithSubsetTreeMembership() throws Exception {
        var counts = treeCounts(treeCountQuery());
        assertThat(counts).containsEntry("country", 1L).containsEntry("area", 1L).containsEntry("location", 1L);
        try (Connection conn = POSTGRES.createConnection("")) {
            var iris = DSL.using(conn, SQLDialect.POSTGRES)
                    .select(DSL.field("iri", String.class)).from("ols_entities")
                    .where(DSL.field("ontology_id").eq("treecounts"))
                    .and(inSubsetTree(null, List.of("s1", "s2"))).fetch(0, String.class);
            assertThat(iris).contains("country", "area", "location").containsAll(counts.keySet());
        }
    }

    @Test
    void treeCountsDeduplicateOverlappingPathsAndMultipleSubsetsAndExcludeSelf() {
        var query = treeCountQuery();
        query.addFilter("isDefiningOntology", List.of("true"), SearchType.WHOLE_FIELD);
        assertThat(treeCounts(query)).containsEntry("target", 3L).doesNotContainKey("self");
        // Raw search uses the same facet dispatcher as the ontology entity-list API.
        assertThat(searchClient.searchRaw(query, 0, 1, false).facets.get("treeAncestor"))
                .isEqualTo(treeCounts(query));
    }

    @Test
    void treeCountsApplyMemberTypeImportObsoleteAndSubsetFilters() {
        assertThat(treeCounts(treeCountQuery())).containsEntry("target", 4L);
        var nativeQuery = treeCountQuery();
        nativeQuery.addFilter("isDefiningOntology", List.of("true"), SearchType.WHOLE_FIELD);
        assertThat(treeCounts(nativeQuery)).containsEntry("target", 3L).containsEntry("country", 1L);
        var obsoleteQuery = treeCountQuery();
        obsoleteQuery.filters.removeIf(f -> f.field.equals("isObsolete"));
        assertThat(treeCounts(obsoleteQuery)).containsEntry("target", 5L);
        var classQuery = treeCountQuery();
        classQuery.filters.removeIf(f -> f.field.equals("type"));
        classQuery.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        assertThat(treeCounts(classQuery)).containsOnlyKeys("target").containsEntry("target", 1L);
        var subsetQuery = treeCountQuery();
        subsetQuery.filters.removeIf(f -> f.field.equals("subset"));
        subsetQuery.addFilter("subset", List.of("s2"), SearchType.WHOLE_FIELD);
        assertThat(treeCounts(subsetQuery)).containsEntry("target", 2L);
        var emptyQuery = treeCountQuery();
        emptyQuery.addFilter("iri", List.of("missing"), SearchType.WHOLE_FIELD);
        assertThat(treeCounts(emptyQuery)).isEmpty();
    }

    @Test
    void treeCountsKeepIntermediateJoinsAndMemberIdentitiesWithinTheirOntology() {
        assertThat(treeCounts(treeCountQuery())).doesNotContainKey("foreign-area");
        var query = treeCountQuery();
        query.filters.removeIf(f -> f.field.equals("ontologyId"));
        query.addFilter("ontologyId", List.of("treecounts", "othercounts"), SearchType.WHOLE_FIELD);
        assertThat(treeCounts(query)).containsEntry("country", 1L).containsEntry("area", 1L)
                .containsEntry("foreign-area", 1L).containsEntry("location", 2L).containsEntry("target", 5L);
    }

    @Test
    void treeCountsLeaveOrdinaryFacetsAndTheirExclusionSemanticsUnchanged() {
        var query = treeCountQuery();
        query.addFacetField("directAncestor");
        query.addFacetField("subset");
        var result = searchClient.searchRaw(query, 0, 1, false);
        assertThat(result.facets.get("treeAncestor")).containsEntry("country", 1L);
        assertThat(result.facets.get("directAncestor")).containsEntry("target", 3L)
                .containsEntry("self", 1L).doesNotContainKey("country");
        assertThat(result.facets.get("subset")).containsEntry("s1", 5L).containsEntry("s2", 3L);
    }

    private static String iri(String json) {
        return JsonParser.parseString(json).getAsJsonObject().get("iri").getAsString();
    }
}
