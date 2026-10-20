"""Custom model code is an explicit, revision-pinned exception to the safe default."""
import hashlib
import json
import logging
from pathlib import Path

TRUSTED_MODELS = json.loads(Path(__file__).with_name("trusted_models.json").read_text())
logger = logging.getLogger(__name__)


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


class TokenizerFallbackAutoProcessor:
    """``AutoProcessor`` that falls back to the plain tokenizer when the hinted processor cannot load.

    Some text-only repositories carry a ``processor_class`` hint copied from a multimodal base
    model: microsoft/harrier-oss-v1-27b names ``Gemma3Processor`` in tokenizer_config.json but
    ships no image processor. Sentence Transformers 6 loads every model through ``AutoProcessor``,
    which honours the hint and raises before the tokenizer is read. Transformers itself tries
    ``AutoTokenizer`` last when no processor class is named; this extends that fallback to a named
    class that cannot be built. The keyword arguments, including ``trust_remote_code``, are passed
    on unchanged, so the fallback grants nothing the processor load did not have.
    """

    @classmethod
    def from_pretrained(cls, model_name_or_path, **kwargs):
        from transformers import AutoProcessor, AutoTokenizer

        try:
            return AutoProcessor.from_pretrained(model_name_or_path, **kwargs)
        except OSError as processor_error:
            try:
                tokenizer = AutoTokenizer.from_pretrained(model_name_or_path, **kwargs)
            except Exception as tokenizer_error:
                raise processor_error from tokenizer_error
            logger.warning("Using the tokenizer of %s because its processor could not be loaded: %s",
                           model_name_or_path, processor_error)
            return tokenizer


def install_processor_fallback():
    """Route Sentence Transformers' processor loading through TokenizerFallbackAutoProcessor."""
    import sentence_transformers.base.modules.transformer as transformer_module

    if not hasattr(transformer_module, "AutoProcessor"):
        raise RuntimeError("Sentence Transformers no longer loads processors through "
                           "sentence_transformers.base.modules.transformer.AutoProcessor; review install_processor_fallback.")
    transformer_module.AutoProcessor = TokenizerFallbackAutoProcessor
