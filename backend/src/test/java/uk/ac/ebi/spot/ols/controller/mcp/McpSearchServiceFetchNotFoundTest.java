package uk.ac.ebi.spot.ols.controller.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.spot.ols.controller.api.exception.ResourceNotFoundException;
import uk.ac.ebi.spot.ols.repository.EntityRepository;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression test for a {@code NullPointerException} in {@link McpSearchService#fetch(String)}.
 *
 * <p>{@code EntityRepository.getByOntologyIdAndIri} returns a plain {@code null} (not an
 * exception) when {@code OlsSearchClient.getFirst} finds no matching row for a well-formed
 * {@code ontologyId}/{@code iri} pair. {@code fetch()} previously passed that {@code null} straight
 * into {@code McpFetchResult.fromJson(JsonElement)}, which immediately calls
 * {@code entity.getAsJsonObject()} with no null check - so a syntactically valid id for an entity
 * that genuinely does not exist threw an undocumented {@code NullPointerException} instead of a
 * clear "not found" error.
 *
 * <p>Found while adding real-Postgres coverage for {@code McpSearchService} on the
 * {@code test/cover-mcp-search-service} branch (both a hand-rolled-fake unit test and a real
 * Postgres reproduction hit this exact NPE before this fix). This is a standalone defect PR,
 * separate from that testing PR, per this programme's defect workflow.</p>
 */
class McpSearchServiceFetchNotFoundTest {

    @Test
    void throwsAClearResourceNotFoundExceptionInsteadOfANullPointerExceptionWhenNoEntityMatches() {
        McpSearchService service = new McpSearchService();
        service.entityRepository = new NullReturningEntityRepository();

        ResourceNotFoundException thrown = assertThrows(
                ResourceNotFoundException.class,
                () -> service.fetch("efo+http://example.org/DOES_NOT_EXIST"));

        assertEquals(
                "No entity found for ontologyId 'efo' and IRI 'http://example.org/DOES_NOT_EXIST'",
                thrown.getMessage());
    }

    @Test
    void stillSucceedsNormallyWhenTheRepositoryFindsAMatchingEntity() throws IOException {
        McpSearchService service = new McpSearchService();
        service.entityRepository = new MatchingEntityRepository();

        String json = service.fetch("efo+http://example.org/EFO_0001");

        JsonObject result = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("efo+http://example.org/EFO_0001", result.get("id").getAsString());
        assertEquals("http://example.org/EFO_0001", result.get("url").getAsString());
        assertFalse(result.get("isObsolete").getAsBoolean());
    }

    private static class NullReturningEntityRepository extends EntityRepository {
        @Override
        public JsonElement getByOntologyIdAndIri(
                String ontologyId, String iri, String lang, JsonTransformOptions outputOpts) {
            return null;
        }
    }

    private static class MatchingEntityRepository extends EntityRepository {
        @Override
        public JsonElement getByOntologyIdAndIri(
                String ontologyId, String iri, String lang, JsonTransformOptions outputOpts) {
            JsonObject json = new JsonObject();
            json.addProperty("ontologyId", ontologyId);
            json.addProperty("iri", iri);
            json.addProperty("curie", "EFO:0001");
            json.addProperty("label", "Liver disease");
            json.addProperty("isObsolete", false);
            JsonArray typeArray = new JsonArray();
            typeArray.add("entity");
            typeArray.add("class");
            json.add("type", typeArray);
            return JsonParser.parseString(json.toString());
        }
    }
}
