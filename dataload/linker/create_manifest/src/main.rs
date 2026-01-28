use std::fs::File;
use std::io::Write;

use clap::Parser;
use rayon::prelude::*;

mod linker_pass1;
mod node_type;
mod ontology_scan_result;
mod ontology_scanner;

use linker_pass1::{run, establish_defining_ontologies};
use ols_shared::LinkerPass1Result;

/// Create manifest for OLS4 linker
#[derive(Parser, Debug)]
#[command(name = "ols_create_manifest")]
#[command(about = "Create manifest JSON for OLS4 linking process")]
struct Args {
    /// Input JSON ontology file(s), comma-separated for multiple files
    #[arg(long)]
    input: String,

    /// Output manifest JSON filename
    #[arg(long)]
    output: String,
}

fn main() {
    if let Err(e) = run_main() {
        eprintln!("ERROR: Failed to create manifest");
        eprintln!("{}", e);
        std::process::exit(1);
    }
}

fn run_main() -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let args = Args::parse();

    let input_files: Vec<&str> = args.input.split(',').map(|s| s.trim()).collect();

    eprintln!("Processing {} input files in parallel...", input_files.len());

    // Process each input file in parallel
    let results: Vec<LinkerPass1Result> = input_files
        .par_iter()
        .map(|input_file| {
            eprintln!("Processing input file: {}", input_file);
            run(input_file).expect(&format!("Failed to process {}", input_file))
        })
        .collect();

    // Merge all results into one
    eprintln!("Merging {} results...", results.len());
    let mut combined_result = LinkerPass1Result::new();
    for file_result in results {
        combined_result.merge(file_result);
    }

    // Establish cross-ontology relationships after all files have been merged
    eprintln!("Establishing cross-ontology relationships...");
    establish_defining_ontologies(&mut combined_result);

    // Write the combined manifest
    eprintln!("Writing manifest to: {}", args.output);
    let json = serde_json::to_string_pretty(&combined_result)?;
    let mut file = File::create(&args.output)?;
    file.write_all(json.as_bytes())?;

    eprintln!("Manifest creation complete.");

    Ok(())
}
