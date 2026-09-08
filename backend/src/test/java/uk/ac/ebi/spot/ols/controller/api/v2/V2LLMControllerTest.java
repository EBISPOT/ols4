package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import uk.ac.ebi.spot.ols.repository.ClassRepository;
import uk.ac.ebi.spot.ols.repository.PropertyRepository;
import uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;
import uk.ac.ebi.spot.ols.service.EmbeddingServiceClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for controller-owned decisions in {@link V2LLMController}: the
 * {@code List<Double>} to {@code float[]} vector conversion, IRI URL-decoding, exact collaborator
 * delegation for all 12 routes, and the {@code llm_models} response-building logic (the one route
 * with real business logic of its own, rather than a thin pass-through).
 *
 * <p>Uses this repo's hand-rolled-fake idiom (subclasses overriding just the methods under test),
 * the same idiom as {@code V2ClassControllerTest}/{@code V2TextTaggerControllerTest}.</p>
 */
class V2LLMControllerTest {

    private RecordingClassRepository classRepository;
    private RecordingPropertyRepository propertyRepository;
    private RecordingEmbeddingServiceClient embeddingServiceClient;
    private RecordingOlsPostgresClient postgresClient;
    private V2LLMController controller;

    @BeforeEach
    void setUp() {
        classRepository = new RecordingClassRepository();
        propertyRepository = new RecordingPropertyRepository();
        embeddingServiceClient = new RecordingEmbeddingServiceClient();
        postgresClient = new RecordingOlsPostgresClient();
        controller = new V2LLMController();
        controller.classRepository = classRepository;
        controller.propertyRepository = propertyRepository;
        controller.embeddingServiceClient = embeddingServiceClient;
        controller.postgresClient = postgresClient;
    }

    // ------------------------------------------------------------------
    // GET /llm_models
    // ------------------------------------------------------------------

    @Test
    void llmModelsMarksOnlyModelsTheEmbeddingServiceOffersAsCanEmbed() throws IOException {
        embeddingServiceClient.availableModels = List.of("model-a", "model-c");
        postgresClient.embeddingModels = List.of("model-a", "model-b");

        HttpEntity<List<Map<String, Object>>> response = controller.getLLMModels();

        assertEquals(2, response.getBody().size());
        assertEquals("model-a", response.getBody().get(0).get("model"));
        assertEquals(true, response.getBody().get(0).get("can_embed"));
        assertEquals("model-b", response.getBody().get(1).get("model"));
        assertEquals(false, response.getBody().get(1).get("can_embed"));
    }

    @Test
    void llmModelsOnlyListsModelsThatHavePostgresEmbeddings() throws IOException {
        // A model the embedding service can serve but that has no stored Postgres embeddings must
        // not appear: only models with usable similarity-search data are listed.
        embeddingServiceClient.availableModels = List.of("model-a", "service-only-model");
        postgresClient.embeddingModels = List.of("model-a");

        HttpEntity<List<Map<String, Object>>> response = controller.getLLMModels();

        assertEquals(1, response.getBody().size());
        assertEquals("model-a", response.getBody().get(0).get("model"));
    }

    @Test
    void llmModelsSortsResultsByModelName() throws IOException {
        embeddingServiceClient.availableModels = List.of();
        postgresClient.embeddingModels = List.of("zebra", "alpha", "middle");

        HttpEntity<List<Map<String, Object>>> response = controller.getLLMModels();

        List<String> names = response.getBody().stream().map(m -> (String) m.get("model")).toList();
        assertEquals(List.of("alpha", "middle", "zebra"), names);
    }

    @Test
    void llmModelsReturnsAnEmptyListWhenPostgresHasNoEmbeddingColumns() throws IOException {
        embeddingServiceClient.availableModels = List.of("model-a");
        postgresClient.embeddingModels = List.of();

        HttpEntity<List<Map<String, Object>>> response = controller.getLLMModels();

        assertTrue(response.getBody().isEmpty());
    }

