package uk.ac.ebi.spot.ols.repository.postgres;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import uk.ac.ebi.spot.ols.controller.api.exception.ResourceNotFoundException;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport.OlsPostgresClientRepositoryHandle;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport.OLS_POSTGRES_CLIENT_EMBEDDING_MODEL;

/**
 * Real-Postgres coverage of {@link OlsPostgresClient}'s embedding/similarity/vector-search family.
 * This is the "milestone 3 of 3" (final) slice of this Tier B target (see
 * {@code docs/backend-testing-strategy.md}'s "Implemented OlsPostgresClient" baseline for the full
 * milestone split; milestone 1's {@code OlsPostgresClientIT} covers the static-logic/{@code getAll}/
 * {@code getOne} slice, milestone 2's {@code OlsPostgresClientGraphIT} covers the graph-traversal
 * family): {@code getSimilar}, {@code getSimilarity}, {@code getEmbeddingVector},
 * {@code searchByVector} (both overloads), {@code searchByVectorInOntology} (both overloads), and
 * {@code getEmbeddingModels}.
 *
 * <p>This class alone already had substantial <em>indirect</em> coverage from
 * {@code V2LLMController}/{@code EmbeddingServiceClient}/Mcp*Service IT suites (~87% lines
 * documented in the "V2 LLM-controller baseline" section) -- but only through those callers' own
 * happy-path fixtures. None of them use an invalid model name, an obsolete source entity, a
 * cross-ontology duplicate IRI+type, or the 4-/6-arg convenience overloads at all: every existing
 * caller (`ClassRepository`, `McpClassService`, `McpEmbeddingService`, `V2LLMController` itself)
 * passes `includeCurations` explicitly, per that baseline's own dead-code note. This class's own
 * dedicated fixture ({@link PostgresIntegrationTestSupport#initializeOlsPostgresClientEmbeddingDatabase})
 * is built specifically to reach those overloads and edge cases directly -- see that method's
 * Javadoc for exactly which row is which and why.
 */
class OlsPostgresClientEmbeddingIT {

    private static PostgreSQLContainer<?> container;
    private static OlsPostgresClientRepositoryHandle handle;
    private static OlsPostgresClient client;

    @BeforeAll
    static void setUpDatabase() {
        container = PostgresIntegrationTestSupport.newContainer();
        container.start();
        PostgresIntegrationTestSupport.initializeOlsPostgresClientEmbeddingDatabase(container);
        handle = PostgresIntegrationTestSupport.createOlsPostgresClientRepositories(container);
        client = handle.olsPostgresClient();
    }

    @AfterAll
    static void tearDownDatabase() {
        if (handle != null) {
            handle.close();
        }
        if (container != null) {
            container.stop();
        }
    }

    private static final String EMB_SOURCE_IRI = "http://example.org/EMB_SOURCE";
    private static final String EMB_IDENTICAL_DIR_ID = "embtest+embclass+http://example.org/EMB_IDENTICAL_DIR";
    private static final String EMB_DIAG_ID = "embtest+embclass+http://example.org/EMB_DIAG";
    private static final String EMB_DIAG_IRI = "http://example.org/EMB_DIAG";
    private static final String EMB_ORTHO_ID = "embtest+embclass+http://example.org/EMB_ORTHO";
    private static final String EMB_ORTHO_IRI = "http://example.org/EMB_ORTHO";
    private static final String EMB_OPPOSITE_ID = "embtest+embclass+http://example.org/EMB_OPPOSITE";
    private static final String EMB_OBSOLETE_ONLY_IRI = "http://example.org/EMB_OBSOLETE_ONLY";
    private static final String EMB_NO_EMBEDDING_IRI = "http://example.org/EMB_NO_EMBEDDING";
    private static final String EMB_NO_EMBEDDING_PARTNER_IRI = "http://example.org/EMB_NO_EMBEDDING_PARTNER";
    private static final String EMB_VECTOR_PARSE_IRI = "http://example.org/EMB_VECTOR_PARSE";
    private static final String EMB_DUP_IRI = "http://example.org/EMB_DUP";
    private static final String EMB_DUP_NON_DEFINING_ID = "embtest+embdup+http://example.org/EMB_DUP";

