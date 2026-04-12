package uk.ac.ebi.spot.ols.repository.v1;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v1.V1Property;
import org.springframework.beans.factory.annotation.Autowired;
import uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient;
import uk.ac.ebi.spot.ols.repository.search.SearchType;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.repository.v1.mappers.V1PropertyMapper;
import static uk.ac.ebi.ols.shared.DefinedFields.*;

@Component
public class V1PropertyRepository {

    @Autowired
    OlsSearchClient searchClient;

    @Autowired
    OlsPostgresClient postgresClient;

    @Autowired
    V1OntologyRepository ontologyRepository;

    public Page<V1Property> getParents(String ontologyId, String iri, String lang, Pageable pageable) {
        return postgresClient.getDirectParents(ontologyId + "+property+" + iri, Map.of(), pageable)
                .map(record -> V1PropertyMapper.mapProperty(record, lang));
    }

    public Page<V1Property> getChildren(String ontologyId, String iri, String lang, Pageable pageable) {
        return this.postgresClient.getDirectChildren(ontologyId + "+property+" + iri, Map.of(), pageable)
                .map(record -> V1PropertyMapper.mapProperty(record, lang));
    }

    public Page<V1Property> getDescendants(String ontologyId, String iri, String lang, Pageable pageable)  {
        return this.postgresClient.getDescendants(ontologyId + "+property+" + iri, Map.of(), pageable)
                .map(record -> V1PropertyMapper.mapProperty(record, lang));
    }

    public Page<V1Property> getAncestors(String ontologyId, String iri, String lang, Pageable pageable)  {
        return postgresClient.getAncestors(ontologyId + "+property+" + iri, Map.of(), pageable)
                .map(record -> V1PropertyMapper.mapProperty(record, lang));
    }

    public V1Property findByOntologyAndIri(String ontologyId, String iri, String lang)  {
        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        JsonElement result = searchClient.getFirst(query);
        if (result == null) {
            return null;
        }
        return V1PropertyMapper.mapProperty(result, lang);
    }

    public Page<V1Property> findAllByOntology(String ontologyId, String lang, Pageable pageable)  {
        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1PropertyMapper.mapProperty(result, lang));
    }

    public V1Property findByOntologyAndShortForm(String ontologyId, String shortForm, String lang)  {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter("shortForm", List.of(shortForm), SearchType.WHOLE_FIELD);

        JsonElement result = searchClient.getFirst(query);
        if (result == null) {
            return null;
        }
        return V1PropertyMapper.mapProperty(result, lang);

    }

    public V1Property findByOntologyAndOboId(String ontologyId, String oboId, String lang)  {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter("oboId", List.of(oboId), SearchType.WHOLE_FIELD);

        JsonElement result = searchClient.getFirst(query);
        if (result == null) {
            return null;
        }
        return V1PropertyMapper.mapProperty(result, lang);

    }

    public Page<V1Property> getRoots(String ontologyId, boolean obsolete, String lang, Pageable pageable)  {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter(HAS_DIRECT_PARENTS.getText(), List.of("false"), SearchType.WHOLE_FIELD);
        query.addFilter(HAS_HIERARCHICAL_PARENTS.getText(), List.of("false"), SearchType.WHOLE_FIELD);

        if(!obsolete)
            query.addFilter(IS_OBSOLETE.getText(), List.of("false"), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1PropertyMapper.mapProperty(result, lang));

    }

    public Page<V1Property> findAll(String lang, Pageable pageable)  {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1PropertyMapper.mapProperty(result, lang));

    }

    public Page<V1Property> findAllByIsDefiningOntology(String lang, Pageable pageable)  {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        query.addFilter(IS_DEFINING_ONTOLOGY.getText(), List.of("true"), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1PropertyMapper.mapProperty(result, lang));
    }

    public Page<V1Property> findAllByIri(String iri, String lang, Pageable pageable)  {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1PropertyMapper.mapProperty(result, lang));

    }

    public Page<V1Property> findAllByIriAndIsDefiningOntology(String iri, String lang, Pageable pageable)  {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);
        query.addFilter(IS_DEFINING_ONTOLOGY.getText(), List.of("true"), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1PropertyMapper.mapProperty(result, lang));
    }

    public Page<V1Property> findAllByShortForm(String shortForm, String lang, Pageable pageable)  {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        query.addFilter("shortForm", List.of(shortForm), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1PropertyMapper.mapProperty(result, lang));

    }

    public Page<V1Property> findAllByShortFormAndIsDefiningOntology(String shortForm, String lang, Pageable pageable)  {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        query.addFilter("shortForm", List.of(shortForm), SearchType.WHOLE_FIELD);
        query.addFilter(IS_DEFINING_ONTOLOGY.getText(), List.of("true"), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1PropertyMapper.mapProperty(result, lang));

    }

    public Page<V1Property> findAllByOboId(String oboId, String lang, Pageable pageable)  {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        query.addFilter("oboId", List.of(oboId), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1PropertyMapper.mapProperty(result, lang));

    }

    public Page<V1Property> findAllByOboIdAndIsDefiningOntology(String oboId, String lang, Pageable pageable)  {

        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("type", List.of("property"), SearchType.WHOLE_FIELD);
        query.addFilter("oboId", List.of(oboId), SearchType.WHOLE_FIELD);
        query.addFilter(IS_DEFINING_ONTOLOGY.getText(), List.of("true"), SearchType.WHOLE_FIELD);

        return searchClient.searchPaginated(query, pageable)
                .map(result -> V1PropertyMapper.mapProperty(result, lang));

    }
}
