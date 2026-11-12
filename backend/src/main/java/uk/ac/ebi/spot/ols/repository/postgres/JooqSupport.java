package uk.ac.ebi.spot.ols.repository.postgres;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.Select;
import org.jooq.SortField;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import java.util.Collection;
import java.util.Set;

public final class JooqSupport {

    public static final Table<?> OLS_AUTOSUGGEST = DSL.table(DSL.name("ols_autosuggest"));
    public static final Table<?> OLS_EMBEDDING_NODES = DSL.table(DSL.name("ols_embedding_nodes"));
    public static final Table<?> OLS_ENTITIES = DSL.table(DSL.name("ols_entities"));
    public static final Table<?> OLS_PCA_MODELS = DSL.table(DSL.name("ols_pca_models"));
    public static final Table<?> OLS_TEXT_TAGGER = DSL.table(DSL.name("ols_text_tagger"));
    public static final Table<?> INFORMATION_SCHEMA_COLUMNS = DSL.table(DSL.name("information_schema", "columns"));
    public static final Table<?> PG_ATTRIBUTE = DSL.table(DSL.name("pg_catalog", "pg_attribute"));
    public static final Table<?> PG_EXTENSION = DSL.table(DSL.name("pg_catalog", "pg_extension"));

    // Candidate type/root/search constraints do not describe the selected subset's members.
    public static final Set<String> TREE_MEMBER_VISIBILITY_FIELDS = Set.of("isObsolete", "isDefiningOntology");

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

