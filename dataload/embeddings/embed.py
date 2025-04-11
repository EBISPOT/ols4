import os
import argparse
import hashlib
import sqlite3
from openai import OpenAI
import json_stream
import gzip
import concurrent.futures
import traceback
import tiktoken

enc = tiktoken.get_encoding("cl100k_base")

oai_client = OpenAI()
model = "text-embedding-3-small"

def init_db(db_path):
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS embeddings (
            hash TEXT,
            ontologyId TEXT,
            entityType TEXT,
            iri TEXT,
            model TEXT,
            embeddings TEXT,
            PRIMARY KEY (hash, ontologyId, entityType)
        )
    ''')
    conn.commit()
    return conn

def insert_embedding(conn, ontologyId, iri, document_hash, embedding_vector, entity_type='class'):
    cursor = conn.cursor()
    cursor.execute('''
        INSERT OR REPLACE INTO embeddings (hash, ontologyId, entityType, iri, model, embeddings)
        VALUES (?, ?, ?, ?, ?, ?)
    ''', (document_hash, ontologyId, entity_type, iri, model, embedding_vector))
    conn.commit()

def get_embedding_by_hash(conn, document_hash):
    cursor = conn.cursor()
    cursor.execute('SELECT embeddings FROM embeddings WHERE hash = ? LIMIT 1', (document_hash,))
    row = cursor.fetchone()
    return row[0] if row else None

def get_embeddings_parallel(documents):
    batch_size = 2000
    batches = [documents[i:i + batch_size] for i in range(0, len(documents), batch_size)]

    with concurrent.futures.ThreadPoolExecutor(max_workers=20) as executor:
        futures = {
            executor.submit(oai_client.embeddings.create, model=model, input=batch): i
            for i, batch in enumerate(batches)
        }

        responses = []
        for future in concurrent.futures.as_completed(futures):
            batch_index = futures[future]
            response = future.result()
            embeddings = [embedding.embedding for embedding in response.data]
            responses.append((batch_index, embeddings))

        responses.sort(key=lambda x: x[0])
        embeddings = [embedding for _, batch_embeddings in responses for embedding in batch_embeddings]

    return embeddings

def compute_sha1(text):
    return hashlib.sha1(text.encode('utf-8')).hexdigest()

def item_to_doc(item):
    try:
        labels = [str(label.get("value", "")) for label in item.get("label") or []]
        synonyms = [str(synonym.get("value", "")) for synonym in item.get("synonym") or []]
        definition = [str(definition.get("value", "")) for definition in item.get("definition") or []]
        return '; '.join(labels + synonyms + definition).strip()
    except Exception:
        print(traceback.format_exc())
        return ""

def process_batch(batch, conn, stats):
    print("Processing batch of size:", len(batch))
    documents_to_embed = []
    embed_indexes = []

    hashes = []
    iris = []
    ont_ids = []
    reused = []

    for i, (ont_id, item) in enumerate(batch):
        document = item_to_doc(item)
        document_hash = compute_sha1(document)

        if not document or len(enc.encode(document)) > 8190:
            print(f"Skipping {item.get('iri')} due to empty or too long text")
            continue

        existing_embedding = get_embedding_by_hash(conn, document_hash)

        iris.append(str(item['iri']))
        hashes.append(document_hash)
        ont_ids.append(ont_id)

        if existing_embedding:
            reused.append((i, existing_embedding))
            stats["cached"] += 1
        else:
            documents_to_embed.append(document)
            embed_indexes.append(i)

    if documents_to_embed:
        stats["retrieved"] += len(documents_to_embed)

    embeddings_data = get_embeddings_parallel(documents_to_embed) if documents_to_embed else []

    all_embeddings = [None] * len(hashes)
    for idx, embedding_str in reused:
        all_embeddings[idx] = embedding_str
    for j, embedding in enumerate(embeddings_data):
        i = embed_indexes[j]
        all_embeddings[i] = ','.join(map(str, embedding))

    for i in range(len(hashes)):
        insert_embedding(conn, ont_ids[i], iris[i], hashes[i], all_embeddings[i])

def stream_json(input_file, conn):
    batch_size = 20000
    current_batch = []
    stats = {"cached": 0, "retrieved": 0}

    with open(input_file, 'r') as f:
        data = json_stream.load(f)
        onts = data['ontologies']
        n = 0
        for ontology in onts:
            ont_id = str(ontology['ontologyId'])
            classes = ontology['classes'].persistent()
            for class_item in classes:
                current_batch.append((ont_id, class_item))
                if len(current_batch) >= batch_size:
                    process_batch(current_batch, conn, stats)
                    n += len(current_batch)
                    print(str(n), flush=True)
                    current_batch = []

        if current_batch:
            process_batch(current_batch, conn, stats)
            n += len(current_batch)
            print(str(n), flush=True)

    print("\n=== Embedding Summary ===")
    print(f"Reused from cache: {stats['cached']}")
    print(f"Retrieved from API: {stats['retrieved']}")

def main():
    parser = argparse.ArgumentParser(description="Stream JSON and store OpenAI embeddings in SQLite")
    parser.add_argument('--input-file', type=str, required=True, help="Input JSON file")
    parser.add_argument('--db-path', type=str, required=True, help="SQLite database file")
    args = parser.parse_args()

    conn = init_db(args.db_path)

    stream_json(args.input_file, conn)

    print(f"\nEmbeddings have been written to {args.db_path}", flush=True)

if __name__ == '__main__':
    main()
