package uk.ac.ebi.spot.ols.controller.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.ols.model.mcp.McpPage;
import uk.ac.ebi.spot.ols.model.mcp.McpSearchResult;
import uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient;
import uk.ac.ebi.spot.ols.service.EmbeddingServiceClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for {@link McpEmbeddingService}'s own wiring decisions: {@code listEmbeddingModels}'s
 * set-combination logic (which is built from the Postgres list only — a model known only to the
 * embedding service is silently dropped, not included with {@code can_embed=false} — and sorted
 * alphabetically by model name), and {@code searchWithEmbeddingModel}'s default paging, {@code
 * float[]}-to-{@code List<Double>} conversion, {@code ontologyId} null/empty/present dispatch,
 * three-way {@code includeCurations} resolution, hardcoded {@code "en"} language (this method takes
 * no {@code lang} parameter at all, unlike {@link McpClassService#searchClassesWithEmbeddingModel}),
 * and the {@code "OntologyEntity"} search-scope literal (McpClassService's equivalent method passes
 * {@code "OntologyClass"} instead — a real, load-bearing difference: see {@code OlsPostgresClient
 * .hasConcreteEntityType}, where {@code "OntologyEntity"} is the one string that disables the
 * type filter entirely, so this service searches every entity type, not just classes).
 *
 * <p>Uses this repo's hand-rolled-fake idiom (subclasses overriding just the methods under test, no
 * Mockito), the same idiom as {@code McpClassServiceTest}.</p>
 */
class McpEmbeddingServiceTest {

    private RecordingEmbeddingServiceClient embeddingServiceClient;
    private RecordingOlsPostgresClient postgresClient;
    private McpEmbeddingService service;

    @BeforeEach
    void setUp() {
        embeddingServiceClient = new RecordingEmbeddingServiceClient();
        postgresClient = new RecordingOlsPostgresClient();
        service = new McpEmbeddingService();
        service.embeddingServiceClient = embeddingServiceClient;
        service.postgresClient = postgresClient;
    }

    // ------------------------------------------------------------------
    // listEmbeddingModels
    // ------------------------------------------------------------------

    @Test
    void listEmbeddingModelsMarksAModelPresentInBothListsAsAbleToEmbed() {
        embeddingServiceClient.availableModels = List.of("shared_model");
        postgresClient.embeddingModels = List.of("shared_model");

        List<Map<String, Object>> result = service.listEmbeddingModels();

        assertEquals(1, result.size());
        assertEquals("shared_model", result.get(0).get("model"));
        assertEquals(true, result.get(0).get("can_embed"));
    }

    @Test
    void listEmbeddingModelsIncludesAPostgresOnlyModelWithCanEmbedFalse() {
        embeddingServiceClient.availableModels = List.of();
        postgresClient.embeddingModels = List.of("postgres_only_model");

        List<Map<String, Object>> result = service.listEmbeddingModels();

        assertEquals(1, result.size());
        assertEquals("postgres_only_model", result.get(0).get("model"));
        assertEquals(false, result.get(0).get("can_embed"));
    }

    @Test
    void listEmbeddingModelsExcludesAModelKnownOnlyToTheEmbeddingServiceEntirely() {
        // The result is built by iterating the Postgres list only (see McpEmbeddingService's own
        // comment: "Build response - only include models that exist in Postgres"). A model the
        // embedding service can serve but that has no embeddings_<model> column in Postgres must not
        // appear at all -- not even with can_embed=false -- since it can never be used for
        // similarity search. This is the easy-to-get-backwards asymmetry called out in the task
        // brief, so it is proven explicitly here rather than assumed.
        embeddingServiceClient.availableModels = List.of("embedding_service_only_model", "shared_model");
        postgresClient.embeddingModels = List.of("shared_model");

        List<Map<String, Object>> result = service.listEmbeddingModels();

        assertEquals(1, result.size());
        assertEquals("shared_model", result.get(0).get("model"));
        assertEquals(true, result.get(0).get("can_embed"));
        assertTrue(
                result.stream().noneMatch(m -> "embedding_service_only_model".equals(m.get("model"))),
                "a model absent from Postgres must be excluded entirely, not included with can_embed=false");
    }

    @Test
    void listEmbeddingModelsSortsTheResultAlphabeticallyByModelNameRegardlessOfInputOrder() {
        embeddingServiceClient.availableModels = List.of();
        postgresClient.embeddingModels = List.of("zeta_model", "alpha_model", "mid_model");

        List<Map<String, Object>> result = service.listEmbeddingModels();

        assertEquals(
                List.of("alpha_model", "mid_model", "zeta_model"),
                result.stream().map(m -> (String) m.get("model")).toList());
    }

    @Test
    void listEmbeddingModelsReturnsEmptyResultWithNoExceptionWhenBothListsAreEmpty() {
        embeddingServiceClient.availableModels = List.of();
        postgresClient.embeddingModels = List.of();

        List<Map<String, Object>> result = service.listEmbeddingModels();

        assertTrue(result.isEmpty());
    }

    // ------------------------------------------------------------------
    // searchWithEmbeddingModel
    // ------------------------------------------------------------------

    @Test
    void searchWithEmbeddingModelEmbedsTheQueryAndConvertsVectorPrecisely() throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.5f, -2.25f, 0.0f, 100.125f};

        service.searchWithEmbeddingModel("liver disease", "test_model", null, null, null, null);

        assertEquals("test_model", embeddingServiceClient.lastModel);
        assertEquals("liver disease", embeddingServiceClient.lastText);
        assertEquals(List.of(1.5, -2.25, 0.0, 100.125), postgresClient.searchVectorVector);
    }

    @Test
    void searchWithEmbeddingModelAppliesDefaultPaging() throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};

        service.searchWithEmbeddingModel("q", "m", null, null, null, null);

        assertEquals(0, postgresClient.searchVectorPageable.getPageNumber());
        assertEquals(20, postgresClient.searchVectorPageable.getPageSize());
    }

    @Test
    void searchWithEmbeddingModelHonoursExplicitPaging() throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};

        service.searchWithEmbeddingModel("q", "m", null, null, 3, 6);

        assertEquals(3, postgresClient.searchVectorPageable.getPageNumber());
        assertEquals(6, postgresClient.searchVectorPageable.getPageSize());
    }

    @Test
    void searchWithEmbeddingModelUsesGlobalSearchWhenOntologyIdIsNullOrEmpty() throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};

        service.searchWithEmbeddingModel("q", "m", null, null, null, null);
        assertTrue(postgresClient.searchVectorCalled);
        assertFalse(postgresClient.searchVectorInOntologyCalled);

        postgresClient.searchVectorCalled = false;
        service.searchWithEmbeddingModel("q", "m", "", null, null, null);
        assertTrue(postgresClient.searchVectorCalled, "an empty-string ontologyId must be treated as absent");
        assertFalse(postgresClient.searchVectorInOntologyCalled);
    }

    @Test
    void searchWithEmbeddingModelUsesOntologyScopedSearchWithDefiningOntologyTrueWhenOntologyIdProvided()
            throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};

        service.searchWithEmbeddingModel("q", "m", "efo", null, null, null);

        assertTrue(postgresClient.searchVectorInOntologyCalled);
        assertFalse(postgresClient.searchVectorCalled);
        assertEquals("efo", postgresClient.searchVectorInOntologyOntologyId);
        assertTrue(postgresClient.searchVectorInOntologyIsDefiningOntology);
        assertEquals("m", postgresClient.searchVectorInOntologyModel);
    }

    @Test
    void searchWithEmbeddingModelUsesOntologyEntityAsTheSearchScopeForTheGlobalSearch() throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};

        service.searchWithEmbeddingModel("q", "m", null, null, null, null);

        // Unlike McpClassService.searchClassesWithEmbeddingModel (which passes "OntologyClass"),
        // this method passes "OntologyClass"'s sibling sentinel "OntologyEntity" -- confirmed by
        // OlsPostgresClient.hasConcreteEntityType to be the one value that disables the type filter
        // entirely, so every entity type (class/property/individual) is searched, not just classes.
        assertEquals("OntologyEntity", postgresClient.searchVectorType);
    }

    @Test
    void searchWithEmbeddingModelUsesOntologyEntityAsTheSearchScopeForTheOntologyScopedSearchToo()
            throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};

        service.searchWithEmbeddingModel("q", "m", "efo", null, null, null);

        assertEquals("OntologyEntity", postgresClient.searchVectorInOntologyType);
    }

    @Test
    void searchWithEmbeddingModelResolvesIncludeCurationsNullAndExplicitTrueToTrue() throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};

        service.searchWithEmbeddingModel("q", "m", null, null, null, null);
        assertTrue(postgresClient.searchVectorIncludeCurations);

        service.searchWithEmbeddingModel("q", "m", null, true, null, null);
        assertTrue(postgresClient.searchVectorIncludeCurations);

        service.searchWithEmbeddingModel("q", "m", null, false, null, null);
        assertFalse(postgresClient.searchVectorIncludeCurations);
    }

    @Test
    void searchWithEmbeddingModelResolvesIncludeCurationsForTheOntologyScopedSearchTheSameWay() throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};

        service.searchWithEmbeddingModel("q", "m", "efo", false, null, null);

        assertFalse(postgresClient.searchVectorInOntologyIncludeCurations);
    }

    @Test
    void searchWithEmbeddingModelUsesTheHardcodedEnglishLanguageWithNoLangParameterToOverrideIt() throws IOException {
        // searchWithEmbeddingModel (unlike McpClassService's searchClassesWithEmbeddingModel) takes
        // no lang parameter at all: JsonTransformer.transformJson is always called with the literal
        // "en". Proven observably (the internal call cannot be intercepted by a collaborator fake):
        // an entity whose only localized label is tagged "de" resolves to no "label" key at all under
        // the hardcoded "en", so McpSearchResult's title -- which concatenates curie + " " + label --
        // ends with the literal string "null" for the missing label, rather than showing "German
        // Label".
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};
        postgresClient.pageResult = plainPage(entityWithGermanOnlyLabel());

        McpPage<McpSearchResult> result = service.searchWithEmbeddingModel("q", "m", null, null, null, null);

        assertEquals("EFO:X null", result.items().get(0).title);
    }

    @Test
    void searchWithEmbeddingModelResolvesAnEnglishTaggedLabelIntoTheTitle() throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};
        postgresClient.pageResult = plainPage(entityWithEnglishLabel());

        McpPage<McpSearchResult> result = service.searchWithEmbeddingModel("q", "m", null, null, null, null);

        assertEquals("EFO:Y English Label", result.items().get(0).title);
    }

    @Test
    void searchWithEmbeddingModelMapsPageContentAndMetadataThroughMcpSearchResult() throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};
        postgresClient.pageResult = new PageImpl<>(
                new ArrayList<>(List.of(searchResultJson(
                        "http://example.org/EFO_0001", "efo", "EFO:0001", "Liver disease", false))),
                PageRequest.of(0, 20), 1);

        McpPage<McpSearchResult> result = service.searchWithEmbeddingModel("q", "m", null, null, null, null);

        assertEquals(1, result.items().size());
        McpSearchResult mapped = result.items().get(0);
        assertEquals("efo+http://example.org/EFO_0001", mapped.id);
        assertEquals("http://example.org/EFO_0001", mapped.url);
        assertEquals("EFO:0001 Liver disease", mapped.title);
        assertFalse(mapped.isObsolete);
        assertEquals(0, result.pageNum());
        assertEquals(20, result.pageSize());
        assertEquals(1, result.totalElements());
        assertEquals(1, result.totalPages());
    }

    @Test
    void searchWithEmbeddingModelMapsIsObsoleteTrueThrough() throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};
        postgresClient.pageResult = plainPage(searchResultJson(
                "http://example.org/EFO_0999", "efo", "EFO:0999", "Legacy liver concept", true));

        McpPage<McpSearchResult> result = service.searchWithEmbeddingModel("q", "m", null, null, null, null);

        assertTrue(result.items().get(0).isObsolete);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Page<JsonElement> plainPage(JsonElement... elements) {
        return new PageImpl<>(new ArrayList<>(List.of(elements)), PageRequest.of(0, 20), elements.length);
    }

    /** A flat, non-"entity"-typed fixture: bypasses LocalizationTransform's entity-localization
     * branch entirely (no "type" key means it isn't classified as an entity/ontology/reification/
     * literal, so it is returned unchanged), matching exactly the fields {@code McpSearchResult
     * .fromJson} reads. */
    private static JsonElement searchResultJson(
            String iri, String ontologyId, String curie, String label, boolean isObsolete) {
        JsonObject json = new JsonObject();
        json.addProperty("iri", iri);
        json.addProperty("ontologyId", ontologyId);
        json.addProperty("curie", curie);
        json.addProperty("label", label);
        json.addProperty("isObsolete", isObsolete);
        return JsonParser.parseString(json.toString());
    }

    private static JsonElement entityWithGermanOnlyLabel() {
        return entityWithLocalizedLabel("de", "German Label", "http://example.org/X", "EFO:X");
    }

    private static JsonElement entityWithEnglishLabel() {
        return entityWithLocalizedLabel("en", "English Label", "http://example.org/Y", "EFO:Y");
    }

    private static JsonElement entityWithLocalizedLabel(String lang, String labelValue, String iri, String curie) {
        JsonArray labelType = new JsonArray();
        labelType.add("literal");

        JsonObject label = new JsonObject();
        label.add("type", labelType);
        label.addProperty("lang", lang);
        label.addProperty("value", labelValue);

        JsonArray topType = new JsonArray();
        topType.add("entity");
        topType.add("class");

        JsonObject json = new JsonObject();
        json.addProperty("iri", iri);
        json.addProperty("ontologyId", "efo");
        json.addProperty("curie", curie);
        json.addProperty("isObsolete", false);
        json.add("type", topType);
        json.add("label", label);
        json.add("linkedEntities", new JsonObject());
        return JsonParser.parseString(json.toString());
    }

    // ------------------------------------------------------------------
    // Hand-rolled fakes (this repo's unit-test idiom; see McpClassServiceTest)
    // ------------------------------------------------------------------

    private static class RecordingEmbeddingServiceClient extends EmbeddingServiceClient {
        private List<String> availableModels = List.of();
        private String lastModel;
        private String lastText;
        private float[] vectorToReturn = new float[] {0.0f};

        @Override
        public List<String> getAvailableModels() {
            return availableModels;
        }

        @Override
        public float[] embedText(String model, String text) throws IOException {
            this.lastModel = model;
            this.lastText = text;
            return vectorToReturn;
        }
    }

    private static class RecordingOlsPostgresClient extends OlsPostgresClient {
        private List<String> embeddingModels = List.of();
        private Page<JsonElement> pageResult = plainPage();

        private boolean searchVectorCalled;
        private String searchVectorType;
        private List<Double> searchVectorVector;
        private Pageable searchVectorPageable;
        private String searchVectorModel;
        private boolean searchVectorIncludeCurations;

        private boolean searchVectorInOntologyCalled;
        private String searchVectorInOntologyType;
        private List<Double> searchVectorInOntologyVector;
        private Pageable searchVectorInOntologyPageable;
        private String searchVectorInOntologyModel;
        private String searchVectorInOntologyOntologyId;
        private boolean searchVectorInOntologyIsDefiningOntology;
        private boolean searchVectorInOntologyIncludeCurations;

        @Override
        public List<String> getEmbeddingModels() {
            return embeddingModels;
        }

        @Override
        public Page<JsonElement> searchByVector(
                String type, List<Double> vector, Pageable pageable, String modelName, boolean includeCurations) {
            this.searchVectorCalled = true;
            this.searchVectorType = type;
            this.searchVectorVector = vector;
            this.searchVectorPageable = pageable;
            this.searchVectorModel = modelName;
            this.searchVectorIncludeCurations = includeCurations;
            return pageResult;
        }

        @Override
        public Page<JsonElement> searchByVectorInOntology(
                String type, List<Double> vector, Pageable pageable, String modelName, String ontologyId,
                boolean isDefiningOntology, boolean includeCurations) {
            this.searchVectorInOntologyCalled = true;
            this.searchVectorInOntologyType = type;
            this.searchVectorInOntologyVector = vector;
            this.searchVectorInOntologyPageable = pageable;
            this.searchVectorInOntologyModel = modelName;
            this.searchVectorInOntologyOntologyId = ontologyId;
            this.searchVectorInOntologyIsDefiningOntology = isDefiningOntology;
            this.searchVectorInOntologyIncludeCurations = includeCurations;
            return pageResult;
        }
    }
}
