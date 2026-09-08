package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.ols.config.WebConfig;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.controller.api.exception.ResourceNotFoundException;
import uk.ac.ebi.spot.ols.repository.ClassRepository;
import uk.ac.ebi.spot.ols.repository.PropertyRepository;
import uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient;
import uk.ac.ebi.spot.ols.service.EmbeddingServiceClient;

import java.net.URI;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-integration coverage for every route on {@link V2LLMController}: real Spring MVC routing,
 * binding, defaults, and exception handling, with {@link ClassRepository}, {@link PropertyRepository},
 * {@link EmbeddingServiceClient}, and {@link OlsPostgresClient} mocked. All 12 routes are enumerated
 * explicitly per this programme's rule against substituting a "representative" subset (see
 * {@code docs/backend-testing-strategy.md}'s parameter-testing standard).
 */
@WebMvcTest(V2LLMController.class)
@ContextConfiguration(classes = {
        V2LLMController.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V2LLMControllerWIT {

    private static final String CLASS_IRI = "http://example.org/EFO_0001";
    private static final String OTHER_CLASS_IRI = "http://example.org/EFO_0002";
    private static final String PROPERTY_IRI = "http://example.org/EFO_0100";
    // Double-encoded per the controller's documented contract (Spring/Tomcat decodes the path
    // segment once; the handler itself calls UriUtils.decode a second time). Built as URI objects
    // (not plain strings passed to the get(String) overload, which re-encodes/normalizes its
    // template and silently drops a decode level) -- the same idiom V2ClassControllerWIT/IT use for
    // their own double-encoded {class} path variable.
    private static final String CLASS_IRI_PATH = "http%253A%252F%252Fexample.org%252FEFO_0001";
    private static final String OTHER_CLASS_IRI_PATH = "http%253A%252F%252Fexample.org%252FEFO_0002";
    private static final String PROPERTY_IRI_PATH = "http%253A%252F%252Fexample.org%252FEFO_0100";
    private static final URI CLASS_SIMILAR_URI = URI.create("/api/v2/classes/" + CLASS_IRI_PATH + "/llm_similar");
    private static final URI CLASS_EMBEDDING_URI =
            URI.create("/api/v2/classes/" + CLASS_IRI_PATH + "/llm_embedding");
    private static final URI CLASS_SIMILARITY_URI = URI.create(
            "/api/v2/classes/" + CLASS_IRI_PATH + "/llm_similarity/" + OTHER_CLASS_IRI_PATH);
    private static final URI PROPERTY_SIMILAR_URI =
            URI.create("/api/v2/properties/" + PROPERTY_IRI_PATH + "/llm_similar");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClassRepository classRepository;

    @MockitoBean
    private PropertyRepository propertyRepository;

    @MockitoBean
    private EmbeddingServiceClient embeddingServiceClient;

    @MockitoBean
    private OlsPostgresClient postgresClient;

    // ------------------------------------------------------------------
    // GET /llm_models
    // ------------------------------------------------------------------

    @Test
    void llmModelsReturnsModelsWithCanEmbedFlags() throws Exception {
        when(embeddingServiceClient.getAvailableModels()).thenReturn(List.of("model-a"));
        when(postgresClient.getEmbeddingModels()).thenReturn(List.of("model-a", "model-b"));

        mockMvc.perform(get("/api/v2/llm_models"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].model").value("model-a"))
                .andExpect(jsonPath("$[0].can_embed").value(true))
                .andExpect(jsonPath("$[1].model").value("model-b"))
                .andExpect(jsonPath("$[1].can_embed").value(false));
    }

    @Test
    void llmModelsRouteReturnsMethodNotAllowedForUnsupportedMethod() throws Exception {
        mockMvc.perform(post("/api/v2/llm_models"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405));
    }

    // ------------------------------------------------------------------
    // POST /classes/llm_embedding
    // ------------------------------------------------------------------

    @Test
    void searchClassesByVectorUsesDefaultsAndConvertsTheBodyToAFloatArray() throws Exception {
        when(classRepository.searchByVector(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(classPage());

        mockMvc.perform(post("/api/v2/classes/llm_embedding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1.0,0.0,-2.5]")
                        .param("model", "test_model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[0].iri").value(CLASS_IRI));

        verify(classRepository).searchByVector(
                eq("test_model"),
                eq(new float[] {1.0f, 0.0f, -2.5f}),
                argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 20),
                eq("en"),
                eq(null),
                any(),
                eq(true));
    }

    @Test
    void searchClassesByVectorBindsEveryParameter() throws Exception {
        when(classRepository.searchByVector(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(classPage());

        mockMvc.perform(post("/api/v2/classes/llm_embedding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1.0,2.0]")
                        .param("page", "1")
                        .param("size", "5")
                        .param("lang", "fr")
                        .param("model", "test_model")
                        .param("ontologyId", "efo")
                        .param("includeCurations", "false"))
                .andExpect(status().isOk());

        verify(classRepository).searchByVector(
                eq("test_model"),
                eq(new float[] {1.0f, 2.0f}),
                argThat(p -> p.getPageNumber() == 1 && p.getPageSize() == 5),
                eq("fr"),
                eq("efo"),
                any(),
                eq(false));
    }

    @Test
    void searchClassesByVectorRequiresTheModelParameter() throws Exception {
        mockMvc.perform(post("/api/v2/classes/llm_embedding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1.0]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void searchClassesByVectorRejectsMalformedIncludeCurations() throws Exception {
        mockMvc.perform(post("/api/v2/classes/llm_embedding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1.0]")
                        .param("model", "test_model")
                        .param("includeCurations", "not-a-boolean"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Method parameter 'includeCurations': Failed to convert value of type "
                                + "'java.lang.String' to required type 'boolean'; "
                                + "Invalid boolean value [not-a-boolean]"));
    }

    // ------------------------------------------------------------------
    // POST /ontologies/{onto}/classes/llm_embedding
    // ------------------------------------------------------------------

    @Test
    void searchClassesByVectorInOntologyUsesDefaultsAndBindsThePathOntologyId() throws Exception {
        when(classRepository.searchByVectorInOntology(
                any(), any(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                .thenReturn(classPage());

        mockMvc.perform(post("/api/v2/ontologies/efo/classes/llm_embedding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1.0,0.0]")
                        .param("model", "test_model"))
                .andExpect(status().isOk());

        verify(classRepository).searchByVectorInOntology(
                eq("efo"),
                eq("test_model"),
                eq(new float[] {1.0f, 0.0f}),
                argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 20),
                eq("en"),
                eq(false),
                any(),
                eq(true));
    }

    @Test
    void searchClassesByVectorInOntologyBindsIsDefiningOntologyAndIncludeCurations() throws Exception {
        when(classRepository.searchByVectorInOntology(
                any(), any(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                .thenReturn(classPage());

        mockMvc.perform(post("/api/v2/ontologies/efo/classes/llm_embedding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1.0]")
                        .param("model", "test_model")
                        .param("isDefiningOntology", "true")
                        .param("includeCurations", "false"))
                .andExpect(status().isOk());

        verify(classRepository).searchByVectorInOntology(
                eq("efo"), eq("test_model"), eq(new float[] {1.0f}), any(), eq("en"),
                eq(true), any(), eq(false));
    }

    @Test
    void searchClassesByVectorInOntologyRequiresTheModelParameter() throws Exception {
        mockMvc.perform(post("/api/v2/ontologies/efo/classes/llm_embedding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1.0]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchClassesByVectorInOntologyRejectsMalformedIsDefiningOntology() throws Exception {
        mockMvc.perform(post("/api/v2/ontologies/efo/classes/llm_embedding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1.0]")
                        .param("model", "test_model")
                        .param("isDefiningOntology", "not-a-boolean"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Method parameter 'isDefiningOntology': Failed to convert value of type "
                                + "'java.lang.String' to required type 'boolean'; "
                                + "Invalid boolean value [not-a-boolean]"));
    }

    // ------------------------------------------------------------------
    // GET /entities/llm_search
    // ------------------------------------------------------------------

    @Test
    void searchEntitiesByTextEmbedsTheQueryAndSearchesWithoutAnOntologyFilterByDefault() throws Exception {
        when(embeddingServiceClient.embedText(any(), any())).thenReturn(new float[] {1.0f, 0.0f});
        when(postgresClient.searchByVector(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(classPage());

        mockMvc.perform(get("/api/v2/entities/llm_search")
                        .param("q", "liver")
                        .param("model", "test_model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[0].iri").value(CLASS_IRI));

        verify(embeddingServiceClient).embedText("test_model", "liver");
        verify(postgresClient).searchByVector(
                eq("OntologyEntity"), eq(List.of(1.0, 0.0)), any(), eq("test_model"), eq(true));
        verify(postgresClient, never()).searchByVectorInOntology(
                any(), any(), any(), any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void searchEntitiesByTextUsesTheOntologyScopedQueryWhenOntologyIdIsGiven() throws Exception {
        when(embeddingServiceClient.embedText(any(), any())).thenReturn(new float[] {1.0f});
        when(postgresClient.searchByVectorInOntology(
                any(), any(), any(), any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(classPage());

        mockMvc.perform(get("/api/v2/entities/llm_search")
                        .param("q", "liver")
                        .param("model", "test_model")
                        .param("ontologyId", "efo")
                        .param("includeCurations", "false"))
                .andExpect(status().isOk());

        verify(postgresClient).searchByVectorInOntology(
                eq("OntologyEntity"), eq(List.of(1.0)), any(), eq("test_model"), eq("efo"), eq(true), eq(false));
    }

    @Test
    void entitiesSearchRequiresQ() throws Exception {
        mockMvc.perform(get("/api/v2/entities/llm_search").param("model", "test_model"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void entitiesSearchRequiresModel() throws Exception {
        mockMvc.perform(get("/api/v2/entities/llm_search").param("q", "liver"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // GET /classes/llm_search
    // ------------------------------------------------------------------

    @Test
    void searchClassesByTextEmbedsTheQueryAndDelegatesToTheClassRepository() throws Exception {
        when(embeddingServiceClient.embedText(any(), any())).thenReturn(new float[] {0.0f, 1.0f});
        when(classRepository.searchByVector(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(classPage());

        mockMvc.perform(get("/api/v2/classes/llm_search")
                        .param("q", "heart disease")
                        .param("model", "test_model")
                        .param("ontologyId", "efo"))
                .andExpect(status().isOk());

        verify(embeddingServiceClient).embedText("test_model", "heart disease");
        verify(classRepository).searchByVector(
                eq("test_model"), eq(new float[] {0.0f, 1.0f}), any(), eq("en"), eq("efo"), any(), eq(true));
    }

    @Test
    void classesSearchRequiresQ() throws Exception {
        mockMvc.perform(get("/api/v2/classes/llm_search").param("model", "test_model"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void classesSearchRequiresModel() throws Exception {
        mockMvc.perform(get("/api/v2/classes/llm_search").param("q", "heart disease"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // GET /ontologies/{onto}/classes/llm_search
    // ------------------------------------------------------------------

    @Test
    void searchClassesByTextInOntologyEmbedsTheQueryAndBindsThePathOntologyIdAndIsDefiningOntology()
            throws Exception {
        when(embeddingServiceClient.embedText(any(), any())).thenReturn(new float[] {1.0f});
        when(classRepository.searchByVectorInOntology(
                any(), any(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                .thenReturn(classPage());

        mockMvc.perform(get("/api/v2/ontologies/efo/classes/llm_search")
                        .param("q", "heart disease")
                        .param("model", "test_model")
                        .param("isDefiningOntology", "true"))
                .andExpect(status().isOk());

        verify(classRepository).searchByVectorInOntology(
                eq("efo"), eq("test_model"), eq(new float[] {1.0f}), any(), eq("en"), eq(true), any(), eq(true));
    }

    @Test
    void ontologyClassesSearchRequiresQ() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/classes/llm_search").param("model", "test_model"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ontologyClassesSearchRequiresModel() throws Exception {
        mockMvc.perform(get("/api/v2/ontologies/efo/classes/llm_search").param("q", "heart disease"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // GET /classes/{class}/llm_similar
    // ------------------------------------------------------------------

    @Test
    void getSimilarClassesUsesTheDefaultModelAndDecodesTheIri() throws Exception {
        when(classRepository.getSimilar(any(), any(), any(), any(), any())).thenReturn(classPage());

        mockMvc.perform(get(CLASS_SIMILAR_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[0].iri").value(CLASS_IRI));

        verify(classRepository).getSimilar(
                argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 20),
                eq(CLASS_IRI), eq("en"), any(), eq("text-embedding-3-small"));
    }

    @Test
    void getSimilarClassesBindsExplicitModelLangAndPagination() throws Exception {
        when(classRepository.getSimilar(any(), any(), any(), any(), any())).thenReturn(classPage());

        mockMvc.perform(get(CLASS_SIMILAR_URI)
                        .param("page", "2").param("size", "3")
                        .param("lang", "fr").param("model", "test_model"))
                .andExpect(status().isOk());

        verify(classRepository).getSimilar(
                argThat(p -> p.getPageNumber() == 2 && p.getPageSize() == 3),
                eq(CLASS_IRI), eq("fr"), any(), eq("test_model"));
    }

    @Test
    void getSimilarClassesReturnsStableNotFoundWhenTheSourceClassIsMissing() throws Exception {
        when(classRepository.getSimilar(any(), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("entity not found"));

        mockMvc.perform(get(CLASS_SIMILAR_URI))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("entity not found"));
    }

    // ------------------------------------------------------------------
    // GET /classes/{class}/llm_embedding
    // ------------------------------------------------------------------

    @Test
    void getClassEmbeddingUsesTheDefaultModelAndReturnsTheVectorAsJson() throws Exception {
        when(classRepository.getEmbeddingVector(CLASS_IRI, "text-embedding-3-small"))
                .thenReturn(List.of(1.0, 0.0, -2.5));

        mockMvc.perform(get(CLASS_EMBEDDING_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("[1.0,0.0,-2.5]"));
    }

    @Test
    void getClassEmbeddingBindsAnExplicitModel() throws Exception {
        when(classRepository.getEmbeddingVector(CLASS_IRI, "test_model")).thenReturn(List.of(1.0));

        mockMvc.perform(get(CLASS_EMBEDDING_URI)
                        .param("model", "test_model"))
                .andExpect(status().isOk())
                .andExpect(content().string("[1.0]"));
    }

    @Test
    void getClassEmbeddingReturnsStableNotFoundWhenMissing() throws Exception {
        when(classRepository.getEmbeddingVector(any(), any()))
                .thenThrow(new ResourceNotFoundException("entity not found"));

        mockMvc.perform(get(CLASS_EMBEDDING_URI))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("entity not found"));
    }

    // ------------------------------------------------------------------
    // GET /classes/{class}/llm_similarity/{otherclass}
    // ------------------------------------------------------------------

    @Test
    void getClassSimilarityDecodesBothIrisAndReturnsTheScoreAsAString() throws Exception {
        when(classRepository.getSimilarity(CLASS_IRI, OTHER_CLASS_IRI, "text-embedding-3-small"))
                .thenReturn(0.5);

        mockMvc.perform(get(CLASS_SIMILARITY_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("0.5"));
    }

    @Test
    void getClassSimilarityBindsAnExplicitModel() throws Exception {
        when(classRepository.getSimilarity(CLASS_IRI, OTHER_CLASS_IRI, "test_model")).thenReturn(1.0);

        mockMvc.perform(get(CLASS_SIMILARITY_URI)
                        .param("model", "test_model"))
                .andExpect(status().isOk())
                .andExpect(content().string("1.0"));
    }

    @Test
    void getClassSimilarityReturnsStableNotFoundWhenEitherClassIsMissing() throws Exception {
        when(classRepository.getSimilarity(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("entity not found"));

        mockMvc.perform(get(CLASS_SIMILARITY_URI))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ------------------------------------------------------------------
    // GET /properties/llm_search
    // ------------------------------------------------------------------

    @Test
    void searchPropertiesByTextEmbedsTheQueryAndSearchesWithoutAnOntologyFilterByDefault() throws Exception {
        when(embeddingServiceClient.embedText(any(), any())).thenReturn(new float[] {1.0f, 0.0f});
        when(postgresClient.searchByVector(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(propertyPage());

        mockMvc.perform(get("/api/v2/properties/llm_search")
                        .param("q", "part of")
                        .param("model", "test_model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[0].iri").value(PROPERTY_IRI));

        verify(postgresClient).searchByVector(
                eq("OntologyProperty"), eq(List.of(1.0, 0.0)), any(), eq("test_model"), eq(true));
    }

    @Test
    void searchPropertiesByTextUsesTheOntologyScopedQueryWhenOntologyIdIsGiven() throws Exception {
        when(embeddingServiceClient.embedText(any(), any())).thenReturn(new float[] {1.0f});
        when(postgresClient.searchByVectorInOntology(
                any(), any(), any(), any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(propertyPage());

        mockMvc.perform(get("/api/v2/properties/llm_search")
                        .param("q", "part of")
                        .param("model", "test_model")
                        .param("ontologyId", "efo"))
                .andExpect(status().isOk());

        verify(postgresClient).searchByVectorInOntology(
                eq("OntologyProperty"), eq(List.of(1.0)), any(), eq("test_model"), eq("efo"), eq(true), eq(true));
    }

    @Test
    void propertiesSearchRequiresQ() throws Exception {
        mockMvc.perform(get("/api/v2/properties/llm_search").param("model", "test_model"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void propertiesSearchRequiresModel() throws Exception {
        mockMvc.perform(get("/api/v2/properties/llm_search").param("q", "part of"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // GET /individuals/llm_search
    // ------------------------------------------------------------------

    @Test
    void searchIndividualsByTextEmbedsTheQueryAndSearchesWithoutAnOntologyFilterByDefault() throws Exception {
        when(embeddingServiceClient.embedText(any(), any())).thenReturn(new float[] {1.0f});
        when(postgresClient.searchByVector(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(classPage());

        mockMvc.perform(get("/api/v2/individuals/llm_search")
                        .param("q", "human")
                        .param("model", "test_model"))
                .andExpect(status().isOk());

        verify(postgresClient).searchByVector(
                eq("OntologyIndividual"), eq(List.of(1.0)), any(), eq("test_model"), eq(true));
    }

    @Test
    void searchIndividualsByTextUsesTheOntologyScopedQueryWhenOntologyIdIsGiven() throws Exception {
        when(embeddingServiceClient.embedText(any(), any())).thenReturn(new float[] {1.0f});
        when(postgresClient.searchByVectorInOntology(
                any(), any(), any(), any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(classPage());

        mockMvc.perform(get("/api/v2/individuals/llm_search")
                        .param("q", "human")
                        .param("model", "test_model")
                        .param("ontologyId", "efo")
                        .param("includeCurations", "false"))
                .andExpect(status().isOk());

        verify(postgresClient).searchByVectorInOntology(
                eq("OntologyIndividual"), eq(List.of(1.0)), any(), eq("test_model"), eq("efo"), eq(true), eq(false));
    }

    @Test
    void individualsSearchRequiresQ() throws Exception {
        mockMvc.perform(get("/api/v2/individuals/llm_search").param("model", "test_model"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void individualsSearchRequiresModel() throws Exception {
        mockMvc.perform(get("/api/v2/individuals/llm_search").param("q", "human"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // GET /properties/{property}/llm_similar
    // ------------------------------------------------------------------

    @Test
    void getSimilarPropertiesUsesTheDefaultModelAndDecodesTheIri() throws Exception {
        when(propertyRepository.getSimilar(any(), any(), any(), any(), any())).thenReturn(propertyPage());

        mockMvc.perform(get(PROPERTY_SIMILAR_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[0].iri").value(PROPERTY_IRI));

        verify(propertyRepository).getSimilar(
                argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 20),
                eq(PROPERTY_IRI), eq("en"), any(), eq("text-embedding-3-small"));
    }

    @Test
    void getSimilarPropertiesBindsExplicitModelLangAndPagination() throws Exception {
        when(propertyRepository.getSimilar(any(), any(), any(), any(), any())).thenReturn(propertyPage());

        mockMvc.perform(get(PROPERTY_SIMILAR_URI)
                        .param("page", "1").param("size", "4")
                        .param("lang", "fr").param("model", "test_model"))
                .andExpect(status().isOk());

        verify(propertyRepository).getSimilar(
                argThat(p -> p.getPageNumber() == 1 && p.getPageSize() == 4),
                eq(PROPERTY_IRI), eq("fr"), any(), eq("test_model"));
    }

    @Test
    void getSimilarPropertiesReturnsStableNotFoundWhenTheSourcePropertyIsMissing() throws Exception {
        when(propertyRepository.getSimilar(any(), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("entity not found"));

        mockMvc.perform(get(PROPERTY_SIMILAR_URI))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("entity not found"));
    }

    // ------------------------------------------------------------------
    // Cross-route: malformed includeCurations rejected the same way everywhere it is declared
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v2/entities/llm_search",
            "/api/v2/classes/llm_search",
            "/api/v2/ontologies/efo/classes/llm_search",
            "/api/v2/properties/llm_search",
            "/api/v2/individuals/llm_search"
    })
    void rejectsMalformedIncludeCurationsOnEveryTextSearchRoute(String uri) throws Exception {
        mockMvc.perform(get(uri)
                        .param("q", "liver")
                        .param("model", "test_model")
                        .param("includeCurations", "not-a-boolean"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Method parameter 'includeCurations': Failed to convert value of type "
                                + "'java.lang.String' to required type 'boolean'; "
                                + "Invalid boolean value [not-a-boolean]"));
    }

    // ------------------------------------------------------------------
    // Cross-route: pagination boundary normalization for every paginated GET route
    // ------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({"page, not-a-number", "size, not-a-number"})
    void similarClassesUsesPaginationDefaultsForMalformedNumericValues(String parameter, String value)
            throws Exception {
        when(classRepository.getSimilar(any(), any(), any(), any(), any())).thenReturn(classPage());

        mockMvc.perform(get(CLASS_SIMILAR_URI).param(parameter, value))
                .andExpect(status().isOk());

        verify(classRepository).getSimilar(
                argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 20),
                eq(CLASS_IRI), eq("en"), any(), eq("text-embedding-3-small"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Page<JsonElement> classPage() {
        return new PageImpl<>(List.of(entityJson(CLASS_IRI, "Liver disease")));
    }

    private static Page<JsonElement> propertyPage() {
        return new PageImpl<>(List.of(entityJson(PROPERTY_IRI, "has specimen")));
    }

    private static JsonElement entityJson(String iri, String label) {
        JsonObject json = new JsonObject();
        json.addProperty("iri", iri);
        json.addProperty("label", label);
        return JsonParser.parseString(json.toString());
    }
}
