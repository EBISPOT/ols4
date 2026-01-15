#!/usr/bin/env python3
"""
Generates Cypher statements to create all Neo4j indexes including:
- Standard indexes for OntologyClass, OntologyProperty, OntologyIndividual, OntologyEntity
- Vector indexes for all embedding models (dimensions read from parquet files)

Usage: python create_neo4j_embedding_indexes.py [embeddings_path]

If embeddings_path is provided, vector indexes will be created for each parquet file found.
"""

import sys
import os
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
    """Generate Cypher statements to create vector indexes for a given model."""
    
    # Sanitize model name for index name (replace hyphens with underscores)
    safe_model_name = model_name.replace('-', '_').replace('.', '_')
    property_name = f"embeddings_{model_name}"
    
    # Include OntologyEntity for cross-type vector searches
    entity_types = ['OntologyClass', 'OntologyProperty', 'OntologyIndividual', 'OntologyEntity']
    
    statements = []
    for entity_type in entity_types:
        index_name = f"{entity_type.lower()}_{safe_model_name}_embeddings"
        
        statement = f"""CREATE VECTOR INDEX {index_name} IF NOT EXISTS
FOR (n:{entity_type}) ON n.`{property_name}` OPTIONS {{ indexConfig: {{
 `vector.dimensions`: {dimensions},
 `vector.similarity_function`: 'cosine'
}}}};"""
        statements.append(statement)
    
    return '\n\n'.join(statements)


def generate_embedding_indexes(embeddings_path: Path) -> str:
    """Generate vector index statements for all parquet files in the embeddings path."""
    if not embeddings_path.exists() or not embeddings_path.is_dir():
        return ""
    
    parquet_files = sorted(embeddings_path.glob('*.parquet'))
    
    if not parquet_files:
        return ""
    
    lines = []
    lines.append(f"// Auto-generated vector indexes for {len(parquet_files)} embedding model(s)")
    lines.append(f"// Generated from: {embeddings_path}")
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
    embeddings_path = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    
    # Output standard indexes
    print("// Standard Neo4j indexes")
    print(STANDARD_INDEXES)
    
    # Output embedding indexes if path provided
    if embeddings_path:
        embedding_indexes = generate_embedding_indexes(embeddings_path)
        if embedding_indexes:
            print(embedding_indexes)
    
    # Wait for all indexes to be created
    print("CALL db.awaitIndexes(10800);")


if __name__ == '__main__':
    main()
