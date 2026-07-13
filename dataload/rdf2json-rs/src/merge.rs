//! `--mergeOutputWith`: after writing this run's ontologies, carry forward any
//! ontology from a previous run's output that we did not (re)index this time,
//! marking it as a fallback. Mirrors the Java `RDF2JSON` merge step.
//!
//! Both the current output and the previous file are streamed (they can be very
//! large — a single ontology's JSON may be gigabytes), so we never materialise a
//! whole ontology in memory beyond its small scalar fields.

use std::collections::HashSet;
use std::error::Error;
use std::fs::File;
use std::io::{BufReader, BufWriter, Read, Write};

use ols_shared::streaming::{read_value, skip_value};
use serde_json::Value;
use struson::reader::{JsonReader, JsonStreamReader, ValueType};
use struson::writer::{JsonStreamWriter, JsonWriter};

use crate::status;

const FALLBACK_REASON: &str =
    "Latest ontology version is failing to load, using the last successful version instead";

/// Rewrite `output_path` so it also contains the ontologies from `prev_path`
/// that were not successfully (re)indexed this run (each marked `is_fallback`).
///
/// `loaded_ids` are the lowercase ontology ids written this run; `config_ids`
/// are the lowercase ids that were in this run's config (used only to choose
/// between a FALLBACK status — we tried and failed — and a kept SUCCESS status).
pub fn merge_previous_run(
    output_path: &str,
    prev_path: &str,
    loaded_ids: &HashSet<String>,
    config_ids: &HashSet<String>,
) -> Result<(), Box<dyn Error>> {
    let tmp_path = format!("{output_path}.merge.tmp");
    {
        let mut writer = JsonStreamWriter::new(BufWriter::new(File::create(&tmp_path)?));
        writer.begin_object()?;
        writer.name("ontologies")?;
        writer.begin_array()?;

        // 1. Copy every ontology this run produced.
        let mut cur = JsonStreamReader::new(BufReader::new(File::open(output_path)?));
        copy_all_ontologies(&mut cur, &mut writer)?;

        // 2. Carry forward previous ontologies we didn't (re)index this run.
        let mut prev = JsonStreamReader::new(BufReader::new(File::open(prev_path)?));
        carry_forward(&mut prev, &mut writer, loaded_ids, config_ids, output_path)?;

        writer.end_array()?;
        writer.end_object()?;
        writer.finish_document()?;
    }
    std::fs::rename(&tmp_path, output_path)?;
    Ok(())
}

/// Copy all `ontologies[]` objects from a `{ "ontologies": [ ... ] }` document
/// straight through to `writer`'s already-open array.
fn copy_all_ontologies<R: Read, W: Write>(
    reader: &mut JsonStreamReader<R>,
    writer: &mut JsonStreamWriter<W>,
) -> Result<(), Box<dyn Error>> {
    reader.begin_object()?;
    while reader.has_next()? {
        let name = reader.next_name_owned()?;
        if name == "ontologies" {
            reader.begin_array()?;
            while reader.has_next()? {
                copy_value(reader, writer)?;
            }
            reader.end_array()?;
        } else {
            skip_value(reader);
        }
    }
    reader.end_object()?;
    Ok(())
}

/// Stream the previous output and append any ontology whose id is not in
/// `loaded_ids`, injecting `is_fallback`/`fallback_reason` and writing a status.
fn carry_forward<R: Read, W: Write>(
    reader: &mut JsonStreamReader<R>,
    writer: &mut JsonStreamWriter<W>,
    loaded_ids: &HashSet<String>,
    config_ids: &HashSet<String>,
    output_path: &str,
) -> Result<(), Box<dyn Error>> {
    reader.begin_object()?;
    while reader.has_next()? {
        let name = reader.next_name_owned()?;
        if name != "ontologies" {
            skip_value(reader);
            continue;
        }
        reader.begin_array()?;
        while reader.has_next()? {
            carry_one(reader, writer, loaded_ids, config_ids, output_path)?;
        }
        reader.end_array()?;
    }
    reader.end_object()?;
    Ok(())
}

