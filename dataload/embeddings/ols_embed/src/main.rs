use clap::Parser;
use openai_api_rust::{embeddings::{EmbeddingsApi, EmbeddingsBody}, Auth, OpenAI};
use rusqlite::{Connection, Statement};
use std::{
    fs::File, io::BufReader, path::PathBuf
};
use struson::reader::{JsonReader, JsonStreamReader, ValueType};
use tiktoken_rs::{cl100k_base, CoreBPE};
use sha1::Digest;
use std::error::Error;
use anyhow::Result;
use candle_core::{Device, Tensor};
use candle_nn::VarBuilder;
use candle_transformers::models::bert::{BertModel, Config, DTYPE};
use tokenizers::Tokenizer;

const BATCH_SIZE:usize = 1000;

struct BertEmbeddingModel {
    model: BertModel,
    tokenizer: Tokenizer,
    device: Device,
}

impl BertEmbeddingModel {
    fn new(model_path: &str, tokenizer_path: &str) -> Result<Self> {
        let device = Device::Cpu;
        
        // Load config
        let config_path = format!("{}/config.json", model_path);
        let config = std::fs::read_to_string(&config_path)?;
        let config: Config = serde_json::from_str(&config)?;
        
        // Load model weights
        let weights_path = format!("{}/model.safetensors", model_path);
        let vb = if std::path::Path::new(&weights_path).exists() {
            unsafe { VarBuilder::from_mmaped_safetensors(&[weights_path], DTYPE, &device)? }
        } else {
            // Try .bin format
            let weights_path = format!("{}/pytorch_model.bin", model_path);
            VarBuilder::from_pth(&weights_path, DTYPE, &device)?
        };
        
        let model = BertModel::load(vb, &config)?;
        
        // Load tokenizer
        let tokenizer = Tokenizer::from_file(tokenizer_path)
            .map_err(|e| anyhow::anyhow!("Failed to load tokenizer: {}", e))?;
        
        Ok(Self {
            model,
            tokenizer,
            device,
        })
    }
    
    fn encode(&self, texts: &[&str]) -> Result<Vec<Vec<f32>>> {
        let mut all_embeddings = Vec::new();
        
        for text in texts {
            // Tokenize
            let encoding = self.tokenizer
                .encode(*text, true)
                .map_err(|e| anyhow::anyhow!("Tokenization error: {}", e))?;
            
            let tokens = encoding.get_ids();
            let token_ids = Tensor::new(tokens, &self.device)?.unsqueeze(0)?;
            let token_type_ids = token_ids.zeros_like()?;
            
            // Forward pass (third parameter is attention mask, None means use all tokens)
            let embeddings = self.model.forward(&token_ids, &token_type_ids, None)?;
            
            // Mean pooling over sequence dimension
            let (_n_sentence, n_tokens, _hidden_size) = embeddings.dims3()?;
            let embeddings = (embeddings.sum(1)? / (n_tokens as f64))?;
            
            // Normalize
            let embeddings = embeddings.broadcast_div(&embeddings.sqr()?.sum_keepdim(1)?.sqrt()?)?;
            
            // Convert to Vec<f32>
            let embedding_vec = embeddings.squeeze(0)?.to_vec1::<f32>()?;
            all_embeddings.push(embedding_vec);
        }
        
        Ok(all_embeddings)
    }
}

enum EmbeddingEngine {
    OpenAI(OpenAI, String), // OpenAI client + model name
    Bert(BertEmbeddingModel, String), // BERT model + model name (from filename)
}

struct DocToEmbed {
    ontology_id:String,
    entity_type:String,
    iri:String,
    hash:String,
    document:String
}

struct EmbeddingResult {
    num_unchanged:usize,
    num_reused:usize,
    num_embedded:usize
}

#[derive(Parser)]
struct Args {
    #[arg(long)]
    input_file: String,
    
    #[arg(long)]
    db_path: String,
    
    #[arg(long)]
    dry_run: bool,
    
    /// Path to local BERT model directory (containing config.json, model weights, and tokenizer.json)
    /// Model name in database will be the directory name
    #[arg(long, conflicts_with = "openai_model")]
    local_model: Option<PathBuf>,
    
