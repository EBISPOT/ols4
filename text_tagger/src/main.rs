mod ac;

use ac::{NerAc, NerAcBuilder, RECORD_SEP, UNIT_SEP};
use serde::Serialize;
use std::fs::File;
use std::io::{self, BufRead, BufWriter, Write};

// ---------------------------------------------------------------------------
// Shared types
// ---------------------------------------------------------------------------

#[derive(Serialize)]
struct Entity {
    start: usize,
    end: usize,
    term_label: String,
    term_iri: String,
    ontology_id: String,
}

#[derive(Serialize)]
struct AnnotateResponse {
    entities: Vec<Entity>,
}

// ---------------------------------------------------------------------------
// Shared logic
// ---------------------------------------------------------------------------

fn annotate_text(ac: &NerAc, text: &str, delimiters: Option<&[u8]>) -> Vec<Entity> {
    let matches = ac.find_all_matches(text, delimiters);
    
    matches
        .into_iter()
        .flat_map(|m| {
            let start = m.start;
            let end = m.end;
            m.value
                .split(UNIT_SEP)
                .map(|s| s.to_string())
                .collect::<Vec<_>>()
                .into_iter()
                .map(move |record| {
                    let mut parts = record.splitn(3, RECORD_SEP);
                    Entity {
                        start,
                        end,
                        term_label: parts.next().unwrap_or("").to_string(),
                        term_iri: parts.next().unwrap_or("").to_string(),
                        ontology_id: parts.next().unwrap_or("").to_string(),
                    }
                })
        })
        .collect()
}

// ---------------------------------------------------------------------------
// CLI Mode
// ---------------------------------------------------------------------------

fn run_cli(ac: NerAc, delimiters: Option<Vec<u8>>) {
    eprintln!("Ready – enter text (one query per line, Ctrl-D to quit):");

    let stdin = io::stdin();
    let stdout = io::stdout();
    let mut out = stdout.lock();

    for line in stdin.lock().lines() {
        let text = line.expect("I/O error reading stdin");
        if text.is_empty() {
            continue;
        }

        let entities = annotate_text(&ac, &text, delimiters.as_deref());
        let resp = AnnotateResponse { entities };
        
        serde_json::to_writer(&mut out, &resp).expect("Failed to write JSON");
        out.write_all(b"\n").expect("Failed to write newline");
        out.flush().expect("Failed to flush");
    }
}

// ---------------------------------------------------------------------------
// Build Mode
// ---------------------------------------------------------------------------

const DEFAULT_MIN_LEN: usize = 3;

