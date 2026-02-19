#!/usr/bin/env bash

set -e

MODEL="$1"
if [ -z "$MODEL" ]; then
    echo "Usage: $0 <model>"
    exit 1
fi

MODEL_NAME="${MODEL##*/}"

srun -t 1:0:0 --mem 32g -c 16 singularity run work/singularity/ghcr.io-ebispot-ols_embeddings-dev.img bash -c "/ols_embed/ols_embed/.venv/bin/transformers-cli download --trust-remote-code --cache-dir /tmp $MODEL && mkdir -p ./models/$MODEL_NAME && cp -Lr /tmp/models--*/snapshots/*/* ./models/$MODEL_NAME/"

echo "Model stored in: ./models/$MODEL_NAME"


