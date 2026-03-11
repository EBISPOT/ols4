use std::collections::BTreeMap;
use std::fs::File;
use std::io::{BufReader, BufWriter, Write};

use clap::Parser;
use ols_shared::streaming::read_value;
use ols_shared::DefinedFields;
use serde_json::{Map, Value};
use struson::reader::{JsonReader, JsonStreamReader};

/// JSON to Solr JSONL converter for OLS4
#[derive(Parser, Debug)]
#[command(name = "ols_json2solr")]
#[command(about = "Convert OLS JSON to Solr JSONL format")]
struct Args {
    /// Ontology ID to process (optional, processes all if not specified)
    #[arg(long)]
    ontology_id: Option<String>,

    /// Input JSON file path
    #[arg(long)]
    input: String,

    /// Output JSONL directory path
    #[arg(long = "outDir")]
    out_dir: String,

    /// Maximum number of rows per output file (-1 for unlimited)
    #[arg(long = "maxRowsPerFile", default_value = "-1")]
    max_rows_per_file: i32,
}

fn main() {
    if let Err(e) = run() {
        eprintln!("ERROR: Failed to convert JSON to Solr JSONL");
        eprintln!("{}", e);
        std::process::exit(1);
    }
}

fn run() -> Result<(), Box<dyn std::error::Error>> {
    let args = Args::parse();

    // Create converter and run
    let converter = SolrConverter::new(
        args.ontology_id,
        args.input,
        args.out_dir,
        args.max_rows_per_file,
    )?;
    converter.convert()?;

    Ok(())
}

/// A rotating writer that creates numbered output files when max rows is reached
struct RotatingWriter {
    base_path: String,
    entity_type: String,
    max_rows_per_file: i32,
    current_file_index: u32,
    current_row_count: i32,
    current_writer: Option<BufWriter<File>>,
}

impl RotatingWriter {
    fn new(
        out_path: &str,
        entity_type: &str,
        max_rows_per_file: i32,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        let mut writer = Self {
            base_path: out_path.to_string(),
            entity_type: entity_type.to_string(),
            max_rows_per_file,
            current_file_index: 0,
            current_row_count: 0,
            current_writer: None,
        };
        writer.open_next_file()?;
        Ok(writer)
    }

    fn open_next_file(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        // Close current writer if exists
        if let Some(ref mut w) = self.current_writer {
            w.flush()?;
        }

        let filename = if self.max_rows_per_file == -1 {
            format!("{}/{}.jsonl", self.base_path, self.entity_type)
        } else {
            format!(
                "{}/{}_{:04}.jsonl",
                self.base_path, self.entity_type, self.current_file_index
            )
        };

        let file = File::create(&filename)?;
        self.current_writer = Some(BufWriter::new(file));
        self.current_row_count = 0;
        self.current_file_index += 1;
        Ok(())
    }

    fn println(&mut self, line: &str) -> Result<(), Box<dyn std::error::Error>> {
        if self.max_rows_per_file != -1 && self.current_row_count >= self.max_rows_per_file {
            self.open_next_file()?;
        }
        if let Some(ref mut w) = self.current_writer {
            writeln!(w, "{}", line)?;
        }
        self.current_row_count += 1;
        Ok(())
    }

    fn close(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        if let Some(ref mut w) = self.current_writer {
            w.flush()?;
        }
        self.current_writer = None;
        Ok(())
    }
}

struct SolrConverter {
    ontology_id: Option<String>,
    input_file_path: String,
    output_file_path: String,
    max_rows_per_file: i32,
}

impl SolrConverter {
    fn new(
        ontology_id: Option<String>,
        input_file_path: String,
        output_file_path: String,
        max_rows_per_file: i32,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        // Create output directory
        std::fs::create_dir_all(&output_file_path)?;

        Ok(Self {
            ontology_id,
            input_file_path,
            output_file_path,
            max_rows_per_file,
        })
    }

