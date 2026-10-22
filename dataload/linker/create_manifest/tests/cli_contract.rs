#[path = "../../tests/support.rs"]
mod support;
use serde_json::json;
use std::process::Command;
use support::*;
#[test]
fn aggregates_files_with_canonical_ownership_and_property_inventory() {
    let dir = Scratch::new();
    let m = manifest(env!("CARGO_BIN_EXE_ols_create_manifest"), &dir);
    assert_eq!(
        m["ontologyIriToOntologyIds"]["https://example.test/b"],
        json!(["b"])
    );
    assert_eq!(m["preferredPrefixToOntologyIds"]["B"], json!(["b"]));
    assert_eq!(m["ontologyIdToImportedOntologyIds"]["a"], json!(["b"]));
    assert_eq!(m["ontologyIdToImportingOntologyIds"]["b"], json!(["a"]));
    let shared = &m["iriToDefinitions"]["https://example.test/B_1"];
    assert_eq!(shared["definitions"].as_array().unwrap().len(), 2);
    assert_eq!(shared["definingOntologyIds"], json!(["b"]));
    assert_eq!(
        shared["definingDefinitions"][0]["label"]["value"],
        "canonical label"
    );
    assert_eq!(
        shared["ontologyIdToDefinitions"]["a"]["curie"]["value"],
        "B:1"
    );
    assert_eq!(
        shared["ontologyIdToDefinitions"]["a"]["isDefiningOntology"],
        false
    );
    assert_eq!(shared["ontologyIdToDefinitions"]["b"]["isObsolete"], true);
    for (iri, kind) in [
        ("https://example.test/B_p", "PROPERTY"),
        ("https://example.test/B_i", "INDIVIDUAL"),
        ("https://example.test/b", "ONTOLOGY"),
    ] {
        assert_eq!(m["ontologyIdToUriToTypes"]["b"][iri], json!([kind]));
    }
    assert!(
        m["ontologyIdToClassProperties"]["b"]
            .as_array()
            .unwrap()
            .contains(&json!("fr+label"))
    );
    assert_eq!(
        m["ontologyIdToEdgeProperties"]["a"],
        json!(["https://example.test/evidence"])
    );
}
#[test]
fn missing_input_exits_unsuccessfully_without_manifest() {
    let dir = Scratch::new();
    let out = dir.0.join("manifest.json");
    let result = Command::new(env!("CARGO_BIN_EXE_ols_create_manifest"))
        .args(["--input", "does-not-exist.json", "--output"])
        .arg(&out)
        .output()
        .unwrap();
    assert!(!result.status.success());
    assert!(!out.exists());
}
