package uk.ac.ebi.spot.ols.controller.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.ols.model.mcp.McpOntology;
import uk.ac.ebi.spot.ols.repository.OntologyRepository;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for {@link McpOntologyService}'s own wiring decisions: default {@code lang}
 * resolution, the always-on {@code JsonTransformOptions} (resolveReferences/manchesterSyntax both
 * hardcoded {@code true}), the exact fixed-argument delegation to
 * {@link OntologyRepository#find} (a single hardcoded {@code PageRequest.of(0, 1000)} plus five
 * hardcoded {@code null}/{@code false} arguments — {@code search}, {@code searchFields},
 * {@code boostFields}, {@code exactMatch}, {@code properties}), and the plain-{@code List} (no
 * {@code McpPage} wrapper, unlike {@code McpClassService}/{@code McpEmbeddingService}) mapping of
 * the result content through {@link McpOntology#fromJson}.
 *
 * <p>{@code OntologyRepository} itself already has its own dedicated coverage elsewhere in this
 * programme ({@code OntologyRepositoryIT}); this suite only proves {@code McpOntologyService}'s own
 * wiring, not the repository's search/filter internals.</p>
 *
 * <p>Uses this repo's hand-rolled-fake idiom (a subclass overriding just the method under test, no
 * Mockito), the same idiom as {@code McpClassServiceTest}/{@code McpEmbeddingServiceTest}.</p>
 */
class McpOntologyServiceTest {

    private RecordingOntologyRepository ontologyRepository;
    private McpOntologyService service;

    @BeforeEach
    void setUp() {
        ontologyRepository = new RecordingOntologyRepository();
        service = new McpOntologyService();
        service.ontologyRepository = ontologyRepository;
    }

    // ------------------------------------------------------------------
    // lang default resolution
    // ------------------------------------------------------------------

    @Test
    void defaultsLangToEnglishWhenNull() throws IOException {
        service.listOntologies(null);

        assertEquals("en", ontologyRepository.lastLang);
    }

    @Test
    void passesThroughAnExplicitLangUnchanged() throws IOException {
        service.listOntologies("fr");

        assertEquals("fr", ontologyRepository.lastLang);
    }

    // ------------------------------------------------------------------
    // Fixed-argument delegation
    // ------------------------------------------------------------------

    @Test
    void delegatesWithExactFixedArguments() throws IOException {
        service.listOntologies("de");

        assertEquals(0, ontologyRepository.lastPageable.getPageNumber());
        assertEquals(1000, ontologyRepository.lastPageable.getPageSize());
        assertEquals("de", ontologyRepository.lastLang);
        assertNull(ontologyRepository.lastSearch);
        assertNull(ontologyRepository.lastSearchFields);
        assertNull(ontologyRepository.lastBoostFields);
        assertFalse(ontologyRepository.lastExactMatch);
        assertNull(ontologyRepository.lastProperties);
        assertTrue(ontologyRepository.lastOutputOpts.resolveReferences);
        assertTrue(ontologyRepository.lastOutputOpts.manchesterSyntax);
    }

    @Test
    void alwaysUsesFixedPageZeroWithPageSizeOneThousand() throws IOException {
        service.listOntologies(null);

        assertEquals(PageRequest.of(0, 1000), ontologyRepository.lastPageable);
    }

    @Test
    void alwaysSetsResolveReferencesAndManchesterSyntaxRegardlessOfLang() throws IOException {
        service.listOntologies("es");

        assertTrue(ontologyRepository.lastOutputOpts.resolveReferences);
        assertTrue(ontologyRepository.lastOutputOpts.manchesterSyntax);
    }

    // ------------------------------------------------------------------
    // Result mapping
    // ------------------------------------------------------------------

    @Test
    void mapsEachResultElementThroughMcpOntologyFromJsonInOrder() throws IOException {
        ontologyRepository.pageResult = new OlsFacetedResultsPage<>(
                new ArrayList<>(List.of(
                        ontologyJson("efo", "Experimental Factor Ontology", "An ontology for variables."),
                        ontologyJson("duo", "Data Use Ontology", "An ontology for data use permissions."))),
                Map.of(), PageRequest.of(0, 1000), 2);

        List<McpOntology> result = service.listOntologies(null);

        assertEquals(2, result.size());
        assertEquals("efo", result.get(0).ontologyId);
        assertEquals(List.of("Experimental Factor Ontology"), result.get(0).label);
        assertEquals(List.of("An ontology for variables."), result.get(0).definition);
        assertEquals("duo", result.get(1).ontologyId);
        assertEquals(List.of("Data Use Ontology"), result.get(1).label);
        assertEquals(List.of("An ontology for data use permissions."), result.get(1).definition);
    }

