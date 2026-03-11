use std::collections::BTreeSet;
use std::collections::HashMap;
use std::fs::File;
use std::io::{BufReader, BufWriter, Read, Write};

use ols_shared::streaming::skip_value;
use ols_shared::DefinedFields;
use serde_json::Value;
use struson::reader::{JsonReader, JsonStreamReader};
use struson::writer::{JsonStreamWriter, JsonWriter, WriterSettings};

use crate::bioregistry::{is_curie, Bioregistry};
use crate::copy_json_gathering_strings::{copy_json_gathering_strings, write_value};
use crate::extract_iri_from_property_name;
use crate::leveldb::LevelDB;
use crate::obo_database_url_service::{map_curie, OboDatabaseUrlService};
use crate::sssom_literal_mappings::CuratedFromEntry;
use ols_shared::{EntityDefinitionSet, LinkerPass1Result};

/// Run LinkerPass2 on an input JSON file
pub fn run(
    input_json_filename: &str,
    output_json_filename: &str,
    leveldb: Option<&LevelDB>,
    pass1_result: &LinkerPass1Result,
    sssom_map: &HashMap<String, Vec<CuratedFromEntry>>,
) -> Result<(), Box<dyn std::error::Error>> {
    let input_file = File::open(input_json_filename)?;
    let output_file = File::create(output_json_filename)?;

    let reader = BufReader::new(input_file);
    let writer = BufWriter::new(output_file);

    let mut json_reader: JsonStreamReader<BufReader<File>> = JsonStreamReader::new(reader);
    let writer_settings = WriterSettings {
        pretty_print: true,
        ..Default::default()
    };
    let mut json_writer: JsonStreamWriter<BufWriter<File>> =
        JsonStreamWriter::new_custom(writer, writer_settings);

    // Initialize services
    eprintln!("Loading bioregistry...");
    let mut bioregistry = Bioregistry::new()?;
    eprintln!("Loading OBO database URLs...");
    let db_urls = OboDatabaseUrlService::new()?;

    eprintln!("--- Linker Pass 2: Processing {}", input_json_filename);
    let mut n_ontologies = 0;

    json_reader.begin_object()?;
    json_writer.begin_object()?;

    while json_reader.has_next()? {
        let name = json_reader.next_name_owned()?;

        if name == "ontologies" {
            json_writer.name("ontologies")?;

            json_reader.begin_array()?;
            json_writer.begin_array()?;

            while json_reader.has_next()? {
                json_reader.begin_object()?;
                json_writer.begin_object()?;

                // ontologyId should always come first
                let ontology_id_name = json_reader.next_name_owned()?;
                if ontology_id_name != "ontologyId" {
                    return Err("the json is not formatted correctly; ontologyId should always come first".into());
                }
                let ontology_id = json_reader.next_string()?.to_string();

                n_ontologies += 1;
                eprintln!("Writing ontology {} ({})", ontology_id, n_ontologies);

                json_writer.name("ontologyId")?;
                json_writer.string_value(&ontology_id)?;

                // Write importsFrom
                json_writer.name(DefinedFields::ImportsFrom.text())?;
                json_writer.begin_array()?;
                if let Some(imports) = pass1_result.ontology_id_to_imported_ontology_ids.get(&ontology_id) {
                    for ont_id in imports {
                        json_writer.string_value(ont_id)?;
                    }
                }
                json_writer.end_array()?;

                // Write exportsTo
                json_writer.name(DefinedFields::ExportsTo.text())?;
                json_writer.begin_array()?;
                if let Some(imported_by) = pass1_result.ontology_id_to_importing_ontology_ids.get(&ontology_id) {
                    for ont_id in imported_by {
                        json_writer.string_value(ont_id)?;
                    }
                }
                json_writer.end_array()?;

                let mut ontology_gathered_strings: BTreeSet<String> = BTreeSet::new();

                while json_reader.has_next()? {
                    let key = json_reader.next_name_owned()?;
                    json_writer.name(&key)?;

                    match key.as_str() {
                        "classes" => {
                            write_entity_array(
                                &mut json_reader,
                                &mut json_writer,
                                "class",
                                &ontology_id,
                                leveldb,
                                pass1_result,
                                &db_urls,
                                &mut bioregistry,
                                sssom_map,
                            )?;
                        }
                        "properties" => {
                            write_entity_array(
                                &mut json_reader,
                                &mut json_writer,
                                "property",
                                &ontology_id,
                                leveldb,
                                pass1_result,
                                &db_urls,
                                &mut bioregistry,
                                sssom_map,
                            )?;
                        }
                        "individuals" => {
                            write_entity_array(
                                &mut json_reader,
                                &mut json_writer,
                                "individual",
                                &ontology_id,
                                leveldb,
                                pass1_result,
                                &db_urls,
                                &mut bioregistry,
                                sssom_map,
                            )?;
                        }
                        _ => {
                            ontology_gathered_strings.insert(
                                extract_iri_from_property_name::extract(&key).to_string()
                            );
                            copy_json_gathering_strings(
                                &mut json_reader,
                                &mut json_writer,
                                &mut ontology_gathered_strings,
                            )?;
                        }
                    }
                }

                // Write linkedEntities for ontology
                json_writer.name("linkedEntities")?;
                let links_to_iris = write_linked_entities_from_gathered_strings(
                    &mut json_writer,
                    &ontology_gathered_strings,
                    &ontology_id,
                    None,
                    leveldb,
                    pass1_result,
                    &db_urls,
                    &mut bioregistry,
                )?;

                // Write linksTo
                json_writer.name("linksTo")?;
                json_writer.begin_array()?;
                for link_to_iri in &links_to_iris {
                    json_writer.string_value(link_to_iri)?;
                }
                json_writer.end_array()?;

                json_reader.end_object()?;
                json_writer.end_object()?;
            }

            json_reader.end_array()?;
            json_writer.end_array()?;
        } else {
            skip_value(&mut json_reader);
        }
    }

    json_reader.end_object()?;
    json_writer.end_object()?;

    // Ensure writer is flushed
    json_writer.finish_document()?;

    eprintln!("--- Linker Pass 2 complete");
    Ok(())
}

