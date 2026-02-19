use clap::Parser;
use std::{
    fs::File, io::BufReader, io::StdoutLock, io::BufWriter, io::stdout
};
use struson::reader::{JsonReader, JsonStreamReader, ValueType};
use tiktoken_rs::{cl100k_base, CoreBPE};
use sha1::Digest;
use std::error::Error;
use std::io::Write;

#[derive(Parser)]
struct Args {
    /// Input linked ontology JSON files
    #[arg(required = true)]
    input_files: Vec<String>,
}

fn compute_sha1(doc:&str) -> String {
    let mut hasher = sha1::Sha1::new();
    hasher.update(doc.as_bytes());
    let result = hasher.finalize();
    format!("{:x}", result)
}

fn get_values(json: &mut JsonStreamReader<BufReader<File>>) -> Vec<String> {
    if json.peek().unwrap() == ValueType::Array {
        let mut ret:Vec<String> = Vec::new();
        json.begin_array().unwrap();
        while json.has_next().unwrap() {
            ret.append(&mut get_values(json));
        }
        json.end_array().unwrap();
        return ret
    } else if json.peek().unwrap() == ValueType::Object {
        let mut ret:Vec<String> = Vec::new();
        json.begin_object().unwrap();
        while json.has_next().unwrap() {
            let name = json.next_name().unwrap();
            if name == "value" {
                ret = get_values(json);
            } else {
                json.skip_value().unwrap();
            }
        }
        json.end_object().unwrap();
        return ret;
    } else if json.peek().unwrap() == ValueType::String {
        let value = json.next_string().unwrap();
        return vec![value];
    } else {
        json.skip_value().unwrap();
        return vec![];
    }
}

fn main() -> Result<(), Box<dyn Error>> {
    let args = Args::parse();

    let stdout = stdout().lock();
    let mut writer = BufWriter::new(stdout);

    let tokenizer = cl100k_base().unwrap();

    let mut total: u64 = 0;
    let mut embedded: u64 = 0;

    writeln!(&mut writer, "pk\tontology_id\tentity_type\tiri\tlabel\thash\ttext_to_embed").unwrap();

    for input_file in &args.input_files {
        eprintln!("Processing file: {}", input_file);

        let file = File::open(input_file)
            .unwrap_or_else(|e| panic!("Failed to open file {}: {}", input_file, e));
        let reader = BufReader::new(file);
        let mut json = JsonStreamReader::new(reader);

        // The linked ontology JSON wraps ontologies in:
        //   { "ontologies": [ { ontologyId, classes, ... }, ... ] }
        json.begin_object().unwrap();

        while json.has_next().unwrap() {
            let top_key = json.next_name().unwrap();
            if top_key == "ontologies" {
                json.begin_array().unwrap();
                while json.has_next().unwrap() {
                    process_ontology_object(&mut json, &tokenizer, &mut writer, &mut total, &mut embedded);
                }
                json.end_array().unwrap();
            } else {
                json.skip_value().unwrap();
            }
        }

        json.end_object().unwrap();
    }

    eprintln!("Total entities seen: {}, entities with isDefiningOntology=true written: {}", total, embedded);

    Ok(())
}

/// Process a single ontology object from within the "ontologies" array.
fn process_ontology_object(
    json: &mut JsonStreamReader<BufReader<File>>,
    tokenizer: &CoreBPE,
    writer: &mut BufWriter<StdoutLock>,
    total: &mut u64,
    embedded: &mut u64,
) {
    json.begin_object().unwrap();

    let mut current_ont_id = String::new();

    while json.has_next().unwrap() {
        let name = json.next_name().unwrap();
        if name == "ontologyId" {
            current_ont_id = json.next_string().unwrap();
            eprintln!("  Ontology: {}", current_ont_id);
        } else if name == "classes" {
            eprintln!("  Processing classes for ontology {}", current_ont_id);
            json.begin_array().unwrap();
            while json.has_next().unwrap() {
                *total += 1;
                *embedded += process_entity("class", &current_ont_id, json, tokenizer, writer);
            }
            json.end_array().unwrap();
        } else if name == "properties" {
            eprintln!("  Processing properties for ontology {}", current_ont_id);
            json.begin_array().unwrap();
            while json.has_next().unwrap() {
                *total += 1;
                *embedded += process_entity("property", &current_ont_id, json, tokenizer, writer);
            }
            json.end_array().unwrap();
        } else if name == "individuals" {
            eprintln!("  Processing individuals for ontology {}", current_ont_id);
            json.begin_array().unwrap();
            while json.has_next().unwrap() {
                *total += 1;
                *embedded += process_entity("individual", &current_ont_id, json, tokenizer, writer);
            }
            json.end_array().unwrap();
        } else {
            json.skip_value().unwrap();
        }
    }
    json.end_object().unwrap();
}

/// Process a single entity from the JSON stream.
/// Returns 1 if the entity was written (isDefiningOntology=true), 0 otherwise.
fn process_entity(
    entity_type:&str,
    ontology_id:&str,
    json:&mut JsonStreamReader<BufReader<File>>,
    tokenizer: &CoreBPE,
    writer: &mut BufWriter<StdoutLock>,
) -> u64 {
    let mut iri:Option<String> = None;
    let mut labels:Vec<String> = Vec::new();
    let mut synonyms:Vec<String> = Vec::new();
    let mut is_defining_ontology = false;

    json.begin_object().unwrap();
    while json.has_next().unwrap() {
        let key = json.next_name().unwrap();
        if key == "iri" {
            iri = Some(json.next_string().unwrap());
        } else if key == "label" {
            labels.append(&mut get_values(json));
        } else if key == "http://www.geneontology.org/formats/oboInOwl#hasExactSynonym" {
            synonyms.append(&mut get_values(json));
        } else if key == "isDefiningOntology" {
            if json.peek().unwrap() == ValueType::Boolean {
                is_defining_ontology = json.next_bool().unwrap();
            } else {
                json.skip_value().unwrap();
            }
        } else {
            json.skip_value().unwrap();
        }
    }
    json.end_object().unwrap();

    // Only embed entities where isDefiningOntology is true
    if !is_defining_ontology {
        return 0;
    }

    let iri_value = iri.unwrap();
    let label_str = labels.iter().next().unwrap_or(&"".to_string()).replace("\t", " ").replace("\n", " ").replace("\r", " ");

    let texts_to_embed: Vec<String> = labels.into_iter().chain(synonyms.into_iter()).collect();

    if texts_to_embed.is_empty() {
        eprintln!("Skipping empty document for {} {} {}", ontology_id, entity_type, &iri_value);
        return 0;
    }

    let mut written: u64 = 0;

    for text in &texts_to_embed {
        let mut document = text.clone();

        let mut tokens:Vec<String> = tokenizer
            .split_by_token_iter(&document, false)
            .map(|result| result.unwrap_or_else(|err| panic!("Tokenization error: {}", err)))
            .collect();

        if tokens.is_empty() {
            continue;
        }

        if tokens.len() > 500 {
            tokens = tokens.into_iter().take(500).collect();
            document = tokens.join("");
        }

        let hash = compute_sha1(&document);

        writeln!(writer, "{}:{}:{}\t{}\t{}\t{}\t{}\t{}\t{}",
            ontology_id,
            entity_type,
            &iri_value,
            ontology_id,
            entity_type,
            &iri_value,
            &label_str,
            hash,
            document.replace("\t", " ").replace("\n", " ").replace("\r", " ")
        ).unwrap();

        written += 1;
    }

    written
}

