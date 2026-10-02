#[path = "../../tests/support.rs"]
mod support;
use serde_json::{Value, json};
use std::{
    io::{Read, Write},
    net::TcpListener,
    process::Command,
    thread,
};
use support::*;
fn definition(id: &str, label: &str, kind: &str, defining: bool) -> Value {
    json!({"ontologyId": id,"entityTypes":[kind],"isDefiningOntology":defining,"label":{"type":["literal"],"value":label},"curie":{"type":["literal"],"value":"B:1"},"isObsolete":false})
}
fn fixture_manifest() -> Value {
    let mut m = json!({"ontologyIdToImportedOntologyIds":{"a":["b"]},"ontologyIdToImportingOntologyIds":{"b":["a"]},"preferredPrefixToOntologyIds":{"B":["b"]},"ontologyIdToBaseUris":{"b":["https://example.test/B_"]},"iriToDefinitions":{}});
    for (iri, kind, label, imported) in [
        ("https://example.test/B_1", "class", "canonical label", true),
        ("https://example.test/B_p", "property", "relation", false),
        ("https://example.test/B_i", "individual", "instance", false),
        ("https://example.test/A_1", "class", "local label", false),
    ] {
        let owner = if iri.ends_with("A_1") { "a" } else { "b" };
        let d = definition(owner, label, kind, true);
        let mut defs = vec![d.clone()];
        let mut local = json!({});
        local[owner] = d.clone();
        if imported {
            let imp = definition("a", "imported label", kind, false);
            defs.insert(0, imp.clone());
            local["a"] = imp;
        }
        m["iriToDefinitions"][iri] = json!({"definitions":defs,"definingDefinitions":[d],"definingOntologyIds":[owner],"ontologyIdToDefinitions":local});
    }
    m
}
#[test]
fn links_known_iris_curies_property_names_and_local_external_sources() {
    let dir = Scratch::new();
    let input = dir.write("input.json", A);
    let manifest = dir.write("manifest.json", &fixture_manifest().to_string());
    let xrefs = dir.write("db-xrefs.yaml", "- database: GOLOCAL\n  entity_types:\n    - url_syntax: https://go.example.test/[example_id]\n");
    let orcid = dir.write("orcid.json", r#"{"0000-0000-0000-0001":"Test Person"}"#);
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let url = format!("http://{}/registry.json", listener.local_addr().unwrap());
    let server = thread::spawn(move || {
        for _ in 0..2 {
            let (mut stream, _) = listener.accept().unwrap();
            let mut request = [0; 4096];
            stream.read(&mut request).unwrap();
            let body = r#"{"ext":{"preferred_prefix":"EXT","uri_format":"https://external.example.test/$1","pattern":"^[0-9]+$"}}"#;
            write!(
                stream,
                "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                body.len(),
                body
            )
            .unwrap();
        }
    });
    let output = dir.0.join("linked.json");
    success(
        Command::new(env!("CARGO_BIN_EXE_ols_link"))
            .arg("--manifest")
            .arg(&manifest)
            .arg("--input")
            .arg(input)
            .arg("--output")
            .arg(&output)
            .env("OLS_TEST_BIOREGISTRY_URL", &url)
            .env("OLS_TEST_DB_XREFS", &xrefs)
            .env("OLS_ORCID_NAME_FIXTURE", &orcid)
            .output()
            .unwrap(),
    );
    let input_b = dir.write("b.json", B);
    let output_b = dir.0.join("b-linked.json");
    success(
        Command::new(env!("CARGO_BIN_EXE_ols_link"))
            .arg("--manifest")
            .arg(&manifest)
            .arg("--input")
            .arg(input_b)
            .arg("--output")
            .arg(&output_b)
            .env("OLS_TEST_BIOREGISTRY_URL", &url)
            .env("OLS_TEST_DB_XREFS", &xrefs)
            .env("OLS_ORCID_NAME_FIXTURE", &orcid)
            .output()
            .unwrap(),
    );
    server.join().unwrap();
    let b = read(&output_b);
    assert_eq!(b["ontologies"][0]["exportsTo"], json!(["a"]));
    for collection in ["properties", "individuals"] {
        assert_eq!(
            b["ontologies"][0][collection][0]["isDefiningOntology"],
            true
        );
        assert_eq!(b["ontologies"][0][collection][0]["definedBy"], json!(["b"]));
    }
    let v = read(&output);
    let o = &v["ontologies"][0];
    let e = &o["classes"][0];
    let links = &e["linkedEntities"];
    assert_eq!(o["importsFrom"], json!(["b"]));
    assert_eq!(e["isDefiningOntology"], true);
    assert_eq!(
        links["https://example.test/B_1"]["label"]["value"],
        "canonical label"
    );
    assert_eq!(links["https://example.test/B_1"]["numAppearsIn"], 2);
    assert_eq!(
        links["https://example.test/B_1"]["hasLocalDefinition"],
        true
    );
    assert_eq!(links["B:1"]["iri"], "https://example.test/B_1");
    assert_eq!(
        links["https://example.test/B_p"]["type"],
        json!(["property"])
    );
    assert_eq!(
        links["https://example.test/B_i"]["type"],
        json!(["individual"])
    );
    assert_eq!(links["EXT:42"]["url"], "https://external.example.test/42");
    assert_eq!(links["GOLOCAL:7"]["url"], "https://go.example.test/7");
    assert_eq!(
        links["https://orcid.org/0000-0000-0000-0001"]["label"]["value"],
        "Test Person"
    );
    for absent in [
        "https://example.test/missing",
        "https://example.test/A_1",
        "http://www.w3.org/2002/07/owl#Thing",
        "https://orcid.org/0000-0000-0000-0002",
    ] {
        assert!(links.get(absent).is_none(), "unexpected link: {absent}");
    }
    assert_eq!(
        e["linksTo"],
        json!([
            "https://example.test/B_1",
            "https://example.test/B_i",
            "https://example.test/B_p",
            "https://orcid.org/0000-0000-0000-0001"
        ])
    );
    let imported = &o["classes"][1];
    assert_eq!(imported["curie"]["value"], "B:1");
    assert_eq!(imported["shortForm"]["value"], "B_1");
    assert_eq!(imported["definedBy"], json!(["b"]));
    assert_eq!(imported["appearsIn"], json!(["a", "b"]));
    assert_eq!(imported["isDefiningOntology"], false);
    assert_eq!(
        e["evidence"]["axioms"][0]["https://example.test/evidence"]["value"],
        "manual"
    );
}
#[test]
fn invalid_manifest_is_a_cli_failure() {
    let dir = Scratch::new();
    let manifest = dir.write("bad.json", "invalid");
    let output = dir.0.join("linked.json");
    let result = Command::new(env!("CARGO_BIN_EXE_ols_link"))
        .arg("--manifest")
        .arg(manifest)
        .args(["--input", "unused.json", "--output"])
        .arg(&output)
        .output()
        .unwrap();
    assert!(!result.status.success());
    assert!(!output.exists());
    assert!(String::from_utf8_lossy(&result.stderr).contains("Failed to link ontology"));
}
