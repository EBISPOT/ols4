FROM rust:1.90.0-bullseye AS rust-builder

COPY Cargo.toml Cargo.lock /opt/
COPY ols_semsim /opt/ols_semsim/
WORKDIR /opt
RUN cargo build --release

FROM python:3.13-slim-bookworm AS python-builder

WORKDIR /opt/ols_embed
RUN pip install --no-cache-dir uv
COPY ols_embed/pyproject.toml ols_embed/uv.lock ./
RUN uv sync --frozen --extra gpu --no-install-project

FROM python:3.13-slim-bookworm

# Retain the runtime tools used by the Nextflow and numerical workloads.
RUN apt-get update -y && \
    apt-get install -y --no-install-recommends bash libgomp1 pigz && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /opt/ols_embed
ENV PATH="/opt/ols_embed/.venv/bin:$PATH"
ENV MODELS_DIR="/data/models"

COPY --from=python-builder /opt/ols_embed/.venv /opt/ols_embed/.venv
COPY --from=rust-builder /opt/target/release/ols_semsim /usr/local/bin/ols_semsim
COPY ols_embed/embed2.py ols_embed/embed_openai.py ols_embed/pca.py \
    ols_embed/avg_embeddings.py ols_embed/server.py \
    ols_embed/visualize_embeddings.py ols_embed/convert_umap_to_web.py ./

EXPOSE 5000

CMD ["gunicorn", "--bind=0.0.0.0:5000", "--workers=1", "--threads=4", "--timeout=300", "--worker-class=sync", "--access-logfile=-", "--error-logfile=-", "server:app"]
