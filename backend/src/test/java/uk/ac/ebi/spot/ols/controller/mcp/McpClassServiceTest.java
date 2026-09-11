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
import uk.ac.ebi.spot.ols.model.mcp.McpClass;
import uk.ac.ebi.spot.ols.model.mcp.McpPage;
import uk.ac.ebi.spot.ols.repository.ClassRepository;
import uk.ac.ebi.spot.ols.repository.EntityRepository;
import uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;
import uk.ac.ebi.spot.ols.service.EmbeddingServiceClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for {@link McpClassService}'s own wiring decisions: default {@code pageNum}/
 * {@code pageSize}/{@code lang} resolution (both the mutable-parameter-reassignment pattern used
 * by {@code searchClasses}/{@code getAncestors}/{@code getChildren}/{@code getDescendants} and the
 * separate {@code final effectiveLang} pattern used by {@code searchClassesWithEmbeddingModel}/
 * {@code getSimilarClasses}, verified independently since a future refactor could desync them),
 * the always-on {@code JsonTransformOptions} (resolveReferences/manchesterSyntax both hardcoded
 * {@code true}), {@code searchClasses}'s three-way {@code isObsolete}/{@code ontologyId} filter
 * construction, {@code searchClassesWithEmbeddingModel}'s {@code float[]}-to-{@code List<Double>}
 * conversion and vector-search/curations branching, and exact collaborator delegation plus
 * {@code McpPage} mapping for every {@code @Tool} method.
 *
 * <p>Uses this repo's hand-rolled-fake idiom (subclasses overriding just the methods under test,
 * no Mockito), the same idiom as {@code V2LLMControllerTest}.</p>
 */
class McpClassServiceTest {

    private RecordingEntityRepository entityRepository;
    private RecordingClassRepository classRepository;
    private RecordingEmbeddingServiceClient embeddingServiceClient;
    private RecordingOlsPostgresClient postgresClient;
    private McpClassService service;

    @BeforeEach
    void setUp() {
        entityRepository = new RecordingEntityRepository();
        classRepository = new RecordingClassRepository();
        embeddingServiceClient = new RecordingEmbeddingServiceClient();
        postgresClient = new RecordingOlsPostgresClient();
        service = new McpClassService();
        service.entityRepository = entityRepository;
        service.classRepository = classRepository;
        service.embeddingServiceClient = embeddingServiceClient;
        service.postgresClient = postgresClient;
    }

    // ------------------------------------------------------------------
    // searchClasses
    // ------------------------------------------------------------------

    @Test
    void searchClassesAppliesDefaultsAndAlwaysFiltersToNonObsoleteClasses() throws IOException {
        service.searchClasses("liver", null, null, null, null, null);

        assertEquals(0, entityRepository.lastPageable.getPageNumber());
        assertEquals(20, entityRepository.lastPageable.getPageSize());
        assertEquals("en", entityRepository.lastLang);
        assertEquals("liver", entityRepository.lastSearch);
        assertNull(entityRepository.lastSearchFields);
        assertNull(entityRepository.lastBoostFields);
        assertNull(entityRepository.lastFacetFields);
        assertFalse(entityRepository.lastExactMatch);
        assertNull(entityRepository.lastExcludeOntologyIds);
        assertEquals(
                Map.of("type", List.of("class"), "isObsolete", List.of("false")),
                entityRepository.lastProperties);
        assertTrue(entityRepository.lastOutputOpts.resolveReferences);
        assertTrue(entityRepository.lastOutputOpts.manchesterSyntax);
    }

    @Test
    void searchClassesHonoursExplicitPagingAndLanguage() throws IOException {
        service.searchClasses("liver", null, 2, 5, "fr", null);

        assertEquals(2, entityRepository.lastPageable.getPageNumber());
        assertEquals(5, entityRepository.lastPageable.getPageSize());
        assertEquals("fr", entityRepository.lastLang);
    }

    @Test
    void searchClassesAddsOntologyIdFilterOnlyWhenProvided() throws IOException {
        service.searchClasses("liver", "efo", null, null, null, null);
        assertEquals(List.of("efo"), entityRepository.lastProperties.get("ontologyId"));

        service.searchClasses("liver", null, null, null, null, null);
        assertFalse(entityRepository.lastProperties.containsKey("ontologyId"));
    }

