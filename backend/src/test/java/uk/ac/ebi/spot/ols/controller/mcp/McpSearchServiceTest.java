package uk.ac.ebi.spot.ols.controller.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.ols.repository.EntityRepository;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for {@link McpSearchService}'s own wiring decisions: {@code search}'s three-way
 * {@code includeObsoleteEntities} filter construction, the exact fixed-argument delegation to
 * {@link EntityRepository#find} (hardcoded {@code PageRequest.of(0, 20)}, hardcoded {@code "en"}
 * lang, hardcoded {@code null} {@code searchFields}/{@code boostFields}/{@code facetFields}/
 * {@code excludeOntologyIds}, hardcoded {@code exactMatch=false} — a literal {@code boolean}, not
 * {@code null} — and the always-on {@code JsonTransformOptions}), {@code fetch}'s
 * {@code id.split("\\+")} parsing (success only on exactly two tokens) and its fixed-argument
 * delegation to {@link EntityRepository#getByOntologyIdAndIri}, and the raw-JSON-string result
 * shape both {@code @Tool} methods return (unlike every other MCP service tested so far in this
 * programme, which return {@code McpPage<T>}/{@code List<T>} directly) — parsed back with
 * {@link Gson} here and asserted on the real structure, not just non-null/non-empty.
 *
 * <p>Two of this class's three {@code @Autowired} fields, {@code embeddingServiceClient} and
 * {@code postgresClient}, are never read or called anywhere in {@code McpSearchService} — only
 * {@code entityRepository} is used, by both {@code @Tool} methods. This is dead/unnecessary Spring
 * wiring, not a defect (it causes no incorrect behaviour), so this suite deliberately leaves both
 * fields {@code null} in every test: every case below still passes without a
 * {@code NullPointerException}, which is itself observable proof neither field is ever touched.
 *
 * <p>Uses this repo's hand-rolled-fake idiom (a subclass overriding just the methods under test, no
 * Mockito), the same idiom as {@code McpClassServiceTest}/{@code McpOntologyServiceTest}.</p>
 */
class McpSearchServiceTest {

    private RecordingEntityRepository entityRepository;
    private McpSearchService service;

    @BeforeEach
    void setUp() {
        entityRepository = new RecordingEntityRepository();
        service = new McpSearchService();
        service.entityRepository = entityRepository;
        // service.embeddingServiceClient and service.postgresClient deliberately left null - see
        // class Javadoc above.
    }

    // ------------------------------------------------------------------
    // search - includeObsoleteEntities three-way resolution
    // ------------------------------------------------------------------

    @Test
    void searchTreatsNullAndExplicitFalseIncludeObsoleteTheSame() throws IOException {
        service.search("liver", null);
        assertEquals(Map.of("isObsolete", List.of("false")), entityRepository.lastProperties);

        service.search("liver", false);
        assertEquals(Map.of("isObsolete", List.of("false")), entityRepository.lastProperties);
    }

    @Test
    void searchOmitsObsoleteFilterOnlyWhenExplicitlyIncluded() throws IOException {
        service.search("liver", true);

        assertTrue(entityRepository.lastProperties.isEmpty());
    }

    // ------------------------------------------------------------------
    // search - exact fixed-argument delegation
    // ------------------------------------------------------------------

    @Test
    void searchDelegatesWithExactFixedArguments() throws IOException {
        service.search("liver", null);

        assertEquals(0, entityRepository.lastPageable.getPageNumber());
        assertEquals(20, entityRepository.lastPageable.getPageSize());
        assertEquals("en", entityRepository.lastLang);
        assertEquals("liver", entityRepository.lastSearch);
        assertNull(entityRepository.lastSearchFields);
        assertNull(entityRepository.lastBoostFields);
        assertNull(entityRepository.lastFacetFields);
        assertFalse(entityRepository.lastExactMatch);
        assertNull(entityRepository.lastExcludeOntologyIds);
        assertTrue(entityRepository.lastOutputOpts.resolveReferences);
        assertTrue(entityRepository.lastOutputOpts.manchesterSyntax);
    }

    @Test
    void searchAlwaysUsesFixedPageZeroWithPageSizeTwenty() throws IOException {
        service.search("anything", true);

        assertEquals(PageRequest.of(0, 20), entityRepository.lastPageable);
    }

    @Test
    void searchPassesTheQueryThroughUnchanged() throws IOException {
        service.search("some free text query", null);

        assertEquals("some free text query", entityRepository.lastSearch);
    }

    // ------------------------------------------------------------------
    // search - result mapping and JSON shape
    // ------------------------------------------------------------------