    @Test
    void returnsAPlainListNotAnMcpPage() throws IOException {
        ontologyRepository.pageResult = new OlsFacetedResultsPage<>(
                new ArrayList<>(List.of(ontologyJson("efo", "Experimental Factor Ontology", "definition"))),
                Map.of(), PageRequest.of(0, 1000), 1);

        List<McpOntology> result = service.listOntologies(null);

        assertTrue(result instanceof List);
        assertEquals(1, result.size());
    }

    @Test
    void returnsAnEmptyListWhenTheRepositoryFindsNoOntologies() throws IOException {
        ontologyRepository.pageResult = new OlsFacetedResultsPage<>(
                new ArrayList<>(), Map.of(), PageRequest.of(0, 1000), 0);

        List<McpOntology> result = service.listOntologies(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void mapsOntologiesWithNoLabelOrDefinitionToEmptyLists() throws IOException {
        ontologyRepository.pageResult = new OlsFacetedResultsPage<>(
                new ArrayList<>(List.of(ontologyJsonWithoutLabelOrDefinition("edam"))),
                Map.of(), PageRequest.of(0, 1000), 1);

        List<McpOntology> result = service.listOntologies(null);

        assertEquals("edam", result.get(0).ontologyId);
        assertEquals(List.of(), result.get(0).label);
        assertEquals(List.of(), result.get(0).definition);
    }

    // ------------------------------------------------------------------
    // Exception propagation
    // ------------------------------------------------------------------

    @Test
    void propagatesAnIOExceptionFromTheRepositoryUnchanged() {
        ontologyRepository.exceptionToThrow = new IOException("search backend unavailable");

        IOException thrown = assertThrows(IOException.class, () -> service.listOntologies(null));

        assertEquals("search backend unavailable", thrown.getMessage());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static JsonElement ontologyJson(String ontologyId, String label, String definition) {
        JsonObject json = new JsonObject();
        json.addProperty("ontologyId", ontologyId);
        JsonArray labelArray = new JsonArray();
        labelArray.add(label);
        json.add("label", labelArray);
        JsonArray definitionArray = new JsonArray();
        definitionArray.add(definition);
        json.add("definition", definitionArray);
        return JsonParser.parseString(json.toString());
    }

    private static JsonElement ontologyJsonWithoutLabelOrDefinition(String ontologyId) {
        JsonObject json = new JsonObject();
        json.addProperty("ontologyId", ontologyId);
        return JsonParser.parseString(json.toString());
    }

    // ------------------------------------------------------------------
    // Hand-rolled fake (this repo's unit-test idiom; see McpClassServiceTest)
    // ------------------------------------------------------------------

    private static class RecordingOntologyRepository extends OntologyRepository {
        private OlsFacetedResultsPage<JsonElement> pageResult =
                new OlsFacetedResultsPage<>(new ArrayList<>(), Map.of(), PageRequest.of(0, 1000), 0);
        private IOException exceptionToThrow;

        private Pageable lastPageable;
        private String lastLang;
        private String lastSearch;
        private String lastSearchFields;
        private String lastBoostFields;
        private boolean lastExactMatch;
        private Map<String, Collection<String>> lastProperties;
        private JsonTransformOptions lastOutputOpts;

        @Override
        public OlsFacetedResultsPage<JsonElement> find(
                Pageable pageable, String lang, String search, String searchFields, String boostFields,
                boolean exactMatch, Map<String, Collection<String>> properties,
                JsonTransformOptions outputOpts) throws IOException {
            this.lastPageable = pageable;
            this.lastLang = lang;
            this.lastSearch = search;
            this.lastSearchFields = searchFields;
            this.lastBoostFields = boostFields;
            this.lastExactMatch = exactMatch;
            this.lastProperties = properties;
            this.lastOutputOpts = outputOpts;
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return pageResult;
        }
    }
}
