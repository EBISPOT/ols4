package uk.ac.ebi.spot.ols.repository.search;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.service.PostgresClient;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.INFORMATION_SCHEMA_COLUMNS;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.OLS_AUTOSUGGEST;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.OLS_ENTITIES;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.arrayContains;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.castAsText;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.field;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.similarity;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.trigramMatch;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.unnest;

/**
 * Executes search queries against PostgreSQL.
 */
@Component
public class OlsSearchClient {

    private static final Logger logger = LoggerFactory.getLogger(OlsSearchClient.class);
    private static final Field<String> COLUMN_NAME = field("column_name", String.class);
    private static final Field<String> ENTITY_ID = field("id", String.class);
    private static final Field<byte[]> ENTITY_JSON = field("_json", byte[].class);
    private static final Field<String> ENTITY_IRI = field("iri", String.class);
    private static final String GROUP_HEAD_ID = "group_head_id";
    private static final String GROUP_SCORE = "group_score";
    private static final String GROUP_TOTAL = "group_total";
    private static final Field<String> LABEL_FOR_SUGGEST = field("label_for_suggest", String.class);
    private static final Field<String> STRING_VALUE = field("string", String.class);
    private static final Field<String> ONTOLOGY_ID = field("ontology_id", String.class);
    private static final Field<String[]> CURATED_FROM_SOURCES = field("curated_from_sources", String[].class);

    @Autowired
    private PostgresClient postgresClient;

    private volatile Set<String> availableFilterColumns;

    private Set<String> getAvailableFilterColumns() {
        if (availableFilterColumns == null) {
            synchronized (this) {
                if (availableFilterColumns == null) {
                    availableFilterColumns = loadFilterColumns();
                }
            }
        }
        return availableFilterColumns;
    }

