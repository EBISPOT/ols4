#!/usr/bin/env python3
"""
Visualize embeddings from a parquet file using TorchDR dimensionality reduction.

Creates:
1. A parquet file with 2D coordinates and metadata (without full embeddings)
2. A scatter plot image of the embeddings
"""

import torch
torch.backends.cuda.preferred_linalg_library("magma")


import argparse
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import polars as pl
import torch
import torchdr


def main():
    parser = argparse.ArgumentParser(
        description="Reduce embeddings to 2D using TorchDR and create visualization"
    )
    parser.add_argument(
        "input_parquet",
        type=Path,
        help="Input parquet file containing embeddings",
    )
    parser.add_argument(
        "--output-parquet",
        type=Path,
        default=None,
        help="Output parquet file for 2D coordinates (default: input_name_torchdr.parquet)",
    )
    parser.add_argument(
        "--output-plot",
        type=Path,
        default=None,
        help="Output image file for the plot (default: input_name_torchdr.png)",
    )
    parser.add_argument(
        "--method",
        type=str,
        default="umap",
        choices=["tsne", "umap"],
        help="Dimensionality reduction method (default: umap)",
    )
    parser.add_argument(
        "--perplexity",
        type=int,
        default=30,
        help="t-SNE perplexity parameter (default: 30)",
    )
    parser.add_argument(
        "--n-neighbors",
        type=int,
        default=15,
        help="UMAP n_neighbors parameter (default: 15)",
    )
    parser.add_argument(
        "--min-dist",
        type=float,
        default=0.1,
        help="UMAP min_dist parameter (default: 0.1)",
    )
    parser.add_argument(
        "--random-state",
        type=int,
        default=42,
        help="Random state for reproducibility (default: 42)",
    )
    parser.add_argument(
        "--device",
        type=str,
        default=None,
        help="Device to use (default: auto-detect cuda/cpu)",
    )
    parser.add_argument(
        "--backend",
        type=str,
        default="auto",
        choices=["auto", "faiss", "torch"],
        help="Backend for nearest neighbor search (default: auto - faiss on cuda, torch otherwise)",
    )
    parser.add_argument(
        "--color-by",
        type=str,
        default="ontology_id",
        help="Column to use for coloring points (default: ontology_id)",
    )

    args = parser.parse_args()

    # Set default output paths based on input filename
    input_stem = args.input_parquet.stem
    input_parent = args.input_parquet.parent

    method_suffix = args.method
    if args.output_parquet is None:
        args.output_parquet = input_parent / f"{input_stem}_{method_suffix}.parquet"
    if args.output_plot is None:
        args.output_plot = input_parent / f"{input_stem}_{method_suffix}.png"

    # Determine device
    if args.device is None:
        if torch.cuda.is_available():
            device = "cuda"
        else:
            # MPS has incomplete support for some operations used by TorchDR
            # Fall back to CPU for reliability
            device = "cpu"
    else:
        device = args.device
    print(f"Using device: {device}")

    # Determine backend for nearest neighbor search
    if args.backend == "auto":
        # Use faiss on CUDA for speed, pure torch otherwise (faiss-cpu can segfault)
        backend = "faiss" if device == "cuda" else None
    elif args.backend == "faiss":
        backend = "faiss"
    else:
        backend = None
    print(f"Using backend: {backend or 'torch'}")

    print(f"Reading embeddings from {args.input_parquet}...")
    df = pl.read_parquet(args.input_parquet)
    print(f"Loaded {len(df)} rows")

    # Extract embeddings as numpy array and convert to torch tensor
    print("Extracting embedding vectors...")
    embeddings = np.array(df["embedding"].to_list())
    print(f"Embedding shape: {embeddings.shape}")
    
    embeddings_tensor = torch.tensor(embeddings, dtype=torch.float32, device=device)

    # Run dimensionality reduction with TorchDR
    torch.manual_seed(args.random_state)
    if args.method == "tsne":
        print(f"Running TorchDR t-SNE (perplexity={args.perplexity})...")
        reducer = torchdr.TSNE(
            n_components=2,
            perplexity=args.perplexity,
            device=device,
            verbose=True,
        )
    else:  # umap
        print(f"Running TorchDR UMAP (n_neighbors={args.n_neighbors}, min_dist={args.min_dist})...")
        reducer = torchdr.UMAP(
            n_components=2,
            n_neighbors=args.n_neighbors,
            min_dist=args.min_dist,
            device=device,
            verbose=True,
            backend=backend,
        )
    
    coords_2d_tensor = reducer.fit_transform(embeddings_tensor)
    coords_2d = coords_2d_tensor.detach().cpu().numpy()
    print(f"{args.method.upper()} complete")

    # Create output dataframe with 2D coordinates and metadata (no embeddings)
    print(f"Writing output parquet to {args.output_parquet}...")
    output_df = df.drop("embedding", "text_to_embed").with_columns(
        [
            pl.Series("x", coords_2d[:, 0]),
            pl.Series("y", coords_2d[:, 1]),
        ]
    )
    output_df.write_parquet(args.output_parquet)
    print(f"Saved {len(output_df)} rows with columns: {output_df.columns}")

    # Create plot
    print(f"Creating plot, coloring by '{args.color_by}'...")
    fig, ax = plt.subplots(figsize=(12, 10))

    # Get unique values for coloring
    if args.color_by in df.columns:
        color_values = output_df[args.color_by].to_list()
        unique_values = list(set(color_values))
        color_map = {v: i for i, v in enumerate(unique_values)}
        colors = [color_map[v] for v in color_values]

        scatter = ax.scatter(
            coords_2d[:, 0],
            coords_2d[:, 1],
            c=colors,
            cmap="tab20",
            alpha=0.7,
            s=10,
        )

        handles = [
            plt.Line2D(
                [0],
                [0],
                marker="o",
                color="w",
                markerfacecolor=plt.cm.tab20(color_map[v] / len(unique_values)),
                markersize=8,
                label=v,
            )
            for v in unique_values
        ]
        ax.legend(handles=handles, title=args.color_by, loc="best", fontsize=8)
    else:
        ax.scatter(coords_2d[:, 0], coords_2d[:, 1], alpha=0.7, s=10)

    ax.set_xlabel(f"{args.method.upper()} 1")
    ax.set_ylabel(f"{args.method.upper()} 2")
    ax.set_title(f"{args.method.upper()} projection of embeddings ({len(df)} points)")

    plt.tight_layout()
    plt.savefig(args.output_plot, dpi=150)
    print(f"Saved plot to {args.output_plot}")

    print("Done!")


if __name__ == "__main__":
    main()
