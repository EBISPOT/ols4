use std::collections::{HashMap, HashSet};
use std::fs::File;
use std::io::BufReader;
use std::path::Path;

use clap::Parser;
use ols_shared::streaming::read_value;
use ols_shared::{DefinedFields, Embeddings};
use serde_json::Value;
use struson::reader::{JsonReader, JsonStreamReader};

mod manifest;
mod ontology_writer;

use manifest::{LinkerPass1Result, OntologyManifestInfo, NodeType};
use ontology_writer::OntologyWriter;

/// JSON to Neo4j CSV converter for OLS4
#[derive(Parser, Debug)]
#[command(name = "ols_json2neo")]
#[command(about = "Convert OLS JSON to Neo4j CSV format")]
struct Args {
    /// Ontology ID to process (optional, processes all if not specified)
    #[arg(long)]
    ontology_id: Option<String>,

    /// Input JSON file path
    #[arg(long)]
    input: String,

    /// Output CSV directory path
    #[arg(long = "outDir")]
    out_dir: String,

    /// Manifest JSON file from create-manifest
    #[arg(long)]
    manifest: String,

    /// Optional list of individual embeddings Parquet files
    #[arg(long = "embeddingParquets", num_args = 1..)]
    embedding_parquets: Option<Vec<String>>,
}

fn main() {
    if let Err(e) = run() {
        eprintln!("ERROR: Failed to convert JSON to CSV");
        eprintln!("{}", e);
        std::process::exit(1);
    }
}

fn load_parquet_file(
    path: &Path,
    ontology_id: Option<&str>,
) -> Result<(String, Embeddings), Box<dyn std::error::Error>> {
    eprintln!("Loading embeddings from {}", path.display());
    let model_name = path
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or("unknown")
        .to_string();

    let mut emb = Embeddings::new();
    emb.load_embeddings_from_file(path.to_str().unwrap(), ontology_id)?;

    eprintln!(
        "Loaded embeddings model {} with {} entries for ontology id {:?}",
        model_name,
        emb.embeddings_cache.len(),
        ontology_id
    );

    Ok((model_name, emb))
}

fn run() -> Result<(), Box<dyn std::error::Error>> {
    let args = Args::parse();

    // Load embeddings from individual parquet files or from a directory
    let mut embeddings: HashMap<String, Embeddings> = HashMap::new();

    if let Some(ref parquet_files) = args.embedding_parquets {
        for parquet_path in parquet_files {
            let path = Path::new(parquet_path);
            if path.exists() {
                let (model_name, emb) =
                    load_parquet_file(path, args.ontology_id.as_deref())?;
                embeddings.insert(model_name, emb);
            } else {
                eprintln!("Warning: embeddings parquet not found: {}", parquet_path);
            }
        }
        eprintln!("Loaded {} embeddings from parquet files", embeddings.len());
    } else {
        eprintln!("No embeddings parquets provided, skipping embeddings load.");
    }

    // Create converter and run
    let converter = NeoConverter::new(
        args.ontology_id,
        args.input,
        args.out_dir,
        args.manifest,
        embeddings,
    )?;
    converter.convert()?;

    Ok(())
}

struct NeoConverter {
    ontology_id: Option<String>,
    input_file_path: String,
    output_file_path: String,
    manifest: LinkerPass1Result,
    embeddings: HashMap<String, Embeddings>,
}

impl NeoConverter {
    fn new(
        ontology_id: Option<String>,
        input_file_path: String,
        output_file_path: String,
        manifest_file_path: String,
        embeddings: HashMap<String, Embeddings>,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        // Load the manifest
        eprintln!("Loading manifest from: {}", manifest_file_path);
        let manifest_file = File::open(&manifest_file_path)?;
        let manifest: LinkerPass1Result = serde_json::from_reader(BufReader::new(manifest_file))?;

        Ok(Self {
            ontology_id,
            input_file_path,
            output_file_path,
            manifest,
            embeddings,
        })
    }

    fn convert(self) -> Result<(), Box<dyn std::error::Error>> {
        eprintln!("Streaming input file: {}", self.input_file_path);
        
        let input_file = File::open(&self.input_file_path)?;
        let reader = BufReader::with_capacity(256 * 1024, input_file);
        let mut json = JsonStreamReader::new(reader);
        
        json.begin_object()?;
        
        let mut found_ontologies = false;
        
        while json.has_next()? {
            let key = json.next_name_owned()?;
            eprintln!("Found top-level key: {}", key);
            
            if key == "ontologies" {
                found_ontologies = true;
                
                json.begin_array()?;
                let mut ontology_count = 0;
                
                while json.has_next()? {
                    ontology_count += 1;
                    self.process_ontology_streaming(&mut json)?;
                }
                
                json.end_array()?;
                eprintln!("Processed {} ontologies", ontology_count);
            } else {
                json.skip_value()?;
            }
        }
        
        json.end_object()?;
        
        if !found_ontologies {
            eprintln!("WARNING: No 'ontologies' array found in input JSON");
        }
        
        Ok(())
    }
    