    /// OpenAI model name (e.g., text-embedding-3-small)
    /// Requires OPENAI_API_KEY environment variable
    #[arg(long, conflicts_with = "local_model")]
    openai_model: Option<String>,
}

fn compute_sha1(doc:&str) -> String {
    let mut hasher = sha1::Sha1::new();
    hasher.update(doc.as_bytes());
    let result = hasher.finalize();
    format!("{:x}", result)
}

fn sqlite_exists(ontology_id:&str, entity_type:&str, iri:&str, hash:&str, sqlite_exists_stmt:&mut Statement) -> bool {
    let mut rows = sqlite_exists_stmt.query(&[&ontology_id, &entity_type, &iri, &hash]).unwrap();
    if let Some(row) = rows.next().unwrap() {
        return row.get(0).unwrap();
    }
    false
}

fn sqlite_get(hash:&str, sqlite_get_stmt:&mut Statement) -> Option<String> {
    let mut rows = sqlite_get_stmt.query(&[&hash]).unwrap();
    if let Some(row) = rows.next().unwrap() {
        return row.get(0).unwrap();
    }
    None
}

fn sqlite_insert(
    ontology_id:&str,
    entity_type:&str,
    iri:&str,
    document:&str,
    hash:&str,
    model:&str,
    embeddings:&str,
    sqlite_insert_stmt:&mut Statement
) {
    sqlite_insert_stmt.execute(&[
        &ontology_id,
        &entity_type,
        &iri,
        &document,
        &hash,
        &model,
        &embeddings
    ]).unwrap();
}

fn create_embedding_engine(args: &Args) -> Result<EmbeddingEngine> {
    if let Some(local_model_path) = &args.local_model {
        // Local BERT model
        let model_name = local_model_path
            .file_name()
            .and_then(|s| s.to_str())
            .ok_or_else(|| anyhow::anyhow!("Invalid model directory name"))?
            .to_string();
        
        // Tokenizer should be in the same directory as the model
        let tokenizer_path = local_model_path.join("tokenizer.json");
        
        eprintln!("Loading local BERT model from: {}", local_model_path.display());
        eprintln!("Loading tokenizer from: {}", tokenizer_path.display());
        eprintln!("Model name for database: {}", model_name);
        eprintln!("Using CPU with {} cores available", num_cpus::get());
        
        let model = BertEmbeddingModel::new(
            local_model_path.to_str().unwrap(),
            tokenizer_path.to_str().unwrap()
        )?;
        
        Ok(EmbeddingEngine::Bert(model, model_name))
    } else if let Some(openai_model_name) = &args.openai_model {
        // OpenAI model
        eprintln!("Using OpenAI model: {}", openai_model_name);
        let auth = Auth::from_env()
            .map_err(|e| anyhow::anyhow!("Failed to get OpenAI auth from environment: {}. Set OPENAI_API_KEY.", e))?;
        let openai = OpenAI::new(auth, "https://api.openai.com/v1/");
        Ok(EmbeddingEngine::OpenAI(openai, openai_model_name.clone()))
    } else {
        Err(anyhow::anyhow!("Must specify either --local-model or --openai-model"))
    }
}

fn generate_embeddings(
    engine: &EmbeddingEngine,
    texts: Vec<String>,
) -> Result<Vec<Vec<f32>>> {
    match engine {
        EmbeddingEngine::OpenAI(openai, model_name) => {
            let embeddings = openai.embeddings_create(&EmbeddingsBody {
                model: model_name.clone(),
                input: texts,
                user: None,
            })
            .map_err(|e| anyhow::anyhow!("OpenAI API error: {:?}", e))?;
            
            let embeddings_data = embeddings.data.ok_or_else(|| {
                anyhow::anyhow!("No embeddings data returned from OpenAI")
            })?;
            
            let result = embeddings_data
                .iter()
                .map(|emb| {
                    // Convert Option<Vec<f64>> to Vec<f32>
                    emb.embedding.as_ref()
                        .map(|v| v.iter().map(|&x| x as f32).collect())
                        .unwrap_or_else(Vec::new)
                })
                .collect();
            
            Ok(result)
        }
        EmbeddingEngine::Bert(model, _model_name) => {
            let text_refs: Vec<&str> = texts.iter().map(|s| s.as_str()).collect();
            let embeddings = model.encode(&text_refs)?;
            Ok(embeddings)
        }
    }
}