fn write_entity_array<R: Read, W: Write>(
    json_reader: &mut JsonStreamReader<R>,
    json_writer: &mut JsonStreamWriter<W>,
    _entity_type: &str,
    ontology_id: &str,
    leveldb: Option<&LevelDB>,
    pass1_result: &LinkerPass1Result,
    db_urls: &OboDatabaseUrlService,
    bioregistry: &mut Bioregistry,
    sssom_map: &HashMap<String, Vec<CuratedFromEntry>>,
) -> Result<(), Box<dyn std::error::Error>> {
    json_reader.begin_array()?;
    json_writer.begin_array()?;

    while json_reader.has_next()? {
        json_writer.begin_object()?;
        json_reader.begin_object()?;

        let mut strings_in_entity: BTreeSet<String> = BTreeSet::new();
        let mut entity_iri: Option<String> = None;

        while json_reader.has_next()? {
            let name = json_reader.next_name_owned()?;
            strings_in_entity.insert(extract_iri_from_property_name::extract(&name).to_string());
            json_writer.name(&name)?;

            match name.as_str() {
                "iri" => {
                    let iri = json_reader.next_string()?.to_string();
                    entity_iri = Some(iri.clone());
                    json_writer.string_value(&iri)?;
                }
                "curie" => {
                    process_curie_object(json_reader, json_writer, pass1_result, entity_iri.as_deref())?;
                }
                "shortForm" => {
                    process_short_form_object(json_reader, json_writer, pass1_result, entity_iri.as_deref())?;
                }
                _ => {
                    copy_json_gathering_strings(json_reader, json_writer, &mut strings_in_entity)?;
                }
            }
        }

        // Add definition metadata if available
        if let Some(ref iri) = entity_iri {
            if let Some(def_of_this_entity) = pass1_result.iri_to_definitions.get(iri) {
                // isDefiningOntology
                json_writer.name(DefinedFields::IsDefiningOntology.text())?;
                json_writer.bool_value(def_of_this_entity.defining_ontology_ids.contains(ontology_id))?;

                // definedBy
                if !def_of_this_entity.defining_definitions.is_empty() {
                    json_writer.name(DefinedFields::DefinedBy.text())?;
                    json_writer.begin_array()?;
                    for def in &def_of_this_entity.defining_definitions {
                        json_writer.string_value(&def.ontology_id)?;
                    }
                    json_writer.end_array()?;
                }

                // appearsIn
                if !def_of_this_entity.definitions.is_empty() {
                    json_writer.name(DefinedFields::AppearsIn.text())?;
                    json_writer.begin_array()?;
                    for def in &def_of_this_entity.definitions {
                        json_writer.string_value(&def.ontology_id)?;
                    }
                    json_writer.end_array()?;
                }
            }
        }

        // Write curatedFrom (only for defining entities)
        if let Some(ref iri) = entity_iri {
            let is_defining = pass1_result.iri_to_definitions.get(iri)
                .map(|d| d.defining_ontology_ids.contains(ontology_id))
                .unwrap_or(false);

            if is_defining {
                if let Some(curations) = sssom_map.get(iri) {
                    json_writer.name("curatedFrom")?;
                    json_writer.begin_array()?;
                    for entry in curations {
                        json_writer.begin_object()?;

                        json_writer.name("text")?;
                        json_writer.string_value(&entry.text)?;

                        json_writer.name("source")?;
                        json_writer.string_value(&entry.source)?;

                        if !entry.subject_categories.is_empty() {
                            json_writer.name("subjectCategories")?;
                            json_writer.begin_array()?;
                            for cat in &entry.subject_categories {
                                json_writer.string_value(cat)?;
                            }
                            json_writer.end_array()?;
                        }

                        json_writer.end_object()?;
                    }
                    json_writer.end_array()?;

                    // Write curatedFromSources: unique source names for this entity
                    let sources: BTreeSet<&str> = curations.iter().map(|e| e.source.as_str()).collect();
                    json_writer.name("curatedFromSources")?;
                    json_writer.begin_array()?;
                    for ds in &sources {
                        json_writer.string_value(ds)?;
                    }
                    json_writer.end_array()?;
                }
            }
        }

        // Write linkedEntities
        json_writer.name("linkedEntities")?;
        let links_to_iris = write_linked_entities_from_gathered_strings(
            json_writer,
            &strings_in_entity,
            ontology_id,
            entity_iri.as_deref(),
            leveldb,
            pass1_result,
            db_urls,
            bioregistry,
        )?;

        // Write linksTo
        json_writer.name("linksTo")?;
        json_writer.begin_array()?;
        for link_to_iri in &links_to_iris {
            json_writer.string_value(link_to_iri)?;
        }
        json_writer.end_array()?;

        json_writer.end_object()?;
        json_reader.end_object()?;
    }

    json_reader.end_array()?;
    json_writer.end_array()?;
    Ok(())
}

