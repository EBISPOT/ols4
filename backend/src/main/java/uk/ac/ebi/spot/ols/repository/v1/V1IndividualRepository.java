package uk.ac.ebi.spot.ols.repository.v1;

import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.neo4j.OlsNeo4jClient;
import uk.ac.ebi.spot.ols.repository.solr.Fuzziness;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrQuery;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.service.Neo4jClient;
import uk.ac.ebi.spot.ols.service.OntologyEntity;

//@RepositoryRestResource(collectionResourceRel = "individuals", exported = false)
@Component
public class V1IndividualRepository {

    @Autowired
    V1OntologyRepository ontologyRepository;

    @Autowired
    OlsSolrClient solrClient;

    @Autowired
    OlsNeo4jClient neo4jClient;

//    @Query(
//            countQuery = "MATCH (n:Individual)-[:INSTANCEOF]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(parent)",
//            value = "MATCH (n:Individual)-[:INSTANCEOF]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN parent")
    public Page<V1Term> getDirectTypes(String ontologyId, String iri, String lang, Pageable pageable) {

	return this.neo4jClient.getParents("OntologyIndividual", ontologyId + "+individual+" + iri,
			Arrays.asList("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), pageable)
				.map(node -> new V1Term(node, lang));
    }

//    @Query(countQuery = "MATCH (n:Individual)-[:INSTANCEOF|SUBCLASSOF*]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct parent)",
//            value = "MATCH (n:Individual)-[:INSTANCEOF|SUBCLASSOF*]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct parent")
    public Page<V1Term> getAllTypes(String ontologyId, String iri, String lang, Pageable pageable) { 

	return this.neo4jClient.getAncestors("OntologyIndividual", ontologyId + "+individual+" + iri,
			Arrays.asList("http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "http://www.w3.org/2000/01/rdf-schema#subClassOf"), pageable)
				.map(node -> new V1Term(node, lang));
    }

//    @Query (value = "MATCH (n:Individual) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN n")
    public V1Individual findByOntologyAndIri(String ontologyId, String iri, String lang) { 

        OlsSolrQuery query = new OlsSolrQuery();
	query.addFilter("lang", lang, Fuzziness.EXACT);
	query.addFilter("type", "individual", Fuzziness.EXACT);
	query.addFilter("ontologyId", ontologyId, Fuzziness.EXACT);
	query.addFilter("uri", iri, Fuzziness.EXACT);

        return new V1Individual(solrClient.getOne(query), lang);
    }

//    @Query (countQuery = "MATCH (n:Individual {ontology_name : {0}}) RETURN count(n)",
//            value = "MATCH (n:Individual {ontology_name : {0}}) RETURN n")
    public Page<V1Individual> findAllByOntology(String ontologyId, String lang, Pageable pageable) { 

        OlsSolrQuery query = new OlsSolrQuery();
	query.addFilter("lang", lang, Fuzziness.EXACT);
	query.addFilter("type", "individual", Fuzziness.EXACT);
	query.addFilter("ontologyId", ontologyId, Fuzziness.EXACT);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> new V1Individual(result, lang));
    }

//    @Query (value = "MATCH (n:Individual) WHERE n.ontology_name = {0} AND n.short_form = {1} RETURN n")
    public V1Individual findByOntologyAndShortForm(String ontologyId, String lang, String shortForm) { 

        OlsSolrQuery query = new OlsSolrQuery();
	query.addFilter("lang", lang, Fuzziness.EXACT);
	query.addFilter("type", "individual", Fuzziness.EXACT);
	query.addFilter("ontologyId", ontologyId, Fuzziness.EXACT);
	query.addFilter("shortForm", shortForm, Fuzziness.EXACT);

        return new V1Individual(solrClient.getOne(query), lang);
    }

//    @Query (value = "MATCH (n:Individual) WHERE n.ontology_name = {0} AND n.obo_id = {1} RETURN n")
    public V1Individual findByOntologyAndOboId(String ontologyId, String lang, String oboId) { 

        OlsSolrQuery query = new OlsSolrQuery();
	query.addFilter("lang", lang, Fuzziness.EXACT);
	query.addFilter("type", "individual", Fuzziness.EXACT);
	query.addFilter("ontologyId", ontologyId, Fuzziness.EXACT);
	query.addFilter("oboId", oboId, Fuzziness.EXACT);

        return new V1Individual(solrClient.getOne(query), lang);

    }

//    @Query (countQuery = "MATCH (n:Individual) RETURN count(n)",
//            value = "MATCH (n:Individual) RETURN n")
    public Page<V1Individual> findAll(String lang, Pageable pageable) { 

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "individual", Fuzziness.EXACT);

        Page<Map<String,Object>> entities = solrClient.searchSolrPaginated(query, pageable);

        return entities.map(result -> new V1Individual(result, lang));
    }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.is_defining_ontology = true RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.is_defining_ontology = true RETURN n")
    public Page<V1Individual> findAllByIsDefiningOntology(String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "individual", Fuzziness.EXACT);
        query.addFilter("isDefiningOntology", "true", Fuzziness.EXACT);

        Page<Map<String,Object>> entities = solrClient.searchSolrPaginated(query, pageable);

        return entities.map(result -> new V1Individual(result, lang));

    }


//    @Query (countQuery = "MATCH (n:Individual) WHERE n.iri = {0} RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.iri = {0} RETURN n")
    public Page<V1Individual> findAllByIri(String iri, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "individual", Fuzziness.EXACT);
        query.addFilter("iri", iri, Fuzziness.EXACT);

        Page<Map<String,Object>> entities = solrClient.searchSolrPaginated(query, pageable);

        return entities.map(result -> new V1Individual(result, lang));
    }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.iri = {0} AND n.is_defining_ontology = true "
//            + "RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.iri = {0} AND n.is_defining_ontology = true RETURN n")
    public Page<V1Individual> findAllByIriAndIsDefiningOntology(String iri, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "individual", Fuzziness.EXACT);
        query.addFilter("isDefiningOntology", "true", Fuzziness.EXACT);
        query.addFilter("iri", iri, Fuzziness.EXACT);

