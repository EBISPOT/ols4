use regex::Regex;
use std::collections::BTreeSet;
use std::io::{Read, Write};
use std::sync::LazyLock;

use ols_shared::streaming::read_value;
use serde_json::Value;
use struson::reader::{JsonReader, JsonStreamReader, ValueType};
use struson::writer::{JsonStreamWriter, JsonWriter};

use crate::extract_iri_from_property_name;

// Note: [A-z] matches A-Z, [, \, ], ^, _, `, a-z (ASCII 65-122)
// This matches Java's behavior exactly for both patterns
static CURIE_PATTERN: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"[A-Z]+:[0-9A-z]+").unwrap()
});

static URI_PATTERN: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"[A-z]+://[^\s]+").unwrap()
});

/// Copy JSON from reader to writer while gathering all string values that might be IRIs or CURIEs
pub fn copy_json_gathering_strings<R: Read, W: Write>(
    reader: &mut JsonStreamReader<R>,
    writer: &mut JsonStreamWriter<W>,
    gathered_strings: &mut BTreeSet<String>,
) -> Result<(), Box<dyn std::error::Error>> {
    match reader.peek()? {
        ValueType::Array => {
            copy_array(reader, writer, gathered_strings)?;
        }
        ValueType::Object => {
            copy_object(reader, writer, gathered_strings)?;
        }
        ValueType::String => {
            let s = reader.next_string()?.to_string();
            
            gathered_strings.insert(s.clone());
            
            // Find CURIEs in the string
            for cap in CURIE_PATTERN.find_iter(&s) {
                gathered_strings.insert(cap.as_str().to_string());
            }
            
            // Find URIs in the string
            for cap in URI_PATTERN.find_iter(&s) {
                gathered_strings.insert(cap.as_str().to_string());
            }
            
            writer.string_value(&s)?;
        }
        _ => {
            // For other types (number, bool, null), read and write directly
            let value = read_value(reader);
            write_value(writer, &value)?;
        }
    }
    Ok(())
}

fn copy_array<R: Read, W: Write>(
    reader: &mut JsonStreamReader<R>,
    writer: &mut JsonStreamWriter<W>,
    gathered_strings: &mut BTreeSet<String>,
) -> Result<(), Box<dyn std::error::Error>> {
    reader.begin_array()?;
    writer.begin_array()?;
    
    while reader.has_next()? {
        copy_json_gathering_strings(reader, writer, gathered_strings)?;
    }
    
    reader.end_array()?;
    writer.end_array()?;
    Ok(())
}

fn copy_object<R: Read, W: Write>(
    reader: &mut JsonStreamReader<R>,
    writer: &mut JsonStreamWriter<W>,
    gathered_strings: &mut BTreeSet<String>,
) -> Result<(), Box<dyn std::error::Error>> {
    reader.begin_object()?;
    writer.begin_object()?;
    
    while reader.has_next()? {
        let name = reader.next_name_owned()?;
        
        // Extract IRI from property name and add to gathered strings
        gathered_strings.insert(extract_iri_from_property_name::extract(&name).to_string());
        
        writer.name(&name)?;
        copy_json_gathering_strings(reader, writer, gathered_strings)?;
    }
    
    reader.end_object()?;
    writer.end_object()?;
    Ok(())
}

/// Write a serde_json Value to a struson JsonStreamWriter
pub fn write_value<W: Write>(
    writer: &mut JsonStreamWriter<W>,
    value: &Value,
) -> Result<(), Box<dyn std::error::Error>> {
    match value {
        Value::Null => {
            writer.null_value()?;
        }
        Value::Bool(b) => {
            writer.bool_value(*b)?;
        }
        Value::Number(n) => {
            if let Some(i) = n.as_i64() {
                writer.number_value(i)?;
            } else if let Some(u) = n.as_u64() {
                writer.number_value(u)?;
            } else if let Some(f) = n.as_f64() {
                writer.fp_number_value(f)?;
            }
        }
        Value::String(s) => {
            writer.string_value(s)?;
        }
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
