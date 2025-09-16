import sys
import ijson
import chromadb
from chromadb.config import Settings
from tqdm import tqdm
import time
import sqlite3
import json

if len(sys.argv) < 2:
    print("Usage: python ols_top_k.py <path_to_embeddings_db>")
    sys.exit(1)

db_path = sys.argv[1]

conn = sqlite3.connect(f'file:{db_path}?mode=ro', uri=True)
cursor = conn.cursor()

chroma_client = chromadb.Client(Settings(anonymized_telemetry=False))
collections = {
    "class": chroma_client.create_collection(name="class_embeddings"),
    "property": chroma_client.create_collection(name="property_embeddings"),
    "individual": chroma_client.create_collection(name="individual_embeddings"),
}

total_entities_added = {
    "class": 0,
    "property": 0,
    "individual": 0,
}
BATCH_SIZE = 100

def fetch_embedding_from_db(ontology_id, entity_type, iri):
    try:
        cursor.execute(
            """
            SELECT embeddings FROM embeddings
            WHERE ontologyId = ? AND entityType = ? AND iri = ?
            """, (ontology_id, entity_type, iri)
        )
        row = cursor.fetchone()
        if row:
            res = json.loads(row[0])
            # if its an array just return it
            if isinstance(res, list):
                return res
            # temp hack to be removed
            elif isinstance(res, dict) and 'embedding' in res:
                return res['embedding']
        else:
            return None
    except Exception as e:
        print(f"    ⚠️ DB lookup failed for {ontology_id}:{entity_type}:{iri}: {e}", file=sys.stderr)
        return None

def parse_ontology_entities(entities, ontology_id, entity_type):
    collection = collections[entity_type]
    batch_embeddings, batch_ids, batch_docs = [], [], []

    added = 0

    for entity in tqdm(entities, desc=f"  Parsing {entity_type} entities for {ontology_id}", unit="entity"):
        iri = entity.get("iri")
        if iri is None:
            continue

        is_defining = entity.get("isDefiningOntology")
        if is_defining is not None and is_defining == False:
            continue

        embedding = fetch_embedding_from_db(ontology_id, entity_type, iri)
        if embedding is None:
            continue

        try:
            flat_embedding = [float(x) for x in embedding]
        except Exception as e:
            print(f"    ⚠️ Skipping invalid embedding for {ontology_id}:{entity_type}:{iri}: {e}", file=sys.stderr)
            continue

        doc_id = f"{ontology_id}:{entity_type}:{iri}"
        batch_embeddings.append(flat_embedding)
        batch_ids.append(doc_id)
        batch_docs.append("")  # Optional document text, kept empty

        if len(batch_embeddings) >= BATCH_SIZE:
            try:
                collection.add(documents=batch_docs, embeddings=batch_embeddings, ids=batch_ids)
                added += len(batch_embeddings)
            except Exception as e:
                print(f"    ⚠️ Failed to batch add vectors: {e}", file=sys.stderr)
            finally:
                batch_embeddings.clear()
                batch_ids.clear()
                batch_docs.clear()

    if batch_embeddings:
        try:
            collection.add(documents=batch_docs, embeddings=batch_embeddings, ids=batch_ids)
            added += len(batch_embeddings)
        except Exception as e:
            print(f"    ⚠️ Failed to batch add final vectors: {e}", file=sys.stderr)

    total_entities_added[entity_type] += added
    if added > 0:
        print(f"    → Added {added} {entity_type} embeddings for ontology '{ontology_id}'", file=sys.stderr)

def process_input():
    objects = ijson.items(sys.stdin, "ontologies.item", use_float=True)
    count = 0
    for ontology in objects:
        ontology_id = ontology.get("ontologyId", f"unknown_{count}")
        print(f"\nProcessing ontology #{count+1}: {ontology_id}", file=sys.stderr)
        for key, value in ontology.items():
            if key == "classes":
                parse_ontology_entities(value, ontology_id, "class")
            elif key == "properties":
                parse_ontology_entities(value, ontology_id, "property")
            elif key == "individuals":
                parse_ontology_entities(value, ontology_id, "individual")
        count += 1
    print(f"\n✅ Finished processing {count} ontologies", file=sys.stderr)
    total = sum(total_entities_added.values())
    print(f"Total entity embeddings added: {total}", file=sys.stderr)

def query_top_k(query_embedding, collection, k=50):
    return collection.query(query_embeddings=[query_embedding], n_results=k)

if __name__ == "__main__":
    start_time = time.time()
    process_input()

    print(f"entity_type\tiri_1\tontology_id_1\tiri_2\tontology_id_2\tdistance")
    for entity_type, collection in collections.items():
        all_embeddings = collection.get(include=["embeddings"])
        embeddings = all_embeddings["embeddings"]
        ids = all_embeddings["ids"]

        if len(embeddings) == 0:
            continue

        for embedding, eid in zip(embeddings, ids):
            results = query_top_k(embedding, collection, k=50)
            result_ids = results["ids"][0]
            scores = results["distances"][0]

            try:
                ontologyid1, iri1_type, iri1 = eid.split(":", 2)
            except ValueError:
                continue

            for result_id, score in zip(result_ids, scores):
                try:
                    ontologyid2, iri2_type, iri2 = result_id.split(":", 2)
                except ValueError:
                    continue

                print(f"{entity_type}\t{iri1}\t{ontologyid1}\t{iri2}\t{ontologyid2}\t{score}")
