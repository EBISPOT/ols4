#!/usr/bin/env python3
"""
Initialize a local PostgreSQL instance, bulk-load OLS4 data using COPY FREEZE,
create indexes, and package the result as postgres.tgz.

This replaces load_into_postgres.sh.  The local PostgreSQL is configured with
WAL disabled for maximum bulk-load speed.  COPY FREEZE is used so that rows
are pre-frozen and skip future VACUUM passes.

Usage:
    python load_into_postgres.py <output_dir> <datadir> [--filter-property <name> ...] [parquet_file ...]
"""

import os
import shutil
import subprocess
import sys
import time
from pathlib import Path


# ---------------------------------------------------------------------------
# Column lists (must match create_postgres_schema.py / json2postgres order)
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
# PostgreSQL binary detection
# ---------------------------------------------------------------------------

def find_pg_bin() -> Path:
    """Return the directory containing PostgreSQL server binaries."""
    for name in ("initdb",):
        path = shutil.which(name)
        if path:
            return Path(path).resolve().parent
    candidate = Path("/usr/lib/postgresql/17/bin")
    if (candidate / "initdb").exists():
        return candidate
    sys.exit("ERROR: Cannot find PostgreSQL binaries (initdb)")


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

def run_cmd(args: list[str], label: str = "", **kwargs):
    """Run a command, raise on failure."""
    proc = subprocess.run(args, capture_output=True, text=True, **kwargs)
    if proc.returncode != 0:
        msg = proc.stderr.strip() or proc.stdout.strip()
        raise RuntimeError(f"{label or args[0]} failed: {msg}")
    return proc


