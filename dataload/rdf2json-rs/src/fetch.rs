//! Input acquisition and RDF format detection, ported from the Java
//! `OntologyGraph.parseRDF` / `getURL` logic.

use std::io::Read;
use std::time::{SystemTime, UNIX_EPOCH};

use oxrdfio::RdfFormat;

use crate::error::Rdf2JsonError;

pub struct SourceData {
    pub bytes: Vec<u8>,
    pub effective_url: String,
    pub content_type: Option<String>,
    pub timestamp_millis: i64,
}

/// Acquire the bytes for `url`, honouring `--loadLocalFiles`, a predownloaded
/// path, `file://` URLs and HTTP(S) downloads, in the same precedence order as
/// the Java implementation.
pub fn fetch_source(
    url: &str,
    load_local_files: bool,
    base_path: Option<&str>,
    downloaded_path: Option<&str>,
) -> Result<SourceData, Rdf2JsonError> {
    // 1. Local files (test mode).
    if load_local_files && !url.contains("://") {
        let mut resolved = url.to_string();
        if let Some(bp) = base_path {
            if !url.starts_with('/') {
                resolved = format!("{bp}/{url}");
            }
        }
        let bytes = std::fs::read(&resolved)
            .map_err(|e| Rdf2JsonError::Io(std::io::Error::new(e.kind(), format!("{resolved}: {e}"))))?;
        let ts = file_mtime_millis(&resolved);
        return Ok(SourceData {
            bytes,
            effective_url: url.to_string(),
            content_type: None,
            timestamp_millis: ts,
        });
    }

    // 2. Predownloaded file + sidecar .mimetype.
    if let Some(dp) = downloaded_path {
        let existing = format!("{dp}/{}", url_to_filename(url));
        if let Ok(bytes) = std::fs::read(&existing) {
            let content_type = std::fs::read_to_string(format!("{existing}.mimetype")).ok();
            let ts = file_mtime_millis(&existing);
            return Ok(SourceData {
                bytes,
                effective_url: url.to_string(),
                content_type,
                timestamp_millis: ts,
            });
        }
    }

    // 3. Download (or file:// URL).
    let ts = now_millis();
    if let Some(path) = url.strip_prefix("file://") {
        let bytes = std::fs::read(path)?;
        return Ok(SourceData {
            bytes,
            effective_url: url.to_string(),
            content_type: None,
            timestamp_millis: ts,
        });
    }
    if !url.contains("://") {
        // Treat as a local path (Java converts to file:// URL).
        let bytes = std::fs::read(url)?;
        return Ok(SourceData {
            bytes,
            effective_url: url.to_string(),
            content_type: None,
            timestamp_millis: ts,
        });
    }

    let resp = ureq::get(url)
        .set("Accept", "application/rdf+xml, text/turtle, text/n3")
        .call()
        .map_err(|e| Rdf2JsonError::Parse(format!("HTTP request failed for {url}: {e}")))?;
    let content_type = resp.header("Content-Type").map(|s| s.to_string());
    let mut bytes = Vec::new();
    resp.into_reader().read_to_end(&mut bytes)?;
    Ok(SourceData {
        bytes,
        effective_url: url.to_string(),
        content_type,
        timestamp_millis: ts,
    })
}

fn url_to_filename(url: &str) -> String {
    url.chars()
        .map(|c| {
            if c.is_ascii_alphanumeric() || c == '.' || c == '-' {
                c
            } else {
                '_'
            }
        })
        .collect()
}

pub fn gunzip(bytes: &[u8]) -> Result<Vec<u8>, Rdf2JsonError> {
    let mut decoder = flate2::read::GzDecoder::new(bytes);
    let mut out = Vec::new();
    decoder.read_to_end(&mut out)?;
    Ok(out)
}

/// Detect an error XML payload returned in place of an ontology (the Java code
/// checks the first 1KB for `<Error>` patterns).
pub fn detect_error_xml(bytes: &[u8], url: &str) -> Result<(), Rdf2JsonError> {
    let n = bytes.len().min(1024);
    let preview = String::from_utf8_lossy(&bytes[..n]);
    let preview = preview.trim();
    if preview.starts_with("<Error>") || (preview.starts_with("<?xml") && preview.contains("<Error>"))
    {
        return Err(Rdf2JsonError::Parse(format!(
            "Received error XML response instead of valid ontology for URL: {url}"
        )));
    }
    Ok(())
}

/// Determine the RDF syntax, mirroring Jena's content-type / filename logic with
/// a default of RDF/XML.
pub fn determine_format(url: &str, content_type: Option<&str>) -> RdfFormat {
    if let Some(ct) = content_type {
        // "text/turtle; charset=..." -> "text/turtle"
        let ct = ct.split(';').next().unwrap_or("").trim();
        // text/plain is deliberately NOT treated as Turtle (many OBO files are
        // served as text/plain but are actually RDF/XML).
        if ct != "text/plain" {
            if let Some(fmt) = format_from_media_type(ct) {
                return fmt;
            }
        }
    }
    format_from_filename(url).unwrap_or(RdfFormat::RdfXml)
}

fn format_from_media_type(ct: &str) -> Option<RdfFormat> {
    match ct {
        "application/rdf+xml" | "application/xml" | "text/xml" => Some(RdfFormat::RdfXml),
        "text/turtle" | "application/x-turtle" => Some(RdfFormat::Turtle),
        "application/n-triples" => Some(RdfFormat::NTriples),
        "application/n-quads" => Some(RdfFormat::NQuads),
        "application/trig" => Some(RdfFormat::TriG),
        "text/n3" | "text/rdf+n3" => Some(RdfFormat::N3),
        "application/ld+json" | "application/json" => Some(RdfFormat::JsonLd {
            profile: oxrdfio::JsonLdProfileSet::empty(),
        }),
        _ => None,
    }
}

fn format_from_filename(url: &str) -> Option<RdfFormat> {
    let lower = url.to_ascii_lowercase();
    let ext = lower.rsplit('.').next().unwrap_or("");
    match ext {
        "rdf" | "owl" | "xml" | "rdfs" | "rdfxml" => Some(RdfFormat::RdfXml),
        "ttl" => Some(RdfFormat::Turtle),
        "nt" => Some(RdfFormat::NTriples),
        "nq" => Some(RdfFormat::NQuads),
        "n3" => Some(RdfFormat::N3),
        "trig" => Some(RdfFormat::TriG),
        "jsonld" | "json" => Some(RdfFormat::JsonLd {
            profile: oxrdfio::JsonLdProfileSet::empty(),
        }),
        _ => None,
    }
}

fn now_millis() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

fn file_mtime_millis(path: &str) -> i64 {
    std::fs::metadata(path)
        .and_then(|m| m.modified())
        .ok()
        .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

/// `java.time.LocalDateTime.now().toString()` equivalent — only emitted when
/// `--noDates` is not set, and never compared by tests.
pub fn now_local_datetime_string() -> String {
    // Non-reproducible runtime metadata; format kept simple.
    let secs = now_millis() / 1000;
    format!("{secs}")
}

/// `new java.util.Date(millis).toString()` equivalent — only emitted when
/// `--noDates` is not set, and never compared by tests.
pub fn date_to_string(millis: i64) -> String {
    format!("{millis}")
}
