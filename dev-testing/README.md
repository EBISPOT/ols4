# Dev Testing

## Running testcases without Docker

`dev-testing/run-testcases-local.sh` runs the full OLS4 test suite (mock dataload and/or API tests) without Docker.

```bash
./dev-testing/run-testcases-local.sh [dataload|api|all]
```

| Mode | What it does |
|---|---|
| `dataload` | Part 1 only: mock dataload test (mirrors `test_dataload.sh`) |
| `api` | Part 2 only: full API test against a live local backend (mirrors `test_api.sh`) |
| `all` | Both tests (default) |

The script:
1. Builds all Maven JARs (`ols-shared`, `rdf2json`, `solr_config_builder`, `backend`, `apitester4`) and Rust binaries
2. **Part 1** — For each of the 110+ testcase configs, runs the data pipeline and diffs output against `testcases_expected_output/`
3. **Part 2** — Merges all testcase configs, runs the full pipeline, loads into local Solr + Neo4j, starts the backend, runs `apitester4.jar` against `http://localhost:8080`, diffs against `testcases_expected_output_api/`

For `api` and `all` modes, the same prerequisites as `dev-local.sh` apply (NEO4J_HOME, SOLR_HOME).

To update expected output after intentional changes:
```bash
# Mock dataload
./dev-testing/run-testcases-local.sh dataload
rm -rf testcases_expected_output/*
cp -r testcases_output/* testcases_expected_output/

# API test
./dev-testing/run-testcases-local.sh api
rm -rf testcases_expected_output_api
mv testcases_output_api testcases_expected_output_api
```

---

## Running OLS4 locally without Docker

`dev-testing/dev-local.sh` loads one ontology config into local Solr and Neo4j instances, leaving both running so you can start the backend and frontend against real data.

### Prerequisites

| Requirement | Notes |
|---|---|
| **Java 21** | Required by Solr 9.x and Neo4j 2025.x |
| **Maven** (`mvn`) | Used to build the Java pipeline JARs |
| **Rust + Cargo** | Install via [rustup.rs](https://rustup.rs) |
| **Python 3** with `requests` | For Neo4j index creation |
| **Neo4j** (local install) | Set `NEO4J_HOME` to the install directory |
| **Solr** (local install) | Set `SOLR_HOME` to the install directory |

On macOS, Neo4j and Solr can be installed via Homebrew:
```bash
brew install neo4j solr
```

Set the environment variables before running (add to your shell profile to make permanent):
```bash
export NEO4J_HOME=/opt/homebrew/opt/neo4j/libexec   # adjust to your install path
export SOLR_HOME=/opt/homebrew/opt/solr/libexec      # adjust to your install path
```

### Usage

```bash
./dev-testing/dev-local.sh <ontology_config.json> [out_dir]
```

| Argument | Required | Default | Description |
|---|---|---|---|
| `ontology_config.json` | Yes | — | Path to a single ontology config (e.g. `dataload/configs/efo.json`) |
| `out_dir` | No | `dev-local-out/` | Output directory — cleaned on every run |

Example:
```bash
./dev-testing/dev-local.sh dataload/configs/duo.json
```

The script will:
1. Build all required Maven JARs (`ols-shared`, `rdf2json`, `solr_config_builder`)
2. Build the Rust data pipeline binaries (`cargo build --release`)
3. Run the full data pipeline for the given ontology
4. Start Solr on `localhost:8983` with a generated schema
5. Load and commit all Solr data
6. Import CSVs into Neo4j and start it on `bolt://localhost:7687`
7. Create Neo4j indexes

### After the script completes

Start the backend:
```bash
cd backend && mvn spring-boot:run
```

Start the frontend:
```bash
cd frontend && npm start
```

The API will be available at `http://localhost:8080` and the UI at `http://localhost:3000`.

### Notes

- Only one ontology config per run. To load multiple ontologies, merge them first with `dataload/merge_configs`.
- The output directory is wiped on every run — don't store anything there.
- Vector/embedding indexes are not created by this script. Add `--embeddingParquets` manually to `ols_json2neo` if needed.