def run_psql(script: str, label: str = "", env=None):
    """Pipe a SQL/psql script to psql -v ON_ERROR_STOP=1."""
    proc = subprocess.run(
        ["psql", "-v", "ON_ERROR_STOP=1"],
        input=script, text=True,
        capture_output=True,
        env=env,
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
# Per-table loader: CREATE + \copy FREEZE in a single transaction
# ---------------------------------------------------------------------------

def load_table(
    table_name: str,
    create_sql: str,
    cols: list[str],
    pgbin_files: list[Path],
    env: dict,
) -> None:
    """Load a single table: BEGIN; CREATE TABLE; \\copy FREEZE; COMMIT."""
    if not pgbin_files:
        print(f"  {table_name}: no files, creating empty table.")
        run_psql(create_sql, f"{table_name} (empty)", env=env)
        return

    total_bytes = sum(f.stat().st_size for f in pgbin_files)
    print(f"  {table_name}: {len(pgbin_files)} files, {sizeof_fmt(total_bytes)} total")

    t0 = time.time()
    col_list = ", ".join(cols)

    lines = ["BEGIN;", create_sql]
    for pgbin in pgbin_files:
        abs_path = str(pgbin.resolve())
        lines.append(
            f"\\copy {table_name} ({col_list}) FROM '{abs_path}' WITH (FORMAT binary, FREEZE)"
        )
    lines.append("COMMIT;")
    script = "\n".join(lines)

    for i, pgbin in enumerate(pgbin_files, 1):
        size = sizeof_fmt(pgbin.stat().st_size)
        print(f"    [{i}/{len(pgbin_files)}] {pgbin.name} ({size})")

    proc = subprocess.run(
        ["psql", "-v", "ON_ERROR_STOP=1"],
        input=script, text=True,
        capture_output=True,
        env=env,
    )
    if proc.returncode != 0:
        msg = proc.stderr.strip() or proc.stdout.strip()
        raise RuntimeError(f"{table_name} load failed: {msg}")

    elapsed = time.time() - t0
    rate = total_bytes / elapsed / 1024 / 1024 if elapsed > 0 else 0
    print(f"  {table_name}: committed in {elapsed:.1f}s ({rate:.1f} MB/s)")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    if len(sys.argv) < 3:
        print(
            f"Usage: {sys.argv[0]} <output_dir> <datadir> [--filter-property <name> ...] [parquet_file ...]",
            file=sys.stderr,
        )
        sys.exit(1)

    output_dir = Path(sys.argv[1]).resolve()
    datadir = Path(sys.argv[2]).resolve()
    script_dir = Path(__file__).resolve().parent

    # Parse remaining args
    filter_properties: list[str] = []
    parquets_raw: list[str] = []
    args_rest = sys.argv[3:]
    i = 0
    while i < len(args_rest):
        if args_rest[i] == "--filter-property" and i + 1 < len(args_rest):
            filter_properties.append(args_rest[i + 1])
            i += 2
        elif args_rest[i].endswith(".parquet"):
            parquets_raw.append(args_rest[i])
            i += 1
        else:
            i += 1

    # --- PostgreSQL binaries ---
    pgbin = find_pg_bin()
    print(f"Using PostgreSQL binaries from {pgbin}")

    pg_data = output_dir / "data"
    pg_port = "5432"
    pg_user = "postgres"
    pg_db = "ols4"

    # Environment for all psql/pg_ctl calls
    pg_env = {**os.environ,
        "PGDATA": str(pg_data),
        "PGPORT": pg_port,
        "PGUSER": pg_user,
        "PGDATABASE": pg_db,
        "PGHOST": "/tmp",
    }

    # Clean any existing data
    if pg_data.exists():
        shutil.rmtree(pg_data)

    # --- Initialize ---
    print("=== Initializing PostgreSQL ===")
    run_cmd(
        [str(pgbin / "initdb"), "-D", str(pg_data),
         "--auth=trust", f"--username={pg_user}", "--no-locale", "--encoding=UTF8"],
        label="initdb",
    )

    # Allow network connections (for Docker containers)
    with open(pg_data / "pg_hba.conf", "a") as f:
        f.write("host all all 0.0.0.0/0 trust\n")

    # Configure for bulk import
    with open(pg_data / "postgresql.conf", "a") as f:
        f.write(f"""
listen_addresses = '*'
port = {pg_port}
unix_socket_directories = '/tmp'
max_connections = 10
shared_buffers = 256MB
work_mem = 256MB
maintenance_work_mem = 1GB
wal_level = minimal
max_wal_senders = 0
fsync = off
synchronous_commit = off
full_page_writes = off
autovacuum = off
max_wal_size = 4GB
checkpoint_completion_target = 0.9
""")

    # --- Start ---
    print("=== Starting PostgreSQL ===")
    run_cmd(
        [str(pgbin / "pg_ctl"), "-D", str(pg_data), "-l", str(pg_data / "logfile"), "start", "-w"],
        label="pg_ctl start",
        env=pg_env,
    )

    try:
        # --- Create database ---
        print("=== Creating database ===")
        subprocess.run(
            [str(pgbin / "createdb"), pg_db],
            capture_output=True, text=True, env=pg_env,
        )

        # --- Generate schema ---
        print("=== Generating schema ===")
        schema_extra: list[str] = []
        for fp in filter_properties:
            schema_extra += ["--filter-property", fp]
        parquets = sorted(parquets_raw)
        schema_extra += parquets

        sections = generate_schema(script_dir, schema_extra)

        # --- Create extensions ---
        print("=== Creating extensions ===")
        run_psql(sections["extensions"], "extensions", env=pg_env)

        # --- Discover pgbin files ---
        entity_files = sorted(f for f in datadir.glob("*_entities.pgbin") if f.stat().st_size > 0)
        emb_files = sorted(f for f in datadir.glob("*_embedding_nodes.pgbin") if f.stat().st_size > 0)
        suggest_files = sorted(f for f in datadir.glob("*_autosuggest.pgbin") if f.stat().st_size > 0)

        # --- Build column lists ---
        entity_cols = list(BASE_ENTITY_COLS)
        for fp in filter_properties:
            entity_cols.append(f'"filter_{fp}"')

        emb_node_cols = list(EMB_NODE_BASE_COLS)
        for pq_file in parquets:
            model = Path(pq_file).stem
            entity_cols.append(f'"embeddings_{model}"')
            emb_node_cols.append(f'"embedding_{model}"')

        # --- Load tables with COPY FREEZE ---
        print("=== Bulk loading with COPY FREEZE ===")
        t0 = time.time()

        load_table("ols_entities", sections["ols_entities"], entity_cols, entity_files, pg_env)
        load_table("ols_embedding_nodes", sections["ols_embedding_nodes"], emb_node_cols, emb_files, pg_env)
        load_table("ols_autosuggest", sections["ols_autosuggest"], ["ontology_id", "string"], suggest_files, pg_env)

        elapsed = time.time() - t0
        print(f"All tables loaded in {elapsed:.1f}s")

        # --- Create indexes ---
        print("=== Creating indexes ===")
        run_psql(sections["indexes"], "indexes", env=pg_env)

        # --- Post-load (tsvector, ANALYZE) ---
        print("=== Post-load updates ===")
        run_psql(sections["post_load"], "post_load", env=pg_env)

    finally:
        # --- Stop ---
        print("=== Stopping PostgreSQL ===")
        subprocess.run(
            [str(pgbin / "pg_ctl"), "-D", str(pg_data), "stop", "-w"],
            capture_output=True, text=True, env=pg_env,
        )

    # --- Production config ---
    with open(pg_data / "postgresql.auto.conf", "w") as f:
        f.write("""\
wal_level = 'replica'
max_wal_senders = 10
fsync = on
synchronous_commit = on
full_page_writes = on
autovacuum = on
shared_buffers = 256MB
""")

    # --- Package ---
    print("=== Packaging PostgreSQL data ===")
    tgz_path = output_dir.parent / "postgres.tgz"
    run_cmd(
        ["tar", "-czf", str(tgz_path), "-C", str(output_dir), "data"],
        label="tar",
    )

    print("=== Done ===")


if __name__ == "__main__":
    main()
