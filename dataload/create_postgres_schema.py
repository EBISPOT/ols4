#!/usr/bin/env python3
"""
Generates SQL statements to create all PostgreSQL tables, indexes, and
dynamic embedding vector columns for OLS4.

Usage: python create_postgres_schema.py [parquet_file ...]

Any parquet files passed as arguments will have vector columns and HNSW indexes created for them.
"""

import sys
from pathlib import Path


SCHEMA_SQL = """
-- Create pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Entities table: classes, properties, individuals, ontologies
CREATE UNLOGGED TABLE ols_entities (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    iri TEXT NOT NULL,
    ontology_id TEXT,
    _json TEXT NOT NULL,
    is_obsolete BOOLEAN DEFAULT FALSE,
    label TEXT[] DEFAULT '{}',
    direct_ancestors TEXT[] DEFAULT '{}',
    hierarchical_ancestors TEXT[] DEFAULT '{}'
) WITH (fillfactor=100);

-- Edges table: relationships between entities
CREATE UNLOGGED TABLE ols_edges (
    start_id TEXT NOT NULL,
    end_id TEXT NOT NULL,
    type TEXT NOT NULL,
    _json TEXT,
    property TEXT[]
) WITH (fillfactor=100);

-- Embedding nodes table: individual embedding vectors linked to entities
CREATE UNLOGGED TABLE ols_embedding_nodes (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    entity_id TEXT NOT NULL
) WITH (fillfactor=100);
"""


INDEX_SQL = """
-- Entity lookup indexes
CREATE INDEX idx_ent_onto_iri ON ols_entities (ontology_id, iri);
CREATE INDEX idx_ent_type ON ols_entities (type);
CREATE INDEX idx_ent_iri ON ols_entities USING hash (iri);
CREATE INDEX idx_ent_onto ON ols_entities (ontology_id);

-- GIN indexes for ancestor array containment (for descendant/children queries)
CREATE INDEX idx_ent_da ON ols_entities USING gin (direct_ancestors);
CREATE INDEX idx_ent_ha ON ols_entities USING gin (hierarchical_ancestors);

-- Edge indexes
CREATE INDEX idx_edge_start ON ols_edges USING hash (start_id);
CREATE INDEX idx_edge_end ON ols_edges USING hash (end_id);
CREATE INDEX idx_edge_type ON ols_edges USING hash (type);
CREATE INDEX idx_edge_prop ON ols_edges USING gin (property);

-- Embedding node indexes
CREATE INDEX idx_emb_entity ON ols_embedding_nodes USING hash (entity_id);
CREATE INDEX idx_emb_type ON ols_embedding_nodes (type);
"""


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


def generate_embedding_sql(parquet_files: list) -> str:
    """Generate ALTER TABLE + CREATE INDEX statements for embedding models."""
    if not parquet_files:
        return ""

    lines = []
    lines.append("-- Dynamic embedding vector columns and HNSW indexes")
    lines.append("")

    for parquet_file in parquet_files:
        model_name = Path(parquet_file).stem
        dimensions = get_embedding_dimension(str(parquet_file))

        if dimensions == 0:
            print(f"-- Skipping {model_name}: could not determine dimensions", file=sys.stderr)
            continue

        safe_model_name = model_name.replace('-', '_').replace('.', '_')
        avg_col = f"embeddings_{model_name}"
        emb_col = f"embedding_{model_name}"

        lines.append(f"-- Model: {model_name} (dimensions: {dimensions})")

        # Add vector columns
        lines.append(f'ALTER TABLE ols_entities ADD COLUMN "{avg_col}" vector({dimensions});')
        lines.append(f'ALTER TABLE ols_embedding_nodes ADD COLUMN "{emb_col}" vector({dimensions});')
        lines.append("")

        # HNSW vector indexes
        lines.append(f'CREATE INDEX idx_ent_emb_{safe_model_name} ON ols_entities USING hnsw ("{avg_col}" vector_cosine_ops);')
        lines.append(f"CREATE INDEX idx_emb_{safe_model_name}_label ON ols_embedding_nodes USING hnsw (\"{emb_col}\" vector_cosine_ops) WHERE type = 'LabelEmbedding';")
        lines.append(f"CREATE INDEX idx_emb_{safe_model_name}_curated ON ols_embedding_nodes USING hnsw (\"{emb_col}\" vector_cosine_ops) WHERE type = 'CurationEmbedding';")
        lines.append("")

    return '\n'.join(lines)


def main():
    parquet_files = [arg for arg in sys.argv[1:] if arg.endswith('.parquet')]

    # Output schema
    print("-- OLS4 PostgreSQL Schema")
    print(SCHEMA_SQL)

    # Output embedding columns if any parquet files provided
    if parquet_files:
        embedding_sql = generate_embedding_sql(parquet_files)
        if embedding_sql:
            print(embedding_sql)

    # Output standard indexes
    print(INDEX_SQL)

    # Convert UNLOGGED to LOGGED for production
    print("-- Convert to production mode")
    print("ALTER TABLE ols_entities SET LOGGED;")
    print("ALTER TABLE ols_edges SET LOGGED;")
    print("ALTER TABLE ols_embedding_nodes SET LOGGED;")
    print("")

    # Analyze
    print("ANALYZE;")


if __name__ == '__main__':
    main()
