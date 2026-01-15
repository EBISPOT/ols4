# Link

This tool performs Pass 2 of the linking process. It takes a manifest (from create-manifest) and a single ontology JSON file, then adds linking metadata to the ontology.

## Purpose

The link tool:
- Takes a pre-computed manifest from Pass 1
- Takes a single ontology JSON file (which can contain multiple ontologies)
- Adds linking information to entities and ontology metadata
- Outputs a linked ontology JSON file

## Building

```bash
cd dataload/linker/link
mvn clean package
```

This creates `target/link-1.0-SNAPSHOT.jar`

## Usage

```bash
java -jar target/link-1.0-SNAPSHOT.jar \
  --manifest manifest.json \
  --input unlinked-ontology.json \
  --output linked-ontology.json \
  [--leveldbPath /path/to/leveldb]
```

### Options

- `--manifest`: Input manifest JSON file from create-manifest (required)
- `--input`: Unlinked ontology JSON input filename (required)
- `--output`: Linked ontology JSON output filename (required)
- `--leveldbPath`: Optional path to LevelDB containing extra mappings (for ORCID etc.)

## Example

Link a single ontology using a pre-computed manifest:

```bash
java -jar target/link-1.0-SNAPSHOT.jar \
  --manifest /data/manifest.json \
  --input /data/efo-unlinked.json \
  --output /data/efo-linked.json
```

With LevelDB for additional mappings:

```bash
java -jar target/link-1.0-SNAPSHOT.jar \
  --manifest /data/manifest.json \
  --input /data/efo-unlinked.json \
  --output /data/efo-linked.json \
  --leveldbPath /data/orcid.leveldb
```

## Output

The linked ontology JSON file contains all the original ontology data plus:
- `importsFrom`: List of ontology IDs this ontology imports from
- `exportsTo`: List of ontology IDs that import from this ontology
- `linkedEntities`: Metadata about linked entities (labels, types, defining ontologies)
- `linksTo`: List of entity IRIs this ontology links to
- `isDefiningOntology`: Boolean indicating if this is the defining ontology for an entity
- `definedBy`: List of ontology IDs that define an entity
- `appearsIn`: List of ontology IDs where an entity appears

## Two-Step Workflow

The typical workflow is:

1. **Create manifest once** from all ontologies:
   ```bash
   java -jar create-manifest.jar --input ont1.json,ont2.json,ont3.json --output manifest.json
   ```

2. **Link each ontology** using the manifest:
   ```bash
   java -jar link.jar --manifest manifest.json --input ont1.json --output ont1-linked.json
   java -jar link.jar --manifest manifest.json --input ont2.json --output ont2-linked.json
   java -jar link.jar --manifest manifest.json --input ont3.json --output ont3-linked.json
   ```

This approach is more efficient when linking many ontologies, as Pass 1 only needs to run once.
