package uk.ac.ebi.spot.ols.repository.v1;

import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
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
import uk.ac.ebi.spot.ols.service.OboDatabaseUrlService;

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
    OlsNeo4jClient neo4jClient;

    @Autowired
    OlsSolrClient solrClient;

    @Autowired
    OboDatabaseUrlService oboDbUrls;


//    @Query(
//            countQuery = "MATCH (n:Class)-[:SUBCLASSOF]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct parent)",
//            value = "MATCH (n:Class)-[:SUBCLASSOF]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct parent")
    public Page<V1Term> getParents(String ontologyId, String iri, String lang, Pageable pageable) {

	return this.neo4jClient.getParents("OntologyClass", ontologyId + "+class+" + iri, Arrays.asList("directParent"), pageable)
			.map(node -> new V1Term(node, lang, oboDbUrls));
    }

//    @Query(
//            countQuery = "MATCH (n:Class)-[:SUBCLASSOF|RelatedTree]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct parent)",
//            value = "MATCH (n:Class)-[:SUBCLASSOF|RelatedTree]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct parent")
    public Page<V1Term> getHierarchicalParents(String ontologyId, String iri, String lang, Pageable pageable) {

	List<String> relationIRIs = List.of("hierarchicalParent");

	return this.neo4jClient.getParents("OntologyClass", ontologyId + "+class+" + iri, relationIRIs, pageable)
            .map(record -> new V1Term(record, lang, oboDbUrls));
    }

//    @Query(
//            countQuery = "MATCH (n:Class)-[:SUBCLASSOF|RelatedTree*]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct parent)",
//            value = "MATCH (n:Class)-[:SUBCLASSOF|RelatedTree*]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct parent")
    public Page<V1Term> getHierarchicalAncestors(String ontologyId, String iri, String lang, Pageable pageable) {

	List<String> relationIRIs = List.of("hierarchicalParent");

	return this.neo4jClient.getAncestors("OntologyClass", ontologyId + "+class+" + iri, relationIRIs, pageable)
            .map(record -> new V1Term(record, lang, oboDbUrls));

    }

//    @Query( countQuery = "MATCH (n:Class)<-[:SUBCLASSOF]-(child) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct child)",
//            value = "MATCH (n:Class)<-[:SUBCLASSOF]-(child) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct child")
    public Page<V1Term> getChildren(String ontologyId, String iri, String lang, Pageable pageable) {

	return this.neo4jClient.getChildren("OntologyClass", ontologyId + "+class+" + iri, Arrays.asList("directParent"), pageable)
            .map(record -> new V1Term(record, lang, oboDbUrls));
    }

//    @Query( countQuery = "MATCH (n:Class)<-[:SUBCLASSOF|RelatedTree]-(child) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct child)",
//            value = "MATCH (n:Class)<-[:SUBCLASSOF|RelatedTree]-(child) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct child")
    public Page<V1Term> getHierarchicalChildren(String ontologyId, String iri, String lang, Pageable pageable) {

	List<String> relationIRIs = List.of("hierarchicalParent");

	return this.neo4jClient.getChildren("OntologyClass", ontologyId + "+class+" + iri, relationIRIs, pageable)
            .map(record -> new V1Term(record, lang, oboDbUrls));

    }

//    @Query( countQuery = "MATCH (n:Class)<-[:SUBCLASSOF|RelatedTree*]-(child) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct child)",
//            value = "MATCH (n:Class)<-[:SUBCLASSOF|RelatedTree*]-(child) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct child")
    public Page<V1Term> getHierarchicalDescendants(String ontologyId, String iri, String lang, Pageable pageable) {

        List<String> relationIRIs = List.of("hierarchicalParent");

	return this.neo4jClient.getDescendants("OntologyClass", ontologyId + "+class+" + iri, relationIRIs, pageable)
            .map(record -> new V1Term(record, lang, oboDbUrls));
    }


//    @Query(countQuery = "MATCH (n:Class)<-[:SUBCLASSOF*]-(child) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct child)",
//            value = "MATCH (n:Class)<-[:SUBCLASSOF*]-(child) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct child")
    public Page<V1Term> getDescendants(String ontologyId, String iri, String lang, Pageable pageable) {

	return this.neo4jClient.getDescendants("OntologyClass", ontologyId + "+class+" + iri, Arrays.asList("directParent"), pageable)
            .map(record -> new V1Term(record, lang, oboDbUrls));

    }

