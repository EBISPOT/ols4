use std::collections::HashMap;
use std::fs::File;
use std::io::BufReader;
use std::path::Path;

use clap::Parser;
use ols_shared::streaming::read_value;
use ols_shared::Embeddings;
use serde_json::Value;
use struson::reader::{JsonReader, JsonStreamReader};

mod ontology_writer;

use ontology_writer::OntologyWriter;

/// JSON to PostgreSQL TSV converter for OLS4
#[derive(Parser, Debug)]
#[command(name = "ols_json2postgres")]
#[command(about = "Convert OLS JSON to PostgreSQL TSV format")]
struct Args {
    /// Ontology ID to process (optional, processes all if not specified)
    #[arg(long)]
    ontology_id: Option<String>,

    /// Input JSON file path
    #[arg(long)]
    input: String,

    /// Output TSV directory path
    #[arg(long = "outDir")]
    out_dir: String,

    /// Optional list of individual embeddings Parquet files
    #[arg(long = "embeddingParquets", num_args = 1..)]
    embedding_parquets: Option<Vec<String>>,

    /// Optional list of filter property URIs to extract as TEXT[] columns
    #[arg(long = "filterProperty", num_args = 1..)]
    filter_properties: Option<Vec<String>>,
}

fn main() {
    if let Err(e) = run() {
        eprintln!("ERROR: Failed to convert JSON to PostgreSQL binary COPY format");
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
    // rdf2json normalizes ontology IDs to lowercase in the generated JSON.
    // Normalize any requested ID here as well so per-ontology exports still
    // match testcases whose config IDs contain uppercase/camelCase.
    let normalized_ontology_id = args.ontology_id.as_ref().map(|s| s.to_lowercase());

    // Load embeddings from individual parquet files or from a directory
    let mut embeddings: HashMap<String, Embeddings> = HashMap::new();

    if let Some(ref parquet_files) = args.embedding_parquets {
        for parquet_path in parquet_files {
            let path = Path::new(parquet_path);
            if path.exists() {
                let (model_name, emb) =
                    load_parquet_file(path, normalized_ontology_id.as_deref())?;
                embeddings.insert(model_name, emb);
            } else {
                eprintln!("Warning: embeddings parquet not found: {}", parquet_path);
            }
        }
        eprintln!("Loaded {} embeddings from parquet files", embeddings.len());
    } else {
        eprintln!("No embeddings parquets provided, skipping embeddings load.");
    }

    let converter = PostgresConverter::new(
        normalized_ontology_id,
        args.input,
        args.out_dir,
        embeddings,
        args.filter_properties.unwrap_or_default(),
    )?;
    converter.convert()?;

    Ok(())
}

struct PostgresConverter {
    ontology_id: Option<String>,
    input_file_path: String,
    output_file_path: String,
    embeddings: HashMap<String, Embeddings>,
    filter_property_names: Vec<String>,
}

impl PostgresConverter {
    fn new(
        ontology_id: Option<String>,
        input_file_path: String,
        output_file_path: String,
        embeddings: HashMap<String, Embeddings>,
        filter_property_names: Vec<String>,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        Ok(Self {
            ontology_id,
            input_file_path,
            output_file_path,
            embeddings,
            filter_property_names,
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
        let mut _classes_processed = false;
        let mut _properties_processed = false;
        let mut _individuals_processed = false;
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
                        let matches_filter = ontology_id
                            .as_ref()
                            .map(|id| id.eq_ignore_ascii_case(filter_id))
                            .unwrap_or(false);
                        if !filter_id.is_empty() && !matches_filter {
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
                        std::fs::create_dir_all(&self.output_file_path)?;
                        writer = Some(OntologyWriter::new(
                            &self.output_file_path,
                            ont_id,
                            &self.embeddings,
                            &ontology_properties,
                            self.filter_property_names.clone(),
                        )?);
                    }
                    
                    let w = writer.as_mut().unwrap();
                    self.process_entity_array_streaming(json, w, "classes")?;
                    _classes_processed = true;
                }
                "properties" => {
                    let ont_id = ontology_id.as_ref().ok_or("properties found before ontologyId")?;
                    
                    if writer.is_none() {
                        std::fs::create_dir_all(&self.output_file_path)?;
                        writer = Some(OntologyWriter::new(
                            &self.output_file_path,
                            ont_id,
                            &self.embeddings,
                            &ontology_properties,
                            self.filter_property_names.clone(),
                        )?);
                    }
                    
                    let w = writer.as_mut().unwrap();
                    self.process_entity_array_streaming(json, w, "properties")?;
                    _properties_processed = true;
                }
                "individuals" => {
                    let ont_id = ontology_id.as_ref().ok_or("individuals found before ontologyId")?;
                    
                    if writer.is_none() {
                        std::fs::create_dir_all(&self.output_file_path)?;
                        writer = Some(OntologyWriter::new(
                            &self.output_file_path,
                            ont_id,
                            &self.embeddings,
                            &ontology_properties,
                            self.filter_property_names.clone(),
                        )?);
                    }
                    
                    let w = writer.as_mut().unwrap();
                    self.process_entity_array_streaming(json, w, "individuals")?;
                    _individuals_processed = true;
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
                std::fs::create_dir_all(&self.output_file_path)?;
                writer = Some(OntologyWriter::new(
                    &self.output_file_path,
                    ont_id,
                    &self.embeddings,
                    &ontology_properties,
                    self.filter_property_names.clone(),
                )?);
            }
            
            let w = writer.as_mut().unwrap();
            
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
        
        eprintln!("  Finished processing {} {}", count, entity_type);
        
        Ok(())
    }
}