fn write_linked_entities_from_gathered_strings<W: Write>(
    json_writer: &mut JsonStreamWriter<W>,
    strings: &BTreeSet<String>,
    ontology_id: &str,
    entity_iri: Option<&str>,
    _leveldb: Option<&LevelDB>,
    pass1_result: &LinkerPass1Result,
    db_urls: &OboDatabaseUrlService,
    bioregistry: &mut Bioregistry,
) -> Result<BTreeSet<String>, Box<dyn std::error::Error>> {
    let mut links_to_iris: BTreeSet<String> = BTreeSet::new();

    json_writer.begin_object()?;

    for s in strings {
        if s.trim().is_empty() {
            continue;
        }

        // Skip certain namespaces
        if s.starts_with("http://www.w3.org/1999/02/22-rdf-syntax-ns#")
            || s.starts_with("http://www.w3.org/2002/07/owl#")
        {
            continue;
        }

        // Skip self-references
        if let Some(iri) = entity_iri {
            if s == iri {
                continue;
            }
        }

        // Check if it's a known IRI
        if let Some(iri_mapping) = pass1_result.iri_to_definitions.get(s) {
            links_to_iris.insert(s.clone());

            json_writer.name(s)?;
            json_writer.begin_object()?;
            write_iri_mapping(json_writer, iri_mapping, ontology_id)?;
            json_writer.end_object()?;
            continue;
        }

        // Try to resolve as CURIE
        let curie = bioregistry.get_curie_for_url(s).or_else(|| {
            if is_curie(s) {
                Some(s.clone())
            } else {
                None
            }
        });

        if let Some(ref curie) = curie {
            if let Some(colon_pos) = curie.find(':') {
                let database_id = &curie[..colon_pos];
                let entry_id = &curie[colon_pos + 1..];

                let mut found_curie_match_to_ontology_term = false;

                // Check if the databaseId is a preferredPrefix of an ontology in OLS
                if let Some(ontology_ids) = pass1_result.preferred_prefix_to_ontology_ids.get(database_id) {
                    for curie_ontology_id in ontology_ids {
                        if let Some(ontology_base_uris) = pass1_result.ontology_id_to_base_uris.get(curie_ontology_id) {
                            for ontology_base_uri in ontology_base_uris {
                                let iri = format!("{}{}", ontology_base_uri, entry_id);
                                if let Some(curie_iri_mapping) = pass1_result.iri_to_definitions.get(&iri) {
                                    found_curie_match_to_ontology_term = true;
                                    json_writer.name(s)?;
                                    json_writer.begin_object()?;
                                    json_writer.name("iri")?;
                                    json_writer.string_value(&iri)?;
                                    write_iri_mapping(json_writer, curie_iri_mapping, ontology_id)?;

                                    links_to_iris.insert(iri);
                                    break;
                                }
                            }
                            if found_curie_match_to_ontology_term {
                                break;
                            }
                        }
                    }
                }

                // Try to map CURIE to URL using bioregistry/db-xrefs
                if let Some(curie_mapping) = map_curie(db_urls, bioregistry, database_id, entry_id) {
                    if !found_curie_match_to_ontology_term {
                        json_writer.name(s)?;
                        json_writer.begin_object()?;
                    }

                    json_writer.name("url")?;
                    json_writer.string_value(&curie_mapping.url)?;
                    json_writer.name("source")?;
                    json_writer.string_value(&curie_mapping.source)?;
                    json_writer.name("curie")?;
                    json_writer.string_value(curie)?;

                    found_curie_match_to_ontology_term = true;
                }

                if found_curie_match_to_ontology_term {
                    json_writer.end_object()?;
                }
            }
        }

        // TODO: LevelDB lookup for ORCIDs etc. - currently disabled as LevelDB
        // support needs proper mutable access patterns
    }

    json_writer.end_object()?;

    Ok(links_to_iris)
}