//    @Query(countQuery = "MATCH (n:Class)-[:SUBCLASSOF*]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct parent)",
//                value = "MATCH (n:Class)-[:SUBCLASSOF*]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct parent")
    public Page<V1Term> getAncestors(String ontologyId, String iri, String lang, Pageable pageable) {

        V1Ontology ontology = ontologyRepository.get(ontologyId, lang);

	return this.neo4jClient.getAncestors("OntologyClass", ontologyId + "+class+" + iri, Arrays.asList("directParent"), pageable)
            .map(record -> new V1Term(record, lang, oboDbUrls));

    }

//    @Query(countQuery = "MATCH (n:Class)-[r:Related]->(related) WHERE n.ontology_name = {0} AND n.iri = {1} AND r.iri = {2} RETURN count(distinct related)",
//                value = "MATCH (n:Class)-[r:Related]->(related) WHERE n.ontology_name = {0} AND n.iri = {1} AND r.iri = {2} RETURN distinct related")
    public Page<V1Term> getRelated(String ontologyId, String iri, String lang, String relation, Pageable pageable) {

	return this.neo4jClient.getChildren("OntologyClass", ontologyId + "+class+" + iri, Arrays.asList(relation), pageable)
            .map(record -> new V1Term(record, lang, oboDbUrls));

    }

//    @Query (value = "MATCH (n:Class) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN n")
    public V1Term findByOntologyAndIri(String ontologyId, String iri, String lang) {

        OlsSolrQuery query = new OlsSolrQuery();
	query.addFilter("lang", lang, Fuzziness.EXACT);
	query.addFilter("type", "class", Fuzziness.EXACT);
	query.addFilter("ontologyId", ontologyId, Fuzziness.EXACT);
	query.addFilter("iri", iri, Fuzziness.EXACT);

        return new V1Term(solrClient.getOne(query), lang, oboDbUrls);

    }

//    @Query (countQuery = "MATCH (n:Class {ontology_name : {0}}) RETURN count(n)",
//            value = "MATCH (n:Class {ontology_name : {0}}) RETURN n")
    public Page<V1Term> findAllByOntology(String ontologyId, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
	query.addFilter("lang", lang, Fuzziness.EXACT);
	query.addFilter("type", "class", Fuzziness.EXACT);
	query.addFilter("ontologyId", ontologyId, Fuzziness.EXACT);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> new V1Term(result, lang, oboDbUrls));
    }

//    @Query (value = "MATCH (n:Class) WHERE n.ontology_name = {0} AND n.short_form = {1} RETURN n")
    public V1Term findByOntologyAndShortForm(String ontologyId, String shortForm, String lang) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "class", Fuzziness.EXACT);
        query.addFilter("ontologyId", ontologyId, Fuzziness.EXACT);
        query.addFilter("shortForm", shortForm, Fuzziness.EXACT);

        return new V1Term(solrClient.getOne(query), lang, oboDbUrls);
    }

//    @Query (value = "MATCH (n:Class) WHERE n.ontology_name = {0} AND n.obo_id = {1} RETURN n")
    public V1Term findByOntologyAndOboId(String ontologyId, String oboId, String lang) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "class", Fuzziness.EXACT);
        query.addFilter("ontologyId", ontologyId, Fuzziness.EXACT);
        query.addFilter("oboId", oboId, Fuzziness.EXACT);

        return new V1Term(solrClient.getOne(query), lang, oboDbUrls);

    }

//    @Query (countQuery = "MATCH (n:Class)-[SUBCLASSOF]->(r:Root) WHERE r.ontology_name = {0} AND n.is_obsolete = {1}  RETURN count(n)",
//            value = "MATCH (n:Class)-[SUBCLASSOF]->(r:Root) WHERE r.ontology_name = {0} AND n.is_obsolete = {1}  RETURN n")
    public Page<V1Term> getRoots(String ontologyId, boolean obsolete, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "class", Fuzziness.EXACT);
        query.addFilter("ontologyId", ontologyId, Fuzziness.EXACT);
        query.addFilter("isRoot", "true", Fuzziness.EXACT);

        if(!obsolete)
            query.addFilter("isObsolete", "false", Fuzziness.EXACT);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> new V1Term(result, lang, oboDbUrls));
    }
    
//    @Query (countQuery = "MATCH (n:PreferredRootTerm) WHERE n.ontology_name = {0} AND n.is_obsolete = {1} RETURN count(n)",
//            value = "MATCH (n:PreferredRootTerm) WHERE n.ontology_name = {0} AND n.is_obsolete = {1} RETURN n")
    public Page<V1Term> getPreferredRootTerms(String ontologyId, boolean obsolete, String lang, Pageable pageable) {
        throw new RuntimeException();
    }

//    @Query (value = "MATCH (n:PreferredRootTerm) WHERE n.ontology_name = {0} AND n.is_obsolete = {1} RETURN count(n)")
    public long getPreferredRootTermCount(String ontologyId, boolean obsolete) {
        throw new RuntimeException();
    }