fn get_model_name(engine: &EmbeddingEngine) -> &str {
    match engine {
        EmbeddingEngine::OpenAI(_, model_name) => model_name,
        EmbeddingEngine::Bert(_, model_name) => model_name,
    }
}

fn process_batch(
    batch: &Vec<DocToEmbed>,
    sqlite_get_stmt:&mut Statement,
    sqlite_exists_stmt:&mut Statement,
    sqlite_insert_stmt:&mut Statement,
    engine: &EmbeddingEngine,
    dry_run:bool
) -> EmbeddingResult {

    let model_name = get_model_name(engine);
    let mut num_unchanged = 0;
    let mut num_reused = 0;
    let mut num_embedded = 0;

    let to_embed = batch.iter().filter(|doc| {

        // eprintln!("Checking if we need to embed {} {} {}", doc.ontology_id, doc.entity_type, doc.iri);

        if sqlite_exists(&doc.ontology_id, &doc.entity_type, &doc.iri, &doc.hash, sqlite_exists_stmt) {
            num_unchanged = num_unchanged + 1;
            // eprintln!("Skipping updating embeddings for {} {} {}; document has not changed", doc.ontology_id, doc.entity_type, doc.iri);
            return false
        }

        // eprintln!("Checking if we need to embed {} {} {} - part 2", doc.ontology_id, doc.entity_type, doc.iri);

        if let Some(embedding) = sqlite_get(&doc.hash, sqlite_get_stmt) {
            num_reused = num_reused + 1;
            // eprintln!("Found cached embeddings we can reuse for {} {} {} (hash = {})", doc.ontology_id, doc.entity_type, doc.iri, doc.hash);
            if !dry_run {
                sqlite_insert(&doc.ontology_id,
                    &doc.entity_type,
                    &doc.iri,
                    &doc.document,
                    &doc.hash,
                    model_name,
                    &embedding,
                    sqlite_insert_stmt
                );
            }
            return false
        }

        num_embedded = num_embedded + 1;
        eprintln!("New document to embed for {} {} {}", doc.ontology_id, doc.entity_type, doc.iri);
        return true
    }).collect::<Vec<_>>();

    eprintln!("Found {} documents to embed in this batch", to_embed.len());

    if (!dry_run) && (!to_embed.is_empty()) {

        let texts: Vec<String> = to_embed.iter().map(|doc| doc.document.clone()).collect();
        
        let embeddings = generate_embeddings(engine, texts).expect("Failed to generate embeddings");

        eprintln!("Got {} embeddings back", embeddings.len());

        let mut n = 0;

        for embedding in embeddings.iter() {

            let doc = &to_embed[n];

            // Convert Vec<f32> to JSON string format compatible with existing storage
            let embedding_json = serde_json::json!({
                "embedding": embedding,
                "index": n,
                "object": "embedding"
            }).to_string();

            sqlite_insert(&doc.ontology_id,
                &doc.entity_type,
                &doc.iri,
                &doc.document,
                &doc.hash,
                model_name,
                &embedding_json,
                sqlite_insert_stmt
            );

            n = n + 1;
        }

    }

    return EmbeddingResult {
        num_unchanged: num_unchanged,
        num_reused: num_reused,
        num_embedded: num_embedded
    };

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
    
    eprintln!("System has {} CPU cores available", num_cpus::get());
    
    let engine = create_embedding_engine(&args)?;
    eprintln!("Model name for database: {}", get_model_name(&engine));

    let conn = Connection::open(&args.db_path).unwrap();

    // conn.execute_batch(
    //     format!("PRAGMA journal_mode = OFF;
    //         PRAGMA synchronous = 0;
    //         PRAGMA locking_mode = EXCLUSIVE;
    //         PRAGMA temp_store = MEMORY;").as_str()
    // )
    // .expect("PRAGMA");

    conn.execute(
        "CREATE TABLE IF NOT EXISTS embeddings (
                ontologyId TEXT not null,
                entityType TEXT not null,
                iri TEXT not null,
                document TEXT not null,
                hash TEXT not null,
                model TEXT not null,
                embeddings TEXT not null,
                PRIMARY KEY(ontologyId,entityType,iri)
                )",
        []
    )
    .unwrap();

    // Check if the index already exists
    let mut stmt = conn.prepare(
        "SELECT name FROM sqlite_master WHERE type='index' AND name='embeddings_hash';"
    )?;
    let mut rows = stmt.query([])?;

    if rows.next()?.is_none() {
        // Index doesn't exist, so create it
        conn.execute("CREATE INDEX embeddings_hash ON embeddings(hash);", [])?;
        eprintln!("Index created!");
    } else {
        eprintln!("Index already exists.");
    }

    let mut sqlite_get_stmt = conn.prepare("SELECT embeddings FROM embeddings WHERE hash = ?").unwrap();
    let mut sqlite_exists_stmt = conn.prepare("SELECT EXISTS(SELECT 1 FROM embeddings WHERE ontologyId = ? AND entityType = ? AND iri = ? AND hash = ?)").unwrap();
    let mut sqlite_insert_stmt = conn.prepare("INSERT OR REPLACE INTO embeddings (ontologyId, entityType, iri, document, hash, model, embeddings) VALUES (?, ?, ?, ?, ?, ?, ?)").unwrap();

    let file = File::open(args.input_file).unwrap();
    let reader = BufReader::new(file);

    let mut batch:Vec<DocToEmbed> = Vec::new();
    let mut current_ont_id = String::new();

    let tokenizer = cl100k_base().unwrap();

    let mut total = 0;
    let mut num_unchanged = 0;
    let mut num_reused = 0;
    let mut num_embedded = 0;

    let mut json = JsonStreamReader::new(reader);

    json.begin_object().unwrap();
    let ontologies = json.next_name().unwrap();
    if ontologies != "ontologies" {
        panic!();
    }
    json.begin_array().unwrap();
    while json.has_next().unwrap() {
        json.begin_object().unwrap();
        while json.has_next().unwrap() {
            let name = json.next_name().unwrap();
            if name == "ontologyId" {
                current_ont_id = json.next_string().unwrap();
            } else if name == "classes" {
                eprintln!("Processing classes for ontology {}", current_ont_id);
                json.begin_array().unwrap();
                while json.has_next().unwrap() {
                    // class
                    total = total + 1;
                    let result = process_entity("class", &current_ont_id, &mut json, &mut batch, &mut sqlite_get_stmt, &mut sqlite_exists_stmt, &mut sqlite_insert_stmt, &engine, &tokenizer, args.dry_run);
                    num_embedded = num_embedded + result.num_embedded;
                    num_unchanged = num_unchanged + result.num_unchanged;
                    num_reused = num_reused + result.num_reused;
                }
                json.end_array().unwrap();
            } else if name == "properties" {
                eprintln!("Processing properties for ontology {}", current_ont_id);
                json.begin_array().unwrap();
                while json.has_next().unwrap() {
                    // property
                    total = total + 1;
                    let result = process_entity("property", &current_ont_id, &mut json, &mut batch, &mut sqlite_get_stmt, &mut sqlite_exists_stmt, &mut sqlite_insert_stmt, &engine, &tokenizer, args.dry_run);
                    num_embedded = num_embedded + result.num_embedded;
                    num_unchanged = num_unchanged + result.num_unchanged;
                    num_reused = num_reused + result.num_reused;
                }
                json.end_array().unwrap();
            } else if name == "individuals" {
                eprintln!("Processing individuals for ontology {}", current_ont_id);
                json.begin_array().unwrap();
                while json.has_next().unwrap() {
                    // individual
                    total = total + 1;
                    let result = process_entity("individual", &current_ont_id, &mut json, &mut batch, &mut sqlite_get_stmt, &mut sqlite_exists_stmt, &mut sqlite_insert_stmt, &engine, &tokenizer, args.dry_run);
                    num_embedded = num_embedded + result.num_embedded;
                    num_unchanged = num_unchanged + result.num_unchanged;
                    num_reused = num_reused + result.num_reused;
                }
                json.end_array().unwrap();
            } else {
                json.skip_value().unwrap();
            }

        }
        json.end_object().unwrap();
    }
    json.end_array().unwrap();
    json.end_object().unwrap();

    if !batch.is_empty() {
        let result = process_batch(
            &batch,
            &mut sqlite_get_stmt,
            &mut sqlite_exists_stmt,
            &mut sqlite_insert_stmt,
            &engine,
            args.dry_run
        );
        num_unchanged = num_unchanged + result.num_unchanged;
        num_reused = num_reused + result.num_reused;
        num_embedded = num_embedded + result.num_embedded;
    }

    eprintln!("");
    eprintln!("=================");
    eprintln!("Embedding summary");
    eprintln!("=================");
    eprintln!("");
    eprintln!("Total number of ontology entities: {}", total);
    eprintln!("Unchanged: {}  (~ {}%)", num_unchanged, ((num_unchanged as f32 / total as f32) * 100.0).round());
    eprintln!("Reused: {}  (~ {}%)", num_reused, ((num_reused as f32 / total as f32) * 100.0).round());
    eprintln!("Newly embedded: {}  (~ {}%)", num_embedded, ((num_embedded as f32 / total as f32) * 100.0).round());

    Ok(())
}

