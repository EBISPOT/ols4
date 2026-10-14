"""Real executable -> binary COPY -> loader CLI -> restored PG17 contracts.

Run in Dockerfile.postgres-contract, which supplies PostgreSQL and pgvector.
No mocks and no external services. The loader owns its disposable cluster;
queries restart its packaged output, so persistence is checked after shutdown.
"""
import gzip
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest

import pyarrow as pa
import pyarrow.parquet as pq

DATALOAD = Path(__file__).resolve().parents[1]
ENV = {**os.environ, "PGUSER": "postgres", "PGDATABASE": "ols4", "PGHOST": "/tmp", "PGPORT": "5432"}


def run(*args, check=True):
    result = subprocess.run([str(a) for a in args], capture_output=True, text=True, env=ENV, timeout=120)
    if check and result.returncode:
        raise AssertionError(f"{args[0]} failed:\n{result.stdout}\n{result.stderr}")
    return result


def literal(value):
    return {"type": ["literal"], "value": value}


def fixture():
    # ontologyId/iri precede entity arrays, matching the executable file protocol.
    root = {"iri": "https://example.test/root", "type": ["class"],
            "label": [literal('Café "root"'), {**literal("Racine"), "lang": "fr"}],
            "shortForm": literal("ROOT_1"), "curie": literal("ROOT:1"),
            "synonym": [literal("shared synonym"), {"type": ["reification"], "value": literal("reified synonym"), "axioms": []}],
            "definition": [literal("A root definition")], "isDefiningOntology": True,
            "hasDirectChildren": True, "isPreferredRoot": True,
            "https://example.test/filter": [literal("alpha"), literal("βeta")],
            "http://www.geneontology.org/formats/oboInOwl#inSubset": ["https://example.test/subset"], "curatedFromSources": ["manual"]}
    child = {"iri": "https://example.test/child", "type": ["class"], "label": literal("Child"),
             "synonym": [literal("shared synonym")], "isObsolete": True,
             "isDefiningOntology": True, "hasDirectParents": True, "hasHierarchicalParents": True,
             "directParent": [root["iri"]], "hierarchicalParent": [{"type": ["reification"], "value": root["iri"], "axioms": []}],
             "directAncestor": [root["iri"]], "hierarchicalAncestor": [root["iri"]],
             "relatedTo": ["https://example.test/instance"]}
    return {"ontologies": [
        {"ontologyId": "test", "iri": "https://example.test/ontology", "preferredPrefix": "TEST", "label": literal("Test ontology"),
         "classes": [root, child],
         "properties": [{"iri": "https://example.test/relation", "type": ["property", "objectProperty"], "label": literal("Relation")}],
         "individuals": [{"iri": "https://example.test/instance", "type": ["individual"], "label": literal("Instance")}]},
        {"ontologyId": "other", "iri": "https://example.test/other", "classes": [], "properties": [], "individuals": []}]}


