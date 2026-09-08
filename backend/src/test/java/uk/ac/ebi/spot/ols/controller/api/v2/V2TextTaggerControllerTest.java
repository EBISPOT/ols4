package uk.ac.ebi.spot.ols.controller.api.v2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.service.TextTaggerService;
import uk.ac.ebi.spot.ols.service.TextTaggerService.TaggedEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2TextTaggerControllerTest {

    private RecordingTextTaggerService textTaggerService;
    private RecordingOlsSearchClient searchClient;
    private V2TextTaggerController controller;

    @BeforeEach
    void setUp() {
        textTaggerService = new RecordingTextTaggerService();
        searchClient = new RecordingOlsSearchClient();
        controller = new V2TextTaggerController();
        controller.textTaggerService = textTaggerService;
        controller.searchClient = searchClient;
    }

    // ------------------------------------------------------------------
    // POST /tag_text
    // ------------------------------------------------------------------

    @Test
    void tagTextReturns503AndDoesNotCallTheServiceWhenUnavailable() {
        textTaggerService.available = false;

        ResponseEntity<Map<String, Object>> response = post(Map.of("text", "insulin resistance"),
                null, null, null, 3, true, false);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("Text tagger service is not available", response.getBody().get("error"));
        assertEquals("The text tagger database has not been configured or the binary is not on the PATH",
                response.getBody().get("message"));
        assertNull(textTaggerService.lastText, "an unavailable service must never be asked to tag text");
    }

    @Test
    void tagTextReturns400WhenTextFieldIsAbsent() {
        textTaggerService.available = true;

        ResponseEntity<Map<String, Object>> response = post(Map.of(), null, null, null, 3, true, false);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Missing required field: text", response.getBody().get("error"));
        assertNull(textTaggerService.lastText);
    }

    @Test
    void tagTextReturns400WhenTextFieldIsNull() {
        textTaggerService.available = true;

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("text", null);

        ResponseEntity<Map<String, Object>> response = post(requestBody, null, null, null, 3, true, false);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Missing required field: text", response.getBody().get("error"));
    }

    @Test
    void tagTextReturns400WhenTextFieldIsEmpty() {
        textTaggerService.available = true;

        ResponseEntity<Map<String, Object>> response = post(Map.of("text", ""), null, null, null, 3, true, false);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Missing required field: text", response.getBody().get("error"));
    }

    @Test
    void tagTextForwardsEveryParameterToTheService() {
        textTaggerService.available = true;
        List<String> ontologyIds = List.of("efo", "hp");
        List<String> sources = List.of("sssom-mappings");

        post(Map.of("text", "insulin resistance"), ontologyIds, sources, "|", 5, false, false);

        assertEquals("insulin resistance", textTaggerService.lastText);
        assertEquals(ontologyIds, textTaggerService.lastOntologyIds);
        assertEquals(sources, textTaggerService.lastSources);
        assertEquals("|", textTaggerService.lastDelimiters);
        assertEquals(5, textTaggerService.lastMinLength);
        assertFalse(textTaggerService.lastIncludeSubstrings);
    }

    @Test
    void tagTextMapsAllFieldsAndOmitsOptionalFieldsWhenAbsent() {
        textTaggerService.available = true;
        textTaggerService.entitiesToReturn.add(new TaggedEntity(
                0, 7, "insulin", "http://purl.obolibrary.org/obo/CHEBI_5931", "chebi",
                "exact", "sssom-mappings", List.of("chemical", "hormone"), true));
        textTaggerService.entitiesToReturn.add(new TaggedEntity(
                8, 18, "resistance", "http://example.org/HP_0000001", "hp",
                null, null, null, false));

        ResponseEntity<Map<String, Object>> response = post(Map.of("text", "insulin resistance"),
                null, null, null, 3, true, true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertEquals("insulin resistance", body.get("text"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) body.get("entities");
        assertEquals(2, entities.size());

        Map<String, Object> withOptionalFields = entities.get(0);
        assertEquals(0, withOptionalFields.get("start"));
        assertEquals(7, withOptionalFields.get("end"));
        assertEquals("insulin", withOptionalFields.get("term_label"));
        assertEquals("http://purl.obolibrary.org/obo/CHEBI_5931", withOptionalFields.get("term_iri"));
        assertEquals("chebi", withOptionalFields.get("ontology_id"));
        assertEquals("exact", withOptionalFields.get("string_type"));
        assertEquals("sssom-mappings", withOptionalFields.get("source"));
        assertEquals(List.of("chemical", "hormone"), withOptionalFields.get("subject_categories"));
        assertEquals(Boolean.TRUE, withOptionalFields.get("is_obsolete"));

        Map<String, Object> withoutOptionalFields = entities.get(1);
        assertEquals(8, withoutOptionalFields.get("start"));
        assertEquals(18, withoutOptionalFields.get("end"));
        assertEquals("resistance", withoutOptionalFields.get("term_label"));
        assertEquals("hp", withoutOptionalFields.get("ontology_id"));
        assertFalse(withoutOptionalFields.containsKey("string_type"),
                "a null string_type must not appear in the response");
        assertFalse(withoutOptionalFields.containsKey("source"),
                "a null source must not appear in the response");
        assertFalse(withoutOptionalFields.containsKey("subject_categories"),
                "a null subject_categories must not appear in the response");
        assertFalse(withoutOptionalFields.containsKey("is_obsolete"),
                "is_obsolete must be omitted, not set to false, when the entity is not obsolete");
    }

    @Test
    void tagTextExcludesObsoleteEntitiesByDefault() {
        textTaggerService.available = true;
        textTaggerService.entitiesToReturn.add(new TaggedEntity(
                0, 7, "insulin", "http://purl.obolibrary.org/obo/CHEBI_5931", "chebi",
                null, null, null, true));
        textTaggerService.entitiesToReturn.add(new TaggedEntity(
                8, 18, "resistance", "http://example.org/HP_0000001", "hp",
                null, null, null, false));

        ResponseEntity<Map<String, Object>> response = post(Map.of("text", "insulin resistance"),
                null, null, null, 3, true, false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) response.getBody().get("entities");
        assertEquals(1, entities.size());
        assertEquals("resistance", entities.get(0).get("term_label"));
    }

    @Test
    void tagTextIncludesObsoleteEntitiesWhenRequested() {
        textTaggerService.available = true;
        textTaggerService.entitiesToReturn.add(new TaggedEntity(
                0, 7, "insulin", "http://purl.obolibrary.org/obo/CHEBI_5931", "chebi",
                null, null, null, true));
        textTaggerService.entitiesToReturn.add(new TaggedEntity(
                8, 18, "resistance", "http://example.org/HP_0000001", "hp",
                null, null, null, false));

        ResponseEntity<Map<String, Object>> response = post(Map.of("text", "insulin resistance"),
                null, null, null, 3, true, true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) response.getBody().get("entities");
        assertEquals(2, entities.size());
    }

    @Test
    void tagTextReturnsEmptyEntitiesListWhenTheServiceFindsNoMatches() {
        textTaggerService.available = true;

        ResponseEntity<Map<String, Object>> response = post(Map.of("text", "no matches here"),
                null, null, null, 3, true, false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(((List<?>) response.getBody().get("entities")).isEmpty());
    }

    // ------------------------------------------------------------------
    // GET /tag_text
    // ------------------------------------------------------------------

    @Test
    void tagTextStatusReportsAvailableTrue() {
        textTaggerService.available = true;

        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response =
                (ResponseEntity<Map<String, Object>>) controller.tagTextStatus();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Boolean.TRUE, response.getBody().get("available"));
    }

    @Test
    void tagTextStatusReportsAvailableFalse() {
        textTaggerService.available = false;

        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response =
                (ResponseEntity<Map<String, Object>>) controller.tagTextStatus();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Boolean.FALSE, response.getBody().get("available"));
    }

    // ------------------------------------------------------------------
    // GET /curation_sources
    // ------------------------------------------------------------------

    @Test
    void getCurationSourcesReturnsTheSearchClientsDistinctSources() {
        searchClient.curatedSources = List.of("sssom-mappings", "manual-curation");

        @SuppressWarnings("unchecked")
        ResponseEntity<List<String>> response = (ResponseEntity<List<String>>) controller.getCurationSources();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("sssom-mappings", "manual-curation"), response.getBody());
    }

    @Test
    void getCurationSourcesReturnsAnEmptyListWhenNoneAreCurated() {
        searchClient.curatedSources = List.of();

        @SuppressWarnings("unchecked")
        ResponseEntity<List<String>> response = (ResponseEntity<List<String>>) controller.getCurationSources();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> post(
            Map<String, Object> requestBody,
            List<String> ontologyIds,
            List<String> sources,
            String delimiters,
            int minLength,
            boolean includeSubstrings,
            boolean includeObsoleteEntities) {
        return (ResponseEntity<Map<String, Object>>) controller.tagText(
                requestBody, ontologyIds, sources, delimiters, minLength, includeSubstrings, includeObsoleteEntities);
    }

    // ------------------------------------------------------------------
    // Hand-rolled fakes (this repo's unit-test idiom; see HealthCheckControllerTest)
    // ------------------------------------------------------------------

    private static class RecordingTextTaggerService extends TextTaggerService {
        private boolean available;
        private final List<TaggedEntity> entitiesToReturn = new ArrayList<>();

        private String lastText;
        private List<String> lastOntologyIds;
        private List<String> lastSources;
        private String lastDelimiters;
        private int lastMinLength;
        private boolean lastIncludeSubstrings;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public List<TaggedEntity> tagText(String text, List<String> priorityOntologyIds, List<String> sources,
                                           String delimiters, int minLength, boolean includeSubstrings) {
            this.lastText = text;
            this.lastOntologyIds = priorityOntologyIds;
            this.lastSources = sources;
            this.lastDelimiters = delimiters;
            this.lastMinLength = minLength;
            this.lastIncludeSubstrings = includeSubstrings;
            return new ArrayList<>(entitiesToReturn);
        }
    }

    private static class RecordingOlsSearchClient extends OlsSearchClient {
        private List<String> curatedSources = new ArrayList<>();

        @Override
        public List<String> getDistinctCuratedSources() {
            return curatedSources;
        }
    }
}
