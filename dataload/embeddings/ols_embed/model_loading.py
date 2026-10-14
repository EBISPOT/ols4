"""Custom model code is an explicit, revision-pinned exception to the safe default."""
import hashlib
import json
from pathlib import Path

TRUSTED_MODELS = json.loads(Path(__file__).with_name("trusted_models.json").read_text())


def model_load_kwargs(model_name_or_path):
    """Return constructor kwargs, rejecting altered local copies of trusted models.

    The policy ships inside the image, separately from the mounted model data.
    Only Python and JSON files select/implement custom code; weights are not
    rehashed at startup. Model storage and the HF cache must be read-only to
    untrusted users to prevent modification between verification and import.
    """
    path = Path(model_name_or_path)
    if path.is_symlink():
        raise ValueError("Model directories must not be symlinks")
    if not path.exists():
        policy = TRUSTED_MODELS.get(model_name_or_path)
        if policy is None:
            return {"trust_remote_code": False}
        return {"trust_remote_code": True, "revision": policy["revision"],
                "model_kwargs": {"code_revision": policy["revision"]},
                "tokenizer_kwargs": {"code_revision": policy["revision"]},
                "config_kwargs": {"code_revision": policy["revision"]}}

    if not path.is_dir():
        raise ValueError("A local model must be a directory")
    policy = next((p for repo, p in TRUSTED_MODELS.items()
                   if path.name == repo.rsplit("/", 1)[-1]), None)
    if policy is None:
        return {"trust_remote_code": False, "local_files_only": True}

    # Validate the complete Python/JSON set, including added nested modules.
    path = path.resolve()
    if any(p.is_symlink() for p in path.rglob("*")):
        raise ValueError("Trusted local models must not contain symlinks")
    files = {str(p.relative_to(path)): p for p in path.rglob("*")
             if p.suffix in {".py", ".json"}}
    if files.keys() != policy["files"].keys():
        raise ValueError("Trusted model code/configuration file set differs from the approved revision")
    for name, file in files.items():
        if not file.is_file():
            raise ValueError("Trusted model code/configuration must not contain symlinks")
        digest = hashlib.sha256(file.read_bytes()).hexdigest()
        if digest != policy["files"][name]:
            raise ValueError(f"Trusted model code/configuration differs from the approved revision: {name}")
    return {"trust_remote_code": True, "local_files_only": True}