    @Test
    void searchClassesTreatsNullAndExplicitFalseIncludeObsoleteTheSame() throws IOException {
        service.searchClasses("liver", null, null, null, null, null);
        assertEquals(List.of("false"), entityRepository.lastProperties.get("isObsolete"));

        service.searchClasses("liver", null, null, null, null, false);
        assertEquals(List.of("false"), entityRepository.lastProperties.get("isObsolete"));
    }

    @Test
    void searchClassesOmitsObsoleteFilterOnlyWhenExplicitlyIncluded() throws IOException {
        service.searchClasses("liver", null, null, null, null, true);

        assertFalse(entityRepository.lastProperties.containsKey("isObsolete"));
        assertEquals(List.of("class"), entityRepository.lastProperties.get("type"));
    }

    @Test
    void searchClassesMapsPageContentAndMetadataThroughUnchanged() throws IOException {
        entityRepository.pageResult = new OlsFacetedResultsPage<>(
                new ArrayList<>(List.of(classJson("http://example.org/EFO_0001", "Liver disease"))),
                Map.of(), PageRequest.of(2, 5), 42);

        McpPage<McpClass> result = service.searchClasses("liver", null, null, null, null, null);

        assertEquals(1, result.items().size());
        assertEquals("http://example.org/EFO_0001", result.items().get(0).iri);
        assertEquals(2, result.pageNum());
        assertEquals(5, result.pageSize());
        assertEquals(42, result.totalElements());
        assertEquals(9, result.totalPages());
    }

    // ------------------------------------------------------------------
    // getAncestors
    // ------------------------------------------------------------------

    @Test
    void getAncestorsAppliesDefaultsAndDelegatesWithFixedIncludeObsoleteFalse() throws IOException {
        service.getAncestors("efo", "http://example.org/EFO_1001", null, null, null);

        assertEquals(0, classRepository.ancestorsPageable.getPageNumber());
        assertEquals(20, classRepository.ancestorsPageable.getPageSize());
        assertEquals("efo", classRepository.ancestorsOntologyId);
        assertEquals("http://example.org/EFO_1001", classRepository.ancestorsIri);
        assertFalse(classRepository.ancestorsIncludeObsolete);
        assertEquals("en", classRepository.ancestorsLang);
        assertTrue(classRepository.ancestorsOutputOpts.resolveReferences);
        assertTrue(classRepository.ancestorsOutputOpts.manchesterSyntax);
    }

    @Test
    void getAncestorsHonoursExplicitPagingAndLanguage() throws IOException {
        service.getAncestors("efo", "http://example.org/EFO_1001", 3, 7, "fr");

        assertEquals(3, classRepository.ancestorsPageable.getPageNumber());
        assertEquals(7, classRepository.ancestorsPageable.getPageSize());
        assertEquals("fr", classRepository.ancestorsLang);
    }

    @Test
    void getAncestorsMapsPageContentAndMetadataThroughUnchanged() throws IOException {
        // offset (5) + pageSize (5) must stay <= total (15), or Spring's PageImpl recomputes total
        // as offset + content.size() instead of honouring the value passed in.
        classRepository.pageResult = new PageImpl<>(
                new ArrayList<>(List.of(classJson("http://example.org/EFO_0001", "Liver disease"))),
                PageRequest.of(1, 5), 15);

        McpPage<McpClass> result = service.getAncestors("efo", "http://example.org/EFO_1001", null, null, null);

        assertEquals(1, result.items().size());
        assertEquals("http://example.org/EFO_0001", result.items().get(0).iri);
        assertEquals(1, result.pageNum());
        assertEquals(5, result.pageSize());
        assertEquals(15, result.totalElements());
        assertEquals(3, result.totalPages());
    }

    // ------------------------------------------------------------------
    // getChildren
    // ------------------------------------------------------------------

    @Test
    void getChildrenAppliesDefaultsAndDelegatesWithFixedIncludeObsoleteFalseAndNullSearch() throws IOException {
        service.getChildren("efo", "http://example.org/EFO_0001", null, null, null);

        assertEquals(0, classRepository.childrenPageable.getPageNumber());
        assertEquals(20, classRepository.childrenPageable.getPageSize());
        assertEquals("efo", classRepository.childrenOntologyId);
        assertEquals("http://example.org/EFO_0001", classRepository.childrenIri);
        assertFalse(classRepository.childrenIncludeObsolete);
        assertNull(classRepository.childrenSearch);
        assertEquals("en", classRepository.childrenLang);
        assertTrue(classRepository.childrenOutputOpts.resolveReferences);
        assertTrue(classRepository.childrenOutputOpts.manchesterSyntax);
    }

