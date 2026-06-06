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
        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Condition where = query.buildCondition(getAvailableFilterColumns());
            List<SortField<?>> orderBy = query.buildOrderBy();

            long count = Optional.ofNullable(dsl.selectCount()
                            .from(OLS_ENTITIES)
                            .where(where)
                            .fetchOne(0, Long.class))
                    .orElse(0L);

            logger.debug("search condition: {}", where);

            var records = dsl.select(ENTITY_JSON)
                    .from(OLS_ENTITIES)
                    .where(where)
                    .orderBy(orderBy)
                    .offset(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .fetch();

            List<JsonElement> results = readJsonResults(records);

            Map<String, Map<String, Long>> facetFieldToCounts = new LinkedHashMap<>();
            for (String facetField : query.facetFields) {
                facetFieldToCounts.put(facetField, executeFacetQuery(dsl, facetField, query));
            }

            return new OlsFacetedResultsPage<>(results, facetFieldToCounts, pageable, count);
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
        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Condition where = query.buildCondition(getAvailableFilterColumns());
            List<SortField<?>> orderBy = query.buildOrderBy();

            long count = Optional.ofNullable(dsl.selectCount()
                            .from(OLS_ENTITIES)
                            .where(where)
                            .fetchOne(0, Long.class))
                    .orElse(0L);

            var records = dsl.select(ENTITY_JSON)
                    .from(OLS_ENTITIES)
                    .where(where)
                    .orderBy(orderBy)
                    .offset(start)
                    .limit(rows)
                    .fetch();

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
