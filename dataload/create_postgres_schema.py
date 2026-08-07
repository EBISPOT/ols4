#!/usr/bin/env python3
"""
Generates SQL statements to create all PostgreSQL tables, indexes, and
dynamic embedding vector columns for OLS4.

Output is organized into named sections delimited by '-- SECTION: <name>'
markers so the loading script can extract per-table SQL for transactional
COPY FREEZE loading.

Sections:
  extensions         - CREATE EXTENSION statements
  ols_entities       - CREATE TABLE + ALTER TABLE for entities
  ols_embedding_nodes - CREATE TABLE + ALTER TABLE for embedding nodes
  indexes            - All CREATE INDEX statements
  post_load          - UPDATE statements for computed columns + ANALYZE

Usage: python create_postgres_schema.py [--filter-property <name> ...] [parquet_file ...]
"""

import sys
from pathlib import Path


EXTENSIONS_SQL = """\
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Immutable wrappers for use in GENERATED ALWAYS AS columns.
-- to_tsvector() and array_to_string() are STABLE in the pg catalog.
-- plpgsql function bodies are opaque: PostgreSQL trusts the declared
-- IMMUTABLE volatility without inspecting the body. Both STABLE calls
-- are hidden inside these wrappers so the generated column expression
-- contains only IMMUTABLE function calls and column references.
--
-- text overload: for scalar columns (iri, short_form, curie).
CREATE OR REPLACE FUNCTION ols_tsvector(txt text)
    RETURNS tsvector LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS
    $$ BEGIN RETURN to_tsvector('pg_catalog.english', coalesce(txt, '')); END $$;

-- text[] overload: for array columns (label, synonym, definition).
-- array_to_string is STABLE, so it must live inside the plpgsql body.
CREATE OR REPLACE FUNCTION ols_tsvector(arr text[])
    RETURNS tsvector LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS
    $$ BEGIN RETURN to_tsvector('pg_catalog.english', coalesce(array_to_string(arr, ' '), '')); END $$;

-- Lowercases every element of a text[] so exact-match array containment (label @>, synonym @>)
-- can be done case-insensitively while still using a GIN expression index. See GitHub issue #1309.
CREATE OR REPLACE FUNCTION ols_lower_array(arr text[])
    RETURNS text[] LANGUAGE sql IMMUTABLE PARALLEL SAFE AS
    $$ SELECT array_agg(lower(x)) FROM unnest(arr) AS x $$;
"""

ENTITIES_TABLE_SQL = """\
CREATE TABLE ols_entities (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    iri TEXT NOT NULL,
    ontology_id TEXT,
    _json BYTEA NOT NULL,
    is_obsolete BOOLEAN DEFAULT FALSE,
    label TEXT[] DEFAULT '{}',
    direct_parents TEXT[] DEFAULT '{}',
    hierarchical_parents TEXT[] DEFAULT '{}',
    direct_ancestors TEXT[] DEFAULT '{}',
    hierarchical_ancestors TEXT[] DEFAULT '{}',

    -- Search/filter columns
    search_type TEXT,
    short_form TEXT,
    curie TEXT,
    obo_id TEXT,
    synonym TEXT[] DEFAULT '{}',
    definition TEXT[] DEFAULT '{}',
    is_defining_ontology BOOLEAN DEFAULT FALSE,
    has_direct_parents BOOLEAN DEFAULT FALSE,
    has_hierarchical_parents BOOLEAN DEFAULT FALSE,
    has_direct_children BOOLEAN DEFAULT FALSE,
    has_hierarchical_children BOOLEAN DEFAULT FALSE,
    is_preferred_root BOOLEAN DEFAULT FALSE,
    ontology_iri TEXT,
    ontology_preferred_prefix TEXT,
    subset TEXT[] DEFAULT '{}',
    related_to TEXT[] DEFAULT '{}',
    curated_from_sources TEXT[] DEFAULT '{}',

    -- Full-text search vector (computed automatically on insert)
    ts_search tsvector GENERATED ALWAYS AS (
        setweight(ols_tsvector(label), 'A') ||
        setweight(ols_tsvector(coalesce(short_form, '') || ' ' || coalesce(curie, '')), 'B') ||
        setweight(ols_tsvector(synonym), 'B') ||
        setweight(ols_tsvector(definition), 'C') ||
        setweight(ols_tsvector(coalesce(iri, '')), 'D')
    ) STORED,

    -- First label for autocomplete grouping / trigram matching
    label_for_suggest TEXT
) WITH (fillfactor=100);
ALTER TABLE ols_entities ALTER COLUMN _json SET STORAGE EXTERNAL;
"""

