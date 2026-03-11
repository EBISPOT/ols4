#!/usr/bin/env python3
"""
Compute per-term average embeddings.

Each term (identified by pk = ontology_id:entity_type:iri) may have multiple
rows in its parquet – one row per label / synonym.  This script groups by pk,
averages the embedding vectors, re-normalises them to unit length, and writes
one row per term.

The kept metadata columns (ontology_id, entity_type, iri, label) are taken
from the first row in each group (they are identical across rows that share
the same pk).

Usage:
    python avg_embeddings.py input.parquet output_avg.parquet
"""

import argparse
import gc
import sys

import numpy as np
import polars as pl
import pyarrow as pa


def load_embedding_matrix(parquet_path: str):
    """
    Load the 'embedding' column via Polars/Arrow zero-copy.
    Returns (X, N, D) where X is (N, D) float64.
    """
    df = pl.scan_parquet(parquet_path).select("embedding").collect()
    emb_series = df["embedding"]
    N = len(emb_series)
    if N == 0:
        return np.empty((0, 0), dtype=np.float64), 0, 0

    n_nulls = emb_series.null_count()
    if n_nulls > 0:
        raise ValueError(f"Found {n_nulls} null embedding(s) in input; cannot average.")

    first = emb_series[0]
    D = len(first)

    emb_arrow = emb_series.to_arrow()
    values_np = emb_arrow.values.to_numpy()
    X = values_np.reshape(N, D).astype(np.float64, copy=False)

    del df, emb_series, emb_arrow, values_np
    gc.collect()
    return X, N, D


def main():
    parser = argparse.ArgumentParser(
        description="Average embeddings per term (grouped by pk)"
    )
    parser.add_argument("input_parquet", help="Input parquet with per-text embeddings")
    parser.add_argument("output_parquet", help="Output parquet with per-term averaged embeddings")
    args = parser.parse_args()

    # --- check schema ---
    schema = pl.read_parquet_schema(args.input_parquet)
    if "embedding" not in schema or "pk" not in schema:
        print("ERROR: input parquet must contain 'pk' and 'embedding' columns", file=sys.stderr)
        sys.exit(1)

    # --- filter out CURATION rows (only average LABEL embeddings) ---
    has_string_type = "string_type" in schema
    if has_string_type:
        label_df = (
            pl.scan_parquet(args.input_parquet)
            .filter(pl.col("string_type") != "CURATION")
            .collect()
        )
        filtered_parquet = args.input_parquet + ".labels_only.parquet"
        label_df.write_parquet(filtered_parquet, compression="zstd")
        del label_df
        gc.collect()
        input_parquet = filtered_parquet
    else:
        input_parquet = args.input_parquet

    # --- load embedding matrix (Arrow zero-copy, like pca.py) ---
    X, N, D = load_embedding_matrix(input_parquet)

    if N == 0:
        # Write an empty parquet preserving schema
        df = pl.scan_parquet(input_parquet).collect()
        df.write_parquet(args.output_parquet, compression="zstd")
        print("Input is empty – wrote empty parquet.")
        return

    print(f"Loaded {N} embeddings, D={D}")

    # --- load pk column only (Arrow-backed, no Python list) ---
    pk_series = pl.scan_parquet(input_parquet).select("pk").collect()["pk"]

    # Sort by pk to group identical pks together; use Polars for the sort
    # to stay vectorized, then get np indices.
    sort_indices = pk_series.arg_sort().to_numpy()

    # Reorder embedding matrix by sort order (vectorized)
    X_sorted = X[sort_indices]
    del X
    gc.collect()

    # Sorted pk values (as Arrow for zero-copy comparison)
    pk_sorted = pk_series.gather(pl.Series(sort_indices))
    del pk_series
    gc.collect()

    # --- find group boundaries using vectorized Polars ops ---
    # A new group starts where pk differs from the previous row
    boundary_mask = pl.concat(
        [pl.Series([True]),  # first row is always a boundary
         pk_sorted[1:] != pk_sorted[:-1]],
        rechunk=True
    )
    group_starts = boundary_mask.arg_true().to_numpy().astype(np.int64)  # ensure int64 indices
    n_groups = len(group_starts)

    # Group sizes (vectorized)
    group_sizes = np.diff(np.append(group_starts, N))

    print(f"Averaging {N} rows into {n_groups} groups (D={D})")

    # --- compute group sums with np.add.reduceat (fully vectorized) ---
    group_sums = np.add.reduceat(X_sorted, group_starts, axis=0)

    del X_sorted
    gc.collect()

    # Average
    avg = group_sums / group_sizes[:, None]
    del group_sums
    gc.collect()

    # Re-normalise to unit length (same convention as sentence-transformers)
    norms = np.linalg.norm(avg, axis=1, keepdims=True)
    norms[norms == 0] = 1.0
    avg /= norms

    avg32 = avg.astype(np.float32, copy=False)
    del avg
    gc.collect()

    # --- build one-row-per-group metadata (first row of each group) ---
    first_row_indices = sort_indices[group_starts]

    # Read schema from filtered parquet for meta columns
    filtered_schema = pl.read_parquet_schema(input_parquet)
    meta_cols = [c for c in filtered_schema if c not in ("embedding", "hash", "text_to_embed",
                 "string_type", "curated_from_source", "curated_from_subject_categories")]
    meta_df = (
        pl.scan_parquet(input_parquet)
        .select(meta_cols)
        .collect()
    )[first_row_indices.tolist()]

    # --- build embedding column (Arrow zero-copy, like pca.py) ---
    flat_values = avg32.reshape(-1)
    values_arr = pa.array(flat_values, type=pa.float32())
    emb_list = pa.FixedSizeListArray.from_arrays(values_arr, list_size=D)
    emb_series = pl.Series("embedding", emb_list)

    out_df = meta_df.with_columns(emb_series)
    out_df.write_parquet(args.output_parquet, compression="zstd")
    print(f"Wrote {n_groups} averaged embeddings to {args.output_parquet}")


if __name__ == "__main__":
    main()
