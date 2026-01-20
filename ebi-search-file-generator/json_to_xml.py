#!/usr/bin/env python3
"""
Convert large ontology JSON files to custom XML format for EBI Search.
This version uses streaming JSON parsing to handle very large files (78GB+).
"""

import argparse
import sys
from datetime import datetime
from xml.etree.ElementTree import Element, SubElement, ElementTree, indent
from typing import Optional, List, Dict
import os

try:
    import ijson
except ImportError:
    print("Error: ijson is required for streaming large files.", file=sys.stderr)
    print("Install it with: pip install ijson", file=sys.stderr)
    sys.exit(1)


def extract_value(obj, default=""):
    """
    Extract value from a nested object structure.
    """
    if obj is None:
        return default
    if isinstance(obj, dict):
        value = obj.get("value")
        if value is None:
            return default
        if isinstance(value, dict):
            return extract_value(value, default)
        if isinstance(value, str):
            return value
        return str(value)
    if isinstance(obj, list) and len(obj) > 0:
        return extract_value(obj[0], default)
    if isinstance(obj, str):
        return obj
    return default


def extract_synonyms(class_obj: dict) -> List[str]:
    """Extract all synonyms from a class object."""
    synonyms = []
    synonym_list = class_obj.get("synonym", [])

    if not synonym_list:
        return synonyms

    for syn_obj in synonym_list:
        if isinstance(syn_obj, dict):
            synonym_text = None

            if "value" in syn_obj:
                synonym_text = extract_value(syn_obj)

            if not synonym_text:
                label = syn_obj.get("http://www.w3.org/2000/01/rdf-schema#label")
                if label:
                    synonym_text = extract_value(label)

            if synonym_text and synonym_text not in synonyms:
                synonyms.append(str(synonym_text))

    return synonyms


def extract_definition(class_obj: dict) -> str:
    """Extract definition from a class object."""
    definition_list = class_obj.get("definition", [])
    if definition_list:
        result = extract_value(definition_list[0])
        return str(result) if result else ""
    return ""


def extract_label(class_obj: dict) -> str:
    """Extract label from a class object."""
    label_list = class_obj.get("label", [])
    if label_list:
        result = extract_value(label_list[0])
        return str(result) if result else ""
    return ""


def get_term_id_from_shortform(shortform: str) -> Optional[str]:
    """Extract ID from shortForm."""
    value = extract_value(shortform)
    if value and "_" in str(value):
        return str(value)
    return None


def get_obo_id_from_curie(curie: str, shortform: str) -> str:
    """Extract OBO ID from curie or shortform."""
    curie_value = extract_value(curie)
    if curie_value and ":" in str(curie_value):
        return str(curie_value)

    shortform_value = extract_value(shortform)
    if shortform_value and "_" in str(shortform_value):
        return str(shortform_value).replace("_", ":", 1)

    return ""


def get_ontology_prefix(shortform: str) -> str:
    """Extract ontology prefix from shortForm."""
    value = extract_value(shortform)
    if value and "_" in str(value):
        return str(value).split("_")[0].lower()
    return "unknown"


def process_ontology_streaming(file_path: str, target_ontology_id: Optional[str] = None,
                               process_all: bool = False) -> List[str]:
    """
    Process ontologies from a large JSON file using streaming.

    Args:
        file_path: Path to JSON file
        target_ontology_id: Specific ontology ID to process (optional)
        process_all: Process all ontologies in the file (default: False)

    Returns:
        List of generated output filenames
    """
    output_files = []

    print(f"Opening large JSON file: {file_path}")
    print(f"Using streaming parser to avoid memory issues...")

    with open(file_path, 'rb') as f:
        # Stream through ontologies array
        ontologies = ijson.items(f, 'ontologies.item')

        ontology_count = 0
        processed_count = 0

        for onto in ontologies:
            ontology_count += 1
            ontology_id = onto.get("ontologyId", "unknown")

            # Progress indicator every 10 ontologies
            if ontology_count % 10 == 0:
                print(f"  Scanned {ontology_count} ontologies, processed {processed_count}...")

            # Skip if not the target ontology (when processing single ontology)
            if not process_all and target_ontology_id:
                if ontology_id.lower() != target_ontology_id.lower():
                    continue

            # Skip ontologies with no classes
            classes = onto.get("classes", [])
            if len(classes) == 0:
                continue

            # If processing first ontology with classes and no target specified
            if not process_all and not target_ontology_id and processed_count > 0:
                break

            # Process this ontology
            output_file = process_single_ontology(onto, ontology_id)
            if output_file:
                output_files.append(output_file)
                processed_count += 1

                # If not processing all, stop after first match
                if not process_all and target_ontology_id:
                    break

        print(f"\nTotal ontologies in file: {ontology_count}")
        print(f"Processed: {processed_count}")

    return output_files


