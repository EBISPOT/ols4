
CREATE INDEX FOR (n:OntologyTerm) ON n.id;

CALL db.awaitIndexes(1800);


