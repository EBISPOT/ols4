package uk.ac.ebi.spot.ols.repository.solr;

public enum SearchType {
    CASE_INSENSITIVE_TOKENS,
    CASE_SENSITIVE_TOKENS,
    WHOLE_FIELD,
    EDGES,
    WHITESPACE,
    WHITESPACE_EDGES
}