    fn process_ontology_streaming(&self, json: &mut JsonStreamReader<BufReader<File>>) -> Result<(), Box<dyn std::error::Error>> {
        json.begin_object()?;
        
        let mut ontology_id: Option<String> = None;
        let mut ontology_properties: serde_json::Map<String, Value> = serde_json::Map::new();
        let mut classes_processed = false;
        let mut properties_processed = false;
        let mut individuals_processed = false;
        let mut writer: Option<OntologyWriter> = None;
        
        while json.has_next()? {
            let key = json.next_name_owned()?;
            
            match key.as_str() {
                "ontologyId" => {
                    let value: Value = read_value(json);
                    let id = value.as_str().ok_or("Expected ontologyId to be a string")?.to_string();
                    ontology_id = Some(id.clone());
                    ontology_properties.insert("ontologyId".to_string(), value);
                    
                    // Check if we should skip this ontology
                    if let Some(ref filter_id) = self.ontology_id {
                        if !filter_id.is_empty() && ontology_id.as_ref() != Some(filter_id) {
                            eprintln!("Skipping ontology: {}", ontology_id.as_ref().unwrap());
                            // Skip remaining fields
                            while json.has_next()? {
                                json.skip_name()?;
                                json.skip_value()?;
                            }
                            json.end_object()?;
                            return Ok(());
                        }
                    }
                    
                    eprintln!("Processing ontology: {}", id);
                }
                "iri" => {
                    let value: Value = read_value(json);
                    ontology_properties.insert("iri".to_string(), value);
                }
                "classes" => {
                    let ont_id = ontology_id.as_ref().ok_or("classes found before ontologyId")?;
                    
                    if writer.is_none() {
                        let manifest_info = self.build_manifest_info(ont_id);
                        std::fs::create_dir_all(&self.output_file_path)?;
                        writer = Some(OntologyWriter::new(
                            &self.output_file_path,
                            manifest_info,
                            &self.embeddings,
                        )?);
                    }
                    
                    let w = writer.as_mut().unwrap();
                    self.process_entity_array_streaming(json, w, "classes")?;
                    classes_processed = true;
                }
                "properties" => {
                    let ont_id = ontology_id.as_ref().ok_or("properties found before ontologyId")?;
                    
                    if writer.is_none() {
                        let manifest_info = self.build_manifest_info(ont_id);
                        std::fs::create_dir_all(&self.output_file_path)?;
                        writer = Some(OntologyWriter::new(
                            &self.output_file_path,
                            manifest_info,
                            &self.embeddings,
                        )?);
                    }
                    
                    let w = writer.as_mut().unwrap();
                    self.process_entity_array_streaming(json, w, "properties")?;
                    properties_processed = true;
                }
                "individuals" => {
                    let ont_id = ontology_id.as_ref().ok_or("individuals found before ontologyId")?;
                    
                    if writer.is_none() {
                        let manifest_info = self.build_manifest_info(ont_id);
                        std::fs::create_dir_all(&self.output_file_path)?;
                        writer = Some(OntologyWriter::new(
                            &self.output_file_path,
                            manifest_info,
                            &self.embeddings,
                        )?);
                    }
                    
                    let w = writer.as_mut().unwrap();
                    self.process_entity_array_streaming(json, w, "individuals")?;
                    individuals_processed = true;
                }
                _ => {
                    // Store other ontology properties (they're usually small)
                    let value: Value = read_value(json);
                    ontology_properties.insert(key, value);
                }
            }
        }
        
        json.end_object()?;
        
        // Write ontology node if we have a valid ontology
        if let Some(ref ont_id) = ontology_id {
            if writer.is_none() {
                let manifest_info = self.build_manifest_info(ont_id);
                std::fs::create_dir_all(&self.output_file_path)?;
                writer = Some(OntologyWriter::new(
                    &self.output_file_path,
                    manifest_info,
                    &self.embeddings,
                )?);
            }
            
            let w = writer.as_mut().unwrap();
            
            if !classes_processed {
                w.write_empty_entities("classes")?;
            }
            if !properties_processed {
                w.write_empty_entities("properties")?;
            }
            if !individuals_processed {
                w.write_empty_entities("individuals")?;
            }
            
            w.write_ontology(&ontology_properties)?;
            w.finish()?;
            
            eprintln!("OntologyWriter complete for {:?}", ontology_id);
        }
        
        Ok(())
    }
    