    fn convert(self) -> Result<(), Box<dyn std::error::Error>> {
        eprintln!("Starting json2solr processing...");
        eprintln!("Input file: {}", self.input_file_path);
        eprintln!("Output directory: {}", self.output_file_path);

        // First pass: count total ontologies for progress reporting
        let total_ontologies = self.count_ontologies()?;
        eprintln!("Found {} ontologies to process", total_ontologies);

        // Create writers once before processing any ontologies
        let mut ontologies_writer =
            RotatingWriter::new(&self.output_file_path, "ontologies", self.max_rows_per_file)?;
        let mut classes_writer =
            RotatingWriter::new(&self.output_file_path, "classes", self.max_rows_per_file)?;
        let mut properties_writer =
            RotatingWriter::new(&self.output_file_path, "properties", self.max_rows_per_file)?;
        let mut individuals_writer =
            RotatingWriter::new(&self.output_file_path, "individuals", self.max_rows_per_file)?;
        let mut autocomplete_writer =
            RotatingWriter::new(&self.output_file_path, "autocomplete", self.max_rows_per_file)?;

        let input_file = File::open(&self.input_file_path)?;
        let reader = BufReader::with_capacity(256 * 1024, input_file);
        let mut json = JsonStreamReader::new(reader);

        json.begin_object()?;

        let mut processed_ontologies = 0;

        while json.has_next()? {
            let name = json.next_name_owned()?;

            if name == "ontologies" {
                json.begin_array()?;

                while json.has_next()? {
                    processed_ontologies += 1;

                    self.process_ontology_streaming(
                        &mut json,
                        &mut ontologies_writer,
                        &mut classes_writer,
                        &mut properties_writer,
                        &mut individuals_writer,
                        &mut autocomplete_writer,
                        processed_ontologies,
                        total_ontologies,
                    )?;
                }

                json.end_array()?;
            } else {
                json.skip_value()?;
            }
        }

        json.end_object()?;

        // Close all writers
        ontologies_writer.close()?;
        classes_writer.close()?;
        properties_writer.close()?;
        individuals_writer.close()?;
        autocomplete_writer.close()?;

        eprintln!("json2solr processing completed successfully!");
        eprintln!("Processed {} ontologies total", processed_ontologies);

        Ok(())
    }

    fn count_ontologies(&self) -> Result<usize, Box<dyn std::error::Error>> {
        let file = File::open(&self.input_file_path)?;
        let reader = BufReader::with_capacity(256 * 1024, file);
        let mut json = JsonStreamReader::new(reader);

        let mut count = 0;

        json.begin_object()?;
        while json.has_next()? {
            let name = json.next_name_owned()?;
            if name == "ontologies" {
                json.begin_array()?;
                while json.has_next()? {
                    json.skip_value()?;
                    count += 1;
                }
                json.end_array()?;
            } else {
                json.skip_value()?;
            }
        }
        json.end_object()?;

        Ok(count)
    }

