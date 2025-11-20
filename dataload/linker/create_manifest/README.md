# Create Manifest

This tool performs Pass 1 of the linking process. It scans one or more ontology JSON files and creates a manifest containing all the metadata needed for linking.

## Purpose

The create-manifest tool:
- Scans multiple ontology JSON files (each can contain 0 or more ontologies)
- Extracts entity definitions, IRIs, ontology relationships, and base URIs
- Determines defining ontologies for each entity
- Outputs a manifest JSON file containing all this metadata

## Building

```bash
cd dataload/linker/create_manifest
mvn clean package
```

This creates `target/create-manifest-1.0-SNAPSHOT.jar`

## Usage

```bash
java -jar target/create-manifest-1.0-SNAPSHOT.jar \
  --input ontology1.json,ontology2.json,ontology3.json \
  --output manifest.json
```

### Options

- `--input`: Comma-separated list of input JSON ontology files (required)
- `--output`: Output manifest JSON filename (required)

## Example

Process multiple ontology files to create a manifest:

```bash
java -jar target/create-manifest-1.0-SNAPSHOT.jar \
  --input /data/efo.json,/data/mondo.json,/data/cl.json \
  --output /data/manifest.json
```

## Output

The manifest.json file contains:
- `iriToDefinitions`: Map of entity IRIs to their definitions across all ontologies
- `ontologyIriToOntologyIds`: Map of ontology IRIs to their IDs
- `preferredPrefixToOntologyIds`: Map of preferred prefixes to ontology IDs
- `ontologyIdToBaseUris`: Map of ontology IDs to their base URIs
- `ontologyIdToImportingOntologyIds`: Map of ontology IDs to ontologies that import from them
- `ontologyIdToImportedOntologyIds`: Map of ontology IDs to ontologies they import from

This manifest is then used by the `link` tool to perform Pass 2 linking.