    // ------------------------------------------------------------------
    // POST /classes/llm_embedding
    // ------------------------------------------------------------------

    @Test
    void searchClassesByVectorConvertsTheRequestBodyToAFloatArrayAndDelegates() throws Exception {
        Pageable pageable = PageRequest.of(1, 5);
        JsonTransformOptions options = new JsonTransformOptions();
        classRepository.pageResult = onePage(classJson("http://example.org/EFO_0001", "Liver disease"));

        controller.searchClassesByVector(
                List.of(1.0, 0.0, -2.5), pageable, "fr", "test_model", "efo", false, options);

        assertEquals("test_model", classRepository.searchVectorModel);
        assertArrayEquals(new float[] {1.0f, 0.0f, -2.5f}, classRepository.searchVectorArray);
        assertSame(pageable, classRepository.searchVectorPageable);
        assertEquals("fr", classRepository.searchVectorLang);
        assertEquals("efo", classRepository.searchVectorOntologyId);
        assertSame(options, classRepository.searchVectorOutputOpts);
        assertFalse(classRepository.searchVectorIncludeCurations);
    }

    @Test
    void searchClassesByVectorHandlesAnEmptyVectorBody() throws Exception {
        classRepository.pageResult = onePage();

        controller.searchClassesByVector(
                List.of(), PageRequest.of(0, 20), "en", "test_model", null, true, new JsonTransformOptions());

        assertArrayEquals(new float[0], classRepository.searchVectorArray);
    }

    // ------------------------------------------------------------------
    // POST /ontologies/{onto}/classes/llm_embedding
    // ------------------------------------------------------------------

    @Test
    void searchClassesByVectorInOntologyConvertsTheVectorAndDelegatesWithThePathOntologyId() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        JsonTransformOptions options = new JsonTransformOptions();
        classRepository.pageResult = onePage();

        controller.searchClassesByVectorInOntology(
                "efo", List.of(2.0, 4.0), pageable, "en", "test_model", true, false, options);

