#!/usr/bin/env python3
"""
Populate an existing (managed) PostgreSQL database with OLS4 data.

Uses COPY ... WITH (FORMAT binary, FREEZE) by running CREATE TABLE and all
COPY commands for each table in a single transaction.  FREEZE marks rows as
already frozen, skipping future VACUUM passes.  The two table types
(entities, embedding_nodes) are loaded sequentially.

Connects using standard libpq environment variables (PGHOST, PGDATABASE, etc.).
The actual COPY is done by piping psql scripts to `psql` subprocesses (no
Python DB driver needed).

Usage:
    python populate_external_postgres.py <datadir> [--parallel-workers N] [--filter-property <name> ...] [--artifacts-dir <path>] [parquet_file ...]
"""

import os
import subprocess
import sys

sys.stdout.reconfigure(line_buffering=True)
import time
from pathlib import Path


# ---------------------------------------------------------------------------
# Column lists (must match create_postgres_schema.py column order)
# ---------------------------------------------------------------------------

BASE_ENTITY_COLS = [
    "id", "type", "iri", "ontology_id", "_json", "is_obsolete",
    "label", "direct_parents", "hierarchical_parents",
    "direct_ancestors", "hierarchical_ancestors",
    "search_type", "short_form", "curie", "obo_id",
    "synonym", "definition",
    "is_defining_ontology", "has_direct_parents", "has_hierarchical_parents",
    "has_direct_children", "has_hierarchical_children", "is_preferred_root",
    "ontology_iri", "ontology_preferred_prefix",
    "subset", "related_to", "curated_from_sources",
    "label_for_suggest",
]

EMB_NODE_BASE_COLS = ["id", "type", "entity_id"]


# ---------------------------------------------------------------------------
# Schema generation (calls create_postgres_schema.py, parses sections)
# ---------------------------------------------------------------------------

def generate_schema(script_dir: Path, extra_args: list[str]) -> dict[str, str]:
    """Run create_postgres_schema.py and return a dict of section_name -> SQL."""
    cmd = [sys.executable, str(script_dir / "create_postgres_schema.py")] + extra_args
    result = subprocess.run(cmd, capture_output=True, text=True, check=True)

    sections: dict[str, str] = {}
    current = None
    lines: list[str] = []

    for line in result.stdout.splitlines():
        if line.startswith("-- SECTION: "):
            if current is not None:
                sections[current] = "\n".join(lines)
            current = line.split("-- SECTION: ", 1)[1].strip()
            lines = []
        else:
            lines.append(line)

    if current is not None:
        sections[current] = "\n".join(lines)

    return sections


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def run_psql(script: str, label: str = ""):
    """Pipe a SQL/psql script to psql -v ON_ERROR_STOP=1."""
    proc = subprocess.run(
        ["psql", "-v", "ON_ERROR_STOP=1"],
        input=script, text=True,
        capture_output=True,
    )
    if proc.returncode != 0:
        msg = proc.stderr.strip() or proc.stdout.strip()
        raise RuntimeError(f"psql failed ({label}): {msg}")
    if label:
        print(f"  {label} done.")


def sizeof_fmt(num: float) -> str:
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if abs(num) < 1024:
            return f"{num:.1f}{unit}"
        num /= 1024
    return f"{num:.1f}PB"


# ---------------------------------------------------------------------------
# Per-table loader: CREATE + \copy FREEZE in one psql transaction
# ---------------------------------------------------------------------------

def load_table(
    table_name: str,
    create_sql: str,
    cols: list[str],
    pgbin_files: list[Path],
) -> None:
    """Load a single table: BEGIN, CREATE TABLE, \\copy FREEZE all files, COMMIT."""
    if not pgbin_files:
        print(f"  {table_name}: no files, skipping.")
        return

    total_bytes = sum(f.stat().st_size for f in pgbin_files)
    print(f"  {table_name}: {len(pgbin_files)} files, {sizeof_fmt(total_bytes)} total")

    t0 = time.time()
    col_list = ", ".join(cols)

    # Build a psql script: single transaction with CREATE + \copy FREEZE
    lines = ["BEGIN;", create_sql]
    for pgbin in pgbin_files:
        abs_path = str(pgbin.resolve())
        lines.append(
            f"\\copy {table_name} ({col_list}) FROM '{abs_path}' WITH (FORMAT binary, FREEZE)"
        )
    lines.append("COMMIT;")

    script = "\n".join(lines)

    # Log progress (we can't get per-file progress from psql, but log what we're doing)
    for i, pgbin in enumerate(pgbin_files, 1):
        size = sizeof_fmt(pgbin.stat().st_size)
        print(f"    [{i}/{len(pgbin_files)}] {pgbin.name} ({size})")

    proc = subprocess.run(
        ["psql", "-v", "ON_ERROR_STOP=1"],
        input=script, text=True,
        capture_output=True,
    )
    if proc.returncode != 0:
        raise RuntimeError(f"{table_name} load failed: {proc.stderr.strip() or proc.stdout.strip()}")

    elapsed = time.time() - t0
    rate = total_bytes / elapsed / 1024 / 1024 if elapsed > 0 else 0
    print(f"  {table_name}: committed in {elapsed:.1f}s ({rate:.1f} MB/s)")


