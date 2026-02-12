#!/usr/bin/env python3
import argparse
import os
from pathlib import Path
from typing import List
import polars as pl
import time
import random
import openai
from openai import OpenAI

def embed_batch(
    client: OpenAI,
    texts: List[str],
    model: str,
    batch_size: int = 2000,      # typical per-request array cap is ~2k items
    max_retries: int = 100,
    initial_backoff: float = 1.0,
    max_backoff: float = 32.0,
    jitter_fraction: float = 0.25,
) -> List[List[float]]:
    """
    Embed texts in batches with exponential backoff and jitter.

    SDK notes:
    - We catch `openai.RateLimitError` (HTTP 429) and `openai.APIStatusError` (4xx/5xx),
      plus connection/timeout classes. The base class is `openai.APIError`.
    - The SDK already auto-retries some errors twice by default; to avoid "double
      retrying", this function disables per-request retries via `.with_options(max_retries=0)`.
    """
    all_embeddings: List[List[float]] = []

    for i in range(0, len(texts), batch_size):
        batch = texts[i : i + batch_size]

        retries = 0
        backoff = initial_backoff

        while True:
            try:
                # turn off the SDK's built-in retries so our loop controls them
                resp = client.with_options(max_retries=0).embeddings.create(
                    model=model,
                    input=batch,
                )
                all_embeddings.extend([d.embedding for d in resp.data])
                break  # success

            except openai.RateLimitError as e:
                # 429 — back off and retry
                if retries >= max_retries:
                    raise
                sleep = backoff * (1.0 + jitter_fraction * random.random())
                print(f"429 rate limit. Retrying in {sleep:.1f}s... (attempt {retries+1}/{max_retries})")
                time.sleep(sleep)
                backoff = min(backoff * 2.0, max_backoff)
                retries += 1

            except openai.APIStatusError as e:
                # Non-2xx; retry only on transient 5xx
                if e.status_code >= 500 and retries < max_retries:
                    sleep = backoff * (1.0 + jitter_fraction * random.random())
                    print(f"Server {e.status_code}. Retrying in {sleep:.1f}s... (attempt {retries+1}/{max_retries})")
                    time.sleep(sleep)
                    backoff = min(backoff * 2.0, max_backoff)
                    retries += 1
                else:
                    raise

            except (openai.APIConnectionError, openai.APITimeoutError) as e:
                # Network hiccups / timeouts: retry like transient errors
                if retries >= max_retries:
                    raise
                sleep = backoff * (1.0 + jitter_fraction * random.random())
                print(f"{e.__class__.__name__}. Retrying in {sleep:.1f}s... (attempt {retries+1}/{max_retries})")
                time.sleep(sleep)
                backoff = min(backoff * 2.0, max_backoff)
                retries += 1

    return all_embeddings

def main():
    parser = argparse.ArgumentParser(description="Generate embeddings for ontology terms using OpenAI API")
    parser.add_argument("--input-tsv", type=str, required=True)
    parser.add_argument("--output-parquet", type=str, required=True)
    parser.add_argument("--model-name", type=str, default="text-embedding-3-small")
    parser.add_argument("--batch-size", type=int, default=2000, help="Number of texts to send per API request")
    parser.add_argument("--api-key", type=str, default=None, help="OpenAI API key (or set OPENAI_API_KEY env var)")

    args = parser.parse_args()

    input_tsv = Path(args.input_tsv)
    output_parquet = Path(args.output_parquet)

    api_key = args.api_key or os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise ValueError("OpenAI API key must be provided via --api-key or OPENAI_API_KEY environment variable")

    client = OpenAI(api_key=api_key)

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

    terms = df["text_to_embed"].to_list()
    
    print(f"Embedding {len(terms)} terms using {args.model_name}...")
    
    model_name = args.model_name.replace("openai/", "")
    
    embeddings = embed_batch(client, terms, model_name, args.batch_size)

    df = df.with_columns([
        pl.Series("embedding", embeddings, dtype=pl.List(pl.Float32))
    ])

    df.write_parquet(output_parquet)
    print(f"Saved embeddings to {output_parquet}")


if __name__ == "__main__":
    main()
