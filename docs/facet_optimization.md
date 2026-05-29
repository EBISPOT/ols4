# Facet Query Optimization

## Problem

With 10M+ entities, facet queries are expensive. The v1 `/api/search` endpoint runs 6 separate `GROUP BY` queries per request (one per facet field: ontologyId, ontologyIri, ontologyPreferredPrefix, type, isDefiningOntology, isObsolete). Each scans all rows matching the FTS/filter conditions.

Solr computes all facets in a single pass over posting lists using docValues. PostgreSQL has no equivalent — each facet is a full aggregation.

## Solution: Pre-aggregated Summary Table + Single-CTE for FTS

### 1. Summary table (for non-FTS queries)

At dataload time, create a small pre-aggregated table:

```sql
CREATE TABLE facet_summary AS
SELECT search_type, ontology_id, is_obsolete, is_defining_ontology,
       ontology_iri, ontology_preferred_prefix,
       COUNT(*) AS cnt
FROM ols_entities
GROUP BY search_type, ontology_id, is_obsolete, is_defining_ontology,
         ontology_iri, ontology_preferred_prefix;
```

~300 ontologies × 4 types × 2 × 2 = ~4,800 rows max. Any facet count for non-FTS queries becomes a SUM over this tiny table:

```sql
SELECT ontology_id, SUM(cnt) FROM facet_summary
WHERE search_type = 'class'
GROUP BY ontology_id;
```

### 2. Single-CTE for FTS queries

When search text is present, consolidate 6 separate scans into 1:

```sql
WITH matched AS (
    SELECT search_type, ontology_id, is_obsolete, is_defining_ontology,
           ontology_iri, ontology_preferred_prefix
    FROM ols_entities
    WHERE ts_search @@ websearch_to_tsquery('english', ?)
    AND <other filters, excluding the facet field per facet-exclusion semantics>
)
SELECT 'ontologyId', ontology_id, COUNT(*) FROM matched GROUP BY ontology_id
UNION ALL
SELECT 'type', search_type, COUNT(*) FROM matched GROUP BY search_type
UNION ALL
SELECT 'isObsolete', is_obsolete::text, COUNT(*) FROM matched GROUP BY is_obsolete
UNION ALL
SELECT 'isDefiningOntology', is_defining_ontology::text, COUNT(*) FROM matched GROUP BY is_defining_ontology
UNION ALL
SELECT 'ontologyPreferredPrefix', ontology_preferred_prefix, COUNT(*) FROM matched GROUP BY ontology_preferred_prefix
UNION ALL
SELECT 'ontologyIri', ontology_iri, COUNT(*) FROM matched GROUP BY ontology_iri;
```

One scan of matching rows instead of 6.

### 3. Restrict facetable columns

Only allow faceting on the 6 summary columns. Reject or fall back to per-field queries for anything else.

## Changes Required

1. **`create_postgres_schema.py`**: Add `CREATE TABLE facet_summary` populated post-load (after `ols_entities` is fully loaded and converted to LOGGED).
2. **`OlsSearchClient.java`**: Split facet logic:
   - No FTS + only summary-table columns → query `facet_summary`
   - FTS present → use single CTE approach
   - Unsupported facet field → fall back to current per-field query
3. **`V1SearchController.java`**: No changes needed (still requests the same 6 facets).
