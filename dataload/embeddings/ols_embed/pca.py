#!/usr/bin/env python3
import argparse
import gc
import json

import numpy as np
import polars as pl
import pyarrow as pa
import joblib


def load_embedding_matrix(parquet_path: str):
    """
    Load the 'embedding' column using Polars/Arrow without going through Python
    list-of-lists. Returns a dense NumPy array (N, D) in float64, ready for full PCA.
    """

    # Load just the embedding column into Polars (Arrow-backed)
    df = pl.scan_parquet(parquet_path).select("embedding").collect()

    if "embedding" not in df.columns:
        raise KeyError("'embedding' column not found in Parquet file.")

    emb_series = df["embedding"]
    N = len(emb_series)
    if N == 0:
        raise ValueError("No rows in embedding column.")

    # Infer dimension from first row (cheap Python access only for this row)
    first = emb_series[0]
    if first is None:
        raise ValueError("First embedding is None; cannot infer dimension.")
    D = len(first)

    print(f"Inferred embedding shape: N={N}, D={D}")

    # Arrow array: ListArray or LargeListArray of floats
    emb_arrow = emb_series.to_arrow()

    # Accept both regular and "large" list arrays
    if not (isinstance(emb_arrow, pa.ListArray) or isinstance(emb_arrow, pa.LargeListArray)):
        raise TypeError(f"Expected ListArray or LargeListArray, got {type(emb_arrow)}")

    # Flattened child array (length should be N * D)
    values = emb_arrow.values

    expected_len = N * D
    if len(values) != expected_len:
        raise ValueError(
            f"Flattened values length {len(values)} != N*D ({expected_len}). "
            "This means at least one embedding has a different length."
        )

    # Convert to NumPy 1D, then reshape to (N, D)
    # This goes through Arrow buffers, *not* Python float objects.
    values_np = values.to_numpy()  # dtype float32 or float64

    X = values_np.reshape(N, D).astype(np.float64, copy=False)

    print(f"Loaded embedding matrix: N={N}, D={D}, dtype={X.dtype}")

    # Drop Polars/Arrow objects to free memory
    del df, emb_series, emb_arrow, values, values_np
    gc.collect()

    return X, N, D


def load_metadata_columns(parquet_path: str):
    """
    Load all columns except 'embedding' using Polars.
    Returns a Polars DataFrame and list of metadata column names.
    """

    df_lazy = pl.scan_parquet(parquet_path)
    columns = df_lazy.collect().columns
    meta_cols = [c for c in columns if c != "embedding"]

    if meta_cols:
        meta_df = df_lazy.select(meta_cols).collect()
        print("Loaded metadata columns:", meta_cols)
    else:
        meta_df = pl.DataFrame()
        print("No metadata columns found.")

    return meta_df, meta_cols


def full_pca_via_covariance(X: np.ndarray, n_components: int):
    """
    Exact PCA via eigen-decomposition of the covariance matrix.

    X: (N, D) float64
    Returns:
      X_reduced: (N, k) float64
      pca_model: dict with keys:
        - mean_
        - components_
        - explained_variance_
        - explained_variance_ratio_
        - n_samples_
        - n_features_
        - n_components_
    """
    N, D = X.shape
    k = n_components
    if k > min(N, D):
        print(f"WARNING: n_components={k} > min(N, D)={min(N, D)}; clamping to {min(N, D)}")
        k = min(N, D)

    print(f"Centering data in-place (N={N}, D={D})...")
    mean = X.mean(axis=0, dtype=np.float64)
    X -= mean  # in-place centering

    gc.collect()

    # Compute covariance matrix: C = (X^T X) / (N - 1)
    print("Computing covariance matrix C = X^T X / (N-1)...")
    # Shape: (D, D) = (4096, 4096)
    C = (X.T @ X) / (N - 1)
    print("Covariance matrix shape:", C.shape)

    gc.collect()

    # Full eigen-decomposition (symmetric matrix)
    print("Eigen-decomposition of covariance matrix (np.linalg.eigh)...")
    # eigh returns eigenvalues in ascending order
    eigvals, eigvecs = np.linalg.eigh(C)

    # We don't need C anymore
    del C
    gc.collect()

    # Sort eigenvalues/vectors in descending order
    idx = np.argsort(eigvals)[::-1]
    eigvals = eigvals[idx]
    eigvecs = eigvecs[:, idx]

    # Keep top k components
    eigvals_k = eigvals[:k]
    components = eigvecs[:, :k]  # shape (D, k)

    # Release unneeded eigenvectors
    del eigvecs
    gc.collect()

    total_var = eigvals.sum()
    explained_variance_ratio = eigvals_k / total_var

    print("Explained variance ratio (first 10):", explained_variance_ratio[:10])

    # Project data onto components: X_reduced = X_c @ components
    print("Projecting data onto top components...")
    X_reduced = X @ components  # shape (N, k)

    # Build a simple PCA "model" for later reuse
    pca_model = {
        "mean_": mean,
        "components_": components,
        "explained_variance_": eigvals_k,
        "explained_variance_ratio_": explained_variance_ratio,
        "n_samples_": N,
        "n_features_": D,
        "n_components_": k,
    }

    return X_reduced, pca_model


