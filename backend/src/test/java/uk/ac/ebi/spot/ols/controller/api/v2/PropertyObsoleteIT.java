package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Explicit hierarchy-column fixture, independent of RDF/linker/loader correctness. */
@Testcontainers
class PropertyObsoleteIT {
    private static final String NS = "https://example.org/propertyobsolete#";
    @Container static final PostgreSQLContainer<?> POSTGRES = PostgresIntegrationTestSupport.newContainer();
    private static PostgresIntegrationTestSupport.PropertyRepositoryHandle handle;
    private static MockMvc mvc;
    private static PostgresIntegrationTestSupport.SearchClientHandle search;

    @BeforeAll
    static void setUp() throws Exception {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        try (var in = PropertyObsoleteIT.class.getResourceAsStream("/property-obsolete/fixture.json");
             var conn = POSTGRES.createConnection("")) {
            var dsl = DSL.using(conn, SQLDialect.POSTGRES);
            for (JsonElement value : JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray()) {
                var e = value.getAsJsonObject();
                var bytes = new ByteArrayOutputStream();
                try (var zip = new GZIPOutputStream(bytes)) { zip.write(e.toString().getBytes(StandardCharsets.UTF_8)); }
                dsl.execute("""
                    INSERT INTO ols_entities (id, ontology_id, iri, type, search_type, _json,
                      is_obsolete, is_defining_ontology, subset, direct_parents, direct_ancestors,
                      hierarchical_parents, hierarchical_ancestors, has_direct_parents, label)
                    VALUES (?, 'propertyobsolete', ?, 'OntologyProperty', 'property', ?, ?, ?,
                      ?::text[], ?::text[], ?::text[], ?::text[], ?::text[], ?, ?::text[])
                    """, "propertyobsolete+property+" + e.get("iri").getAsString(), e.get("iri").getAsString(),
                        bytes.toByteArray(), e.get("isObsolete").getAsBoolean(), e.get("isDefiningOntology").getAsBoolean(),
                        strings(e.get("http://www.geneontology.org/formats/oboInOwl#inSubset")), strings(e.get("directParent")),
                        strings(e.get("directAncestor")), strings(e.get("hierarchicalParent")), strings(e.get("hierarchicalAncestor")),
                        e.get("hasDirectParents").getAsBoolean(), strings(e.get("label")));
            }
        }
        handle = PostgresIntegrationTestSupport.createPropertyRepository(POSTGRES);
        search = PostgresIntegrationTestSupport.createSearchClient(POSTGRES);
        var controller = new V2PropertyController();
        controller.propertyRepository = handle.repository();
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
    }

    private static String[] strings(JsonElement value) {
        var result = new java.util.ArrayList<String>();
        value.getAsJsonArray().forEach(v -> result.add(v.getAsString()));
        return result.toArray(String[]::new);
    }

    @AfterAll static void close() { if (handle != null) handle.close(); if (search != null) search.close(); }

