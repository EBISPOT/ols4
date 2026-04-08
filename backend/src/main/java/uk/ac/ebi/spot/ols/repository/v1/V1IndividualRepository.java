package uk.ac.ebi.spot.ols.repository.v1;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient;
import uk.ac.ebi.spot.ols.repository.search.SearchType;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.v1.mappers.V1IndividualMapper;
import uk.ac.ebi.spot.ols.repository.v1.mappers.V1TermMapper;
import static uk.ac.ebi.ols.shared.DefinedFields.*;

//@RepositoryRestResource(collectionResourceRel = "individuals", exported = false)
@Component
public class V1IndividualRepository {

    @Autowired
    V1OntologyRepository ontologyRepository;

    @Autowired
    OlsSearchClient searchClient;

    @Autowired
    OlsPostgresClient postgresClient;

//    @Query(
//            countQuery = "MATCH (n:Individual)-[:INSTANCEOF]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(parent)",
//            value = "MATCH (n:Individual)-[:INSTANCEOF]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN parent")
    public Page<V1Term> getDirectTypes(String ontologyId, String iri, String lang, Pageable pageable) {

	return this.postgresClient.traverseOutgoingEdges("OntologyIndividual", ontologyId + "+individual+" + iri,
			Arrays.asList("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), Map.of(), Map.of(), pageable)
				.map(node -> V1TermMapper.mapTerm(node, lang));
    }

//    @Query(countQuery = "MATCH (n:Individual)-[:INSTANCEOF|SUBCLASSOF*]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN count(distinct parent)",
//            value = "MATCH (n:Individual)-[:INSTANCEOF|SUBCLASSOF*]->(parent) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN distinct parent")
    public Page<V1Term> getAllTypes(String ontologyId, String iri, String lang, Pageable pageable) { 

	return this.postgresClient.recursivelyTraverseOutgoingEdges("OntologyIndividual", ontologyId + "+individual+" + iri,
			Arrays.asList("http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "http://www.w3.org/2000/01/rdf-schema#subClassOf"), Map.of(), Map.of(), pageable)
				.map(node -> V1TermMapper.mapTerm(node, lang));
    }

//    @Query (value = "MATCH (n:Individual) WHERE n.ontology_name = {0} AND n.iri = {1} RETURN n")
    public V1Individual findByOntologyAndIri(String ontologyId, String iri, String lang) {

        OlsSearchQuery query = new OlsSearchQuery();
	query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
	query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
	query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        JsonElement result = searchClient.getFirst(query);
        if (result == null) {
            return null;
        }
        return V1IndividualMapper.mapIndividual(result, lang);
    }

//    @Query (countQuery = "MATCH (n:Individual {ontology_name : {0}}) RETURN count(n)",
//            value = "MATCH (n:Individual {ontology_name : {0}}) RETURN n")
    public OlsFacetedResultsPage<V1Individual> findAllByOntology(String ontologyId, String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
	query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
	query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1IndividualMapper.mapIndividual(result, lang));
    }

//    @Query (value = "MATCH (n:Individual) WHERE n.ontology_name = {0} AND n.short_form = {1} RETURN n")
    public V1Individual findByOntologyAndShortForm(String ontologyId, String lang, String shortForm) {

        OlsSearchQuery query = new OlsSearchQuery();
	query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
	query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
	query.addFilter("shortForm", List.of(shortForm), SearchType.WHOLE_FIELD);

        JsonElement result = searchClient.getFirst(query);
        if (result == null) {
            return null;
        }
        return V1IndividualMapper.mapIndividual(result, lang);
    }

//    @Query (value = "MATCH (n:Individual) WHERE n.ontology_name = {0} AND n.obo_id = {1} RETURN n")
    public V1Individual findByOntologyAndOboId(String ontologyId, String lang, String oboId) {

        OlsSearchQuery query = new OlsSearchQuery();
	query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
	query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
	query.addFilter("oboId", List.of(oboId), SearchType.WHOLE_FIELD);

        JsonElement result = searchClient.getFirst(query);
        if (result == null) {
            return null;
        }
        return V1IndividualMapper.mapIndividual(result, lang);

    }

//    @Query (countQuery = "MATCH (n:Individual) RETURN count(n)",
//            value = "MATCH (n:Individual) RETURN n")
    public OlsFacetedResultsPage<V1Individual> findAll(String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);

        OlsFacetedResultsPage<JsonElement> entities = searchClient.searchPaginated(query, pageable);

        return entities.map(result -> V1IndividualMapper.mapIndividual(result, lang));
    }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.is_defining_ontology = true RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.is_defining_ontology = true RETURN n")
    public OlsFacetedResultsPage<V1Individual> findAllByIsDefiningOntology(String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
        query.addFilter(IS_DEFINING_ONTOLOGY.getText(), List.of("true"), SearchType.WHOLE_FIELD);

        OlsFacetedResultsPage<JsonElement> entities = searchClient.searchPaginated(query, pageable);

        return entities.map(result -> V1IndividualMapper.mapIndividual(result, lang));

    }


//    @Query (countQuery = "MATCH (n:Individual) WHERE n.iri = {0} RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.iri = {0} RETURN n")
    public OlsFacetedResultsPage<V1Individual> findAllByIri(String iri, String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        OlsFacetedResultsPage<JsonElement> entities = searchClient.searchPaginated(query, pageable);

        return entities.map(result -> V1IndividualMapper.mapIndividual(result, lang));
    }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.iri = {0} AND n.is_defining_ontology = true "
//            + "RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.iri = {0} AND n.is_defining_ontology = true RETURN n")
    public OlsFacetedResultsPage<V1Individual> findAllByIriAndIsDefiningOntology(String iri, String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
        query.addFilter(IS_DEFINING_ONTOLOGY.getText(), List.of("true"), SearchType.WHOLE_FIELD);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        OlsFacetedResultsPage<JsonElement> entities = searchClient.searchPaginated(query, pageable);

        return entities.map(result -> V1IndividualMapper.mapIndividual(result, lang));

    }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.short_form = {0} RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.short_form = {0} RETURN n")
    public OlsFacetedResultsPage<V1Individual> findAllByShortForm(String shortForm, String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
        query.addFilter("shortForm", List.of(shortForm), SearchType.WHOLE_FIELD);

        OlsFacetedResultsPage<JsonElement> entities = searchClient.searchPaginated(query, pageable);

        return entities.map(result -> V1IndividualMapper.mapIndividual(result, lang));

    }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.short_form = {0} AND "
//            + "n.is_defining_ontology = true RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.short_form = {0} AND n.is_defining_ontology = true "
//                    + "RETURN n")
    public OlsFacetedResultsPage<V1Individual> findAllByShortFormAndIsDefiningOntology(String shortForm, String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
        query.addFilter(IS_DEFINING_ONTOLOGY.getText(), List.of("true"), SearchType.WHOLE_FIELD);
        query.addFilter("shortForm", List.of(shortForm), SearchType.WHOLE_FIELD);

        OlsFacetedResultsPage<JsonElement> entities = searchClient.searchPaginated(query, pageable);

        return entities.map(result -> V1IndividualMapper.mapIndividual(result, lang));
    }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.obo_id = {0} RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.obo_id = {0} RETURN n")
    public OlsFacetedResultsPage<V1Individual> findAllByOboId(String oboId, String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
        query.addFilter("oboId", List.of(oboId), SearchType.WHOLE_FIELD);

        OlsFacetedResultsPage<JsonElement> entities = searchClient.searchPaginated(query, pageable);

        return entities.map(result -> V1IndividualMapper.mapIndividual(result, lang));
    }

//    @Query (countQuery = "MATCH (n:Individual) WHERE n.obo_id = {0} AND n.is_defining_ontology = true  "
//            + "RETURN count(n)",
//            value = "MATCH (n:Individual) WHERE n.obo_id = {0} AND n.is_defining_ontology = true RETURN n")
    public OlsFacetedResultsPage<V1Individual> findAllByOboIdAndIsDefiningOntology(String oboId, String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("individual"), SearchType.WHOLE_FIELD);
        query.addFilter(IS_DEFINING_ONTOLOGY.getText(), List.of("true"), SearchType.WHOLE_FIELD);
        query.addFilter("oboId", List.of(oboId), SearchType.WHOLE_FIELD);

        OlsFacetedResultsPage<JsonElement> entities = searchClient.searchPaginated(query, pageable);

        return entities.map(result -> V1IndividualMapper.mapIndividual(result, lang));
    }

}