        assertEquals("efo", classRepository.searchVectorInOntologyOntologyId);
        assertEquals("test_model", classRepository.searchVectorInOntologyModel);
        assertArrayEquals(new float[] {2.0f, 4.0f}, classRepository.searchVectorInOntologyArray);
        assertSame(pageable, classRepository.searchVectorInOntologyPageable);
        assertEquals("en", classRepository.searchVectorInOntologyLang);
        assertTrue(classRepository.searchVectorInOntologyIsDefiningOntology);
        assertFalse(classRepository.searchVectorInOntologyIncludeCurations);
        assertSame(options, classRepository.searchVectorInOntologyOutputOpts);
    }

    // ------------------------------------------------------------------
    // GET /entities/llm_search
    // ------------------------------------------------------------------

    @Test
    void searchEntitiesByTextEmbedsTheQueryThenSearchesAllTypesWithoutAnOntologyFilter() throws Exception {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f, 0.5f};
        postgresClient.pageResult = onePage(classJson("http://example.org/EFO_0001", "Liver disease"));

        controller.searchEntitiesByText(
                "liver", PageRequest.of(0, 20), "en", "test_model", null, true, new JsonTransformOptions());

        assertEquals("test_model", embeddingServiceClient.lastModel);
        assertEquals("liver", embeddingServiceClient.lastText);
        assertEquals("OntologyEntity", postgresClient.searchVectorType);
        assertEquals(List.of(1.0, 0.5), postgresClient.searchVectorVector);
        assertEquals("test_model", postgresClient.searchVectorModel);
        assertTrue(postgresClient.searchVectorIncludeCurations);
        assertFalse(postgresClient.searchVectorInOntologyCalled, "no ontologyId must skip the ontology-scoped query");
    }

    @Test
    void searchEntitiesByTextUsesTheOntologyScopedQueryWhenOntologyIdIsGiven() throws Exception {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};
        postgresClient.pageResult = onePage();

        controller.searchEntitiesByText(
                "liver", PageRequest.of(0, 20), "en", "test_model", "efo", false, new JsonTransformOptions());

        assertTrue(postgresClient.searchVectorInOntologyCalled);
        assertEquals("OntologyEntity", postgresClient.searchVectorInOntologyType);
        assertEquals("efo", postgresClient.searchVectorInOntologyOntologyId);
        assertTrue(postgresClient.searchVectorInOntologyIsDefiningOntology,
                "the free-text entity search always searches the defining ontology");
        assertFalse(postgresClient.searchVectorInOntologyIncludeCurations);
    }

    @Test
    void searchEntitiesByTextTreatsAnEmptyOntologyIdAsAbsent() throws Exception {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};
        postgresClient.pageResult = onePage();

        controller.searchEntitiesByText(
                "liver", PageRequest.of(0, 20), "en", "test_model", "", true, new JsonTransformOptions());

        assertFalse(postgresClient.searchVectorInOntologyCalled);
        assertTrue(postgresClient.searchVectorCalled);
    }

    // ------------------------------------------------------------------
    // GET /classes/llm_search
    // ------------------------------------------------------------------

    @Test
    void searchClassesByTextEmbedsTheQueryThenDelegatesToTheClassRepository() throws Exception {
        embeddingServiceClient.vectorToReturn = new float[] {0.0f, 1.0f, 0.0f, 0.0f};
        classRepository.pageResult = onePage();
        JsonTransformOptions options = new JsonTransformOptions();

        controller.searchClassesByText(
                "heart disease", PageRequest.of(0, 20), "en", "test_model", "efo", true, options);

        assertEquals("test_model", embeddingServiceClient.lastModel);
        assertEquals("heart disease", embeddingServiceClient.lastText);
        assertArrayEquals(new float[] {0.0f, 1.0f, 0.0f, 0.0f}, classRepository.searchVectorArray);
        assertEquals("test_model", classRepository.searchVectorModel);
        assertEquals("efo", classRepository.searchVectorOntologyId);
        assertTrue(classRepository.searchVectorIncludeCurations);
        assertSame(options, classRepository.searchVectorOutputOpts);
    }

    // ------------------------------------------------------------------
    // GET /ontologies/{onto}/classes/llm_search
    // ------------------------------------------------------------------

    @Test
    void searchClassesByTextInOntologyEmbedsTheQueryThenDelegatesWithThePathOntologyId() throws Exception {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f, 0.0f};
        classRepository.pageResult = onePage();

        controller.searchClassesByTextInOntology(
                "efo", "heart disease", PageRequest.of(0, 20), "en", "test_model", true, false,
                new JsonTransformOptions());

        assertEquals("efo", classRepository.searchVectorInOntologyOntologyId);
        assertArrayEquals(new float[] {1.0f, 0.0f}, classRepository.searchVectorInOntologyArray);
        assertTrue(classRepository.searchVectorInOntologyIsDefiningOntology);
        assertFalse(classRepository.searchVectorInOntologyIncludeCurations);
    }

    // ------------------------------------------------------------------
    // GET /classes/{class}/llm_similar
    // ------------------------------------------------------------------

    @Test
    void getSimilarClassesDecodesTheIriBeforeDelegating() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        JsonTransformOptions options = new JsonTransformOptions();
        classRepository.pageResult = onePage();

        controller.getSimilarClasses(
                pageable, "http%3A%2F%2Fexample.org%2FEFO_0001", "en", "test_model", options);

        assertSame(pageable, classRepository.getSimilarPageable);
        assertEquals("http://example.org/EFO_0001", classRepository.getSimilarIri);
        assertEquals("en", classRepository.getSimilarLang);
        assertEquals("test_model", classRepository.getSimilarModel);
        assertSame(options, classRepository.getSimilarOutputOpts);
    }

    // ------------------------------------------------------------------
    // GET /classes/{class}/llm_embedding
    // ------------------------------------------------------------------

    @Test
    void getClassEmbeddingDecodesTheIriAndReturnsTheVectorAsJson() throws Exception {
        classRepository.embeddingVectorResult = List.of(1.0, 0.0, -2.5);

        HttpEntity<String> response = controller.getClassEmbedding(
                "http%3A%2F%2Fexample.org%2FEFO_0001", "test_model");

        assertEquals("http://example.org/EFO_0001", classRepository.embeddingVectorIri);
        assertEquals("test_model", classRepository.embeddingVectorModel);
        assertEquals("[1.0,0.0,-2.5]", response.getBody());
    }

    // ------------------------------------------------------------------
    // GET /classes/{class}/llm_similarity/{otherclass}
    // ------------------------------------------------------------------

    @Test
    void getClassSimilarityDecodesBothIrisAndReturnsTheScoreAsAString() throws Exception {
        classRepository.similarityResult = 0.5;

        HttpEntity<String> response = controller.getClassSimilarity(
                "http%3A%2F%2Fexample.org%2FEFO_0001", "http%3A%2F%2Fexample.org%2FEFO_0002", "test_model");

        assertEquals("http://example.org/EFO_0001", classRepository.similarityIri);
        assertEquals("http://example.org/EFO_0002", classRepository.similarityIri2);
        assertEquals("test_model", classRepository.similarityModel);
        assertEquals("0.5", response.getBody());
    }

    // ------------------------------------------------------------------
    // GET /properties/llm_search
    // ------------------------------------------------------------------

    @Test
    void searchPropertiesByTextEmbedsTheQueryThenSearchesWithoutAnOntologyFilter() throws Exception {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f, 0.0f};
        postgresClient.pageResult = onePage();

        controller.searchPropertiesByText(
                "part of", PageRequest.of(0, 20), "en", "test_model", null, true, new JsonTransformOptions());

        assertEquals("part of", embeddingServiceClient.lastText);
        assertEquals("OntologyProperty", postgresClient.searchVectorType);
        assertEquals(List.of(1.0, 0.0), postgresClient.searchVectorVector);
        assertFalse(postgresClient.searchVectorInOntologyCalled);
    }

    @Test
    void searchPropertiesByTextUsesTheOntologyScopedQueryWhenOntologyIdIsGiven() throws Exception {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};
        postgresClient.pageResult = onePage();

        controller.searchPropertiesByText(
                "part of", PageRequest.of(0, 20), "en", "test_model", "efo", true, new JsonTransformOptions());

        assertTrue(postgresClient.searchVectorInOntologyCalled);
        assertEquals("OntologyProperty", postgresClient.searchVectorInOntologyType);
        assertEquals("efo", postgresClient.searchVectorInOntologyOntologyId);
    }

    // ------------------------------------------------------------------
    // GET /individuals/llm_search
    // ------------------------------------------------------------------

    @Test
    void searchIndividualsByTextEmbedsTheQueryThenSearchesWithoutAnOntologyFilter() throws Exception {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};
        postgresClient.pageResult = onePage();

        controller.searchIndividualsByText(
                "human", PageRequest.of(0, 20), "en", "test_model", null, true, new JsonTransformOptions());

        assertEquals("human", embeddingServiceClient.lastText);
        assertEquals("OntologyIndividual", postgresClient.searchVectorType);
        assertFalse(postgresClient.searchVectorInOntologyCalled);
    }

    @Test
    void searchIndividualsByTextUsesTheOntologyScopedQueryWhenOntologyIdIsGiven() throws Exception {
        embeddingServiceClient.vectorToReturn = new float[] {1.0f};
        postgresClient.pageResult = onePage();

        controller.searchIndividualsByText(
                "human", PageRequest.of(0, 20), "en", "test_model", "efo", true, new JsonTransformOptions());

        assertTrue(postgresClient.searchVectorInOntologyCalled);
        assertEquals("OntologyIndividual", postgresClient.searchVectorInOntologyType);
    }

    // ------------------------------------------------------------------
    // GET /properties/{property}/llm_similar
    // ------------------------------------------------------------------

    @Test
    void getSimilarPropertiesDecodesTheIriBeforeDelegating() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        JsonTransformOptions options = new JsonTransformOptions();
        propertyRepository.pageResult = onePage();

        controller.getSimilarProperties(
                pageable, "http%3A%2F%2Fexample.org%2FEFO_0100", "en", "test_model", options);

        assertSame(pageable, propertyRepository.getSimilarPageable);
        assertEquals("http://example.org/EFO_0100", propertyRepository.getSimilarIri);
        assertEquals("en", propertyRepository.getSimilarLang);
        assertEquals("test_model", propertyRepository.getSimilarModel);
        assertSame(options, propertyRepository.getSimilarOutputOpts);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Page<JsonElement> onePage(JsonElement... elements) {
        return new PageImpl<>(new ArrayList<>(List.of(elements)), PageRequest.of(0, 20), elements.length);
    }

    private static JsonElement classJson(String iri, String label) {
        JsonObject json = new JsonObject();
        json.addProperty("iri", iri);
        json.addProperty("label", label);
        return JsonParser.parseString(json.toString());
    }

    // ------------------------------------------------------------------
    // Hand-rolled fakes (this repo's unit-test idiom; see V2ClassControllerTest)
    // ------------------------------------------------------------------

    private static class RecordingClassRepository extends ClassRepository {
        private Page<JsonElement> pageResult = onePage();

        private float[] searchVectorArray;
        private String searchVectorModel;
        private Pageable searchVectorPageable;
        private String searchVectorLang;
        private String searchVectorOntologyId;
        private JsonTransformOptions searchVectorOutputOpts;
        private boolean searchVectorIncludeCurations;

        private String searchVectorInOntologyOntologyId;
        private String searchVectorInOntologyModel;
        private float[] searchVectorInOntologyArray;
        private Pageable searchVectorInOntologyPageable;
        private String searchVectorInOntologyLang;
        private boolean searchVectorInOntologyIsDefiningOntology;
        private boolean searchVectorInOntologyIncludeCurations;
        private JsonTransformOptions searchVectorInOntologyOutputOpts;

        private Pageable getSimilarPageable;
        private String getSimilarIri;
        private String getSimilarLang;
        private JsonTransformOptions getSimilarOutputOpts;
        private String getSimilarModel;

        private String embeddingVectorIri;
        private String embeddingVectorModel;
        private List<Double> embeddingVectorResult = List.of();

        private String similarityIri;
        private String similarityIri2;
        private String similarityModel;
        private double similarityResult;

        @Override
        public Page<JsonElement> searchByVector(String modelName, float[] vector, Pageable pageable, String lang,
                String ontologyId, JsonTransformOptions outputOpts, boolean includeCurations) {
            this.searchVectorModel = modelName;
            this.searchVectorArray = vector;
            this.searchVectorPageable = pageable;
            this.searchVectorLang = lang;
            this.searchVectorOntologyId = ontologyId;
            this.searchVectorOutputOpts = outputOpts;
            this.searchVectorIncludeCurations = includeCurations;
            return pageResult;
        }

        @Override
        public Page<JsonElement> searchByVectorInOntology(String ontologyId, String modelName, float[] vector,
                Pageable pageable, String lang, boolean isDefiningOntology, JsonTransformOptions outputOpts,
                boolean includeCurations) {
            this.searchVectorInOntologyOntologyId = ontologyId;
            this.searchVectorInOntologyModel = modelName;
            this.searchVectorInOntologyArray = vector;
            this.searchVectorInOntologyPageable = pageable;
            this.searchVectorInOntologyLang = lang;
            this.searchVectorInOntologyIsDefiningOntology = isDefiningOntology;
            this.searchVectorInOntologyOutputOpts = outputOpts;
            this.searchVectorInOntologyIncludeCurations = includeCurations;
            return pageResult;
        }

        @Override
        public Page<JsonElement> getSimilar(Pageable pageable, String iri, String lang,
                JsonTransformOptions outputOpts, String modelName) {
            this.getSimilarPageable = pageable;
            this.getSimilarIri = iri;
            this.getSimilarLang = lang;
            this.getSimilarOutputOpts = outputOpts;
            this.getSimilarModel = modelName;
            return pageResult;
        }

        @Override
        public List<Double> getEmbeddingVector(String iri, String modelName) {
            this.embeddingVectorIri = iri;
            this.embeddingVectorModel = modelName;
            return embeddingVectorResult;
        }

        @Override
        public double getSimilarity(String iri, String iri2, String modelName) {
            this.similarityIri = iri;
            this.similarityIri2 = iri2;
            this.similarityModel = modelName;
            return similarityResult;
        }
    }

    private static class RecordingPropertyRepository extends PropertyRepository {
        private Page<JsonElement> pageResult = onePage();

        private Pageable getSimilarPageable;
        private String getSimilarIri;
        private String getSimilarLang;
        private JsonTransformOptions getSimilarOutputOpts;
        private String getSimilarModel;

        @Override
        public Page<JsonElement> getSimilar(Pageable pageable, String iri, String lang,
                JsonTransformOptions outputOpts, String modelName) {
            this.getSimilarPageable = pageable;
            this.getSimilarIri = iri;
            this.getSimilarLang = lang;
            this.getSimilarOutputOpts = outputOpts;
            this.getSimilarModel = modelName;
            return pageResult;
        }
    }

    private static class RecordingEmbeddingServiceClient extends EmbeddingServiceClient {
        private List<String> availableModels = List.of();
        private float[] vectorToReturn = new float[] {0.0f};
        private String lastModel;
        private String lastText;

        @Override
        public List<String> getAvailableModels() {
            return availableModels;
        }

        @Override
        public float[] embedText(String model, String text) {
            this.lastModel = model;
            this.lastText = text;
            return vectorToReturn;
        }
    }

    private static class RecordingOlsPostgresClient extends OlsPostgresClient {
        private List<String> embeddingModels = List.of();
        private Page<JsonElement> pageResult = onePage();

        private boolean searchVectorCalled;
        private String searchVectorType;
        private List<Double> searchVectorVector;
        private String searchVectorModel;
        private boolean searchVectorIncludeCurations;

        private boolean searchVectorInOntologyCalled;
        private String searchVectorInOntologyType;
        private String searchVectorInOntologyOntologyId;
        private boolean searchVectorInOntologyIsDefiningOntology;
        private boolean searchVectorInOntologyIncludeCurations;

        @Override
        public List<String> getEmbeddingModels() {
            return embeddingModels;
        }

        @Override
        public Page<JsonElement> searchByVector(String type, List<Double> vector, Pageable pageable,
                String modelName, boolean includeCurations) {
            this.searchVectorCalled = true;
            this.searchVectorType = type;
            this.searchVectorVector = vector;
            this.searchVectorModel = modelName;
            this.searchVectorIncludeCurations = includeCurations;
            return pageResult;
        }

        @Override
        public Page<JsonElement> searchByVectorInOntology(String type, List<Double> vector, Pageable pageable,
                String modelName, String ontologyId, boolean isDefiningOntology, boolean includeCurations) {
            this.searchVectorInOntologyCalled = true;
            this.searchVectorInOntologyType = type;
            this.searchVectorInOntologyOntologyId = ontologyId;
            this.searchVectorInOntologyIsDefiningOntology = isDefiningOntology;
            this.searchVectorInOntologyIncludeCurations = includeCurations;
            return pageResult;
        }
    }
}