class PostgresBoundaryContract(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="ols-pg-contract-")
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.input = self.base / "ontology.json"
        self.input.write_text(json.dumps(fixture(), ensure_ascii=False))
        self.data = self.base / "binary"
        self.data.mkdir()
        self.output = self.base / "cluster"
        self.output.mkdir()

    def convert(self, *extra, check=True):
        return run("ols_json2postgres", "--input", self.input, "--outDir", self.data, *extra, check=check)

    def load(self, *extra, check=True):
        return run(sys.executable, DATALOAD / "load_into_postgres.py", self.output, self.data, *extra, check=check)

    def restart_packaged(self):
        archive = self.base / "postgres.tgz"
        self.assertTrue(archive.is_file())
        restored = self.base / "restored"
        restored.mkdir()
        with tarfile.open(archive) as tar:
            # This archive is produced locally by the loader under test.
            tar.extractall(restored)
        pgdata = restored / "data"
        run("pg_ctl", "-D", pgdata, "-l", restored / "log", "start", "-w")
        self.addCleanup(lambda: run("pg_ctl", "-D", pgdata, "stop", "-w"))

    def query(self, sql):
        result = run("psql", "-XAt", "-v", "ON_ERROR_STOP=1", "-c", sql)
        return json.loads(result.stdout)

    def test_persisted_entities_schema_search_artifacts_and_vectors(self):
        vectors = self.base / "tiny.parquet"
        pq.write_table(pa.table({
            "ontology_id": ["test", "test", "test"], "entity_type": ["class"] * 3,
            "iri": ["https://example.test/root"] * 3,
            "string_type": ["LABEL", "LABEL", "CURATION"],
            "embedding": pa.array([[1., 0., 0.], [0., 1., 0.], [0., 0., 1.]], type=pa.list_(pa.float32()))}), vectors)
        self.convert("--ontology-id", "TEST", "--filterProperty", "https://example.test/filter", "--embeddingParquets", vectors)
        self.assertEqual(sorted(f.name for f in self.data.iterdir()), ["test_autosuggest.pgbin", "test_embedding_nodes.pgbin", "test_entities.pgbin"])
        artifacts = self.base / "artifacts"
        artifacts.mkdir()
        model = b'{"components":[1,2]}'
        (artifacts / "tiny_pca.json").write_bytes(model)
        (artifacts / "tiny_pca16.json").write_bytes(b"excluded")
        tagger = gzip.compress(b"test tagger")
        (artifacts / "text_tagger_db.bin.gz").write_bytes(tagger)
        self.load("--filter-property", "https://example.test/filter", "--artifacts-dir", artifacts,
                  "--parallel-workers", "1", "--maintenance-work-mem", "64MB", vectors)
        self.restart_packaged()
        rows = self.query("SELECT json_agg(t ORDER BY id) FROM (SELECT id,type,iri,ontology_id,label,synonym,definition,is_obsolete,short_form,curie,obo_id,direct_parents,hierarchical_parents,direct_ancestors,hierarchical_ancestors,related_to,is_defining_ontology,is_preferred_root,has_direct_children,has_direct_parents,ontology_iri,ontology_preferred_prefix,subset,curated_from_sources,\"filter_https://example.test/filter\" AS filter_values,\"embeddings_tiny\"::text AS embedding,encode(_json,'hex') AS raw FROM ols_entities) t")
        self.assertEqual(len(rows), 5)
        by_iri = {row["iri"]: row for row in rows}
        root = by_iri["https://example.test/root"]
        self.assertEqual(root["id"], "test+class+https://example.test/root")
        self.assertEqual(root["type"], "OntologyClass")
        self.assertEqual(root["label"], ['Café "root"', "Racine"])
        self.assertEqual(root["synonym"], ["shared synonym", "reified synonym"])
        self.assertEqual(root["definition"], ["A root definition"])
        self.assertEqual(root["filter_values"], ["alpha", "βeta"])
        self.assertEqual(root["embedding"], "[0.5,0.5,0]")
        self.assertEqual([root[k] for k in ["short_form", "curie", "obo_id"]], ["ROOT_1", "ROOT:1", "ROOT:1"])
        self.assertTrue(root["is_defining_ontology"] and root["is_preferred_root"] and root["has_direct_children"])
        self.assertEqual(root["subset"], ["https://example.test/subset"])
        self.assertEqual(root["curated_from_sources"], ["manual"])
        self.assertEqual(root["ontology_iri"], "https://example.test/ontology")
        self.assertEqual(root["ontology_preferred_prefix"], "TEST")
        self.assertEqual(json.loads(gzip.decompress(bytes.fromhex(root["raw"]))), fixture()["ontologies"][0]["classes"][0])
        child = by_iri["https://example.test/child"]
        for name in ["direct_parents", "hierarchical_parents", "direct_ancestors", "hierarchical_ancestors"]:
            self.assertEqual(child[name], [root["iri"]])
        self.assertTrue(child["is_obsolete"] and child["has_direct_parents"])
        self.assertEqual(child["related_to"], ["https://example.test/instance"])
        self.assertEqual(child["filter_values"], [])
        self.assertIsNone(child["embedding"])
        for iri, kind in [("https://example.test/relation", "OntologyProperty"), ("https://example.test/instance", "OntologyIndividual"), ("https://example.test/ontology", "Ontology")]:
            self.assertEqual(by_iri[iri]["type"], kind)
        embeddings = self.query("SELECT json_agg(t ORDER BY type,embedding) FROM (SELECT entity_id,ontology_id,entity_type,type,embedding_tiny::text AS embedding FROM ols_embedding_nodes) t")
        self.assertEqual(len(embeddings), 3)
        self.assertEqual({r["type"] for r in embeddings}, {"LabelEmbedding", "CurationEmbedding"})
        self.assertEqual({r["entity_id"] for r in embeddings}, {root["id"]})
        # Every embedding row repeats its entity's ontology and type, so a scoped vector search can filter without joining.
        self.assertEqual({(r["ontology_id"], r["entity_type"]) for r in embeddings}, {(root["ontology_id"], root["type"])})
        self.assertEqual(self.query("SELECT count(*) FROM ols_autosuggest WHERE string='shared synonym'"), 1)
        self.assertEqual(self.query("SELECT count(*) FROM ols_entities WHERE ts_search @@ plainto_tsquery('english','reified')"), 1)
        indexes = self.query("SELECT json_object_agg(indexname,indexdef) FROM pg_indexes WHERE schemaname='public'")
        self.assertIn("USING gin", indexes["idx_ent_label_fts"])
        self.assertIn("text_pattern_ops", indexes["idx_autosuggest_prefix"])
        self.assertIn("USING hnsw", indexes["idx_emb_tiny_label"])
        self.assertIn("(ontology_id, type, entity_type) WHERE (embedding_tiny IS NOT NULL)", indexes["idx_emb_tiny_scope"])
        self.assertTrue(any('filter_https://example.test/filter' in d for d in indexes.values()))
        self.assertEqual(self.query("SELECT json_agg(extname ORDER BY extname) FROM pg_extension WHERE extname IN ('vector','pg_trgm')"), ["pg_trgm", "vector"])
        self.assertEqual(self.query("SELECT json_object_agg(attname,format_type(atttypid,atttypmod)) FROM pg_attribute WHERE attrelid='ols_entities'::regclass AND attname IN ('label','_json','is_obsolete','embeddings_tiny')"), {"label":"text[]", "_json":"bytea", "is_obsolete":"boolean", "embeddings_tiny":"vector(3)"})
        self.assertEqual(self.query("SELECT json_agg(name) FROM ols_pca_models"), ["tiny_pca"])
        self.assertEqual(self.query("SELECT to_json(encode(model,'hex')) FROM ols_pca_models"), model.hex())
        self.assertEqual(self.query("SELECT to_json(encode(lo_get(tagger_db_oid),'hex')) FROM ols_text_tagger"), tagger.hex())

    def test_no_embeddings_and_empty_collections_load(self):
        self.convert()
        self.load()
        self.restart_packaged()
        self.assertEqual(self.query("SELECT count(*) FROM ols_entities"), 6)
        self.assertEqual(self.query("SELECT count(*) FROM ols_embedding_nodes"), 0)
        self.assertEqual(self.query("SELECT json_agg(ontology_id ORDER BY ontology_id) FROM ols_entities WHERE type='Ontology'"), ["other", "test"])

    def test_corrupt_copy_fails_without_packaging_or_running_server(self):
        self.convert()
        (self.data / "test_entities.pgbin").write_bytes(b"not a COPY stream")
        result = self.load(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("ols_entities load failed", result.stderr)
        self.assertFalse((self.base / "postgres.tgz").exists())
        self.assertNotEqual(run("pg_ctl", "-D", self.output / "data", "status", check=False).returncode, 0)

    def test_malformed_json_is_a_converter_failure(self):
        self.input.write_text('{"ontologies":[')
        result = self.convert(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Failed to convert JSON", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