//    @Query (countQuery = "MATCH (n:Class) RETURN count(n)",
//            value = "MATCH (n:Class) RETURN n")
    public Page<V1Term> findAll(String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "class", Fuzziness.EXACT);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> new V1Term(result, lang, oboDbUrls));
    }
    
//    @Query (countQuery = "MATCH (n:Class) WHERE n.is_defining_ontology = true RETURN count(n)",
//    		value = "MATCH (n:Class) WHERE n.is_defining_ontology = true RETURN n")
    public Page<V1Term> findAllByIsDefiningOntology(String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "class", Fuzziness.EXACT);
        query.addFilter("isDefiningOntology", "true", Fuzziness.EXACT);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> new V1Term(result, lang, oboDbUrls));

    }

//    @Query (countQuery = "MATCH (n:Class) WHERE n.iri = {0} RETURN count(n)",
//            value = "MATCH (n:Class) WHERE n.iri = {0} RETURN n")
    public Page<V1Term> findAllByIri(String iri, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "class", Fuzziness.EXACT);
        query.addFilter("iri", iri, Fuzziness.EXACT);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> new V1Term(result, lang, oboDbUrls));
    }

//    @Query (countQuery = "MATCH (n:Class) WHERE n.iri = {0} AND n.is_defining_ontology = true "
//    		+ "RETURN count(n)",
//    		value = "MATCH (n:Class) WHERE n.iri = {0} AND n.is_defining_ontology = true RETURN n")
    public Page<V1Term> findAllByIriAndIsDefiningOntology(String iri, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "class", Fuzziness.EXACT);
        query.addFilter("isDefiningOntology", "true", Fuzziness.EXACT);
        query.addFilter("iri", iri, Fuzziness.EXACT);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> new V1Term(result, lang, oboDbUrls));
    }
    		
//    @Query (countQuery = "MATCH (n:Class) WHERE n.short_form = {0} RETURN count(n)",
//            value = "MATCH (n:Class) WHERE n.short_form = {0} RETURN n")
    public Page<V1Term> findAllByShortForm(String shortForm, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "class", Fuzziness.EXACT);
        query.addFilter("shortForm", shortForm, Fuzziness.EXACT);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> new V1Term(result, lang, oboDbUrls));
    }

//    @Query (countQuery = "MATCH (n:Class) WHERE n.short_form = {0} AND n.is_defining_ontology = true"
//    		+ " RETURN count(n)",
//    		value = "MATCH (n:Class) WHERE n.short_form = {0} AND n.is_defining_ontology = true RETURN n")
    public Page<V1Term> findAllByShortFormAndIsDefiningOntology(String shortForm, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "class", Fuzziness.EXACT);
        query.addFilter("isDefiningOntology", "true", Fuzziness.EXACT);
        query.addFilter("shortForm", shortForm, Fuzziness.EXACT);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> new V1Term(result, lang, oboDbUrls));
    }
    
//    @Query (countQuery = "MATCH (n:Class) WHERE n.obo_id = {0} RETURN count(n)",
//            value = "MATCH (n:Class) WHERE n.obo_id = {0} RETURN n")
    public Page<V1Term> findAllByOboId(String oboId, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "class", Fuzziness.EXACT);
        query.addFilter("oboId", oboId, Fuzziness.EXACT);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> new V1Term(result, lang, oboDbUrls));

    }

//    @Query (countQuery = "MATCH (n:Class) WHERE n.obo_id = {0} AND n.is_defining_ontology = true "
//    		+ "RETURN count(n)",
//    		value = "MATCH (n:Class) WHERE n.obo_id = {0} AND n.is_defining_ontology = true RETURN n")
    public Page<V1Term> findAllByOboIdAndIsDefiningOntology(String oboId, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("lang", lang, Fuzziness.EXACT);
        query.addFilter("type", "class", Fuzziness.EXACT);
        query.addFilter("isDefiningOntology", "true", Fuzziness.EXACT);
        query.addFilter("oboId", oboId, Fuzziness.EXACT);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> new V1Term(result, lang, oboDbUrls));
    }
    
//    @Query (countQuery = "MATCH (i:Individual)-[INSTANCEOF]->(c:Class) WHERE i.ontology_name = {0} AND c.iri = {1} RETURN count(i)",
//            value = "MATCH (i:Individual)-[INSTANCEOF]->(c:Class) WHERE i.ontology_name = {0} AND c.iri = {1} RETURN i")
    public Page<V1Individual> getInstances(String ontologyId, String iri, Pageable pageable) {
        throw new RuntimeException();
    }

}