    #[allow(clippy::too_many_arguments)]
    fn process_ontology_streaming(
        &self,
        json: &mut JsonStreamReader<BufReader<File>>,
        ontologies_writer: &mut RotatingWriter,
        classes_writer: &mut RotatingWriter,
        properties_writer: &mut RotatingWriter,
        individuals_writer: &mut RotatingWriter,
        autocomplete_writer: &mut RotatingWriter,
        processed_count: usize,
        total_count: usize,
    ) -> Result<(), Box<dyn std::error::Error>> {
        json.begin_object()?;

        let mut ontology_id: Option<String> = None;
        let mut ontology: Map<String, Value> = Map::new();
        let mut should_process = false;

        while json.has_next()? {
            let key = json.next_name_owned()?;

            match key.as_str() {
                "ontologyId" => {
                    let value: Value = read_value(json);
                    let id = value
                        .as_str()
                        .ok_or("Expected ontologyId to be a string")?
                        .to_string();
                    ontology_id = Some(id.clone());
                    ontology.insert("ontologyId".to_string(), value);

                    // Check if we should process this ontology
                    should_process = self.ontology_id.is_none()
                        || self.ontology_id.as_ref() == Some(&id);

                    // Report progress
                    let progress_percent = (processed_count as f64 / total_count as f64) * 100.0;
                    let action = if should_process {
                        "Processing"
                    } else {
                        "Skipping"
                    };
                    eprintln!(
                        "[{}/{}] ({:.1}%) {} ontology: {}",
                        processed_count, total_count, progress_percent, action, id
                    );

                    if !should_process {
                        // Skip the rest of this ontology
                        while json.has_next()? {
                            json.next_name_owned()?;
                            json.skip_value()?;
                        }
                        json.end_object()?;
                        return Ok(());
                    }
                }
                "classes" if should_process => {
                    let ont_id = ontology_id
                        .as_ref()
                        .ok_or("classes found before ontologyId")?;

                    json.begin_array()?;

                    let mut class_count = 0;
                    while json.has_next()? {
                        class_count += 1;

                        let class_value: Value = read_value(json);
                        if let Some(class_obj) = class_value.as_object() {
                            self.process_entity(
                                ont_id,
                                "class",
                                class_obj,
                                classes_writer,
                                autocomplete_writer,
                            )?;
                        }

                        if class_count % 1000 == 0 {
                            eprintln!("  - Processed {} classes...", class_count);
                        }
                    }

                    json.end_array()?;
                }
                "properties" if should_process => {
                    let ont_id = ontology_id
                        .as_ref()
                        .ok_or("properties found before ontologyId")?;

                    json.begin_array()?;

                    let mut property_count = 0;
                    while json.has_next()? {
                        property_count += 1;

                        let property_value: Value = read_value(json);
                        if let Some(property_obj) = property_value.as_object() {
                            self.process_entity(
                                ont_id,
                                "property",
                                property_obj,
                                properties_writer,
                                autocomplete_writer,
                            )?;
                        }
                    }

                    json.end_array()?;

                    if property_count > 0 {
                        eprintln!("  - Processed {} properties", property_count);
                    }
                }
                "individuals" if should_process => {
                    let ont_id = ontology_id
                        .as_ref()
                        .ok_or("individuals found before ontologyId")?;

                    json.begin_array()?;

                    let mut individual_count = 0;
                    while json.has_next()? {
                        individual_count += 1;

                        let individual_value: Value = read_value(json);
                        if let Some(individual_obj) = individual_value.as_object() {
                            self.process_entity(
                                ont_id,
                                "individual",
                                individual_obj,
                                individuals_writer,
                                autocomplete_writer,
                            )?;
                        }
                    }

                    json.end_array()?;

                    if individual_count > 0 {
                        eprintln!("  - Processed {} individuals", individual_count);
                    }
                }
                _ => {
                    // Store other ontology properties
                    let value: Value = read_value(json);
                    ontology.insert(key, value);
                }
            }
        }

        json.end_object()?;

        // Write ontology document if we processed this ontology
        if should_process {
            if let Some(ref ont_id) = ontology_id {
                // Create flattened ontology using BTreeMap for sorted keys (like Java's TreeMap)
                let mut flattened_ontology: BTreeMap<String, Value> = BTreeMap::new();

                // Create ontology JSON object without classes/properties/individuals
                // Java uses TreeMap here (sorted alphabetically) unlike entities which preserve order
                let ontology_json_obj: BTreeMap<String, Value> = ontology
                    .iter()
                    .filter(|(k, _)| {
                        k.as_str() != "classes"
                            && k.as_str() != "properties"
                            && k.as_str() != "individuals"
                    })
                    .map(|(k, v)| (k.clone(), v.clone()))
                    .collect();

                flattened_ontology.insert(
                    "_json".to_string(),
                    Value::String(serde_json::to_string(&ontology_json_obj)?),
                );

                let iri = ontology
                    .get("iri")
                    .and_then(|v| v.as_str())
                    .unwrap_or("");
                flattened_ontology.insert(
                    "id".to_string(),
                    Value::String(format!("{}+ontology+{}", ont_id, iri)),
                );

                flatten_properties(&ontology, &mut flattened_ontology);

                ontologies_writer.println(&serde_json::to_string(&flattened_ontology)?)?;
            }
        }

        Ok(())
    }

