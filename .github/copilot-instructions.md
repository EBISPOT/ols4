# OLS4 (Ontology Lookup Service)

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

## Working Effectively

### Bootstrap and Build
- Install Java 17 and Maven 3.x (required for all Java components)
- Install Node.js 20+ and npm for frontend development
- **CRITICAL**: Build all Maven projects first:
  - `mvn clean package` -- takes 1-2 minutes. NEVER CANCEL. Set timeout to 180+ seconds.
- Build frontend:
  - `cd frontend && npm install` -- takes 30 seconds. NEVER CANCEL. Set timeout to 120+ seconds.
  - `cd frontend && REACT_APP_ENV=dev npm run build` -- takes 2-3 seconds.

### Testing and Validation
- **CRITICAL**: Run local dataload tests to validate changes:
  - `mkdir -p /tmp/test_output && dataload/create_datafiles.sh ./testcases/owl2-primer/minimal.json /tmp/test_output --loadLocalFiles --noDates` -- takes 2-3 seconds.
- **DO NOT** run `test_dataload.sh` or `test_api.sh` -- these require internet access and will fail in sandboxed environments.
- **ALWAYS** use `--loadLocalFiles` flag for local testing to avoid network dependencies.

### Running Components Locally

#### Full Docker Setup (when network accessible)
- `export OLS4_CONFIG=./dataload/configs/efo.json`
- `docker compose up` -- **DATALOAD TAKES 10-45 MINUTES**. NEVER CANCEL. Set timeout to 3600+ seconds.
- Frontend available at `http://localhost:8081`
- Backend API at `http://localhost:8080`

#### Local Development (requires external Neo4j and Solr)
- Set environment variables:
  - `export NEO4J_HOME=/path/to/neo4j`
  - `export SOLR_HOME=/path/to/solr`
  - `export OLS4_HOME=/home/runner/work/ols4/ols4`
- Start external services manually, then:
  - Backend: `./dev-testing/start-backend.sh`
  - Frontend: `./dev-testing/start-frontend.sh`

### Environment Variables for Development
- `OLS_SOLR_HOST=http://localhost:8983`
- `OLS_NEO4J_HOST=bolt://localhost:7687`
- `JAVA_OPTS` -- set heap size (e.g., `-Xms5G -Xmx10G`) for large ontologies

## Validation

### Manual Testing Requirements
- **ALWAYS** build both Maven and frontend projects after making changes
- **ALWAYS** test dataload with minimal test case to verify functionality:
  - `mkdir -p /tmp/validation && dataload/create_datafiles.sh ./testcases/owl2-primer/minimal.json /tmp/validation --loadLocalFiles --noDates`
- Validate API responses if backend changes are made (requires running backend)
- Test frontend build and check for TypeScript/build errors
- **TypeScript validation**: Run `npx tsc --noEmit` in frontend/ to check for type errors (note: codebase has existing TypeScript issues)
- **NEVER** run tests requiring internet access in sandboxed environments

### Complete User Scenarios to Test
- **Dataload workflow**: Create output dir → Load test ontology → Generate CSV/JSON → Verify output files created
- **Backend API**: Start backend → Test basic endpoints like `/api/ontologies` (requires Neo4j/Solr data)
- **Frontend**: Build frontend → Verify no TypeScript errors → Check bundle generation

## Common Tasks

### Repository Structure
```
ols4/
├── dataload/          # Java ontology processing pipeline
├── backend/           # Spring Boot REST API (Java 17)
├── frontend/          # React TypeScript application
├── testcases/         # Local test ontologies
├── dev-testing/       # Development scripts
├── .github/workflows/ # CI/CD pipelines
└── docker-compose.yml # Full stack deployment
```

### Key Commands Reference
```bash
# Build everything
mvn clean package                           # 1-2 minutes
cd frontend && npm install && npm run build # 30 seconds + 2 seconds

# Test dataload (local only)
mkdir -p /tmp/out && dataload/create_datafiles.sh ./testcases/owl2-primer/minimal.json /tmp/out --loadLocalFiles --noDates

# Run development scripts (requires external Neo4j/Solr)
./dev-testing/start-backend.sh
./dev-testing/start-frontend.sh

# Docker deployment (network required)
export OLS4_CONFIG=./dataload/configs/efo.json
docker compose up  # TAKES 10-45 MINUTES
```

### CI/CD Information
- GitHub Actions: `.github/workflows/test.yml` and `.github/workflows/docker.yml`
- Tests run on Java 14 (note: local development uses Java 17)
- Docker images built for dev and stable branches
- API tests use `test_api.sh` and dataload tests use `test_dataload.sh`

### Technology Stack
- **Dataload**: Java 17, Maven, OWL API, custom annotation processors
- **Backend**: Spring Boot 3.5.5, Java 17, Neo4j driver, Solr client
- **Frontend**: React 17, TypeScript, esbuild, Material-UI, custom build system
- **Databases**: Neo4j 2025.03.0 (graph), Solr 9.8.1 (search)
- **Deployment**: Docker Compose, Kubernetes Helm charts

### File Locations
- **Main configs**: `dataload/configs/` (efo.json, small.json for testing)
- **Test ontologies**: `testcases/` (local OWL files)
- **Backend source**: `backend/src/main/java/`
- **Frontend source**: `frontend/src/`
- **Development scripts**: `dev-testing/`
- **Expected test outputs**: `testcases_expected_output/` and `testcases_expected_output_api/`

### Known Issues and Workarounds
- **Network restrictions**: Use local test files only, avoid remote ontology downloads
- **Docker build failures**: May fail due to network restrictions downloading Java JDK
- **Test failures**: Full test suite requires internet access -- use local-only tests
- **Memory requirements**: Large ontologies require heap size adjustment via JAVA_OPTS
- **Java version mismatch**: CI uses Java 14, development uses Java 17 -- both work
- **TypeScript errors**: Codebase has existing TypeScript issues (80+ errors) -- focus on new errors only

### Debugging Tips
- Check logs in dataload output for processing issues
- Use minimal test cases first (`testcases/owl2-primer/minimal.json`)
- Frontend build errors are usually TypeScript related
- Backend startup issues often relate to missing environment variables
- **ALWAYS** use `--loadLocalFiles --noDates` flags for consistent local testing