    private static final String VEC_CLASS_A_ID = "vectest+vecclass+http://example.org/VEC_CLASS_A";
    private static final String VEC_CLASS_B_ID = "vectest+vecclass+http://example.org/VEC_CLASS_B";
    private static final String VEC_PROPERTY_A_ID = "vectest+vecproperty+http://example.org/VEC_PROPERTY_A";
    private static final String VEC_ONTO_CLASS_DEFINING_ID = "vectestonto+vecontoclass+http://example.org/VEC_ONTO_CLASS";
    private static final String VEC_ONTO_CLASS_TARGET_ID = "vectestonto2+vecontoclass+http://example.org/VEC_ONTO_CLASS";
    private static final String VEC_ONTO_PROPERTY_DEFINING_ID = "vectestonto+vecontoproperty+http://example.org/VEC_ONTO_PROPERTY";
    private static final String VEC_ONTO_PROPERTY_TARGET_ID = "vectestonto2+vecontoproperty+http://example.org/VEC_ONTO_PROPERTY";

    private static final PageRequest ANY_PAGEABLE = PageRequest.of(0, 10);

    private static List<String> ids(Page<JsonElement> page) {
        return page.getContent().stream()
                .map(e -> e.getAsJsonObject().get("id").getAsString())
                .toList();
    }

    private static double score(Page<JsonElement> page, String id) {
        return page.getContent().stream()
                .filter(e -> e.getAsJsonObject().get("id").getAsString().equals(id))
                .findFirst()
                .orElseThrow()
                .getAsJsonObject().get("score").getAsDouble();
    }

    // ------------------------------------------------------------------
    // getSimilar
    // ------------------------------------------------------------------

    @Test
    void getSimilarOrdersResultsByCosineSimilarityWithExactScores() {
        Page<JsonElement> page = client.getSimilar("EmbClass", EMB_SOURCE_IRI, ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL);

        assertThat(ids(page)).containsExactly(EMB_IDENTICAL_DIR_ID, EMB_DIAG_ID, EMB_ORTHO_ID, EMB_OPPOSITE_ID);
        assertThat(score(page, EMB_IDENTICAL_DIR_ID)).isEqualTo(1.0);
        assertThat(score(page, EMB_DIAG_ID)).isEqualTo(0.9);
        assertThat(score(page, EMB_ORTHO_ID)).isEqualTo(0.5);
        assertThat(score(page, EMB_OPPOSITE_ID)).isEqualTo(0.0);
    }

    @Test
    void getSimilarExcludesTheSourceEntityItselfFromResults() {
        Page<JsonElement> page = client.getSimilar("EmbClass", EMB_SOURCE_IRI, ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL);

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(ids(page)).doesNotContain("embtest+embclass+http://example.org/EMB_SOURCE");
    }

