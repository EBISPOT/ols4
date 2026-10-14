#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec "${OLS_EMBED_PYTHON:-python3}" "$script_dir/ols_embed/download_model.py" "$@"
