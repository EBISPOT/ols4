package uk.ac.ebi.spot.ols.repository.v1;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit coverage for {@link V1GraphRepository}'s three pure-logic helpers --
 * {@code collectLinkedEntityLabels}, {@code findRelatedPropertyUri}, and {@code transformJson}.
 * All three are package-private/static with no Postgres dependency, so a hand-built JsonObject/
 * JSON-string fixture per branch is the complete unit-test bar for them, same idiom as
 * {@code AnnotationExtractorTest}. Everything else on {@link V1GraphRepository} (the three
 * getGraphFor* wrappers, getNode, getParentsAndRelatedTo, getRelatedFrom, and the
 * getGraphForEntity assembly logic that calls these helpers) is real-Postgres-backed and covered
 * instead by {@code V1GraphRepositoryIT} -- see docs/backend-testing-strategy.md's
 * "Implemented V1GraphRepository baseline" section.
 */
class V1GraphRepositoryTest {

    private final V1GraphRepository repository = new V1GraphRepository();

    // --- collectLinkedEntityLabels ------------------------------------------------------------

    @Test
    void collectLinkedEntityLabels_noLinkedEntitiesKey_isNoOp() {
        JsonObject node = JsonParser.parseString("""
                { "iri": "http://example.org/X", "label": "X" }
                """).getAsJsonObject();
        Map<String, String> iriToLabel = new HashMap<>();

        V1GraphRepository.collectLinkedEntityLabels(node, iriToLabel);

        assertThat(iriToLabel).isEmpty();
    }

    @Test
    void collectLinkedEntityLabels_firstCollectedLabelWins_notOverwrittenByLaterNode() {
        JsonObject firstNode = JsonParser.parseString("""
                { "linkedEntities": { "http://example.org/P": { "label": "First Label" } } }
                """).getAsJsonObject();
        JsonObject secondNode = JsonParser.parseString("""
                { "linkedEntities": { "http://example.org/P": { "label": "Second Label" } } }
                """).getAsJsonObject();
        Map<String, String> iriToLabel = new HashMap<>();

        // Simulates nodeMap.values() iteration order in getGraphForEntity: collectLinkedEntityLabels
        // is called once per node, in order, into the same accumulating map.
        V1GraphRepository.collectLinkedEntityLabels(firstNode, iriToLabel);
        V1GraphRepository.collectLinkedEntityLabels(secondNode, iriToLabel);

        assertThat(iriToLabel).containsEntry("http://example.org/P", "First Label");
    }

    @Test
    void collectLinkedEntityLabels_entryWithNoLabelField_isSkipped_notAddedAsNull() {
        JsonObject node = JsonParser.parseString("""
                { "linkedEntities": { "http://example.org/P": { "somethingElse": "x" } } }
                """).getAsJsonObject();
        Map<String, String> iriToLabel = new HashMap<>();

        V1GraphRepository.collectLinkedEntityLabels(node, iriToLabel);

        assertThat(iriToLabel).doesNotContainKey("http://example.org/P");
        assertThat(iriToLabel).isEmpty();
    }

    // --- findRelatedPropertyUri --------------------------------------------------------------

    @Test
    void findRelatedPropertyUri_noRelatedToKey_returnsNull() {
        String sourceJson = """
                { "iri": "http://example.org/X" }
                """;

        String result = V1GraphRepository.findRelatedPropertyUri(sourceJson, "http://example.org/TARGET");

        assertThat(result).isNull();
    }

    @Test
    void findRelatedPropertyUri_relatedToPresentButNotAnArray_returnsNull() {
        String sourceJson = """
                { "relatedTo": "not an array" }
                """;

        String result = V1GraphRepository.findRelatedPropertyUri(sourceJson, "http://example.org/TARGET");

        assertThat(result).isNull();
    }

    @Test
    void findRelatedPropertyUri_arrayWithNonObjectElements_skippedNotCrash_andStillFindsMatch() {
        String sourceJson = """
                {
                  "relatedTo": [
                    "a plain string element",
                    42,
                    { "property": "http://example.org/hasFoo", "value": "http://example.org/TARGET" }
                  ]
                }
                """;

        String result = V1GraphRepository.findRelatedPropertyUri(sourceJson, "http://example.org/TARGET");

        assertThat(result).isEqualTo("http://example.org/hasFoo");
    }

    @Test
    void findRelatedPropertyUri_matchingTargetIriFoundAmongMultipleEntries_returnsItsProperty() {
        String sourceJson = """
                {
                  "relatedTo": [
                    { "property": "http://example.org/hasA", "value": "http://example.org/A" },
                    { "property": "http://example.org/hasB", "value": "http://example.org/B" },
                    { "property": "http://example.org/hasC", "value": "http://example.org/C" }
                  ]
                }
                """;

        String result = V1GraphRepository.findRelatedPropertyUri(sourceJson, "http://example.org/B");

        assertThat(result).isEqualTo("http://example.org/hasB");
    }

    @Test
    void findRelatedPropertyUri_notFoundAmongAnyEntry_returnsNull() {
        String sourceJson = """
                {
                  "relatedTo": [
                    { "property": "http://example.org/hasA", "value": "http://example.org/A" }
                  ]
                }
                """;

        String result = V1GraphRepository.findRelatedPropertyUri(sourceJson, "http://example.org/NOT_PRESENT");

        assertThat(result).isNull();
    }

    // --- transformJson: proves the LocalizationTransform -> RemoveLiteralDatatypesTransform order ---

    /**
     * Each label entry is a reified literal tagged with its own {@code lang}. If transformJson ran
     * RemoveLiteralDatatypesTransform first, it would unwrap both literals to bare strings (losing
     * the lang tag) before LocalizationTransform ever sees them, so LocalizationTransform's
     * language filter would have nothing left to filter on and both languages would survive. Running
     * LocalizationTransform first (the actual, correct order) filters down to just the requested
     * language while the values are still lang-tagged, and RemoveLiteralDatatypesTransform then only
     * has to unwrap the single already-filtered result.
     */
    @Test
    void transformJson_chainsLocalizationThenRemoveLiteralDatatypes_inThatExactOrder() {
        String json = """
                {
                  "type": ["entity", "class"],
                  "iri": "http://example.org/X",
                  "label": [
                    { "type": ["literal"], "lang": "en", "value": "English Label" },
                    { "type": ["literal"], "lang": "fr", "value": "Label Francais" }
                  ],
                  "linkedEntities": {}
                }
                """;

        JsonObject result = repository.transformJson(json, "fr");

        assertThat(result.getAsJsonArray("label"))
                .extracting(com.google.gson.JsonElement::getAsString)
                .containsExactly("Label Francais");
    }
}