    fn process_entity(
        &self,
        ontology_id: &str,
        entity_type: &str,
        entity: &Map<String, Value>,
        entity_writer: &mut RotatingWriter,
        autocomplete_writer: &mut RotatingWriter,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let iri = entity.get("iri").and_then(|v| v.as_str()).unwrap_or("");

        let entity_id = format!("{}+{}+{}", ontology_id, entity_type, iri);

        // Use BTreeMap for sorted keys (like Java's TreeMap)
        let mut flattened_entity: BTreeMap<String, Value> = BTreeMap::new();
        flattened_entity.insert("id".to_string(), Value::String(entity_id.clone()));

        flatten_properties(entity, &mut flattened_entity);

        // Extract curatedFrom entries into searchable Solr fields
        if let Some(Value::Array(curated_from)) = entity.get("curatedFrom") {
            let mut curated_texts: Vec<Value> = Vec::new();
            let mut curated_categories: Vec<Value> = Vec::new();

            for entry in curated_from {
                if let Some(obj) = entry.as_object() {
                    if let Some(text) = obj.get("text").and_then(|v| v.as_str()) {
                        curated_texts.push(Value::String(text.to_string()));
                    }
                    if let Some(cats) = obj.get("subjectCategories").and_then(|v| v.as_array()) {
                        for cat in cats {
                            if let Some(c) = cat.as_str() {
                                curated_categories.push(Value::String(c.to_string()));
                            }
                        }
                    }
                }
            }

            if !curated_texts.is_empty() {
                flattened_entity.insert("curatedFrom".to_string(), Value::Array(curated_texts));
            }
            if !curated_categories.is_empty() {
                flattened_entity.insert("curatedFromSubjectCategories".to_string(), Value::Array(curated_categories));
            }
        }

        // Index curatedFromSources for faceting (already a string array from the linker)
        if let Some(Value::Array(sources)) = entity.get("curatedFromSources") {
            if !sources.is_empty() {
                flattened_entity.insert("curatedFromSources".to_string(), Value::Array(sources.clone()));
            }
        }

        // Store original JSON (without embeddings in it), preserving original key order
        // Java's Gson preserves LinkedHashMap insertion order from JSON parsing
        let entity_for_json: Map<String, Value> = entity
            .iter()
            .filter(|(k, _)| k.as_str() != "embeddings")
            .map(|(k, v)| (k.clone(), v.clone()))
            .collect();
        
        flattened_entity.insert(
            "_json".to_string(),
            Value::String(serde_json::to_string(&entity_for_json)?),
        );

        entity_writer.println(&serde_json::to_string(&flattened_entity)?)?;

        write_autocomplete_entries(ontology_id, &entity_id, &flattened_entity, autocomplete_writer)?;

        Ok(())
    }

}

fn flatten_properties(
    properties: &Map<String, Value>,
    flattened: &mut BTreeMap<String, Value>,
) {
    for (k, v) in properties {
        let discarded = discard_metadata(v);
        if discarded.is_none() {
            continue;
        }
        let discarded = discarded.unwrap();

        // Replace colons with double underscores
        let key = k.replace(':', "__");

        match discarded {
            Value::Array(arr) => {
                let flattened_list: Vec<Value> = arr
                    .iter()
                    .filter_map(|entry| discard_metadata(entry))
                    .map(|obj| obj_to_value(&obj))
                    .collect();
                flattened.insert(key, Value::Array(flattened_list));
            }
            _ => {
                flattened.insert(key, obj_to_value(&discarded));
            }
        }
    }
}

/// There are 5 cases when the object can be a Map {} instead of a literal.
///
///  (1) It's a literal with type information { datatype: ..., value: ... }
///
///  (2) It's a class expression
///
///  (3) It's a localization, which is a specific case of (1) where a
///      language and localized value are provided.
///
///  (4) It's reification { type: reification|related, ....,  value: ... }
///
///  (5) it's some random json object from the ontology config
///
/// In the case of (1), we discard the datatype and keep the value
///
/// In the case of (2), we don't store anything in solr fields. Class
/// expressions should already have been evaluated into separate "related"
/// fields by the RelatedAnnotator in rdf2json.
///
/// In the case of (3), we create a Solr document for each language (see
/// above), and the language is passed into this function so we know which
/// language's strings to keep.
///
/// In the case of (4), we discard any metadata (in Neo4j the metadata is
/// preserved for edges, but in Solr we don't care about it).
///
/// In the case of (5) we discard it in solr because json objects won't be
/// querable anyway.
fn discard_metadata(obj: &Value) -> Option<Value> {
    match obj {
        Value::Object(dict) => {
            let type_val = dict.get("type");

            if type_val.is_none() {
                // (2) class expression or json junk from the ontology config
                return None;
            }

            let type_val = type_val.unwrap();
            if !type_val.is_array() {
                // (2) class expression or json junk from the ontology config
                return None;
            }

            let types: Vec<&str> = type_val
                .as_array()
                .unwrap()
                .iter()
                .filter_map(|v| v.as_str())
                .collect();

            if types.contains(&"literal") {
                // (1) typed literal
                dict.get("value").and_then(|v| discard_metadata(v))
            } else if types.contains(&"reification") || types.contains(&"related") {
                // (4) reification
                dict.get("value").and_then(|v| discard_metadata(v))
            } else if types.contains(&"datatype") {
                None
            } else {
                // Unknown type - discard
                None
            }
        }
        _ => Some(obj.clone()),
    }
}

