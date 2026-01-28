use std::fs::File;
use std::io::BufReader;

use clap::Parser;

mod bioregistry;
mod copy_json_gathering_strings;
mod extract_iri_from_property_name;
mod curie_map_result;
mod leveldb;
mod linker_pass2;
mod obo_database_url_service;

use leveldb::LevelDB;
use linker_pass2::run;
use ols_shared::LinkerPass1Result;

/// Link OLS4 ontology JSON files
#[derive(Parser, Debug)]
#[command(name = "ols_link")]
#[command(about = "Link OLS4 ontology JSON with manifest data")]
struct Args {
    /// Input manifest JSON file (from create-manifest)
    #[arg(long)]
    manifest: String,

    /// Unlinked ontology JSON input filename
    #[arg(long)]
    input: String,

    /// Linked ontology JSON output filename
    #[arg(long)]
    output: String,

    /// Optional path of LevelDB containing extra mappings (for ORCID etc.)
    #[arg(long = "leveldbPath")]
    leveldb_path: Option<String>,
}

fn main() {
    if let Err(e) = run_main() {
        eprintln!("ERROR: Failed to link ontology");
        eprintln!("{}", e);
        std::process::exit(1);
    }
}

fn run_main() -> Result<(), Box<dyn std::error::Error>> {
    let args = Args::parse();

    // Load manifest
    eprintln!("Loading manifest from: {}", args.manifest);
    let manifest_file = File::open(&args.manifest)?;
    let manifest_reader = BufReader::new(manifest_file);
    let pass1_result: LinkerPass1Result = serde_json::from_reader(manifest_reader)?;

    // Open LevelDB if path provided
    let leveldb = if let Some(ref path) = args.leveldb_path {
        Some(LevelDB::open(path)?)
    } else {
        None
    };

    // Run linking
    eprintln!("Linking ontology from: {}", args.input);
    run(&args.input, &args.output, leveldb.as_ref(), &pass1_result)?;

    eprintln!("Linking complete. Output written to: {}", args.output);

    Ok(())
}
