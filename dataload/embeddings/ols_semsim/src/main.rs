use anyhow::{anyhow, bail, Context, Result};
use clap::Parser;
use polars::lazy::dsl::{col, lit};
use polars::prelude::*;
use rayon::prelude::*;
use std::io::{self, Write};
use std::sync::atomic::{AtomicUsize, Ordering};

/// Pairwise cosine similarity between entities of two ontologies from a Parquet file.
///
/// Required columns:
///   ontology_id, entity_type, iri, label, hash, text_to_embed, embedding (List<Float32>)
#[derive(Parser, Debug)]
#[command(version, about)]
struct Args {
    /// Path to the input Parquet file
    #[arg(long)]
    parquet: String,

    /// Ontology ID A (subjects)
    #[arg(long)]
    a: String,

    /// Ontology ID B (objects)
    #[arg(long)]
    b: String,

    /// Cosine similarity threshold (inclusive)
    #[arg(long)]
    threshold: f32,
}

#[derive(Clone)]
struct Item {
    iri: String,
    label: String,
    emb: Vec<f32>,
    norm: f32,
}

struct Match {
    subject_iri: String,
    subject_label: String,
    object_iri: String,
    object_label: String,
    cosine_similarity: f32,
}

fn main() -> Result<()> {
    let args = Args::parse();

    let path = PlPath::new(&args.parquet);
    
    let df_a = LazyFrame::scan_parquet(path.clone(), Default::default())
        .context("failed to scan parquet")?
        .select(&[col("ontology_id"), col("iri"), col("label"), col("embedding")])
        .filter(col("ontology_id").eq(lit(args.a.clone())))
        .collect()
        .context("failed to collect A")?;
    
    let df_b = LazyFrame::scan_parquet(path, Default::default())
        .context("failed to scan parquet")?
        .select(&[col("ontology_id"), col("iri"), col("label"), col("embedding")])
        .filter(col("ontology_id").eq(lit(args.b.clone())))
        .collect()
        .context("failed to collect B")?;

    for needed in ["iri", "label", "embedding"] {
        if !df_a.get_column_names().iter().any(|c| c == &needed) {
            bail!("missing required column: {needed}");
        }
    }
    
    // Check if embedding column is List<Float32> or Array<Float32, N>
    let emb_dtype = df_a.column("embedding")?.dtype();
    match emb_dtype {
        DataType::List(inner) if **inner == DataType::Float32 => {
            // Variable-length list is fine
        }
        DataType::Array(inner, _) if **inner == DataType::Float32 => {
            // Fixed-size array is fine
        }
        _ => {
            bail!("column 'embedding' must be List<Float32> or Array<Float32, N>, found {:?}", emb_dtype);
        }
    }

    let items_a = extract_items(&df_a).context("failed to extract items for A")?;
    let items_b = extract_items(&df_b).context("failed to extract items for B")?;

    eprintln!("DEBUG: Found {} items in ontology A ({})", items_a.len(), args.a);
    eprintln!("DEBUG: Found {} items in ontology B ({})", items_b.len(), args.b);
    if items_a.is_empty() || items_b.is_empty() {
        eprintln!("DEBUG: One or both ontologies have no items - checking available ontology_ids...");
        let all_ontologies = LazyFrame::scan_parquet(PlPath::new(&args.parquet), Default::default())
            .context("failed to scan parquet for debug")?
            .select(&[col("ontology_id")])
            .unique(None, UniqueKeepStrategy::First)
            .collect()
            .context("failed to collect ontology_ids")?;
        eprintln!("DEBUG: Available ontology_ids in file:");
        for val in all_ontologies.column("ontology_id")?.str()?.into_iter() {
            eprintln!("  - {:?}", val);
        }
    }

    println!("subject_id\tsubject_label\tobject_id\tobject_label\tcosine_similarity");
    io::stdout().flush().ok();

    let threshold = args.threshold;
    let total = items_a.len();
    let processed = AtomicUsize::new(0);

    eprintln!("Computing pairwise similarities for {} × {} = {} comparisons...", 
              total, items_b.len(), total * items_b.len());

    items_a.par_iter().for_each(|a| {
        let mut matches: Vec<Match> = items_b
            .par_iter()
            .filter_map(|b| {
                if a.emb.len() != b.emb.len() {
                    panic!("embedding length mismatch between {} and {}", a.iri, b.iri);
                }
                let dot = dot_f32(&a.emb, &b.emb);
                let denom = a.norm * b.norm;
                if denom == 0.0 {
                    panic!("zero norm for {} or {}", a.iri, b.iri);
                }
                let cos_sim = dot / denom;
                if cos_sim >= threshold {
                    Some(Match {
                        subject_iri: a.iri.clone(),
                        subject_label: a.label.clone(),
                        object_iri: b.iri.clone(),
                        object_label: b.label.clone(),
                        cosine_similarity: cos_sim,
                    })
                } else {
                    None
                }
            })
            .collect();

        // Sort by cosine similarity in descending order (highest first)
        matches.sort_by(|a, b| {
            b.cosine_similarity.partial_cmp(&a.cosine_similarity).unwrap_or(std::cmp::Ordering::Equal)
        });

        if !matches.is_empty() {
            let mut handle = io::stdout().lock();
            for m in matches {
                let _ = writeln!(handle, "{}\t{}\t{}\t{}\t{:.6}",
                    m.subject_iri, m.subject_label, m.object_iri, m.object_label, m.cosine_similarity);
            }
        }

        let count = processed.fetch_add(1, Ordering::Relaxed) + 1;
        if count % 100 == 0 || count == total {
            eprintln!("Progress: {}/{} subjects processed ({:.1}%)", 
                      count, total, (count as f64 / total as f64) * 100.0);
        }
    });

    Ok(())
}