        Page<Map<String,Object>> entities = solrClient.searchSolrPaginated(query, pageable);

        return entities.map(result -> new V1Individual(result, lang));

    }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.short_form = {0} RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.short_form = {0} RETURN n")
    public Page<V1Individual> findAllByShortForm(String shortForm, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "individual", Fuzziness.EXACT);
        query.addFilter("shortForm", shortForm, Fuzziness.EXACT);

        Page<Map<String,Object>> entities = solrClient.searchSolrPaginated(query, pageable);

        return entities.map(result -> new V1Individual(result, lang));

    }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.short_form = {0} AND "
//            + "n.is_defining_ontology = true RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.short_form = {0} AND n.is_defining_ontology = true "
//                    + "RETURN n")
    public Page<V1Individual> findAllByShortFormAndIsDefiningOntology(String shortForm, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "individual", Fuzziness.EXACT);
        query.addFilter("isDefiningOntology", "true", Fuzziness.EXACT);
        query.addFilter("shortForm", shortForm, Fuzziness.EXACT);

        Page<Map<String,Object>> entities = solrClient.searchSolrPaginated(query, pageable);

        return entities.map(result -> new V1Individual(result, lang));
    }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.obo_id = {0} RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.obo_id = {0} RETURN n")
    public Page<V1Individual> findAllByOboId(String oboId, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "individual", Fuzziness.EXACT);
        query.addFilter("oboId", oboId, Fuzziness.EXACT);

        Page<Map<String,Object>> entities = solrClient.searchSolrPaginated(query, pageable);

        return entities.map(result -> new V1Individual(result, lang));
    }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.obo_id = {0} AND n.is_defining_ontology = true  "
//            + "RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.obo_id = {0} AND n.is_defining_ontology = true RETURN n")
    public Page<V1Individual> findAllByOboIdAndIsDefiningOntology(String oboId, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "individual", Fuzziness.EXACT);
        query.addFilter("isDefiningOntology", "true", Fuzziness.EXACT);
        query.addFilter("oboId", oboId, Fuzziness.EXACT);

        Page<Map<String,Object>> entities = solrClient.searchSolrPaginated(query, pageable);

        return entities.map(result -> new V1Individual(result, lang));
    }

}