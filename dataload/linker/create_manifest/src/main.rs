use std::fs::File;
use std::io::Write;

use clap::Parser;

mod linker_pass1;
mod node_type;
mod ontology_scan_result;
mod ontology_scanner;

use linker_pass1::run;
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

fn run_main() -> Result<(), Box<dyn std::error::Error>> {
    let args = Args::parse();

    let input_files: Vec<&str> = args.input.split(',').map(|s| s.trim()).collect();

    let mut combined_result = LinkerPass1Result::new();

    // Process each input file
    for input_file in &input_files {
        eprintln!("Processing input file: {}", input_file);

        let file_result = run(input_file)?;

        // Merge results from this file into combined result
        combined_result.merge(file_result);
    }

    // Write the combined manifest
    eprintln!("Writing manifest to: {}", args.output);
    let json = serde_json::to_string_pretty(&combined_result)?;
    let mut file = File::create(&args.output)?;
    file.write_all(json.as_bytes())?;

    eprintln!("Manifest creation complete.");

    Ok(())
}