fn write_iri_mapping<W: Write>(
    json_writer: &mut JsonStreamWriter<W>,
    definitions: &EntityDefinitionSet,
    ontology_id: &str,
) -> Result<(), Box<dyn std::error::Error>> {
    // definedBy
    if !definitions.defining_definitions.is_empty() {
        json_writer.name(DefinedFields::DefinedBy.text())?;
        json_writer.begin_array()?;
        for def in &definitions.defining_definitions {
            json_writer.string_value(&def.ontology_id)?;
        }
        json_writer.end_array()?;
    } else if definitions.defining_ontology_ids.len() == 1 {
        // Term only defined in one ontology, that's the canonical one
        json_writer.name(DefinedFields::DefinedBy.text())?;
        json_writer.begin_array()?;
        json_writer.string_value(definitions.defining_ontology_ids.iter().next().unwrap())?;
        json_writer.end_array()?;
    }

    json_writer.name("numAppearsIn")?;
    json_writer.number_value(definitions.definitions.len() as i64)?;

    json_writer.name(DefinedFields::HasLocalDefinition.text())?;
    json_writer.bool_value(definitions.ontology_id_to_definitions.contains_key(ontology_id))?;

    let def_from_this_ontology = definitions.ontology_id_to_definitions.get(ontology_id);

    // Priority: 1. Defining ontology, 2. This ontology, 3. First available
    if let Some(defining_def) = definitions.defining_definitions.iter().next() {
        // Use metadata from defining ontology
        json_writer.name("label")?;
        write_value(json_writer, defining_def.label.as_ref().unwrap_or(&Value::Null))?;
        json_writer.name("curie")?;
        write_value(json_writer, defining_def.curie.as_ref().unwrap_or(&Value::Null))?;
        json_writer.name("type")?;
        json_writer.begin_array()?;
        for t in &defining_def.entity_types {
            json_writer.string_value(t)?;
        }
        json_writer.end_array()?;
    } else if let Some(def) = def_from_this_ontology {
        // Use metadata from this ontology
        json_writer.name("label")?;
        write_value(json_writer, def.label.as_ref().unwrap_or(&Value::Null))?;
        json_writer.name("curie")?;
        write_value(json_writer, def.curie.as_ref().unwrap_or(&Value::Null))?;
        json_writer.name("type")?;
        json_writer.begin_array()?;
        for t in &def.entity_types {
            json_writer.string_value(t)?;
        }
        json_writer.end_array()?;
    } else if let Some(fallback_def) = definitions.definitions.iter().next() {
        // Fallback to first available
        json_writer.name("type")?;
        json_writer.begin_array()?;
        for t in &fallback_def.entity_types {
            json_writer.string_value(t)?;
        }
        json_writer.end_array()?;
        json_writer.name("label")?;
        write_value(json_writer, fallback_def.label.as_ref().unwrap_or(&Value::Null))?;
        json_writer.name("curie")?;
        write_value(json_writer, fallback_def.curie.as_ref().unwrap_or(&Value::Null))?;
    }

    Ok(())
}