fn obj_to_value(obj: &Value) -> Value {
    match obj {
        Value::String(_) => obj.clone(),
        _ => Value::String(obj_to_string(obj)),
    }
}

/// Convert a value to string, matching Java's gson.toJson behavior
fn obj_to_string(obj: &Value) -> String {
    match obj {
        Value::String(s) => s.clone(),
        Value::Number(n) => {
            // Match Java's Gson behavior: integers stay as integers, floats have decimals
            if let Some(i) = n.as_i64() {
                i.to_string()
            } else if let Some(u) = n.as_u64() {
                u.to_string()
            } else if let Some(f) = n.as_f64() {
                // Java's Gson outputs floats with .0 for whole numbers
                if f.fract() == 0.0 {
                    format!("{}.0", f as i64)
                } else {
                    f.to_string()
                }
            } else {
                n.to_string()
            }
        }
        Value::Bool(b) => b.to_string(),
        Value::Null => "null".to_string(),
        _ => serde_json::to_string(obj).unwrap_or_default(),
    }
}

fn write_autocomplete_entries(
    ontology_id: &str,
    entity_id: &str,
    flattened_entity: &BTreeMap<String, Value>,
    autocomplete_writer: &mut RotatingWriter,
) -> Result<(), Box<dyn std::error::Error>> {
    let label_field = DefinedFields::Label.text();

    if let Some(labels) = flattened_entity.get(label_field) {
        match labels {
            Value::String(label) => {
                let entry = make_autocomplete_entry(ontology_id, entity_id, label);
                autocomplete_writer.println(&serde_json::to_string(&entry)?)?;
            }
            Value::Array(label_list) => {
                for label_val in label_list {
                    if let Value::String(label) = label_val {
                        let entry = make_autocomplete_entry(ontology_id, entity_id, label);
                        autocomplete_writer.println(&serde_json::to_string(&entry)?)?;
                    }
                }
            }
            _ => {}
        }
    }

    let synonym_field = DefinedFields::Synonym.text();

    if let Some(synonyms) = flattened_entity.get(synonym_field) {
        match synonyms {
            Value::String(synonym) => {
                let entry = make_autocomplete_entry(ontology_id, entity_id, synonym);
                autocomplete_writer.println(&serde_json::to_string(&entry)?)?;
            }
            Value::Array(synonym_list) => {
                for synonym_val in synonym_list {
                    if let Value::String(synonym) = synonym_val {
                        let entry = make_autocomplete_entry(ontology_id, entity_id, synonym);
                        autocomplete_writer.println(&serde_json::to_string(&entry)?)?;
                    }
                }
            }
            _ => {}
        }
    }

    Ok(())
}

fn make_autocomplete_entry(ontology_id: &str, entity_id: &str, label: &str) -> serde_json::Map<String, Value> {
    // Use LinkedHashMap-like behavior (serde_json::Map preserves insertion order)
    // Java uses LinkedHashMap which preserves insertion order
    let mut entry = serde_json::Map::new();
    entry.insert("ontologyId".to_string(), Value::String(ontology_id.to_string()));
    entry.insert("id".to_string(), Value::String(entity_id.to_string()));
    entry.insert("label".to_string(), Value::String(label.to_string()));
    entry
}
