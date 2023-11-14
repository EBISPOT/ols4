package uk.ac.ebi.spot.ols.repository.v1;

import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.neo4j.OlsNeo4jClient;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrQuery;
import uk.ac.ebi.spot.ols.repository.solr.SearchType;
import uk.ac.ebi.spot.ols.repository.v1.mappers.V1TermMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class V1TermRepository {

    private Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    V1OntologyRepository ontologyRepository;

    @Autowired
    V1PropertyRepository propertyRepository;

    @Autowired
    OlsNeo4jClient neo4jClient;

    @Autowired
    OlsSolrClient solrClient;

    public Page<V1Term> getParents(String ontologyId, String iri, String lang, Pageable pageable) {

        return this.neo4jClient.traverseOutgoingEdges("OntologyClass", ontologyId + "+class+" + iri, Arrays.asList("directParent"), Map.of(), pageable)
                .map(node -> V1TermMapper.mapTerm(node, lang));
    }

    public Page<V1Term> getHierarchicalParents(String ontologyId, String iri, String lang, Pageable pageable) {

        List<String> relationIRIs = List.of("hierarchicalParent");

        return this.neo4jClient.traverseOutgoingEdges("OntologyClass", ontologyId + "+class+" + iri, relationIRIs, Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));
    }

    public Page<V1Term> getHierarchicalAncestors(String ontologyId, String iri, String lang, Pageable pageable) {

        List<String> relationIRIs = List.of("hierarchicalParent");

        return this.neo4jClient.recursivelyTraverseOutgoingEdges("OntologyClass", ontologyId + "+class+" + iri, relationIRIs, Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));

    }

    public Page<V1Term> getChildren(String ontologyId, String iri, String lang, Pageable pageable) {

        return this.neo4jClient.traverseIncomingEdges("OntologyClass", ontologyId + "+class+" + iri, Arrays.asList("directParent"), Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));
    }

    public Page<V1Term> getHierarchicalChildren(String ontologyId, String iri, String lang, Pageable pageable) {

        List<String> relationIRIs = List.of("hierarchicalParent");

        return this.neo4jClient.traverseIncomingEdges("OntologyClass", ontologyId + "+class+" + iri, relationIRIs, Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));

    }

    public Page<V1Term> getHierarchicalDescendants(String ontologyId, String iri, String lang, Pageable pageable) {

        List<String> relationIRIs = List.of("hierarchicalParent");

        return this.neo4jClient.recursivelyTraverseIncomingEdges("OntologyClass", ontologyId + "+class+" + iri, relationIRIs, Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));
    }


    public Page<V1Term> getDescendants(String ontologyId, String iri, String lang, Pageable pageable) {

        return this.neo4jClient.recursivelyTraverseIncomingEdges("OntologyClass", ontologyId + "+class+" + iri, Arrays.asList("directParent"), Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));

    }

    public Page<V1Term> getAncestors(String ontologyId, String iri, String lang, Pageable pageable) {

        V1Ontology ontology = ontologyRepository.get(ontologyId, lang);

        return this.neo4jClient.recursivelyTraverseOutgoingEdges("OntologyClass", ontologyId + "+class+" + iri, Arrays.asList("directParent"), Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));

    }

    public Page<V1Term> getRelated(String ontologyId, String iri, String lang, String relation, Pageable pageable) {

        return this.neo4jClient.traverseOutgoingEdges(
                        "OntologyClass", ontologyId + "+class+" + iri,
                        Arrays.asList("relatedTo"),
                        Map.of("property", relation),
                        pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));

    }

    public V1Term findByOntologyAndIri(String ontologyId, String iri, String lang) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        JsonElement first;
        try {
            first = solrClient.getFirst(query);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
        return V1TermMapper.mapTerm(first, lang);

    }

    public Page<V1Term> findAllByOntology(String ontologyId, Boolean obsoletes, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        if (obsoletes != null) query.addFilter("isObsolete", List.of(Boolean.toString(obsoletes)), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public V1Term findByOntologyAndShortForm(String ontologyId, String shortForm, String lang) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter("shortForm", List.of(shortForm), SearchType.WHOLE_FIELD);

        JsonElement first;
        try {
            first = solrClient.getFirst(query);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
        return V1TermMapper.mapTerm(first, lang);
    }

    public V1Term findByOntologyAndOboId(String ontologyId, String oboId, String lang) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter("curie", List.of(oboId), SearchType.WHOLE_FIELD);

        JsonElement first;
        try {
            first = solrClient.getFirst(query);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
        return V1TermMapper.mapTerm(first, lang);

    }

    public Page<V1Term> getRoots(String ontologyId, boolean obsolete, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter("hasDirectParent", List.of("false"), SearchType.WHOLE_FIELD);
        query.addFilter("hasHierarchicalParent", List.of("false"), SearchType.WHOLE_FIELD);

        if (!obsolete)
            query.addFilter("isObsolete", List.of("false"), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
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

    public Page<V1Term> findAll(String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public Page<V1Term> findAllByIsDefiningOntology(String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("isDefiningOntology", List.of("true"), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));

    }

    public Page<V1Term> findAllByIri(String iri, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public Page<V1Term> findAllByIriAndIsDefiningOntology(String iri, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("isDefiningOntology", List.of("true"), SearchType.WHOLE_FIELD);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public Page<V1Term> findAllByShortForm(String shortForm, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("shortForm", List.of(shortForm), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public Page<V1Term> findAllByShortFormAndIsDefiningOntology(String shortForm, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("isDefiningOntology", List.of("true"), SearchType.WHOLE_FIELD);
        query.addFilter("shortForm", List.of(shortForm), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public Page<V1Term> findAllByOboId(String oboId, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("curie", List.of(oboId), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));

    }

    public Page<V1Term> findAllByOboIdAndIsDefiningOntology(String oboId, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("isDefiningOntology", List.of("true"), SearchType.WHOLE_FIELD);
        query.addFilter("curie", List.of(oboId), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    //    @Query (countQuery = "MATCH (i:Individual)-[INSTANCEOF]->(c:Class) WHERE i.ontology_name = {0} AND c.iri = {1} RETURN count(i)",
    //            value = "MATCH (i:Individual)-[INSTANCEOF]->(c:Class) WHERE i.ontology_name = {0} AND c.iri = {1} RETURN i")
    public Page<V1Individual> getInstances(String ontologyId, String iri, Pageable pageable) {
        throw new RuntimeException();
    }

}