    @Test
    void getChildrenHonoursExplicitPagingAndLanguage() throws IOException {
        service.getChildren("efo", "http://example.org/EFO_0001", 4, 8, "de");

        assertEquals(4, classRepository.childrenPageable.getPageNumber());
        assertEquals(8, classRepository.childrenPageable.getPageSize());
        assertEquals("de", classRepository.childrenLang);
    }

    @Test
    void getChildrenMapsPageContentAndMetadataThroughUnchanged() throws IOException {
        classRepository.pageResult = new PageImpl<>(
                new ArrayList<>(List.of(classJson("http://example.org/EFO_1001", "Clinical liver child"))),
                PageRequest.of(0, 20), 1);

        McpPage<McpClass> result = service.getChildren("efo", "http://example.org/EFO_0001", null, null, null);

        assertEquals(1, result.items().size());
        assertEquals("http://example.org/EFO_1001", result.items().get(0).iri);
        assertEquals(1, result.totalElements());
        assertEquals(1, result.totalPages());
    }

    // ------------------------------------------------------------------
    // getDescendants
    // ------------------------------------------------------------------

    @Test
    void getDescendantsAppliesDefaultsAndDelegatesWithFixedIncludeObsoleteFalse() throws IOException {
        service.getDescendants("efo", "http://example.org/EFO_0001", null, null, null);

        assertEquals(0, classRepository.descendantsPageable.getPageNumber());
        assertEquals(20, classRepository.descendantsPageable.getPageSize());
        assertEquals("efo", classRepository.descendantsOntologyId);
        assertEquals("http://example.org/EFO_0001", classRepository.descendantsIri);
        assertFalse(classRepository.descendantsIncludeObsolete);
        assertEquals("en", classRepository.descendantsLang);
        assertTrue(classRepository.descendantsOutputOpts.resolveReferences);
        assertTrue(classRepository.descendantsOutputOpts.manchesterSyntax);
    }

    @Test
    void getDescendantsHonoursExplicitPagingAndLanguage() throws IOException {
        service.getDescendants("efo", "http://example.org/EFO_0001", 6, 11, "es");

        assertEquals(6, classRepository.descendantsPageable.getPageNumber());
        assertEquals(11, classRepository.descendantsPageable.getPageSize());
        assertEquals("es", classRepository.descendantsLang);
    }

    @Test
    void getDescendantsMapsPageContentAndMetadataThroughUnchanged() throws IOException {
        classRepository.pageResult = new PageImpl<>(
                new ArrayList<>(List.of(classJson("http://example.org/EFO_1001", "Clinical liver child"))),
                PageRequest.of(0, 20), 1);

        McpPage<McpClass> result = service.getDescendants("efo", "http://example.org/EFO_0001", null, null, null);

        assertEquals(1, result.items().size());
        assertEquals("http://example.org/EFO_1001", result.items().get(0).iri);
        assertEquals(1, result.totalElements());
    }

    // ------------------------------------------------------------------
    // searchClassesWithEmbeddingModel
    // ------------------------------------------------------------------