fn run_build(output_path: &str, min_len: usize) {
    eprintln!("Building database with minimum label length: {} characters", min_len);

    let stdin = io::stdin();
    let mut lines = stdin.lock().lines();
    let mut builder = NerAcBuilder::new();

    // ── Parse header ───────────────────────────────────────────────
    let header_line = lines
        .next()
        .expect("empty input – expected a TSV header line")
        .expect("I/O error reading header");

    let headers: Vec<&str> = header_line.split('\t').collect();

    let col = |name: &str| -> usize {
        headers
            .iter()
            .position(|h| *h == name)
            .unwrap_or_else(|| {
                eprintln!("Error: required column '{}' not found in header", name);
                eprintln!("  header columns: {:?}", headers);
                std::process::exit(1);
            })
    };

    let idx_ontology_id = col("ontology_id");
    let idx_label = col("label");
    let idx_iri = col("iri");

    let min_cols = [idx_ontology_id, idx_label, idx_iri]
        .into_iter()
        .max()
        .unwrap()
        + 1;

    // ── Process data rows ──────────────────────────────────────────
    let mut row_num: usize = 1; // 1 = header already consumed
    let mut skipped: usize = 0;

    for line_result in lines {
        row_num += 1;
        let line = line_result.expect("I/O error reading stdin");
        if line.is_empty() {
            continue;
        }

        let fields: Vec<&str> = line.split('\t').collect();

        if fields.len() < min_cols {
            eprintln!(
                "warning: row {} has {} columns, expected at least {} – skipping",
                row_num,
                fields.len(),
                min_cols
            );
            skipped += 1;
            continue;
        }

        let ontology_id = fields[idx_ontology_id];
        let label = fields[idx_label];
        let iri = fields[idx_iri];

        if label.is_empty() || iri.is_empty() {
            skipped += 1;
            continue;
        }

        if label.len() < min_len {
            skipped += 1;
            continue;
        }

        let value = format!(
            "{}{}{}{}{}",
            label, RECORD_SEP, iri, RECORD_SEP, ontology_id
        );

        // Index by the label/synonym text
        builder.add_entry(label, &value);

        if row_num % 500_000 == 0 {
            eprintln!("  processed {} rows …", row_num - 1);
        }
    }

    eprintln!(
        "Read {} data rows ({} skipped), {} unique keys",
        row_num - 1,
        skipped,
        builder.entry_count()
    );

    let ac = builder.build();
    eprintln!("Aho-Corasick binary size: {} bytes", ac.buf.len());

    let mut out =
        BufWriter::new(File::create(output_path).expect("Failed to create output file"));
    out.write_all(&ac.buf)
        .expect("Failed to write AC data");
    out.flush().expect("Failed to flush output");

    eprintln!("Saved database to {}", output_path);
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

fn main() {
    let args: Vec<String> = std::env::args().collect();
    
    let mode = args.get(1).map(|s| s.as_str());

    match mode {
        Some("build") => {
            // Parse build arguments
            let mut output_path = "text_tagger_db.bin".to_string();
            let mut min_len = DEFAULT_MIN_LEN;
            
            let mut i = 2;
            while i < args.len() {
                match args[i].as_str() {
                    "--min-len" => {
                        i += 1;
                        min_len = args.get(i)
                            .expect("--min-len requires a value")
                            .parse()
                            .expect("--min-len must be a non-negative integer");
                    }
                    "--output" | "-o" => {
                        i += 1;
                        output_path = args.get(i)
                            .expect("--output requires a value")
                            .to_string();
                    }
                    arg => {
                        output_path = arg.to_string();
                    }
                }
                i += 1;
            }
            
            run_build(&output_path, min_len);
        }
        Some("cli") | None => {
            // Parse cli arguments
            let mut db_path: Option<String> = None;
            let mut delimiters: Option<Vec<u8>> = None;

            let mut i = 2;
            while i < args.len() {
                match args[i].as_str() {
                    "--delimiters" => {
                        i += 1;
                        let delim_str = args.get(i)
                            .expect("--delimiters requires a value");
                        delimiters = Some(delim_str.bytes().collect());
                    }
                    arg => {
                        db_path = Some(arg.to_string());
                    }
                }
                i += 1;
            }

            let db_path = db_path
                .or_else(|| std::env::var("text_tagger_db_PATH").ok())
                .unwrap_or_else(|| "text_tagger_db.bin".to_string());

            eprintln!("Loading database from {} …", db_path);
            if let Some(ref d) = delimiters {
                eprintln!("Using delimiters: {:?}", d.iter().map(|&b| b as char).collect::<Vec<_>>());
            }
            let buf = std::fs::read(&db_path).unwrap_or_else(|e| {
                eprintln!("Failed to read {}: {}", db_path, e);
                std::process::exit(1);
            });
            eprintln!("Loaded {} bytes", buf.len());

            let ac = NerAc::from_buf(buf);
            run_cli(ac, delimiters);
        }
        Some(other) => {
            eprintln!("Unknown mode: {}", other);
            eprintln!();
            eprintln!("Usage: {} <mode> [options]", args[0]);
            eprintln!();
            eprintln!("Modes:");
            eprintln!("  build [--output FILE] [--min-len N]");
            eprintln!("        Build database from TSV on stdin");
            eprintln!("        Default output: text_tagger_db.bin");
            eprintln!("        Default min-len: {}", DEFAULT_MIN_LEN);
            eprintln!();
            eprintln!("  cli [DB_PATH] [--delimiters CHARS]");
            eprintln!("        Run in CLI mode (default, reads text from stdin)");
            eprintln!("        Default DB_PATH: text_tagger_db.bin or $text_tagger_db_PATH");
            eprintln!("        --delimiters: word-boundary characters (e.g. ' ,.;')");
            std::process::exit(1);
        }
    }
}
