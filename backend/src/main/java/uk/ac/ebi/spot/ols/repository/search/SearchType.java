package uk.ac.ebi.spot.ols.repository.search;

/**
 * Search type enum, kept for API compatibility with existing code.
 * In the PostgreSQL implementation, these are largely ignored since
 * tsvector ranking handles search field weighting at index time.
 */
public enum SearchType {
    CASE_INSENSITIVE_TOKENS,
    CASE_SENSITIVE_TOKENS,
    WHOLE_FIELD,
    EDGES,
    WHITESPACE,
    WHITESPACE_EDGES
}
