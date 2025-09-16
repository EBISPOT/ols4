use clap::Parser;
use rusqlite::{Connection, Statement};
use rusqlite::OpenFlags;
use std::{
    fmt::Write, fs::File, io::{self, BufReader}
};
use struson::{reader::{JsonReader, JsonStreamReader, ValueType}, writer::WriterSettings};
use struson::writer::{JsonWriter, JsonStreamWriter};
use std::error::Error;

#[derive(Parser)]
struct Args {
    #[arg(long)]
    db_path: String,
}

fn sqlite_get(
    ontology_id: &str,
    entity_type: &str,
    iri: &str,
    sqlite_get_stmt: &mut Statement,
) -> Option<String> {
    let mut rows = sqlite_get_stmt
        .query(&[&ontology_id, &entity_type, &iri])
        .unwrap();
    if let Some(row) = rows.next().unwrap() {
        return row.get(0).unwrap();
    }
    None
}

fn copy_value<R, W>(json: &mut R, json_out: &mut W) -> Result<(), Box<dyn Error>>
where
    R: JsonReader,
    W: JsonWriter,
{
    match json.peek()? {
        ValueType::Null => {
            json.next_null().unwrap();
            json_out.null_value().unwrap();
        }
        ValueType::Boolean => {
            json_out.bool_value(json.next_bool().unwrap()).unwrap();
        }
        ValueType::Number => {
            json_out
                .number_value_from_string(json.next_number_as_str().unwrap())
                .unwrap();
        }
        ValueType::String => {
            json_out.string_value(json.next_str().unwrap()).unwrap();
        }
        ValueType::Object => {
            json.begin_object().unwrap();
            json_out.begin_object().unwrap();
            while json.has_next().unwrap() {
                let key = json.next_name().unwrap();
                json_out.name(&key).unwrap();
                copy_value(json, json_out).unwrap();
            }
            json.end_object().unwrap();
            json_out.end_object().unwrap();
        }
        ValueType::Array => {
            json.begin_array().unwrap();
            json_out.begin_array().unwrap();
            while json.has_next().unwrap() {
                copy_value(json, json_out).unwrap();
            }
            json.end_array().unwrap();
            json_out.end_array().unwrap();
        }
    }
    Ok(())
}

fn write_entity<R, W>(
    json: &mut R,
    json_out: &mut W,
    sqlite_get_stmt: &mut Statement,
    ontology_id: &str,
    entity_type: &str,
) -> Result<(), Box<dyn Error>>
where
    R: JsonReader,
    W: JsonWriter,
{
    json.begin_object().unwrap();
    json_out.begin_object().unwrap();

    let mut iri_opt: Option<String> = None;

    while json.has_next().unwrap() {
        let key = json.next_name().unwrap();
        json_out.name(&key).unwrap();
        if key == "iri" {
            let iri = json.next_str().unwrap();
            iri_opt = Some(iri.to_string());
            json_out.string_value(iri).unwrap();
        } else {
            copy_value(json, json_out).unwrap();
        }
    }

    if let Some(ref iri) = iri_opt {
        if let Some(mut emb_str_full) =
            sqlite_get_stmt
                .query(&[&ontology_id, &entity_type, iri_opt.unwrap().as_str()])
                .unwrap()
                .next()
                .unwrap()
                .and_then(|row| row.get::<_, Option<String>>(0).unwrap())
        {

            //// TODO: Remove temp hack
            ////
            if emb_str_full.starts_with("{") {
                // this is a temporary hack as we have two formats of embeddings in the 300 GB
                // database and would be expensive to re-embed everything. TODO: manually
                // patch the existing embeddings in the DB to all be the same.
                if !emb_str_full.starts_with("{\"object\":\"embedding\",\"embedding\":[") {
                    panic!("Unexpected embedding format: {}", emb_str_full);
                }
                emb_str_full = emb_str_full[emb_str_full.find('[').unwrap()..emb_str_full.find(']').unwrap() + 1].to_string();
            }
            ////



            // write embeddings array
            json_out.name("embeddings").unwrap();
            json_out.begin_array().unwrap();
            let emb_str = &emb_str_full[1..emb_str_full.len() - 1];
            for val in emb_str.split(',') {
                json_out
                    .number_value_from_string(val.trim())
                    .unwrap();
            }
            json_out.end_array().unwrap();
        }
    }

    json.end_object().unwrap();
    json_out.end_object().unwrap();

    Ok(())
}

fn main() -> Result<(), Box<dyn Error>> {
    let args = Args::parse();
    let conn = Connection::open_with_flags(&args.db_path, OpenFlags::SQLITE_OPEN_READ_ONLY)?;
    let mut sqlite_get_stmt = conn.prepare(
        "SELECT embeddings FROM embeddings WHERE ontologyId = ? AND entityType = ? AND iri = ?",
    )?;

    let stdin = io::stdin().lock();
    let reader = BufReader::new(stdin);
    let mut json = JsonStreamReader::new(reader);

    let stdout = io::stdout().lock();
    let mut json_out = JsonStreamWriter::new_custom(stdout, WriterSettings {
        pretty_print: true,
        ..Default::default()
    });

    // start root object
    json.begin_object().unwrap();
    json_out.begin_object().unwrap();

    // "ontologies" field
    let ont_field = json.next_name().unwrap();
    if ont_field != "ontologies" {
        panic!("Expected 'ontologies' field, found '{}'", ont_field);
    }
    json_out.name("ontologies").unwrap();
    json.begin_array().unwrap();
    json_out.begin_array().unwrap();

    // iterate each ontology object
    while json.has_next().unwrap() {
        json.begin_object().unwrap();
        json_out.begin_object().unwrap();

        let mut current_ont_id: Option<String> = None;

        // fields within the ontology object
        while json.has_next().unwrap() {
            // take property name as owned String to avoid borrowing json
            let name_str = json.next_name().unwrap().to_string();
            json_out.name(&name_str).unwrap();

            if name_str == "ontologyId" {
                let ont_id = json.next_string().unwrap();
                eprintln!("Processing ontology {}", ont_id);
                current_ont_id = Some(ont_id.clone());
                json_out.string_value(&ont_id).unwrap();

            } else if name_str == "classes"
                || name_str == "properties"
                || name_str == "individuals"
            {
                json.begin_array().unwrap();
                json_out.begin_array().unwrap();

                while json.has_next().unwrap() {
                    write_entity(
                        &mut json,
                        &mut json_out,
                        &mut sqlite_get_stmt,
                        current_ont_id.as_deref().unwrap(),
                        match name_str.as_str() {
                            "classes" => "class",
                            "properties" => "property",
                            "individuals" => "individual",
                            _ => unreachable!(),
                        },
                    )?;
                }

                json.end_array().unwrap();
                json_out.end_array().unwrap();

            } else {
                copy_value(&mut json, &mut json_out)?;
            }
        }

        json.end_object().unwrap();
        json_out.end_object().unwrap();
    }

    json.end_array().unwrap();
    json_out.end_array().unwrap();
    json.end_object().unwrap();
    json_out.end_object().unwrap();

    Ok(())
}
