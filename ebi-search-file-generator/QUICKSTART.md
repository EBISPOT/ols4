# Quick Start Guide

## JSON to XML Converter (Recommended)

### Basic Usage

```bash
# Convert JSON file (auto-detects ontology and creates output file)
python json_to_xml.py --input rdf-output-hp.json
# Output: EBISearch_hp.xml

# For multi-ontology JSON, specify which one
python json_to_xml.py --input rdf-output.json --ontology-id hp
# Output: EBISearch_hp.xml
```

### Example with your files

```bash
# HP ontology
python json_to_xml.py --input rdf-output-hp.json
# Creates: EBISearch_hp.xml with 19,934 HP terms

# OIO ontology
python json_to_xml.py --input rdf-output-success.json
# Creates: EBISearch_oio.xml with 6 OIO terms
```

### How It Works

1. **Auto-detects prefix**: Reads `preferredPrefix` from JSON (e.g., "HP")
2. **Fallback**: Uses `ontologyId` if `preferredPrefix` is missing
3. **Filters terms**: Only includes terms matching the prefix (e.g., HP_*)
4. **Auto-names output**: Creates `EBISearch_{ontology_id}.xml`

## What Gets Extracted

For each term, the converter extracts:

1. **ID** - e.g., `HP_0000766` (from shortForm field)
2. **Name** - Primary label
3. **Description** - Definition text
4. **Synonyms** - All synonym variations
5. **OBO ID** - Colon format, e.g., `HP:0000766`
6. **IRI** - Full URI
7. **Ontology name** - Lowercase prefix, e.g., `hp`

## Output Format

```xml
<entry id="HP_0000766">
    <name>Abnormal sternum morphology</name>
    <description>An anomaly of the sternum...</description>
    <additional_fields>
        <field name="ontology_name">hp</field>
        <field name="obo_id">HP:0000766</field>
        <field name="iri">http://purl.obolibrary.org/obo/HP_0000766</field>
        <field name="synonym">Abnormality of the sternum</field>
        <field name="synonym">Pectus deformities</field>
    </additional_fields>
</entry>
```

## JSON Input Format Expected

The script expects JSON with this structure:

```json
{
  "ontologies": [
    {
      "ontologyId": "hp",
      "title": "Human Phenotype Ontology",
      "description": "...",
      "classes": [
        {
          "iri": "http://purl.obolibrary.org/obo/HP_0000766",
          "shortForm": {"value": "HP_0000766"},
          "curie": {"value": "HP:0000766"},
          "label": [{"value": "Abnormal sternum morphology"}],
          "definition": [{"value": "An anomaly of the sternum..."}],
          "synonym": [
            {
              "http://www.w3.org/2000/01/rdf-schema#label": {
                "value": "Abnormality of the sternum"
              }
            }
          ]
        }
      ]
    }
  ]
}
```

## Dependencies Required!

You'll need to install ijson (its to stream large json files) if not already in your environment:

```bash
pip install ijson
```
