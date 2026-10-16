# OLS embedding runtime

The image uses the committed `uv.lock` with `uv sync --frozen`. Security floors
are Sentence Transformers 6.0.0 and AnyIO 4.14.2. Version 5.6 of Sentence
Transformers still imports custom modules from local directories with
`trust_remote_code=False`; version 6 enforces that flag.

## Model trust

Both the batch CLI and HTTP server disable custom Python code by default.
`trusted_models.json`, shipped inside the image, approves the NVIDIA
`nvidia/llama-embed-nemotron-8b` snapshot at revision
`aa3b43a495a9b280d1bdb716da37c54bb495d630`. This snapshot's architecture needs
custom code. Hub loading pins both the model revision and the code revision.
The server grants the same exception to a local `llama-embed-nemotron-8b`
directory only when its complete Python/JSON file set and SHA-256 fingerprints
match the approved snapshot. Missing, changed, added files and symlinks are
rejected before the model constructor runs. Unapproved local custom modules
are rejected by Sentence Transformers 6.

The fingerprints cover executable code and configuration, including tokenizer
configuration, but do not cover the large weight shards. They are an integrity
check, not a filesystem sandbox. Mount `MODELS_DIR` read-only, restrict who can
populate it, and protect the Hugging Face and Transformers caches from untrusted
writers. Updating an approved model requires reviewing its code and
configuration, recording its immutable revision and new fingerprints, and
building a new image. Do not edit the policy inside a running container.

## Downloading and rollout

Use the new image to populate a **fresh** models directory:

```sh
python /opt/ols_embed/download_model.py nvidia/llama-embed-nemotron-8b --models-dir /data/models
```

Downloads do not import model code. The approved model's revision cannot be
overridden. Ordinary model downloads resolve the requested revision to an
immutable Hub commit. Files are copied out of the Hub cache into regular local
files, verified, then published as a directory; existing model directories are
never overwritten. The repository wrapper `../download_model.sh` runs the same
helper in a Python environment with the image's dependencies; set
`OLS_EMBED_PYTHON` to that environment's interpreter if necessary. It no longer
runs a site-specific Slurm/Singularity command or enables trust during download.

Rebuild the embedding image separately using its publication workflow. It pulls
fresh base images and explicitly upgrades Debian `perl-base`; the build fails
if its version is below `5.36.0-7+deb12u4`. The workflow scans the published image
by digest and retains its full vulnerability report.

Before deployment, run a smoke test with the actual NVIDIA weights on the GPU
host, verify embedding dimensions and normalization, and compare results against
a frozen input. Sentence Transformers 6 also upgrades Transformers to version 5,
so model compatibility is a rollout gate. Then mount the verified model directory
read-only and deploy the rebuilt image by digest. Changes here do not publish or
deploy an image by themselves.

## Regression checks

The `Embedding Security Tests` workflow runs only when embedding files or its
CI configuration change; Markdown-only changes are excluded. CI installs only
the test-required packages, constrained by the runtime lock, with CPU PyTorch
wheels, then runs:

```sh
python -m unittest discover -s dataload/embeddings/tests -v
```

The tests exercise both CLI GPU branches and server constructor calls with model
loading mocked, plus the real library's rejection of a local custom module with
an inert import marker. No GPU, model weight download, or API key is needed.

References: [Sentence Transformers advisory](https://github.com/advisories/GHSA-jhr6-gm9c-rqjv),
[version 6 trust enforcement](https://github.com/huggingface/sentence-transformers/pull/3935),
[AnyIO advisory](https://github.com/agronholm/anyio/security/advisories/GHSA-82r6-8w77-94w6),
[Debian Perl security fix](https://security-tracker.debian.org/tracker/CVE-2026-13221).
