
CREATE INDEX FOR (n:OwlClass) ON n.id;
CREATE INDEX FOR (n:OwlProperty) ON n.id;
CREATE INDEX FOR (n:OwlIndividual) ON n.id;

CALL db.awaitIndexes(1800);


