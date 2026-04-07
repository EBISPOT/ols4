#!/usr/bin/env python3
import argparse
import os
import re
from pathlib import Path
from typing import List
import polars as pl
import time
import random
import openai
from openai import OpenAI

# Control characters that cause OpenAI 400 BadRequestError
_CONTROL_CHARS_RE = re.compile(r'[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-\x9f]')


def _sanitize(text: str) -> str:
    """Strip control characters that OpenAI rejects."""
    return _CONTROL_CHARS_RE.sub('', text)

def _embed_single_batch(
    client: OpenAI,
    batch: List[str],
    model: str,
    max_retries: int,
    initial_backoff: float,
    max_backoff: float,
    jitter_fraction: float,
) -> List[List[float]]:
    """
    Embed a single batch. On 400 BadRequestError, adaptively split the batch
    in half and recurse to isolate the offending item(s).  When a single item
    still triggers a 400, raise so we don't silently return wrong-dimension
    zero vectors.
    """
    retries = 0
    backoff = initial_backoff

    while True:
        try:
            resp = client.with_options(max_retries=0).embeddings.create(
                model=model,
                input=batch,
            )
            return [d.embedding for d in resp.data]

        except openai.BadRequestError as e:
            if len(batch) == 1:
                raise  # single item is genuinely bad
            mid = len(batch) // 2
            print(f"400 BadRequestError on batch of {len(batch)}. Splitting in half and retrying...")
            left  = _embed_single_batch(client, batch[:mid], model, max_retries, initial_backoff, max_backoff, jitter_fraction)
            right = _embed_single_batch(client, batch[mid:], model, max_retries, initial_backoff, max_backoff, jitter_fraction)
            return left + right

        except openai.RateLimitError as e:
            if retries >= max_retries:
                raise
            sleep = backoff * (1.0 + jitter_fraction * random.random())
            print(f"429 rate limit. Retrying in {sleep:.1f}s... (attempt {retries+1}/{max_retries})")
            time.sleep(sleep)
            backoff = min(backoff * 2.0, max_backoff)
            retries += 1

        except openai.APIStatusError as e:
            if e.status_code >= 500 and retries < max_retries:
                sleep = backoff * (1.0 + jitter_fraction * random.random())
                print(f"Server {e.status_code}. Retrying in {sleep:.1f}s... (attempt {retries+1}/{max_retries})")
                time.sleep(sleep)
                backoff = min(backoff * 2.0, max_backoff)
                retries += 1
            else:
                raise

        except (openai.APIConnectionError, openai.APITimeoutError) as e:
            if retries >= max_retries:
                raise
            sleep = backoff * (1.0 + jitter_fraction * random.random())
            print(f"{e.__class__.__name__}. Retrying in {sleep:.1f}s... (attempt {retries+1}/{max_retries})")
            time.sleep(sleep)
            backoff = min(backoff * 2.0, max_backoff)
            retries += 1


def embed_batch(
    client: OpenAI,
    texts: List[str],
    model: str,
    batch_size: int = 2000,
    max_retries: int = 100,
    initial_backoff: float = 1.0,
    max_backoff: float = 32.0,
    jitter_fraction: float = 0.25,
) -> List[List[float]]:
    """
    Embed texts in batches with exponential backoff and jitter.
    Delegates each batch to _embed_single_batch which handles adaptive
    splitting on BadRequestError.
    """
    all_embeddings: List[List[float]] = []

    for i in range(0, len(texts), batch_size):
        batch = texts[i : i + batch_size]
        result = _embed_single_batch(client, batch, model, max_retries, initial_backoff, max_backoff, jitter_fraction)
        all_embeddings.extend(result)

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

    # Sanitize: strip control chars, replace empty strings with placeholder
    terms = [_sanitize(t) for t in terms]
    terms = [t if t.strip() else '[empty]' for t in terms]
    
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
