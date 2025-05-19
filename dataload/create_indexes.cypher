
CREATE INDEX FOR (n:OntologyClass) ON n.id;
CREATE INDEX FOR (n:OntologyIndividual) ON n.id;
CREATE INDEX FOR (n:OntologyProperty) ON n.id;
CREATE INDEX FOR (n:OntologyEntity) ON n.id;

CREATE INDEX FOR (n:OntologyClass) ON n.iri;

CREATE VECTOR INDEX class_embeddings IF NOT EXISTS
FOR (n:OntologyClass) ON n.embeddings OPTIONS { indexConfig: {
 `vector.dimensions`: 1536,
 `vector.similarity_function`: 'cosine'
}};

CREATE VECTOR INDEX property_embeddings IF NOT EXISTS
FOR (n:OntologyProperty) ON n.embeddings OPTIONS { indexConfig: {
 `vector.dimensions`: 1536,
 `vector.similarity_function`: 'cosine'
}};

CALL db.awaitIndexes(10800);


