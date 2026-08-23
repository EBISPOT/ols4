package uk.ac.ebi.spot.ols.repository.postgres;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public final class JooqSupport {

    public static final Table<?> OLS_AUTOSUGGEST = DSL.table(DSL.name("ols_autosuggest"));
    public static final Table<?> OLS_EMBEDDING_NODES = DSL.table(DSL.name("ols_embedding_nodes"));
    public static final Table<?> OLS_ENTITIES = DSL.table(DSL.name("ols_entities"));
    public static final Table<?> OLS_PCA_MODELS = DSL.table(DSL.name("ols_pca_models"));
    public static final Table<?> OLS_TEXT_TAGGER = DSL.table(DSL.name("ols_text_tagger"));
    public static final Table<?> INFORMATION_SCHEMA_COLUMNS = DSL.table(DSL.name("information_schema", "columns"));

    private JooqSupport() {
    }

    public static <T> Field<T> field(String column, Class<T> type) {
        return DSL.field(DSL.name(column), type);
    }

    public static <T> Field<T> field(String qualifier, String column, Class<T> type) {
        if (qualifier == null || qualifier.isBlank()) {
            return field(column, type);
        }
        return DSL.field(DSL.name(qualifier, column), type);
    }

    public static Condition arrayContains(Field<String[]> arrayField, String value) {
        // Use array containment (@>) rather than `value = ANY(array)`: the latter cannot use a GIN
        // index and forces a sequential scan, while `array @> ARRAY[value]` is semantically
        // identical and GIN-accelerated. The ::text[] cast keeps the operand types aligned when
        // the value is supplied as a bind parameter. See GitHub issue #1276.
        return DSL.condition("{0} @> ARRAY[{1}]::text[]", arrayField, DSL.val(value));
    }

    public static Condition arrayContainsCaseInsensitive(Field<String[]> arrayField, String lowerValue) {
        // Case-insensitive counterpart to arrayContains(): matches against the ols_lower_array()
        // expression (see idx_ent_label_lower / idx_ent_synonym_lower) instead of the raw column,
        // so the comparison stays GIN-accelerated. Caller must lowercase `lowerValue` already.
        // See GitHub issue #1309.
        return DSL.condition("ols_lower_array({0}) @> ARRAY[{1}]::text[]", arrayField, DSL.val(lowerValue));
    }

    public static Condition arrayContains(Field<String[]> arrayField, Field<String> valueField) {
        // `value = ANY(array)` form: only useful when the *value* side is the scanned column
        // (e.g. e2.iri = ANY(e1.direct_parents) in a nested loop, served by a btree index on
        // e2.iri). When the *array* side is the scanned column, use arrayContainsField instead.
        return DSL.condition("{0} = ANY({1})", valueField, arrayField);
    }

    public static Condition arrayContainsField(Field<String[]> arrayField, Field<String> valueField) {
        // GIN-accelerated containment for joins where the array column is on the scanned side
        // (e.g. e2.direct_parents @> ARRAY[e1.iri]). The `= ANY` form cannot use the GIN index
        // and scans every row of the ontology per lookup. See GitHub issue #1276.
        return DSL.condition("{0} @> ARRAY[{1}]", arrayField, valueField);
    }

    public static Field<String> castAsText(Field<?> field) {
        return DSL.field("CAST({0} AS text)", SQLDataType.VARCHAR, field);
    }

    public static Field<Double> similarity(Field<String> field, String value) {
        return DSL.function("similarity", SQLDataType.DOUBLE, field, castAsText(DSL.val(value)));
    }

    public static Condition trigramMatch(Field<String> field, String value) {
        return DSL.condition("{0} % {1}", field, castAsText(DSL.val(value)));
    }

    /**
     * True when {@code field}'s similarity to {@code value} clears the server's configured
     * threshold (show_limit(), default 0.3) -- the same condition {@code %}/trigramMatch tests,
     * expressed explicitly so it can be applied to a pre-filtered candidate set (see
     * OlsSearchClient.suggestLabelsUncached) rather than relying on the operator's own index-backed
     * candidate generation.
     */
    public static Condition similarityAtLeastThreshold(Field<String> field, String value) {
        return DSL.condition("similarity({0}, {1}) >= show_limit()", field, castAsText(DSL.val(value)));
    }

    /**
     * Upper bound on a candidate string's own trigram count for it to have any mathematical chance
     * of matching {@code value} at the current similarity threshold t: similarity = |A∩B|/|A∪B| >= t
     * requires |B| <= |A|/t (since |A∩B| <= |A| always). Trigram count is computed via show_trgm()
     * rather than derived from string length, because pg_trgm tokenizes multi-word input per word,
     * not over the whole padded string -- a length-based estimate would be wrong for those.
     *
     * <p>Used to pre-filter substring-match candidates in OlsSearchClient.suggestLabelsUncached
     * before ranking: any candidate longer (in trigram terms) than this bound is provably unable to
     * pass {@link #similarityAtLeastThreshold}, so excluding it can never drop a true match -- it
     * only shrinks the candidate pool pg_trgm's substring/ILIKE search has to rank.
     */
    public static Field<Integer> maxTrigramCandidateLength(String value) {
        return DSL.field(
                "ceil(array_length(show_trgm({0}), 1) / show_limit()) - 1",
                SQLDataType.INTEGER,
                castAsText(DSL.val(value))
        );
    }

    public static Table<?> unnest(Field<String[]> arrayField, String tableAlias, String columnAlias) {
        return DSL.table("unnest({0})", arrayField).as(tableAlias, columnAlias);
    }

    public static Field<Double> vectorDistance(Field<?> field, String vectorLiteral) {
        return DSL.field("{0} <=> CAST({1} AS vector)", SQLDataType.DOUBLE, field, DSL.val(vectorLiteral));
    }

    public static Field<Double> vectorDistance(Field<?> left, Field<?> right) {
        return DSL.field("{0} <=> {1}", SQLDataType.DOUBLE, left, right);
    }

    public static Field<Object> websearchToTsQuery(String searchText) {
        return DSL.function("websearch_to_tsquery", SQLDataType.OTHER, DSL.inline("english"), DSL.val(searchText));
    }

    public static Field<Object> phraseToTsQuery(String searchText) {
        return DSL.function("phraseto_tsquery", SQLDataType.OTHER, DSL.inline("english"), DSL.val(searchText));
    }

    /**
     * Builds a tsquery from an already-formatted tsquery string (e.g. "micro:* &amp; scop:*").
     * Used for prefix matching so that, for example, "micro" matches "microscopy".
     */
    public static Field<Object> toTsQuery(String tsQueryString) {
        return DSL.function("to_tsquery", SQLDataType.OTHER, DSL.inline("english"), DSL.val(tsQueryString));
    }

    public static Condition matchesTsQuery(Field<?> tsVector, Field<?> tsQuery) {
        return DSL.condition("{0} @@ {1}", tsVector, tsQuery);
    }

    public static Condition tsvectorMatches(Field<?> textOrArrayField, Field<?> tsQuery) {
        // ols_tsvector() has both a text and text[] overload (see create_postgres_schema.py);
        // Postgres resolves the right one from the column's runtime type. Used to restrict
        // non-exact search to specific searchFields/queryFields instead of the blanket ts_search
        // column, which spans every field regardless of what was requested. See GitHub issue #1308.
        return DSL.condition("ols_tsvector({0}) @@ {1}", textOrArrayField, tsQuery);
    }
}
