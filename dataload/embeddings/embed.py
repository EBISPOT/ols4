import os
import csv
import argparse
import hashlib
from openai import OpenAI
import json_stream  # Importing jsonstream for efficient JSON parsing
import gzip
import concurrent.futures  # For parallel execution
import traceback

oai_client = OpenAI()
model = "text-embedding-3-small"

def get_embeddings_parallel(documents):
    """Get embeddings for documents in parallel (5 requests of 2000 documents) while preserving the order."""
    batch_size = 2000
    batches = [documents[i:i + batch_size] for i in range(0, len(documents), batch_size)]
    
    with concurrent.futures.ThreadPoolExecutor(max_workers=20) as executor:
        futures = {
            executor.submit(oai_client.embeddings.create, model=model, input=batch): i
            for i, batch in enumerate(batches)
        }
        
        responses = []
        # Process results in the order batches were submitted
        for future in concurrent.futures.as_completed(futures):
            batch_index = futures[future]  # Retrieve the batch index
            response = future.result()
            embeddings = [embedding.embedding for embedding in response.data]
            responses.append((batch_index, embeddings))
        
        # Sort the responses based on the batch index to preserve the original order
        responses.sort(key=lambda x: x[0])  # Sort by the batch index
        
        # Flatten the list of embeddings while preserving the original order
        embeddings = [embedding for _, batch_embeddings in responses for embedding in batch_embeddings]
    
    return embeddings

def compute_sha1(text):
    """Compute SHA1 hash for a given text."""
    return hashlib.sha1(text.encode('utf-8')).hexdigest()

def load_existing_hashes(output_csv):
    """Load the existing hashes from the output CSV into a dictionary."""
    embeddings_cache = {}
    try:
        with gzip.open(output_csv, 'rt') as out_csv:
            csv_reader = csv.DictReader(out_csv, delimiter='\t')
            for row in csv_reader:
                if 'hash' not in row:
                    continue
                if 'embeddings' not in row:
                    continue
                embeddings_cache[row['hash']] = row['embeddings']
    except FileNotFoundError:
        print("Existing embeddings file not found")
        pass
    print("Loaded " + str(len(embeddings_cache)) + " existing embeddings")
    return embeddings_cache

def item_to_doc(item):
    try:
        labels = [str(label.get("value", "")) for label in item.get("label") or []]
        synonyms = [str(synonym.get("value", "")) for synonym in item.get("synonym") or []]
        definition = [str(definition.get("value", "")) for definition in item.get("definition") or []]
        document = '; '.join(labels + synonyms + definition).strip()
        return document
    except Exception as e:
        print(traceback.format_exc())
        return ""

def process_batch(batch, embeddings_cache, csv_writer):
    print("Processing batch of size:", len(batch))
    documents = []
    iris = []
    hashes = []
    
    for _item in batch:
        ont_id, item = _item
        document = item_to_doc(item)
        document_hash = compute_sha1(document)

        if(len(document) == 0):
            continue
        
        # Skip embedding if the hash already exists in the cache
        if document_hash in embeddings_cache:
            embedding_vector = embeddings_cache[document_hash]
            csv_writer.writerow({
                "ontologyId": ont_id,
                "entityType": "class",
                "iri": str(item['iri']),
                "hash": document_hash,
                "model": model,
                "embeddings": embedding_vector
            })
            continue
        
        documents.append(document)
        iris.append(str(item['iri']))
        hashes.append(document_hash)

    embeddings_data = get_embeddings_parallel(documents) if documents else []
    
    n = 0
    for embedding in embeddings_data:
        embedding_hash = hashes[n]
        embedding_vector = ','.join(map(str, embedding))
        embeddings_cache[embedding_hash] = embedding_vector
        csv_writer.writerow({
            "ontologyId": ont_id,
            "entityType": "class",
            "iri": iris[n],
            "hash": embedding_hash,
            "model": model,
            "embeddings": embedding_vector
        })
        n = n + 1

def stream_json(input_file, output_csv, embeddings_cache):
    batch_size = 20000
    current_batch = []
    
    with open(input_file, 'r') as f, gzip.open(output_csv, 'wt') as out_csv:
        csv_writer = csv.DictWriter(out_csv, fieldnames=["ontologyId", "entityType", "iri", "hash", "model", "embeddings"], delimiter='\t')
        if out_csv.tell() == 0: 
            csv_writer.writeheader()

        data = json_stream.load(f)

        n = 0
        onts = data['ontologies']
        for ontology in onts:
            ont_id = str(ontology['ontologyId'])
            classes = ontology['classes'].persistent()
            for class_item in classes:
                current_batch.append((ont_id, class_item))
                if len(current_batch) >= batch_size:
                    process_batch(current_batch, embeddings_cache, csv_writer)
                    n = n + len(current_batch)
                    current_batch = []
                    print(str(n), flush=True)
        if current_batch:
            process_batch(current_batch, embeddings_cache, csv_writer)
            n = n + len(current_batch)
            current_batch = []
            print(str(n), flush=True)

def main():
    parser = argparse.ArgumentParser(description="Stream large JSON file, traverse nested arrays, and get embeddings from OpenAI API")
    parser.add_argument('--input-file', type=str, help="Input JSON file", required=True)
    parser.add_argument('--new-embeddings-tsv', type=str, help="Output TSV file for embeddings", required=True)
    parser.add_argument('--old-embeddings-tsv', type=str, help="Optional old TSV file to update embeddings incrementally", required=False)
    args = parser.parse_args()

    if args.old_embeddings_tsv != None:
        embeddings_cache = load_existing_hashes(args.old_embeddings_tsv)
    else:
        embeddings_cache = {}

    stream_json(args.input_file, args.new_embeddings_tsv, embeddings_cache)  # Process the input file

    print(f"Embeddings have been written to {args.new_embeddings_tsv}", flush=True)

if __name__ == '__main__':
    main()

