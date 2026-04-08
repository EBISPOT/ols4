package uk.ac.ebi.spot.ols.repository.search;

import java.util.*;

/**
 * Builds a parameterized PostgreSQL query for entity search/filter operations.
 * Translates OLS field names to Postgres columns.
 */
public class OlsSearchQuery {

    String searchText = null;
    boolean exactMatch = false;
    List<SearchFilter> filters = new ArrayList<>();
    List<SearchFilter> excludeFilters = new ArrayList<>();
    List<String> facetFields = new ArrayList<>();

    enum ColumnType { TEXT, BOOLEAN, TEXT_ARRAY }

    static final Map<String, String> COLUMN_MAP;
    static final Map<String, ColumnType> COLUMN_TYPES;

    static {
        Map<String, String> cols = new LinkedHashMap<>();
        Map<String, ColumnType> types = new LinkedHashMap<>();

        cols.put("type", "search_type");               types.put("type", ColumnType.TEXT);
        cols.put("ontologyId", "ontology_id");          types.put("ontologyId", ColumnType.TEXT);
        cols.put("iri", "iri");                         types.put("iri", ColumnType.TEXT);
        cols.put("shortForm", "short_form");            types.put("shortForm", ColumnType.TEXT);
        cols.put("curie", "curie");                     types.put("curie", ColumnType.TEXT);
        cols.put("oboId", "curie");                     types.put("oboId", ColumnType.TEXT);
        cols.put("isObsolete", "is_obsolete");          types.put("isObsolete", ColumnType.BOOLEAN);
        cols.put("isDefiningOntology", "is_defining_ontology");  types.put("isDefiningOntology", ColumnType.BOOLEAN);
        cols.put("hasDirectParents", "has_direct_parents");      types.put("hasDirectParents", ColumnType.BOOLEAN);
        cols.put("hasHierarchicalParents", "has_hierarchical_parents"); types.put("hasHierarchicalParents", ColumnType.BOOLEAN);
        cols.put("hasDirectChildren", "has_direct_children");    types.put("hasDirectChildren", ColumnType.BOOLEAN);
        cols.put("hasChildren", "has_direct_children");              types.put("hasChildren", ColumnType.BOOLEAN);
        cols.put("hasHierarchicalChildren", "has_hierarchical_children"); types.put("hasHierarchicalChildren", ColumnType.BOOLEAN);
        cols.put("isPreferredRoot", "is_preferred_root");        types.put("isPreferredRoot", ColumnType.BOOLEAN);
        cols.put("ontologyPreferredPrefix", "ontology_preferred_prefix"); types.put("ontologyPreferredPrefix", ColumnType.TEXT);
        cols.put("ontologyIri", "ontology_iri");                 types.put("ontologyIri", ColumnType.TEXT);
        cols.put("hierarchicalAncestor", "hierarchical_ancestors"); types.put("hierarchicalAncestor", ColumnType.TEXT_ARRAY);
        cols.put("directAncestor", "direct_ancestors");          types.put("directAncestor", ColumnType.TEXT_ARRAY);
        cols.put("relatedTo", "related_to");                     types.put("relatedTo", ColumnType.TEXT_ARRAY);
        cols.put("curatedFromSources", "curated_from_sources");  types.put("curatedFromSources", ColumnType.TEXT_ARRAY);
        cols.put("label", "label");                              types.put("label", ColumnType.TEXT_ARRAY);
        cols.put("synonym", "synonym");                          types.put("synonym", ColumnType.TEXT_ARRAY);
        cols.put("definition", "definition");                    types.put("definition", ColumnType.TEXT_ARRAY);
        cols.put("subset", "subset");                            types.put("subset", ColumnType.TEXT_ARRAY);

        COLUMN_MAP = Collections.unmodifiableMap(cols);
        COLUMN_TYPES = Collections.unmodifiableMap(types);
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public String getSearchText() {
        return this.searchText;
    }

    public void setExactMatch(boolean exactMatch) {
        this.exactMatch = exactMatch;
    }

    public void addFilter(String propertyName, Collection<String> propertyValues, SearchType searchType) {
        this.filters.add(new SearchFilter(propertyName, propertyValues, false));
    }

    public void addExcludeFilter(String propertyName, Collection<String> propertyValues, SearchType searchType) {
        this.excludeFilters.add(new SearchFilter(propertyName, propertyValues, true));
    }

    public void addFacetField(String propertyName) {
        this.facetFields.add(propertyName);
    }

    /**
     * Resolve an OLS field name to a Postgres column name.
     * Handles known fields via COLUMN_MAP and dynamic filter properties
     * (property URIs with : replaced by __) via the filter_ column prefix.
     */
    static String resolveColumn(String field) {
        String col = COLUMN_MAP.get(field);
        if (col != null) return col;
        // Dynamic filter property: URI with __ for :  → filter_<field> column
        // Returns null if the column doesn't exist (checked by availableFilterColumns)
        return "\"filter_" + field.replace("__", ":") + "\"";
    }

    /**
     * Check if a field resolves to a known (non-dynamic) column.
     */
    static boolean isKnownColumn(String field) {
        return COLUMN_MAP.containsKey(field);
    }

    static ColumnType resolveColumnType(String field) {
        ColumnType ct = COLUMN_TYPES.get(field);
        if (ct != null) return ct;
        // Dynamic filter columns are TEXT[]
        return ColumnType.TEXT_ARRAY;
    }

    /**
     * Build the WHERE clause and parameters for this query.
     * Returns a WhereClause with SQL fragment and parameter list.
     * @param availableFilterColumns set of quoted column names for dynamic filter_ columns in the DB, or null to allow all
     */
    public WhereClause buildWhereClause(Set<String> availableFilterColumns) {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();

        // Full-text search
        if (searchText != null && !searchText.isBlank()) {
            if (exactMatch) {
                where.append(" AND ts_search @@ phraseto_tsquery('english', ?)");
            } else {
                where.append(" AND ts_search @@ websearch_to_tsquery('english', ?)");
            }
            params.add(searchText);
        }

        // Positive filters (skip dynamic filter columns that may not exist)
        for (SearchFilter f : filters) {
            if (!isKnownColumn(f.field) && availableFilterColumns != null
                    && !availableFilterColumns.contains(resolveColumn(f.field))) {
                continue;
            }
            appendFilterCondition(where, params, f, false);
        }

        // Exclude filters (skip dynamic filter columns that may not exist)
        for (SearchFilter f : excludeFilters) {
            if (!isKnownColumn(f.field) && availableFilterColumns != null
                    && !availableFilterColumns.contains(resolveColumn(f.field))) {
                continue;
            }
            appendFilterCondition(where, params, f, true);
        }

        return new WhereClause(where.toString(), params);
    }

    /**
     * Build the ORDER BY clause for relevance scoring.
     */
    public WhereClause buildWhereClause() {
        return buildWhereClause(null);
    }

    public String buildOrderBy() {
        if (searchText != null && !searchText.isBlank()) {
            // Relevance scoring: ts_rank + is_defining boost + ontology type boost + exact label boost
            return " ORDER BY "
                + "ts_rank_cd(ts_search, websearch_to_tsquery('english', ?), 32)"
                + " + CASE WHEN is_defining_ontology THEN 100.0 ELSE 0 END"
                + " + CASE WHEN search_type = 'ontology' THEN 1.0 ELSE 0 END"
                + " + CASE WHEN ? = ANY(label) THEN 1000.0 ELSE 0 END"
                + " DESC, id ASC";
        }
        return " ORDER BY id ASC";
    }

    /**
     * Return the parameters needed for the ORDER BY clause.
     */
    public List<Object> buildOrderByParams() {
        if (searchText != null && !searchText.isBlank()) {
            return List.of(searchText, searchText);
        }
        return List.of();
    }

    private void appendFilterCondition(StringBuilder where, List<Object> params, SearchFilter f, boolean negate) {
        String column = resolveColumn(f.field);
        ColumnType colType = resolveColumnType(f.field);
        String prefix = negate ? " AND NOT (" : " AND (";
        String suffix = ")";

        // Expand type=entity to class/property/individual.
        // The API accepts "entity" as a type filter, but search_type stores
        // only the specific type (class/property/individual).
        Collection<String> values = f.values;
        if ("type".equals(f.field)) {
            List<String> expanded = new ArrayList<>();
            for (String v : values) {
                if ("entity".equals(v)) {
                    expanded.add("class");
                    expanded.add("property");
                    expanded.add("individual");
                } else {
                    expanded.add(v);
                }
            }
            values = expanded;
        }

        if (colType == ColumnType.BOOLEAN) {
            // Boolean filter: only first value matters
            String val = values.iterator().next();
            boolean boolVal = "true".equalsIgnoreCase(val);
            where.append(prefix).append(column).append(" = ?").append(suffix);
            params.add(boolVal);
        } else if (colType == ColumnType.TEXT_ARRAY) {
            // Array containment: any value must be in the array
            where.append(prefix);
            int i = 0;
            for (String val : values) {
                if (i++ > 0) where.append(" OR ");
                where.append("? = ANY(").append(column).append(")");
                params.add(val);
            }
            where.append(suffix);
        } else {
            // Text column: exact match, OR for multiple values
            if (values.size() == 1) {
                where.append(prefix).append(column).append(" = ?").append(suffix);
                params.add(values.iterator().next());
            } else {
                where.append(prefix).append(column).append(" = ANY(?)").append(suffix);
                params.add(values.toArray(new String[0]));
            }
        }
    }

    static class SearchFilter {
        String field;
        Collection<String> values;
        boolean negate;

        SearchFilter(String field, Collection<String> values, boolean negate) {
            this.field = field;
            this.values = values;
            this.negate = negate;
        }
    }

    public static class WhereClause {
        public final String sql;
        public final List<Object> params;

        WhereClause(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    /**
     * Compatibility shim: search fields are absorbed by the tsvector ranking.
     * This method is a no-op but kept so SearchFieldsParser compiles without changes.
     */
    public void addSearchField(String propertyName, int weight, SearchType searchType) {
        // No-op: PostgreSQL tsvector handles search field weighting at index time
    }

    /**
     * Compatibility shim: boost fields are handled by the ORDER BY scoring.
     */
    public void addBoostField(String propertyName, String propertyValue, int weight, SearchType searchType) {
        // No-op: boosting is built into buildOrderBy()
    }
}
