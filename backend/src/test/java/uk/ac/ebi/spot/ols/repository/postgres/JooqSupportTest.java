package uk.ac.ebi.spot.ols.repository.postgres;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

/**
 * Direct unit coverage for {@link JooqSupport}: every public method (and every overload) rendered
 * to its actual Postgres SQL text via a plain, connection-free {@code DSLContext}
 * ({@code DSL.using(SQLDialect.POSTGRES)}, the same idiom already used by
 * {@code OlsSearchQueryBoostTest}/{@code OlsSearchQueryIriTest}), with bind values inlined via
 * {@code renderInlined} so the exact literal/operator/cast structure each expression produces is
 * asserted, not just its shape with placeholders.
 *
 * <p>This proves the SQL fragment each builder emits is syntactically what was intended. It does
 * <b>not</b> prove the fragment is semantically correct against the real custom Postgres functions
 * ({@code ols_tsvector}, {@code ols_lower_array}) and pg_trgm builtins ({@code similarity},
 * {@code show_limit}, {@code show_trgm}, the {@code %} operator) several of these methods depend
 * on -- several of these conditions were written to fix specific past production incidents
 * (GitHub issues #1276, #1308, #1309, cited inline on the methods below), so semantic correctness
 * matters more here than the class's small size suggests. That half is covered separately by
 * {@link JooqSupportIT}, which executes these same builders against real Postgres with the
 * production schema applied.
 */
class JooqSupportTest {

    private static final DSLContext CTX = DSL.using(SQLDialect.POSTGRES);

    private static String render(Field<?> field) {
        return CTX.renderInlined(field);
    }

    private static String render(Condition condition) {
        return CTX.renderInlined(condition);
    }

    // ------------------------------------------------------------------
    // Private constructor: this is a pure static-method utility class, never instantiated by
    // production code, but the explicit no-op private constructor is still a real line/branch a
    // coverage report tracks -- enumerated explicitly rather than left implicit.
    // ------------------------------------------------------------------

    @Test
    void constructorIsPrivateAndUninstantiatedFromOutsideTheClass() {
        assertTrue(Modifier.isPrivate(
                JooqSupport.class.getDeclaredConstructors()[0].getModifiers()));
    }

    @Test
    void constructorCanStillBeInvokedReflectivelyAndDoesNothing()
            throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Constructor<JooqSupport> constructor = JooqSupport.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        JooqSupport instance = constructor.newInstance();

