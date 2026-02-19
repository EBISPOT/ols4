use std::collections::{BTreeMap, BTreeSet, HashSet};
use std::fs::File;
use std::io::{BufReader, Read};

use ols_shared::streaming::{read_value, skip_value};
use ols_shared::DefinedFields;
use serde_json::Value;
use struson::reader::{JsonReader, JsonStreamReader};

use crate::ontology_scanner::scan_ontology;
use ols_shared::{EntityDefinition, EntityDefinitionSet, LinkerPass1Result};

/// Run LinkerPass1 on an input JSON file
pub fn run(input_json_filename: &str) -> Result<LinkerPass1Result, Box<dyn std::error::Error>> {
    let mut result = LinkerPass1Result::new();

    eprintln!("--- Linker Pass 1: Scanning {}", input_json_filename);

    // First pass: scan for properties using OntologyScanner
    let file1 = File::open(input_json_filename)?;
    let reader1 = BufReader::new(file1);
    let mut scanner_reader: JsonStreamReader<BufReader<File>> = JsonStreamReader::new(reader1);
    
    // Second pass: parse entity definitions
    let file2 = File::open(input_json_filename)?;
    let reader2 = BufReader::new(file2);
    let mut entity_reader: JsonStreamReader<BufReader<File>> = JsonStreamReader::new(reader2);

    let ignore_properties: HashSet<String> = HashSet::new();
    let mut n_ontologies = 0;

    // Begin both readers on the root object
    scanner_reader.begin_object().unwrap();
    entity_reader.begin_object().unwrap();

    while scanner_reader.has_next().unwrap() {
        let scanner_name = scanner_reader.next_name_owned().unwrap();
        let entity_name = entity_reader.next_name_owned().unwrap();

        if scanner_name == "ontologies" {
            scanner_reader.begin_array().unwrap();
            entity_reader.begin_array().unwrap();

            while scanner_reader.has_next().unwrap() {
                // Use scanner to extract property sets
                let scan_result = scan_ontology(&mut scanner_reader, &ignore_properties);

                // Store scanner results in manifest
                result.ontology_id_to_ontology_properties.insert(
                    scan_result.ontology_id.clone(),
                    scan_result.all_ontology_properties.clone(),
                );
                result.ontology_id_to_class_properties.insert(
                    scan_result.ontology_id.clone(),
                    scan_result.all_class_properties.clone(),
                );
                result.ontology_id_to_property_properties.insert(
                    scan_result.ontology_id.clone(),
                    scan_result.all_property_properties.clone(),
                );
                result.ontology_id_to_individual_properties.insert(
                    scan_result.ontology_id.clone(),
                    scan_result.all_individual_properties.clone(),
                );
                result.ontology_id_to_edge_properties.insert(
                    scan_result.ontology_id.clone(),
                    scan_result.all_edge_properties.clone(),
                );

                // Convert NodeType enum to strings for serialization
                let mut uri_to_type_strings: BTreeMap<String, BTreeSet<String>> = BTreeMap::new();
                for (uri, types) in &scan_result.uri_to_types {
                    let type_strings: BTreeSet<String> =
                        types.iter().map(|t| t.to_string()).collect();
                    uri_to_type_strings.insert(uri.clone(), type_strings);
                }
                result
                    .ontology_id_to_uri_to_types
                    .insert(scan_result.ontology_id.clone(), uri_to_type_strings);

                // Now parse the entity reader for this ontology
                parse_ontology(&mut entity_reader, &mut result, &mut n_ontologies)?;
            }

            scanner_reader.end_array().unwrap();
            entity_reader.end_array().unwrap();
        } else {
            skip_value(&mut scanner_reader);
            skip_value(&mut entity_reader);
        }
    }

    scanner_reader.end_object().unwrap();
    entity_reader.end_object().unwrap();

    eprintln!(
        "--- Linker Pass 1 complete. Found {} ontologies and {} distinct IRIs",
        n_ontologies,
        result.iri_to_definitions.len()
    );

    Ok(result)
}