    fn process_entity_array_streaming(
        &self,
        json: &mut JsonStreamReader<BufReader<File>>,
        writer: &mut OntologyWriter,
        entity_type: &str,
    ) -> Result<(), Box<dyn std::error::Error>> {
        writer.begin_entities(entity_type)?;
        
        json.begin_array()?;
        
        let mut count = 0;
        
        while json.has_next()? {
            let entity: Value = read_value(json);
            
            if let Some(entity_obj) = entity.as_object() {
                writer.write_entity(entity_type, entity_obj, &entity)?;
                count += 1;
                
                if count % 10000 == 0 {
                    eprintln!("  Processed {} {}...", count, entity_type);
                }
            }
        }
        
        json.end_array()?;
        
        writer.end_entities(entity_type)?;
        eprintln!("  Finished processing {} {}", count, entity_type);
        
        Ok(())
    }
    
    fn build_manifest_info(&self, ontology_id: &str) -> OntologyManifestInfo {
        let mut manifest_info = OntologyManifestInfo {
            ontology_id: ontology_id.to_string(),
            ontology_uri: String::new(),
            all_ontology_properties: HashSet::new(),
            all_class_properties: HashSet::new(),
            all_property_properties: HashSet::new(),
            all_individual_properties: HashSet::new(),
            all_edge_properties: HashSet::new(),
            uri_to_types: HashMap::new(),
        };
        
        // Apply blacklist to remove properties that shouldn't be in Neo4j
        manifest_info.all_ontology_properties = Self::filter_blacklist(
            self.manifest
                .ontology_id_to_ontology_properties
                .get(ontology_id)
                .cloned()
                .unwrap_or_default(),
        );
        manifest_info.all_class_properties = Self::filter_blacklist(
            self.manifest
                .ontology_id_to_class_properties
                .get(ontology_id)
                .cloned()
                .unwrap_or_default(),
        );
        manifest_info.all_property_properties = Self::filter_blacklist(
            self.manifest
                .ontology_id_to_property_properties
                .get(ontology_id)
                .cloned()
                .unwrap_or_default(),
        );
        manifest_info.all_individual_properties = Self::filter_blacklist(
            self.manifest
                .ontology_id_to_individual_properties
                .get(ontology_id)
                .cloned()
                .unwrap_or_default(),
        );
        manifest_info.all_edge_properties = self
            .manifest
            .ontology_id_to_edge_properties
            .get(ontology_id)
            .cloned()
            .unwrap_or_default();
        
        // Add defined fields that are added by LinkerPass2 and won't be in the manifest
        let linker_added_entity_fields: HashSet<String> = [
            "linkedEntities".to_string(),
            DefinedFields::IsDefiningOntology.text().to_string(),
            DefinedFields::DefinedBy.text().to_string(),
            DefinedFields::LinksTo.text().to_string(),
        ]
        .into_iter()
        .collect();
        
        manifest_info.all_class_properties.extend(linker_added_entity_fields.clone());
        manifest_info.all_property_properties.extend(linker_added_entity_fields.clone());
        manifest_info.all_individual_properties.extend(linker_added_entity_fields);
        
        manifest_info.all_ontology_properties.extend([
            "linkedEntities".to_string(),
            DefinedFields::ImportsFrom.text().to_string(),
            DefinedFields::ExportsTo.text().to_string(),
            DefinedFields::LinksTo.text().to_string(),
        ]);
        
        // Convert string type sets to NodeType sets for uri_to_types
        if let Some(uri_to_type_strings) = self
            .manifest
            .ontology_id_to_uri_to_types
            .get(ontology_id)
        {
            for (uri, type_strs) in uri_to_type_strings {
                let node_types: HashSet<NodeType> = type_strs
                    .iter()
                    .filter_map(|s| match s.as_str() {
                        "ONTOLOGY" => Some(NodeType::Ontology),
                        "CLASS" => Some(NodeType::Class),
                        "PROPERTY" => Some(NodeType::Property),
                        "INDIVIDUAL" => Some(NodeType::Individual),
                        _ => None,
                    })
                    .collect();
                manifest_info.uri_to_types.insert(uri.clone(), node_types);
            }
        }
        
        manifest_info
    }
    
    /// Filter out blacklisted properties that shouldn't be stored as Neo4j node properties.
    fn filter_blacklist(properties: HashSet<String>) -> HashSet<String> {
        let blacklist: HashSet<&str> = [
            DefinedFields::AppearsIn.text(),
            "searchableAnnotationValues",
        ]
        .into_iter()
        .collect();
        
        properties
            .into_iter()
            .filter(|prop| !blacklist.contains(prop.as_str()))
            .collect()
    }
}