    private Set<String> loadFilterColumns() {
        Set<String> cols = new HashSet<>();
        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            var records = dsl.select(COLUMN_NAME)
                    .from(INFORMATION_SCHEMA_COLUMNS)
                    .where(field("table_name", String.class).eq("ols_entities"))
                    .and(COLUMN_NAME.like("filter\\_%", '\\'))
                    .fetch();
            for (Record record : records) {
                cols.add(record.get(COLUMN_NAME));
            }
        } catch (SQLException e) {
            logger.warn("Failed to load filter columns: {}", e.getMessage());
        }
        return cols;
    }

    /**
     * Paginated search with faceting support.
     */
    public OlsFacetedResultsPage<JsonElement> searchPaginated(OlsSearchQuery query, Pageable pageable) {
        return searchPaginated(query, pageable, true);
    }

    /**
     * Paginated search with optional total counting. When totals are omitted, the returned total is
     * limited to the current page boundary; this mode is intended for clients such as autocomplete
     * that only consume the result elements.
     */
    public OlsFacetedResultsPage<JsonElement> searchPaginated(
            OlsSearchQuery query, Pageable pageable, boolean includeTotal) {
        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Condition where = query.buildCondition(getAvailableFilterColumns());
            List<SortField<?>> orderBy = query.buildOrderBy();

            Long count = includeTotal
                    ? Optional.ofNullable(dsl.selectCount()
                            .from(OLS_ENTITIES)
                            .where(where)
                            .fetchOne(0, Long.class))
                            .orElse(0L)
                    : null;

            logger.debug("search condition: {}", where);

            var records = dsl.select(ENTITY_JSON)
                    .from(OLS_ENTITIES)
                    .where(where)
                    .orderBy(orderBy)
                    .offset(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .fetch();

            List<JsonElement> results = readJsonResults(records);

            long total = count != null ? count : pageable.getOffset() + results.size();

            Map<String, Map<String, Long>> facetFieldToCounts = new LinkedHashMap<>();
            for (String facetField : query.facetFields) {
                facetFieldToCounts.put(facetField, executeFacetQuery(dsl, facetField, query));
            }

            return new OlsFacetedResultsPage<>(results, facetFieldToCounts, pageable, total);
        } catch (SQLException e) {
            throw new RuntimeException("Search query failed", e);
        }
    }

    /**
     * Get first matching result.
     */
    public JsonElement getFirst(OlsSearchQuery query) {
        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Record record = dsl.select(ENTITY_JSON)
                    .from(OLS_ENTITIES)
                    .where(query.buildCondition(getAvailableFilterColumns()))
                    .orderBy(query.buildOrderBy())
                    .limit(1)
                    .fetchOne();

            if (record == null) {
                return null;
            }

            return JsonParser.parseString(PostgresClient.decompressJson(record.get(ENTITY_JSON)));
        } catch (SQLException e) {
            throw new RuntimeException("getFirst query failed", e);
        }
    }

    /**
     * Get counts grouped by a field, for statistics.
     */
    public Map<String, Long> getCountsByField(String fieldName) {
        Field<?> groupField = buildFacetField(null, fieldName);
        Field<String> keyField = buildFacetKeyField(null, fieldName);
        Map<String, Long> counts = new LinkedHashMap<>();

        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            var records = dsl.select(keyField, DSL.count())
                    .from(OLS_ENTITIES)
                    .groupBy(groupField)
                    .fetch();

            for (Record record : records) {
                String key = record.get(keyField);
                if (key != null) {
                    counts.put(key, record.get(1, Long.class));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Counts query failed", e);
        }

        return counts;
    }

    /**
     * Autocomplete/suggest: find entities whose label matches a prefix via trigram similarity.
     * Results are grouped by label to deduplicate.
     */
    public OlsFacetedResultsPage<JsonElement> suggest(String prefix, Pageable pageable) {
        if (prefix == null || prefix.isBlank()) {
            return new OlsFacetedResultsPage<>(List.of(), Map.of(), pageable, 0);
        }

        Field<Double> simExpr = similarity(LABEL_FOR_SUGGEST, prefix);
        Field<Double> sim = simExpr.as("sim");

        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            long count = Optional.ofNullable(dsl.select(DSL.countDistinct(LABEL_FOR_SUGGEST))
                            .from(OLS_ENTITIES)
                            .where(trigramMatch(LABEL_FOR_SUGGEST, prefix))
                            .fetchOne(0, Long.class))
                    .orElse(0L);

            Field<Integer> rn = DSL.rowNumber()
                    .over(DSL.partitionBy(LABEL_FOR_SUGGEST).orderBy(simExpr.desc(), ENTITY_ID.asc()))
                    .as("rn");
            Table<?> ranked = dsl.select(LABEL_FOR_SUGGEST, ENTITY_JSON, sim, rn)
                    .from(OLS_ENTITIES)
                    .where(trigramMatch(LABEL_FOR_SUGGEST, prefix))
                    .asTable("ranked");
            Field<byte[]> rankedJson = field("ranked", "_json", byte[].class);
            Field<String> rankedLabel = field("ranked", "label_for_suggest", String.class);
            Field<Double> rankedSim = field("ranked", "sim", Double.class);

            var records = dsl.select(rankedJson)
                    .from(ranked)
                    .where(field("ranked", "rn", Integer.class).eq(1))
                    .orderBy(rankedLabel.asc(), rankedSim.desc())
                    .limit(pageable.getPageSize())
                    .offset(pageable.getOffset())
                    .fetch();

            return new OlsFacetedResultsPage<>(readJsonResults(records, rankedJson), Map.of(), pageable, count);
        } catch (SQLException e) {
            throw new RuntimeException("Suggest query failed", e);
        }
    }

    /**
     * Execute a facet count query for a single field.
     * Uses the query's filters EXCLUDING the facet field itself (facet exclusion behavior).
     */
    private Map<String, Long> executeFacetQuery(DSLContext dsl, String facetField, OlsSearchQuery query) {
        String column = OlsSearchQuery.resolveColumn(facetField);
        OlsSearchQuery.ColumnType colType = OlsSearchQuery.resolveColumnType(facetField);

        if (!OlsSearchQuery.isKnownColumn(facetField) && !getAvailableFilterColumns().contains(column)) {
            return Map.of();
        }

        Condition where = query.buildCondition(getAvailableFilterColumns(), null, facetField);
        Map<String, Long> counts = new LinkedHashMap<>();

        if (colType == OlsSearchQuery.ColumnType.TEXT_ARRAY) {
            Field<String[]> arrayField = field(column, String[].class);
            Table<?> facetValues = unnest(arrayField, "facet_values", "v");
            Field<String> valueField = field("facet_values", "v", String.class);
            var records = dsl.select(valueField, DSL.count())
                    .from(OLS_ENTITIES)
                    .crossJoin(facetValues)
                    .where(where)
                    .groupBy(valueField)
                    .orderBy(DSL.count().desc())
                    .fetch();

            for (Record record : records) {
                String key = record.get(valueField);
                if (key != null) {
                    counts.put(key, record.get(1, Long.class));
                }
            }
            return counts;
        }

        Field<?> facetColumn = buildFacetField(null, facetField);
        Field<String> facetKey = buildFacetKeyField(null, facetField);
        var records = dsl.select(facetKey, DSL.count())
                .from(OLS_ENTITIES)
                .where(where)
                .groupBy(facetColumn)
                .orderBy(DSL.count().desc())
                .fetch();

        for (Record record : records) {
            String key = record.get(facetKey);
            if (key != null) {
                counts.put(key, record.get(1, Long.class));
            }
        }

        return counts;
    }

    /**
     * Raw paginated search returning _json strings, total count, and facet maps.
     * Used by V1 controllers that build their own response format.
     */
    public RawSearchResult searchRaw(OlsSearchQuery query, int start, int rows) {
        return searchRaw(query, start, rows, false);
    }

    /**
     * Raw paginated search, optionally collapsing results so that only one entity per distinct IRI
     * is returned.
     *
     * <p>The same term is usually present in many ontologies (a defining ontology plus every
     * ontology that imports it), all sharing one IRI. Collapsing keeps the best-ranked entity of
     * each IRI group — which, because {@link OlsSearchQuery#buildOrderBy} boosts defining
     * ontologies, is normally the entity from the ontology that defines the term. {@code numFound}
     * becomes the number of distinct IRIs rather than the number of entities. This backs the v1
     * {@code groupField} search parameter (GitHub issue #1333).
     *
     * <p>Facet counts stay entity-level even when collapsing, so the ontology facet still answers
     * "which ontologies contain this term" for a grouped result.
     */
    public RawSearchResult searchRaw(OlsSearchQuery query, int start, int rows, boolean collapseByIri) {
        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Condition where = query.buildCondition(getAvailableFilterColumns());
            List<SortField<?>> orderBy = query.buildOrderBy();

            long count;
            org.jooq.Result<? extends Record> records;

            if (collapseByIri) {
                records = fetchCollapsedByIri(dsl, query, where, orderBy, start, rows);
                // The number of IRI groups is carried out of the page query itself; it is only
                // unavailable when the page came back empty, which is the one case cheap enough
                // to count separately.
                count = records.isEmpty()
                        ? countDistinctIris(dsl, where)
                        : records.get(0).get(GROUP_TOTAL, Long.class);
            } else {
                count = Optional.ofNullable(dsl.selectCount()
                                .from(OLS_ENTITIES)
                                .where(where)
                                .fetchOne(0, Long.class))
                        .orElse(0L);

                records = dsl.select(ENTITY_JSON)
                        .from(OLS_ENTITIES)
                        .where(where)
                        .orderBy(orderBy)
                        .offset(start)
                        .limit(rows)
                        .fetch();
            }

            List<String> jsonStrings = readJsonStrings(records);

            Map<String, Map<String, Long>> facets = new LinkedHashMap<>();
            for (String facetField : query.facetFields) {
                facets.put(facetField, executeFacetQuery(dsl, facetField, query));
            }

            return new RawSearchResult(jsonStrings, count, facets);
        } catch (SQLException e) {
            throw new RuntimeException("Raw search query failed", e);
        }
    }

    /**
     * Fetch one row per distinct IRI: rank the matches within each IRI group using the query's own
     * ordering, keep the first of each group, page over those group heads, and only then join the
     * compressed {@code _json} payload back on.
     *
     * <p>Ordering and paging happen on id/iri/score alone so that the {@code LIMIT} is applied
     * before the payload lookup — otherwise PostgreSQL joins {@code _json} for every group head in
     * the result set (hundreds of thousands of primary key lookups for a broad query) just to
     * return ten rows.
     *
     * <p>Entities from a defining ontology win their group: the query ordering already boosts them
     * when there is search text to rank, and the explicit tiebreak keeps that true for filter-only
     * queries, which are ordered by id alone.
     */
    private org.jooq.Result<? extends Record> fetchCollapsedByIri(DSLContext dsl, OlsSearchQuery query,
                                                                 Condition where,
                                                                 List<SortField<?>> orderBy,
                                                                 int start, int rows) {
        List<SortField<?>> groupHeadOrder = new ArrayList<>();
        groupHeadOrder.add(field("is_defining_ontology", Boolean.class).desc());
        groupHeadOrder.addAll(orderBy);

        Field<Double> rank = query.buildRankExpression(null);
        Field<Integer> rn = DSL.rowNumber()
                .over(DSL.partitionBy(ENTITY_IRI).orderBy(groupHeadOrder))
                .as("rn");

        List<Field<?>> rankedFields = new ArrayList<>();
        rankedFields.add(ENTITY_ID.as(GROUP_HEAD_ID));
        if (rank != null) {
            rankedFields.add(rank.as(GROUP_SCORE));
        }
        rankedFields.add(rn);

        Table<?> ranked = dsl.select(rankedFields)
                .from(OLS_ENTITIES)
                .where(where)
                .asTable("ranked");

        List<Field<?>> headFields = new ArrayList<>();
        headFields.add(field("ranked", GROUP_HEAD_ID, String.class));
        if (rank != null) {
            headFields.add(field("ranked", GROUP_SCORE, Double.class));
        }
        // Counted over the group heads before the LIMIT applies, so the page query also yields the
        // total number of IRI groups without a second pass over the matches.
        headFields.add(DSL.count().over().cast(Long.class).as(GROUP_TOTAL));

        Table<?> groupHeads = dsl.select(headFields)
                .from(ranked)
                .where(field("ranked", "rn", Integer.class).eq(1))
                .orderBy(collapsedOrderBy("ranked", rank != null))
                .offset(start)
                .limit(rows)
                .asTable("group_heads");

        return dsl.select(ENTITY_JSON, field("group_heads", GROUP_TOTAL, Long.class))
                .from(OLS_ENTITIES)
                .join(groupHeads)
                .on(field("group_heads", GROUP_HEAD_ID, String.class).eq(ENTITY_ID))
                .orderBy(collapsedOrderBy("group_heads", rank != null))
                .fetch();
    }

    private long countDistinctIris(DSLContext dsl, Condition where) {
        return Optional.ofNullable(dsl.select(DSL.countDistinct(ENTITY_IRI))
                        .from(OLS_ENTITIES)
                        .where(where)
                        .fetchOne(0, Long.class))
                .orElse(0L);
    }

    /**
     * Ordering over already-collapsed group heads: by the score column carried out of the ranking
     * subquery, falling back to id for filter-only queries that have no score.
     */
    private static List<SortField<?>> collapsedOrderBy(String qualifier, boolean hasScore) {
        Field<String> id = field(qualifier, GROUP_HEAD_ID, String.class);
        if (!hasScore) {
            return List.of(id.asc());
        }
        return List.of(field(qualifier, GROUP_SCORE, Double.class).desc(), id.asc());
    }

    /**
     * Suggest with ontology filtering.
     */
    public List<String> suggestLabels(String prefix, List<String> ontologyIds, int start, int rows) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }

        Condition where = trigramMatch(STRING_VALUE, prefix);
        if (ontologyIds != null && !ontologyIds.isEmpty()) {
            where = where.and(ONTOLOGY_ID.in(ontologyIds));
        }

        Field<Double> sim = similarity(STRING_VALUE, prefix).as("sim");

        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            var records = dsl.selectDistinct(STRING_VALUE, sim)
                    .from(OLS_AUTOSUGGEST)
                    .where(where)
                    .orderBy(sim.desc(), STRING_VALUE.asc())
                    .offset(start)
                    .limit(rows)
                    .fetch();

            List<String> labels = new ArrayList<>();
            for (Record record : records) {
                labels.add(record.get(STRING_VALUE));
            }
            return labels;
        } catch (SQLException e) {
            throw new RuntimeException("suggestLabels query failed", e);
        }
    }

    /**
     * Get the most recent data load date from the database.
     * Reads the "loaded" field from each ontology's JSON to find when data was last released,
     * rather than using the current timestamp (which would reflect deployment time, not data time).
     */
    public String getLastModified() {
        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Field<String> typeField = field("type", String.class);
            var records = dsl.select(ENTITY_JSON)
                    .from(OLS_ENTITIES)
                    .where(typeField.eq("Ontology"))
                    .fetch();

            String maxLoaded = null;
            for (Record record : records) {
                try {
                    String json = PostgresClient.decompressJson(record.get(ENTITY_JSON));
                    JsonElement loadedEl = JsonParser.parseString(json).getAsJsonObject().get("loaded");
                    if (loadedEl != null && !loadedEl.isJsonNull()) {
                        String loaded = extractLoadedValue(loadedEl);
                        if (loaded != null && (maxLoaded == null || loaded.compareTo(maxLoaded) > 0)) {
                            maxLoaded = loaded;
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to extract loaded date from ontology JSON: {}", e.getMessage());
                }
            }
            return maxLoaded;
        } catch (SQLException e) {
            throw new RuntimeException("getLastModified failed", e);
        }
    }

    private static String extractLoadedValue(JsonElement el) {
        if (el.isJsonObject()) {
            JsonElement value = el.getAsJsonObject().get("value");
            return value != null && !value.isJsonNull() ? value.getAsString() : null;
        } else if (el.isJsonArray()) {
            if (!el.getAsJsonArray().isEmpty()) {
                return extractLoadedValue(el.getAsJsonArray().get(0));
            }
        } else if (el.isJsonPrimitive()) {
            return el.getAsString();
        }
        return null;
    }

    /**
     * Get all distinct curated_from_sources values.
     */
    public List<String> getDistinctCuratedSources() {
        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Table<?> sources = unnest(CURATED_FROM_SOURCES, "curated_sources", "v");
            Field<String> sourceField = field("curated_sources", "v", String.class);
            var records = dsl.selectDistinct(sourceField)
                    .from(OLS_ENTITIES)
                    .crossJoin(sources)
                    .orderBy(sourceField.asc())
                    .fetch();

            List<String> values = new ArrayList<>();
            for (Record record : records) {
                values.add(record.get(sourceField));
            }
            return values;
        } catch (SQLException e) {
            throw new RuntimeException("getDistinctCuratedSources failed", e);
        }
    }

    /**
     * Raw search result container for V1 controllers.
     */
    public static class RawSearchResult {
        public final List<String> jsonStrings;
        public final long numFound;
        public final Map<String, Map<String, Long>> facets;

        public RawSearchResult(List<String> jsonStrings, long numFound, Map<String, Map<String, Long>> facets) {
            this.jsonStrings = jsonStrings;
            this.numFound = numFound;
            this.facets = facets;
        }
    }

    private Field<?> buildFacetField(String qualifier, String fieldName) {
        String column = OlsSearchQuery.resolveColumn(fieldName);
        return switch (OlsSearchQuery.resolveColumnType(fieldName)) {
            case BOOLEAN -> field(qualifier, column, Boolean.class);
            case TEXT_ARRAY -> field(qualifier, column, String[].class);
            case TEXT -> field(qualifier, column, String.class);
        };
    }

    private Field<String> buildFacetKeyField(String qualifier, String fieldName) {
        return switch (OlsSearchQuery.resolveColumnType(fieldName)) {
            case TEXT -> field(qualifier, OlsSearchQuery.resolveColumn(fieldName), String.class);
            case BOOLEAN, TEXT_ARRAY -> castAsText(buildFacetField(qualifier, fieldName)).as("facet_key");
        };
    }

    private List<JsonElement> readJsonResults(org.jooq.Result<? extends Record> records) throws SQLException {
        return readJsonResults(records, ENTITY_JSON);
    }

    private List<JsonElement> readJsonResults(org.jooq.Result<? extends Record> records, Field<byte[]> jsonField) throws SQLException {
        List<JsonElement> results = new ArrayList<>(records.size());
        for (Record record : records) {
            results.add(JsonParser.parseString(PostgresClient.decompressJson(record.get(jsonField))));
        }
        return results;
    }

    private List<String> readJsonStrings(org.jooq.Result<? extends Record> records) throws SQLException {
        List<String> results = new ArrayList<>(records.size());
        for (Record record : records) {
            results.add(PostgresClient.decompressJson(record.get(ENTITY_JSON)));
        }
        return results;
    }
}
