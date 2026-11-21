import argparse
from pathlib import Path
from typing import List
import polars as pl
from sentence_transformers import SentenceTransformer
import torch
from model_loading import install_processor_fallback, model_load_kwargs

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

    install_processor_fallback()
    load_kwargs = model_load_kwargs(args.model_name)
    n_gpus = torch.cuda.device_count()
    if n_gpus > 1:
        # Cap each GPU at 75 GiB so the model is split evenly with headroom
        # for activations, and nothing gets offloaded to disk/meta.
        per_gpu = "75GiB"
        max_mem = {i: per_gpu for i in range(n_gpus)}
        print(f"Detected {n_gpus} GPUs — loading with device_map='auto', max_memory={max_mem}")

        # SentenceTransformer.__init__ calls self.to(device) after loading,
        # which conflicts with accelerate's device_map dispatch (tries to
        # consolidate all params onto one GPU).  Similarly, encode() calls
        # self.to(self.device).  Disable both by making .to() a no-op.
        SentenceTransformer.to = lambda self, *a, **kw: self
        load_kwargs["model_kwargs"] = {
            **load_kwargs.get("model_kwargs", {}),
            "device_map": "auto", "max_memory": max_mem,
        }
        model = SentenceTransformer(args.model_name, **load_kwargs)
        encode_device = None
    else:
        model = SentenceTransformer(args.model_name, **load_kwargs)
        encode_device = args.device

    terms = df["text_to_embed"].to_list()

    embeddings = model.encode(
        terms,
        show_progress_bar=True,
        convert_to_numpy=True,
        normalize_embeddings=True,
        batch_size=args.batch_size,
        device=encode_device,
    )

    df = df.with_columns([
        pl.Series("embedding", embeddings.tolist(), dtype=pl.List(pl.Float32))
    ])

    df.write_parquet(output_parquet)


if __name__ == "__main__":
    main()