fn extract_items(df: &DataFrame) -> Result<Vec<Item>> {
    let n = df.height();
    let iri_s = df.column("iri")?;
    let label_s = df.column("label")?;
    let emb_col = df.column("embedding")?;

    let mut out = Vec::with_capacity(n);
    
    // Check if it's a list or array type
    let is_list = matches!(emb_col.dtype(), DataType::List(_));
    let is_array = matches!(emb_col.dtype(), DataType::Array(_, _));

    if !is_list && !is_array {
        bail!("'embedding' must be List<Float32> or Array<Float32, N>");
    }

    for i in 0..n {
        let iri_val = iri_s
            .str()
            .map_err(|_| anyhow!("iri column is not string type"))?
            .get(i)
            .ok_or_else(|| anyhow!("null or non-utf8 iri at row {i}"))?
            .to_string();
        let label_val = label_s
            .str()
            .map_err(|_| anyhow!("label column is not string type"))?
            .get(i)
            .unwrap_or("")
            .to_string();

        let emb: Vec<f32> = if is_list {
            let emb_list = emb_col.list()
                .map_err(|_| anyhow!("'embedding' must be List<Float32>"))?;
            let inner = emb_list
                .get_as_series(i)
                .ok_or_else(|| anyhow!("null embedding at row {i}"))?;
            if inner.dtype() != &DataType::Float32 {
                bail!("embedding row {i} not Float32 (found {:?})", inner.dtype());
            }
            inner
                .f32()
                .map_err(|_| anyhow!("embedding row {i} not Float32"))?
                .into_no_null_iter()
                .collect()
        } else {
            // It's an array type
            let emb_array = emb_col.array()
                .map_err(|_| anyhow!("'embedding' must be Array<Float32, N>"))?;
            let inner = emb_array
                .get_as_series(i)
                .ok_or_else(|| anyhow!("null embedding at row {i}"))?;
            if inner.dtype() != &DataType::Float32 {
                bail!("embedding row {i} not Float32 (found {:?})", inner.dtype());
            }
            inner
                .f32()
                .map_err(|_| anyhow!("embedding row {i} not Float32"))?
                .into_no_null_iter()
                .collect()
        };

        let norm = l2_norm(&emb);
        out.push(Item {
            iri: iri_val,
            label: label_val,
            emb,
            norm,
        });
    }
    Ok(out)
}

#[inline]
fn dot_f32(a: &[f32], b: &[f32]) -> f32 {
    let mut acc = 0.0f32;
    for i in 0..a.len() {
        acc += a[i] * b[i];
    }
    acc
}

#[inline]
fn l2_norm(v: &[f32]) -> f32 {
    let mut acc = 0.0f32;
    for &x in v {
        acc += x * x;
    }
    acc.sqrt()
}

