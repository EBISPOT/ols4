#!/usr/bin/env python3
"""
Initialize a local PostgreSQL instance, bulk-load OLS4 data using COPY FREEZE,
create indexes, and package the result as postgres.tgz.

This replaces load_into_postgres.sh.  The local PostgreSQL is configured with
WAL disabled for maximum bulk-load speed.  COPY FREEZE is used so that rows
are pre-frozen and skip future VACUUM passes.

Usage:
    python load_into_postgres.py <output_dir> <datadir> [--parallel-workers N] [--filter-property <name> ...] [--artifacts-dir <path>] [parquet_file ...]
"""

import os
import shutil
import subprocess
import sys
import time
import ctypes.util
import grp
import pwd
from pathlib import Path
from typing import Optional


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


def find_nss_wrapper_library() -> Optional[str]:
    """Return a usable libnss_wrapper path/soname if available."""
    lib = ctypes.util.find_library("nss_wrapper")
    if lib:
        return lib

    for candidate in (
        "/usr/lib/x86_64-linux-gnu/libnss_wrapper.so",
        "/usr/lib/aarch64-linux-gnu/libnss_wrapper.so",
        "/usr/lib64/libnss_wrapper.so",
        "/usr/lib/libnss_wrapper.so",
    ):
        if Path(candidate).exists():
            return candidate

    return None


def build_runtime_user_env(base_env: dict[str, str], work_dir: Path) -> dict[str, str]:
    """Ensure subprocesses can resolve the current UID/GID even in uid-mapped containers."""
    env = dict(base_env)
    uid = os.geteuid()
    gid = os.getegid()

    try:
        pwd.getpwuid(uid)
        user_missing = False
    except KeyError:
        user_missing = True

    try:
        grp.getgrgid(gid)
        group_missing = False
    except KeyError:
        group_missing = True

    if not user_missing and not group_missing:
        return env

    lib = find_nss_wrapper_library()
    if not lib:
        raise RuntimeError(
            f"Current UID:GID {uid}:{gid} is not present in passwd/group, and libnss_wrapper is unavailable"
        )

    wrapper_dir = work_dir / ".nss-wrapper"
    runtime_home = wrapper_dir / "home"
    wrapper_dir.mkdir(parents=True, exist_ok=True)
    runtime_home.mkdir(parents=True, exist_ok=True)

    passwd_path = wrapper_dir / "passwd"
    group_path = wrapper_dir / "group"

    passwd_contents = Path("/etc/passwd").read_text() if Path("/etc/passwd").exists() else ""
    group_contents = Path("/etc/group").read_text() if Path("/etc/group").exists() else ""

    if passwd_contents and not passwd_contents.endswith("\n"):
        passwd_contents += "\n"
    if group_contents and not group_contents.endswith("\n"):
        group_contents += "\n"

    if group_missing:
        group_name = env.get("GROUP") or f"gid{gid}"
        group_contents += f"{group_name}:x:{gid}:\n"

    if user_missing:
        username = env.get("USER") or env.get("LOGNAME") or f"uid{uid}"
        shell = env.get("SHELL") or "/bin/sh"
        passwd_contents += f"{username}:x:{uid}:{gid}:OLS runtime user:{runtime_home}:{shell}\n"
        env["USER"] = username
        env["LOGNAME"] = username

    passwd_path.write_text(passwd_contents)
    group_path.write_text(group_contents)

    existing_preload = env.get("LD_PRELOAD", "")
    preload_parts = [part for part in existing_preload.split(":") if part]
    if lib not in preload_parts:
        preload_parts.insert(0, lib)

    env["LD_PRELOAD"] = ":".join(preload_parts)
    env["NSS_WRAPPER_PASSWD"] = str(passwd_path)
    env["NSS_WRAPPER_GROUP"] = str(group_path)
    env["HOME"] = str(runtime_home)

    print(f"Using nss_wrapper for runtime UID:GID {uid}:{gid}")
    return env


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
# Artifact upload (PCA models + text tagger binary)
# ---------------------------------------------------------------------------

def upload_artifacts(artifacts_path: Path, sections: dict, env: dict) -> None:
    """Upload PCA model JSONs and text_tagger_db.bin into the local postgres."""
    print("=== Uploading artifacts ===")

    run_psql(sections.get("ols_pca_models", ""), "ols_pca_models table", env=env)
    run_psql(sections.get("ols_text_tagger", ""), "ols_text_tagger table", env=env)

    pca_files = sorted(artifacts_path.glob("*_pca*.json"))
    for pca_file in pca_files:
        if "_pca16" in pca_file.name:
            continue
        name = pca_file.stem
        size = sizeof_fmt(pca_file.stat().st_size)
        print(f"  Uploading PCA model: {name} ({size})")
        data_hex = pca_file.read_bytes().hex()
        sql = (
            f"DELETE FROM ols_pca_models WHERE name = '{name}';\n"
            f"INSERT INTO ols_pca_models (name, model) VALUES ('{name}', '\\x{data_hex}');\n"
        )
        run_psql(sql, f"pca:{name}", env=env)

    tagger_path = artifacts_path / "text_tagger_db.bin.gz"
    if tagger_path.exists():
        size = sizeof_fmt(tagger_path.stat().st_size)
        print(f"  Uploading text_tagger_db.bin.gz ({size}) as Large Object")
        sql = (
            "DELETE FROM ols_text_tagger;\n"
            f"\\lo_import '{tagger_path}'\n"
            "INSERT INTO ols_text_tagger (tagger_db_oid) VALUES (:LASTOID);\n"
        )
        run_psql(sql, "text_tagger", env=env)
    else:
        print("  text_tagger_db.bin.gz not found, skipping")


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
    centroid_parquets_raw: list[str] = []
    parallel_workers = 0
    maintenance_work_mem = ""
    artifacts_dir = ""
    args_rest = sys.argv[3:]
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
        elif args_rest[i] == "--descendants-centroid-parquet" and i + 1 < len(args_rest):
            centroid_parquets_raw.append(args_rest[i + 1])
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

    runtime_env = build_runtime_user_env(os.environ, output_dir)

    # Environment for all psql/pg_ctl calls
    pg_env = {**runtime_env,
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
        env=pg_env,
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
        centroid_parquets = sorted(centroid_parquets_raw)
        for pq in centroid_parquets:
            schema_extra += ["--descendants-centroid-parquet", pq]

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

        # descendants_centroid columns go to ols_entities only (not ols_embedding_nodes)
        for pq_file in centroid_parquets:
            stem = Path(pq_file).stem
            model_name = stem.removesuffix("_descendants_centroid")
            entity_cols.append(f'"descendants_centroid_{model_name}"')

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
        tables = ["ols_entities", "ols_embedding_nodes", "ols_autosuggest"]
        if parallel_workers > 0:
            print(f"  Setting parallel_workers={parallel_workers} on all tables")
            for tbl in tables:
                run_psql(f"ALTER TABLE {tbl} SET (parallel_workers = {parallel_workers});", env=pg_env)

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
            run_psql(set_prefix + stmt + ";", env=pg_env)
            print(f"  [{idx_i}/{total}] done ({time.time() - t_idx:.1f}s)")

        if parallel_workers > 0:
            for tbl in tables:
                run_psql(f"ALTER TABLE {tbl} RESET (parallel_workers);", env=pg_env)

        # --- Upload artifacts (PCA models + text tagger) ---
        if artifacts_dir:
            upload_artifacts(Path(artifacts_dir).resolve(), sections, pg_env)

        # --- Post-load (ANALYZE) ---
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
