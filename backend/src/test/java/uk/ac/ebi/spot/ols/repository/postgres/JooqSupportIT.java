package uk.ac.ebi.spot.ols.repository.postgres;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport.JooqSupportRepositoryHandle;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.INFORMATION_SCHEMA_COLUMNS;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.OLS_AUTOSUGGEST;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.OLS_EMBEDDING_NODES;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.OLS_ENTITIES;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.OLS_PCA_MODELS;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.OLS_TEXT_TAGGER;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.arrayContains;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.arrayContainsCaseInsensitive;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.arrayContainsField;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.castAsText;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.field;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.matchesTsQuery;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.maxTrigramCandidateLength;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.phraseToTsQuery;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.similarity;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.similarityAtLeastThreshold;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.toTsQuery;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.trigramMatch;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.tsvectorMatches;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.unnest;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.vectorDistance;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.websearchToTsQuery;
import static uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport.JOOQ_SUPPORT_EMBEDDING_MODEL;
import static uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport.JOOQ_SUPPORT_ONTOLOGY_ID;

/**
 * Real-Postgres coverage of {@link JooqSupport}, against a disposable container with the
 * production schema applied (extensions, {@code ols_tsvector()}/{@code ols_lower_array()}, and
 * every index from {@code dataload/create_postgres_schema.py}). {@link JooqSupportTest} already
 * proves each builder renders the SQL text it was intended to; this class proves that SQL text is
 * also <em>semantically</em> correct once it actually runs -- filtering the rows it should and
 * excluding the rows it shouldn't -- against the real custom functions and pg_trgm/pgvector
 * builtins several of these methods depend on. That distinction matters here specifically because
 * several of these conditions were written to fix real past production incidents (GitHub issues
 * #1276, #1308, #1309, cited on the methods below): a syntactically-plausible fragment that quietly
 * matched the wrong rows would defeat the entire point of those fixes while still rendering
 * "correct-looking" SQL.
 *
 * <p>The fixture ({@link PostgresIntegrationTestSupport#initializeJooqSupportDatabase}) is five
 * hand-picked {@code ols_entities} rows under a dedicated {@link
 * PostgresIntegrationTestSupport#JOOQ_SUPPORT_ONTOLOGY_ID} ontology id, plus three {@code
 * ols_embedding_nodes} rows with hand-computable pgvector cosine distances -- see that method's
 * Javadoc for exactly which row is which and why.
 */
class JooqSupportIT {

    private static PostgreSQLContainer<?> container;
    private static JooqSupportRepositoryHandle handle;

