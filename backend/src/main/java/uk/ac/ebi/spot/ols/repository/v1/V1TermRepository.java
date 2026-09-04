package uk.ac.ebi.spot.ols.repository.v1;

import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery;
import uk.ac.ebi.spot.ols.repository.search.SearchType;
import uk.ac.ebi.spot.ols.repository.v1.mappers.V1TermMapper;
import static uk.ac.ebi.ols.shared.DefinedFields.*;

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
    OlsPostgresClient postgresClient;

    @Autowired
    OlsSearchClient searchClient;

    // Restricts direct-parent/child/ancestor/descendant hierarchy lookups to class entities.
    // Without this, an individual or property whose direct_parents/direct_ancestors array
    // happens to reference the same IRI (a normal RDF pattern — e.g. an individual's transitive
    // class ancestor) would be returned alongside the actual class hierarchy. ClassRepository
    // (V2) was patched for the identical leak in PR #1373; this mirrors that fix for V1.
    private static final Map<String, String> CLASS_NODE_PROPERTIES = Map.of("type", "OntologyClass");

    public Page<V1Term> getParents(String ontologyId, String iri, String lang, Pageable pageable) {

        return this.postgresClient.getDirectParents(
                ontologyId + "+class+" + iri, CLASS_NODE_PROPERTIES, pageable)
                .map(node -> V1TermMapper.mapTerm(node, lang));
    }

    public Page<V1Term> getHierarchicalParents(String ontologyId, String iri, String lang, Pageable pageable) {

        return this.postgresClient.getHierarchicalParents(ontologyId + "+class+" + iri, Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));
    }

    public Page<V1Term> getHierarchicalAncestors(String ontologyId, String iri, String lang, Pageable pageable) {

        return this.postgresClient.getHierarchicalAncestors(ontologyId + "+class+" + iri, Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));
    }

    public Page<V1Term> getChildren(String ontologyId, String iri, String lang, Pageable pageable) {

        return this.postgresClient.getDirectChildren(
                ontologyId + "+class+" + iri, CLASS_NODE_PROPERTIES, pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));
    }

    public Page<V1Term> getHierarchicalChildren(String ontologyId, String iri, String lang, Pageable pageable) {

        return this.postgresClient.getHierarchicalChildren(ontologyId + "+class+" + iri, Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));
    }

    public Page<V1Term> getHierarchicalDescendants(String ontologyId, String iri, String lang, Pageable pageable) {

        return this.postgresClient.getHierarchicalDescendants(ontologyId + "+class+" + iri, Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));
    }


    public Page<V1Term> getDescendants(String ontologyId, String iri, String lang, Pageable pageable) {

        return this.postgresClient.getDescendants(
                ontologyId + "+class+" + iri, CLASS_NODE_PROPERTIES, pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));
    }

    public Page<V1Term> getAncestors(String ontologyId, String iri, String lang, Pageable pageable) {

        return this.postgresClient.getAncestors(
                ontologyId + "+class+" + iri, CLASS_NODE_PROPERTIES, pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));
    }

    public Page<V1Term> getRelated(String ontologyId, String iri, String lang, String relation, Pageable pageable) {

        return this.postgresClient.getRelatedTo(ontologyId + "+class+" + iri, Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));
    }

    public V1Term findByOntologyAndIri(String ontologyId, String iri, String lang) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        JsonElement first;
        try {
            first = searchClient.getFirst(query);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
        if (first == null) {
            return null;
        }
        return V1TermMapper.mapTerm(first, lang);

    }

    public Page<V1Term> findAllByOntology(String ontologyId, Boolean obsoletes, String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        if (obsoletes != null) query.addFilter(IS_OBSOLETE.getText(), List.of(Boolean.toString(obsoletes)), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public V1Term findByOntologyAndShortForm(String ontologyId, String shortForm, String lang) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter("shortForm", List.of(shortForm), SearchType.WHOLE_FIELD);

        JsonElement first;
        try {
            first = searchClient.getFirst(query);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
        if (first == null) {
            return null;
        }
        return V1TermMapper.mapTerm(first, lang);
    }

    public V1Term findByOntologyAndOboId(String ontologyId, String oboId, String lang) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter("curie", List.of(oboId), SearchType.WHOLE_FIELD);

        JsonElement first;
        try {
            first = searchClient.getFirst(query);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
        if (first == null) {
            return null;
        }
        return V1TermMapper.mapTerm(first, lang);

    }

    public Page<V1Term> getRoots(String ontologyId, boolean obsolete, String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter(HAS_DIRECT_PARENTS.getText(), List.of("false"), SearchType.WHOLE_FIELD);
        query.addFilter(HAS_HIERARCHICAL_PARENTS.getText(), List.of("false"), SearchType.WHOLE_FIELD);

        if (!obsolete)
            query.addFilter(IS_OBSOLETE.getText(), List.of("false"), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    //    @Query (countQuery = "MATCH (n:PreferredRootTerm) WHERE n.ontology_name = {0} AND n.is_obsolete = {1} RETURN count(n)",
    //            value = "MATCH (n:PreferredRootTerm) WHERE n.ontology_name = {0} AND n.is_obsolete = {1} RETURN n")
    public Page<V1Term> getPreferredRootTerms(String ontologyId, boolean obsolete, String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter("isPreferredRoot", List.of("true"), SearchType.WHOLE_FIELD);

        if (!obsolete)
            query.addFilter(IS_OBSOLETE.getText(), List.of(Boolean.toString(obsolete)), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    //    @Query (value = "MATCH (n:PreferredRootTerm) WHERE n.ontology_name = {0} AND n.is_obsolete = {1} RETURN count(n)")
    public long getPreferredRootTermCount(String ontologyId, boolean obsolete) {
        throw new RuntimeException();
    }

    public Page<V1Term> findAll(String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public Page<V1Term> findAllByIsDefiningOntology(String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter(IS_DEFINING_ONTOLOGY.getText(), List.of("true"), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));

    }

    public Page<V1Term> findAllByIri(String iri, String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public Page<V1Term> findAllByIriAndIsDefiningOntology(String iri, String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter(IS_DEFINING_ONTOLOGY.getText(), List.of("true"), SearchType.WHOLE_FIELD);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public Page<V1Term> findAllByShortForm(String shortForm, String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("shortForm", List.of(shortForm), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public Page<V1Term> findAllByShortFormAndIsDefiningOntology(String shortForm, String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter(IS_DEFINING_ONTOLOGY.getText(), List.of("true"), SearchType.WHOLE_FIELD);
        query.addFilter("shortForm", List.of(shortForm), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public Page<V1Term> findAllByOboId(String oboId, String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("curie", List.of(oboId), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));

    }

    public Page<V1Term> findAllByOboIdAndIsDefiningOntology(String oboId, String lang, Pageable pageable) {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter(IS_DEFINING_ONTOLOGY.getText(), List.of("true"), SearchType.WHOLE_FIELD);
        query.addFilter("curie", List.of(oboId), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    //    @Query (countQuery = "MATCH (i:Individual)-[INSTANCEOF]->(c:Class) WHERE i.ontology_name = {0} AND c.iri = {1} RETURN count(i)",
    //            value = "MATCH (i:Individual)-[INSTANCEOF]->(c:Class) WHERE i.ontology_name = {0} AND c.iri = {1} RETURN i")
    public Page<V1Individual> getInstances(String ontologyId, String iri, Pageable pageable) {
        throw new RuntimeException();
    }

}
