package uk.ac.ebi.spot.ols.repository.v1;

import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.Neo4jQueryHelper;
import uk.ac.ebi.spot.ols.service.Neo4jClient;

import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * @author Simon Jupp
 * @date 30/04/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
//@RepositoryRestResource(collectionResourceRel = "terms", exported = false)
@Component
public class V1TermRepository {
//        extends GraphRepository<Term> {


    @Autowired
    V1OntologyRepository ontologyRepository;

    @Autowired
    V1PropertyRepository propertyRepository;

    @Autowired
    Neo4jQueryHelper neo4jQueryHelper;


//    @Query(
//            countQuery = "MATCH (n:Class)-[:SUBCLASSOF]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct parent)",
//            value = "MATCH (n:Class)-[:SUBCLASSOF]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct parent")
    public Page<V1Term> getParents(String ontologyId, String iri, String lang, Pageable pageable) {

        V1Ontology ontology = ontologyRepository.get(ontologyId, lang);

	return this.neo4jQueryHelper.getParents("OntologyTerm", ontologyId + "+" + iri, Arrays.asList("http://www.w3.org/2000/01/rdf-schema#subClassOf"), pageable)
			.map(node -> new V1Term(node, ontology, lang));
    }

//    @Query(
//            countQuery = "MATCH (n:Class)-[:SUBCLASSOF|RelatedTree]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct parent)",
//            value = "MATCH (n:Class)-[:SUBCLASSOF|RelatedTree]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct parent")
    public Page<V1Term> getHierarchicalParents(String ontologyId, String iri, String lang, Pageable pageable) {

        V1Ontology ontology = ontologyRepository.get(ontologyId, lang);

	List<String> relationURIs = new ArrayList<>();
	relationURIs.add("http://www.w3.org/2000/01/rdf-schema#subClassOf");
	relationURIs.addAll(ontology.config.hierarchicalProperties);

	return this.neo4jQueryHelper.getParents("OntologyTerm", ontologyId + "+" + iri, relationURIs, pageable)
            .map(record -> new V1Term(record, ontology, lang));
    }

//    @Query(
//            countQuery = "MATCH (n:Class)-[:SUBCLASSOF|RelatedTree*]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct parent)",
//            value = "MATCH (n:Class)-[:SUBCLASSOF|RelatedTree*]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct parent")
    public Page<V1Term> getHierarchicalAncestors(String ontologyId, String iri, String lang, Pageable pageable) {

        V1Ontology ontology = ontologyRepository.get(ontologyId, lang);

	List<String> relationURIs = new ArrayList<>();
	relationURIs.add("http://www.w3.org/2000/01/rdf-schema#subClassOf");
        relationURIs.addAll(ontology.config.hierarchicalProperties);

	return this.neo4jQueryHelper.getAncestors("OntologyTerm", ontologyId + "+" + iri, relationURIs, pageable)
            .map(record -> new V1Term(record, ontology, lang));

    }

//    @Query( countQuery = "MATCH (n:Class)<-[:SUBCLASSOF]-(child) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct child)",
//            value = "MATCH (n:Class)<-[:SUBCLASSOF]-(child) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct child")
    public Page<V1Term> getChildren(String ontologyId, String iri, String lang, Pageable pageable) {

        try {
            iri = UriUtils.decode(iri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new ResourceNotFoundException();
        }

        V1Ontology ontology = ontologyRepository.get(ontologyId, lang);

	return this.neo4jQueryHelper.getChildren("OntologyTerm", ontologyId + "+" + iri, Arrays.asList("http://www.w3.org/2000/01/rdf-schema#subClassOf"), pageable)
            .map(record -> new V1Term(record, ontology, lang));
    }

//    @Query( countQuery = "MATCH (n:Class)<-[:SUBCLASSOF|RelatedTree]-(child) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct child)",
//            value = "MATCH (n:Class)<-[:SUBCLASSOF|RelatedTree]-(child) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct child")
    public Page<V1Term> getHierarchicalChildren(String ontologyId, String iri, String lang, Pageable pageable) {

        V1Ontology ontology = ontologyRepository.get(ontologyId, lang);

	List<String> relationURIs = new ArrayList<>();
	relationURIs.add("http://www.w3.org/2000/01/rdf-schema#subClassOf");
	relationURIs.addAll(ontology.config.hierarchicalProperties);

	return this.neo4jQueryHelper.getChildren("OntologyTerm", ontologyId + "+" + iri, relationURIs, pageable)
            .map(record -> new V1Term(record, ontology, lang));

    }

//    @Query( countQuery = "MATCH (n:Class)<-[:SUBCLASSOF|RelatedTree*]-(child) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct child)",
//            value = "MATCH (n:Class)<-[:SUBCLASSOF|RelatedTree*]-(child) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct child")
    public Page<V1Term> getHierarchicalDescendants(String ontologyId, String iri, String lang, Pageable pageable) {

        V1Ontology ontology = ontologyRepository.get(ontologyId, lang);

	List<String> relationURIs = new ArrayList<>();
	relationURIs.add("http://www.w3.org/2000/01/rdf-schema#subClassOf");
	relationURIs.addAll(ontology.config.hierarchicalProperties);

	return this.neo4jQueryHelper.getDescendants("OntologyTerm", ontologyId + "+" + iri, relationURIs, pageable)
            .map(record -> new V1Term(record, ontology, lang));
    }


//    @Query(countQuery = "MATCH (n:Class)<-[:SUBCLASSOF*]-(child) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct child)",
//            value = "MATCH (n:Class)<-[:SUBCLASSOF*]-(child) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct child")
    public Page<V1Term> getDescendants(String ontologyId, String iri, String lang, Pageable pageable) {

        V1Ontology ontology = ontologyRepository.get(ontologyId, lang);

	return this.neo4jQueryHelper.getDescendants("OntologyTerm", ontologyId + "+" + iri, Arrays.asList("http://www.w3.org/2000/01/rdf-schema#subClassOf"), pageable)
            .map(record -> new V1Term(record, ontology, lang));

    }

//    @Query(countQuery = "MATCH (n:Class)-[:SUBCLASSOF*]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct parent)",
//                value = "MATCH (n:Class)-[:SUBCLASSOF*]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct parent")
    public Page<V1Term> getAncestors(String ontologyId, String iri, String lang, Pageable pageable) {

        V1Ontology ontology = ontologyRepository.get(ontologyId, lang);

	return this.neo4jQueryHelper.getAncestors("OntologyTerm", ontologyId + "+" + iri, Arrays.asList("http://www.w3.org/2000/01/rdf-schema#subClassOf"), pageable)
            .map(record -> new V1Term(record, ontology, lang));

    }

//    @Query(countQuery = "MATCH (n:Class)-[r:Related]->(related) WHERE n.ontology_name = {0} AND n.iri = {1} AND r.uri = {2} RETURN count(distinct related)",
//                value = "MATCH (n:Class)-[r:Related]->(related) WHERE n.ontology_name = {0} AND n.iri = {1} AND r.uri = {2} RETURN distinct related")
    public Page<V1Term> getRelated(String ontologyId, String iri, String lang, String relation, Pageable pageable) {

        V1Ontology ontology = ontologyRepository.get(ontologyId, lang);

	return this.neo4jQueryHelper.getChildren("OntologyTerm", ontologyId + "+" + iri, Arrays.asList(relation), pageable)
            .map(record -> new V1Term(record, ontology, lang));

    }

//    @Query (value = "MATCH (n:Class) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN n")
    public V1Term findByOntologyAndIri(String ontologyId, String iri, String lang) {

        V1Ontology ontology = ontologyRepository.get(ontologyId, lang);

	return new V1Term(this.neo4jQueryHelper.getOne("OntologyTerm", "id", ontologyId + "+" + iri), ontology, lang);

    }

//    @Query (countQuery = "MATCH (n:Class {ontology_name : {0}}) RETURN count(n)",
//            value = "MATCH (n:Class {ontology_name : {0}}) RETURN n")
    public Page<V1Term> findAllByOntology(String ontologyId, String lang, Pageable pageable) {

        V1Ontology ontology = ontologyRepository.get(ontologyId, lang);

	return neo4jQueryHelper.getAllInOntology(ontologyId, "OntologyTerm", pageable)
            .map(record -> new V1Term(record, ontology, lang));
    }

//    @Query (value = "MATCH (n:Class) WHERE n.ontology_name = {0} AND n.short_form = {1} RETURN n")
    public V1Term findByOntologyAndShortForm(String ontologyId, String shortForm, String lang) {
        throw new RuntimeException();
    }

//    @Query (value = "MATCH (n:Class) WHERE n.ontology_name = {0} AND n.obo_id = {1} RETURN n")
    public V1Term findByOntologyAndOboId(String ontologyId, String oboId, String lang) {
        throw new RuntimeException();
    }

//    @Query (countQuery = "MATCH (n:Class)-[SUBCLASSOF]->(r:Root) WHERE r.ontology_name = {0} AND n.is_obsolete = {1}  RETURN count(n)",
//            value = "MATCH (n:Class)-[SUBCLASSOF]->(r:Root) WHERE r.ontology_name = {0} AND n.is_obsolete = {1}  RETURN n")
    public Page<V1Term> getRoots(String ontologyId, boolean obsolete, Pageable pageable) {
        throw new RuntimeException();
    }
    
//    @Query (countQuery = "MATCH (n:PreferredRootTerm) WHERE n.ontology_name = {0} AND n.is_obsolete = {1} RETURN count(n)",
//            value = "MATCH (n:PreferredRootTerm) WHERE n.ontology_name = {0} AND n.is_obsolete = {1} RETURN n")
    public Page<V1Term> getPreferredRootTerms(String ontologyId, boolean obsolete, Pageable pageable) {
        throw new RuntimeException();
    }

//    @Query (value = "MATCH (n:PreferredRootTerm) WHERE n.ontology_name = {0} AND n.is_obsolete = {1} RETURN count(n)")
    public long getPreferredRootTermCount(String ontologyId, boolean obsolete) {
        throw new RuntimeException();
    }

//    @Query (countQuery = "MATCH (n:Class) RETURN count(n)",
//            value = "MATCH (n:Class) RETURN n")
    public Page<V1Term> findAll(Pageable pageable) {
        throw new RuntimeException();
    }
    
//    @Query (countQuery = "MATCH (n:Class) WHERE n.is_defining_ontology = true RETURN count(n)",
//    		value = "MATCH (n:Class) WHERE n.is_defining_ontology = true RETURN n")
    public Page<V1Term> findAllByIsDefiningOntology(Pageable pageable) {
        throw new RuntimeException();
    }

//    @Query (countQuery = "MATCH (n:Class) WHERE n.iri = {0} RETURN count(n)",
//            value = "MATCH (n:Class) WHERE n.iri = {0} RETURN n")
    public Page<V1Term> findAllByIri(String iri, Pageable pageable) {
        throw new RuntimeException();
    }

//    @Query (countQuery = "MATCH (n:Class) WHERE n.iri = {0} AND n.is_defining_ontology = true "
//    		+ "RETURN count(n)",
//    		value = "MATCH (n:Class) WHERE n.iri = {0} AND n.is_defining_ontology = true RETURN n")
    public Page<V1Term> findAllByIriAndIsDefiningOntology(String iri, Pageable pageable) {
        throw new RuntimeException();
    }
    		
//    @Query (countQuery = "MATCH (n:Class) WHERE n.short_form = {0} RETURN count(n)",
//            value = "MATCH (n:Class) WHERE n.short_form = {0} RETURN n")
    public Page<V1Term> findAllByShortForm(String shortForm, Pageable pageable) {
        throw new RuntimeException();
    }

//    @Query (countQuery = "MATCH (n:Class) WHERE n.short_form = {0} AND n.is_defining_ontology = true"
//    		+ " RETURN count(n)",
//    		value = "MATCH (n:Class) WHERE n.short_form = {0} AND n.is_defining_ontology = true RETURN n")
    public Page<V1Term> findAllByShortFormAndIsDefiningOntology(String shortForm, Pageable pageable) {
        throw new RuntimeException();
    }
    
//    @Query (countQuery = "MATCH (n:Class) WHERE n.obo_id = {0} RETURN count(n)",
//            value = "MATCH (n:Class) WHERE n.obo_id = {0} RETURN n")
    public Page<V1Term> findAllByOboId(String oboId, Pageable pageable) {
        throw new RuntimeException();
    }

//    @Query (countQuery = "MATCH (n:Class) WHERE n.obo_id = {0} AND n.is_defining_ontology = true "
//    		+ "RETURN count(n)",
//    		value = "MATCH (n:Class) WHERE n.obo_id = {0} AND n.is_defining_ontology = true RETURN n")
    public Page<V1Term> findAllByOboIdAndIsDefiningOntology(String oboId, Pageable pageable) {
        throw new RuntimeException();
    }
    
//    @Query (countQuery = "MATCH (i:Individual)-[INSTANCEOF]->(c:Class) WHERE i.ontology_name = {0} AND c.iri = {1} RETURN count(i)",
//            value = "MATCH (i:Individual)-[INSTANCEOF]->(c:Class) WHERE i.ontology_name = {0} AND c.iri = {1} RETURN i")
    public Page<V1Individual> getInstances(String ontologyId, String iri, Pageable pageable) {
        throw new RuntimeException();
    }





    String relatedGraphQuery = "MATCH path = (n:Class)-[r:SUBCLASSOF|Related]-(parent)\n"+
            "WHERE n.ontology_name = {0} AND n.iri = {1}\n"+
            "UNWIND nodes(path) as p\n" +
            "UNWIND rels(path) as r1\n" +
            "RETURN {nodes: collect( distinct {iri: p.iri, label: p.label})[0..200], " +
            "edges: collect (distinct {source: startNode(r1).iri, target: endNode(r1).iri, label: r1.label, uri: r1.uri}  )[0..200]} as result";

    public Object getGraphJson(String ontologyName, String iri) {
        return getGraphJson(ontologyName, iri, 1);
    }


    public Object getGraphJson(String ontologyName, String iri, int distance) {

        Session session = Neo4jClient.getSession();

        Map<String, Object> paramt = new HashMap<>();
        paramt.put("0", ontologyName);
        paramt.put("1", iri);
//        paramt.put("2",distance);
        Result res = session.run(relatedGraphQuery, paramt);

        return res.next().get("result");

    }



}
