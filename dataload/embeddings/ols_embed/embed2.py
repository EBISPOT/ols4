import argparse
from pathlib import Path
from typing import List
import polars as pl
from sentence_transformers import SentenceTransformer
import torch

torch.backends.cuda.matmul.allow_tf32 = True
torch.set_float32_matmul_precision("high")


def main():
    parser = argparse.ArgumentParser(description="Generate embeddings for ontology terms")
    parser.add_argument("--input-tsv", type=str, required=True)
    parser.add_argument("--output-parquet", type=str, required=True)
    parser.add_argument("--model-name", type=str, default="sentence-transformers/all-MiniLM-L6-v2")
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--device", type=str, default="cpu")

    args = parser.parse_args()

    input_tsv = Path(args.input_tsv)
    output_parquet = Path(args.output_parquet)

    # Check if input file is empty or doesn't exist
    if not input_tsv.exists() or input_tsv.stat().st_size == 0:
        # Create empty parquet with correct schema
        df = pl.DataFrame({
            'hash': pl.Series([], dtype=pl.Utf8),
            'text_to_embed': pl.Series([], dtype=pl.Utf8),
            'embedding': pl.Series([], dtype=pl.List(pl.Float32))
        })
        df.write_parquet(output_parquet)
        return

    df = pl.read_csv(input_tsv, separator="\t", has_header=False, new_columns=["hash", "text_to_embed"])
    
    # Check if dataframe is empty after reading
    if df.height == 0:
        df = df.with_columns([
            pl.Series("embedding", [], dtype=pl.List(pl.Float32))
        ])
        df.write_parquet(output_parquet)
        return

    model = SentenceTransformer(args.model_name, trust_remote_code=True)
    terms = df["text_to_embed"].to_list()

    embeddings = model.encode(
        terms,
        show_progress_bar=True,
        convert_to_numpy=True,
        normalize_embeddings=True,
        batch_size=args.batch_size,
        device=args.device,
    )

    df = df.with_columns([
        pl.Series("embedding", embeddings.tolist(), dtype=pl.List(pl.Float32))
    ])

    df.write_parquet(output_parquet)


if __name__ == "__main__":
    main()


