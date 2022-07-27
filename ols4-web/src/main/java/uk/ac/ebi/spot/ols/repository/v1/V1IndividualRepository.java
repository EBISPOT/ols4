package uk.ac.ebi.spot.ols.repository.v1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.model.v1.V1Term;

//@RepositoryRestResource(collectionResourceRel = "individuals", exported = false)
@Component
public class V1IndividualRepository {

    @Autowired
    V1OntologyRepository ontologyRepository;


//    @Query(
//            countQuery = "MATCH (n:Individual)-[:INSTANCEOF]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(parent)",
//            value = "MATCH (n:Individual)-[:INSTANCEOF]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN parent")
    public Page<V1Term> getDirectTypes(String ontologyId, String iri, String lang, Pageable pageable) {

//        Ontology ontology = ontologyRepository.get(ontologyId);
//
//        return Neo4jQueryHelper.getParents("OwlProperty", ontologyId + "+" + iri, Arrays.asList("http://www.w3.org/2000/01/rdf-schema#subPropertyOf"), pageable)
//                .map(record -> new Property(record, this, ontology));

        throw new RuntimeException();
    }

//    @Query(countQuery = "MATCH (n:Individual)-[:INSTANCEOF|SUBCLASSOF*]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct parent)",
//            value = "MATCH (n:Individual)-[:INSTANCEOF|SUBCLASSOF*]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct parent")
    public Page<V1Term> getAllTypes(String ontologyName, String iri, String lang, Pageable pageable) { throw new RuntimeException(); }

//    @Query (value = "MATCH (n:Individual) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN n")
    public V1Individual findByOntologyAndIri(String ontologyName, String iri, String lang) { throw new RuntimeException(); }

//    @Query (countQuery = "MATCH (n:Individual {ontology_name : {0}}) RETURN count(n)",
//            value = "MATCH (n:Individual {ontology_name : {0}}) RETURN n")
    public Page<V1Individual> findAllByOntology(String ontologyName, String lang, Pageable pageable) { throw new RuntimeException(); }

//    @Query (value = "MATCH (n:Individual) WHERE n.ontology_name = {0} AND n.short_form = {1} RETURN n")
    public V1Individual findByOntologyAndShortForm(String ontologyId, String lang, String shortForm) { throw new RuntimeException(); }

//    @Query (value = "MATCH (n:Individual) WHERE n.ontology_name = {0} AND n.obo_id = {1} RETURN n")
    public V1Individual findByOntologyAndOboId(String ontologyId, String lang, String oboId) { throw new RuntimeException(); }

//    @Query (countQuery = "MATCH (n:Individual) RETURN count(n)",
//            value = "MATCH (n:Individual) RETURN n")
    public Page<V1Individual> findAll(String lang, Pageable pageable) { throw new RuntimeException(); }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.is_defining_ontology = true RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.is_defining_ontology = true RETURN n")
    public Page<V1Individual> findAllByIsDefiningOntology(String lang, Pageable pageable) { throw new RuntimeException(); }


//    @Query (countQuery = "MATCH (n:Individual) WHERE n.iri = {0} RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.iri = {0} RETURN n")
    public Page<V1Individual> findAllByIri(String iri, String lang, Pageable pageable) { throw new RuntimeException(); }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.iri = {0} AND n.is_defining_ontology = true "
//            + "RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.iri = {0} AND n.is_defining_ontology = true RETURN n")
    public Page<V1Individual> findAllByIriAndIsDefiningOntology(String iri, String lang, Pageable pageable) { throw new RuntimeException(); }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.short_form = {0} RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.short_form = {0} RETURN n")
    public Page<V1Individual> findAllByShortForm(String shortForm, String lang, Pageable pageable) { throw new RuntimeException(); }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.short_form = {0} AND "
//            + "n.is_defining_ontology = true RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.short_form = {0} AND n.is_defining_ontology = true "
//                    + "RETURN n")
    public Page<V1Individual> findAllByShortFormAndIsDefiningOntology(String shortForm, String lang, Pageable pageable) { throw new RuntimeException(); }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.obo_id = {0} RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.obo_id = {0} RETURN n")
    public Page<V1Individual> findAllByOboId(String oboId, String lang, Pageable pageable) { throw new RuntimeException(); }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.obo_id = {0} AND n.is_defining_ontology = true  "
//            + "RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.obo_id = {0} AND n.is_defining_ontology = true RETURN n")
    public Page<V1Individual> findAllByOboIdAndIsDefiningOntology(String oboId, String lang, Pageable pageable) { throw new RuntimeException(); }

}