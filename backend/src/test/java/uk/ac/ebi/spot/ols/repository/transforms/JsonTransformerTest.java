package uk.ac.ebi.spot.ols.repository.transforms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit coverage for {@link JsonTransformer}. This is a pure static-method utility class
 * (no Spring bean, no Postgres dependency) whose only logic of its own is the two-flag dispatch
 * in {@code transformJson}: {@link LocalizationTransform} and {@link RemoveLiteralDatatypesTransform}
 * always run, while {@link ResolveReferencesTransform} and {@link ManchesterSyntaxTransform} run
 * only when {@code JsonTransformOptions.resolveReferences}/{@code .manchesterSyntax} are set. This
 * suite proves that dispatch across all four flag combinations and that the two unconditional
 * transforms always fire -- it does not attempt exhaustive branch coverage of the four sub-transform
 * classes themselves, each of which is its own separate, not-yet-covered Tier B backlog item (see
 * docs/backend-testing-strategy.md's "Implemented JsonTransformer baseline" section).
 *
 * <p>{@code JsonTransformOptions} itself is excluded from the Tier B backlog: it is a plain data
 * holder (two public booleans with getters/setters, no logic), used here only as an input fixture.
 */
class JsonTransformerTest {

    private static final String EFO_PARENT_IRI = "http://www.ebi.ac.uk/efo/EFO_0001641";
    private static final String HAS_SYMPTOM_IRI = "http://www.example.org/hasSymptom";
    private static final String FEVER_IRI = "http://www.example.org/Fever";
    private static final String OWL_ON_PROPERTY = "http://www.w3.org/2002/07/owl#onProperty";
    private static final String OWL_SOME_VALUES_FROM = "http://www.w3.org/2002/07/owl#someValuesFrom";
    private static final String EXPECTED_MANCHESTER = HAS_SYMPTOM_IRI + " some " + FEVER_IRI;

    private static JsonArray arrayOf(String... values) {
        JsonArray arr = new JsonArray();
        for (String v : values) {
            arr.add(v);
        }
        return arr;
    }

    private static JsonObject literal(String lang, String value) {
        JsonObject obj = new JsonObject();
        obj.add("type", arrayOf("literal"));
        obj.addProperty("lang", lang);
        obj.addProperty("value", value);
        return obj;
    }

    private static JsonTransformOptions options(boolean resolveReferences, boolean manchesterSyntax) {
        JsonTransformOptions opts = new JsonTransformOptions();
        opts.resolveReferences = resolveReferences;
        opts.manchesterSyntax = manchesterSyntax;
        return opts;
    }

    /** An owl:someValuesFrom restriction -- the minimal class-expression shape ManchesterSyntaxTransform collapses to a string. */
    private static JsonObject restrictionFixture() {
        JsonObject restriction = new JsonObject();
        restriction.add(OWL_ON_PROPERTY, arrayOf(HAS_SYMPTOM_IRI));
        restriction.add(OWL_SOME_VALUES_FROM, arrayOf(FEVER_IRI));
        return restriction;
    }

    /** The linked-entity object once ResolveReferencesTransform inlines it, including the "iri" it adds. */
    private static JsonObject resolvedParentEntity() {
        JsonObject parent = new JsonObject();
        parent.add("type", arrayOf("entity"));
        JsonArray label = new JsonArray();
        label.add("epithelial cell derived cell line");
        parent.add("label", label);
        parent.addProperty("iri", EFO_PARENT_IRI);
        return parent;
    }

    /**
     * A realistic V2-entity-shaped fixture combining everything transformJson's four sub-transforms
     * can act on: a two-language label (LocalizationTransform), a reference resolvable via
     * linkedEntities (ResolveReferencesTransform), and a class-expression restriction
     * (ManchesterSyntaxTransform) -- modelled on the shapes ClassRepository/EntityRepository/etc.
     * actually hand to JsonTransformer.transformJson() in production.
     */
    private static JsonObject entityFixture() {
        JsonObject json = new JsonObject();
        json.add("type", arrayOf("entity"));

        JsonArray label = new JsonArray();
        label.add(literal("en", "Diabetes"));
        label.add(literal("fr", "Diabète"));
        json.add("label", label);

        json.add("directParent", arrayOf(EFO_PARENT_IRI));

        JsonObject linkedEntities = new JsonObject();
        JsonObject parentEntity = new JsonObject();
        parentEntity.add("type", arrayOf("entity"));
        JsonArray parentLabel = new JsonArray();
        parentLabel.add("epithelial cell derived cell line");
        parentEntity.add("label", parentLabel);
        linkedEntities.add(EFO_PARENT_IRI, parentEntity);
        json.add("linkedEntities", linkedEntities);

        JsonArray subClassOf = new JsonArray();
        subClassOf.add(restrictionFixture());
        json.add("subClassOf", subClassOf);

        return json;
    }

    // --- The two unconditional transforms, proven in isolation -----------------------------

    @Test
    void localizationTransformSelectsRequestedLanguageAndDropsOtherLanguages() {
        JsonArray labels = new JsonArray();
        labels.add(literal("en", "Diabetes"));
        labels.add(literal("fr", "Diabète"));

        JsonElement result = JsonTransformer.transformJson(labels, "en", options(false, false));

        assertThat(result).isEqualTo(arrayOf("Diabetes"));
    }

    @Test
    void typedLiteralWithoutLangIsUnwrappedByRemoveLiteralDatatypesTransform() {
        // Per RemoveLiteralDatatypesTransform's own javadoc example: { "type": ["literal"], "value": "Diabetes" } -> "Diabetes".
        // A literal with no "lang" key only survives LocalizationTransform when lang="" is requested (no language filter);
        // this isolates RemoveLiteralDatatypesTransform's own unwrap, distinct from LocalizationTransform's separate
        // matching-language unwrap exercised above.
        JsonObject typedLiteral = new JsonObject();
        typedLiteral.add("type", arrayOf("literal"));
        typedLiteral.addProperty("value", "Diabetes");

        JsonElement result = JsonTransformer.transformJson(typedLiteral, "", options(false, false));

        assertThat(result).isEqualTo(new JsonPrimitive("Diabetes"));
    }

    // --- The two-flag dispatch, all four combinations ---------------------------------------
    // Each case also asserts the "label" field, proving the two unconditional transforms still
    // ran even when both/either optional transform is enabled, not only when both are disabled.

    @Test
    void neitherFlagEnabled_onlyUnconditionalTransformsRun() {
        JsonObject result = JsonTransformer.transformJson(entityFixture(), "en", options(false, false))
                .getAsJsonObject();

        assertThat(result.getAsJsonArray("label")).isEqualTo(arrayOf("Diabetes"));
        assertThat(result.getAsJsonArray("directParent").get(0)).isEqualTo(new JsonPrimitive(EFO_PARENT_IRI));
        assertThat(result.getAsJsonArray("subClassOf").get(0)).isEqualTo(restrictionFixture());
    }

    @Test
    void onlyResolveReferencesEnabled_resolvesReferencesButLeavesClassExpressionsAlone() {
        JsonObject result = JsonTransformer.transformJson(entityFixture(), "en", options(true, false))
                .getAsJsonObject();

        assertThat(result.getAsJsonArray("label")).isEqualTo(arrayOf("Diabetes"));
        assertThat(result.getAsJsonArray("directParent").get(0)).isEqualTo(resolvedParentEntity());
        assertThat(result.getAsJsonArray("subClassOf").get(0)).isEqualTo(restrictionFixture());
    }

    @Test
    void onlyManchesterSyntaxEnabled_convertsClassExpressionsButLeavesReferencesUnresolved() {
        JsonObject result = JsonTransformer.transformJson(entityFixture(), "en", options(false, true))
                .getAsJsonObject();

        assertThat(result.getAsJsonArray("label")).isEqualTo(arrayOf("Diabetes"));
        assertThat(result.getAsJsonArray("directParent").get(0)).isEqualTo(new JsonPrimitive(EFO_PARENT_IRI));
        assertThat(result.getAsJsonArray("subClassOf").get(0)).isEqualTo(new JsonPrimitive(EXPECTED_MANCHESTER));
    }

    @Test
    void bothFlagsEnabled_resolvesReferencesAndConvertsClassExpressions() {
        JsonObject result = JsonTransformer.transformJson(entityFixture(), "en", options(true, true))
                .getAsJsonObject();

        assertThat(result.getAsJsonArray("label")).isEqualTo(arrayOf("Diabetes"));
        assertThat(result.getAsJsonArray("directParent").get(0)).isEqualTo(resolvedParentEntity());
        assertThat(result.getAsJsonArray("subClassOf").get(0)).isEqualTo(new JsonPrimitive(EXPECTED_MANCHESTER));
    }
}
