package uk.ac.ebi.spot.ols.repository.search;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.field;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.inSubsetTree;

/** Production search/child SQL against generated RDF ancestry; native flags model linker output. */
@Testcontainers
class EligibleSubsetMembersIT {
    private static final String NS = "https://example.org/eligible#";
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = PostgresIntegrationTestSupport.newContainer();
    private static PostgresIntegrationTestSupport.SearchClientHandle search;
    private static PostgresIntegrationTestSupport.OlsPostgresClientRepositoryHandle browse;

    @BeforeAll
    static void loadFixture() throws Exception {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        try (var in = EligibleSubsetMembersIT.class.getResourceAsStream("/eligible-subsets/generated.json");
             Connection conn = POSTGRES.createConnection("")) {
            var ontology = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("ontologies").get(0).getAsJsonObject();
            var dsl = DSL.using(conn, SQLDialect.POSTGRES);
            for (String kind : List.of("classes", "individuals")) {
                for (JsonElement value : ontology.getAsJsonArray(kind)) {
                    var entity = value.getAsJsonObject();
                    String iri = entity.get("iri").getAsString();
                    // RDF2JSON precedes the linker. This explicit fixture policy models its native flag.
                    boolean nativeMember = iri.startsWith(NS);
                    entity.addProperty("isDefiningOntology", nativeMember);
                    var out = new ByteArrayOutputStream();
                    try (var gzip = new GZIPOutputStream(out)) {
                        gzip.write(entity.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    dsl.execute("""
                            INSERT INTO ols_entities (id, ontology_id, iri, type, search_type, _json,
                              is_obsolete, is_defining_ontology, is_preferred_root, subset,
                              direct_parents, hierarchical_parents, direct_ancestors, hierarchical_ancestors)
                            VALUES (?, 'eligible', ?, ?, ?, ?, ?, ?, ?, ?::text[], ?::text[], ?::text[], ?::text[], ?::text[])
                            """, "eligible+" + (kind.equals("classes") ? "class" : "individual") + "+" + iri, iri,
                            kind.equals("classes") ? "OntologyClass" : "OntologyIndividual",
                            kind.equals("classes") ? "class" : "individual", out.toByteArray(),
                            entity.has("isObsolete") && entity.get("isObsolete").getAsBoolean(), nativeMember,
                            entity.has("isPreferredRoot") && entity.get("isPreferredRoot").getAsBoolean(),
                            strings(entity.get("http://www.geneontology.org/formats/oboInOwl#inSubset")),
                            strings(entity.get("directParent")), strings(entity.get("hierarchicalParent")),
                            strings(entity.get("directAncestor")), strings(entity.get("hierarchicalAncestor")));
                }
            }
        }
        search = PostgresIntegrationTestSupport.createSearchClient(POSTGRES);
        browse = PostgresIntegrationTestSupport.createOlsPostgresClientRepositories(POSTGRES);
    }

    private static String[] strings(JsonElement value) {
        if (value == null || value.isJsonNull()) return new String[0];
        if (value.isJsonObject()) return strings(value.getAsJsonObject().get("value"));
        if (!value.isJsonArray()) return new String[]{value.getAsString()};
        var result = new ArrayList<String>();
        value.getAsJsonArray().forEach(v -> result.addAll(List.of(strings(v))));
        return result.toArray(String[]::new);
    }

    @AfterAll
    static void closeClients() {
        if (search != null) search.close();
        if (browse != null) browse.close();
    }

    private static OlsSearchQuery query(boolean imported, boolean obsolete, List<String> subsets) {
        var query = new OlsSearchQuery();
        query.addFilter("ontologyId", List.of("eligible"), SearchType.WHOLE_FIELD);
        if (!imported) query.addFilter("isDefiningOntology", List.of("true"), SearchType.WHOLE_FIELD);
        if (!obsolete) query.addFilter("isObsolete", List.of("false"), SearchType.WHOLE_FIELD);
        query.setSubsetTree(subsets);
        return query;
    }

    private static List<String> iris(OlsSearchQuery query) {
        return search.searchClient().searchRaw(query, 0, 100, false).jsonStrings.stream()
                .map(s -> JsonParser.parseString(s).getAsJsonObject().get("iri").getAsString()).toList();
    }

    @Test
    void preferredRootsAgreeExactlyWithEligibleMemberCountsAcrossVisibilityAndSubsetUnions() {
        for (var subsets : List.of(List.of(NS + "S"), List.of(NS + "S", NS + "S2"))) {
            for (boolean imported : List.of(false, true)) {
                for (boolean obsolete : List.of(false, true)) {
                    var roots = query(imported, obsolete, subsets);
                    roots.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
                    roots.addFilter("isPreferredRoot", List.of("true"), SearchType.WHOLE_FIELD);
                    assertThat(iris(roots)).as("imports=%s obsolete=%s subsets=%s", imported, obsolete, subsets)
                            .containsExactlyInAnyOrderElementsOf(imported || obsolete ? List.of(NS + "A", NS + "B") : List.of(NS + "B"));
                    var counts = query(imported, obsolete, subsets);
                    counts.addFilter("subset", subsets, SearchType.WHOLE_FIELD);
                    counts.addFacetField("treeAncestor");
                    var expected = new HashMap<String, Long>();
                    expected.put(NS + "B", 1L);
                    expected.put(NS + "Top", 1L + (imported ? 1 : 0) + (obsolete ? 1 : 0));
                    if (imported || obsolete) expected.put(NS + "A", (imported ? 1L : 0L) + (obsolete ? 1L : 0L));
                    assertThat(search.searchClient().searchRaw(counts, 0, 1, false).facets.get("treeAncestor"))
                            .containsExactlyInAnyOrderEntriesOf(expected);
                }
            }
        }
    }

    @Test
    void childBranchesUseVisibilityWithoutRestrictingSeedsToCandidateClassType() {
        for (boolean imported : List.of(false, true)) {
            for (boolean obsolete : List.of(false, true)) {
                var props = new HashMap<String, String>();
                props.put("type", "OntologyClass");
                if (!imported) props.put("isDefiningOntology", "true");
                if (!obsolete) props.put("isObsolete", "false");
                var expected = imported || obsolete ? List.of(NS + "A", NS + "B") : List.of(NS + "B");
                var client = browse.olsPostgresClient();
                assertThat(client.getDirectChildren("eligible+class+" + NS + "Top", props, PageRequest.of(0, 100), null, List.of(NS + "S")))
                        .extracting(v -> v.getAsJsonObject().get("iri").getAsString()).containsExactlyInAnyOrderElementsOf(expected);
                assertThat(client.getHierarchicalChildrenByIri("eligible", NS + "Top", props, PageRequest.of(0, 100), List.of(NS + "S")))
                        .extracting(v -> v.getAsJsonObject().get("iri").getAsString()).containsExactlyInAnyOrderElementsOf(expected);
            }
        }
    }

    @Test
    void subsetUnionDeduplicatesActiveIndividualAndDoesNotRetainHiddenOnlyRoot() {
        var roots = query(false, false, List.of(NS + "S2"));
        roots.addFilter("isPreferredRoot", List.of("true"), SearchType.WHOLE_FIELD);
        roots.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        assertThat(iris(roots)).containsExactly(NS + "B");
    }

    @Test
    void importedOnlyFilterKeepsItsActualPredicateAndDoesNotBecomeIncludeAllImports() {
        var query = query(true, false, List.of(NS + "S"));
        query.addFilter("isDefiningOntology", List.of("false"), SearchType.WHOLE_FIELD);
        assertThat(iris(query)).containsExactly("https://external.example.org/Imported");
    }

    @Test
    void importedOnlySeedEligibilityDoesNotKeepTheUnrelatedNativeBranch() throws Exception {
        try (Connection conn = POSTGRES.createConnection("")) {
            var dsl = DSL.using(conn, SQLDialect.POSTGRES);
            var eligibleImports = field("m", "ontology_id", String.class).eq("eligible")
                    .and(field("m", "is_defining_ontology", Boolean.class).isFalse());
            assertThat(dsl.select(field("iri", String.class)).from("ols_entities")
                    .where(inSubsetTree(null, List.of(NS + "S"), eligibleImports)).fetch(0, String.class))
                    .containsExactlyInAnyOrder(NS + "A", NS + "Top", "https://external.example.org/Imported");
        }
    }

    @Test
    void hiddenConnectorStaysInsideClosureWhenNeededByAnEligibleMember() throws Exception {
        try (Connection conn = POSTGRES.createConnection("")) {
            conn.setAutoCommit(false);
            var dsl = DSL.using(conn, SQLDialect.POSTGRES);
            // Test-only rows roll back; the imported connector is not itself an eligible seed.
            dsl.execute("""
                    INSERT INTO ols_entities (id, ontology_id, iri, type, search_type, _json,
                      is_obsolete, is_defining_ontology, subset, direct_ancestors, hierarchical_ancestors)
                    SELECT 'eligible-connector', 'eligible', ?, 'OntologyClass', 'class', _json,
                      false, false, '{}'::text[], ARRAY[?, ?]::text[], ARRAY[?, ?]::text[]
                    FROM ols_entities WHERE id = ?
                    """, NS + "Connector", NS + "B", NS + "Top", NS + "B", NS + "Top", "eligible+individual+" + NS + "Active");
            dsl.execute("""
                    INSERT INTO ols_entities (id, ontology_id, iri, type, search_type, _json,
                      is_obsolete, is_defining_ontology, subset, direct_ancestors, hierarchical_ancestors)
                    SELECT 'eligible-leaf', 'eligible', ?, 'OntologyClass', 'class', _json,
                      false, true, ARRAY[?]::text[], ARRAY[?, ?, ?]::text[], ARRAY[?, ?, ?]::text[]
                    FROM ols_entities WHERE id = ?
                    """, NS + "Leaf", NS + "S3", NS + "Connector", NS + "B", NS + "Top",
                    NS + "Connector", NS + "B", NS + "Top", "eligible+individual+" + NS + "Active");
            var nativeActive = field("m", "ontology_id", String.class).eq("eligible")
                    .and(field("m", "is_defining_ontology", Boolean.class).isTrue())
                    .and(field("m", "is_obsolete", Boolean.class).isFalse());
            assertThat(dsl.select(field("iri", String.class)).from("ols_entities")
                    .where(inSubsetTree(null, List.of(NS + "S3"), nativeActive)).fetch(0, String.class))
                    .containsExactlyInAnyOrder(NS + "Leaf", NS + "Connector", NS + "B", NS + "Top");
            conn.rollback();
        }
    }

}
