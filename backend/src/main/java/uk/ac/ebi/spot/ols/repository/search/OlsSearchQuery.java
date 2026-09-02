package uk.ac.ebi.spot.ols.repository.search;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.data.domain.Sort;

import java.net.URI;
import java.util.*;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.arrayContains;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.arrayContainsCaseInsensitive;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.field;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.matchesTsQuery;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.phraseToTsQuery;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.toTsQuery;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.tsvectorMatches;

/**
 * Builds jOOQ conditions and ordering for entity search/filter operations.
 * Translates OLS field names to PostgreSQL columns.
 */
public class OlsSearchQuery {

    private static final Pattern SAFE_FIELD_NAME = Pattern.compile("^[a-zA-Z0-9_:/.#-]+$");

    String searchText = null;
    boolean exactMatch = false;
    List<SearchFilter> filters = new ArrayList<>();
    List<List<SearchFilter>> anyFilterGroups = new ArrayList<>();
    List<SearchFilter> excludeFilters = new ArrayList<>();
    List<String> facetFields = new ArrayList<>();
    List<String> searchFields = null;
    List<BoostField> boostFields = new ArrayList<>();

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
        cols.put("hasChildren", "has_direct_children");          types.put("hasChildren", ColumnType.BOOLEAN);
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
        this.filters.add(new SearchFilter(propertyName, propertyValues, false, searchType));
    }

    /**
     * Add a group of alternative filters. The group is ANDed with the rest of the query, while
     * its members are ORed together.
     */
    public void addAnyFilter(
            Map<String, ? extends Collection<String>> alternatives, SearchType searchType) {
        List<SearchFilter> group = alternatives.entrySet().stream()
                .map(entry -> new SearchFilter(
                        entry.getKey(), entry.getValue(), false, searchType))
                .toList();
        this.anyFilterGroups.add(group);
    }

    public void addExcludeFilter(String propertyName, Collection<String> propertyValues, SearchType searchType) {
        this.excludeFilters.add(new SearchFilter(propertyName, propertyValues, true, searchType));
    }

    public void addFacetField(String propertyName) {
        this.facetFields.add(propertyName);
    }

    public void setSearchFields(Collection<String> fields) {
        this.searchFields = (fields != null && !fields.isEmpty()) ? new ArrayList<>(fields) : null;
    }

    /**
     * Resolve an OLS field name to a PostgreSQL column name.
     * Handles known fields via COLUMN_MAP and dynamic filter properties
     * (property URIs with : replaced by __) via the filter_ column prefix.
     */
    static String resolveColumn(String field) {
        String col = COLUMN_MAP.get(field);
        if (col != null) return col;
        String resolved = field.replace("__", ":");
        if (!SAFE_FIELD_NAME.matcher(resolved).matches()) {
            throw new IllegalArgumentException("Invalid filter field name: " + field);
        }
        return "filter_" + resolved;
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
        return ColumnType.TEXT_ARRAY;
    }

    public Condition buildCondition(Set<String> availableFilterColumns) {
        return buildCondition(availableFilterColumns, null, null);
    }

    public Condition buildCondition(Set<String> availableFilterColumns, String qualifier) {
        return buildCondition(availableFilterColumns, qualifier, null);
    }

    public Condition buildCondition(Set<String> availableFilterColumns, String qualifier, String excludedFacetField) {
        Condition condition = DSL.trueCondition();

        if (searchText != null && !searchText.isBlank()) {
            if (!usesDefaultSearchFields()) {
                condition = condition.and(exactMatch
                        ? buildExactFieldCondition(qualifier, availableFilterColumns)
                        : buildFieldRestrictedTsCondition(qualifier, availableFilterColumns));
            } else {
                condition = condition.and(isIriQuery(searchText)
                        ? buildExactIriCondition(qualifier)
                        : matchesTsQuery(field(qualifier, "ts_search", Object.class), buildTsQuery()));
            }
        }

        for (SearchFilter f : filters) {
            if (excludedFacetField != null && excludedFacetField.equals(f.field)) {
                continue;
            }
            if (!isFilterAvailable(availableFilterColumns, f.field)) {
                condition = condition.and(DSL.falseCondition());
                continue;
            }
            condition = condition.and(buildFilterCondition(qualifier, f, false));
        }

        for (List<SearchFilter> group : anyFilterGroups) {
            Condition alternatives = DSL.falseCondition();
            for (SearchFilter f : group) {
                if (isFilterAvailable(availableFilterColumns, f.field)) {
                    alternatives = alternatives.or(buildFilterCondition(qualifier, f, false));
                }
            }
            condition = condition.and(alternatives);
        }

        for (SearchFilter f : excludeFilters) {
            if (!isFilterAvailable(availableFilterColumns, f.field)) {
                continue;
            }
            condition = condition.and(buildFilterCondition(qualifier, f, true));
        }

        return condition;
    }

    public List<SortField<?>> buildOrderBy() {
        return buildOrderBy((String) null);
    }

    public List<SortField<?>> buildOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return buildOrderBy();
        }

        List<SortField<?>> orderBy = new ArrayList<>();
        for (Sort.Order order : sort) {
            String column = COLUMN_MAP.get(order.getProperty());
            ColumnType columnType = COLUMN_TYPES.get(order.getProperty());
            if (column == null || columnType == ColumnType.TEXT_ARRAY) {
                throw new IllegalArgumentException("Unsupported sort field: " + order.getProperty());
            }

            Field<?> sortField = columnType == ColumnType.BOOLEAN
                    ? field(column, Boolean.class)
                    : field(column, String.class);
            orderBy.add(order.isAscending() ? sortField.asc() : sortField.desc());
        }
        orderBy.add(field("id", String.class).asc());
        return orderBy;
    }

    public List<SortField<?>> buildOrderBy(String qualifier) {
        Field<Double> rank = buildRankExpression(qualifier);
        if (rank != null) {
            return List.of(rank.desc(), field(qualifier, "id", String.class).asc());
        }
        return List.of(field(qualifier, "id", String.class).asc());
    }

    /**
     * The relevance score results are ordered by, or null when there is no search text to rank
     * (filter-only queries are ordered by id alone).
     *
     * <p>Exposed separately from {@link #buildOrderBy} so callers can select the score as a column
     * — needed when ordering has to happen in a subquery rather than on the base table.
     */
    public Field<Double> buildRankExpression(String qualifier) {
        if (searchText == null || searchText.isBlank()) {
            return null;
        }
        Field<Double> rank = usesDefaultSearchFields() && isIriQuery(searchText)
                ? DSL.inline(0.0)
                : DSL.function(
                        "ts_rank_cd",
                        SQLDataType.DOUBLE,
                        field(qualifier, "ts_search", Object.class),
                        buildTsQuery(),
                        DSL.inline(32));
        rank = rank
                .plus(DSL.when(field(qualifier, "is_defining_ontology", Boolean.class).isTrue(), 100.0).otherwise(0.0))
                .plus(DSL.when(field(qualifier, "search_type", String.class).eq("ontology"), 1.0).otherwise(0.0))
                // Case-insensitive so an exact-label hit still gets the ranking bonus regardless of
                // input casing (e.g. "mus musculus" vs stored "Mus musculus") — matches the
                // case-insensitive semantics buildExactFieldCondition() already uses. Without this,
                // exact matches whose case differs from the query rank purely on ts_rank_cd and can
                // end up buried many pages deep. See GitHub issue #1312.
                .plus(DSL.when(arrayContainsCaseInsensitive(field(qualifier, "label", String[].class), searchText.toLowerCase(Locale.ROOT)), 1000.0).otherwise(0.0));

        if (usesDefaultSearchFields() && isIriQuery(searchText)) {
            // Keep the defining entity first when an IRI is shared by several ontologies.
            rank = rank.plus(DSL.when(buildExactIriCondition(qualifier), 1_000_000.0).otherwise(0.0));
        }

        for (BoostField boost : boostFields) {
            Condition matches = buildBoostCondition(qualifier, boost);
            rank = rank.plus(DSL.when(matches, (double) boost.weight).otherwise(0.0));
        }
        return rank;
    }

    private Condition buildExactIriCondition(String qualifier) {
        return DSL.lower(field(qualifier, "iri", String.class))
                .eq(searchText.trim().toLowerCase(Locale.ROOT));
    }

    private boolean usesDefaultSearchFields() {
        return searchFields == null || searchFields.isEmpty();
    }

    /**
     * Detect URL-shaped IRIs without treating CURIEs such as {@code NCIT:C2985} as IRIs.
     */
    private static boolean isIriQuery(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            return uri.isAbsolute()
                    && (value.contains("://") || "urn".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Condition buildBoostCondition(String qualifier, BoostField boost) {
        String column = COLUMN_MAP.get(boost.propertyName);
        ColumnType columnType = COLUMN_TYPES.get(boost.propertyName);
        if (column == null || columnType == null) {
            throw new IllegalArgumentException("Unsupported boost field: " + boost.propertyName);
        }

        String value = boost.propertyValue == null ? searchText : boost.propertyValue;
        if (columnType == ColumnType.BOOLEAN) {
            return field(qualifier, column, Boolean.class).eq(Boolean.parseBoolean(value));
        }
        return tsvectorMatches(
                field(qualifier, column, Object.class),
                toTsQuery(buildPrefixTsQueryString(value)));
    }

    /**
     * For exact-match queries with explicit searchFields, restrict the match to those specific
     * indexed columns.
     *
     * Checks known columns (COLUMN_MAP) first, then dynamically-indexed filter_ columns so that
     * ontology property URIs work when the property has been declared as filterProperty in the
     * ontology config (e.g. owl:versionInfo, TAXRANK_1000000).
     *
     * If none of the requested searchFields resolve to an indexed column, returns falseCondition
     * (zero results) rather than falling back to ts_search. Falling back to ts_search would give
     * results from completely unrelated fields, which is misleading — the caller explicitly asked
     * for a specific field and should get zero results if that field is not indexed.
     *
     * Array columns use case-insensitive GIN @> containment against ols_lower_array(column)
     * (idx_ent_label_lower / idx_ent_synonym_lower) for fast bitmap index scans.
     * Scalar columns use lower()=lower() for case-insensitive equality.
     */
    private Condition buildExactFieldCondition(String qualifier, Set<String> availableFilterColumns) {
        String lowerSearch = searchText.toLowerCase(Locale.ROOT);
        Condition anyField = DSL.falseCondition();
        for (String qf : searchFields) {
            boolean knownCol = COLUMN_MAP.containsKey(qf);
            String col;
            try {
                col = resolveColumn(qf);
            } catch (IllegalArgumentException e) {
                continue;
            }
            boolean filterCol = !knownCol && availableFilterColumns != null && availableFilterColumns.contains(col);
            if (!knownCol && !filterCol) continue;
            ColumnType ct = knownCol ? resolveColumnType(qf) : ColumnType.TEXT_ARRAY;
            if (ct == ColumnType.TEXT_ARRAY) {
                anyField = anyField.or(arrayContainsCaseInsensitive(field(qualifier, col, String[].class), lowerSearch));
            } else {
                anyField = anyField.or(DSL.lower(field(qualifier, col, String.class)).eq(lowerSearch));
            }
        }
        return anyField;
    }

    /**
     * For non-exact queries with explicit searchFields, restrict full-text matching to those
     * specific fields instead of the blanket ts_search column.
     *
     * ts_search is a single generated tsvector spanning every searchable column (label, short_form,
     * curie, synonym, definition, iri), so matching against it ignores which fields the caller
     * asked for — a query restricted to queryFields=label,synonym would still match on hits inside
     * definition. This builds an ad-hoc ols_tsvector(column) @@ tsquery per requested field instead,
     * OR'd together, mirroring buildExactFieldCondition()'s column resolution. See GitHub issue #1308.
     *
     * Backed by per-field GIN expression indexes for label, synonym, curie, short_form, and iri so
     * the default field-restricted query stays index-accelerated. Dynamic fields without such an
     * index still match correctly, just via a slower scan.
     */
    private Condition buildFieldRestrictedTsCondition(String qualifier, Set<String> availableFilterColumns) {
        Field<Object> tsQuery = buildTsQuery();
        Condition anyField = DSL.falseCondition();
        for (String qf : searchFields) {
            boolean knownCol = COLUMN_MAP.containsKey(qf);
            String col;
            try {
                col = resolveColumn(qf);
            } catch (IllegalArgumentException e) {
                continue;
            }
            boolean filterCol = !knownCol && availableFilterColumns != null && availableFilterColumns.contains(col);
            if (!knownCol && !filterCol) continue;
            if (knownCol && resolveColumnType(qf) == ColumnType.BOOLEAN) continue;
            anyField = anyField.or(tsvectorMatches(field(qualifier, col, Object.class), tsQuery));
        }
        return anyField;
    }

    /**
     * Build the tsquery used for both matching and ranking.
     *
     * <p>For exact matches we keep phrase semantics. For normal (non-exact) searches we build a
     * prefix tsquery (each token suffixed with {@code :*}) so that partial words match — e.g.
     * "micro" matches "microscopy". This restores the partial-match behaviour that existed before
     * the move to PostgreSQL full text search (see GitHub issue #1267). Prefix matching works
     * against the stemmed lexemes stored in the {@code ts_search} tsvector, so "micro" matches the
     * stored lexeme "microscopi".
     */
    private Field<Object> buildTsQuery() {
        if (exactMatch) {
            return phraseToTsQuery(searchText);
        }
        return toTsQuery(buildPrefixTsQueryString(searchText));
    }

    /**
     * Turn free-text search input into a prefix tsquery string of the form
     * {@code "tok1:* & tok2:*"}. Tokens are split on any non-alphanumeric character, which strips
     * tsquery operator characters ({@code & | ! ( ) : * ' \}) that would otherwise break parsing.
     * Stop-word removal and stemming are left to {@code to_tsquery('english', ...)}. Returns an
     * empty string when there are no usable tokens; {@code to_tsquery('english', '')} yields an
     * empty tsquery that matches nothing, which is the desired behaviour.
     */
    static String buildPrefixTsQueryString(String searchText) {
        StringBuilder sb = new StringBuilder();
        // Split on anything that is not a Unicode letter or digit; to_tsquery normalises case.
        for (String token : searchText.trim().split("[^\\p{L}\\p{N}]+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" & ");
            }
            sb.append(token).append(":*");
        }
        return sb.toString();
    }

    private boolean isFilterAvailable(Set<String> availableFilterColumns, String fieldName) {
        return isKnownColumn(fieldName)
                || availableFilterColumns == null
                || availableFilterColumns.contains(resolveColumn(fieldName));
    }

    private Condition buildFilterCondition(String qualifier, SearchFilter f, boolean negate) {
        String column = resolveColumn(f.field);
        ColumnType colType = resolveColumnType(f.field);
        Collection<String> values = expandFilterValues(f);

        Condition condition;
        if (colType == ColumnType.BOOLEAN) {
            boolean boolVal = "true".equalsIgnoreCase(values.iterator().next());
            condition = field(qualifier, column, Boolean.class).eq(boolVal);
        } else if (colType == ColumnType.TEXT_ARRAY) {
            Condition anyValue = DSL.falseCondition();
            Field<String[]> arrayField = field(qualifier, column, String[].class);
            for (String val : values) {
                anyValue = anyValue.or(arrayContains(arrayField, val));
            }
            condition = anyValue;
        } else if ("ontology_id".equals(column)) {
            // ontology_id is always stored lowercase by rdf2json. Lowercase only the *input* and
            // compare against the bare column. This keeps the filter case-insensitive for every
            // call site regardless of SearchType — v1/select pass WHOLE_FIELD (GitHub issue #1265)
            // — while staying sargable against idx_ent_onto_iri. Wrapping the column in LOWER()
            // defeats that index and forces a full table scan (GitHub issue #1276).
            List<String> lowered = values.stream()
                .map(v -> v.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
            Field<String> col = field(qualifier, column, String.class);
            condition = lowered.size() == 1 ? col.eq(lowered.get(0)) : col.in(lowered);
        } else if (f.searchType == SearchType.CASE_INSENSITIVE_TOKENS) {
            // Generic case-insensitive match for columns whose stored values are not normalised.
            List<String> lowered = values.stream()
                .map(v -> v.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
            if (lowered.size() == 1) {
                condition = DSL.lower(field(qualifier, column, String.class)).eq(lowered.get(0));
            } else {
                condition = DSL.lower(field(qualifier, column, String.class)).in(lowered);
            }
        } else if (values.size() == 1) {
            condition = field(qualifier, column, String.class).eq(values.iterator().next());
        } else {
            condition = field(qualifier, column, String.class).in(values);
        }

        return negate ? condition.not() : condition;
    }

    private Collection<String> expandFilterValues(SearchFilter filter) {
        if (!"type".equals(filter.field)) {
            return filter.values;
        }

        List<String> expanded = new ArrayList<>();
        for (String value : filter.values) {
            if ("entity".equals(value)) {
                expanded.add("class");
                expanded.add("property");
                expanded.add("individual");
            } else {
                expanded.add(value);
            }
        }
        return expanded;
    }

    static class SearchFilter {
        String field;
        Collection<String> values;
        boolean negate;
        SearchType searchType;

        SearchFilter(String field, Collection<String> values, boolean negate, SearchType searchType) {
            this.field = field;
            this.values = values;
            this.negate = negate;
            this.searchType = searchType;
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
        if (weight <= 0) {
            throw new IllegalArgumentException("Boost weight must be positive: " + weight);
        }
        boostFields.add(new BoostField(propertyName, propertyValue, weight, searchType));
    }

    static class BoostField {
        String propertyName;
        String propertyValue;
        int weight;
        SearchType searchType;

        BoostField(String propertyName, String propertyValue, int weight, SearchType searchType) {
            this.propertyName = propertyName;
            this.propertyValue = propertyValue;
            this.weight = weight;
            this.searchType = searchType;
        }
    }
}
