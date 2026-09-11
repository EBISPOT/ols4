package uk.ac.ebi.spot.ols.repository.postgres;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient.normalizeCosineDistance;
import static uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient.normalizeCosineSimilarity;

/**
 * Direct unit coverage for the pure logic in {@link OlsPostgresClient}: the SQL-injection-relevant
 * embedding-model-name sanitizers, the cosine similarity/distance normalization math, and the
 * nearest-neighbor candidate-limit/entity-type/vector-literal helpers used by the embedding/vector
 * search family. This is the "milestone 1" slice of this Tier B target's coverage (see
 * {@code docs/backend-testing-strategy.md}'s "Implemented OlsPostgresClient" baseline for the full
 * split): everything here is exercised with no Postgres connection at all, either because the
 * method is package-private static ({@link #normalizeCosineSimilarity}/{@link
 * #normalizeCosineDistance}) and callable directly from this same-package test class, or because it
 * is reached via reflection ({@code hasConcreteEntityType}, {@code nearestNeighborCandidateLimit},
 * {@code vectorLiteral} -- all private instance methods with no Postgres dependency of their own),
 * or because the public entry point that calls it ({@code sanitizeEmbeddingColumnName}/{@code
 * sanitizeEmbeddingNodeColumnName}, both private) fails fast with {@link IllegalArgumentException}
 * before the method ever opens a Postgres connection, so a bare {@code new OlsPostgresClient()}
 * with no {@code postgresClient} collaborator wired in is sufficient. Real-Postgres-backed
 * behaviour ({@code getAll}/{@code getOne}/{@code getDatabaseNodeCount} in this milestone; the
 * graph-traversal and embedding-search families in later milestones) is covered separately by
 * {@code OlsPostgresClientIT}.
 */
class OlsPostgresClientTest {

    private final OlsPostgresClient client = new OlsPostgresClient();

    private static final PageRequest ANY_PAGEABLE = PageRequest.of(0, 10);

    // ------------------------------------------------------------------
    // sanitizeEmbeddingColumnName / sanitizeEmbeddingNodeColumnName -- reached only via public
    // entry points, since both sanitizers are private. Every public method that calls one of them
    // does so as its very first statement, before the try-with-resources that opens a Postgres
    // connection, so a client with no postgresClient collaborator wired in is enough to prove the
    // guard fires without ever touching the database.
    // ------------------------------------------------------------------

