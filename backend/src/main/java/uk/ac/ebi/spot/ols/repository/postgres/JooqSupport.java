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
        return DSL.condition("{0} = ANY({1})", DSL.val(value), arrayField);
    }

    public static Condition arrayContains(Field<String[]> arrayField, Field<String> valueField) {
        return DSL.condition("{0} = ANY({1})", valueField, arrayField);
    }

    public static Field<String> castAsText(Field<?> field) {
        return DSL.field("CAST({0} AS text)", SQLDataType.VARCHAR, field);
    }

    public static Field<Double> similarity(Field<String> field, String value) {
        return DSL.function("similarity", SQLDataType.DOUBLE, field, DSL.val(value));
    }

    public static Condition trigramMatch(Field<String> field, String value) {
        return DSL.condition("{0} % {1}", field, DSL.val(value));
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
}
