
CREATE INDEX FOR (n:OntologyEntity) ON n.id;

CALL db.awaitIndexes(1800);