fn process_curie_object<R: Read, W: Write>(
    json_reader: &mut JsonStreamReader<R>,
    json_writer: &mut JsonStreamWriter<W>,
    pass1_result: &LinkerPass1Result,
    entity_iri: Option<&str>,
) -> Result<(), Box<dyn std::error::Error>> {
    json_reader.begin_object()?;

    let mut type_array: Vec<String> = Vec::new();
    let mut curie_value: Option<String> = None;

    while json_reader.has_next()? {
        let field_name = json_reader.next_name_owned()?;
        match field_name.as_str() {
            "type" => {
                json_reader.begin_array()?;
                while json_reader.has_next()? {
                    type_array.push(json_reader.next_string()?.to_string());
                }
                json_reader.end_array()?;
            }
            "value" => {
                let _original_value = json_reader.next_string()?.to_string();
                // Modify the value attribute using the processed curie value
                curie_value = Some(get_processed_curie_value(pass1_result, entity_iri));
            }
            _ => {
                skip_value(json_reader);
            }
        }
    }
    json_reader.end_object()?;

    // Write the modified curie object
    json_writer.begin_object()?;
    json_writer.name("type")?;
    json_writer.begin_array()?;
    for t in &type_array {
        json_writer.string_value(t)?;
    }
    json_writer.end_array()?;
    json_writer.name("value")?;
    json_writer.string_value(&curie_value.unwrap_or_default())?;
    json_writer.end_object()?;

    Ok(())
}

fn process_short_form_object<R: Read, W: Write>(
    json_reader: &mut JsonStreamReader<R>,
    json_writer: &mut JsonStreamWriter<W>,
    pass1_result: &LinkerPass1Result,
    entity_iri: Option<&str>,
) -> Result<(), Box<dyn std::error::Error>> {
    json_reader.begin_object()?;

    let mut type_array: Vec<String> = Vec::new();
    let mut short_form_value: Option<String> = None;

    while json_reader.has_next()? {
        let field_name = json_reader.next_name_owned()?;
        match field_name.as_str() {
            "type" => {
                json_reader.begin_array()?;
                while json_reader.has_next()? {
                    type_array.push(json_reader.next_string()?.to_string());
                }
                json_reader.end_array()?;
            }
            "value" => {
                let _original_value = json_reader.next_string()?.to_string();
                // Modify the value attribute - replace : with _ for shortForm
                short_form_value = Some(get_processed_curie_value(pass1_result, entity_iri).replace(':', "_"));
            }
            _ => {
                skip_value(json_reader);
            }
        }
    }
    json_reader.end_object()?;

    // Write the modified short form object
    json_writer.begin_object()?;
    json_writer.name("type")?;
    json_writer.begin_array()?;
    for t in &type_array {
        json_writer.string_value(t)?;
    }
    json_writer.end_array()?;
    json_writer.name("value")?;
    json_writer.string_value(&short_form_value.unwrap_or_default())?;
    json_writer.end_object()?;

    Ok(())
}

fn get_processed_curie_value(pass1_result: &LinkerPass1Result, entity_iri: Option<&str>) -> String {
    let entity_iri = match entity_iri {
        Some(iri) if !iri.is_empty() => iri,
        _ => return String::new(),
    };

    let def = match pass1_result.iri_to_definitions.get(entity_iri) {
        Some(d) => d,
        None => return String::new(),
    };

    if let Some(first_def) = def.definitions.iter().next() {
        if let Some(Value::Object(ref curie_obj)) = first_def.curie {
            if let Some(Value::String(value)) = curie_obj.get("value") {
                return value.clone();
            }
        }
    }

    String::new()
}