    @Test
    void getSimilarRejectsNullModelName() {
        assertThatThrownBy(() -> client.getSimilar("Class", "http://example.org/X", ANY_PAGEABLE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid embedding model name: null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad name", "bad;name", "bad'name", "bad\"name", "bad/name", "../etc/passwd"})
    void getSimilarRejectsModelNameWithUnsafeCharacters(String unsafeModelName) {
        assertThatThrownBy(() -> client.getSimilar("Class", "http://example.org/X", ANY_PAGEABLE, unsafeModelName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid embedding model name: " + unsafeModelName);
    }

    @Test
    void getSimilarityRejectsNullModelName() {
        assertThatThrownBy(() -> client.getSimilarity("Class", "http://example.org/A", "http://example.org/B", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid embedding model name: null");
    }

    @Test
    void getSimilarityRejectsModelNameWithUnsafeCharacters() {
        assertThatThrownBy(() -> client.getSimilarity(
                "Class", "http://example.org/A", "http://example.org/B", "drop table;"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid embedding model name: drop table;");
    }

    @Test
    void getEmbeddingVectorRejectsNullModelName() {
        assertThatThrownBy(() -> client.getEmbeddingVector("Class", "http://example.org/X", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid embedding model name: null");
    }

    @Test
    void getEmbeddingVectorRejectsModelNameWithUnsafeCharacters() {
        assertThatThrownBy(() -> client.getEmbeddingVector("Class", "http://example.org/X", "has space"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid embedding model name: has space");
    }

    @Test
    void searchByVectorFourArgOverloadRejectsUnsafeModelNameBeforeTouchingPostgres() {
        assertThatThrownBy(() -> client.searchByVector("Class", List.of(1.0, 0.0), ANY_PAGEABLE, "bad name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid embedding model name: bad name");
    }

    @Test
    void searchByVectorFiveArgOverloadRejectsNullModelName() {
        assertThatThrownBy(() -> client.searchByVector("Class", List.of(1.0, 0.0), ANY_PAGEABLE, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid embedding model name: null");
    }

    @Test
    void searchByVectorInOntologySixArgOverloadRejectsUnsafeModelName() {
        assertThatThrownBy(() -> client.searchByVectorInOntology(
                "Class", List.of(1.0), ANY_PAGEABLE, "bad;name", "efo", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid embedding model name: bad;name");
    }

    @Test
    void searchByVectorInOntologySevenArgOverloadRejectsNullModelName() {
        assertThatThrownBy(() -> client.searchByVectorInOntology(
                "Class", List.of(1.0), ANY_PAGEABLE, null, "efo", true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid embedding model name: null");
    }

    @Test
    void sanitizersAcceptAWellFormedModelNameAndDoNotThrowIllegalArgumentException() {
        // A syntactically valid model name must pass the sanitizer and proceed to the
        // Postgres-touching code, which fails with something other than IllegalArgumentException
        // since this client has no real postgresClient wired in (a NullPointerException from the
        // unwired collaborator, not a rejection of the model name itself).
        assertThatThrownBy(() -> client.getEmbeddingVector("Class", "http://example.org/X", "valid.Model-Name_1"))
                .isNotInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    // normalizeCosineSimilarity / normalizeCosineDistance -- package-private static, called
    // directly. Both round to 6 decimal places and clamp to [0, 1] via the private
    // clampUnitInterval -- boundary values slightly outside [-1, 1] (as real floating-point cosine
    // similarity/distance computations can produce) exercise that clamp indirectly.
    // ------------------------------------------------------------------

    @Test
    void normalizeCosineSimilarityMapsPerfectSimilarityToOne() {
        assertThat(normalizeCosineSimilarity(1.0)).isEqualTo(1.0);
    }

    @Test
    void normalizeCosineSimilarityMapsPerfectDissimilarityToZero() {
        assertThat(normalizeCosineSimilarity(-1.0)).isEqualTo(0.0);
    }

    @Test
    void normalizeCosineSimilarityMapsOrthogonalToOneHalf() {
        assertThat(normalizeCosineSimilarity(0.0)).isEqualTo(0.5);
    }

    @Test
    void normalizeCosineSimilarityClampsFloatingPointOverflowAboveOneToOne() {
        // (1.0000001 + 1) / 2 = 1.00000005, which clampUnitInterval must clamp back down to 1.0
        // before rounding -- the exact kind of floating-point drift real cosine similarity can
        // produce for two identical vectors.
        assertThat(normalizeCosineSimilarity(1.0000001)).isEqualTo(1.0);
    }

    @Test
    void normalizeCosineSimilarityClampsFloatingPointOverflowBelowNegativeOneToZero() {
        // (-1.0000001 + 1) / 2 = -0.00000005, which must clamp up to 0.0, not surface as negative.
        assertThat(normalizeCosineSimilarity(-1.0000001)).isEqualTo(0.0);
    }

    @Test
    void normalizeCosineSimilarityRoundsToSixDecimalPlaces() {
        // (0.123456789 + 1) / 2 = 0.5617283945 -> rounds to 0.561728 (banker's-unaware
        // Math.round, i.e. round-half-up on the millionths digit).
        assertThat(normalizeCosineSimilarity(0.123456789)).isEqualTo(0.561728);
    }

    @Test
    void normalizeCosineDistanceMapsIdenticalVectorsToOne() {
        assertThat(normalizeCosineDistance(0.0)).isEqualTo(1.0);
    }

    @Test
    void normalizeCosineDistanceMapsMaximumDistanceToZero() {
        assertThat(normalizeCosineDistance(2.0)).isEqualTo(0.0);
    }

    @Test
    void normalizeCosineDistanceMapsOrthogonalToOneHalf() {
        assertThat(normalizeCosineDistance(1.0)).isEqualTo(0.5);
    }

    @Test
    void normalizeCosineDistanceClampsFloatingPointDriftBelowZeroToOne() {
        // 1 - (-0.0000001 / 2) = 1.00000005, must clamp down to 1.0.
        assertThat(normalizeCosineDistance(-0.0000001)).isEqualTo(1.0);
    }

    @Test
    void normalizeCosineDistanceClampsFloatingPointDriftAboveTwoToZero() {
        // 1 - (2.0000002 / 2) = -0.0000001, must clamp up to 0.0.
        assertThat(normalizeCosineDistance(2.0000002)).isEqualTo(0.0);
    }

    @Test
    void normalizeCosineDistanceRoundsToSixDecimalPlaces() {
        // 1 - (0.123456789 / 2) = 0.9382716055 -> rounds to 0.938272.
        assertThat(normalizeCosineDistance(0.123456789)).isEqualTo(0.938272);
    }

    // ------------------------------------------------------------------
    // hasConcreteEntityType(String) -- private instance method, reached via reflection.
    // ------------------------------------------------------------------

    private boolean hasConcreteEntityType(String type) throws ReflectiveOperationException {
        Method method = OlsPostgresClient.class.getDeclaredMethod("hasConcreteEntityType", String.class);
        method.setAccessible(true);
        try {
            return (boolean) method.invoke(client, type);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    @Test
    void hasConcreteEntityTypeIsFalseForNull() throws ReflectiveOperationException {
        assertThat(hasConcreteEntityType(null)).isFalse();
    }

    @Test
    void hasConcreteEntityTypeIsFalseForBlank() throws ReflectiveOperationException {
        assertThat(hasConcreteEntityType("")).isFalse();
        assertThat(hasConcreteEntityType("   ")).isFalse();
    }

    @Test
    void hasConcreteEntityTypeIsFalseForTheGenericOntologyEntityMarker() throws ReflectiveOperationException {
        assertThat(hasConcreteEntityType("OntologyEntity")).isFalse();
    }

    @Test
    void hasConcreteEntityTypeIsTrueForAnyOtherNonBlankType() throws ReflectiveOperationException {
        assertThat(hasConcreteEntityType("Class")).isTrue();
        assertThat(hasConcreteEntityType("OntologyClass")).isTrue();
        assertThat(hasConcreteEntityType("Property")).isTrue();
        assertThat(hasConcreteEntityType("Individual")).isTrue();
    }

    // ------------------------------------------------------------------
    // nearestNeighborCandidateLimit(int, boolean, boolean) -- private instance method, reflection.
    // Three independent scaling factors (base, filterByType, filterByOntology) plus a final
    // 20,000 cap, each exercised in isolation and combined.
    // ------------------------------------------------------------------

    private int nearestNeighborCandidateLimit(int requestedLimit, boolean filterByType, boolean filterByOntology)
            throws ReflectiveOperationException {
        Method method = OlsPostgresClient.class.getDeclaredMethod(
                "nearestNeighborCandidateLimit", int.class, boolean.class, boolean.class);
        method.setAccessible(true);
        try {
            return (int) method.invoke(client, requestedLimit, filterByType, filterByOntology);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    @Test
    void nearestNeighborCandidateLimitUsesTheMinimumFloorOfOneHundredWhenLimitTimesTwentyIsSmaller()
            throws ReflectiveOperationException {
        // requestedLimit * 20 = 20, below the 100 floor.
        assertThat(nearestNeighborCandidateLimit(1, false, false)).isEqualTo(100);
    }

    @Test
    void nearestNeighborCandidateLimitScalesByTwentyOnceAboveTheFloor() throws ReflectiveOperationException {
        // requestedLimit * 20 = 200, above the 100 floor.
        assertThat(nearestNeighborCandidateLimit(10, false, false)).isEqualTo(200);
    }

    @Test
    void nearestNeighborCandidateLimitFilterByTypeHasNoEffectWhenItDoesNotExceedTheBase()
            throws ReflectiveOperationException {
        // base = max(1*20, 100) = 100; type bump = max(100, 1*100) = 100 -- no change.
        assertThat(nearestNeighborCandidateLimit(1, true, false)).isEqualTo(100);
    }

    @Test
    void nearestNeighborCandidateLimitFilterByTypeBumpsAboveTheBaseWhenItIsLarger()
            throws ReflectiveOperationException {
        // base = max(2*20, 100) = 100; type bump = max(100, 2*100) = 200.
        assertThat(nearestNeighborCandidateLimit(2, true, false)).isEqualTo(200);
    }

    @Test
    void nearestNeighborCandidateLimitFilterByOntologyBumpsFarAboveTheBase() throws ReflectiveOperationException {
        // base = max(2*20, 100) = 100; ontology bump = max(100, 2*500) = 1000.
        assertThat(nearestNeighborCandidateLimit(2, false, true)).isEqualTo(1000);
    }

    @Test
    void nearestNeighborCandidateLimitCombinesTypeAndOntologyBumpsTakingTheLarger()
            throws ReflectiveOperationException {
        // base = 100; type bump = max(100, 200) = 200; ontology bump = max(200, 1000) = 1000.
        assertThat(nearestNeighborCandidateLimit(2, true, true)).isEqualTo(1000);
    }

    @Test
    void nearestNeighborCandidateLimitCapsAtTwentyThousandFromBaseAlone() throws ReflectiveOperationException {
        // requestedLimit * 20 = 40,000, above the 20,000 cap, with no type/ontology bump involved.
        assertThat(nearestNeighborCandidateLimit(2000, false, false)).isEqualTo(20_000);
    }

    @Test
    void nearestNeighborCandidateLimitCapsAtTwentyThousandFromTheOntologyBumpAlone()
            throws ReflectiveOperationException {
        // base = max(200*20, 100) = 4,000; ontology bump = max(4,000, 200*500=100,000) = 100,000,
        // capped down to 20,000.
        assertThat(nearestNeighborCandidateLimit(200, false, true)).isEqualTo(20_000);
    }

    @Test
    void nearestNeighborCandidateLimitCapsAtTwentyThousandFromTheCombinedBumps()
            throws ReflectiveOperationException {
        assertThat(nearestNeighborCandidateLimit(1000, true, true)).isEqualTo(20_000);
    }

    // ------------------------------------------------------------------
    // vectorLiteral(List<Double>) -- private instance method, reflection.
    // ------------------------------------------------------------------

    private String vectorLiteral(List<Double> vector) throws ReflectiveOperationException {
        Method method = OlsPostgresClient.class.getDeclaredMethod("vectorLiteral", List.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(client, vector);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    @Test
    void vectorLiteralOfEmptyListIsEmptyBrackets() throws ReflectiveOperationException {
        assertThat(vectorLiteral(List.of())).isEqualTo("[]");
    }

    @Test
    void vectorLiteralOfSingleElementListHasNoJoiningComma() throws ReflectiveOperationException {
        assertThat(vectorLiteral(List.of(1.5))).isEqualTo("[1.5]");
    }

    @Test
    void vectorLiteralOfMultipleElementsJoinsWithCommasInOrder() throws ReflectiveOperationException {
        assertThat(vectorLiteral(List.of(1.0, 2.5, -3.0))).isEqualTo("[1.0,2.5,-3.0]");
    }

}
