import unittest

from dataload.create_postgres_schema import ENTITY_INDEX_SQL


class EntityIndexSqlTest(unittest.TestCase):

    def test_field_restricted_full_text_indexes_are_persistent(self):
        for column in ("label", "synonym", "curie", "short_form", "iri"):
            self.assertIn(
                f"CREATE INDEX idx_ent_{column}_fts ON ols_entities "
                f"USING gin (ols_tsvector({column}));",
                ENTITY_INDEX_SQL,
            )


if __name__ == "__main__":
    unittest.main()