fn carry_one<R: Read, W: Write>(
    reader: &mut JsonStreamReader<R>,
    writer: &mut JsonStreamWriter<W>,
    loaded_ids: &HashSet<String>,
    config_ids: &HashSet<String>,
    output_path: &str,
) -> Result<(), Box<dyn Error>> {
    reader.begin_object()?;
    if !reader.has_next()? {
        reader.end_object()?;
        return Ok(());
    }
    // The linker requires `ontologyId` to be the first key of every ontology.
    let first = reader.next_name_owned()?;
    if first != "ontologyId" {
        return Err("mergeOutputWith does not look like rdf2json output (ontologyId must be first)".into());
    }
    let id = reader.next_string()?.to_string();
    let id_lc = id.to_lowercase();

    if loaded_ids.contains(&id_lc) {
        // Already (re)produced this run — drop the previous version.
        while reader.has_next()? {
            let _ = reader.next_name_owned()?;
            skip_value(reader);
        }
        reader.end_object()?;
        return Ok(());
    }

    // Carry forward, capturing the version for the status file and appending the
    // fallback marker fields after the original members.
    writer.begin_object()?;
    writer.name("ontologyId")?;
    writer.string_value(&id)?;
    let mut version: Option<String> = None;
    while reader.has_next()? {
        let key = reader.next_name_owned()?;
        writer.name(&key)?;
        if key == "version" {
            let v = read_value(reader);
            version = extract_version(&v);
            write_value(writer, &v)?;
        } else {
            copy_value(reader, writer)?;
        }
    }
    writer.name("is_fallback")?;
    writer.bool_value(true)?;
    writer.name("fallback_reason")?;
    writer.string_value(FALLBACK_REASON)?;
    writer.end_object()?;
    reader.end_object()?;

    if config_ids.contains(&id_lc) {
        status::write_fallback(output_path, &id_lc, version.as_deref(), FALLBACK_REASON);
    } else {
        status::write_success(output_path, &id_lc, version.as_deref());
    }
    Ok(())
}

/// Stream-copy one JSON value from `reader` to `writer` without materialising
/// large containers (scalars go through serde_json, which is cheap).
fn copy_value<R: Read, W: Write>(
    reader: &mut JsonStreamReader<R>,
    writer: &mut JsonStreamWriter<W>,
) -> Result<(), Box<dyn Error>> {
    match reader.peek()? {
        ValueType::Array => {
            reader.begin_array()?;
            writer.begin_array()?;
            while reader.has_next()? {
                copy_value(reader, writer)?;
            }
            reader.end_array()?;
            writer.end_array()?;
        }
        ValueType::Object => {
            reader.begin_object()?;
            writer.begin_object()?;
            while reader.has_next()? {
                let name = reader.next_name_owned()?;
                writer.name(&name)?;
                copy_value(reader, writer)?;
            }
            reader.end_object()?;
            writer.end_object()?;
        }
        ValueType::String => {
            let s = reader.next_string()?.to_string();
            writer.string_value(&s)?;
        }
        _ => {
            let v = read_value(reader);
            write_value(writer, &v)?;
        }
    }
    Ok(())
}

fn write_value<W: Write>(
    writer: &mut JsonStreamWriter<W>,
    value: &Value,
) -> Result<(), Box<dyn Error>> {
    match value {
        Value::Null => writer.null_value()?,
        Value::Bool(b) => writer.bool_value(*b)?,
        Value::Number(n) => {
            if let Some(i) = n.as_i64() {
                writer.number_value(i)?;
            } else if let Some(u) = n.as_u64() {
                writer.number_value(u)?;
            } else if let Some(f) = n.as_f64() {
                writer.fp_number_value(f)?;
            }
        }
        Value::String(s) => writer.string_value(s)?,
        Value::Array(arr) => {
            writer.begin_array()?;
            for item in arr {
                write_value(writer, item)?;
            }
            writer.end_array()?;
        }
        Value::Object(obj) => {
            writer.begin_object()?;
            for (k, v) in obj {
                writer.name(k)?;
                write_value(writer, v)?;
            }
            writer.end_object()?;
        }
    }
    Ok(())
}

/// Java extracts the version from a `version` field that is either a string or a
/// `{ "value": "..." }` literal object.
fn extract_version(v: &Value) -> Option<String> {
    match v {
        Value::String(s) => Some(s.clone()),
        Value::Object(o) => o.get("value").and_then(|x| x.as_str()).map(|s| s.to_string()),
        _ => None,
    }
}
