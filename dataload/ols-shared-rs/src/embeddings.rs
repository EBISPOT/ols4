use std::collections::HashMap;
use std::fs::File;

use arrow::array::{Array, FixedSizeListArray, Float32Array, Float64Array, LargeListArray, LargeStringArray, ListArray, StringArray};
use parquet::arrow::arrow_reader::ParquetRecordBatchReaderBuilder;

/// Helper enum to handle both StringArray and LargeStringArray
enum StringColumnRef<'a> {
    Regular(&'a StringArray),
    Large(&'a LargeStringArray),
}

impl<'a> StringColumnRef<'a> {
    fn value(&self, i: usize) -> &str {
        match self {
            StringColumnRef::Regular(arr) => arr.value(i),
            StringColumnRef::Large(arr) => arr.value(i),
        }
    }
}

fn get_string_column<'a>(col: &'a dyn Array, col_name: &str) -> Result<StringColumnRef<'a>, Box<dyn std::error::Error>> {
    if let Some(arr) = col.as_any().downcast_ref::<StringArray>() {
        Ok(StringColumnRef::Regular(arr))
    } else if let Some(arr) = col.as_any().downcast_ref::<LargeStringArray>() {
        Ok(StringColumnRef::Large(arr))
    } else {
        Err(format!("{} is not a string column (expected StringArray or LargeStringArray)", col_name).into())
    }
}

pub struct Embeddings {
    /// Cache of embeddings: key -> list of embedding vectors (multiple per entity)
    pub embeddings_cache: HashMap<String, Vec<Vec<f32>>>,
}

impl Embeddings {
    pub fn new() -> Self {
        Self {
            embeddings_cache: HashMap::new(),
        }
    }

    /// Returns the total number of individual embedding vectors stored
    pub fn total_vectors(&self) -> usize {
        self.embeddings_cache.values().map(|v| v.len()).sum()
    }

    pub fn load_embeddings_from_file(
        &mut self,
        parquet_path: &str,
        filter_by_ontology_id: Option<&str>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let file = File::open(parquet_path)?;
        let builder = ParquetRecordBatchReaderBuilder::try_new(file)?;
        let reader = builder.build()?;

        for batch_result in reader {
            let batch = batch_result?;

            let ontology_id_col = get_string_column(
                batch.column_by_name("ontology_id").ok_or("Missing ontology_id column")?,
                "ontology_id"
            )?;

            let entity_type_col = get_string_column(
                batch.column_by_name("entity_type").ok_or("Missing entity_type column")?,
                "entity_type"
            )?;

            let iri_col = get_string_column(
                batch.column_by_name("iri").ok_or("Missing iri column")?,
                "iri"
            )?;

            let embedding_col = batch
                .column_by_name("embedding")
                .ok_or("Missing embedding column")?;

            // Handle the embedding column - it's a list of floats (could be ListArray, LargeListArray, or FixedSizeListArray)
            let embedding_list: Box<dyn Fn(usize) -> arrow::array::ArrayRef> = 
                if let Some(list) = embedding_col.as_any().downcast_ref::<ListArray>() {
                    Box::new(move |i| list.value(i))
                } else if let Some(list) = embedding_col.as_any().downcast_ref::<LargeListArray>() {
                    Box::new(move |i| list.value(i))
                } else if let Some(list) = embedding_col.as_any().downcast_ref::<FixedSizeListArray>() {
                    Box::new(move |i| list.value(i))
                } else {
                    return Err(format!("embedding is not a list column (got {:?})", embedding_col.data_type()).into());
                };

            for i in 0..batch.num_rows() {
                let ontology_id = ontology_id_col.value(i);

                // Filter by ontology ID if specified
                if let Some(filter_id) = filter_by_ontology_id {
                    if !filter_id.is_empty() && ontology_id != filter_id {
                        continue;
                    }
                }

                let entity_type = entity_type_col.value(i);
                let iri = iri_col.value(i);

                // Extract embedding values - handle both f32 and f64
                let embedding_array = embedding_list(i);
                let embedding: Vec<f32> = if let Some(float_array) = embedding_array.as_any().downcast_ref::<Float32Array>() {
                    (0..float_array.len()).map(|j| float_array.value(j)).collect()
                } else if let Some(float_array) = embedding_array.as_any().downcast_ref::<Float64Array>() {
                    (0..float_array.len()).map(|j| float_array.value(j) as f32).collect()
                } else {
                    return Err(format!("embedding elements are not f32 or f64 (got {:?})", embedding_array.data_type()).into());
                };

                let key = Self::make_key(ontology_id, entity_type, iri);
                self.embeddings_cache.entry(key).or_insert_with(Vec::new).push(embedding);
            }
        }

        Ok(())
    }

    fn make_key(ontology_id: &str, entity_type: &str, iri: &str) -> String {
        format!("{}|{}|{}", ontology_id, entity_type, iri)
    }

    /// Get all embedding vectors for a given entity (multiple per entity: label + synonyms)
    pub fn get_embeddings(&self, ontology_id: &str, entity_type: &str, iri: &str) -> Option<&Vec<Vec<f32>>> {
        let key = Self::make_key(ontology_id, entity_type, iri);
        self.embeddings_cache.get(&key)
    }

    /// Get the mean (average) of all embedding vectors for a given entity
    pub fn get_average_embedding(&self, ontology_id: &str, entity_type: &str, iri: &str) -> Option<Vec<f32>> {
        let vectors = self.get_embeddings(ontology_id, entity_type, iri)?;
        if vectors.is_empty() {
            return None;
        }
        Some(mean_vector(vectors))
    }
}

/// Compute the element-wise mean of a slice of vectors
pub fn mean_vector(vectors: &[Vec<f32>]) -> Vec<f32> {
    if vectors.is_empty() {
        return Vec::new();
    }
    let dim = vectors[0].len();
    let n = vectors.len() as f32;
    let mut result = vec![0.0f32; dim];
    for v in vectors {
        for (i, &val) in v.iter().enumerate() {
            result[i] += val;
        }
    }
    for val in &mut result {
        *val /= n;
    }
    result
}