    @Test
    void searchMapsEachResultThroughMcpSearchResultAndSerializesAsAJsonArrayInOrder() throws IOException {
        entityRepository.pageResult = new OlsFacetedResultsPage<>(
                new ArrayList<>(List.of(
                        entityJson("efo", "http://example.org/EFO_0001", "EFO:0001", "Liver disease", false),
                        entityJson("duo", "http://example.org/DUO_0001", "DUO:0001", "Data use permission", false))),
                Map.of(), PageRequest.of(0, 20), 2);

        String json = service.search("liver", null);

        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        assertEquals(2, array.size());

        JsonObject first = array.get(0).getAsJsonObject();
        assertEquals("efo+http://example.org/EFO_0001", first.get("id").getAsString());
        assertEquals("http://example.org/EFO_0001", first.get("url").getAsString());
        assertEquals("EFO:0001 Liver disease", first.get("title").getAsString());
        assertFalse(first.get("isObsolete").getAsBoolean());

        JsonObject second = array.get(1).getAsJsonObject();
        assertEquals("duo+http://example.org/DUO_0001", second.get("id").getAsString());
        assertEquals("http://example.org/DUO_0001", second.get("url").getAsString());
        assertEquals("DUO:0001 Data use permission", second.get("title").getAsString());
    }

    @Test
    void searchMapsIsObsoleteTrueThroughUnchanged() throws IOException {
        entityRepository.pageResult = new OlsFacetedResultsPage<>(
                new ArrayList<>(List.of(
                        entityJson("efo", "http://example.org/EFO_0999", "EFO:0999", "Legacy concept", true))),
                Map.of(), PageRequest.of(0, 20), 1);

        String json = service.search("legacy", true);

        JsonObject result = JsonParser.parseString(json).getAsJsonArray().get(0).getAsJsonObject();
        assertTrue(result.get("isObsolete").getAsBoolean());
    }

    @Test
    void searchReturnsAnEmptyJsonArrayWhenTheRepositoryFindsNoResults() throws IOException {
        entityRepository.pageResult = new OlsFacetedResultsPage<>(
                new ArrayList<>(), Map.of(), PageRequest.of(0, 20), 0);

        String json = service.search("nothing matches this", null);

        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        assertEquals(0, array.size());
    }

    @Test
    void searchPropagatesAnIOExceptionFromTheRepositoryUnchanged() {
        entityRepository.exceptionToThrow = new IOException("search backend unavailable");

        IOException thrown = assertThrows(IOException.class, () -> service.search("liver", null));

        assertEquals("search backend unavailable", thrown.getMessage());
    }

    // ------------------------------------------------------------------
    // fetch - id.split("\\+") parsing
    // ------------------------------------------------------------------

    @Test
    void fetchSucceedsWithExactlyTwoTokens() throws IOException {
        entityRepository.fetchResult = entityJson(
                "go", "http://purl.obolibrary.org/obo/GO_0008150", "GO:0008150", "biological_process", false);

        String json = service.fetch("go+http://purl.obolibrary.org/obo/GO_0008150");

        assertEquals("go", entityRepository.lastOntologyId);
        assertEquals("http://purl.obolibrary.org/obo/GO_0008150", entityRepository.lastIri);
        JsonObject result = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("go+http://purl.obolibrary.org/obo/GO_0008150", result.get("id").getAsString());
    }

    @Test
    void fetchThrowsIllegalArgumentExceptionWhenNoPlusSignIsPresent() {
        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> service.fetch("goGO_0008150"));

