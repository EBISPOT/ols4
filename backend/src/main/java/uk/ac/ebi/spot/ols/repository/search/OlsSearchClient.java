package uk.ac.ebi.spot.ols.repository.search;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.service.PostgresClient;

import java.sql.*;
import java.sql.Timestamp;
import java.util.*;

/**
 * Executes search queries against PostgreSQL.
 * All queries use parameterized PreparedStatements.
 */
@Component
public class OlsSearchClient {

    private static final Logger logger = LoggerFactory.getLogger(OlsSearchClient.class);

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
        String sql = "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'ols_entities' AND column_name LIKE 'filter\\_%'";
        try (Connection conn = postgresClient.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                cols.add("\"" + rs.getString("column_name") + "\"");
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
        OlsSearchQuery.WhereClause wc = query.buildWhereClause(getAvailableFilterColumns());
        String orderBy = query.buildOrderBy();
        List<Object> orderParams = query.buildOrderByParams();

        // Build data query
        String dataSql = "SELECT _json FROM ols_entities WHERE TRUE" + wc.sql + orderBy
                + " OFFSET ? LIMIT ?";

        List<Object> dataParams = new ArrayList<>(wc.params);
        dataParams.addAll(orderParams);
        dataParams.add((long) pageable.getOffset());
        dataParams.add(pageable.getPageSize());

        // Build count query
        String countSql = "SELECT COUNT(*) FROM ols_entities WHERE TRUE" + wc.sql;

        logger.debug("search sql: {}", dataSql);
        logger.debug("search params: {}", wc.params);

        try (Connection conn = postgresClient.getConnection()) {
            // Execute count
            long count;
            try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
                setParams(countStmt, wc.params);
                try (ResultSet rs = countStmt.executeQuery()) {
                    rs.next();
                    count = rs.getLong(1);
                }
            }

            // Execute data
            List<JsonElement> results = new ArrayList<>();
            try (PreparedStatement dataStmt = conn.prepareStatement(dataSql)) {
                setParams(dataStmt, dataParams);
                try (ResultSet rs = dataStmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(JsonParser.parseString(PostgresClient.decompressJson(rs, 1)));
                    }
                }
            }