fn process_entity(
    entity_type:&str,
    ontology_id:&str,
    json:&mut JsonStreamReader<BufReader<File>>,
    batch:&mut Vec<DocToEmbed>,
    sqlite_get_stmt:&mut Statement,
    sqlite_exists_stmt:&mut Statement,
    sqlite_insert_stmt:&mut Statement,
    engine: &EmbeddingEngine,
    tokenizer: &CoreBPE,
    dry_run:bool
) -> EmbeddingResult {
    let mut iri:Option<String> = None;
    let mut labels:Vec<String> = Vec::new();
    let mut synonyms:Vec<String> = Vec::new();
    let mut definitions:Vec<String> = Vec::new();
    
    let mut num_unchanged = 0;
    let mut num_reused = 0;
    let mut num_embedded = 0;

    json.begin_object().unwrap();
    while json.has_next().unwrap() {
        let key = json.next_name().unwrap();
        if key == "iri" {
            iri = Some(json.next_string().unwrap());
        } else if key == "label" {
            labels.append(&mut get_values(json));
        } else if key == "synonym" {
            synonyms.append(&mut get_values(json));
        } else if key == "definition" {
            definitions.append(&mut get_values(json));
        } else {
            json.skip_value().unwrap();
        }
    }
    json.end_object().unwrap();

    if !iri.is_some() {
        panic!("No IRI found for {}", entity_type);
    }

    let the_iri = iri.unwrap();

    let mut document = labels.into_iter().chain( synonyms.into_iter()).chain( definitions.into_iter()).collect::<Vec<String>>().join("; ");

    let mut tokens:Vec<String> = tokenizer
        .split_by_token_iter(&document, false)
        .map(|result| result.unwrap_or_else(|err| panic!("Tokenization error: {}", err)))
        .collect();

    if tokens.len() > 8000 {
        tokens = tokens.into_iter().take(8000).collect();
        document = tokens.join(" ");
    }

    if tokens.len() == 0 {
        eprintln!("Skipping empty document for {} {} {}", ontology_id, "class", the_iri);
        return EmbeddingResult { num_unchanged: 0, num_reused: 0, num_embedded: 0 };
    }

    let hash = compute_sha1(&document);

    batch.push(DocToEmbed {
        ontology_id: ontology_id.to_owned(),
        entity_type: entity_type.to_owned(),
        iri: the_iri.clone(),
        hash: hash,
        document: document
    });

    if batch.len() >= BATCH_SIZE {

        let result = process_batch(
            batch,
            sqlite_get_stmt,
            sqlite_exists_stmt,
            sqlite_insert_stmt,
            engine,
            dry_run);

        num_unchanged = num_unchanged + result.num_unchanged;
        num_reused = num_reused + result.num_reused;
        num_embedded = num_embedded + result.num_embedded;

        batch.clear();
    }

    return EmbeddingResult {
        num_unchanged: num_unchanged,
        num_reused: num_reused,
        num_embedded: num_embedded
    };
}


