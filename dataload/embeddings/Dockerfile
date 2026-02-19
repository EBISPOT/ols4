
FROM rust:1.90.0-bullseye

RUN apt-get update -y && apt-get install -y pigz && rm -rf /var/lib/apt/lists/*

ENV PATH="/opt/ols_embed/.venv/bin:$PATH"

ENV UV_INSTALL_DIR="/usr/bin"
ENV UV_PYTHON_INSTALL_DIR="/usr/bin"
ENV UV_TOOL_DIR="/usr/bin"
ENV UV_TOOL_BIN_DIR="/usr/bin"
ENV UV_CACHE_DIR="/opt/uv_cache"
RUN curl -LsSf https://astral.sh/uv/install.sh | sh && \
        uv python install 3.13

RUN curl https://install.duckdb.org | sh && \
    cp /root/.duckdb/cli/latest/duckdb /usr/bin/


# Python
#
RUN mkdir -p /opt/ols_embed
COPY ols_embed/pyproject.toml ols_embed/uv.lock /opt/ols_embed/
RUN cd /opt/ols_embed && uv sync --frozen --extra gpu


# Rust
#
COPY Cargo.toml Cargo.lock /opt/
COPY ols_semsim /opt/ols_semsim/
WORKDIR /opt
RUN cargo build --release
RUN cp /opt/target/release/ols_semsim /usr/local/bin/


COPY ols_embed/embed2.py /opt/ols_embed/
COPY ols_embed/embed_openai.py /opt/ols_embed/
COPY ols_embed/pca.py /opt/ols_embed/
COPY ols_embed/server.py /opt/ols_embed/
COPY ols_embed/visualize_embeddings.py /opt/ols_embed/

WORKDIR /opt/ols_embed
ENV MODELS_DIR="/data/models"

EXPOSE 5000

CMD ["gunicorn", "--bind=0.0.0.0:5000", "--workers=1", "--threads=4", "--timeout=300", "--worker-class=sync", "--access-logfile=-", "--error-logfile=-", "server:app"]
