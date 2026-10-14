"""Download data at a pinned revision without importing any model code."""
import argparse
from pathlib import Path
import shutil
import tempfile

from huggingface_hub import HfApi, snapshot_download
from model_loading import TRUSTED_MODELS, model_load_kwargs


def download_model(repo_id, models_dir, revision=None):
    # Reject path components; repo_id is also used to construct the local name.
    parts = repo_id.split("/")
    if len(parts) != 2 or any(not p or p in {".", ".."} or "\\" in p for p in parts):
        raise ValueError("Expected a Hugging Face repo ID in owner/model form")
    policy = TRUSTED_MODELS.get(repo_id)
    if policy:
        if revision is not None and revision != policy["revision"]:
            raise ValueError("Custom-code model revision must match the approved revision")
        revision = policy["revision"]
    else:
        revision = HfApi().model_info(repo_id, revision=revision).sha
    models_dir = Path(models_dir)
    models_dir.mkdir(parents=True, exist_ok=True)
    destination = models_dir / parts[1]
    if destination.exists() or destination.is_symlink():
        raise ValueError(f"Destination already exists; choose a fresh models directory: {destination}")
    snapshot = snapshot_download(repo_id=repo_id, revision=revision)
    # Materialize cache symlinks into regular files and publish only after verification.
    with tempfile.TemporaryDirectory(prefix=".download-", dir=models_dir) as tmp:
        staged = Path(tmp) / parts[1]
        shutil.copytree(snapshot, staged, symlinks=False)
        model_load_kwargs(str(staged))
        staged.rename(destination)
    return destination, revision


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model")
    parser.add_argument("--models-dir", default="./models")
    parser.add_argument("--revision")
    args = parser.parse_args()
    destination, revision = download_model(args.model, args.models_dir, args.revision)
    print(f"Model {args.model}@{revision} stored in: {destination}")


if __name__ == "__main__":
    main()