            // Execute facets
            Map<String, Map<String, Long>> facetFieldToCounts = new LinkedHashMap<>();
            for (String facetField : query.facetFields) {
                facetFieldToCounts.put(facetField, executeFacetQuery(conn, facetField, query));
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
        OlsSearchQuery.WhereClause wc = query.buildWhereClause(getAvailableFilterColumns());
        String orderBy = query.buildOrderBy();
        List<Object> orderParams = query.buildOrderByParams();

        String sql = "SELECT _json FROM ols_entities WHERE TRUE" + wc.sql + orderBy + " LIMIT 1";

        List<Object> params = new ArrayList<>(wc.params);
        params.addAll(orderParams);

        try (Connection conn = postgresClient.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            setParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                return JsonParser.parseString(PostgresClient.decompressJson(rs, 1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("getFirst query failed", e);
        }
    }

    /**
     * Get counts grouped by a field, for statistics.
     */
    public Map<String, Long> getCountsByField(String field) {
        String column = OlsSearchQuery.resolveColumn(field);
        String sql = "SELECT " + column + ", COUNT(*) FROM ols_entities GROUP BY " + column;

        Map<String, Long> counts = new LinkedHashMap<>();
        try (Connection conn = postgresClient.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString(1);
                if (key != null) {
                    counts.put(key, rs.getLong(2));
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
        String sql = "SELECT DISTINCT ON (label_for_suggest) _json, similarity(label_for_suggest, ?) AS sim"
                + " FROM ols_entities"
                + " WHERE label_for_suggest % ?"
                + " ORDER BY label_for_suggest, sim DESC"
                + " LIMIT ? OFFSET ?";

        String countSql = "SELECT COUNT(DISTINCT label_for_suggest) FROM ols_entities WHERE label_for_suggest % ?";

        try (Connection conn = postgresClient.getConnection()) {
            long count;
            try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
                countStmt.setString(1, prefix);
                try (ResultSet rs = countStmt.executeQuery()) {
                    rs.next();
                    count = rs.getLong(1);
                }
            }

            List<JsonElement> results = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, prefix);
                stmt.setString(2, prefix);
                stmt.setInt(3, pageable.getPageSize());
                stmt.setLong(4, pageable.getOffset());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(JsonParser.parseString(PostgresClient.decompressJson(rs, 1)));
                    }
                }
            }

            return new OlsFacetedResultsPage<>(results, Map.of(), pageable, count);

        } catch (SQLException e) {
            throw new RuntimeException("Suggest query failed", e);
        }
    }

    /**
     * Execute a facet count query for a single field.
     * Uses the query's filters EXCLUDING the facet field itself (facet exclusion behavior).
     */
    private Map<String, Long> executeFacetQuery(Connection conn, String facetField, OlsSearchQuery query) throws SQLException {
        String column = OlsSearchQuery.resolveColumn(facetField);
        OlsSearchQuery.ColumnType colType = OlsSearchQuery.resolveColumnType(facetField);

        // Skip facet entirely if the column doesn't exist
        if (!OlsSearchQuery.isKnownColumn(facetField) && !getAvailableFilterColumns().contains(column)) {
            return Map.of();
        }

        // Build WHERE clause excluding filters on this facet field
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();

        // FTS condition
        if (query.searchText != null && !query.searchText.isBlank()) {
            if (query.exactMatch) {
                where.append(" AND ts_search @@ phraseto_tsquery('english', ?)");
            } else {
                where.append(" AND ts_search @@ websearch_to_tsquery('english', ?)");
            }
            params.add(query.searchText);
        }

        // Filters excluding the facet field (also skip missing dynamic filter columns)
        Set<String> filterCols = getAvailableFilterColumns();
        for (OlsSearchQuery.SearchFilter f : query.filters) {
            if (!f.field.equals(facetField)) {
                if (!OlsSearchQuery.isKnownColumn(f.field)
                        && !filterCols.contains(OlsSearchQuery.resolveColumn(f.field))) {
                    continue;
                }
                appendFilterCondition(where, params, f);
            }
        }
        for (OlsSearchQuery.SearchFilter f : query.excludeFilters) {
            if (!OlsSearchQuery.isKnownColumn(f.field)
                    && !filterCols.contains(OlsSearchQuery.resolveColumn(f.field))) {
                continue;
            }
            appendExcludeFilterCondition(where, params, f);
        }

        String sql;
        if (colType == OlsSearchQuery.ColumnType.TEXT_ARRAY) {
            // Unnest array and count distinct values
            sql = "SELECT v, COUNT(*) FROM ols_entities, unnest(" + column + ") AS v WHERE TRUE"
                    + where + " GROUP BY v ORDER BY COUNT(*) DESC";
        } else {
            sql = "SELECT " + column + ", COUNT(*) FROM ols_entities WHERE TRUE"
                    + where + " GROUP BY " + column + " ORDER BY COUNT(*) DESC";
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            setParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString(1);
                    if (key != null) {
                        counts.put(key, rs.getLong(2));
                    }
                }
            }
        }
        return counts;
    }

    private void appendFilterCondition(StringBuilder where, List<Object> params, OlsSearchQuery.SearchFilter f) {
        String column = OlsSearchQuery.resolveColumn(f.field);
        OlsSearchQuery.ColumnType colType = OlsSearchQuery.resolveColumnType(f.field);

        if (colType == OlsSearchQuery.ColumnType.BOOLEAN) {
            String val = f.values.iterator().next();
            where.append(" AND ").append(column).append(" = ?");
            params.add("true".equalsIgnoreCase(val));
        } else if (colType == OlsSearchQuery.ColumnType.TEXT_ARRAY) {
            where.append(" AND (");
            int i = 0;
            for (String val : f.values) {
                if (i++ > 0) where.append(" OR ");
                where.append("? = ANY(").append(column).append(")");
                params.add(val);
            }
            where.append(")");
        } else {
            if (f.values.size() == 1) {
                where.append(" AND ").append(column).append(" = ?");
                params.add(f.values.iterator().next());
            } else {
                where.append(" AND ").append(column).append(" = ANY(?)");
                params.add(f.values.toArray(new String[0]));
            }
        }
    }

    private void appendExcludeFilterCondition(StringBuilder where, List<Object> params, OlsSearchQuery.SearchFilter f) {
        String column = OlsSearchQuery.resolveColumn(f.field);
        OlsSearchQuery.ColumnType colType = OlsSearchQuery.resolveColumnType(f.field);

        if (colType == OlsSearchQuery.ColumnType.BOOLEAN) {
            String val = f.values.iterator().next();
            where.append(" AND NOT (").append(column).append(" = ?)");
            params.add("true".equalsIgnoreCase(val));
        } else if (colType == OlsSearchQuery.ColumnType.TEXT_ARRAY) {
            where.append(" AND NOT (");
            int i = 0;
            for (String val : f.values) {
                if (i++ > 0) where.append(" OR ");
                where.append("? = ANY(").append(column).append(")");
                params.add(val);
            }
            where.append(")");
        } else {
            if (f.values.size() == 1) {
                where.append(" AND NOT (").append(column).append(" = ?)");
                params.add(f.values.iterator().next());
            } else {
                where.append(" AND NOT (").append(column).append(" = ANY(?))");
                params.add(f.values.toArray(new String[0]));
            }
        }
    }

    /**
     * Raw paginated search returning _json strings, total count, and facet maps.
     * Used by V1 controllers that build their own response format.
     */
    public RawSearchResult searchRaw(OlsSearchQuery query, int start, int rows) {
        OlsSearchQuery.WhereClause wc = query.buildWhereClause(getAvailableFilterColumns());
        String orderBy = query.buildOrderBy();
        List<Object> orderParams = query.buildOrderByParams();

        String dataSql = "SELECT _json FROM ols_entities WHERE TRUE" + wc.sql + orderBy
                + " OFFSET ? LIMIT ?";

        List<Object> dataParams = new ArrayList<>(wc.params);
        dataParams.addAll(orderParams);
        dataParams.add((long) start);
        dataParams.add(rows);

        String countSql = "SELECT COUNT(*) FROM ols_entities WHERE TRUE" + wc.sql;

        logger.debug("searchRaw sql: {}", dataSql);
        logger.debug("searchRaw params: {}", dataParams);

        try (Connection conn = postgresClient.getConnection()) {
            long count;
            try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
                setParams(countStmt, wc.params);
                try (ResultSet rs = countStmt.executeQuery()) {
                    rs.next();
                    count = rs.getLong(1);
                }
            }

            List<String> jsonStrings = new ArrayList<>();
            try (PreparedStatement dataStmt = conn.prepareStatement(dataSql)) {
                setParams(dataStmt, dataParams);
                try (ResultSet rs = dataStmt.executeQuery()) {
                    while (rs.next()) {
                        jsonStrings.add(PostgresClient.decompressJson(rs, 1));
                    }
                }
            }

            // Facets
            Map<String, Map<String, Long>> facets = new LinkedHashMap<>();
            for (String facetField : query.facetFields) {
                facets.put(facetField, executeFacetQuery(conn, facetField, query));
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
        StringBuilder where = new StringBuilder(" WHERE string % ?");
        List<Object> params = new ArrayList<>();
        params.add(prefix);

        if (ontologyIds != null && !ontologyIds.isEmpty()) {
            where.append(" AND ontology_id = ANY(?)");
            params.add(ontologyIds.toArray(new String[0]));
        }

        String sql = "SELECT DISTINCT string, similarity(string, ?) AS sim"
                + " FROM ols_autosuggest" + where
                + " ORDER BY sim DESC, string ASC"
                + " OFFSET ? LIMIT ?";

        List<Object> allParams = new ArrayList<>();
        allParams.add(prefix); // for similarity()
        allParams.addAll(params);
        allParams.add((long) start);
        allParams.add(rows);

        try (Connection conn = postgresClient.getConnection()) {
            List<String> labels = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                setParams(stmt, allParams);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        labels.add(rs.getString(1));
                    }
                }
            }
            return labels;
        } catch (SQLException e) {
            throw new RuntimeException("suggestLabels query failed", e);
        }
    }

    /**
     * Get last modification time from the database.
     * Uses the transaction timestamp of the current connection as a proxy.
     */
    public String getLastModified() {
        String sql = "SELECT NOW()";
        try (Connection conn = postgresClient.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                Timestamp ts = rs.getTimestamp(1);
                return ts != null ? ts.toInstant().toString() : null;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("getLastModified failed", e);
        }
    }

    /**
     * Get all distinct curated_from_sources values.
     */
    public List<String> getDistinctCuratedSources() {
        String sql = "SELECT DISTINCT v FROM ols_entities, unnest(curated_from_sources) AS v ORDER BY v";
        List<String> sources = new ArrayList<>();
        try (Connection conn = postgresClient.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                sources.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("getDistinctCuratedSources failed", e);
        }
        return sources;
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

    private void setParams(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object val = params.get(i);
            if (val == null) {
                stmt.setNull(i + 1, Types.VARCHAR);
            } else if (val instanceof String) {
                stmt.setString(i + 1, (String) val);
            } else if (val instanceof Boolean) {
                stmt.setBoolean(i + 1, (Boolean) val);
            } else if (val instanceof Integer) {
                stmt.setInt(i + 1, (Integer) val);
            } else if (val instanceof Long) {
                stmt.setLong(i + 1, (Long) val);
            } else if (val instanceof String[]) {
                stmt.setArray(i + 1, stmt.getConnection().createArrayOf("text", (String[]) val));
            } else {
                stmt.setObject(i + 1, val);
            }
        }
    }
}