    @BeforeAll
    static void setUpDatabase() {
        container = PostgresIntegrationTestSupport.newContainer();
        container.start();
        PostgresIntegrationTestSupport.initializeJooqSupportDatabase(container);
        handle = PostgresIntegrationTestSupport.createJooqSupportRepositories(container);
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

    private static final Field<String> ID = field("id", String.class);
    private static final Field<String> ONTOLOGY_ID = field("ontology_id", String.class);

    /**
     * Runs {@code SELECT id FROM ols_entities WHERE ontology_id = <fixture> AND <extra>}, scoped to
     * this class's dedicated fixture rows so no other suite's data can leak into an assertion.
     */
    private List<String> matchingIds(Condition extra) throws SQLException {
        try (Connection conn = handle.postgresClient().getConnection()) {
            return dsl(conn).select(ID)
                    .from(OLS_ENTITIES)
                    .where(ONTOLOGY_ID.eq(JOOQ_SUPPORT_ONTOLOGY_ID))
                    .and(extra)
                    .orderBy(ID)
                    .fetch(ID);
        }
    }

    private DSLContext dsl(Connection conn) {
        return handle.postgresClient().dsl(conn);
    }

    // ------------------------------------------------------------------
    // Table constants: prove every one resolves to a real table/view in the applied production
    // schema, not just that it renders the intended name (JooqSupportTest already proves that).
    // ------------------------------------------------------------------

    @Test
    void everyTableConstantResolvesToARealTableInTheProductionSchema() throws SQLException {
        try (Connection conn = handle.postgresClient().getConnection()) {
            DSLContext dslContext = dsl(conn);
            for (Table<?> table : List.of(
                    OLS_AUTOSUGGEST, OLS_EMBEDDING_NODES, OLS_ENTITIES, OLS_PCA_MODELS,
                    OLS_TEXT_TAGGER, INFORMATION_SCHEMA_COLUMNS)) {
                Integer count = dslContext.selectCount().from(table).fetchOne(0, Integer.class);
                assertThat(count).as("SELECT COUNT(*) FROM %s must succeed", table).isNotNull();
            }
        }
    }

    // ------------------------------------------------------------------
    // arrayContains(Field<String[]>, String) vs arrayContainsCaseInsensitive -- GitHub #1276/#1309
    // ------------------------------------------------------------------

    @Test
    void arrayContainsIsExactAndCaseSensitive() throws SQLException {
        assertThat(matchingIds(arrayContains(field("label", String[].class), "Cancer")))
                .containsExactly("jst+class+cancer_upper");
        assertThat(matchingIds(arrayContains(field("label", String[].class), "cancer")))
                .containsExactly("jst+class+cancer_lower");
    }

    @Test
    void arrayContainsCaseInsensitiveMatchesRegardlessOfStoredCase() throws SQLException {
        // Caller-supplied value must already be lowercased, per JooqSupport's own javadoc.
        assertThat(matchingIds(arrayContainsCaseInsensitive(field("label", String[].class), "cancer")))
                .containsExactly("jst+class+cancer_lower", "jst+class+cancer_upper");
    }

    // ------------------------------------------------------------------
    // arrayContains(Field,Field) "= ANY" join form vs arrayContainsField "@>" join form -- both
    // must find the same real parent/child relationship, via two structurally different SQL forms.
    // ------------------------------------------------------------------

    @Test
    void arrayContainsAnyFormFindsTheRealParentThroughDirectParents() throws SQLException {
        assertThat(parentsOfViaAnyForm("jst+class+diabetes_child"))
                .containsExactly("jst+class+diabetes_parent");
        assertThat(parentsOfViaAnyForm("jst+class+diabetes_parent"))
                .as("the parent row itself has no direct_parents of its own")
                .isEmpty();
    }

    @Test
    void arrayContainsFieldContainmentFormFindsTheSameParent() throws SQLException {
        assertThat(parentsOfViaContainmentForm("jst+class+diabetes_child"))
                .containsExactly("jst+class+diabetes_parent");
        assertThat(parentsOfViaContainmentForm("jst+class+diabetes_parent")).isEmpty();
    }

    private List<String> parentsOfViaAnyForm(String childId) throws SQLException {
        Table<?> child = OLS_ENTITIES.as("child");
        Table<?> parent = OLS_ENTITIES.as("parent");
        Field<String[]> childDirectParents = field("child", "direct_parents", String[].class);
        Field<String> parentIri = field("parent", "iri", String.class);
        Field<String> parentId = field("parent", "id", String.class);
        Field<String> childId1 = field("child", "id", String.class);

        try (Connection conn = handle.postgresClient().getConnection()) {
            return dsl(conn).select(parentId)
                    .from(child)
                    .join(parent).on(arrayContains(childDirectParents, parentIri))
                    .where(childId1.eq(childId))
                    .fetch(parentId);
        }
    }

    private List<String> parentsOfViaContainmentForm(String childId) throws SQLException {
        Table<?> child = OLS_ENTITIES.as("child");
        Table<?> parent = OLS_ENTITIES.as("parent");
        Field<String[]> childDirectParents = field("child", "direct_parents", String[].class);
        Field<String> parentIri = field("parent", "iri", String.class);
        Field<String> parentId = field("parent", "id", String.class);
        Field<String> childId1 = field("child", "id", String.class);

        try (Connection conn = handle.postgresClient().getConnection()) {
            return dsl(conn).select(parentId)
                    .from(child)
                    .join(parent).on(arrayContainsField(childDirectParents, parentIri))
                    .where(childId1.eq(childId))
                    .fetch(parentId);
        }
    }

    // ------------------------------------------------------------------
    // unnest -- mirrors OlsSearchClient.getDistinctCuratedSources()'s crossJoin(unnest(...)) shape
    // ------------------------------------------------------------------

    @Test
    void unnestExpandsEachArrayElementIntoItsOwnRow() throws SQLException {
        Field<String[]> curatedFromSources = field("curated_from_sources", String[].class);
        Table<?> sources = unnest(curatedFromSources, "curated_sources", "v");
        Field<String> sourceField = field("curated_sources", "v", String.class);

        try (Connection conn = handle.postgresClient().getConnection()) {
            List<String> values = dsl(conn).selectDistinct(sourceField)
                    .from(OLS_ENTITIES)
                    .crossJoin(sources)
                    .where(ONTOLOGY_ID.eq(JOOQ_SUPPORT_ONTOLOGY_ID))
                    .orderBy(sourceField.asc())
                    .fetch(sourceField);

            // cancer_upper has two sources ("pubmed","hpo") and cancer_lower has one ("orphanet"):
            // three rows total prove unnest() really expands one array-valued row into several,
            // not just passes the array through.
            assertThat(values).containsExactly("hpo", "orphanet", "pubmed");
        }
    }

    // ------------------------------------------------------------------
    // castAsText
    // ------------------------------------------------------------------

    @Test
    void castAsTextConvertsABooleanColumnToItsRealTextRepresentation() throws SQLException {
        Field<String> asText = castAsText(field("is_defining_ontology", Boolean.class));

        try (Connection conn = handle.postgresClient().getConnection()) {
            assertThat(dsl(conn).select(asText).from(OLS_ENTITIES)
                    .where(ID.eq("jst+class+cancer_upper")).fetchOne(asText))
                    .isEqualTo("true");
            assertThat(dsl(conn).select(asText).from(OLS_ENTITIES)
                    .where(ID.eq("jst+class+cancer_lower")).fetchOne(asText))
                    .isEqualTo("false");
        }
    }

    // ------------------------------------------------------------------
    // similarity / trigramMatch / similarityAtLeastThreshold -- pg_trgm, real near-miss/non-match
    // ------------------------------------------------------------------

    @Test
    void similarityScoresARealNearMissHigherThanAnUnrelatedString() throws SQLException {
        Field<String> labelForSuggest = field("label_for_suggest", String.class);
        Field<Double> sim = similarity(labelForSuggest, "Diabetes");

        try (Connection conn = handle.postgresClient().getConnection()) {
            Double childSimilarity = dsl(conn).select(sim).from(OLS_ENTITIES)
                    .where(ID.eq("jst+class+diabetes_child")).fetchOne(sim);
            Double unrelatedSimilarity = dsl(conn).select(sim).from(OLS_ENTITIES)
                    .where(ID.eq("jst+class+fulltext_control")).fetchOne(sim);

            assertThat(childSimilarity)
                    .as("'Diabetes Mellitus' shares real trigrams with the query 'Diabetes'")
                    .isGreaterThan(unrelatedSimilarity);
        }
    }

    @Test
    void trigramMatchIncludesTheRealNearMissAndExcludesUnrelatedStrings() throws SQLException {
        Condition matchesDiabetes = trigramMatch(field("label_for_suggest", String.class), "Diabetes");

        assertThat(matchingIds(matchesDiabetes))
                .containsExactly("jst+class+diabetes_child", "jst+class+diabetes_parent");
    }

    @Test
    void similarityAtLeastThresholdAgreesExactlyWithTrigramMatch() throws SQLException {
        // JooqSupport's own javadoc: this is "the same condition %/trigramMatch tests, expressed
        // explicitly" -- so on this fixture the two must produce the identical result set.
        Condition explicitThreshold = similarityAtLeastThreshold(field("label_for_suggest", String.class), "Diabetes");

        assertThat(matchingIds(explicitThreshold))
                .containsExactly("jst+class+diabetes_child", "jst+class+diabetes_parent");
    }

    // ------------------------------------------------------------------
    // maxTrigramCandidateLength -- soundness of the pre-filter bound: it must never exclude a row
    // that genuinely passes similarityAtLeastThreshold.
    // ------------------------------------------------------------------

    @Test
    void maxTrigramCandidateLengthNeverExcludesARealMatch() throws SQLException {
        Field<String> labelForSuggest = field("label_for_suggest", String.class);
        Field<Integer> bound = maxTrigramCandidateLength("Diabetes");
        Condition passesThreshold = similarityAtLeastThreshold(labelForSuggest, "Diabetes");

        try (Connection conn = handle.postgresClient().getConnection()) {
            Integer maxLength = dsl(conn).select(bound).fetchOne(bound);
            assertThat(maxLength).isNotNull();

            var rows = dsl(conn)
                    .select(ID, labelForSuggest, DSL.length(labelForSuggest), passesThreshold)
                    .from(OLS_ENTITIES)
                    .where(ONTOLOGY_ID.eq(JOOQ_SUPPORT_ONTOLOGY_ID))
                    .fetch();

            boolean sawARealMatch = false;
            for (var row : rows) {
                boolean passed = row.value4();
                int length = row.value3();
                if (passed) {
                    sawARealMatch = true;
                    assertThat(length)
                            .as("row '%s' (length %d) passed similarityAtLeastThreshold but exceeds the "
                                    + "candidate-length bound %d, which must never happen", row.value2(), length, maxLength)
                            .isLessThanOrEqualTo(maxLength);
                }
            }
            assertThat(sawARealMatch).as("fixture must contain at least one real match to make this check meaningful").isTrue();
        }
    }

    // ------------------------------------------------------------------
    // tsvectorMatches -- ols_tsvector() wrapping a specific field, GitHub #1308
    // ------------------------------------------------------------------

    @Test
    void tsvectorMatchesRestrictsMatchingToTheGivenFieldOnly() throws SQLException {
        Condition labelMatchesMalignant =
                tsvectorMatches(field("label", Object.class), toTsQuery("malignant:*"));
        assertThat(matchingIds(labelMatchesMalignant)).containsExactly("jst+class+fulltext_control");

        Condition definitionMatchesProliferation =
                tsvectorMatches(field("definition", Object.class), phraseToTsQuery("cell proliferation"));
        assertThat(matchingIds(definitionMatchesProliferation)).containsExactly("jst+class+fulltext_control");

        // The word "Diabetes" never appears in any row's definition, only in label/synonym.
        Condition definitionMatchesDiabetes =
                tsvectorMatches(field("definition", Object.class), toTsQuery("diabetes:*"));
        assertThat(matchingIds(definitionMatchesDiabetes)).isEmpty();
    }

    // ------------------------------------------------------------------
    // matchesTsQuery + websearchToTsQuery / phraseToTsQuery / toTsQuery -- against the generated
    // ts_search column (label + short_form/curie + synonym + definition + iri, all fields at once)
    // ------------------------------------------------------------------

    @Test
    void matchesTsQueryWithWebsearchToTsQueryAppliesOrSemanticsAcrossRows() throws SQLException {
        Condition condition = matchesTsQuery(
                field("ts_search", Object.class), websearchToTsQuery("cancer OR diabetes"));

        assertThat(matchingIds(condition)).containsExactly(
                "jst+class+cancer_lower",
                "jst+class+cancer_upper",
                "jst+class+diabetes_child",
                "jst+class+diabetes_parent");
    }

    @Test
    void matchesTsQueryWithPhraseToTsQueryRequiresTheExactContiguousPhrase() throws SQLException {
        Condition condition = matchesTsQuery(
                field("ts_search", Object.class), phraseToTsQuery("sugar diabetes"));

        // Only diabetes_child's synonym "Sugar Diabetes" contains this exact two-word phrase;
        // diabetes_parent's label is the single word "Diabetes" with no adjacent "sugar".
        assertThat(matchingIds(condition)).containsExactly("jst+class+diabetes_child");
    }

    @Test
    void matchesTsQueryWithToTsQueryAppliesPrefixMatching() throws SQLException {
        Condition condition = matchesTsQuery(field("ts_search", Object.class), toTsQuery("diabet:*"));

        assertThat(matchingIds(condition))
                .containsExactly("jst+class+diabetes_child", "jst+class+diabetes_parent");
    }

    // ------------------------------------------------------------------
    // vectorDistance(Field,String literal) / vectorDistance(Field,Field) -- pgvector <=> cosine
    // distance, both overloads, against hand-computable vectors.
    // ------------------------------------------------------------------

    @Test
    void vectorDistanceAgainstALiteralVectorComputesTheExactCosineDistance() throws SQLException {
        Field<Object> embedding = field("embedding_" + JOOQ_SUPPORT_EMBEDDING_MODEL, Object.class);
        Field<Double> distance = vectorDistance(embedding, "[1,0,0,0]");
        Field<String> nodeId = field("id", String.class);

        try (Connection conn = handle.postgresClient().getConnection()) {
            var rows = dsl(conn).select(nodeId, distance).from(OLS_EMBEDDING_NODES).fetch();

            assertThat(rows).hasSize(3);
            for (var row : rows) {
                String id = row.value1();
                double actual = row.value2();
                double expected = switch (id) {
                    case "jst-emb-identical" -> 0.0; // same direction: cosine distance 0
                    case "jst-emb-orthogonal" -> 1.0; // perpendicular: cosine distance 1
                    case "jst-emb-opposite" -> 2.0; // opposite direction: cosine distance 2
                    default -> throw new AssertionError("unexpected embedding node id: " + id);
                };
                assertThat(actual).as("cosine distance for %s", id).isCloseTo(expected, within(1e-6));
            }
        }
    }

    @Test
    void vectorDistanceBetweenTwoColumnsComputesTheExactCosineDistance() throws SQLException {
        Table<?> a = OLS_EMBEDDING_NODES.as("a");
        Table<?> b = OLS_EMBEDDING_NODES.as("b");
        Field<Object> aEmbedding = field("a", "embedding_" + JOOQ_SUPPORT_EMBEDDING_MODEL, Object.class);
        Field<Object> bEmbedding = field("b", "embedding_" + JOOQ_SUPPORT_EMBEDDING_MODEL, Object.class);
        Field<String> aId = field("a", "id", String.class);
        Field<String> bId = field("b", "id", String.class);
        Field<Double> distance = vectorDistance(aEmbedding, bEmbedding);

        try (Connection conn = handle.postgresClient().getConnection()) {
            Double actual = dsl(conn).select(distance)
                    .from(a).crossJoin(b)
                    .where(aId.eq("jst-emb-identical").and(bId.eq("jst-emb-opposite")))
                    .fetchOne(distance);

            // [1,0,0,0] vs [-1,0,0,0]: exactly opposite direction -> cosine distance exactly 2.
            assertThat(actual).isCloseTo(2.0, within(1e-6));
        }
    }
}
