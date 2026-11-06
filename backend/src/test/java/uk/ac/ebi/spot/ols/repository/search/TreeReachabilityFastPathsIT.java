package uk.ac.ebi.spot.ols.repository.search;

import org.jooq.impl.DSL;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.*;

/** Semantic counterexamples for ontology-wide nonrecursive closure selection. */
@Testcontainers
class TreeReachabilityFastPathsIT {
    @Container static final PostgreSQLContainer<?> DB = PostgresIntegrationTestSupport.newContainer();

    @BeforeAll static void setup() throws Exception {
        PostgresIntegrationTestSupport.initializeDatabase(DB);
        try (var conn = DB.createConnection("")) {
            var sql = DSL.using(conn, SQLDialect.POSTGRES);
            // Relation-only assertions never decode JSON, but retain a valid gzip payload.
            sql.execute("ALTER TABLE ols_entities ALTER COLUMN _json SET DEFAULT decode('1f8b08000000000002ffabae050043bfa6a302000000', 'hex')");
            for (String shape : List.of("direct", "hierarchical", "identical", "directContains", "hierarchicalContains", "nullDirect", "nullHierarchical", "duplicates", "nullBoth")) {
                String[] full = {"B", "R"}, empty = {};
                String[] direct = full, hierarchical = empty;
                switch (shape) {
                    case "hierarchical" -> { direct = empty; hierarchical = full; }
                    case "identical" -> hierarchical = full;
                    case "directContains" -> hierarchical = new String[]{"R"};
                    case "hierarchicalContains" -> { direct = new String[]{"R"}; hierarchical = full; }
                    case "nullDirect" -> { direct = null; hierarchical = full; }
                    case "nullHierarchical" -> hierarchical = null;
                    case "duplicates" -> { direct = new String[]{"B", "R", "B", "R"}; hierarchical = new String[]{"R", "B", "R"}; }
                    case "nullBoth" -> { direct = null; hierarchical = null; }
                }
                for (String member : List.of("M", "N")) {
                    sql.execute("INSERT INTO ols_entities(id, ontology_id, iri, type, subset, direct_ancestors, hierarchical_ancestors) VALUES (?,?,?,'OntologyClass',ARRAY['selected'],?::text[],?::text[])",
                            shape+member, shape, member, direct, hierarchical);
                }
                // Every row in each ontology respects the selected containment direction.
                sql.execute("INSERT INTO ols_entities(id, ontology_id, iri, type, direct_ancestors, hierarchical_ancestors) VALUES (?,?, 'B','OntologyClass',?::text[],?::text[])",
                        shape+"B", shape, direct == null ? null : direct.length == 0 ? empty : new String[]{"R"},
                        hierarchical == null ? null : hierarchical.length == 0 ? empty : new String[]{"R"});
                sql.execute("INSERT INTO ols_entities(id, ontology_id, iri, type) VALUES (?,?, 'R','OntologyClass')", shape+"R", shape);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"direct", "hierarchical", "identical", "directContains", "hierarchicalContains", "nullDirect", "nullHierarchical", "duplicates", "nullBoth"})
    void closureAndDistinctMemberCountsRespectFastPathShapes(String shape) throws Exception {
        try (var conn = DB.createConnection("")) {
            var sql = DSL.using(conn, SQLDialect.POSTGRES);
            var seeds = DSL.field("m.ontology_id", String.class).eq(shape).and(DSL.condition("m.subset @> ARRAY['selected']::text[]"));
            var closure = sql.select(DSL.field("iri", String.class)).from(treeReachability(seeds, false)).fetch(0, String.class);
            if (shape.equals("nullBoth")) assertThat(closure).containsExactlyInAnyOrder("M", "N");
            else assertThat(closure).containsExactlyInAnyOrder("M", "N", "B", "R");
            var counts = sql.select(DSL.field("iri", String.class), DSL.count())
                    .from(treeReachability(seeds, true)).where("iri <> member_iri").groupBy(DSL.field("iri"))
                    .fetchMap(DSL.field("iri", String.class), DSL.count());
            assertThat(counts).containsExactlyInAnyOrderEntriesOf(shape.equals("nullBoth") ? Map.of() : Map.of("B",2,"R",2));
            // Exercise the same complete closure through the subset predicate used by tree endpoints.
            var nodes = sql.select(DSL.field("iri", String.class)).from("ols_entities")
                    .where(DSL.field("ontology_id").eq(shape)).and(inSubsetTree(null, List.of("selected"))).fetch(0, String.class);
            assertThat(nodes).containsExactlyInAnyOrderElementsOf(closure);
        }
    }
}