EMBEDDING_NODES_TABLE_SQL = """\
CREATE TABLE ols_embedding_nodes (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    entity_id TEXT NOT NULL
) WITH (fillfactor=100);
"""

ENTITY_INDEX_SQL = """\
CREATE INDEX idx_ent_onto_iri ON ols_entities (ontology_id, iri);
CREATE INDEX idx_ent_type ON ols_entities (type);
CREATE INDEX idx_ent_iri ON ols_entities (iri);
CREATE INDEX idx_ent_dp ON ols_entities USING gin (direct_parents);
CREATE INDEX idx_ent_hp ON ols_entities USING gin (hierarchical_parents);
CREATE INDEX idx_ent_da ON ols_entities USING gin (direct_ancestors);
CREATE INDEX idx_ent_ha ON ols_entities USING gin (hierarchical_ancestors);
CREATE INDEX idx_ent_search_type ON ols_entities (search_type);
CREATE INDEX idx_ent_short_form ON ols_entities (short_form);
CREATE INDEX idx_ent_curie ON ols_entities (curie);
CREATE INDEX idx_ent_is_def ON ols_entities (is_defining_ontology) WHERE is_defining_ontology = true;
CREATE INDEX idx_ent_pref_root ON ols_entities (is_preferred_root) WHERE is_preferred_root = true;
CREATE INDEX idx_ent_subset ON ols_entities USING gin (subset);
CREATE INDEX idx_ent_label ON ols_entities USING gin (label);
CREATE INDEX idx_ent_synonym ON ols_entities USING gin (synonym);
-- Case-insensitive counterparts, used by exact-match array containment (see arrayContainsCaseInsensitive
-- in JooqSupport.java / GitHub issue #1309). Without these the case-insensitive query falls back to an
-- unindexed unnest() scan of the whole table.
CREATE INDEX idx_ent_label_lower ON ols_entities USING gin (ols_lower_array(label));
CREATE INDEX idx_ent_synonym_lower ON ols_entities USING gin (ols_lower_array(synonym));
-- Per-field full-text indexes, used by buildFieldRestrictedTsCondition() (JooqSupport.tsvectorMatches)
-- to keep non-exact searchFields/queryFields-restricted queries index-accelerated instead of matching
-- against the blanket ts_search column, which spans every field regardless of what was requested.
-- Every field used by the default field-restricted query branch needs its own expression index;
-- PostgreSQL can combine them for OR conditions. See GitHub issue #1308.
CREATE INDEX idx_ent_label_fts ON ols_entities USING gin (ols_tsvector(label));
CREATE INDEX idx_ent_synonym_fts ON ols_entities USING gin (ols_tsvector(synonym));
CREATE INDEX idx_ent_curie_fts ON ols_entities USING gin (ols_tsvector(curie));
CREATE INDEX idx_ent_short_form_fts ON ols_entities USING gin (ols_tsvector(short_form));
CREATE INDEX idx_ent_iri_fts ON ols_entities USING gin (ols_tsvector(iri));
CREATE INDEX idx_ent_related_to ON ols_entities USING gin (related_to);
CREATE INDEX idx_ent_fts ON ols_entities USING gin (ts_search);
CREATE INDEX idx_ent_trgm_suggest ON ols_entities USING gin (label_for_suggest gin_trgm_ops);
-- Composite indexes ending in id: paginated listings ORDER BY id LIMIT n. Without an index
-- providing (filter equality + id order) the planner walks the whole primary key expecting an
-- early match, which scans the entire table when matches are rare (e.g. tree roots). These also
-- enable index-only-scan counts since COPY FREEZE leaves all pages visible.
CREATE INDEX idx_ent_st_obs_id ON ols_entities (search_type, is_obsolete, id);
CREATE INDEX idx_ent_onto_st_obs_id ON ols_entities (ontology_id, search_type, is_obsolete, id);
-- Expression indexes for case-insensitive dynamic filters (?iri=, ?shortForm=, ?curie=),
-- which compare lower(column) = lower(value) and otherwise force a full table scan.
CREATE INDEX idx_ent_lower_iri ON ols_entities (lower(iri));
CREATE INDEX idx_ent_lower_short_form ON ols_entities (lower(short_form));
CREATE INDEX idx_ent_lower_curie ON ols_entities (lower(curie));
"""