        assertThat(instance).isNotNull();
    }

    // ------------------------------------------------------------------
    // Table constants
    // ------------------------------------------------------------------

    @Test
    void olsAutosuggestTableRendersUnqualifiedQuotedName() {
        assertThat(CTX.render(OLS_AUTOSUGGEST)).isEqualTo("\"ols_autosuggest\"");
    }

    @Test
    void olsEmbeddingNodesTableRendersUnqualifiedQuotedName() {
        assertThat(CTX.render(OLS_EMBEDDING_NODES)).isEqualTo("\"ols_embedding_nodes\"");
    }

    @Test
    void olsEntitiesTableRendersUnqualifiedQuotedName() {
        assertThat(CTX.render(OLS_ENTITIES)).isEqualTo("\"ols_entities\"");
    }

    @Test
    void olsPcaModelsTableRendersUnqualifiedQuotedName() {
        assertThat(CTX.render(OLS_PCA_MODELS)).isEqualTo("\"ols_pca_models\"");
    }

    @Test
    void olsTextTaggerTableRendersUnqualifiedQuotedName() {
        assertThat(CTX.render(OLS_TEXT_TAGGER)).isEqualTo("\"ols_text_tagger\"");
    }

    @Test
    void informationSchemaColumnsTableRendersSchemaQualifiedName() {
        assertThat(CTX.render(INFORMATION_SCHEMA_COLUMNS)).isEqualTo("\"information_schema\".\"columns\"");
    }

    // ------------------------------------------------------------------
    // field(column, type) / field(qualifier, column, type)
    // ------------------------------------------------------------------

    @Test
    void fieldTwoArgRendersUnqualifiedQuotedColumn() {
        assertThat(render(field("label", String.class))).isEqualTo("\"label\"");
    }

    @Test
    void fieldThreeArgWithNullQualifierRendersUnqualified() {
        assertThat(render(field(null, "label", String.class))).isEqualTo("\"label\"");
    }

    @Test
    void fieldThreeArgWithEmptyQualifierRendersUnqualified() {
        assertThat(render(field("", "label", String.class))).isEqualTo("\"label\"");
    }

    @Test
    void fieldThreeArgWithWhitespaceOnlyQualifierRendersUnqualified() {
        // isBlank(), not isEmpty(): a whitespace-only qualifier must take the same branch as "".
        assertThat(render(field("   ", "label", String.class))).isEqualTo("\"label\"");
    }

    @Test
    void fieldThreeArgWithNonBlankQualifierRendersQualified() {
        assertThat(render(field("e1", "label", String.class))).isEqualTo("\"e1\".\"label\"");
    }

    // ------------------------------------------------------------------
    // arrayContains(Field<String[]>, String) -- GIN-friendly array containment, GitHub issue #1276
    // ------------------------------------------------------------------

    @Test
    void arrayContainsWithLiteralValueRendersGinFriendlyContainment() {
        Field<String[]> label = field("label", String[].class);

        Condition condition = arrayContains(label, "hello");

        assertThat(render(condition)).isEqualTo("(\"label\" @> ARRAY['hello']::text[])");
    }

    @Test
    void arrayContainsEscapesASingleQuoteInTheLiteralValue() {
        Field<String[]> label = field("label", String[].class);

        Condition condition = arrayContains(label, "d'Arcy");

        assertThat(render(condition)).isEqualTo("(\"label\" @> ARRAY['d''Arcy']::text[])");
    }

    // ------------------------------------------------------------------
    // arrayContainsCaseInsensitive -- ols_lower_array() counterpart, GitHub issue #1309
    // ------------------------------------------------------------------

    @Test
    void arrayContainsCaseInsensitiveWrapsFieldInOlsLowerArray() {
        Field<String[]> label = field("label", String[].class);

        Condition condition = arrayContainsCaseInsensitive(label, "hello");

        assertThat(render(condition))
                .isEqualTo("(ols_lower_array(\"label\") @> ARRAY['hello']::text[])");
    }

    // ------------------------------------------------------------------
    // arrayContains(Field<String[]>, Field<String>) -- "value = ANY(array)" form
    // ------------------------------------------------------------------

    @Test
    void arrayContainsFieldVariantRendersValueEqualsAnyArray() {
        Field<String[]> directParents = field("e1", "direct_parents", String[].class);
        Field<String> iri = field("e2", "iri", String.class);

        Condition condition = arrayContains(directParents, iri);

        assertThat(render(condition)).isEqualTo("(\"e2\".\"iri\" = ANY(\"e1\".\"direct_parents\"))");
    }

    // ------------------------------------------------------------------
    // arrayContainsField -- GIN-friendly join containment, GitHub issue #1276
    // ------------------------------------------------------------------

    @Test
    void arrayContainsFieldRendersGinFriendlyJoinContainment() {
        Field<String[]> directParents = field("e2", "direct_parents", String[].class);
        Field<String> iri = field("e1", "iri", String.class);

        Condition condition = arrayContainsField(directParents, iri);

        assertThat(render(condition)).isEqualTo("(\"e2\".\"direct_parents\" @> ARRAY[\"e1\".\"iri\"])");
    }

    // ------------------------------------------------------------------
    // castAsText
    // ------------------------------------------------------------------

    @Test
    void castAsTextRendersExplicitTextCast() {
        Field<?> embedding = field("embedding", Object.class);

        Field<String> cast = castAsText(embedding);

        assertThat(render(cast)).isEqualTo("CAST(\"embedding\" AS text)");
    }

    // ------------------------------------------------------------------
    // similarity -- pg_trgm builtin
    // ------------------------------------------------------------------

    @Test
    void similarityRendersFunctionCallWithCastValue() {
        Field<String> labelForSuggest = field("label_for_suggest", String.class);

        Field<Double> sim = similarity(labelForSuggest, "hello");

        assertThat(render(sim)).isEqualTo("similarity(\"label_for_suggest\", CAST('hello' AS text))");
    }

    // ------------------------------------------------------------------
    // trigramMatch -- the % operator
    // ------------------------------------------------------------------

    @Test
    void trigramMatchRendersPercentOperator() {
        Field<String> labelForSuggest = field("label_for_suggest", String.class);

        Condition condition = trigramMatch(labelForSuggest, "hello");

        assertThat(render(condition)).isEqualTo("(\"label_for_suggest\" % CAST('hello' AS text))");
    }

    // ------------------------------------------------------------------
    // similarityAtLeastThreshold -- explicit show_limit() threshold check
    // ------------------------------------------------------------------

    @Test
    void similarityAtLeastThresholdRendersExplicitShowLimitComparison() {
        Field<String> string = field("string", String.class);

        Condition condition = similarityAtLeastThreshold(string, "hello");

        assertThat(render(condition))
                .isEqualTo("(similarity(\"string\", CAST('hello' AS text)) >= show_limit())");
    }

    // ------------------------------------------------------------------
    // maxTrigramCandidateLength -- show_trgm()-based candidate-pruning upper bound
    // ------------------------------------------------------------------

    @Test
    void maxTrigramCandidateLengthRendersShowTrgmBasedBound() {
        Field<Integer> bound = maxTrigramCandidateLength("hello");

        assertThat(render(bound))
                .isEqualTo("ceil(array_length(show_trgm(CAST('hello' AS text)), 1) / show_limit()) - 1");
    }

    // ------------------------------------------------------------------
    // unnest
    // ------------------------------------------------------------------

    @Test
    void unnestRendersAliasedUnnestTableExpression() {
        Field<String[]> curatedFromSources = field("curated_from_sources", String[].class);

        Table<?> table = unnest(curatedFromSources, "curated_sources", "v");

        assertThat(CTX.render(table)).isEqualTo("\"curated_sources\"");
        assertThat(CTX.renderInlined(DSL.selectFrom(table)))
                .isEqualTo("select * from unnest(\"curated_from_sources\") as \"curated_sources\" (\"v\")");
    }

    // ------------------------------------------------------------------
    // vectorDistance(Field, String) -- pgvector <=> operator against a literal vector
    // ------------------------------------------------------------------

    @Test
    void vectorDistanceWithLiteralVectorRendersCosineDistanceOperator() {
        Field<Object> embedding = field("embedding", Object.class);

        Field<Double> distance = vectorDistance(embedding, "[1,2,3]");

        assertThat(render(distance)).isEqualTo("\"embedding\" <=> CAST('[1,2,3]' AS vector)");
    }

    // ------------------------------------------------------------------
    // vectorDistance(Field, Field) -- pgvector <=> operator between two columns
    // ------------------------------------------------------------------

    @Test
    void vectorDistanceBetweenTwoFieldsRendersCosineDistanceOperator() {
        Field<Object> a = field("a", "embedding", Object.class);
        Field<Object> b = field("b", "embedding", Object.class);

        Field<Double> distance = vectorDistance(a, b);

        assertThat(render(distance)).isEqualTo("\"a\".\"embedding\" <=> \"b\".\"embedding\"");
    }

    // ------------------------------------------------------------------
    // websearchToTsQuery
    // ------------------------------------------------------------------

    @Test
    void websearchToTsQueryRendersEnglishConfiguredFunctionCall() {
        Field<Object> query = websearchToTsQuery("hello world");

        assertThat(render(query)).isEqualTo("websearch_to_tsquery('english', 'hello world')");
    }

    // ------------------------------------------------------------------
    // phraseToTsQuery
    // ------------------------------------------------------------------

    @Test
    void phraseToTsQueryRendersEnglishConfiguredFunctionCall() {
        Field<Object> query = phraseToTsQuery("hello world");

        assertThat(render(query)).isEqualTo("phraseto_tsquery('english', 'hello world')");
    }

    // ------------------------------------------------------------------
    // toTsQuery -- takes an already-formatted tsquery string, e.g. for prefix matching
    // ------------------------------------------------------------------

    @Test
    void toTsQueryRendersEnglishConfiguredFunctionCall() {
        Field<Object> query = toTsQuery("micro:* & scop:*");

        assertThat(render(query)).isEqualTo("to_tsquery('english', 'micro:* & scop:*')");
    }

    // ------------------------------------------------------------------
    // matchesTsQuery -- the @@ operator
    // ------------------------------------------------------------------

    @Test
    void matchesTsQueryRendersAtAtOperator() {
        Field<Object> tsSearch = field("ts_search", Object.class);
        Field<Object> query = toTsQuery("hello:*");

        Condition condition = matchesTsQuery(tsSearch, query);

        assertThat(render(condition)).isEqualTo("(\"ts_search\" @@ to_tsquery('english', 'hello:*'))");
    }

    // ------------------------------------------------------------------
    // tsvectorMatches -- ols_tsvector() wrapped in the @@ operator, GitHub issue #1308
    // ------------------------------------------------------------------

    @Test
    void tsvectorMatchesWrapsFieldInOlsTsvectorBeforeAtAtOperator() {
        Field<Object> label = field("label", Object.class);
        Field<Object> query = toTsQuery("hello:*");

        Condition condition = tsvectorMatches(label, query);

        assertThat(render(condition))
                .isEqualTo("(ols_tsvector(\"label\") @@ to_tsquery('english', 'hello:*'))");
    }
}
