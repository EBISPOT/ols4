package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.ac.ebi.spot.ols.controller.api.exception.ResourceNotFoundException;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.OntologyRepository;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2OntologyControllerTest {

    private V2OntologyController controller;
    private RecordingOntologyRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RecordingOntologyRepository();
        controller = new V2OntologyController();
        controller.ontologyRepository = repository;
    }

    @Test
    void listExcludesObsoleteOntologiesAndForwardsSupportedParameters() throws Exception {
        Pageable pageable = PageRequest.of(2, 7);
        JsonTransformOptions outputOptions = new JsonTransformOptions();
        outputOptions.resolveReferences = true;
        MultiValueMap<String, String> requestProperties = new LinkedMultiValueMap<>();
        requestProperties.put("lang", List.of("fr"));
        requestProperties.put("page", List.of("2"));
        requestProperties.put("domain", List.of("biology", "health"));

        var response = controller.getOntologies(
                pageable,
                "factor",
                "title ontologyId",
                "title^10",
                true,
                false,
                requestProperties,
                "fr",
                outputOptions);

        assertEquals(HttpStatus.OK, ((org.springframework.http.ResponseEntity<?>) response).getStatusCode());
        assertSame(pageable, repository.pageable);
        assertEquals("fr", repository.lang);
        assertEquals("factor", repository.search);
        assertEquals("title ontologyId", repository.searchFields);
        assertEquals("title^10", repository.boostFields);
        assertTrue(repository.exactMatch);
        assertEquals(
                Map.of(
                        "isObsolete", List.of("false"),
                        "domain", List.of("biology", "health")),
                repository.properties);
        assertSame(outputOptions, repository.outputOptions);
    }

    @Test
    void listIncludesObsoleteOntologiesWhenRequested() throws Exception {
        controller.getOntologies(
                PageRequest.of(0, 20),
                null,
                null,
                null,
                false,
                true,
                new LinkedMultiValueMap<>(),
                "en",
                new JsonTransformOptions());

        assertFalse(repository.properties.containsKey("isObsolete"));
    }

    @Test
    void groupsOntologiesByTag() throws Exception {
        Map<String, List<V2Entity>> grouped = Map.of("experimental", List.of(entity("efo-test")));
        repository.groupedResult = grouped;

        var response = controller.getOntologiesByTag("en", new JsonTransformOptions());

        assertEquals("tags", repository.groupedField);
        assertSame(grouped, response.getBody());
    }

    @Test
    void groupsOntologiesByDomain() throws Exception {
        Map<String, List<V2Entity>> grouped = Map.of("biology", List.of(entity("efo-test")));
        repository.groupedResult = grouped;

        var response = controller.getOntologiesByDomain("en", new JsonTransformOptions());

        assertEquals("domain", repository.groupedField);
        assertSame(grouped, response.getBody());
    }

    @Test
    void returnsOntologyWhenItExists() throws Exception {
        V2Entity ontology = entity("efo-test");
        repository.entityById = ontology;

        var response = controller.getOntology("efo-test", "en", new JsonTransformOptions());

        assertEquals(HttpStatus.OK, ((org.springframework.http.ResponseEntity<?>) response).getStatusCode());
        assertSame(ontology, response.getBody());
        assertEquals("efo-test", repository.ontologyId);
    }

    @Test
    void throwsNotFoundWhenOntologyDoesNotExist() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> controller.getOntology("missing", "en", new JsonTransformOptions()));
    }

    private static V2Entity entity(String ontologyId) {
        return new V2Entity(JsonParser.parseString("{\"ontologyId\":\"" + ontologyId + "\"}"));
    }

    private static class RecordingOntologyRepository extends OntologyRepository {
        private Pageable pageable;
        private String lang;
        private String search;
        private String searchFields;
        private String boostFields;
        private boolean exactMatch;
        private Map<String, Collection<String>> properties;
        private JsonTransformOptions outputOptions;
        private String groupedField;
        private Map<String, List<V2Entity>> groupedResult = Map.of();
        private String ontologyId;
        private V2Entity entityById;

        @Override
        public OlsFacetedResultsPage<JsonElement> find(
                Pageable pageable,
                String lang,
                String search,
                String searchFields,
                String boostFields,
                boolean exactMatch,
                Map<String, Collection<String>> properties,
                JsonTransformOptions outputOptions) {
            this.pageable = pageable;
            this.lang = lang;
            this.search = search;
            this.searchFields = searchFields;
            this.boostFields = boostFields;
            this.exactMatch = exactMatch;
            this.properties = properties;
            this.outputOptions = outputOptions;
            return new OlsFacetedResultsPage<>(List.of(), Map.of(), pageable, 0);
        }

        @Override
        public Map<String, List<V2Entity>> getGroupedByField(
                String fieldName,
                String lang,
                JsonTransformOptions outputOptions) {
            this.groupedField = fieldName;
            return groupedResult;
        }

        @Override
        public V2Entity getById(String ontologyId, String lang, JsonTransformOptions outputOptions) {
            this.ontologyId = ontologyId;
            return entityById;
        }
    }
}