# ---------------------------------------------------------------------------
# Artifact upload (PCA models + text tagger binary)
# ---------------------------------------------------------------------------

def upload_artifacts(artifacts_path: Path, sections: dict) -> None:
    """Upload PCA model JSONs and text_tagger_db.bin to postgres via psql."""
    print("=== Uploading artifacts ===")

    # Create the artifact tables (idempotent)
    run_psql(sections.get("ols_pca_models", ""), "ols_pca_models table")
    run_psql(sections.get("ols_text_tagger", ""), "ols_text_tagger table")

    pca_files = sorted(artifacts_path.glob("*_pca*.json"))
    for pca_file in pca_files:
        if "_pca16" in pca_file.name:
            continue
        name = pca_file.stem
        size = sizeof_fmt(pca_file.stat().st_size)
        print(f"  Uploading PCA model: {name} ({size})")
        # Use \lo_import + INSERT via bytea hex encoding
        data_hex = pca_file.read_bytes().hex()
        sql = (
            f"DELETE FROM ols_pca_models WHERE name = '{name}';\n"
            f"INSERT INTO ols_pca_models (name, model) VALUES ('{name}', '\\x{data_hex}');\n"
        )
        run_psql(sql, f"pca:{name}")

    tagger_path = artifacts_path / "text_tagger_db.bin.gz"
    if tagger_path.exists():
        size = sizeof_fmt(tagger_path.stat().st_size)
        print(f"  Uploading text_tagger_db.bin.gz ({size}) as Large Object")
        # Clean up old large objects, then use \lo_import
        sql = (
            "SELECT lo_unlink(tagger_db_oid) FROM ols_text_tagger;\n"
            "DELETE FROM ols_text_tagger;\n"
            f"\\lo_import '{tagger_path}'\n"
            "INSERT INTO ols_text_tagger (tagger_db_oid) VALUES (:LASTOID);\n"
        )
        run_psql(sql, "text_tagger")
    else:
        print("  text_tagger_db.bin.gz not found, skipping")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <datadir> [--filter-property <name> ...] [parquet_file ...]", file=sys.stderr)
        sys.exit(1)

    datadir = Path(sys.argv[1]).resolve()
    script_dir = Path(__file__).resolve().parent

    # Parse remaining args: --parallel-workers, --filter-property flags, and parquet files
    filter_properties = []
    parquets_raw = []
    parallel_workers = 0
    maintenance_work_mem = ""
    artifacts_dir = ""
    args_rest = sys.argv[2:]
    i = 0
    while i < len(args_rest):
        if args_rest[i] == "--parallel-workers" and i + 1 < len(args_rest):
            parallel_workers = int(args_rest[i + 1])
            i += 2
        elif args_rest[i] == "--maintenance-work-mem" and i + 1 < len(args_rest):
            maintenance_work_mem = args_rest[i + 1]
            i += 2
        elif args_rest[i] == "--filter-property" and i + 1 < len(args_rest):
            filter_properties.append(args_rest[i + 1])
            i += 2
        elif args_rest[i] == "--artifacts-dir" and i + 1 < len(args_rest):
            artifacts_dir = args_rest[i + 1]
            i += 2
        elif args_rest[i].endswith(".parquet"):
            parquets_raw.append(args_rest[i])
            i += 1
        else:
            i += 1

    # --- Validate env ---
    for var in ("PGHOST", "PGDATABASE", "PGUSER"):
        if not os.environ.get(var):
            sys.exit(f"ERROR: {var} must be set")

    pghost = os.environ["PGHOST"]
    pgport = os.environ.get("PGPORT", "5432")
    pgdb = os.environ["PGDATABASE"]
    pguser = os.environ["PGUSER"]

    print(f"=== Connecting to {pguser}@{pghost}:{pgport}/{pgdb} ===")
    try:
        run_psql("SELECT 1;")
    except Exception as e:
        sys.exit(f"ERROR: Cannot connect: {e}")
    print("Connection OK.")

    # --- Generate schema ---
    print("=== Generating schema ===")
    schema_extra = []
    for fp in filter_properties:
        schema_extra += ["--filter-property", fp]
    # Sort parquets alphabetically (must match json2postgres column order)
    parquets = sorted(parquets_raw)
    schema_extra += parquets

    sections = generate_schema(script_dir, schema_extra)

    # --- Drop old tables ---
    print("=== Dropping existing OLS tables ===")
    run_psql(
        "DROP TABLE IF EXISTS ols_embedding_nodes CASCADE;\n"
        "DROP TABLE IF EXISTS ols_entities CASCADE;\n"
        "DROP TABLE IF EXISTS ols_autosuggest CASCADE;",
        "drop"
    )

    # --- Create extensions ---
    print("=== Creating extensions ===")
    run_psql(sections["extensions"], "extensions")

    # --- Discover pgbin files ---
    entity_files = sorted(datadir.glob("*_entities.pgbin"))
    emb_files = sorted(datadir.glob("*_embedding_nodes.pgbin"))
    suggest_files = sorted(datadir.glob("*_autosuggest.pgbin"))

    # Filter out empty files
    entity_files = [f for f in entity_files if f.stat().st_size > 0]
    emb_files = [f for f in emb_files if f.stat().st_size > 0]
    suggest_files = [f for f in suggest_files if f.stat().st_size > 0]

    # --- Build column lists ---
    entity_cols = list(BASE_ENTITY_COLS)
    for fp in filter_properties:
        entity_cols.append(f'"filter_{fp}"')

    emb_node_cols = list(EMB_NODE_BASE_COLS)
    for pq_file in parquets:
        model = Path(pq_file).stem
        entity_cols.append(f'"embeddings_{model}"')
        emb_node_cols.append(f'"embedding_{model}"')

    # --- Load tables sequentially ---
    print("=== Bulk loading with COPY FREEZE ===")
    t0 = time.time()

    load_table("ols_entities", sections["ols_entities"], entity_cols, entity_files)
    load_table("ols_embedding_nodes", sections["ols_embedding_nodes"], emb_node_cols, emb_files)
    load_table("ols_autosuggest", sections["ols_autosuggest"], ["ontology_id", "string"], suggest_files)

    elapsed = time.time() - t0
    print(f"All tables loaded in {elapsed:.1f}s")

    # --- Create indexes ---
    print("=== Creating indexes ===")
    tables = ["ols_entities", "ols_embedding_nodes", "ols_autosuggest"]
    if parallel_workers > 0:
        print(f"  Setting parallel_workers={parallel_workers} on all tables")
        for tbl in tables:
            run_psql(f"ALTER TABLE {tbl} SET (parallel_workers = {parallel_workers});")

    # Session-level SET commands to prepend to each index build
    set_prefix = ""
    if maintenance_work_mem:
        set_prefix += f"SET maintenance_work_mem = '{maintenance_work_mem}';\n"
        print(f"  Setting maintenance_work_mem={maintenance_work_mem} per session")
    if parallel_workers > 0:
        set_prefix += f"SET max_parallel_maintenance_workers = {parallel_workers};\n"
        print(f"  Setting max_parallel_maintenance_workers={parallel_workers} per session")

    index_stmts = [
        stmt.strip() for stmt in sections["indexes"].split(";")
        if stmt.strip() and not stmt.strip().startswith("--")
    ]
    total = len(index_stmts)
    for idx_i, stmt in enumerate(index_stmts, 1):
        short = stmt.split("(")[0].strip() if "(" in stmt else stmt.strip()
        print(f"  [{idx_i}/{total}] {short} ...", flush=True)
        t_idx = time.time()
        run_psql(set_prefix + stmt + ";")
        print(f"  [{idx_i}/{total}] done ({time.time() - t_idx:.1f}s)")

    if parallel_workers > 0:
        for tbl in tables:
            run_psql(f"ALTER TABLE {tbl} RESET (parallel_workers);")

    # --- Post-load (ANALYZE) ---
    print("=== Post-load updates ===")
    run_psql(sections["post_load"], "post_load")

    # --- Upload artifacts (PCA models + text tagger db) ---
    if artifacts_dir:
        upload_artifacts(Path(artifacts_dir).resolve(), sections)

    print(f"=== Done ===")
    print(f"Database {pgdb} on {pghost} has been populated.")
    print(f"  OLS_POSTGRES_HOST={pghost}")
    print(f"  OLS_POSTGRES_PORT={pgport}")
    print(f"  OLS_POSTGRES_DB={pgdb}")
    print(f"  OLS_POSTGRES_USER={pguser}")


if __name__ == "__main__":
    main()
