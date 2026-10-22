import unittest
from pathlib import Path

from dataload.load_into_postgres import generate_schema, index_statements


class LoaderIndexStatementsTest(unittest.TestCase):
    def test_generated_commented_indexes_reach_execution(self):
        sections = generate_schema(Path(__file__).resolve().parents[1], [])
        statements = index_statements(sections["indexes"])
        for name in ("idx_ent_label_lower", "idx_ent_label_fts", "idx_ent_st_obs_id",
                     "idx_ent_lower_iri", "idx_autosuggest_prefix"):
            self.assertTrue(any(f"CREATE INDEX {name} " in sql for sql in statements), name)
        self.assertTrue(all(sql.startswith("CREATE INDEX") for sql in statements))

    def test_comment_semicolons_do_not_create_sql_fragments(self):
        self.assertEqual(index_statements("-- documentation; still a comment\nCREATE INDEX idx ON t (id);"),
                         ["CREATE INDEX idx ON t (id)"])


if __name__ == "__main__":
    unittest.main()
