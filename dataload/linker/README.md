# Linker

The linker has been split into two separate Maven projects for improved modularity and parallelization:

## Projects

### 1. create_manifest
**Location:** `linker/create_manifest/`

Performs Pass 1 of the linking process - scans multiple ontology JSON files and creates a manifest containing all linking metadata.

See [create_manifest/README.md](create_manifest/README.md) for details.

### 2. link
**Location:** `linker/link/`

Performs Pass 2 of the linking process - takes a manifest and a single ontology JSON file, adds linking metadata to the ontology.

See [link/README.md](link/README.md) for details.

## Workflow

The typical two-step workflow is:

1. **Create manifest once** from all ontologies:
   ```bash
   java -jar create_manifest/target/create-manifest-1.0-SNAPSHOT.jar \
     --input ont1.json,ont2.json,ont3.json \
     --output manifest.json
   ```

2. **Link each ontology** individually using the manifest:
   ```bash
   java -jar link/target/link-1.0-SNAPSHOT.jar \
     --manifest manifest.json \
     --input ont1.json \
     --output ont1-linked.json
   ```

This approach allows for parallel execution of step 2 across multiple ontologies, improving performance when processing large numbers of ontologies.

## Building

Build both projects from the dataload directory:

```bash
cd dataload
mvn clean package
```

Or build individually:

```bash
cd linker/create_manifest
mvn clean package

cd ../link
mvn clean package
```
