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

/** Production SQL against an explicit ECTO-shaped fixture; not an RDF/Rust loader test. */
@Testcontainers
class NativeTreeIT {
    private static final String NS = "https://example.org/native#";
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = PostgresIntegrationTestSupport.newContainer();
    private static PostgresIntegrationTestSupport.SearchClientHandle search;
    private static PostgresIntegrationTestSupport.OlsPostgresClientRepositoryHandle browse;

    @BeforeAll
    static void loadFixture() throws Exception {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        try (var in = NativeTreeIT.class.getResourceAsStream("/native-tree/fixture.json");
             Connection conn = POSTGRES.createConnection("")) {
            var ontology = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("ontologies").get(0).getAsJsonObject();
            var dsl = DSL.using(conn, SQLDialect.POSTGRES);
            for (String kind : List.of("classes", "individuals", "properties")) {
                for (JsonElement value : ontology.getAsJsonArray(kind)) {
                    var entity = value.getAsJsonObject();
                    String iri = entity.get("iri").getAsString();
                    // Explicit native flags model linker output; fixture provenance is documented alongside JSON.
                    boolean nativeMember = entity.get("isDefiningOntology").getAsBoolean();
                    entity.addProperty("isDefiningOntology", nativeMember);
                    var out = new ByteArrayOutputStream();
                    try (var gzip = new GZIPOutputStream(out)) {
                        gzip.write(entity.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    dsl.execute("""
                            INSERT INTO ols_entities (id, ontology_id, iri, type, search_type, _json,
                              is_obsolete, is_defining_ontology, is_preferred_root, subset,
                              direct_parents, hierarchical_parents, direct_ancestors, hierarchical_ancestors)
                            VALUES (?, 'native', ?, ?, ?, ?, ?, ?, ?, ?::text[], ?::text[], ?::text[], ?::text[], ?::text[])
                            """, "native+" + (kind.equals("classes") ? "class" : kind.equals("properties") ? "property" : "individual") + "+" + iri, iri,
                            kind.equals("classes") ? "OntologyClass" : kind.equals("properties") ? "OntologyProperty" : "OntologyIndividual",
                            kind.equals("classes") ? "class" : kind.equals("properties") ? "property" : "individual", out.toByteArray(),
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
        query.addFilter("ontologyId", List.of("native"), SearchType.WHOLE_FIELD);
        if (!imported) query.addFilter("isDefiningOntology", List.of("true"), SearchType.WHOLE_FIELD);
        if (!obsolete) query.addFilter("isObsolete", List.of("false"), SearchType.WHOLE_FIELD);
        query.setSubsetTree(subsets);
        return query;
    }

    private static List<String> iris(OlsSearchQuery query) {
        return search.searchClient().searchRaw(query, 0, 100, false).jsonStrings.stream()
                .map(s -> JsonParser.parseString(s).getAsJsonObject().get("iri").getAsString()).toList();
    }

    private static OlsSearchQuery nativeQuery(List<String> subsets) {
        var q = query(false, false, subsets);
        uk.ac.ebi.spot.ols.repository.helpers.DynamicFilterParser.addDynamicFiltersToQuery(q,
                Map.of("treeContext", List.of("true")));
        return q;
    }

    @Test
    void nativeRootsRetainOnlyNecessaryImportedConnectorsWithoutChangingDefaultLists() {
        var q = nativeQuery(List.of());
        q.addFilter("isPreferredRoot", List.of("true"), SearchType.WHOLE_FIELD);
        assertThat(iris(q)).containsExactly(NS + "R");
        var first = search.searchClient().searchPaginated(q, PageRequest.of(0, 1));
        assertThat(first.getContent()).extracting(v -> v.getAsJsonObject().get("iri").getAsString())
                .containsExactly(NS + "R");
        assertThat(first.getTotalElements()).isEqualTo(1);
        var beyondEnd = search.searchClient().searchPaginated(q, PageRequest.of(1, 1));
        assertThat(beyondEnd.getContent()).isEmpty();
        assertThat(beyondEnd.getTotalElements()).isEqualTo(1);
        var ordinary = query(false, false, List.of());
        ordinary.addFilter("isPreferredRoot", List.of("true"), SearchType.WHOLE_FIELD);
        assertThat(iris(ordinary)).isEmpty();
    }

    @Test
    void nativeSubsetClosureCannotBorrowANativeMemberOutsideTheSelectedSubset() {
        assertThat(iris(nativeQuery(List.of(NS + "S")))).containsExactlyInAnyOrder(NS + "R", NS + "K", NS + "C");
        assertThat(iris(nativeQuery(List.of(NS + "S2")))).containsExactlyInAnyOrder(NS + "R", NS + "K", NS + "I");
        assertThat(iris(nativeQuery(List.of(NS + "S", NS + "S2")))).containsExactlyInAnyOrder(NS + "R", NS + "K", NS + "C", NS + "I");
    }

    @Test
    void nativeChildrenKeepImportedConnectorsForExpansionAndStaySubsetScoped() {
        var props = Map.of("type", "OntologyClass", "isObsolete", "false", "isDefiningOntology", "true", "treeContext", "true");
        var client = browse.olsPostgresClient();
        assertThat(client.getDirectChildren("native+class+" + NS + "R", props, PageRequest.of(0, 100), null, List.of()))
                .extracting(v -> v.getAsJsonObject().get("iri").getAsString()).containsExactlyInAnyOrder(NS + "K", NS + "D");
        assertThat(client.getHierarchicalChildrenByIri("native", NS + "R", props, PageRequest.of(0, 100), List.of(NS + "S")))
                .extracting(v -> v.getAsJsonObject().get("iri").getAsString()).containsExactly(NS + "K");
        assertThat(client.getDirectChildren("native+class+" + NS + "K", props, PageRequest.of(0, 100), null, List.of(NS + "S")))
                .extracting(v -> v.getAsJsonObject().get("iri").getAsString()).containsExactly(NS + "C");
    }

    @Test
    void nativePropertiesRetainTheirImportedRootAndDefaultFalseIsAnOrdinaryList() {
        var q = nativeQuery(List.of());
        q.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        assertThat(iris(q)).contains(NS + "P", NS + "Q");
        var ordinary = query(false, false, List.of());
        uk.ac.ebi.spot.ols.repository.helpers.DynamicFilterParser.addDynamicFiltersToQuery(ordinary, Map.of("treeContext", List.of("false")));
        ordinary.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        assertThat(iris(ordinary)).contains(NS + "Q").doesNotContain(NS + "P");
    }

    @Test
    void propertyRepositoryForwardsNativeContextWithoutMutatingImmutableDefaults() {
        var repository = new uk.ac.ebi.spot.ols.repository.PropertyRepository();
        org.springframework.test.util.ReflectionTestUtils.setField(repository, "postgresClient", browse.olsPostgresClient());
        var result = repository.getChildrenByOntologyId("native", PageRequest.of(0, 100), NS + "P", false,
                List.of(), true, "en", new uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions());
        assertThat(result).extracting(v -> v.getAsJsonObject().get("iri").getAsString()).containsExactly(NS + "Q");
        assertThat(repository.getChildrenByOntologyId("native", PageRequest.of(0, 100),
                "http://www.geneontology.org/formats/oboInOwl#SubsetProperty", true, List.of(), false,
                "en", new uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions()))
                .extracting(v -> v.getAsJsonObject().get("iri").getAsString()).containsExactlyInAnyOrder(NS + "S", NS + "S2");
    }

    @Test
    void importedOnStillRetainsObsoleteConnectorsAboveActiveMembers() {
        var q = query(true, false, List.of(NS + "S"));
        q.setTreeContext(true);
        assertThat(iris(q)).containsExactlyInAnyOrder(NS + "R", NS + "K", NS + "C", NS + "D", NS + "X");
    }

    @Test
    void countsRemainNativeMembersAndDoNotCountRetainedImportedConnectors() {
        var q = query(false, false, List.of());
        q.addFilter("type", List.of("class", "individual"), SearchType.WHOLE_FIELD);
        q.addFacetField("treeAncestor");
        assertThat(search.searchClient().searchRaw(q, 0, 1, false).facets.get("treeAncestor"))
                .containsExactlyInAnyOrderEntriesOf(Map.of(NS + "R", 3L, NS + "K", 2L, NS + "D", 1L));
    }
}