fn parse_ontology<R: Read>(
    reader: &mut JsonStreamReader<R>,
    result: &mut LinkerPass1Result,
    n_ontologies: &mut usize,
) -> Result<(), Box<dyn std::error::Error>> {
    reader.begin_object().unwrap();

    let mut ontology_id: Option<String> = None;
    let mut ontology_base_uris: BTreeSet<String> = BTreeSet::new();

    while reader.has_next().unwrap() {
        let key = reader.next_name_owned().unwrap();

        match key.as_str() {
            "ontologyId" => {
                ontology_id = Some(reader.next_string().unwrap().to_string());
                *n_ontologies += 1;
                eprintln!("Scanning ontology: {}", ontology_id.as_ref().unwrap());
            }
            "iri" => {
                let ont_id = ontology_id
                    .as_ref()
                    .ok_or("missing ontologyId before iri")?;
                let ontology_iri = reader.next_string().unwrap().to_string();

                result
                    .ontology_iri_to_ontology_ids
                    .entry(ontology_iri)
                    .or_insert_with(BTreeSet::new)
                    .insert(ont_id.clone());
            }
            key if key == DefinedFields::BaseUri.text() => {
                let base_uris = read_value(reader);
                if let Value::Array(arr) = base_uris {
                    for base_uri in arr {
                        if let Value::String(uri) = base_uri {
                            ontology_base_uris.insert(uri);
                        }
                    }
                }
            }
            "preferredPrefix" => {
                let preferred_prefix = reader.next_string().unwrap().to_string();
                let ont_id = ontology_id
                    .as_ref()
                    .ok_or("missing ontologyId before preferredPrefix")?;

                ontology_base_uris
                    .insert(format!("http://purl.obolibrary.org/obo/{}_", preferred_prefix));

                result
                    .preferred_prefix_to_ontology_ids
                    .entry(preferred_prefix)
                    .or_insert_with(BTreeSet::new)
                    .insert(ont_id.clone());
            }
            "classes" => {
                let ont_id = ontology_id
                    .as_ref()
                    .ok_or("missing ontologyId before classes")?
                    .clone();
                parse_entity_array(reader, "class", &ont_id, &ontology_base_uris, result)?;
            }
            "properties" => {
                let ont_id = ontology_id
                    .as_ref()
                    .ok_or("missing ontologyId before properties")?
                    .clone();
                parse_entity_array(reader, "property", &ont_id, &ontology_base_uris, result)?;
            }
            "individuals" => {
                let ont_id = ontology_id
                    .as_ref()
                    .ok_or("missing ontologyId before individuals")?
                    .clone();
                parse_entity_array(reader, "individual", &ont_id, &ontology_base_uris, result)?;
            }
            _ => {
                skip_value(reader);
            }
        }
    }

    reader.end_object().unwrap();

    if let Some(ont_id) = ontology_id {
        result
            .ontology_id_to_base_uris
            .insert(ont_id.clone(), ontology_base_uris);
        eprintln!(
            "Now have {} ontologies and {} distinct IRIs",
            n_ontologies,
            result.iri_to_definitions.len()
        );
    }

    Ok(())
}

fn parse_entity_array<R: Read>(
    reader: &mut JsonStreamReader<R>,
    entity_type: &str,
    ontology_id: &str,
    ontology_base_uris: &BTreeSet<String>,
    result: &mut LinkerPass1Result,
) -> Result<(), Box<dyn std::error::Error>> {
    reader.begin_array().unwrap();

    while reader.has_next().unwrap() {
        parse_entity(reader, entity_type, ontology_id, ontology_base_uris, result)?;
    }

    reader.end_array().unwrap();
    Ok(())
}

fn parse_entity<R: Read>(
    reader: &mut JsonStreamReader<R>,
    entity_type: &str,
    ontology_id: &str,
    ontology_base_uris: &BTreeSet<String>,
    result: &mut LinkerPass1Result,
) -> Result<(), Box<dyn std::error::Error>> {
    reader.begin_object().unwrap();

    let mut iri: Option<String> = None;
    let mut label: Option<Value> = None;
    let mut curie: Option<Value> = None;
    let mut types: Option<BTreeSet<String>> = None;
    let mut is_obsolete = false;

    while reader.has_next().unwrap() {
        let key = reader.next_name_owned().unwrap();

        match key.as_str() {
            "iri" => {
                iri = Some(reader.next_string().unwrap().to_string());
            }
            "label" => {
                label = Some(read_value(reader));
            }
            "curie" => {
                curie = Some(read_value(reader));
            }
            "type" => {
                let type_value = read_value(reader);
                if let Value::Array(arr) = type_value {
                    types = Some(
                        arr.iter()
                            .filter_map(|v| v.as_str().map(String::from))
                            .collect(),
                    );
                }
            }
            "isObsolete" => {
                is_obsolete = reader.next_bool().unwrap();
            }
            _ => {
                skip_value(reader);
            }
        }
    }

    reader.end_object().unwrap();

    let iri = iri.ok_or("entity had no IRI")?;
    let types = types.ok_or("entity had no types")?;

    let entity_definition = EntityDefinition {
        ontology_id: ontology_id.to_string(),
        entity_types: types,
        is_defining_ontology: false,
        label,
        curie,
        is_obsolete,
    };

    let definition_set = result
        .iri_to_definitions
        .entry(iri.clone())
        .or_insert_with(EntityDefinitionSet::default);

    definition_set.definitions.insert(entity_definition.clone());
    definition_set
        .ontology_id_to_definitions
        .insert(ontology_id.to_string(), entity_definition);

    // Check if this entity's IRI starts with any of the ontology's base URIs
    for base_uri in ontology_base_uris {
        if iri.starts_with(base_uri) {
            definition_set
                .defining_ontology_ids
                .insert(ontology_id.to_string());
            break;
        }
    }

    Ok(())
}

