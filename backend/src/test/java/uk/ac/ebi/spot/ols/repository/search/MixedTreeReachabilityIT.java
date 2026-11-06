package uk.ac.ebi.spot.ols.repository.search;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.inSubsetTree;

/** Exercises RDF2JSON output, including missing hierarchical arrays on typed individuals. */
@Testcontainers
class MixedTreeReachabilityIT {
    private static final String NS = "https://example.org/mixed#";
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = PostgresIntegrationTestSupport.newContainer();
    private static PostgresIntegrationTestSupport.SearchClientHandle handle;

    @BeforeAll
    static void loadGeneratedFixture() throws Exception {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        try (var in = MixedTreeReachabilityIT.class.getResourceAsStream("/tree-reachability/generated.json");
             Connection conn = POSTGRES.createConnection("")) {
            JsonObject ontology = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("ontologies").get(0).getAsJsonObject();
            var dsl = DSL.using(conn, SQLDialect.POSTGRES);
            for (String kind : List.of("classes", "individuals")) {
                for (JsonElement value : ontology.getAsJsonArray(kind)) {
                    JsonObject entity = value.getAsJsonObject();
                    String iri = entity.get("iri").getAsString();
                    var out = new ByteArrayOutputStream();
                    try (var gzip = new GZIPOutputStream(out)) {
                        gzip.write(entity.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    dsl.execute("""
                            INSERT INTO ols_entities
                            (id, ontology_id, iri, type, search_type, _json, subset,
                             direct_ancestors, hierarchical_ancestors)
                            VALUES (?, 'mixed', ?, ?, ?, ?, ?::text[], ?::text[], ?::text[])
                            """, "mixed+" + kind + "+" + iri, iri,
                            kind.equals("classes") ? "OntologyClass" : "OntologyIndividual",
                            kind.equals("classes") ? "class" : "individual", out.toByteArray(),
                            strings(entity.get("http://www.geneontology.org/formats/oboInOwl#inSubset")),
                            strings(entity.get("directAncestor")), strings(entity.get("hierarchicalAncestor")));
                }
            }
            // An imported copy of C has different ancestry. Never follow it from mixed's I.
            dsl.execute("""
                    INSERT INTO ols_entities (id, ontology_id, iri, type, search_type, _json,
                        hierarchical_ancestors)
                    SELECT 'foreign-C', 'foreign', iri, type, search_type, _json, ARRAY['foreign-root']
                    FROM ols_entities WHERE ontology_id = 'mixed' AND iri = ?
                    """, NS + "C");
        }
        handle = PostgresIntegrationTestSupport.createSearchClient(POSTGRES);
    }

    private static String[] strings(JsonElement value) {
        if (value == null || value.isJsonNull()) return new String[0];
        if (value.isJsonObject()) return strings(value.getAsJsonObject().get("value"));
        if (!value.isJsonArray()) return new String[]{value.getAsString()};
        var values = new ArrayList<String>();
        value.getAsJsonArray().forEach(item -> values.addAll(List.of(strings(item))));
        return values.toArray(String[]::new);
    }

    @AfterAll
    static void closeClient() { if (handle != null) handle.close(); }

    private static Map<String, Long> counts(String... subsets) {
        var query = new OlsSearchQuery();
        query.addFilter("ontologyId", List.of("mixed"), SearchType.WHOLE_FIELD);
        query.addFilter("subset", List.of(subsets), SearchType.WHOLE_FIELD);
        query.addFacetField("treeAncestor");
        return handle.searchClient().searchRaw(query, 0, 1, false).facets.get("treeAncestor");
    }

    private static List<String> closure(String... subsets) throws Exception {
        try (Connection conn = POSTGRES.createConnection("")) {
            return DSL.using(conn, SQLDialect.POSTGRES).select(DSL.field("iri", String.class))
                    .from("ols_entities").where(DSL.field("ontology_id").eq("mixed"))
                    .and(inSubsetTree(null, List.of(subsets))).fetch(0, String.class);
        }
    }

    @Test
    void followsTypeThenHierarchicalAncestryFromGeneratedRdf() throws Exception {
        assertThat(closure(NS + "S", NS + "S2")).containsExactlyInAnyOrder(NS + "P", NS + "C", NS + "I");
        assertThat(counts(NS + "S", NS + "S2")).containsExactlyInAnyOrderEntriesOf(Map.of(NS + "P", 1L, NS + "C", 1L));
    }

    @Test
    void followsRepeatedAlternatingEdgesAndDeduplicatesSharedRoutes() throws Exception {
        assertThat(closure(NS + "Long")).containsExactlyInAnyOrder(NS + "P", NS + "C", NS + "I", NS + "J", NS + "K", NS + "L");
        assertThat(counts(NS + "Long", NS + "S", NS + "S2")).containsExactlyInAnyOrderEntriesOf(
                Map.of(NS + "P", 2L, NS + "C", 2L, NS + "I", 1L, NS + "J", 1L, NS + "K", 1L));
    }

    @Test
    void terminatesCyclesAndExcludesSelfCounts() throws Exception {
        assertThat(closure(NS + "Cycle")).containsExactlyInAnyOrder(NS + "X", NS + "Y", NS + "Z");
        assertThat(counts(NS + "Cycle")).containsExactlyInAnyOrderEntriesOf(Map.of(NS + "X", 1L, NS + "Y", 1L));
        var query = new OlsSearchQuery();
        query.addFilter("ontologyId", List.of("mixed"), SearchType.WHOLE_FIELD);
        query.addFilter("iri", List.of(NS + "X"), SearchType.WHOLE_FIELD);
        query.addFacetField("treeAncestor");
        assertThat(handle.searchClient().searchRaw(query, 0, 1, false).facets.get("treeAncestor"))
                .containsExactlyInAnyOrderEntriesOf(Map.of(NS + "Y", 1L));
    }
}
