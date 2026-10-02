"""Restart the production loader's output and query the surviving entities."""
import json
import os
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, "/opt/ols/dataload")
from load_into_postgres import build_runtime_user_env, find_pg_bin

root = Path(sys.argv[1]).resolve()
data = root / "data"
env = build_runtime_user_env(os.environ, root)
env.update(PGHOST="/tmp", PGPORT="5432", PGDATABASE="ols4", PGUSER="postgres")
pg_ctl = str(find_pg_bin() / "pg_ctl")


def command(*args):
    return subprocess.run(args, env=env, check=True, capture_output=True, text=True, timeout=30)


command(pg_ctl, "-D", str(data), "-l", str(root / "verify.log"), "start", "-w")
try:
    query = "SELECT json_agg(json_build_object('ontology',ontology_id,'type',type,'iri',iri) ORDER BY ontology_id,type,iri) FROM ols_entities"
    result = command("psql", "-XAt", "-v", "ON_ERROR_STOP=1", "-c", query)
    Path("loaded.json").write_text(json.dumps(json.loads(result.stdout)))
finally:
    command(pg_ctl, "-D", str(data), "stop", "-w")