        assertEquals(
                "ID must be of the format ontologyid+entityIri, e.g. go+http://purl.obolibrary.org/obo/GO_0008150",
                thrown.getMessage());
    }

    @Test
    void fetchThrowsIllegalArgumentExceptionForAnEmptyStringInput() {
        // "".split("\\+") yields a single-element array containing the empty string (length 1,
        // not 0), so this hits the same "tokens.length != 2" branch as a plain no-"+" id.
        assertThrows(IllegalArgumentException.class, () -> service.fetch(""));
    }

    @Test
    void fetchThrowsIllegalArgumentExceptionWhenMoreThanOneLiteralPlusIsPresent() {
        // Confirmed empirically that no committed test fixture or golden output IRI
        // (backend/src/test/resources/fixtures, testcases_expected_output_api/mcp/*.json) contains
        // a literal '+' character, so this branch is not known to be reachable with any real OBO
        // Library purl IRI in this codebase today - but the split-based parser does not guard
        // against it, so it is tested directly here.
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class, () -> service.fetch("go+http://example.org/GO_1+2"));

        assertEquals(
                "ID must be of the format ontologyid+entityIri, e.g. go+http://purl.obolibrary.org/obo/GO_0008150",
                thrown.getMessage());
    }

    // ------------------------------------------------------------------
    // fetch - exact fixed-argument delegation
    // ------------------------------------------------------------------

    @Test
    void fetchDelegatesWithExactFixedArguments() throws IOException {
        entityRepository.fetchResult = entityJson(
                "efo", "http://example.org/EFO_0001", "EFO:0001", "Liver disease", false);

        service.fetch("efo+http://example.org/EFO_0001");

        assertEquals("efo", entityRepository.lastOntologyId);
        assertEquals("http://example.org/EFO_0001", entityRepository.lastIri);
        assertEquals("en", entityRepository.lastFetchLang);
        assertTrue(entityRepository.lastFetchOutputOpts.resolveReferences);
        assertTrue(entityRepository.lastFetchOutputOpts.manchesterSyntax);
    }

    @Test
    void fetchSplitsExactlyOnTheFirstAndOnlyPlusForATwoTokenId() throws IOException {
        entityRepository.fetchResult = entityJson(
                "go", "http://purl.obolibrary.org/obo/GO_0008150", "GO:0008150", "biological_process", false);

        service.fetch("go+http://purl.obolibrary.org/obo/GO_0008150");

        assertEquals("go", entityRepository.lastOntologyId);
        assertEquals("http://purl.obolibrary.org/obo/GO_0008150", entityRepository.lastIri);
    }

    // ------------------------------------------------------------------
    // fetch - result mapping and JSON shape
    // ------------------------------------------------------------------

    @Test
    void fetchMapsTheResultThroughMcpFetchResultAndSerializesAsAJsonObject() throws IOException {
        entityRepository.fetchResult = entityJsonWithDefinitionAndType(
                "duo", "http://purl.obolibrary.org/obo/DUO_0000001", "DUO:0000001", "data use permission",
                "A data item that is used to indicate consent permissions.", false, "class");

        String json = service.fetch("duo+http://purl.obolibrary.org/obo/DUO_0000001");

        JsonObject result = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("duo+http://purl.obolibrary.org/obo/DUO_0000001", result.get("id").getAsString());
        assertEquals("http://purl.obolibrary.org/obo/DUO_0000001", result.get("url").getAsString());
        assertEquals("DUO:0000001 data use permission", result.get("title").getAsString());
        assertEquals(
                "A data item that is used to indicate consent permissions.", result.get("text").getAsString());
        assertFalse(result.get("isObsolete").getAsBoolean());
    }

    @Test
    void fetchMapsIsObsoleteTrueThroughUnchanged() throws IOException {
        entityRepository.fetchResult = entityJson(
                "efo", "http://example.org/EFO_0999", "EFO:0999", "Legacy concept", true);

        String json = service.fetch("efo+http://example.org/EFO_0999");

        JsonObject result = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(result.get("isObsolete").getAsBoolean());
    }

    @Test
    void fetchMetadataForAClassEntityIsOnlyItsTypeNotTheFullMcpClassStructure() throws IOException {
        // McpFetchResult.fromJson checks `type == "class"` with reference equality against a
        // Gson-parsed string, which never matches a real Gson JsonPrimitive value (confirmed
        // empirically - Gson never returns an interned literal here), so this branch is
        // permanently dead in production: metadata always ends up as the plain
        // Map.of("type", type) fallback, even for a genuine class entity. This is not treated as
        // a defect to fix here: it matches the already-committed golden file
        // testcases_expected_output_api/mcp/fetch.json exactly (a "duo" class entity whose
        // "metadata" is {"type": "class"}, not a full nested McpClass object), so this test only
        // documents McpSearchService.fetch()'s real, current, already-baselined output shape.
        entityRepository.fetchResult = entityJsonWithDefinitionAndType(
                "duo", "http://purl.obolibrary.org/obo/DUO_0000001", "DUO:0000001", "data use permission",
                "A data item that is used to indicate consent permissions.", false, "class");

        String json = service.fetch("duo+http://purl.obolibrary.org/obo/DUO_0000001");

        JsonObject metadata = JsonParser.parseString(json).getAsJsonObject().get("metadata").getAsJsonObject();
        assertEquals(1, metadata.entrySet().size());
        assertEquals("class", metadata.get("type").getAsString());
    }

    @Test
    void fetchThrowsNullPointerExceptionWhenTheRepositoryFindsNoMatchingEntity() {
        // EntityRepository.getByOntologyIdAndIri returns a plain null (not an exception) when
        // OlsSearchClient.getFirst finds no matching row. McpSearchService.fetch() passes that
        // null straight into McpFetchResult.fromJson(JsonElement), which immediately calls
        // entity.getAsJsonObject() with no null check - so a syntactically valid, well-formed id
        // for an entity that genuinely does not exist currently throws an undocumented
        // NullPointerException instead of a clear "not found" error. This is a real, reachable
        // production defect (confirmed against the real EntityRepository/OlsSearchClient source,
        // not a mock artifact, and not asserted otherwise by any committed golden file), tracked
        // separately per this programme's defect workflow rather than fixed in this testing PR.
        entityRepository.fetchResult = null;

        assertThrows(NullPointerException.class, () -> service.fetch("efo+http://example.org/DOES_NOT_EXIST"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static JsonElement entityJson(
            String ontologyId, String iri, String curie, String label, boolean isObsolete) {
        JsonObject json = new JsonObject();
        json.addProperty("ontologyId", ontologyId);
        json.addProperty("iri", iri);
        json.addProperty("curie", curie);
        json.addProperty("label", label);
        json.addProperty("isObsolete", isObsolete);
        // A "type" field is always present on real production entity JSON (see entity-fixture.json
        // below); McpFetchResult.fromJson reads it unconditionally (even for search results, which
        // never look at it) and Map.of("type", null) throws NullPointerException if it is absent -
        // a real quirk, but not one these tests are targeting, so it is always supplied here.
        JsonArray typeArray = new JsonArray();
        typeArray.add("entity");
        typeArray.add("class");
        json.add("type", typeArray);
        return JsonParser.parseString(json.toString());
    }

    private static JsonElement entityJsonWithDefinitionAndType(
            String ontologyId, String iri, String curie, String label, String definition, boolean isObsolete,
            String type) {
        JsonObject json = new JsonObject();
        json.addProperty("ontologyId", ontologyId);
        json.addProperty("iri", iri);
        json.addProperty("curie", curie);
        json.addProperty("label", label);
        json.addProperty("definition", definition);
        json.addProperty("isObsolete", isObsolete);
        JsonArray typeArray = new JsonArray();
        // "entity" sorts after "class" alphabetically, so JsonHelper.objectToString's
        // alphabetical-then-first resolution of a multi-valued "type" picks "class" here - matching
        // real production entity JSON shape (see backend/src/test/resources/fixtures/entities/
        // entity-fixture.json's "type": ["entity", "class"]).
        typeArray.add("entity");
        typeArray.add(type);
        json.add("type", typeArray);
        return JsonParser.parseString(json.toString());
    }

    // ------------------------------------------------------------------
    // Hand-rolled fake (this repo's unit-test idiom; see McpClassServiceTest)
    // ------------------------------------------------------------------

    private static class RecordingEntityRepository extends EntityRepository {
        private OlsFacetedResultsPage<JsonElement> pageResult =
                new OlsFacetedResultsPage<>(new ArrayList<>(), Map.of(), PageRequest.of(0, 20), 0);
        private IOException exceptionToThrow;

        private Pageable lastPageable;
        private String lastLang;
        private String lastSearch;
        private String lastSearchFields;
        private String lastBoostFields;
        private String lastFacetFields;
        private boolean lastExactMatch;
        private Collection<String> lastExcludeOntologyIds;
        private Map<String, Collection<String>> lastProperties;
        private JsonTransformOptions lastOutputOpts;

        private JsonElement fetchResult;
        private String lastOntologyId;
        private String lastIri;
        private String lastFetchLang;
        private JsonTransformOptions lastFetchOutputOpts;

        @Override
        public OlsFacetedResultsPage<JsonElement> find(
                Pageable pageable, String lang, String search, String searchFields, String boostFields,
                String facetFields, boolean exactMatch, Collection<String> excludeOntologyIds,
                Map<String, Collection<String>> properties, JsonTransformOptions outputOpts) throws IOException {
            this.lastPageable = pageable;
            this.lastLang = lang;
            this.lastSearch = search;
            this.lastSearchFields = searchFields;
            this.lastBoostFields = boostFields;
            this.lastFacetFields = facetFields;
            this.lastExactMatch = exactMatch;
            this.lastExcludeOntologyIds = excludeOntologyIds;
            this.lastProperties = properties;
            this.lastOutputOpts = outputOpts;
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return pageResult;
        }

        @Override
        public JsonElement getByOntologyIdAndIri(
                String ontologyId, String iri, String lang, JsonTransformOptions outputOpts) {
            this.lastOntologyId = ontologyId;
            this.lastIri = iri;
            this.lastFetchLang = lang;
            this.lastFetchOutputOpts = outputOpts;
            return fetchResult;
        }
    }
}