    private static com.google.gson.JsonObject request(String node, String route, String... params) throws Exception {
        String iri = java.net.URLEncoder.encode(java.net.URLEncoder.encode(NS + node, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        var request = get(URI.create("/api/v2/ontologies/propertyobsolete/properties/" + iri + "/" + route));
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return JsonParser.parseString(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString()).getAsJsonObject();
    }

    private static List<String> nodes(com.google.gson.JsonObject response) {
        var result = new java.util.ArrayList<String>();
        response.getAsJsonArray("elements").forEach(e -> result.add(e.getAsJsonObject().get("shortForm").getAsString()));
        return result;
    }

    @Test void propertySearchBindsAndFiltersBothCountAndPageLiterally() throws Exception {
        var missing = request("R", "children", "searchQuery", "no-matching-property", "treeContext", "true", "lang", "fr");
        assertThat(nodes(missing)).isEmpty();
        assertThat(missing.get("totalElements").getAsLong()).isZero();
        for (String literal : List.of("%", "_")) {
            assertThat(request("R", "children", "searchQuery", literal).get("totalElements").getAsLong()).isZero();
        }
        assertThat(request("R", "children", "searchQuery", "   ").get("totalElements").getAsLong()).isEqualTo(4);
        var match = request("R", "children", "searchQuery", "ACTIVE", "includeObsoleteEntities", "false",
                "includeImportedEntities", "false", "treeContext", "true", "subsetTree", NS+"S,"+NS+"S2", "lang", "fr", "size", "1");
        assertThat(nodes(match)).containsExactly("A");
        assertThat(match.get("totalElements").getAsLong()).isEqualTo(1);
        var past = request("R", "children", "searchQuery", "ACTIVE", "includeImportedEntities", "false", "size", "1", "page", "1");
        assertThat(nodes(past)).isEmpty();
        assertThat(past.get("totalElements").getAsLong()).isEqualTo(1);
    }

    @Test void explicitFalseFiltersOrdinaryChildrenButPreservesTotals() throws Exception {
        var page = request("R", "children", "includeObsoleteEntities", "false", "includeImportedEntities", "false", "lang", "fr");
        assertThat(nodes(page)).containsExactly("A");
        assertThat(page.get("totalElements").getAsLong()).isEqualTo(1);
    }

    @Test void contextKeepsRequiredObsoleteImportedConnectorButDropsObsoleteLeaf() throws Exception {
        var page = request("R", "children", "includeObsoleteEntities", "false", "includeImportedEntities", "false", "treeContext", "true");
        assertThat(nodes(page)).containsExactlyInAnyOrder("A", "K");
        assertThat(page.get("totalElements").getAsLong()).isEqualTo(2);
        assertThat(nodes(request("K", "children", "includeObsoleteEntities", "false", "includeImportedEntities", "false", "treeContext", "true")))
                .containsExactly("C");
    }

    @Test void omittedAndTruePreserveHistoricalObsoleteInclusion() throws Exception {
        assertThat(nodes(request("R", "children"))).containsExactlyInAnyOrder("A", "K", "O", "X");
        assertThat(nodes(request("R", "children", "includeObsoleteEntities", "true"))).containsExactlyInAnyOrder("A", "K", "O", "X");
        assertThat(nodes(request("C", "ancestors"))).containsExactlyInAnyOrder("R", "K");
        assertThat(nodes(request("C", "ancestors", "includeObsoleteEntities", "true"))).containsExactlyInAnyOrder("R", "K");
    }

    @Test void ancestorsBindExplicitFalseAndImportsIndependently() throws Exception {
        assertThat(nodes(request("C", "ancestors", "includeObsoleteEntities", "false", "includeImportedEntities", "true", "lang", "de")))
                .containsExactly("R");
        assertThat(nodes(request("C", "ancestors", "includeObsoleteEntities", "true", "includeImportedEntities", "false")))
                .containsExactly("R");
    }

    @Test void subsetUnionCannotBorrowActiveMemberOutsideSelectedSubset() throws Exception {
        assertThat(nodes(request("R", "children", "includeObsoleteEntities", "false", "includeImportedEntities", "false", "treeContext", "true", "subsetTree", NS + "S2"))).isEmpty();
        assertThat(nodes(request("R", "children", "includeObsoleteEntities", "false", "includeImportedEntities", "false", "treeContext", "true", "subsetTree", NS + "S," + NS + "S2")))
                .containsExactlyInAnyOrder("A", "K");
        assertThat(nodes(request("R", "children", "includeObsoleteEntities", "true", "includeImportedEntities", "false", "treeContext", "true", "subsetTree", NS + "S2")))
                .containsExactly("O");
    }

    @Test void repositoryOverloadsPreserveLegacyInclusionAndExecuteExplicitVisibility() {
        var repository = handle.repository();
        var pageable = org.springframework.data.domain.PageRequest.of(0, 100);
        var options = new uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions();
        assertThat(repository.getChildrenByOntologyId("propertyobsolete", pageable, NS + "R", true, null, "en", options))
                .extracting(e -> e.getAsJsonObject().get("shortForm").getAsString()).containsExactlyInAnyOrder("A", "K", "O", "X");
        assertThat(repository.getChildrenByOntologyId("propertyobsolete", pageable, NS + "R", true, null, true, "en", options))
                .extracting(e -> e.getAsJsonObject().get("shortForm").getAsString()).containsExactlyInAnyOrder("A", "K", "O", "X");
        assertThat(repository.getChildrenByOntologyId("propertyobsolete", pageable, NS + "R", false, false, null, true, "en", options))
                .extracting(e -> e.getAsJsonObject().get("shortForm").getAsString()).containsExactlyInAnyOrder("A", "K");
        assertThat(repository.getAncestorsByOntologyId("propertyobsolete", pageable, NS + "C", true, "en", options))
                .extracting(e -> e.getAsJsonObject().get("shortForm").getAsString()).containsExactlyInAnyOrder("R", "K");
        assertThat(repository.getAncestorsByOntologyId("propertyobsolete", pageable, NS + "C", false, true, "en", options))
                .extracting(e -> e.getAsJsonObject().get("shortForm").getAsString()).containsExactly("R");
    }

    @Test void descendantBadgesCountEligibleMembersAndExcludeRetainedConnectors() {
        for (boolean obsolete : List.of(false, true)) {
            var query = new uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery();
            query.addFilter("ontologyId", List.of("propertyobsolete"), uk.ac.ebi.spot.ols.repository.search.SearchType.WHOLE_FIELD);
            query.addFilter("type", List.of("property"), uk.ac.ebi.spot.ols.repository.search.SearchType.WHOLE_FIELD);
            query.addFilter("isDefiningOntology", List.of("true"), uk.ac.ebi.spot.ols.repository.search.SearchType.WHOLE_FIELD);
            if (!obsolete) query.addFilter("isObsolete", List.of("false"), uk.ac.ebi.spot.ols.repository.search.SearchType.WHOLE_FIELD);
            query.addFacetField("treeAncestor");
            assertThat(search.searchClient().searchRaw(query, 0, 1, false).facets.get("treeAncestor"))
                    .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(NS + "R", obsolete ? 3L : 2L, NS + "K", 1L));
        }
    }

    @Test void paginationUsesSameVisibilityPredicateForRowsAndTotals() throws Exception {
        var page = request("R", "children", "includeObsoleteEntities", "false", "treeContext", "true", "page", "1", "size", "1");
        assertThat(nodes(page)).containsExactly("K");
        assertThat(page.get("totalElements").getAsLong()).isEqualTo(3);
    }
}
