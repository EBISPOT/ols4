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
import uk.ac.ebi.spot.ols.repository.EntityRepository;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2EntityControllerTest {

    private V2EntityController controller;
    private RecordingEntityRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RecordingEntityRepository();
        controller = new V2EntityController();
        controller.entityRepository = repository;
    }

    @Test
    void globalListOwnsObsoleteFilteringExclusionsAndReservedParameters() throws Exception {
        Pageable pageable = PageRequest.of(2, 7);
        JsonTransformOptions outputOptions = new JsonTransformOptions();
        outputOptions.resolveReferences = true;
        MultiValueMap<String, String> requestProperties = new LinkedMultiValueMap<>();
        requestProperties.put("facetFields", List.of("ontologyId type"));
        requestProperties.put("includeTotal", List.of("false"));
        requestProperties.put("http://example.org/category", List.of("clinical", "research"));

        controller.getEntities(
                pageable,
                "liver",
                "label definition",
                "label^10",
                "ontologyId type",
                true,
                false,
                "ncit,snomed",
                false,
                requestProperties,
                "fr",
                outputOptions);

        assertEquals(RecordingEntityRepository.Call.GLOBAL_LIST, repository.call);
        assertSame(pageable, repository.pageable);
        assertEquals("fr", repository.lang);
        assertEquals("liver", repository.search);
        assertEquals("label definition", repository.searchFields);
        assertEquals("label^10", repository.boostFields);
        assertEquals("ontologyId type", repository.facetFields);
        assertTrue(repository.exactMatch);
        assertEquals(List.of("ncit", "snomed"), repository.excludeOntologyIds);
        assertFalse(repository.includeTotal);
        assertEquals(
                Map.of(
                        "isObsolete", List.of("false"),
                        "http://example.org/category", List.of("clinical", "research")),
                repository.properties);
        assertSame(outputOptions, repository.outputOptions);
    }

    @Test
    void globalListIncludesObsoleteEntitiesAndLeavesEmptyExclusionsUnset() throws Exception {
        controller.getEntities(
                PageRequest.of(0, 20),
                null,
                null,
                null,
                null,
                false,
                true,
                "",
                true,
                new LinkedMultiValueMap<>(),
                "en",
                new JsonTransformOptions());

        assertFalse(repository.properties.containsKey("isObsolete"));
        assertNull(repository.excludeOntologyIds);
        assertTrue(repository.includeTotal);
    }

    @Test
    void ontologyListAddsScopeAndControllerOwnedObsoleteFilter() throws Exception {
        MultiValueMap<String, String> requestProperties = new LinkedMultiValueMap<>();
        requestProperties.put("subset", List.of("slim"));

        controller.getTerms(
                PageRequest.of(1, 5),
                "efo",
                "disease",
                "label",
                "label^5",
                "type",
                false,
                false,
                requestProperties,
                "en",
                new JsonTransformOptions());

        assertEquals(RecordingEntityRepository.Call.ONTOLOGY_LIST, repository.call);
        assertEquals("efo", repository.ontologyId);
        assertEquals(
                Map.of("isObsolete", List.of("false"), "subset", List.of("slim")),
                repository.properties);
    }

    @Test
    void singleEntityDecodesTheIriBeforeRepositoryDelegation() throws Exception {
        repository.entity = entity("http://example.org/EFO_0001", "Liver disease");

        var response = controller.getEntity(
                "efo",
                "http%3A%2F%2Fexample.org%2FEFO_0001",
                "en",
                new JsonTransformOptions());

        assertEquals("http://example.org/EFO_0001", repository.iri);
        assertEquals("Liver disease", response.getBody().any().get("label"));
    }

    @Test
    void singleEntityThrowsNotFoundWhenTheRepositoryReturnsNull() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> controller.getEntity(
                        "efo",
                        "http%3A%2F%2Fexample.org%2Fmissing",
                        "en",
                        new JsonTransformOptions()));
    }

    @Test
    void relatedFromDecodesTheIriBeforeRepositoryDelegation() throws Exception {
        Pageable pageable = PageRequest.of(1, 3);
        JsonTransformOptions outputOptions = new JsonTransformOptions();

        controller.getEntityRelatedFrom(
                pageable,
                "efo",
                "http%3A%2F%2Fexample.org%2FEFO_0001",
                "fr",
                outputOptions);

        assertEquals(RecordingEntityRepository.Call.RELATED_FROM, repository.call);
        assertEquals("efo", repository.ontologyId);
        assertEquals("http://example.org/EFO_0001", repository.iri);
        assertSame(pageable, repository.pageable);
        assertEquals("fr", repository.lang);
        assertSame(outputOptions, repository.outputOptions);
    }

    private static JsonElement entity(String iri, String label) {
        return JsonParser.parseString("""
                {"type":["entity","class"],"ontologyId":"efo","iri":"%s","label":"%s"}
                """.formatted(iri, label));
    }

    private static class RecordingEntityRepository extends EntityRepository {
        private enum Call { GLOBAL_LIST, ONTOLOGY_LIST, RELATED_FROM }

        private Call call;
        private Pageable pageable;
        private String ontologyId;
        private String iri;
        private String lang;
        private String search;
        private String searchFields;
        private String boostFields;
        private String facetFields;
        private boolean exactMatch;
        private Collection<String> excludeOntologyIds;
        private Map<String, Collection<String>> properties;
        private JsonTransformOptions outputOptions;
        private boolean includeTotal;
        private JsonElement entity;

        @Override
        public OlsFacetedResultsPage<JsonElement> find(
                Pageable pageable,
                String lang,
                String search,
                String searchFields,
                String boostFields,
                String facetFields,
                boolean exactMatch,
                Collection<String> excludeOntologyIds,
                Map<String, Collection<String>> properties,
                JsonTransformOptions outputOptions,
                boolean includeTotal) {
            this.call = Call.GLOBAL_LIST;
            recordList(
                    pageable,
                    lang,
                    search,
                    searchFields,
                    boostFields,
                    facetFields,
                    exactMatch,
                    properties,
                    outputOptions);
            this.excludeOntologyIds = excludeOntologyIds;
            this.includeTotal = includeTotal;
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
                String facetFields,
                boolean exactMatch,
                Map<String, Collection<String>> properties,
                JsonTransformOptions outputOptions) {
            this.call = Call.ONTOLOGY_LIST;
            this.ontologyId = ontologyId;
            recordList(
                    pageable,
                    lang,
                    search,
                    searchFields,
                    boostFields,
                    facetFields,
                    exactMatch,
                    properties,
                    outputOptions);
            return emptyPage(pageable);
        }

        @Override
        public JsonElement getByOntologyIdAndIri(
                String ontologyId,
                String iri,
                String lang,
                JsonTransformOptions outputOptions) {
            this.ontologyId = ontologyId;
            this.iri = iri;
            this.lang = lang;
            this.outputOptions = outputOptions;
            return entity;
        }

        @Override
        public OlsFacetedResultsPage<JsonElement> getRelatedFrom(
                String ontologyId,
                String iri,
                Pageable pageable,
                String lang,
                JsonTransformOptions outputOptions) {
            this.call = Call.RELATED_FROM;
            this.ontologyId = ontologyId;
            this.iri = iri;
            this.pageable = pageable;
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
                String facetFields,
                boolean exactMatch,
                Map<String, Collection<String>> properties,
                JsonTransformOptions outputOptions) {
            this.pageable = pageable;
            this.lang = lang;
            this.search = search;
            this.searchFields = searchFields;
            this.boostFields = boostFields;
            this.facetFields = facetFields;
            this.exactMatch = exactMatch;
            this.properties = properties;
            this.outputOptions = outputOptions;
        }

        private static OlsFacetedResultsPage<JsonElement> emptyPage(Pageable pageable) {
            return new OlsFacetedResultsPage<>(List.of(), Map.of(), pageable, 0);
        }
    }
}
