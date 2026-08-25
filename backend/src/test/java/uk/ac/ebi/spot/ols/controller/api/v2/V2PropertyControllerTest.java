package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.ac.ebi.spot.ols.repository.PropertyRepository;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.controller.api.exception.ResourceNotFoundException;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2PropertyControllerTest {

    private V2PropertyController controller;
    private RecordingPropertyRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RecordingPropertyRepository();
        controller = new V2PropertyController();
        controller.propertyRepository = repository;
    }

    @Test
    void globalListOwnsObsoleteFilteringAndReservedParameters() throws Exception {
        Pageable pageable = PageRequest.of(2, 7);
        JsonTransformOptions outputOptions = new JsonTransformOptions();
        outputOptions.resolveReferences = true;
        MultiValueMap<String, String> requestProperties = new LinkedMultiValueMap<>();
        requestProperties.put("sort", List.of("iri,desc"));
        requestProperties.put("http://example.org/category", List.of("clinical", "research"));

        controller.getProperties(
                pageable,
                "specimen",
                "label definition",
                "label^10",
                true,
                false,
                requestProperties,
                "fr",
                outputOptions);

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
        assertSame(outputOptions, repository.outputOptions);
    }

    @Test
    void globalListIncludesObsoletePropertiesWhenRequested() throws Exception {
        controller.getProperties(
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
    void ontologyListScopesTheCallAndOwnsObsoleteFiltering() throws Exception {
        MultiValueMap<String, String> requestProperties = new LinkedMultiValueMap<>();
        requestProperties.put("subset", List.of("relations"));

        controller.getProperties(
                PageRequest.of(1, 5),
                "efo",
                "specimen",
                "label",
                "label^5",
                false,
                false,
                requestProperties,
                "en",
                new JsonTransformOptions());

        assertEquals(RecordingPropertyRepository.Call.ONTOLOGY_LIST, repository.call);
        assertEquals("efo", repository.ontologyId);
        assertEquals(
                Map.of("isObsolete", List.of("false"), "subset", List.of("relations")),
                repository.properties);
    }

    @Test
    void singlePropertyDecodesTheIriBeforeRepositoryDelegation() throws Exception {
        repository.property = property("http://example.org/EFO_0100", "has specimen");

        var response = controller.getProperty(
                "efo",
                "http%3A%2F%2Fexample.org%2FEFO_0100",
                "en",
                new JsonTransformOptions());

        assertEquals("http://example.org/EFO_0100", repository.iri);
        assertSame(repository.property, response.getBody());
    }

    @Test
    void singlePropertyThrowsNotFoundWhenTheRepositoryReturnsNull() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> controller.getProperty(
                        "efo",
                        "http%3A%2F%2Fexample.org%2Fmissing",
                        "en",
                        new JsonTransformOptions()));
    }

    @Test
    void childrenRouteDecodesAndDelegatesItsPublicArguments() throws Exception {
        Pageable pageable = PageRequest.of(1, 3);
        JsonTransformOptions options = new JsonTransformOptions();

        controller.getChildrenByOntology(
                pageable,
                "efo",
                "http%3A%2F%2Fexample.org%2FEFO_0100",
                "fr",
                options);

        assertEquals(RecordingPropertyRepository.Call.CHILDREN, repository.call);
        assertEquals("efo", repository.ontologyId);
        assertEquals("http://example.org/EFO_0100", repository.iri);
        assertSame(pageable, repository.pageable);
        assertEquals("fr", repository.lang);
        assertSame(options, repository.outputOptions);
    }

    @Test
    void ancestorsRouteDecodesAndDelegatesItsPublicArguments() throws Exception {
        Pageable pageable = PageRequest.of(2, 4);
        JsonTransformOptions options = new JsonTransformOptions();

        controller.getAncestorsByOntology(
                pageable,
                "efo",
                "http%3A%2F%2Fexample.org%2FEFO_0101",
                "de",
                options);

        assertEquals(RecordingPropertyRepository.Call.ANCESTORS, repository.call);
        assertEquals("efo", repository.ontologyId);
        assertEquals("http://example.org/EFO_0101", repository.iri);
        assertSame(pageable, repository.pageable);
        assertEquals("de", repository.lang);
        assertSame(options, repository.outputOptions);
    }

    private static V2Entity property(String iri, String label) {
        return new V2Entity(JsonParser.parseString("""
                {"type":["entity","property"],"ontologyId":"efo","iri":"%s","label":"%s"}
                """.formatted(iri, label)));
    }

    private static class RecordingPropertyRepository extends PropertyRepository {
        private enum Call { GLOBAL_LIST, ONTOLOGY_LIST, CHILDREN, ANCESTORS }

        private Call call;
        private Pageable pageable;
        private String ontologyId;
        private String iri;
        private String lang;
        private String search;
        private String searchFields;
        private String boostFields;
        private boolean exactMatch;
        private Map<String, Collection<String>> properties;
        private JsonTransformOptions outputOptions;
        private V2Entity property;

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
            return property;
        }

        @Override
        public Page<JsonElement> getChildrenByOntologyId(
                String ontologyId,
                Pageable pageable,
                String iri,
                String lang,
                JsonTransformOptions outputOptions) {
            this.call = Call.CHILDREN;
            recordHierarchy(ontologyId, pageable, iri, lang, outputOptions);
            return new PageImpl<>(List.of(), pageable, 0);
        }

        @Override
        public Page<JsonElement> getAncestorsByOntologyId(
                String ontologyId,
                Pageable pageable,
                String iri,
                String lang,
                JsonTransformOptions outputOptions) {
            this.call = Call.ANCESTORS;
            recordHierarchy(ontologyId, pageable, iri, lang, outputOptions);
            return new PageImpl<>(List.of(), pageable, 0);
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

        private void recordHierarchy(
                String ontologyId,
                Pageable pageable,
                String iri,
                String lang,
                JsonTransformOptions outputOptions) {
            this.ontologyId = ontologyId;
            this.pageable = pageable;
            this.iri = iri;
            this.lang = lang;
            this.outputOptions = outputOptions;
        }

        private static OlsFacetedResultsPage<JsonElement> emptyPage(Pageable pageable) {
            return new OlsFacetedResultsPage<>(List.of(), Map.of(), pageable, 0);
        }
    }
}
