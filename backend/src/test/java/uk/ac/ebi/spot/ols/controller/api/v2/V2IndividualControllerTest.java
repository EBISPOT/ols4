package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.ac.ebi.spot.ols.controller.api.exception.ResourceNotFoundException;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.IndividualRepository;
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

class V2IndividualControllerTest {

    private V2IndividualController controller;
    private RecordingIndividualRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RecordingIndividualRepository();
        controller = new V2IndividualController();
        controller.individualRepository = repository;
    }

    @Test
    void globalListOwnsObsoleteFilteringAndReservedParameters() throws Exception {
        Pageable pageable = PageRequest.of(2, 7);
        JsonTransformOptions options = new JsonTransformOptions();
        options.resolveReferences = true;
        MultiValueMap<String, String> requestProperties = new LinkedMultiValueMap<>();
        requestProperties.put("sort", List.of("iri,desc"));
        requestProperties.put("http://example.org/category", List.of("clinical", "research"));

        controller.getIndividuals(
                pageable, "specimen", "label definition", "label^10", true, false,
                requestProperties, "fr", options);

        assertEquals(RecordingIndividualRepository.Call.GLOBAL_LIST, repository.call);
        assertSame(pageable, repository.pageable);
        assertEquals("fr", repository.lang);
        assertEquals("specimen", repository.search);
        assertEquals("label definition", repository.searchFields);
        assertEquals("label^10", repository.boostFields);
        assertTrue(repository.exactMatch);
        assertEquals(
                Map.of(
                        "isObsolete", List.of("false"),
                        "http://example.org/category", List.of("clinical", "research")),
                repository.properties);
        assertSame(options, repository.outputOptions);
    }

    @Test
    void globalListIncludesObsoleteIndividualsWhenRequested() throws Exception {
        controller.getIndividuals(
                PageRequest.of(0, 20), null, null, null, false, true,
                new LinkedMultiValueMap<>(), "en", new JsonTransformOptions());

        assertFalse(repository.properties.containsKey("isObsolete"));
    }

    @Test
    void ontologyListScopesTheCallAndOwnsObsoleteFiltering() throws Exception {
        MultiValueMap<String, String> requestProperties = new LinkedMultiValueMap<>();
        requestProperties.put("subset", List.of("individuals"));

        controller.getIndividuals(
                PageRequest.of(1, 5), "efo", "specimen", "label", "label^5",
                false, false, requestProperties, "en", new JsonTransformOptions());

        assertEquals(RecordingIndividualRepository.Call.ONTOLOGY_LIST, repository.call);
        assertEquals("efo", repository.ontologyId);
        assertEquals(
                Map.of("isObsolete", List.of("false"), "subset", List.of("individuals")),
                repository.properties);
    }

    @Test
    void singleIndividualDecodesTheIriBeforeRepositoryDelegation() throws Exception {
        repository.individual = individual("http://example.org/EFO_I100", "Liver specimen alpha");

        var response = controller.getIndividual(
                "efo", "http%3A%2F%2Fexample.org%2FEFO_I100", "en",
                new JsonTransformOptions());

        assertEquals("http://example.org/EFO_I100", repository.iri);
        assertSame(repository.individual, response.getBody());
    }

    @Test
    void singleIndividualThrowsNotFoundWhenTheRepositoryReturnsNull() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> controller.getIndividual(
                        "efo", "http%3A%2F%2Fexample.org%2Fmissing", "en",
                        new JsonTransformOptions()));
    }

    @Test
    void classIndividualsDecodeTheClassIriAndDelegateEveryPublicArgument() throws Exception {
        Pageable pageable = PageRequest.of(1, 3);
        JsonTransformOptions options = new JsonTransformOptions();

        controller.getClassIndividuals(
                pageable, "efo", "http%3A%2F%2Fexample.org%2FEFO_0001", true, "de", options);

        assertEquals(RecordingIndividualRepository.Call.CLASS_INDIVIDUALS, repository.call);
        assertEquals("efo", repository.ontologyId);
        assertEquals("http://example.org/EFO_0001", repository.classIri);
        assertTrue(repository.includeObsoleteEntities);
        assertSame(pageable, repository.pageable);
        assertEquals("de", repository.lang);
        assertSame(options, repository.outputOptions);
    }

    private static V2Entity individual(String iri, String label) {
        return new V2Entity(JsonParser.parseString("""
                {"type":["entity","individual"],"ontologyId":"efo","iri":"%s","label":"%s"}
                """.formatted(iri, label)));
    }

    private static class RecordingIndividualRepository extends IndividualRepository {
        private enum Call { GLOBAL_LIST, ONTOLOGY_LIST, CLASS_INDIVIDUALS }

        private Call call;
        private Pageable pageable;
        private String ontologyId;
        private String iri;
        private String classIri;
        private String lang;
        private String search;
        private String searchFields;
        private String boostFields;
        private boolean exactMatch;
        private boolean includeObsoleteEntities;
        private Map<String, Collection<String>> properties;
        private JsonTransformOptions outputOptions;
        private V2Entity individual;

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
            this.call = Call.GLOBAL_LIST;
            recordList(
                    pageable, lang, search, searchFields, boostFields, exactMatch,
                    properties, outputOptions);
            return emptyPage(pageable);
        }

        @Override
        public OlsFacetedResultsPage<JsonElement> findByOntologyId(
                String ontologyId,
                Pageable pageable,
                String lang,
                String search,
                String searchFields,
                String boostFields,
                boolean exactMatch,
                Map<String, Collection<String>> properties,
                JsonTransformOptions outputOptions) {
            this.call = Call.ONTOLOGY_LIST;
            this.ontologyId = ontologyId;
            recordList(
                    pageable, lang, search, searchFields, boostFields, exactMatch,
                    properties, outputOptions);
            return emptyPage(pageable);
        }

        @Override
        public V2Entity getByOntologyIdAndIri(
                String ontologyId,
                String iri,
                String lang,
                JsonTransformOptions outputOptions) {
            this.ontologyId = ontologyId;
            this.iri = iri;
            this.lang = lang;
            this.outputOptions = outputOptions;
            return individual;
        }

        @Override
        public OlsFacetedResultsPage<JsonElement> getIndividualsOfClass(
                String ontologyId,
                String classIri,
                Pageable pageable,
                boolean includeObsoleteEntities,
                String lang,
                JsonTransformOptions outputOptions) {
            this.call = Call.CLASS_INDIVIDUALS;
            this.ontologyId = ontologyId;
            this.classIri = classIri;
            this.pageable = pageable;
            this.includeObsoleteEntities = includeObsoleteEntities;
            this.lang = lang;
            this.outputOptions = outputOptions;
            return emptyPage(pageable);
        }

        private void recordList(
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
        }

        private static OlsFacetedResultsPage<JsonElement> emptyPage(Pageable pageable) {
            return new OlsFacetedResultsPage<>(List.of(), Map.of(), pageable, 0);
        }
    }
}
