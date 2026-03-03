#!/usr/bin/env python3
"""
Convert a UMAP parquet file into the compressed binary format consumed by
the frontend UMAPViewer component.

Produces four files in the output directory:
  umap.bin.gz    – 8-bit coords + ontology deltas + JSON metadata header
  umap16.bin.gz  – 16-bit coords (loaded on demand for hi-res zoom)
  labels.txt.gz  – one label per line (Hilbert-sorted order)
  iris.txt.gz    – one IRI per line  (Hilbert-sorted order)

Points are sorted along a Hilbert curve and delta-encoded for compactness.
"""

import argparse
import gzip
import json
from pathlib import Path

import numpy as np
import polars as pl


def hilbert_index(x: np.ndarray, y: np.ndarray, order: int = 8) -> np.ndarray:
    """Compute Hilbert curve index for 2D points."""
    d = np.zeros(len(x), dtype=np.uint32)
    x, y = x.copy(), y.copy()
    for s_exp in range(order - 1, -1, -1):
        s = 1 << s_exp
        rx = ((x & s) > 0).astype(np.uint32)
        ry = ((y & s) > 0).astype(np.uint32)
        d += (s * s) * ((3 * rx) ^ ry)
        mask = ry == 0
        swap_mask = mask & (rx == 1)
        x = np.where(swap_mask, s - 1 - x, x)
        y = np.where(swap_mask, s - 1 - y, y)
        x, y = np.where(mask, y, x), np.where(mask, x, y)
    return d


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Convert UMAP parquet to compressed binary web format"
    )
    parser.add_argument(
        "input_parquet",
        type=Path,
        help="Input parquet file with x, y, ontology_id, label, iri columns",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        required=True,
        help="Directory to write output files into",
    )
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)

    print(f"Loading {args.input_parquet}...")
    df = pl.read_parquet(args.input_parquet)
    n = len(df)
    print(f"  {n:,} points")

    x = df["x"].to_numpy()
    y = df["y"].to_numpy()
    x_min, x_max = float(x.min()), float(x.max())
    y_min, y_max = float(y.min()), float(y.max())
    x_span, y_span = x_max - x_min, y_max - y_min

    # Normalize to uint8 / uint16
    print("Normalizing coordinates...")
    x8 = ((x - x_min) / x_span * 255).astype(np.uint8)
    y8 = ((y - y_min) / y_span * 255).astype(np.uint8)
    x16 = ((x - x_min) / x_span * 65535).astype(np.uint16)
    y16 = ((y - y_min) / y_span * 65535).astype(np.uint16)

    # Hilbert sort
    print("Computing Hilbert curve order...")
    hilbert_idx = hilbert_index(x8.astype(np.int32), y8.astype(np.int32))
    sort_idx = np.argsort(hilbert_idx)

    # Ontology ID mapping
    print("Processing ontology IDs...")
    unique_ontologies = sorted(df["ontology_id"].unique().to_list())
    ont_to_idx = {ont: i for i, ont in enumerate(unique_ontologies)}
    ont_indices = np.array(
        [ont_to_idx[o] for o in df["ontology_id"].to_list()], dtype=np.uint16
    )
    print(f"  {len(unique_ontologies)} unique ontologies")

    # Apply Hilbert sort
    x8_sorted = x8[sort_idx]
    y8_sorted = y8[sort_idx]
    x16_sorted = x16[sort_idx]
    y16_sorted = y16[sort_idx]
    ont_sorted = ont_indices[sort_idx]

    labels_col = df["label"].fill_null("").to_list()
    iris_col = df["iri"].fill_null("").to_list()
    labels_sorted = [labels_col[i] for i in sort_idx]
    iris_sorted = [iris_col[i] for i in sort_idx]

    # Delta encode 8-bit positions
    print("Delta encoding...")
    x_delta = np.diff(x8_sorted.astype(np.int16), prepend=x8_sorted[0])
    y_delta = np.diff(y8_sorted.astype(np.int16), prepend=y8_sorted[0])
    x_delta[0] = x8_sorted[0]
    y_delta[0] = y8_sorted[0]

    # Delta encode 16-bit positions
    x16_delta = np.diff(x16_sorted.astype(np.int32), prepend=x16_sorted[0])
    y16_delta = np.diff(y16_sorted.astype(np.int32), prepend=y16_sorted[0])
    x16_delta[0] = x16_sorted[0]
    y16_delta[0] = y16_sorted[0]

    # Delta encode ontology indices
    ont_delta = np.diff(ont_sorted.astype(np.int32), prepend=ont_sorted[0])
    ont_delta[0] = ont_sorted[0]

    # Ontology bounds (5th-95th percentile) for zoom targets
    print("Calculating ontology bounds...")
    x16_norm = x16_sorted / 65535.0
    y16_norm = y16_sorted / 65535.0
    ont_bounds: dict[str, dict] = {}
    for ont_idx, ont_name in enumerate(unique_ontologies):
        mask = ont_sorted == ont_idx
        xs = x16_norm[mask]
        ys = y16_norm[mask]
        if len(xs) == 0:
            continue
        lo = max(0, int(len(xs) * 0.05))
        hi = min(len(xs) - 1, int(len(xs) * 0.95))
        xs_s = np.sort(xs)
        ys_s = np.sort(ys)
        min_x, max_x = float(xs_s[lo]), float(xs_s[hi])
        min_y, max_y = float(ys_s[lo]), float(ys_s[hi])
        pad_x = (max_x - min_x) * 0.15
        pad_y = (max_y - min_y) * 0.15
        cx = (min_x + max_x) / 2
        cy = (min_y + max_y) / 2
        width = (max_x - min_x) + pad_x * 2
        height = (max_y - min_y) + pad_y * 2
        if width == 0 and height == 0:
            continue
        zoom_scale = min(500, max(1, 0.9 / max(width, height)))
        ont_bounds[ont_name] = {
            "cx": round(cx, 6),
            "cy": round(cy, 6),
            "scale": round(zoom_scale, 2),
        }

    # Metadata JSON
    meta = {
        "count": n,
        "x_min": x_min,
        "x_max": x_max,
        "y_min": y_min,
        "y_max": y_max,
        "ontologies": unique_ontologies,
        "bounds": ont_bounds,
    }
    meta_json = json.dumps(meta).encode("utf-8")
    meta_len = len(meta_json)

    # ── Write output files ──────────────────────────────────────────────
    print("Saving compressed binary files...")

    # umap.bin.gz  – 8-bit coords interleaved + ontology deltas
    coords8 = np.empty(n * 2, dtype=np.int8)
    coords8[0::2] = x_delta.astype(np.int8)
    coords8[1::2] = y_delta.astype(np.int8)
    with gzip.open(args.output_dir / "umap.bin.gz", "wb", compresslevel=9) as f:
        f.write(meta_len.to_bytes(4, "little"))
        f.write(meta_json)
        f.write(coords8.tobytes())
        f.write(ont_delta.astype(np.int16).tobytes())

    # umap16.bin.gz – 16-bit coords interleaved
    coords16 = np.empty(n * 2, dtype=np.int16)
    coords16[0::2] = x16_delta.astype(np.int16)
    coords16[1::2] = y16_delta.astype(np.int16)
    with gzip.open(args.output_dir / "umap16.bin.gz", "wb", compresslevel=9) as f:
        f.write(coords16.tobytes())

    # labels.txt.gz
    print("Saving labels...")
    labels_text = "\n".join(
        str(lbl).replace("\n", " ")[:100] for lbl in labels_sorted
    )
    with gzip.open(args.output_dir / "labels.txt.gz", "wb", compresslevel=9) as f:
        f.write(labels_text.encode("utf-8"))

    # iris.txt.gz
    print("Saving IRIs...")
    iris_text = "\n".join(str(iri).replace("\n", "") for iri in iris_sorted)
    with gzip.open(args.output_dir / "iris.txt.gz", "wb", compresslevel=9) as f:
        f.write(iris_text.encode("utf-8"))

    # Report sizes
    print("\nOutput files:")
    total = 0
    for fpath in sorted(args.output_dir.iterdir()):
        size = fpath.stat().st_size
        total += size
        print(f"  {fpath.name}: {size:,} bytes ({size / 1024:.1f} KB)")
    print(f"  TOTAL: {total:,} bytes ({total / 1024:.1f} KB)")

    print("Done!")


if __name__ == "__main__":
    main()
