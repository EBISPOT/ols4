use std::collections::{HashMap, HashSet};
use std::fs::File;
use std::io::BufReader;
use std::path::Path;

use clap::Parser;
use serde_json::Value;

mod defined_fields;
mod embeddings;
mod manifest;
mod ontology_writer;

use defined_fields::DefinedFields;
use embeddings::Embeddings;
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

    /// Optional folder containing embeddings Parquet files
    #[arg(long = "embeddingDbsPath")]
    embedding_dbs_path: Option<String>,
}

fn main() {
    if let Err(e) = run() {
        eprintln!("ERROR: Failed to convert JSON to CSV");
        eprintln!("{}", e);
        std::process::exit(1);
    }
}

fn run() -> Result<(), Box<dyn std::error::Error>> {
    let args = Args::parse();

    // Load embeddings if path provided
    let mut embeddings: HashMap<String, Embeddings> = HashMap::new();

    if let Some(ref embeddings_path) = args.embedding_dbs_path {
        let embeddings_dir = Path::new(embeddings_path);
        if embeddings_dir.exists() && embeddings_dir.is_dir() {
            for entry in std::fs::read_dir(embeddings_dir)? {
                let entry = entry?;
                let path = entry.path();
                if path.extension().and_then(|s| s.to_str()) == Some("parquet") {
                    eprintln!("Loading embeddings from {}", path.display());
                    let model_name = path
                        .file_stem()
                        .and_then(|s| s.to_str())
                        .unwrap_or("unknown")
                        .to_string();

                    let mut emb = Embeddings::new();
                    emb.load_embeddings_from_file(
                        path.to_str().unwrap(),
                        args.ontology_id.as_deref(),
                    )?;

                    eprintln!(
                        "Loaded embeddings model {} with {} entries for ontology id {:?}",
                        model_name,
                        emb.embeddings_cache.len(),
                        args.ontology_id
                    );

                    embeddings.insert(model_name, emb);
                }
            }
            eprintln!(
                "Loaded {} embeddings databases",
                embeddings.len()
            );
        }
    } else {
        eprintln!("No embeddings path provided, skipping embeddings load.");
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
        eprintln!("Reading input file: {}", self.input_file_path);
        let input_file = File::open(&self.input_file_path)?;
        let reader = BufReader::new(input_file);
        
        // Parse the entire JSON
        let root: Value = serde_json::from_reader(reader)?;
        
        let root_obj = root.as_object().ok_or("Expected JSON object at root")?;
        
        let mut found_ontologies = false;
        
        for (name, value) in root_obj {
            eprintln!("Found top-level key: {}", name);
            
            if name == "ontologies" {
                found_ontologies = true;
                
                let ontologies = value.as_array().ok_or("Expected 'ontologies' to be an array")?;
                let mut ontology_count = 0;
                
                for ontology_value in ontologies {
                    ontology_count += 1;
                    
                    let ontology = ontology_value.as_object().ok_or("Expected ontology to be an object")?;
                    
                    let read_ontology_id = ontology
                        .get("ontologyId")
                        .and_then(|v| v.as_str())
                        .ok_or("Expected 'ontologyId' as first field in ontology")?;
                    
                    // Skip ontologies that don't match the requested ontologyId
                    if let Some(ref filter_id) = self.ontology_id {
                        if !filter_id.is_empty() && read_ontology_id != filter_id {
                            eprintln!("Skipping ontology: {}", read_ontology_id);
                            continue;
                        }
                    }
                    
                    eprintln!("Processing ontology: {}", read_ontology_id);
                    
                    // Get scanner results from manifest
                    let manifest_info = self.build_manifest_info(read_ontology_id);
                    
                    // Create output directory if it doesn't exist
                    std::fs::create_dir_all(&self.output_file_path)?;
                    
                    let mut writer = OntologyWriter::new(
                        &self.output_file_path,
                        manifest_info,
                        &self.embeddings,
                    )?;
                    
                    writer.write(ontology)?;
                    
                    eprintln!("OntologyWriter complete for {:?}", self.ontology_id);
                }
                
                eprintln!("Processed {} ontologies", ontology_count);
            }
        }
        
        if !found_ontologies {
            eprintln!("WARNING: No 'ontologies' array found in input JSON");
        }
        
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