    @Test
    void searchClassesWithEmbeddingModelEmbedsTheQueryAndConvertsVectorPrecisely() throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.5f, -2.25f, 0.0f, 100.125f};

        service.searchClassesWithEmbeddingModel("liver disease", "test_model", null, null, null, null, null);

        assertEquals("test_model", embeddingServiceClient.lastModel);
        assertEquals("liver disease", embeddingServiceClient.lastText);
        assertEquals(List.of(1.5, -2.25, 0.0, 100.125), postgresClient.searchVectorVector);
    }

    @Test
    void searchClassesWithEmbeddingModelUsesGlobalSearchWhenOntologyIdIsNullOrEmpty() throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};

        service.searchClassesWithEmbeddingModel("q", "m", null, null, null, null, null);
        assertTrue(postgresClient.searchVectorCalled);
        assertFalse(postgresClient.searchVectorInOntologyCalled);

        postgresClient.searchVectorCalled = false;
        service.searchClassesWithEmbeddingModel("q", "m", "", null, null, null, null);
        assertTrue(postgresClient.searchVectorCalled, "an empty-string ontologyId must be treated as absent");
        assertFalse(postgresClient.searchVectorInOntologyCalled);
    }

    @Test
    void searchClassesWithEmbeddingModelUsesOntologyScopedSearchWithDefiningOntologyTrueWhenOntologyIdProvided()
            throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};

        service.searchClassesWithEmbeddingModel("q", "m", "efo", null, null, null, null);

        assertTrue(postgresClient.searchVectorInOntologyCalled);
        assertFalse(postgresClient.searchVectorCalled);
        assertEquals("OntologyClass", postgresClient.searchVectorInOntologyType);
        assertEquals("efo", postgresClient.searchVectorInOntologyOntologyId);
        assertTrue(postgresClient.searchVectorInOntologyIsDefiningOntology);
        assertEquals("m", postgresClient.searchVectorInOntologyModel);
    }

    @Test
    void searchClassesWithEmbeddingModelUsesOntologyClassAsTheEntityTypeForTheGlobalSearchToo() throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};

        service.searchClassesWithEmbeddingModel("q", "m", null, null, null, null, null);

        assertEquals("OntologyClass", postgresClient.searchVectorType);
    }

    @Test
    void searchClassesWithEmbeddingModelResolvesIncludeCurationsNullAndExplicitTrueToTrue() throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};

        service.searchClassesWithEmbeddingModel("q", "m", null, null, null, null, null);
        assertTrue(postgresClient.searchVectorIncludeCurations);

        service.searchClassesWithEmbeddingModel("q", "m", null, true, null, null, null);
        assertTrue(postgresClient.searchVectorIncludeCurations);

        service.searchClassesWithEmbeddingModel("q", "m", null, false, null, null, null);
        assertFalse(postgresClient.searchVectorIncludeCurations);
    }

    @Test
    void searchClassesWithEmbeddingModelHonoursExplicitPaging() throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};

        service.searchClassesWithEmbeddingModel("q", "m", null, null, 3, 6, null);

        assertEquals(3, postgresClient.searchVectorPageable.getPageNumber());
        assertEquals(6, postgresClient.searchVectorPageable.getPageSize());
    }

    @Test
    void searchClassesWithEmbeddingModelAlwaysResolvesReferencesRegardlessOfCallerOptions() throws IOException {
        // outputOpts is used only inside McpClassService's own JsonTransformer.transformJson call
        // (never handed to a fake collaborator), so the hardcoded resolveReferences=true is proven
        // observably here: a directParent IRI only resolves into a full linkedEntities object when
        // resolveReferences is actually true.
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};
        postgresClient.pageResult = plainPage(classJsonWithLinkedParent(
                "http://example.org/EFO_1001", "http://example.org/EFO_0001", "Liver disease"));

        McpPage<McpClass> result = service.searchClassesWithEmbeddingModel("q", "m", null, null, null, null, null);

        McpClass mapped = result.items().get(0);
        assertEquals(1, mapped.directParent.size());
        assertEquals("http://example.org/EFO_0001", mapped.directParent.get(0).iri);
        assertEquals(List.of("Liver disease"), mapped.directParent.get(0).label);
    }

    @Test
    void searchClassesWithEmbeddingModelDefaultsEffectiveLangToEnWhenLangIsNull() throws IOException {
        // effectiveLang is a `final` local, used only inside the internal JsonTransformer call, so
        // it cannot be captured via a fake collaborator either. Proven observably instead: an entity
        // whose only localized label is tagged "de" resolves to no label under the default (en)
        // language and to the German label when "de" is requested explicitly.
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};
        postgresClient.pageResult = plainPage(entityWithGermanOnlyLabel());

        McpPage<McpClass> defaultLang =
                service.searchClassesWithEmbeddingModel("q", "m", null, null, null, null, null);
        McpPage<McpClass> germanLang =
                service.searchClassesWithEmbeddingModel("q", "m", null, null, null, null, "de");

        assertEquals(List.of(), defaultLang.items().get(0).label);
        assertEquals(List.of("German Label"), germanLang.items().get(0).label);
    }

    @Test
    void searchClassesWithEmbeddingModelMapsPageContentAndMetadataThroughUnchanged() throws IOException {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};
        postgresClient.pageResult = new PageImpl<>(
                new ArrayList<>(List.of(classJson("http://example.org/EFO_0001", "Liver disease"))),
                PageRequest.of(0, 20), 1);

        McpPage<McpClass> result = service.searchClassesWithEmbeddingModel("q", "m", null, null, null, null, null);

        assertEquals(1, result.items().size());
        assertEquals("http://example.org/EFO_0001", result.items().get(0).iri);
        assertEquals(1, result.totalElements());
    }

    // ------------------------------------------------------------------
    // getSimilarClasses
    // ------------------------------------------------------------------

    @Test
    void getSimilarClassesAppliesDefaultsAndDelegates() {
        service.getSimilarClasses("http://example.org/EFO_0001", "test_model", null, null, null);

        assertEquals(0, classRepository.getSimilarPageable.getPageNumber());
        assertEquals(20, classRepository.getSimilarPageable.getPageSize());
        assertEquals("http://example.org/EFO_0001", classRepository.getSimilarIri);
        assertEquals("en", classRepository.getSimilarLang);
        assertEquals("test_model", classRepository.getSimilarModel);
        assertTrue(classRepository.getSimilarOutputOpts.resolveReferences);
        assertTrue(classRepository.getSimilarOutputOpts.manchesterSyntax);
    }

    @Test
    void getSimilarClassesHonoursExplicitPagingAndLanguage() {
        service.getSimilarClasses("http://example.org/EFO_0001", "test_model", 4, 9, "fr");

        assertEquals(4, classRepository.getSimilarPageable.getPageNumber());
        assertEquals(9, classRepository.getSimilarPageable.getPageSize());
        assertEquals("fr", classRepository.getSimilarLang);
    }

    @Test
    void getSimilarClassesMapsPageContentAndMetadataThroughUnchanged() {
        // offset (0) + pageSize (3) must stay <= total (3), or Spring's PageImpl recomputes total
        // as offset + content.size() instead of honouring the value passed in.
        classRepository.pageResult = new PageImpl<>(
                new ArrayList<>(List.of(classJson("http://example.org/EFO_0002", "Hepatic condition"))),
                PageRequest.of(0, 3), 3);

        McpPage<McpClass> result =
                service.getSimilarClasses("http://example.org/EFO_0001", "test_model", null, null, null);

        assertEquals(1, result.items().size());
        assertEquals("http://example.org/EFO_0002", result.items().get(0).iri);
        assertEquals(3, result.totalElements());
        assertEquals(1, result.totalPages());
    }

    // ------------------------------------------------------------------
    // getClassSimilarity
    // ------------------------------------------------------------------

    @Test
    void getClassSimilarityDelegatesArgumentsAndReturnsValueUnchanged() {
        classRepository.similarityResult = 0.734;

        double result = service.getClassSimilarity(
                "http://example.org/EFO_0001", "http://example.org/EFO_0002", "test_model");

        assertEquals("http://example.org/EFO_0001", classRepository.similarityIri1);
        assertEquals("http://example.org/EFO_0002", classRepository.similarityIri2);
        assertEquals("test_model", classRepository.similarityModel);
        assertEquals(0.734, result);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Page<JsonElement> plainPage(JsonElement... elements) {
        return new PageImpl<>(new ArrayList<>(List.of(elements)), PageRequest.of(0, 20), elements.length);
    }

    private static JsonElement classJson(String iri, String label) {
        JsonObject json = new JsonObject();
        json.addProperty("iri", iri);
        json.addProperty("label", label);
        return JsonParser.parseString(json.toString());
    }

    private static JsonElement classJsonWithLinkedParent(String iri, String parentIri, String parentLabel) {
        JsonObject parentLabelWrapper = new JsonObject();
        JsonArray parentLabelArray = new JsonArray();
        parentLabelArray.add(parentLabel);
        parentLabelWrapper.add("label", parentLabelArray);

        JsonObject linkedEntities = new JsonObject();
        linkedEntities.add(parentIri, parentLabelWrapper);

        JsonArray directParent = new JsonArray();
        directParent.add(parentIri);

        JsonObject json = new JsonObject();
        json.addProperty("iri", iri);
        json.add("directParent", directParent);
        json.add("linkedEntities", linkedEntities);
        return JsonParser.parseString(json.toString());
    }

    private static JsonElement entityWithGermanOnlyLabel() {
        JsonArray labelType = new JsonArray();
        labelType.add("literal");

        JsonObject label = new JsonObject();
        label.add("type", labelType);
        label.addProperty("lang", "de");
        label.addProperty("value", "German Label");

        JsonArray topType = new JsonArray();
        topType.add("entity");
        topType.add("class");

        JsonObject json = new JsonObject();
        json.addProperty("iri", "http://example.org/X");
        json.add("type", topType);
        json.add("label", label);
        json.add("linkedEntities", new JsonObject());
        return JsonParser.parseString(json.toString());
    }

    // ------------------------------------------------------------------
    // Hand-rolled fakes (this repo's unit-test idiom; see V2LLMControllerTest)
    // ------------------------------------------------------------------

    private static class RecordingEntityRepository extends EntityRepository {
        private OlsFacetedResultsPage<JsonElement> pageResult =
                new OlsFacetedResultsPage<>(new ArrayList<>(), Map.of(), PageRequest.of(0, 20), 0);

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
            return pageResult;
        }
    }

    private static class RecordingClassRepository extends ClassRepository {
        private Page<JsonElement> pageResult = plainPage();

        private String ancestorsOntologyId;
        private Pageable ancestorsPageable;
        private String ancestorsIri;
        private boolean ancestorsIncludeObsolete;
        private String ancestorsLang;
        private JsonTransformOptions ancestorsOutputOpts;

        private String childrenOntologyId;
        private Pageable childrenPageable;
        private String childrenIri;
        private boolean childrenIncludeObsolete;
        private String childrenSearch;
        private String childrenLang;
        private JsonTransformOptions childrenOutputOpts;

        private String descendantsOntologyId;
        private Pageable descendantsPageable;
        private String descendantsIri;
        private boolean descendantsIncludeObsolete;
        private String descendantsLang;
        private JsonTransformOptions descendantsOutputOpts;

        private Pageable getSimilarPageable;
        private String getSimilarIri;
        private String getSimilarLang;
        private JsonTransformOptions getSimilarOutputOpts;
        private String getSimilarModel;

        private String similarityIri1;
        private String similarityIri2;
        private String similarityModel;
        private double similarityResult;

        @Override
        public Page<JsonElement> getAncestorsByOntologyId(
                String ontologyId, Pageable pageable, String iri, boolean includeObsolete, String lang,
                JsonTransformOptions outputOpts) {
            this.ancestorsOntologyId = ontologyId;
            this.ancestorsPageable = pageable;
            this.ancestorsIri = iri;
            this.ancestorsIncludeObsolete = includeObsolete;
            this.ancestorsLang = lang;
            this.ancestorsOutputOpts = outputOpts;
            return pageResult;
        }

        @Override
        public Page<JsonElement> getChildrenByOntologyId(
                String ontologyId, Pageable pageable, String iri, boolean includeObsolete, String search,
                String lang, JsonTransformOptions outputOpts) {
            this.childrenOntologyId = ontologyId;
            this.childrenPageable = pageable;
            this.childrenIri = iri;
            this.childrenIncludeObsolete = includeObsolete;
            this.childrenSearch = search;
            this.childrenLang = lang;
            this.childrenOutputOpts = outputOpts;
            return pageResult;
        }

        @Override
        public Page<JsonElement> getDescendantsByOntologyId(
                String ontologyId, Pageable pageable, String iri, boolean includeObsolete, String lang,
                JsonTransformOptions outputOpts) {
            this.descendantsOntologyId = ontologyId;
            this.descendantsPageable = pageable;
            this.descendantsIri = iri;
            this.descendantsIncludeObsolete = includeObsolete;
            this.descendantsLang = lang;
            this.descendantsOutputOpts = outputOpts;
            return pageResult;
        }

        @Override
        public Page<JsonElement> getSimilar(
                Pageable pageable, String iri, String lang, JsonTransformOptions outputOpts, String modelName) {
            this.getSimilarPageable = pageable;
            this.getSimilarIri = iri;
            this.getSimilarLang = lang;
            this.getSimilarOutputOpts = outputOpts;
            this.getSimilarModel = modelName;
            return pageResult;
        }

        @Override
        public double getSimilarity(String iri, String iri2, String modelName) {
            this.similarityIri1 = iri;
            this.similarityIri2 = iri2;
            this.similarityModel = modelName;
            return similarityResult;
        }
    }

    private static class RecordingEmbeddingServiceClient extends EmbeddingServiceClient {
        private String lastModel;
        private String lastText;
        private float[] vectorToReturn = new float[] {0.0f};

        @Override
        public float[] embedText(String model, String text) throws IOException {
            this.lastModel = model;
            this.lastText = text;
            return vectorToReturn;
        }
    }

    private static class RecordingOlsPostgresClient extends OlsPostgresClient {
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