    @Test
    void getSimilarThrowsResourceNotFoundExceptionWhenNoMatchingEntityExists() {
        assertThatThrownBy(() -> client.getSimilar(
                "EmbClass", "http://example.org/DOES_NOT_EXIST", ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getSimilarThrowsResourceNotFoundExceptionWhenTheOnlyMatchingEntityIsObsolete() {
        // EMB_OBSOLETE_ONLY has a real embedding, but the source lookup requires is_obsolete = false.
        assertThatThrownBy(() -> client.getSimilar(
                "EmbObsoleteSrc", EMB_OBSOLETE_ONLY_IRI, ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getSimilarPrefersTheDefiningOntologyRowAsTheSourceEntity() {
        // EMB_DUP exists in both embtest (non-defining) and embtest2 (defining), same IRI+type.
        // The source lookup must pick the defining (embtest2) row, which is then excluded from the
        // result set by id -- leaving exactly the non-defining (embtest) row as the sole candidate.
        Page<JsonElement> page = client.getSimilar("EmbDup", EMB_DUP_IRI, ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL);

        assertThat(ids(page)).containsExactly(EMB_DUP_NON_DEFINING_ID);
    }

    // ------------------------------------------------------------------
    // getSimilarity
    // ------------------------------------------------------------------

    @Test
    void getSimilarityReturnsTheExactCosineSimilarityScoreForAFoundPair() {
        double similarity = client.getSimilarity(
                "EmbClass", EMB_SOURCE_IRI, EMB_ORTHO_IRI, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL);

        assertThat(similarity).isEqualTo(0.5);
    }

    @Test
    void getSimilarityThrowsWhenOneIriDoesNotExist() {
        assertThatThrownBy(() -> client.getSimilarity(
                "EmbClass", EMB_SOURCE_IRI, "http://example.org/DOES_NOT_EXIST", OLS_POSTGRES_CLIENT_EMBEDDING_MODEL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getSimilarityThrowsWhenTheFirstEntityHasNoEmbeddingValue() {
        // Both entities share type EmbNoEmbedding (unlike EMB_SOURCE, type EmbClass) so this
        // isolates "the first entity has no embedding value" from an unrelated type mismatch.
        assertThatThrownBy(() -> client.getSimilarity(
                "EmbNoEmbedding", EMB_NO_EMBEDDING_IRI, EMB_NO_EMBEDDING_PARTNER_IRI, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getSimilarityThrowsWhenTheSecondEntityHasNoEmbeddingValue() {
        assertThatThrownBy(() -> client.getSimilarity(
                "EmbNoEmbedding", EMB_NO_EMBEDDING_PARTNER_IRI, EMB_NO_EMBEDDING_IRI, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // getEmbeddingVector
    // ------------------------------------------------------------------

    @Test
    void getEmbeddingVectorParsesTheBracketedCommaSeparatedVectorExactly() {
        // [-1.5,2,0,12]: a leading negative decimal and a trailing two-digit integer -- chosen so
        // an off-by-one in the bracket-stripping substring bounds would visibly corrupt either end.
        List<Double> vector = client.getEmbeddingVector("EmbVectorParse", EMB_VECTOR_PARSE_IRI, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL);

        assertThat(vector).containsExactly(-1.5, 2.0, 0.0, 12.0);
    }

    @Test
    void getEmbeddingVectorThrowsWhenTheEntityDoesNotExist() {
        assertThatThrownBy(() -> client.getEmbeddingVector(
                "EmbClass", "http://example.org/DOES_NOT_EXIST", OLS_POSTGRES_CLIENT_EMBEDDING_MODEL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getEmbeddingVectorThrowsWhenTheEmbeddingValueIsNull() {
        assertThatThrownBy(() -> client.getEmbeddingVector(
                "EmbNoEmbedding", EMB_NO_EMBEDDING_IRI, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // searchByVector
    // ------------------------------------------------------------------

    @Test
    void searchByVectorFourArgOverloadIncludesCurationsByDefault() {
        // VEC_CLASS_B's CurationEmbedding ([4,3,0,0], score 0.9) beats its own LabelEmbedding
        // ([0,1,0,0], score 0.5) -- proving the 4-arg overload's hardcoded includeCurations=true
        // default is genuinely reached and not just assumed.
        Page<JsonElement> page = client.searchByVector(
                "VecClass", List.of(1.0, 0.0, 0.0, 0.0), ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL);

        assertThat(ids(page)).containsExactly(VEC_CLASS_A_ID, VEC_CLASS_B_ID);
        assertThat(score(page, VEC_CLASS_A_ID)).isEqualTo(1.0);
        assertThat(score(page, VEC_CLASS_B_ID)).isEqualTo(0.9);
    }

    @Test
    void searchByVectorFiveArgOverloadExcludesCurationsWhenFalse() {
        Page<JsonElement> page = client.searchByVector(
                "VecClass", List.of(1.0, 0.0, 0.0, 0.0), ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, false);

        assertThat(ids(page)).containsExactly(VEC_CLASS_A_ID, VEC_CLASS_B_ID);
        assertThat(score(page, VEC_CLASS_A_ID)).isEqualTo(1.0);
        assertThat(score(page, VEC_CLASS_B_ID)).isEqualTo(0.5);
    }

    @Test
    void searchByVectorFiveArgOverloadIncludesCurationsWhenTrue() {
        Page<JsonElement> page = client.searchByVector(
                "VecClass", List.of(1.0, 0.0, 0.0, 0.0), ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, true);

        assertThat(score(page, VEC_CLASS_B_ID)).isEqualTo(0.9);
    }

    @Test
    void searchByVectorFiltersOutADifferentConcreteEntityType() {
        Page<JsonElement> page = client.searchByVector(
                "VecClass", List.of(1.0, 0.0, 0.0, 0.0), ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, false);

        assertThat(ids(page)).doesNotContain(VEC_PROPERTY_A_ID);
    }

    @Test
    void searchByVectorWithTheGenericOntologyEntityTypeDoesNotFilterByType() {
        // "OntologyEntity" disables the type filter entirely, so VEC_PROPERTY_A (a different
        // concrete type than the "VecClass" used elsewhere in this fixture group) is found here,
        // unlike in searchByVectorFiltersOutADifferentConcreteEntityType above. searchByVector has
        // no ontology scoping at all, so this also legitimately surfaces the separate
        // searchByVectorInOntology fixture group's rows -- this test only asserts containment, not
        // an exact total, for that reason.
        Page<JsonElement> page = client.searchByVector(
                "OntologyEntity", List.of(1.0, 0.0, 0.0, 0.0), ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, false);

        assertThat(ids(page)).contains(VEC_CLASS_A_ID, VEC_PROPERTY_A_ID, VEC_CLASS_B_ID);
    }

    // ------------------------------------------------------------------
    // searchByVectorInOntology
    // ------------------------------------------------------------------

    @Test
    void searchByVectorInOntologyDefiningTrueFindsTheDefiningOntologyRowDirectly() {
        Page<JsonElement> page = client.searchByVectorInOntology(
                "VecOntoClass", List.of(1.0, 0.0, 0.0, 0.0), ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL,
                "vectestonto", true, false);

        assertThat(ids(page)).containsExactly(VEC_ONTO_CLASS_DEFINING_ID);
    }

    @Test
    void searchByVectorInOntologyDefiningFalseJoinsThroughToTheTargetOntologyRow() {
        // isDefiningOntology = false must return vectestonto2's own VEC_ONTO_CLASS row (the
        // "target"), found by joining through vectestonto's (the "defining" ontology's) embedding
        // node -- not the defining row itself.
        Page<JsonElement> page = client.searchByVectorInOntology(
                "VecOntoClass", List.of(1.0, 0.0, 0.0, 0.0), ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL,
                "vectestonto2", false, false);

        assertThat(ids(page)).containsExactly(VEC_ONTO_CLASS_TARGET_ID);
    }

    @Test
    void searchByVectorInOntologyDefiningTrueWithGenericTypeReturnsBothDefiningRowsWhenCurationsIncluded() {
        Page<JsonElement> page = client.searchByVectorInOntology(
                "OntologyEntity", List.of(1.0, 0.0, 0.0, 0.0), ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL,
                "vectestonto", true, true);

        assertThat(ids(page)).containsExactlyInAnyOrder(VEC_ONTO_CLASS_DEFINING_ID, VEC_ONTO_PROPERTY_DEFINING_ID);
    }

    @Test
    void searchByVectorInOntologyDefiningFalseWithGenericTypeReturnsBothTargetRowsWhenCurationsIncluded() {
        Page<JsonElement> page = client.searchByVectorInOntology(
                "OntologyEntity", List.of(1.0, 0.0, 0.0, 0.0), ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL,
                "vectestonto2", false, true);

        assertThat(ids(page)).containsExactlyInAnyOrder(VEC_ONTO_CLASS_TARGET_ID, VEC_ONTO_PROPERTY_TARGET_ID);
    }

    @Test
    void searchByVectorInOntologySixArgOverloadDefaultsIncludeCurationsToTrue() {
        Page<JsonElement> page = client.searchByVectorInOntology(
                "OntologyEntity", List.of(1.0, 0.0, 0.0, 0.0), ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL,
                "vectestonto", true);

        assertThat(ids(page)).containsExactlyInAnyOrder(VEC_ONTO_CLASS_DEFINING_ID, VEC_ONTO_PROPERTY_DEFINING_ID);
    }

    @Test
    void searchByVectorInOntologySevenArgOverloadExcludesCurationsWhenFalse() {
        // VEC_ONTO_PROPERTY has only a CurationEmbedding node -- with curations excluded, only the
        // LabelEmbedding-backed VEC_ONTO_CLASS is found.
        Page<JsonElement> page = client.searchByVectorInOntology(
                "OntologyEntity", List.of(1.0, 0.0, 0.0, 0.0), ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL,
                "vectestonto", true, false);

        assertThat(ids(page)).containsExactly(VEC_ONTO_CLASS_DEFINING_ID);
    }

    // ------------------------------------------------------------------
    // getEmbeddingModels
    // ------------------------------------------------------------------

    @Test
    void getEmbeddingModelsIncludesTheRegisteredModelWithTheEmbeddingsPrefixStripped() {
        List<String> models = client.getEmbeddingModels();

        assertThat(models).contains(OLS_POSTGRES_CLIENT_EMBEDDING_MODEL);
    }

    @Test
    void getEmbeddingModelsExcludesAPca16SuffixedVariant() {
        List<String> models = client.getEmbeddingModels();

        assertThat(models).noneMatch(model -> model.contains("pca16"));
    }
}