def process_single_ontology(onto: dict, ontology_id: str) -> Optional[str]:
    """
    Process a single ontology and generate XML file.

    Args:
        onto: Ontology dictionary
        ontology_id: Ontology ID

    Returns:
        Output filename if successful, None otherwise
    """
    # Get preferredPrefix, fallback to ontologyId
    preferred_prefix = onto.get("preferredPrefix", ontology_id)
    if not preferred_prefix:
        preferred_prefix = ontology_id

    # Use uppercase for filtering
    prefix_filter = preferred_prefix.upper()

    metadata = {
        "name": onto.get("title", preferred_prefix),
        "description": onto.get("description", ""),
        "release": "None",
        "release_date": datetime.now().strftime("%Y-%m-%dT%H:%M:%S.000+0000"),
        "ontology_id": ontology_id,
        "preferred_prefix": preferred_prefix
    }

    # Try to extract version info
    version_iri = onto.get("http://www.w3.org/2002/07/owl#versionIRI")
    if version_iri:
        version = extract_value(version_iri)
        if version:
            metadata["release"] = version

    # Generate output filename
    output_filename = f"EBISearch_{ontology_id}.xml"

    # Process classes
    classes = onto.get("classes", [])
    print(f"\nProcessing ontology '{ontology_id}' (prefix: {preferred_prefix}) with {len(classes)} classes")

    all_terms = []
    for class_obj in classes:
        shortform = class_obj.get("shortForm", {})
        term_id = get_term_id_from_shortform(shortform)

        if not term_id:
            continue

        # Filter by prefix
        if not term_id.upper().startswith(f"{prefix_filter}_"):
            continue

        # Extract term information
        iri = class_obj.get("iri", "")
        label = extract_label(class_obj)
        definition = extract_definition(class_obj)
        synonyms = extract_synonyms(class_obj)
        curie = class_obj.get("curie", {})

        term = {
            "id": term_id,
            "name": label,
            "description": definition,
            "ontology_name": get_ontology_prefix(shortform),
            "obo_id": get_obo_id_from_curie(curie, shortform),
            "iri": iri,
            "synonyms": synonyms
        }

        all_terms.append(term)

    print(f"  Found {len(all_terms)} terms with prefix {prefix_filter}")

    if len(all_terms) == 0:
        print(f"  Skipping - no terms found")
        return None

    # Create XML
    create_xml(metadata, all_terms, output_filename)

    return output_filename


def create_xml(metadata: dict, terms: list, output_file: str):
    """Create XML file in the specified format."""
    # Create root element
    database = Element("database")

    # Add metadata
    name_elem = SubElement(database, "name")
    name_elem.text = metadata["name"]

    desc_elem = SubElement(database, "description")
    desc_elem.text = metadata["description"]

    release_elem = SubElement(database, "release")
    release_elem.text = metadata["release"]

    release_date_elem = SubElement(database, "release_date")
    release_date_elem.text = metadata["release_date"]

    entry_count_elem = SubElement(database, "entry_count")
    entry_count_elem.text = str(len(terms))

    # Add entries
    entries = SubElement(database, "entries")

    for term in sorted(terms, key=lambda x: x["id"]):
        entry = SubElement(entries, "entry", id=term["id"])

        name_elem = SubElement(entry, "name")
        name_elem.text = term["name"]

        desc_elem = SubElement(entry, "description")
        desc_elem.text = term["description"]

        # Additional fields
        add_fields = SubElement(entry, "additional_fields")

        ontology_field = SubElement(add_fields, "field", name="ontology_name")
        ontology_field.text = term["ontology_name"]

        obo_id_field = SubElement(add_fields, "field", name="obo_id")
        obo_id_field.text = term["obo_id"]

        iri_field = SubElement(add_fields, "field", name="iri")
        iri_field.text = term["iri"]

        # Add synonyms (if any)
        for synonym in term.get("synonyms", []):
            synonym_field = SubElement(add_fields, "field", name="synonym")
            synonym_field.text = synonym

    # Create tree and write to file
    tree = ElementTree(database)
    indent(tree, space="    ")
    tree.write(output_file, encoding="utf-8", xml_declaration=False)
    print(f"  XML file created: {output_file}")


def main():
    parser = argparse.ArgumentParser(
        description="Convert large ontology JSON files to XML format (handles 78GB+ files)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Process first ontology with classes
  %(prog)s --input large-file.json

  # Process specific ontology by ID
  %(prog)s --input large-file.json --ontology-id hp

  # Process ALL ontologies (creates multiple XML files)
  %(prog)s --input large-file.json --all

This version uses streaming JSON parsing to handle very large files
without loading the entire file into memory.
        """
    )

    parser.add_argument(
        "-i", "--input",
        required=True,
        help="Path to JSON ontology file (can be very large)"
    )

    parser.add_argument(
        "--ontology-id",
        default=None,
        help="Specific ontology ID to process (optional)"
    )

    parser.add_argument(
        "--all",
        action="store_true",
        help="Process ALL ontologies in the file (creates multiple XML files)"
    )

    args = parser.parse_args()

    try:
        # Check file exists and get size
        file_size = os.path.getsize(args.input) / (1024**3)  # Size in GB
        print(f"File size: {file_size:.2f} GB")

        # Process ontologies with streaming
        output_files = process_ontology_streaming(
            args.input,
            args.ontology_id,
            args.all
        )

        if not output_files:
            print(f"\nWarning: No output files generated")
            sys.exit(1)

        print(f"\n{'='*50}")
        print(f"Success! Generated {len(output_files)} file(s):")
        for output_file in output_files:
            file_size_mb = os.path.getsize(output_file) / (1024**2)
            print(f"  - {output_file} ({file_size_mb:.2f} MB)")
        print(f"{'='*50}")

    except FileNotFoundError:
        print(f"Error: File not found: {args.input}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