EMBEDDING_NODE_INDEX_SQL = """\
CREATE INDEX idx_emb_entity ON ols_embedding_nodes (entity_id);
CREATE INDEX idx_emb_type ON ols_embedding_nodes (type);
"""


AUTOSUGGEST_TABLE_SQL = """\
CREATE TABLE ols_autosuggest (
    ontology_id TEXT NOT NULL,
    string TEXT NOT NULL
) WITH (fillfactor=100);
"""

AUTOSUGGEST_INDEX_SQL = """\
CREATE INDEX idx_autosuggest_trgm ON ols_autosuggest USING gin (string gin_trgm_ops);
CREATE INDEX idx_autosuggest_onto ON ols_autosuggest (ontology_id);
"""

PCA_MODELS_TABLE_SQL = """\
CREATE TABLE ols_pca_models (
    name TEXT PRIMARY KEY,
    model BYTEA NOT NULL
);
"""

TEXT_TAGGER_TABLE_SQL = """\
CREATE TABLE ols_text_tagger (
    tagger_db_oid OID NOT NULL
);
"""

def generate_filter_property_sql(filter_properties: list) -> tuple:
    """Generate (ALTER TABLE SQL, CREATE INDEX SQL) for filter property columns.

    Returns a tuple of (alter_sql, index_sql) so they can go in separate sections.
    """
    if not filter_properties:
        return ("", "")

    alter_lines = []
    index_lines = []

    for prop_name in filter_properties:
        col_name = f"filter_{prop_name}"
        safe_idx = prop_name.replace('/', '_').replace('#', '_').replace('.', '_').replace('-', '_').replace(':', '_')
        alter_lines.append(f'ALTER TABLE ols_entities ADD COLUMN "{col_name}" TEXT[] DEFAULT \'{{}}\';')
        index_lines.append(f'CREATE INDEX idx_ent_fp_{safe_idx} ON ols_entities USING gin ("{col_name}");')

    return ('\n'.join(alter_lines) + '\n', '\n'.join(index_lines) + '\n')


def get_embedding_dimension(parquet_path: str) -> int:
    """Read the first row of a parquet file and return the embedding dimension."""
    try:
        import pyarrow.parquet as pq
    except ImportError:
        print("-- Warning: pyarrow not available, cannot read embedding dimensions", file=sys.stderr)
        return 0

    try:
        pf = pq.ParquetFile(parquet_path)
        first_batch = pf.read_row_group(0, columns=['embedding'])

        if first_batch.num_rows == 0:
            print(f"-- Warning: Empty parquet file: {parquet_path}", file=sys.stderr)
            return 0

        first_embedding = first_batch.column('embedding')[0].as_py()
        if first_embedding is None:
            print(f"-- Warning: Null embedding in: {parquet_path}", file=sys.stderr)
            return 0

        return len(first_embedding)
    except Exception as e:
        print(f"-- Warning: Could not read {parquet_path}: {e}", file=sys.stderr)
        return 0