def main():
    parser = argparse.ArgumentParser(description="Exact PCA on embedding column using covariance eigendecomposition")
    parser.add_argument("input_parquet")
    parser.add_argument("output_parquet")
    parser.add_argument("n_components", type=int)
    parser.add_argument("--pca-model-out", default="pca_model.joblib")
    parser.add_argument("--pca-json-out", default="pca_model.json",
                        help="Output PCA model as JSON (for loading in Java backend)")
    args = parser.parse_args()

    # ---- Load embeddings (float64) ----
    X, N, D = load_embedding_matrix(args.input_parquet)

    # ---- Load metadata ----
    meta_df, meta_cols = load_metadata_columns(args.input_parquet)

    k = args.n_components

    # ---- PCA via covariance eigendecomposition (exact) ----
    print(f"Running PCA via covariance: N={N}, D={D}, k={k}")
    X_reduced, pca_model = full_pca_via_covariance(X, k)
    k = pca_model["n_components_"]  # may have been clamped to min(N, D)
    print("PCA completed.")

    joblib.dump(pca_model, args.pca_model_out)
    print(f"PCA model saved to {args.pca_model_out}")

    # Also save as JSON for loading in the Java backend
    pca_json = {
        "mean": pca_model["mean_"].tolist(),
        "components": pca_model["components_"].tolist(),
        "n_components": int(pca_model["n_components_"]),
        "n_features": int(pca_model["n_features_"]),
        "explained_variance_ratio": pca_model["explained_variance_ratio_"].tolist(),
    }
    with open(args.pca_json_out, 'w') as f:
        json.dump(pca_json, f)
    print(f"PCA model JSON saved to {args.pca_json_out}")

    # We no longer need the full X
    del X
    gc.collect()

    # ---- Downcast reduced embeddings to float32 ----
    print("Downcasting embeddings to float32...")
    Xr32 = X_reduced.astype(np.float32, copy=False)
    del X_reduced
    gc.collect()

    # ---- Build output Polars DataFrame with list[float32] embeddings ----
    print("Building Arrow list column for reduced embeddings...")

    N_out, k_out = Xr32.shape
    assert N_out == N
    assert k_out == k

    # Flatten to 1D
    flat_values = Xr32.reshape(-1)

    # Build Arrow array from the flat values
    values_arr = pa.array(flat_values, type=pa.float32())

    # Each row is a fixed-size list of length k_out
    emb_arrow = pa.FixedSizeListArray.from_arrays(values_arr, list_size=k_out)

    # Convert Arrow list array to Polars Series
    emb_series = pl.Series("embedding", emb_arrow)

    # Combine with metadata
    out_df = meta_df.with_columns(emb_series)

    # ---- Write Parquet via Polars ----
    print(f"Writing Parquet to {args.output_parquet} using Polars...")
    out_df.write_parquet(args.output_parquet, compression="zstd")

    print("Done.")


if __name__ == "__main__":
    main()


