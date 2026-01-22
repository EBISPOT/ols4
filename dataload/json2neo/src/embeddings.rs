use std::collections::HashMap;
use std::fs::File;

use arrow::array::{Array, Float32Array, ListArray, StringArray};
use parquet::arrow::arrow_reader::ParquetRecordBatchReaderBuilder;

pub struct Embeddings {
    pub embeddings_cache: HashMap<String, Vec<f32>>,
}

impl Embeddings {
    pub fn new() -> Self {
        Self {
            embeddings_cache: HashMap::new(),
        }
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

            let ontology_id_col = batch
                .column_by_name("ontology_id")
                .ok_or("Missing ontology_id column")?
                .as_any()
                .downcast_ref::<StringArray>()
                .ok_or("ontology_id is not a string column")?;

            let entity_type_col = batch
                .column_by_name("entity_type")
                .ok_or("Missing entity_type column")?
                .as_any()
                .downcast_ref::<StringArray>()
                .ok_or("entity_type is not a string column")?;

            let iri_col = batch
                .column_by_name("iri")
                .ok_or("Missing iri column")?
                .as_any()
                .downcast_ref::<StringArray>()
                .ok_or("iri is not a string column")?;

            let embedding_col = batch
                .column_by_name("embedding")
                .ok_or("Missing embedding column")?;

            // Handle the embedding column - it's a list of floats
            let embedding_list = embedding_col
                .as_any()
                .downcast_ref::<ListArray>()
                .ok_or("embedding is not a list column")?;

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

                // Extract embedding values
                let embedding_array = embedding_list.value(i);
                let float_array = embedding_array
                    .as_any()
                    .downcast_ref::<Float32Array>()
                    .ok_or("embedding elements are not f32")?;

                let embedding: Vec<f32> = (0..float_array.len())
                    .map(|j| float_array.value(j))
                    .collect();

                let key = Self::make_key(ontology_id, entity_type, iri);
                self.embeddings_cache.insert(key, embedding);
            }
        }

        Ok(())
    }

    fn make_key(ontology_id: &str, entity_type: &str, iri: &str) -> String {
        format!("{}|{}|{}", ontology_id, entity_type, iri)
    }

    pub fn get_embeddings(&self, ontology_id: &str, entity_type: &str, iri: &str) -> Option<&Vec<f32>> {
        let key = Self::make_key(ontology_id, entity_type, iri);
        self.embeddings_cache.get(&key)
    }
}
