"""Run real production Nextflow processes with baseline and mixed outcomes."""
import argparse
import csv
import json
import os
from pathlib import Path
import subprocess
import tempfile

REPO = Path(__file__).resolve().parents[2]
FIXTURE = REPO / "dataload/tests/nextflow"


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--image", required=True, help="Dataload image built from the checkout under test")
    parser.add_argument("--controller-image", help="Optional Docker Nextflow controller for local runs")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="ols-nextflow-isolation-") as temp:
        base = Path(temp).resolve()
        ontologies = []
        for name in ["a", "b"]:
            ttl = base / f"{name}.ttl"
            ttl.write_text(f"""@prefix owl: <http://www.w3.org/2002/07/owl#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
<https://example.test/{name}> a owl:Ontology .
<https://example.test/{name}#Root> a owl:Class ; rdfs:label "Root {name}"@en .
""")
            ontologies.append({"id": name, "ontology_purl": str(ttl), "base_uri": [f"https://example.test/{name}#"]})
        ontologies += [{"id": "broken", "ontology_purl": str(base / "missing.ttl")},
                       {"id": "crash", "ontology_purl": str(base / "a.ttl")}]
        config = base / "config.json"
        config.write_text(json.dumps({"ontologies": ontologies}))
        no_file = base / "NO_FILE"
        no_file.touch()
        env = {**os.environ, "OLS_HOME": str(base), "OLS_OUT_DIR": str(base), "OLS_EMBEDDINGS_PATH": str(base), "OLS_EMBEDDINGS_CONFIG": str(config), "OLS_EMBEDDINGS_PREV": str(base), "NXF_ANSI_LOG": "false"}
        outputs = {}
        for mode, ids in [("baseline", "a,b"), ("mixed", "a,b,broken,crash")]:
            run_dir = base / mode
            run_dir.mkdir()
            results = run_dir / "results"
            command = ["nextflow"]
            if args.controller_image:
                command = ["docker", "run", "--rm", "--entrypoint", "nextflow",
                           "-v", f"{REPO}:{REPO}:ro", "-v", f"{base}:{base}",
                           "-v", "/var/run/docker.sock:/var/run/docker.sock", "-w", str(run_dir)]
                for key in ["OLS_HOME", "OLS_OUT_DIR", "OLS_EMBEDDINGS_PATH", "OLS_EMBEDDINGS_CONFIG", "OLS_EMBEDDINGS_PREV", "NXF_ANSI_LOG"]:
                    command += ["-e", f"{key}={env[key]}"]
                command += [args.controller_image]
            command += ["run", str(FIXTURE / "isolation.nf"), "-c", str(FIXTURE / "isolation.config"),
                        "--fixture_config", str(config), "--ids", ids, "--no_file", str(no_file),
                        "--results", str(results), "--test_image", args.image, "-with-trace", str(run_dir / "trace.tsv")]
            proc = subprocess.run(command, cwd=run_dir, env=env, text=True, capture_output=True, timeout=240)
            check(proc.returncode == 0, f"{mode} workflow failed:\n{proc.stdout}\n{proc.stderr}")
            print(proc.stdout)
            with (run_dir / "trace.tsv").open() as trace_file:
                trace = list(csv.DictReader(trace_file, delimiter="\t"))
            parsed = [r for r in trace if r["name"].startswith("rdf2json")]
            check(len(parsed) == len(ids.split(',')), f"wrong RDF2JSON task count: {trace}")
            check(sum(r["status"] == "FAILED" and r["exit"] == "42" for r in parsed) == (mode == "mixed"), "hard failure not isolated")
            for name in ["a", "b"]:
                status = json.loads((results / "json" / f"{name}.status.json").read_text())
                check(status["status"] == "SUCCESS", f"{name} did not succeed: {status}")
                data = json.loads((results / "json" / f"{name}.json").read_text())
                check(data["ontologies"][0]["classes"][0]["iri"] == f"https://example.test/{name}#Root", f"wrong successful output: {data}")
            if mode == "mixed":
                broken = json.loads((results / "json/broken.status.json").read_text())
                check(broken["status"] == "FAILED_NO_FALLBACK", f"wrong semantic failure: {broken}")
                check(json.loads((results / "json/broken.json").read_text())["ontologies"] == [], "failed ontology leaked entities")
                check(not (results / "json/crash.json").exists(), "hard failed task published JSON")
                check(not list((results / "binary").glob("broken_*.pgbin")), "semantic failure produced COPY files")
                check(not list((results / "binary").glob("crash_*.pgbin")), "hard failure reached converter")
            outputs[mode] = results
        for name in ["a", "b"]:
            check((outputs["baseline"] / "json" / f"{name}.json").read_bytes() == (outputs["mixed"] / "json" / f"{name}.json").read_bytes(), f"{name} JSON changed alongside failures")
            for suffix in ["entities", "autosuggest"]:
                file = f"{name}_{suffix}.pgbin"
                check((outputs["baseline"] / "binary" / file).read_bytes() == (outputs["mixed"] / "binary" / file).read_bytes(), f"{name} binary output changed alongside failures")
        print("PASS: successful ontology JSON and COPY files are unchanged alongside semantic and process failures")


if __name__ == "__main__":
    main()
