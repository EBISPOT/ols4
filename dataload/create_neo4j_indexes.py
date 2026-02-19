#!/usr/bin/env python3
"""
Generates Cypher statements to create all Neo4j indexes including:
- Standard indexes for OntologyClass, OntologyProperty, OntologyIndividual, OntologyEntity
- Vector indexes for all embedding models (dimensions read from parquet files)

Usage: python create_neo4j_indexes.py [parquet_file ...]

Any parquet files passed as arguments will have vector indexes created for them.
"""

import sys
from pathlib import Path


# Standard indexes that are always created
STANDARD_INDEXES = """
CREATE INDEX FOR (n:OntologyClass) ON n.id;
CREATE INDEX FOR (n:OntologyIndividual) ON n.id;
CREATE INDEX FOR (n:OntologyProperty) ON n.id;
CREATE INDEX FOR (n:OntologyEntity) ON n.id;

CREATE INDEX FOR (n:OntologyClass) ON n.iri;
"""


def get_embedding_dimension(parquet_path: str) -> int:
    """Read the first row of a parquet file and return the embedding dimension."""
    try:
        import pyarrow.parquet as pq
    except ImportError:
        print("Warning: pyarrow not available, cannot read embedding dimensions", file=sys.stderr)
        return 0

    try:
        # Read just the first row of the embedding column
        pf = pq.ParquetFile(parquet_path)
        first_batch = pf.read_row_group(0, columns=['embedding'])

        if first_batch.num_rows == 0:
            print(f"Warning: Empty parquet file: {parquet_path}", file=sys.stderr)
            return 0

        first_embedding = first_batch.column('embedding')[0].as_py()
        if first_embedding is None:
            print(f"Warning: Null embedding in: {parquet_path}", file=sys.stderr)
            return 0

        return len(first_embedding)
    except Exception as e:
        print(f"Warning: Could not read {parquet_path}: {e}", file=sys.stderr)
        return 0


def generate_vector_index_cypher(model_name: str, dimensions: int) -> str:
    """Generate Cypher statements to create vector indexes for a given model.

    Creates two indexes per model:
    1. OntologyEntity index on the average embedding (for term-term similarity)
    2. Embedding child node index on individual embeddings (for free-text vector search)
    """

    # Sanitize model name for index name (replace hyphens with underscores)
    safe_model_name = model_name.replace('-', '_').replace('.', '_')

    statements = []

    # 1. OntologyEntity index for average embeddings (term-term similarity)
    avg_property_name = f"embeddings_{model_name}"
    avg_index_name = f"ontologyentity_{safe_model_name}_embeddings"
    statements.append(f"""CREATE VECTOR INDEX {avg_index_name} IF NOT EXISTS
FOR (n:OntologyEntity) ON n.`{avg_property_name}` OPTIONS {{ indexConfig: {{
 `vector.dimensions`: {dimensions},
 `vector.similarity_function`: 'cosine'
}}}};""")

    # 2. Embedding child node index for individual embeddings (free-text search)
    emb_property_name = f"embedding_{model_name}"
    emb_index_name = f"embedding_{safe_model_name}"
    statements.append(f"""CREATE VECTOR INDEX {emb_index_name} IF NOT EXISTS
FOR (n:Embedding) ON n.`{emb_property_name}` OPTIONS {{ indexConfig: {{
 `vector.dimensions`: {dimensions},
 `vector.similarity_function`: 'cosine'
}}}};""")

    return '\n\n'.join(statements)


def generate_embedding_indexes(parquet_files: list[Path]) -> str:
    """Generate vector index statements for the given parquet files."""
    if not parquet_files:
        return ""

    lines = []
    lines.append(f"// Auto-generated vector indexes for {len(parquet_files)} embedding model(s)")
    lines.append("")

    for parquet_file in parquet_files:
        model_name = parquet_file.stem  # filename without extension

        dimensions = get_embedding_dimension(str(parquet_file))

        if dimensions == 0:
            print(f"// Skipping {model_name}: could not determine dimensions", file=sys.stderr)
            continue

        lines.append(f"// Model: {model_name} (dimensions: {dimensions})")
        lines.append(generate_vector_index_cypher(model_name, dimensions))
        lines.append("")

    return '\n'.join(lines)


def main():
    parquet_files = [Path(arg) for arg in sys.argv[1:] if arg.endswith('.parquet')]

    # Output standard indexes
    print("// Standard Neo4j indexes")
    print(STANDARD_INDEXES)

    # Output embedding indexes if any parquet files provided
    if parquet_files:
        embedding_indexes = generate_embedding_indexes(parquet_files)
        if embedding_indexes:
            print(embedding_indexes)

    # Wait for all indexes to be created
    print("CALL db.awaitIndexes(10800);")


if __name__ == '__main__':
    main()
