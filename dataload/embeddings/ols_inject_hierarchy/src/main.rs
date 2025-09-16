use clap::Parser;
use crossbeam::channel;
use indicatif::{ProgressBar, ProgressStyle};
use rayon::prelude::*;
use rusqlite::Connection;
use rusqlite::OpenFlags;
use serde::Deserialize;
use serde;
use std::{
    collections::{HashMap, HashSet, VecDeque}, error::Error, f32::consts::E, fs::File, io::BufReader, path::PathBuf, sync::Arc, thread, time::Duration
};
use struson::reader::{JsonReader, JsonStreamReader, ValueType};

#[derive(Parser, Debug)]
struct Args {
    #[arg(short, long)]
    input_db: PathBuf,
    #[arg(short, long)]
    ontology_json: PathBuf,
    #[arg(short, long)]
    output_db: PathBuf,
    #[arg(short, long, default_value = "0.5")]
    parent_weight: f32,
}

#[derive(Debug, Clone, Deserialize)]
struct Entity {
    iri: String,
    #[serde(default)]
    directParent: Vec<String>,
}

#[derive(Debug, Clone, Deserialize)]
struct Ontology {
    id: String,
    #[serde(default)]
    classes: Vec<Entity>,
    #[serde(default)]
    properties: Vec<Entity>,
    #[serde(default)]
    individuals: Vec<Entity>,
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
struct EntityKey {
    entity_type: String,
    iri: String,
}

impl EntityKey {
    fn new(etype: &str, iri: &str) -> Self {
        Self {
            entity_type: etype.to_string(),
            iri: iri.to_string(),
        }
    }
}

fn main() -> Result<(), Box<dyn Error>> {
    let args = Arc::new(Args::parse());

    {
        let out = Connection::open(&args.output_db)?;
        out.execute_batch(
            "PRAGMA journal_mode = WAL;
             CREATE TABLE IF NOT EXISTS embeddings (
               ontologyId TEXT NOT NULL,
               entityType TEXT NOT NULL,
               iri        TEXT NOT NULL,
               embeddings TEXT NOT NULL,
               PRIMARY KEY(ontologyId, entityType, iri)
             )",
        )?;
    }

    let pb = Arc::new(
        ProgressBar::new_spinner()
            .with_style(
                ProgressStyle::with_template(
                    "{spinner:.green} {pos} ontologies processed [{elapsed_precise}]",
                )?,
            )
            .with_message("streaming…"),
    );
    pb.enable_steady_tick(Duration::from_millis(100));

    let (tx, rx) = channel::bounded::<Ontology>(8);
    let (write_tx, write_rx) = channel::unbounded::<(String, String, String, String)>();

    // Writer thread
    let writer_db = args.output_db.clone();
    let writer_handle = thread::spawn(move || {
        if let Err(e) = (|| -> Result<(), Box<dyn Error + Send + Sync>> {
            let mut conn = Connection::open(&writer_db)?;
            let tx = conn.transaction()?;
            let mut stmt = tx.prepare(
                "INSERT OR REPLACE INTO embeddings (ontologyId, entityType, iri, embeddings)
                 VALUES (?, ?, ?, ?)",
            )?;
            for (oid, etype, iri, emb_json) in write_rx {
                stmt.execute(&[&oid, &etype, &iri, &emb_json])?;
            }
            drop(stmt);
            tx.commit()?;
            Ok(())
        })() {
            eprintln!("Writer thread failed: {}", e);
        }
    });

    // JSON reader thread
    {
        let tx = tx.clone();
        let json_path = args.ontology_json.clone();
        thread::spawn(move || -> Result<(), Box<dyn Error + Send + Sync>> {
            let file = File::open(&json_path)?;
            let reader = BufReader::new(file);
            let mut json = JsonStreamReader::new(reader);

            json.begin_object().unwrap();
            let name = json.next_name().unwrap();
            if name != "ontologies" {
                panic!("Expected top-level \"ontologies\"");
            }
            json.begin_array().unwrap();

            while json.has_next().unwrap() {
                json.begin_object().unwrap();
                let mut id = String::new();
                let mut classes = Vec::new();
                let mut properties = Vec::new();
                let mut individuals = Vec::new();

                while json.has_next().unwrap() {
                    let field = json.next_name().unwrap();
                    match field {
                        "ontologyId" => {
                            id = json.next_string().unwrap();
                            eprintln!("Reading ontology JSON: {}", id);
                        }
                        "classes" => {
                            let etype = field.to_string();
                            json.begin_array().unwrap();
                            while json.has_next().unwrap() {
                                json.begin_object().unwrap();
                                let mut iri = String::new();
                                let mut direct_parents = Vec::new();
                                while json.has_next().unwrap() {
                                    let fname = json.next_name().unwrap();
                                    match fname {
                                        "iri" => iri = json.next_string().unwrap(),
                                        "directParent" => {
                                            json.begin_array().unwrap();
                                            while json.has_next().unwrap() {
                                                if json.peek().unwrap() == ValueType::String {
                                                    direct_parents.push(json.next_string().unwrap());
                                                } else {
                                                    json.skip_value().unwrap();
                                                }
                                            }
                                            json.end_array().unwrap();
                                        }
                                        _ => json.skip_value().unwrap(),
                                    }
                                }
                                json.end_object().unwrap();
                                let ent = Entity { iri, directParent: direct_parents };
                                match etype.as_str() {
                                    "classes" => classes.push(ent),
                                    // "properties" => properties.push(ent),
                                    // "individuals" => individuals.push(ent),
                                    _ => unreachable!(),
                                }
                            }
                            json.end_array().unwrap();
                        }
                        _ => json.skip_value().unwrap(),
                    }
                }

                json.end_object().unwrap();

                eprintln!("Finished reading ontology JSON: {}", id);

                tx.send(Ontology { id, classes, properties, individuals }).unwrap();
            }

            eprintln!("JSON finished reading");

            json.end_array().unwrap();
            json.end_object().unwrap();

            drop(tx);
            Ok(())
        });
    }

    // Parallel processing
    rx.into_iter()
        .par_bridge()
        .for_each_with((Arc::clone(&args), Arc::clone(&pb), write_tx.clone()), |(args, pb, tx), ontology| {
            let results = process_one_ontology(&ontology, &args).unwrap();
            for (oid, etype, iri, emb_json) in results {
                tx.send((oid, etype, iri, emb_json)).unwrap();
            }
            pb.inc(1);
        });

    drop(write_tx); // Close writer channel
    writer_handle.join().unwrap(); // Wait for writer to finish
    pb.finish_with_message("all ontologies processed");
    Ok(())
}

fn process_one_ontology(
    ontology: &Ontology,
    args: &Args,
) -> Result<Vec<(String, String, String, String)>, Box<dyn Error>> {
    eprintln!("Processing ontology: {}", ontology.id);

    let input = Connection::open_with_flags(&args.input_db, OpenFlags::SQLITE_OPEN_READ_ONLY)?;
    let mut select_stmt = input.prepare(
        "SELECT embeddings FROM embeddings
         WHERE ontologyId = ? AND entityType = ? AND iri = ?",
    )?;

    let mut parents_map: HashMap<EntityKey, Vec<EntityKey>> = HashMap::new();
    let mut children_map: HashMap<EntityKey, Vec<EntityKey>> = HashMap::new();
    let mut all_entities: HashSet<EntityKey> = HashSet::new();

    for ent in &ontology.classes {
        let key = EntityKey::new("class", &ent.iri);
        all_entities.insert(key.clone());
    }

    for ent in &ontology.classes {
        let key = EntityKey::new("class", &ent.iri);
        for parent_iri in &ent.directParent {
            let pkey = EntityKey::new("class", parent_iri);
            if !all_entities.contains(&pkey) {
                eprintln!("Parent {} not found in ontology {}, skipping", parent_iri, ontology.id);
                continue;
            } else if key.eq(&pkey) {
                eprintln!("Entity {} has itself as a parent; skipping", key.iri);
                continue;
            } else {
                parents_map.entry(key.clone()).or_default().push(pkey.clone());
                children_map.entry(pkey).or_default().push(key.clone());
            }
        }
    }

    let sorted = topological_sort(&ontology.id, &all_entities, &parents_map, &children_map);

    if sorted.is_err() {
        eprintln!("Ontology {} appears to be cyclic; writing embeddings as they are", ontology.id);

        let mut result = Vec::new();
        for key in &all_entities {
            let emb: Vec<f32> = sqlite_get(&ontology.id, key, &mut select_stmt)
                .and_then(|j| serde_json::from_str(&j).ok())
                .unwrap_or_default();

            let j = serde_json::to_string(&emb)?;
            result.push((ontology.id.clone(), key.entity_type.clone(), key.iri.clone(), j));
        }

        return Ok(result);
    } else {

        let mut processed: HashMap<EntityKey, Vec<f32>> = HashMap::new();
        for key in sorted.unwrap() {
            let self_emb: Vec<f32> = sqlite_get(&ontology.id, &key, &mut select_stmt)
                .and_then(|j| serde_json::from_str(&j).ok())
                .unwrap_or_default();

            let parent_embs: Vec<_> = parents_map.get(&key)
                .into_iter()
                .flat_map(|plist| plist.iter())
                .filter_map(|pk| processed.get(pk))
                .filter(|v| v.len() > 0)
                .cloned()
                .collect();

            let new_emb = if self_emb.is_empty() || parent_embs.is_empty() {
                self_emb.clone()
            } else {
                let mean = mean_vector(&parent_embs);
                self_emb.iter()
                    .zip(&mean)
                    .map(|(s, p)| (1.0 - args.parent_weight) * s + args.parent_weight * p)
                    .collect()
            };
            processed.insert(key, new_emb);
        }

        let mut result = Vec::with_capacity(processed.len());
        for (key, emb) in processed {
            let j = serde_json::to_string(&emb)?;
            result.push((ontology.id.clone(), key.entity_type, key.iri, j));
        }

        eprintln!("Ontology {} processed: {} entities, {} embeddings",
                ontology.id, all_entities.len(), result.len());

        return Ok(result)
    }

}

fn sqlite_get(ontology_id: &str, key: &EntityKey, stmt: &mut rusqlite::Statement) -> Option<String> {
    let mut rows = stmt.query(&[ontology_id, &key.entity_type, &key.iri]).ok()?;
    rows.next().ok().flatten().and_then(|r| r.get(0).ok())
}

/* 
Kahn’s algorithm for topological sorting, which works by:
    Counting in-degrees (how many parents each node has).
    Starting with all nodes with 0 in-degree (i.e., no parents).
    Iteratively removing those nodes from the graph, reducing the in-degree of their children.
    Adding any children that now have 0 in-degree to the processing queue.
    */
fn topological_sort(
    ontology_id: &str,
    nodes: &HashSet<EntityKey>,
    parents_map: &HashMap<EntityKey, Vec<EntityKey>>,
    children_map: &HashMap<EntityKey, Vec<EntityKey>>,
) -> Result<Vec<EntityKey>, Box<dyn Error>> {
    eprintln!("Sorting {} nodes for ontology {}; {} parents, {} children",
             nodes.len(), ontology_id, parents_map.len(), children_map.len());

    let mut in_deg: HashMap<EntityKey, usize> = nodes.iter().map(|n| (n.clone(), 0)).collect();
    for (child, parents) in parents_map {
        for _ in parents {
            *in_deg.entry(child.clone()).or_default() += 1;
        }
    }

    let mut queue: VecDeque<EntityKey> = in_deg
        .iter()
        .filter_map(|(node, &deg)| if deg == 0 { Some(node.clone()) } else { None })
        .collect();

    let mut sorted = Vec::with_capacity(nodes.len());

    while let Some(node) = queue.pop_front() {
        sorted.push(node.clone());
        if let Some(children) = children_map.get(&node) {
            for child in children {
                if let Some(deg) = in_deg.get_mut(child) {
                    *deg -= 1;
                    if *deg == 0 {
                        queue.push_back(child.clone());
                    }
                }
            }
        }
    }

    if sorted.len() != nodes.len() {

        // Some nodes were never reduced to in-degree 0, meaning they’re in a cycle.
        // Because in Kahn’s algorithm, a node can only be processed (and added to the sorted
        // list) once all of its parents have been removed (i.e., its in-degree is 0).
        // In a cycle, that never happens — all involved nodes are waiting on each other,
        // so none ever reach 0 in-degree.

        let remaining: Vec<_> = nodes.difference(&sorted.iter().cloned().collect()).cloned().collect();
        eprint!("{}: Cycle detected involving nodes: {:?}; expected {} sorted, got {}",
                    ontology_id, remaining, nodes.len(), sorted.len());

        Err("Cyclic ontology".into())
    } else {
        eprintln!("Topological sort completed for ontology {}: {} nodes sorted", ontology_id, sorted.len());
        Ok(sorted)
    }
}

fn mean_vector(vs: &[Vec<f32>]) -> Vec<f32> {
    if vs.is_empty() {
        return Vec::new();
    }
    let len = vs[0].len();
    let mut sum = vec![0.0; len];
    for v in vs {
        for (i, &val) in v.iter().enumerate() {
            sum[i] += val;
        }
    }
    for x in &mut sum {
        *x /= vs.len() as f32;
    }
    sum
}
