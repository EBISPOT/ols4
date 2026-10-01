#![allow(dead_code)]
use serde_json::Value;
use std::{
    fs,
    path::{Path, PathBuf},
    process::{Command, Output},
    sync::atomic::{AtomicUsize, Ordering},
};
static NEXT: AtomicUsize = AtomicUsize::new(0);
pub struct Scratch(pub PathBuf);
impl Scratch {
    pub fn new() -> Self {
        let dir = std::env::temp_dir().join(format!(
            "ols-link-contract-{}-{}",
            std::process::id(),
            NEXT.fetch_add(1, Ordering::Relaxed)
        ));
        fs::create_dir(&dir).unwrap();
        Self(dir)
    }
    pub fn write(&self, name: &str, text: &str) -> PathBuf {
        let path = self.0.join(name);
        fs::write(&path, text).unwrap();
        path
    }
}
impl Drop for Scratch {
    fn drop(&mut self) {
        let _ = fs::remove_dir_all(&self.0);
    }
}
pub fn success(output: Output) {
    assert!(
        output.status.success(),
        "stdout: {}\nstderr: {}",
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
}
pub fn read(path: &Path) -> Value {
    serde_json::from_slice(&fs::read(path).unwrap()).unwrap()
}
pub fn manifest(bin: &str, dir: &Scratch) -> Value {
    let a = dir.write("a.json", A);
    let b = dir.write("b.json", B);
    let output = dir.0.join("manifest.json");
    // Repeating the exact file must not duplicate entity provenance.
    success(
        Command::new(bin)
            .args([
                "--input",
                &format!("{}, {},{}", a.display(), b.display(), b.display()),
                "--output",
            ])
            .arg(&output)
            .output()
            .unwrap(),
    );
    read(&output)
}
pub const A: &str = r#"{"ontologies":[{"ontologyId":"a","iri":"https://example.test/a","preferredPrefix":"A","baseUri":["https://example.test/A_"],"classes":[
{"iri":"https://example.test/A_1","type":["class"],"label":{"type":["literal"],"value":"local label"},"curie":{"type":["literal"],"value":"A:1"},"shortForm":{"type":["literal"],"value":"A_1"},"references":["https://example.test/B_1","B:1","https://example.test/missing","EXT:42","GOLOCAL:7","https://orcid.org/0000-0000-0000-0001","https://orcid.org/0000-0000-0000-0002","https://example.test/A_1","http://www.w3.org/2002/07/owl#Thing"],"relatedTo+https://example.test/B_p":{"type":["related"],"value":"https://example.test/B_i"},"evidence":{"type":["reification"],"value":"https://example.test/B_1","axioms":[{"type":["axiom"],"https://example.test/evidence":{"type":["literal"],"value":"manual"}}]}},
{"iri":"https://example.test/B_1","type":["class"],"label":{"type":["literal"],"value":"imported label"},"curie":{"type":["literal"],"value":"B_1"},"shortForm":{"type":["literal"],"value":"B_1"}}],"properties":[],"individuals":[]}]}"#;
pub const B: &str = r#"{"ontologies":[{"ontologyId":"b","iri":"https://example.test/b","preferredPrefix":"B","baseUri":["https://example.test/B_"],"classes":[{"iri":"https://example.test/B_1","type":["class"],"label":{"type":["literal"],"lang":"fr","value":"canonical label"},"curie":{"type":["literal"],"value":"B:1"},"shortForm":{"type":["literal"],"value":"B_1"},"isObsolete":true}],"properties":[{"iri":"https://example.test/B_p","type":["property","objectProperty"],"label":{"type":["literal"],"value":"relation"}}],"individuals":[{"iri":"https://example.test/B_i","type":["individual"],"label":{"type":["literal"],"value":"instance"}}]}]}"#;
