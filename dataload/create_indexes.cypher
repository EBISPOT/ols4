
CREATE INDEX FOR (n:OntologyClass) ON n.id;
CREATE INDEX FOR (n:OntologyIndividual) ON n.id;
CREATE INDEX FOR (n:OntologyProperty) ON n.id;
CREATE INDEX FOR (n:OntologyEntity) ON n.id;

CALL db.awaitIndexes(1800);


