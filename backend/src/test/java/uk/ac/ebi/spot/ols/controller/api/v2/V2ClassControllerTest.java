package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.ac.ebi.spot.ols.controller.api.exception.ResourceNotFoundException;
import uk.ac.ebi.spot.ols.repository.ClassRepository;
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

class V2ClassControllerTest {

    private V2ClassController controller;
    private RecordingClassRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RecordingClassRepository();
        controller = new V2ClassController();
        controller.classRepository = repository;
    }

    @Test
    void globalListOwnsObsoleteFilteringAndReservedParameters() throws Exception {
        Pageable pageable = PageRequest.of(2, 7);
        JsonTransformOptions options = new JsonTransformOptions();
        options.resolveReferences = true;
        MultiValueMap<String, String> requestProperties = new LinkedMultiValueMap<>();
        requestProperties.put("sort", List.of("iri,desc"));
        requestProperties.put("http://example.org/category", List.of("clinical", "research"));

        controller.getClasses(
                pageable, "liver", "label definition", "label^10", true, false,
                requestProperties, "fr", options);

        assertEquals(RecordingClassRepository.Call.GLOBAL_LIST, repository.call);
        assertSame(pageable, repository.pageable);
        assertEquals("fr", repository.lang);
        assertEquals("liver", repository.search);
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
    void globalListCanIncludeObsoleteClasses() throws Exception {
        controller.getClasses(
                PageRequest.of(0, 20), null, null, null, false, true,
                new LinkedMultiValueMap<>(), "en", new JsonTransformOptions());

        assertFalse(repository.properties.containsKey("isObsolete"));
    }

    @Test
    void ontologyListScopesTheCallAndOwnsObsoleteFiltering() throws Exception {
        MultiValueMap<String, String> requestProperties = new LinkedMultiValueMap<>();
        requestProperties.put("subset", List.of("disease"));

        controller.getClasses(
                PageRequest.of(1, 5), "efo", "liver", "label", "label^5", false,
                false, requestProperties, "en", new JsonTransformOptions());

        assertEquals(RecordingClassRepository.Call.ONTOLOGY_LIST, repository.call);
        assertEquals("efo", repository.ontologyId);
        assertEquals(
                Map.of("isObsolete", List.of("false"), "subset", List.of("disease")),
                repository.properties);
    }

    @Test
    void singleClassDecodesTheIriBeforeRepositoryDelegation() throws Exception {
        repository.result = classJson("http://example.org/EFO_0001", "Liver disease");

        var response = controller.getClass(
                "efo", "http%3A%2F%2Fexample.org%2FEFO_0001", "en",
                new JsonTransformOptions());

        assertEquals("http://example.org/EFO_0001", repository.iri);
        assertEquals("Liver disease", response.getBody().any().get("label"));
    }

    @Test
    void singleClassThrowsNotFoundWhenTheRepositoryReturnsNull() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> controller.getClass(
                        "efo", "http%3A%2F%2Fexample.org%2Fmissing", "en",
                        new JsonTransformOptions()));
    }

    @Test
    void relatedFromDecodesAndDelegatesItsPublicArguments() throws Exception {
        Pageable pageable = PageRequest.of(1, 3);
        JsonTransformOptions options = new JsonTransformOptions();

        controller.getClassRelatedFrom(
                pageable, "efo", "http%3A%2F%2Fexample.org%2FEFO_0001", "fr", options);

        assertHierarchyCall(
                RecordingClassRepository.Call.RELATED_FROM,
                pageable,
                "http://example.org/EFO_0001",
                "fr",
                options,
                false);
    }

    @Test
    void childrenDecodesAndDelegatesSearchAndObsoleteSelection() throws Exception {
        Pageable pageable = PageRequest.of(1, 3);
        JsonTransformOptions options = new JsonTransformOptions();

        controller.getChildrenByOntology(
                pageable, "efo", "http%3A%2F%2Fexample.org%2FEFO_0001", true,
                "clinical", "fr", options);

        assertHierarchyCall(
                RecordingClassRepository.Call.CHILDREN,
                pageable,
                "http://example.org/EFO_0001",
                "fr",
                options,
                true);
        assertEquals("clinical", repository.search);
    }

    @ParameterizedTest
    @EnumSource(HierarchyRoute.class)
    void hierarchyRoutesDecodeAndDelegateEveryControllerOwnedArgument(HierarchyRoute route)
            throws Exception {
        Pageable pageable = PageRequest.of(2, 4);
        JsonTransformOptions options = new JsonTransformOptions();
        String encodedIri = "http%3A%2F%2Fexample.org%2FEFO_1001";

        switch (route) {
            case ANCESTORS -> controller.getAncestorsByOntology(
                    pageable, "efo", encodedIri, true, "de", options);
            case DESCENDANTS -> controller.getDescendantsByOntology(
                    pageable, "efo", encodedIri, true, "de", options);
            case HIERARCHICAL_DESCENDANTS -> controller.getHierarchicalDescendantsByOntology(
                    pageable, "efo", encodedIri, true, "de", options);
            case HIERARCHICAL_CHILDREN -> controller.getHierarchicalChildrenByOntology(
                    pageable, "efo", encodedIri, true, "de", options);
            case HIERARCHICAL_ANCESTORS -> controller.getHierarchicalAncestorsByOntology(
                    pageable, "efo", encodedIri, true, "de", options);
            case INDIVIDUAL_ANCESTORS -> controller.getIndividualAncestorsByOntology(
                    pageable, "efo", encodedIri, true, "de", options);
        }

        assertHierarchyCall(
                route.call, pageable, "http://example.org/EFO_1001", "de", options, true);
    }

    private void assertHierarchyCall(
            RecordingClassRepository.Call call,
            Pageable pageable,
            String iri,
            String lang,
            JsonTransformOptions options,
            boolean includeObsolete) {
        assertEquals(call, repository.call);
        assertEquals("efo", repository.ontologyId);
        assertEquals(iri, repository.iri);
        assertSame(pageable, repository.pageable);
        assertEquals(lang, repository.lang);
        assertSame(options, repository.outputOptions);
        assertEquals(includeObsolete, repository.includeObsolete);
    }

    private static JsonElement classJson(String iri, String label) {
        return JsonParser.parseString("""
                {"type":["entity","class"],"ontologyId":"efo","iri":"%s","label":"%s"}
                """.formatted(iri, label));
    }

    private enum HierarchyRoute {
        ANCESTORS(RecordingClassRepository.Call.ANCESTORS),
        DESCENDANTS(RecordingClassRepository.Call.DESCENDANTS),
        HIERARCHICAL_DESCENDANTS(RecordingClassRepository.Call.HIERARCHICAL_DESCENDANTS),
        HIERARCHICAL_CHILDREN(RecordingClassRepository.Call.HIERARCHICAL_CHILDREN),
        HIERARCHICAL_ANCESTORS(RecordingClassRepository.Call.HIERARCHICAL_ANCESTORS),
        INDIVIDUAL_ANCESTORS(RecordingClassRepository.Call.INDIVIDUAL_ANCESTORS);

        private final RecordingClassRepository.Call call;

        HierarchyRoute(RecordingClassRepository.Call call) {
            this.call = call;
        }
    }

    private static class RecordingClassRepository extends ClassRepository {
        private enum Call {
            GLOBAL_LIST,
            ONTOLOGY_LIST,
            RELATED_FROM,
            CHILDREN,
            ANCESTORS,
            DESCENDANTS,
            HIERARCHICAL_DESCENDANTS,
            HIERARCHICAL_CHILDREN,
            HIERARCHICAL_ANCESTORS,
            INDIVIDUAL_ANCESTORS
        }

        private Call call;
        private Pageable pageable;
        private String ontologyId;
        private String iri;
        private String lang;
        private String search;
        private String searchFields;
        private String boostFields;
        private boolean exactMatch;
        private boolean includeObsolete;
        private Map<String, Collection<String>> properties;
        private JsonTransformOptions outputOptions;
        private JsonElement result;

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
            call = Call.GLOBAL_LIST;
            recordList(pageable, lang, search, searchFields, boostFields, exactMatch,
                    properties, outputOptions);
            return facetedPage(pageable);
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
            call = Call.ONTOLOGY_LIST;
            this.ontologyId = ontologyId;
            recordList(pageable, lang, search, searchFields, boostFields, exactMatch,
                    properties, outputOptions);
            return facetedPage(pageable);
        }

        @Override
        public JsonElement getByOntologyIdAndIri(
                String ontologyId,
                String iri,
                String lang,
                JsonTransformOptions outputOptions) {
            recordHierarchy(ontologyId, null, iri, false, lang, outputOptions);
            return result;
        }

        @Override
        public OlsFacetedResultsPage<JsonElement> getRelatedFrom(
                String ontologyId,
                String iri,
                Pageable pageable,
                String lang,
                JsonTransformOptions outputOptions) {
            call = Call.RELATED_FROM;
            recordHierarchy(ontologyId, pageable, iri, false, lang, outputOptions);
            return facetedPage(pageable);
        }

        @Override
        public Page<JsonElement> getChildrenByOntologyId(
                String ontologyId,
                Pageable pageable,
                String iri,
                boolean includeObsolete,
                String search,
                String lang,
                JsonTransformOptions outputOptions) {
            call = Call.CHILDREN;
            this.search = search;
            recordHierarchy(ontologyId, pageable, iri, includeObsolete, lang, outputOptions);
            return page(pageable);
        }

        @Override
        public Page<JsonElement> getAncestorsByOntologyId(
                String ontologyId, Pageable pageable, String iri, boolean includeObsolete,
                String lang, JsonTransformOptions outputOptions) {
            return hierarchy(Call.ANCESTORS, ontologyId, pageable, iri, includeObsolete, lang,
                    outputOptions);
        }

        @Override
        public Page<JsonElement> getDescendantsByOntologyId(
                String ontologyId, Pageable pageable, String iri, boolean includeObsolete,
                String lang, JsonTransformOptions outputOptions) {
            return hierarchy(Call.DESCENDANTS, ontologyId, pageable, iri, includeObsolete, lang,
                    outputOptions);
        }

        @Override
        public Page<JsonElement> getHierarchicalDescendantsByOntologyId(
                String ontologyId, Pageable pageable, String iri, boolean includeObsolete,
                String lang, JsonTransformOptions outputOptions) {
            return hierarchy(Call.HIERARCHICAL_DESCENDANTS, ontologyId, pageable, iri,
                    includeObsolete, lang, outputOptions);
        }

        @Override
        public Page<JsonElement> getHierarchicalChildrenByOntologyId(
                String ontologyId, Pageable pageable, String iri, boolean includeObsolete,
                String lang, JsonTransformOptions outputOptions) {
            return hierarchy(Call.HIERARCHICAL_CHILDREN, ontologyId, pageable, iri,
                    includeObsolete, lang, outputOptions);
        }

        @Override
        public Page<JsonElement> getHierarchicalAncestorsByOntologyId(
                String ontologyId, Pageable pageable, String iri, boolean includeObsolete,
                String lang, JsonTransformOptions outputOptions) {
            return hierarchy(Call.HIERARCHICAL_ANCESTORS, ontologyId, pageable, iri,
                    includeObsolete, lang, outputOptions);
        }

        @Override
        public Page<JsonElement> getIndividualAncestorsByOntologyId(
                String ontologyId, Pageable pageable, String iri, boolean includeObsolete,
                String lang, JsonTransformOptions outputOptions) {
            return hierarchy(Call.INDIVIDUAL_ANCESTORS, ontologyId, pageable, iri,
                    includeObsolete, lang, outputOptions);
        }

        private Page<JsonElement> hierarchy(
                Call call,
                String ontologyId,
                Pageable pageable,
                String iri,
                boolean includeObsolete,
                String lang,
                JsonTransformOptions outputOptions) {
            this.call = call;
            recordHierarchy(ontologyId, pageable, iri, includeObsolete, lang, outputOptions);
            return page(pageable);
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
                boolean includeObsolete,
                String lang,
                JsonTransformOptions outputOptions) {
            this.ontologyId = ontologyId;
            this.pageable = pageable;
            this.iri = iri;
            this.includeObsolete = includeObsolete;
            this.lang = lang;
            this.outputOptions = outputOptions;
        }

        private static OlsFacetedResultsPage<JsonElement> facetedPage(Pageable pageable) {
            return new OlsFacetedResultsPage<>(List.of(), Map.of(), pageable, 0);
        }

        private static Page<JsonElement> page(Pageable pageable) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
    }
}
