import importlib.util
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import tomllib
import unittest
from unittest.mock import patch

import numpy as np
import polars as pl
from sentence_transformers import SentenceTransformer

SOURCE = Path(__file__).resolve().parents[1] / "ols_embed"
sys.path.insert(0, str(SOURCE))


def import_script(name):
    spec = importlib.util.spec_from_file_location(name, SOURCE / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class LoadingCallSiteTests(unittest.TestCase):
    def test_cli_does_not_trust_unknown_model_on_either_gpu_path(self):
        for gpu_count in (0, 2):
            with self.subTest(gpu_count=gpu_count), tempfile.TemporaryDirectory() as tmp:
                source = Path(tmp) / "input.tsv"
                output = Path(tmp) / "output.parquet"
                source.write_text("hash\ttext\n")
                cli = import_script("embed2")
                argv = ["embed2", "--input-tsv", str(source), "--output-parquet", str(output),
                        "--model-name", "unknown/model"]
                with patch.object(cli, "SentenceTransformer") as factory, \
                     patch.object(cli.torch.cuda, "device_count", return_value=gpu_count), \
                     patch.object(sys, "argv", argv):
                    factory.return_value.encode.return_value = np.ones((1, 3), dtype=np.float32)
                    cli.main()
                    self.assertFalse(factory.call_args.kwargs["trust_remote_code"])
                    self.assertEqual(pl.read_parquet(output)["embedding"].to_list(), [[1., 1., 1.]])

    def test_cli_keeps_code_pin_and_gpu_dispatch_for_approved_model(self):
        import model_loading
        model_name = "nvidia/llama-embed-nemotron-8b"
        revision = model_loading.TRUSTED_MODELS[model_name]["revision"]
        for gpu_count in (0, 2):
            with self.subTest(gpu_count=gpu_count), tempfile.TemporaryDirectory() as tmp:
                source = Path(tmp) / "input.tsv"
                source.write_text("hash\ttext\n")
                cli = import_script("embed2")
                argv = ["embed2", "--input-tsv", str(source), "--output-parquet", str(Path(tmp) / "out.parquet"), "--model-name", model_name]
                with patch.object(cli, "SentenceTransformer") as factory, \
                     patch.object(cli.torch.cuda, "device_count", return_value=gpu_count), \
                     patch.object(sys, "argv", argv):
                    factory.return_value.encode.return_value = np.ones((1, 3), dtype=np.float32)
                    cli.main()
                    kwargs = factory.call_args.kwargs
                    self.assertTrue(kwargs["trust_remote_code"])
                    self.assertEqual(kwargs["revision"], revision)
                    self.assertEqual(kwargs["model_kwargs"]["code_revision"], revision)
                    if gpu_count == 2:
                        self.assertEqual(kwargs["model_kwargs"]["device_map"], "auto")
                        self.assertEqual(kwargs["model_kwargs"]["max_memory"], {0: "75GiB", 1: "75GiB"})

    def test_server_does_not_trust_unknown_local_model(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / "unknown").mkdir()
            with patch.dict(os.environ, {"MODELS_DIR": tmp}), \
                 patch("sentence_transformers.SentenceTransformer") as factory:
                server = import_script("server")
                self.assertFalse(factory.call_args.kwargs["trust_remote_code"])
                self.assertIn("unknown", server.loaded_models)
                self.assertEqual(server.app.test_client().post("/spot/embed", json={"model": "absent", "text": ["x"]}).status_code, 404)

    def test_server_does_not_load_symlink_outside_models_directory(self):
        with tempfile.TemporaryDirectory() as tmp, tempfile.TemporaryDirectory() as external:
            (Path(tmp) / "unknown").symlink_to(external, target_is_directory=True)
            with patch.dict(os.environ, {"MODELS_DIR": tmp}), \
                 patch("sentence_transformers.SentenceTransformer") as factory:
                server = import_script("server")
                factory.assert_not_called()
                self.assertEqual(server.loaded_models, {})

    def test_lock_contains_effective_security_fixes(self):
        lock = tomllib.loads((SOURCE / "uv.lock").read_text())
        versions = {p["name"]: tuple(map(int, p["version"].split("."))) for p in lock["package"] if p["name"] in {"sentence-transformers", "anyio"}}
        self.assertGreaterEqual(versions["sentence-transformers"], (6, 0, 0))
        self.assertGreaterEqual(versions["anyio"], (4, 14, 2))


class TrustedModelTests(unittest.TestCase):
    def setUp(self):
        import model_loading
        self.loading = model_loading
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.model = Path(self.tmp.name) / "reviewed-model"
        self.model.mkdir()
        self.files = {"modules.json": "[]", "config.json": "{}", "modeling_reviewed.py": "# reviewed code\n"}
        for name, content in self.files.items():
            (self.model / name).write_text(content)
        self.revision = "a" * 40
        self.policy = {"reviewed/reviewed-model": {"revision": self.revision,
            "files": {name: hashlib.sha256(content.encode()).hexdigest() for name, content in self.files.items()}}}
        patcher = patch.object(self.loading, "TRUSTED_MODELS", self.policy)
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_custom_hub_model_pins_weights_config_tokenizer_and_code(self):
        kwargs = self.loading.model_load_kwargs("reviewed/reviewed-model")
        self.assertTrue(kwargs["trust_remote_code"])
        self.assertEqual(kwargs["revision"], self.revision)
        for key in ("config_kwargs", "tokenizer_kwargs", "model_kwargs"):
            self.assertEqual(kwargs[key]["code_revision"], self.revision)

    def test_identical_local_copy_is_allowed_offline(self):
        self.assertEqual(self.loading.model_load_kwargs(str(self.model)),
                         {"trust_remote_code": True, "local_files_only": True})

    def test_altered_config_cannot_enable_code_from_another_repo(self):
        (self.model / "config.json").write_text('{"auto_map":{"AutoModel":"evil/repo--modeling_evil.Model"}}')
        with self.assertRaisesRegex(ValueError, "differs"):
            self.loading.model_load_kwargs(str(self.model))

    def test_added_python_module_is_rejected(self):
        (self.model / "modeling_unreviewed.py").write_text("raise RuntimeError('must not execute')")
        with self.assertRaisesRegex(ValueError, "file set"):
            self.loading.model_load_kwargs(str(self.model))

    def test_missing_config_is_rejected(self):
        (self.model / "modules.json").unlink()
        with self.assertRaisesRegex(ValueError, "file set"):
            self.loading.model_load_kwargs(str(self.model))

    def test_nested_symlink_cannot_supply_model_code(self):
        nested = self.model / "nested"
        nested.symlink_to(self.model, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "symlinks"):
            self.loading.model_load_kwargs(str(self.model))

    def test_downloader_uses_approved_revision_and_materializes_snapshot(self):
        import download_model
        output = Path(self.tmp.name) / "downloads"
        with patch.object(download_model, "TRUSTED_MODELS", self.policy), \
             patch.object(download_model, "snapshot_download", return_value=str(self.model)) as download:
            destination, revision = download_model.download_model("reviewed/reviewed-model", output)
        download.assert_called_once_with(repo_id="reviewed/reviewed-model", revision=self.revision)
        self.assertEqual(revision, self.revision)
        self.assertEqual((destination / "config.json").read_text(), "{}")

    def test_downloader_rejects_custom_revision_override(self):
        import download_model
        with patch.object(download_model, "TRUSTED_MODELS", self.policy), \
             patch.object(download_model, "snapshot_download") as download:
            with self.assertRaisesRegex(ValueError, "approved revision"):
                download_model.download_model("reviewed/reviewed-model", self.tmp.name, revision="main")
            download.assert_not_called()

    def test_downloader_does_not_overwrite_existing_model(self):
        import download_model
        with patch.object(download_model, "TRUSTED_MODELS", self.policy), \
             patch.object(download_model, "snapshot_download") as download:
            with self.assertRaisesRegex(ValueError, "already exists"):
                download_model.download_model("reviewed/reviewed-model", self.tmp.name)
            download.assert_not_called()

    def test_server_refuses_tampered_approved_model_before_constructor(self):
        with patch.dict(os.environ, {"MODELS_DIR": self.tmp.name}), \
             patch("sentence_transformers.SentenceTransformer") as factory:
            (self.model / "modules.json").write_text("[{}]")
            server = import_script("server")
            factory.assert_not_called()
            self.assertEqual(server.loaded_models, {})


class RealLibrarySecurityTests(unittest.TestCase):
    def test_untrusted_local_custom_module_cannot_execute_at_import(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            marker = root / "executed"
            (root / "modeling_untrusted.py").write_text(
                "from pathlib import Path\nPath(" + repr(str(marker)) + ").write_text('executed')\nclass Custom: pass\n")
            (root / "modules.json").write_text(json.dumps([
                {"idx": 0, "name": "0", "path": "", "type": "modeling_untrusted.Custom"}]))
            with self.assertRaisesRegex(ValueError, "trust_remote_code"):
                SentenceTransformer(str(root), trust_remote_code=False, local_files_only=True)
            self.assertFalse(marker.exists())


if __name__ == "__main__":
    unittest.main()