def generate_embedding_sql(parquet_files: list) -> tuple:
    """Generate per-table ALTER TABLE and CREATE INDEX statements for embedding models.

    Returns a tuple of (entities_alter, emb_nodes_alter, entities_index, emb_nodes_index).
    """
    if not parquet_files:
        return ("", "", "", "")

    ent_alter = []
    emb_alter = []
    ent_index = []
    emb_index = []

    for parquet_file in parquet_files:
        model_name = Path(parquet_file).stem
        dimensions = get_embedding_dimension(str(parquet_file))

        if dimensions == 0:
            print(f"-- Skipping {model_name}: could not determine dimensions", file=sys.stderr)
            continue

        safe_model_name = model_name.replace('-', '_').replace('.', '_')
        avg_col = f"embeddings_{model_name}"
        emb_col = f"embedding_{model_name}"

        ent_alter.append(f'ALTER TABLE ols_entities ADD COLUMN "{avg_col}" vector({dimensions});')
        ent_alter.append(f'ALTER TABLE ols_entities ALTER COLUMN "{avg_col}" SET STORAGE EXTERNAL;')

        emb_alter.append(f'ALTER TABLE ols_embedding_nodes ADD COLUMN "{emb_col}" vector({dimensions});')
        emb_alter.append(f'ALTER TABLE ols_embedding_nodes ALTER COLUMN "{emb_col}" SET STORAGE EXTERNAL;')

        ent_index.append(f'CREATE INDEX idx_ent_emb_{safe_model_name} ON ols_entities USING hnsw ("{avg_col}" vector_cosine_ops);')

        emb_index.append(f"CREATE INDEX idx_emb_{safe_model_name}_label ON ols_embedding_nodes USING hnsw (\"{emb_col}\" vector_cosine_ops) WHERE type = 'LabelEmbedding';")
        emb_index.append(f"CREATE INDEX idx_emb_{safe_model_name}_curated ON ols_embedding_nodes USING hnsw (\"{emb_col}\" vector_cosine_ops) WHERE type = 'CurationEmbedding';")

    return (
        '\n'.join(ent_alter) + '\n' if ent_alter else "",
        '\n'.join(emb_alter) + '\n' if emb_alter else "",
        '\n'.join(ent_index) + '\n' if ent_index else "",
        '\n'.join(emb_index) + '\n' if emb_index else "",
    )


def main():
    # Parse --filter-property <name> flags and remaining parquet file args
    filter_properties = []
    parquet_files = []
    args = sys.argv[1:]
    i = 0
    while i < len(args):
        if args[i] == '--filter-property' and i + 1 < len(args):
            filter_properties.append(args[i + 1])
            i += 2
        elif args[i].endswith('.parquet'):
            parquet_files.append(args[i])
            i += 1
        else:
            i += 1

    # Generate dynamic SQL pieces
    emb_ent_alter, emb_node_alter, emb_ent_index, emb_node_index = \
        generate_embedding_sql(parquet_files)
    filter_alter, filter_index = generate_filter_property_sql(filter_properties)

    # -- Extensions --
    print("-- SECTION: extensions")
    print(EXTENSIONS_SQL)

    # -- Entities table (CREATE + ALTERs for embeddings and filter properties) --
    print("-- SECTION: ols_entities")
    print(ENTITIES_TABLE_SQL)
    if emb_ent_alter:
        print(emb_ent_alter)
    if filter_alter:
        print(filter_alter)

    # -- Autosuggest table --
    print("-- SECTION: ols_autosuggest")
    print(AUTOSUGGEST_TABLE_SQL)

    # -- Embedding nodes table (CREATE + ALTERs for embedding columns) --
    print("-- SECTION: ols_embedding_nodes")
    print(EMBEDDING_NODES_TABLE_SQL)
    if emb_node_alter:
        print(emb_node_alter)

    # -- Indexes (all tables) --
    print("-- SECTION: indexes")
    print(ENTITY_INDEX_SQL)
    if emb_ent_index:
        print(emb_ent_index)
    if filter_index:
        print(filter_index)
    print(EMBEDDING_NODE_INDEX_SQL)
    if emb_node_index:
        print(emb_node_index)
    print(AUTOSUGGEST_INDEX_SQL)

    # -- PCA models table --
    print("-- SECTION: ols_pca_models")
    print(PCA_MODELS_TABLE_SQL)

    # -- Text tagger table --
    print("-- SECTION: ols_text_tagger")
    print(TEXT_TAGGER_TABLE_SQL)

    # -- Post-load computed columns + ANALYZE --
    print("-- SECTION: post_load")
    print("ANALYZE;")


if __name__ == '__main__':
    main()
