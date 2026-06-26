//! Per-ontology status files (`*.status.json`), ported from
//! `OntologyStatusWriter`. Uses serde_json; null fields are omitted and field
//! order is ontologyId, status, errorMessage, version.

use serde::Serialize;

#[derive(Clone, Copy)]
pub enum Status {
    Success,
    Fallback,
    FailedNoFallback,
    Skipped,
}

impl Status {
    fn name(self) -> &'static str {
        match self {
            Status::Success => "SUCCESS",
            Status::Fallback => "FALLBACK",
            Status::FailedNoFallback => "FAILED_NO_FALLBACK",
            Status::Skipped => "SKIPPED",
        }
    }
}

#[derive(Serialize)]
struct OntologyStatus<'a> {
    #[serde(rename = "ontologyId")]
    ontology_id: &'a str,
    status: &'a str,
    #[serde(rename = "errorMessage", skip_serializing_if = "Option::is_none")]
    error_message: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    version: Option<&'a str>,
}

fn status_file_path(output_path: &str) -> String {
    output_path.replace(".json", ".status.json")
}

fn write_status(
    output_path: &str,
    ontology_id: &str,
    status: Status,
    error_message: Option<&str>,
    version: Option<&str>,
) {
    let record = OntologyStatus {
        ontology_id,
        status: status.name(),
        error_message,
        version,
    };
    let json = serde_json::to_string_pretty(&record).unwrap_or_default();
    let path = status_file_path(output_path);
    if let Err(e) = std::fs::write(&path, json) {
        eprintln!("Failed to write status file {path}: {e}");
    }
}

pub fn write_success(output_path: &str, ontology_id: &str, version: Option<&str>) {
    write_status(output_path, ontology_id, Status::Success, None, version);
}

pub fn write_fallback(output_path: &str, ontology_id: &str, version: Option<&str>, error: &str) {
    write_status(output_path, ontology_id, Status::Fallback, Some(error), version);
}

pub fn write_failed_no_fallback(output_path: &str, ontology_id: &str, error: &str) {
    write_status(
        output_path,
        ontology_id,
        Status::FailedNoFallback,
        Some(error),
        None,
    );
}

pub fn write_skipped(output_path: &str, ontology_id: &str, reason: &str) {
    write_status(output_path, ontology_id, Status::Skipped, Some(reason), None);
}