/// Establish defining ontologies and cross-ontology import relationships.
/// This must be called AFTER all ontology files have been parsed and merged,
/// otherwise cross-ontology relationships will not be detected.
pub fn establish_defining_ontologies(result: &mut LinkerPass1Result) {
    // First pass: establish defining ontologies based on ontology IRIs
    let ontology_iri_to_ids = result.ontology_iri_to_ontology_ids.clone();

    for (iri, definitions) in result.iri_to_definitions.iter_mut() {

        // Update curie values and set isDefiningOntology flag
        let defining_ids = definitions.defining_ontology_ids.clone();
        let first_defining_id = defining_ids.iter().next().cloned();

        // Get defining entity's curie if available
        let defining_curie = first_defining_id.as_ref().and_then(|id| {
            definitions
                .ontology_id_to_definitions
                .get(id)
                .and_then(|def| def.curie.clone())
        });

        let mut updated_definitions = BTreeSet::new();
        for mut def in definitions.definitions.iter().cloned() {
            // Fix CURIE if needed
            if let Some(ref curie_val) = def.curie {
                if let Value::Object(curie_obj) = curie_val {
                    if let Some(Value::String(curie_str)) = curie_obj.get("value") {
                        if !curie_str.contains(':') {
                            if let Some(ref defining_curie_val) = defining_curie {
                                if let Value::Object(def_curie_obj) = defining_curie_val {
                                    if let Some(Value::String(def_curie_str)) =
                                        def_curie_obj.get("value")
                                    {
                                        let mut new_curie_obj = curie_obj.clone();
                                        new_curie_obj.insert(
                                            "value".to_string(),
                                            Value::String(def_curie_str.clone()),
                                        );
                                        def.curie = Some(Value::Object(new_curie_obj));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Mark as defining ontology if applicable
            if defining_ids.contains(&def.ontology_id) {
                def.is_defining_ontology = true;
            }

            updated_definitions.insert(def);
        }
        definitions.definitions = updated_definitions;

        // Update ontology_id_to_definitions with updated definitions
        for def in &definitions.definitions {
            definitions
                .ontology_id_to_definitions
                .insert(def.ontology_id.clone(), def.clone());
        }
    }

    // Second pass: establish import relationships
    let mut importing_ids: BTreeMap<String, BTreeSet<String>> = BTreeMap::new();
    let mut imported_ids: BTreeMap<String, BTreeSet<String>> = BTreeMap::new();

    for (iri, definitions) in result.iri_to_definitions.iter() {
        for def_a in &definitions.definitions {
            if def_a.is_defining_ontology {
                // The definition "defA" is in a defining ontology. If any other
                // ontologies use this entity and AREN'T defining, they are considered
                // as "importing" from this ontology.
                for def_b in &definitions.definitions {
                    if !def_b.is_defining_ontology {
                        // defB imports from defA
                        imported_ids
                            .entry(def_b.ontology_id.clone())
                            .or_insert_with(BTreeSet::new)
                            .insert(def_a.ontology_id.clone());

                        importing_ids
                            .entry(def_a.ontology_id.clone())
                            .or_insert_with(BTreeSet::new)
                            .insert(def_b.ontology_id.clone());
                    }
                }
            }
        }
    }

    result.ontology_id_to_imported_ontology_ids = imported_ids;
    result.ontology_id_to_importing_ontology_ids = importing_ids;

    // Compute defining definitions
    for (iri, definitions) in result.iri_to_definitions.iter_mut() {
        definitions.defining_definitions = definitions
            .definitions
            .iter()
            .filter(|def| def.is_defining_ontology)
            .cloned()
            .collect();
    }
}
