//! rdf2json — converts OWL/RDF ontologies into OLS's intermediate JSON format.
//! A Rust port of the Java `uk.ac.ebi.rdf2json.RDF2JSON` tool. rdf2json reads RDF
//! triples (via oxrdfio) and projects them faithfully onto the OLS JSON model;
//! OWL-level normalisation (and IC-score injection) is handled upstream by
//! owlmake, so this tool stays a fast, vocabulary-agnostic triple projector.

mod annotators;
mod config;
mod error;
mod fetch;
mod graph;
mod merge;
mod model;
mod status;
mod validate_language;
mod writer;

use std::collections::HashSet;

use clap::Parser;

use crate::config::ConfigExt;
use crate::graph::{Options, OntologyGraph};

#[derive(Parser, Debug)]
#[command(name = "rdf2json")]
struct Args {
    /// Config JSON filename(s) separated by a comma. Subsequent configs are
    /// merged with / override previous ones.
    #[arg(long)]
    config: String,

    /// JSON output filename.
    #[arg(long)]
    output: String,

    /// Optional path of predownloaded ontologies from the downloader.
    #[arg(long)]
    downloaded_path: Option<String>,

    /// JSON file to merge our output with (keep ontologies not indexed this run).
    #[arg(long = "mergeOutputWith")]
    merge_output_with: Option<String>,

    /// Whether to load local files (unsafe, for testing).
    #[arg(long = "loadLocalFiles", default_value_t = false)]
    load_local_files: bool,

    /// Base path for resolving relative file paths (used with --loadLocalFiles).
    #[arg(long = "basePath")]
    base_path: Option<String>,

    /// Leave LOADED dates blank (for testing).
    #[arg(long = "noDates", default_value_t = false)]
    no_dates: bool,

    /// Optional comma-separated list of ontology IDs to load.
    #[arg(long = "ontologyIds")]
    ontology_ids: Option<String>,
}

fn main() {
    // accept both --downloaded_path and --downloadedPath spellings
    let args = Args::parse_from(normalize_arg_aliases(std::env::args()));

    let config_paths: Vec<String> = args.config.split(',').map(|s| s.to_string()).collect();

    let mut configs = match config::load_and_merge_configs(&config_paths) {
        Ok(c) => c,
        Err(e) => {
            eprintln!("{e}");
            std::process::exit(1);
        }
    };

    // Filter by --ontologyIds if requested.
    if let Some(ids) = &args.ontology_ids {
        let filter: HashSet<String> = ids
            .split(',')
            .map(|s| s.trim().to_lowercase())
            .collect();
        configs.retain(|c| {
            let id = c.get("id").and_then(|v| v.as_str()).unwrap_or("").to_lowercase();
            filter.contains(&id)
        });
    }

    let output_path = &args.output;
    let mut graphs: Vec<OntologyGraph> = Vec::new();

    // Lowercase ids of every ontology in this run's config (used by
    // --mergeOutputWith to tell a failed-this-run ontology from one merely kept).
    let config_ids: HashSet<String> = configs
        .iter()
        .filter_map(|c| c.get("id").and_then(|v| v.as_str()))
        .map(|s| s.to_lowercase())
        .collect();

    for config in configs {
        let ontology_id = config
            .get("id")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_lowercase();

        if config.is_true("is_obsolete") {
            eprintln!("Skipping obsolete ontology: {ontology_id}");
            status::write_skipped(output_path, &ontology_id, "Ontology is marked as obsolete");
            continue;
        }

        eprintln!("--- Loading ontology: {ontology_id}");

        let opts = Options {
            load_local_files: args.load_local_files,
            base_path: args.base_path.clone(),
            no_dates: args.no_dates,
            downloaded_path: args.downloaded_path.clone(),
        };

        match OntologyGraph::load(config, opts) {
            Ok(mut graph) => {
                if graph.ontology_node_id.is_none() {
                    eprintln!("No Ontology node found; nothing will be written");
                    status::write_failed_no_fallback(
                        output_path,
                        &ontology_id,
                        "No Ontology node found in RDF",
                    );
                    continue;
                }
                annotators::run_all(&mut graph);
                let version = extract_version(&graph);
                graphs.push(graph);
                status::write_success(output_path, &ontology_id, version.as_deref());
            }
            Err(e) => {
                eprintln!("Error processing ontology {ontology_id}: {e}");
                status::write_failed_no_fallback(output_path, &ontology_id, &e.to_string());
            }
        }
    }

    // Write this run's output (scoped so the file is flushed and closed before
    // any --mergeOutputWith pass reads it back).
    {
        let file = match std::fs::File::create(output_path) {
            Ok(f) => f,
            Err(e) => {
                eprintln!("Failed to create output {output_path}: {e}");
                std::process::exit(1);
            }
        };
        let out = std::io::BufWriter::new(file);
        if let Err(e) = writer::write_document(&graphs, out) {
            eprintln!("Failed to write output {output_path}: {e}");
            std::process::exit(1);
        }
    }

    // --mergeOutputWith: carry forward any ontology from the previous run that we
    // did not successfully (re)index this time (kept and marked as a fallback).
    if let Some(prev) = &args.merge_output_with {
        if std::path::Path::new(prev).exists() {
            let loaded_ids: HashSet<String> = graphs
                .iter()
                .filter_map(|g| g.config.get("id").and_then(|v| v.as_str()))
                .map(|s| s.to_lowercase())
                .collect();
            if let Err(e) = merge::merge_previous_run(output_path, prev, &loaded_ids, &config_ids) {
                eprintln!("Failed to merge with previous output {prev}: {e}");
            }
        } else {
            eprintln!("--mergeOutputWith file not found, skipping: {prev}");
        }
    }

    eprintln!("RDF2JSON processing complete. Status file written alongside output.");
}

fn extract_version(graph: &OntologyGraph) -> Option<String> {
    let onto = graph.ontology_node()?;
    let v = onto.properties.get_property_value("version")?;
    match &v.kind {
        model::PVKind::Literal(lit) => Some(lit.value.clone()),
        model::PVKind::Uri(u) => Some(u.clone()),
        _ => None,
    }
}

/// Allow the Java-style camelCase long options to also be passed with the
/// clap-canonical kebab/explicit names. We accept `--downloadedPath` as an alias
/// for the downloaded path option.
fn normalize_arg_aliases<I: Iterator<Item = String>>(args: I) -> Vec<String> {
    args.map(|a| {
        if let Some(rest) = a.strip_prefix("--downloadedPath") {
            format!("--downloaded-path{rest}")
        } else {
            a
        }
    })
    .collect()
}