    /** Literal, case-insensitive label containment shared by tree browsing endpoints. */
    public static Condition labelContains(String qualifier, String search) {
        if (search == null || search.isBlank()) return DSL.trueCondition();
        return DSL.exists(DSL.selectOne()
                .from(DSL.unnest(field(qualifier, "label", String[].class)).as("labels", "l"))
                .where(DSL.lower(field("labels", "l", String.class))
                        .contains(search.toLowerCase(java.util.Locale.ROOT))));
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

    /**
     * The members of any selected subset and all context needed to reach them in the tree.
     * The uncorrelated closure is evaluated once, with ontology/IRI pairs preventing leakage.
     */
    public static Condition inSubsetTree(String qualifier, Collection<String> subsets) {
        return inSubsetTree(qualifier, subsets, DSL.trueCondition());
    }

    /** Eligibility uses seed alias m, never connectors. Empty subsets means all eligible members. */
    public static Condition inSubsetTree(String qualifier, Collection<String> subsets, Condition eligibility) {
        return DSL.condition("({0}, {1}) IN (SELECT ontology_id, iri FROM {2} tree_reachability)",
                field(qualifier, "ontology_id", String.class), field(qualifier, "iri", String.class),
                treeReachability(subsetMembers(subsets, eligibility), false));
    }

    private static Condition subsetMembers(Collection<String> subsets, Condition eligibility) {
        if (subsets == null || subsets.isEmpty()) {
            return eligibility;
        }
        return eligibility.and(DSL.condition("{0} && {1}::text[]",
                field("m", "subset", String[].class), DSL.val(subsets.toArray(new String[0]))));
    }

    /**
     * Filter a small, single-ontology candidate set by tree reachability. Both inputs must be
     * scoped to ontologyId. Seed eligibility uses alias m, separately from candidate filters.
     */
    public static Table<?> reachableTreeCandidates(Select<?> candidates, String ontologyId,
            Collection<String> subsets, Condition eligibility) {
        Condition members = subsetMembers(subsets, eligibility);
        return DSL.table("""
                (WITH RECURSIVE candidates AS MATERIALIZED ({0}),
                mixed AS MATERIALIZED (
                    SELECT EXISTS (
                        SELECT 1 FROM ols_entities a WHERE a.ontology_id = {1}
                            AND NOT (coalesce(a.direct_ancestors, '{}'::text[]) = coalesce(a.hierarchical_ancestors, '{}'::text[])
                                  OR coalesce(a.direct_ancestors, '{}'::text[]) <@
                                     coalesce(a.hierarchical_ancestors, '{}'::text[]))
                    ) AND EXISTS (
                        SELECT 1 FROM ols_entities a WHERE a.ontology_id = {1}
                            AND NOT (coalesce(a.direct_ancestors, '{}'::text[]) = coalesce(a.hierarchical_ancestors, '{}'::text[])
                                  OR coalesce(a.hierarchical_ancestors, '{}'::text[]) <@
                                     coalesce(a.direct_ancestors, '{}'::text[]))
                    ) AS value
                ), connectors AS MATERIALIZED (
                    -- Every outgoing link from an eligible row is already in the anchor.
                    SELECT m.ontology_id, m.iri, m.direct_ancestors, m.hierarchical_ancestors
                    FROM ols_entities m
                    WHERE (SELECT value FROM mixed) AND m.ontology_id = {1}
                        AND ({2}) IS NOT TRUE
                        AND (cardinality(m.direct_ancestors) > 0 OR cardinality(m.hierarchical_ancestors) > 0)
                ), reachable(ontology_id, iri) AS (
                    SELECT m.ontology_id, unnest(ARRAY[m.iri] ||
                           CASE WHEN m.direct_ancestors = m.hierarchical_ancestors
                                THEN m.direct_ancestors ELSE m.direct_ancestors || m.hierarchical_ancestors END)
                    FROM ols_entities m WHERE {2}
                    UNION
                    SELECT r.ontology_id, ancestors.iri
                    FROM reachable r
                    JOIN connectors a ON a.ontology_id = r.ontology_id AND a.iri = r.iri
                    CROSS JOIN LATERAL unnest(
                        CASE WHEN a.direct_ancestors = a.hierarchical_ancestors
                             THEN a.direct_ancestors ELSE a.direct_ancestors || a.hierarchical_ancestors END
                    ) ancestors(iri)
                )
                SELECT c.* FROM candidates c
                JOIN reachable r ON r.ontology_id = c.ontology_id AND r.iri = c.iri)
                """, candidates, DSL.val(ontologyId), members);
    }

    /**
     * Count and page the same set of matching entity IDs, with one row carrying the total if the
     * page is empty. Read compressed documents only for the selected IDs. Sort fields refer to p.
     */
    public static Result<Record> fetchEntityPage(DSLContext dsl, Select<?> matching,
            Collection<SortField<?>> orderBy, long offset, int size) {
        Table<?> matchingRows = DSL.table(DSL.name("matching"));
        Table<?> page = DSL.table(DSL.name("page")).as("p");
        var pageIds = dsl.select(DSL.asterisk()).from(matchingRows.as("p"))
                .orderBy(orderBy).offset(offset).limit(size);
        var rows = dsl.select(field("totals", "value", Long.class), field("e", "_json", byte[].class))
                .from(dsl.select(DSL.count().as("value")).from(matchingRows).asTable("totals"))
                .leftJoin(page).on(DSL.trueCondition())
                .leftJoin(OLS_ENTITIES.as("e"))
                .on(field("e", "id", String.class).eq(field("p", "id", String.class)))
                .orderBy(orderBy);
        return dsl.resultQuery("WITH matching AS MATERIALIZED ({0}), page AS ({1}) {2}",
                matching, pageIds, rows).fetch();
    }

    /**
     * Complete tree reachability, including arbitrary transitions between direct/type and
     * hierarchical ancestry. The condition filters seed members (alias m), never connectors.
     *
     * UNION computes a finite fixed point, deduplicating paths and terminating cycles without
     * a depth limit. With perMember=true, member_id preserves distinct stored entity identities
     * for counts and member_iri allows callers to exclude self. Otherwise both are NULL so
     * overlapping members share one closure per ontology, avoiding member-by-ancestor expansion.
     * Ancestor arrays also retain references whose intermediate entity is absent from the store.
     *
     * RDF2JSON materializes each ancestry kind as a transitive closure. When one kind contains
     * the other throughout an ontology (including pure-kind ontologies), their union is already
     * complete. Only genuinely mixed ontologies require recursion. Inspect only seed ontologies,
     * so a small subset does not trigger a scan of every ontology in the store: one index
     * lookup per seed ontology, as a LATERAL join. Written as
     * {@code a.ontology_id IN (SELECT ontology_id FROM members) GROUP BY a.ontology_id} it
     * was planned as a sequential scan of ols_entities hash-joined to the seed ontologies
     * (the planner cannot see how few a materialized CTE holds): 11 M rows and ~18 GB read
     * on a full instance to answer for one ontology of 4,482 entities.
     */
    public static Table<?> treeReachability(Condition members, boolean perMember) {
        return treeReachability(members, perMember, null);
    }

    /** For a broad per-member facet, reuse the rows of the one ontology that contains all seeds. */
    public static Table<?> treeReachability(Condition members, boolean perMember, String scopedOntology) {
        var scopedNodes = scopedOntology == null ? DSL.sql("") : DSL.sql("""
                scoped_nodes AS MATERIALIZED (
                    SELECT ontology_id, iri, direct_ancestors, hierarchical_ancestors
                    FROM ols_entities WHERE ontology_id = {0}
                ),
                """, DSL.val(scopedOntology));
        return DSL.table("""
                (WITH RECURSIVE members AS MATERIALIZED (
                    SELECT m.id, m.iri, m.ontology_id, m.direct_ancestors, m.hierarchical_ancestors
                    FROM ols_entities m WHERE {2}
                ), mixed_ontologies AS MATERIALIZED (
                    SELECT o.ontology_id
                    FROM (SELECT DISTINCT ontology_id FROM members) o
                    CROSS JOIN LATERAL (
                        SELECT bool_or(NOT (coalesce(a.direct_ancestors, '{}'::text[]) = coalesce(a.hierarchical_ancestors, '{}'::text[])
                                            OR coalesce(a.direct_ancestors, '{}'::text[]) <@ coalesce(a.hierarchical_ancestors, '{}'::text[]))) AS direct_only,
                               bool_or(NOT (coalesce(a.direct_ancestors, '{}'::text[]) = coalesce(a.hierarchical_ancestors, '{}'::text[])
                                            OR coalesce(a.hierarchical_ancestors, '{}'::text[]) <@ coalesce(a.direct_ancestors, '{}'::text[]))) AS hierarchical_only
                        FROM ols_entities a WHERE a.ontology_id = o.ontology_id
                    ) kinds
                    WHERE kinds.direct_only AND kinds.hierarchical_only
                ), {4} tree_reach(member_id, member_iri, ontology_id, iri) AS (
                    SELECT {0}, {1}, m.ontology_id,
                           unnest(ARRAY[m.iri] || CASE WHEN m.direct_ancestors = m.hierarchical_ancestors
                               THEN m.direct_ancestors ELSE m.direct_ancestors || m.hierarchical_ancestors END)
                    FROM members m WHERE m.ontology_id IN (SELECT ontology_id FROM mixed_ontologies)
                    UNION
                    SELECT r.member_id, r.member_iri, r.ontology_id, ancestors.iri
                    FROM tree_reach r
                    JOIN {5} a ON a.ontology_id = r.ontology_id AND a.iri = r.iri
                    CROSS JOIN LATERAL unnest(CASE WHEN a.direct_ancestors = a.hierarchical_ancestors
                        THEN a.direct_ancestors ELSE a.direct_ancestors || a.hierarchical_ancestors END) ancestors(iri)
                ) SELECT {3} member_id, member_iri, ontology_id, iri FROM (
                    SELECT {0} AS member_id, {1} AS member_iri, m.ontology_id, ancestors.iri
                    FROM members m
                    CROSS JOIN LATERAL (
                        SELECT DISTINCT unnest(ARRAY[m.iri] || CASE WHEN m.direct_ancestors = m.hierarchical_ancestors
                               THEN m.direct_ancestors ELSE m.direct_ancestors || m.hierarchical_ancestors END) AS iri
                    ) ancestors
                    WHERE m.ontology_id NOT IN (SELECT ontology_id FROM mixed_ontologies)
                    UNION ALL
                    SELECT * FROM tree_reach
                ) complete_reachability)
                """, perMember ? field("m", "id", String.class) : DSL.inline((String) null),
                perMember ? field("m", "iri", String.class) : DSL.inline((String) null), members,
                DSL.sql(perMember ? "" : "DISTINCT"), scopedNodes,
                DSL.sql(scopedOntology == null ? "ols_entities" : "scoped_nodes"));
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
        // The vector arrives as a text parameter and is cast in an uncorrelated subquery, which
        // Postgres evaluates once per execution. Cast in place (`<=> CAST(? AS vector)`) it is
        // evaluated once only while the plan is a custom one, where the parameter is folded to a
        // constant. pgjdbc makes a statement a server-side prepared one after five uses on a
        // connection, and five uses later Postgres may give it a generic plan, which parses the
        // text again for every row whose distance it computes. For a 512-dimension vector that
        // costs far more than fetching the row's vector and computing the distance: ranking the
        // 80,000 vectors of a test ontology took 0.4 s under a custom plan and 9 s under a generic
        // one. A search that ranks many rows could therefore be fast for its first calls on a
        // connection and many times slower afterwards. See GitHub issue #1445.
        return DSL.field("{0} <=> (SELECT CAST({1} AS vector))", SQLDataType.DOUBLE, field, DSL.val(vectorLiteral));
    }

    public static Field<Double> vectorDistance(Field<?> left, Field<?> right) {
        return DSL.field("{0} <=> {1}", SQLDataType.DOUBLE, left, right);
    }

    /**
     * {@code SET LOCAL setting = value} in function form, so that several settings can be applied
     * in one statement. The value lasts until the surrounding transaction ends and so cannot leak
     * into whatever next borrows the pooled connection. Outside a transaction it has no effect.
     */
    public static Field<String> setLocal(String setting, String value) {
        return DSL.function("set_config", SQLDataType.VARCHAR, DSL.inline(setting), DSL.inline(value), DSL.inline(true));
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
