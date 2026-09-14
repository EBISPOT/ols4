package uk.ac.ebi.spot.ols.repository.transforms;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Direct unit coverage for {@link RemoveLiteralDatatypesTransform}. This is a pure static-method
 * utility class (no Spring bean, no Postgres dependency) with a single public entry point,
 * {@code transform(JsonElement)}: it recurses through arrays and object values (via the shared
 * {@link uk.ac.ebi.spot.ols.repository.transforms.helpers.JsonCollectionHelper}, itself out of
 * scope for dedicated testing per the {@code ManchesterSyntaxTransform} baseline's note) and
 * collapses any object shaped {@code {"type": ["literal"], "value": <v>}} into
 * {@code transform(<v>)}, recursively.
 */
class RemoveLiteralDatatypesTransformTest {

    // --- Primitives and JsonNull: returned as-is --------------------------------------------

    @Test
    void stringPrimitiveIsReturnedAsIs() {
        JsonElement input = new JsonPrimitive("hello");

        assertEquals(input, RemoveLiteralDatatypesTransform.transform(input));
    }

    @Test
    void numberPrimitiveIsReturnedAsIs() {
        JsonElement input = new JsonPrimitive(42);

        assertEquals(input, RemoveLiteralDatatypesTransform.transform(input));
    }

    @Test
    void booleanPrimitiveIsReturnedAsIs() {
        JsonElement input = new JsonPrimitive(true);

        assertEquals(input, RemoveLiteralDatatypesTransform.transform(input));
    }

    @Test
    void jsonNullInstanceIsReturnedAsIs() {
        // JsonNull.INSTANCE (Gson's JSON null) is distinct from a raw Java null reference -- the
        // latter is the separate, empirically-investigated case in nullValueForMissingValueKey...
        // below. isJsonArray()/isJsonObject() are both false for JsonNull, so this falls to the
        // final "return object" branch, same as any other primitive.
        JsonElement result = RemoveLiteralDatatypesTransform.transform(JsonNull.INSTANCE);

        assertEquals(JsonNull.INSTANCE, result);
    }

    // --- Arrays: recurse into every element --------------------------------------------------

    @Test
    void arrayInputRecursesIntoEachElement() {
        JsonElement input = json("""
                [
                  {"type": ["literal"], "value": "Diabetes"},
                  "https://orcid.org/0000-0002-3410-4655",
                  {"type": ["literal"], "value": "Hypertension"}
                ]
                """);

        JsonElement expected = json("""
                ["Diabetes", "https://orcid.org/0000-0002-3410-4655", "Hypertension"]
                """);

        assertEquals(expected, RemoveLiteralDatatypesTransform.transform(input));
    }

    @Test
    void nestedArrayOfArraysRecursesAtEveryLevel() {
        JsonElement input = json("""
                [
                  [1, 2],
                  [3, {"type": ["literal"], "value": "x"}]
                ]
                """);

        JsonElement expected = json("""
                [
                  [1, 2],
                  [3, "x"]
                ]
                """);

        assertEquals(expected, RemoveLiteralDatatypesTransform.transform(input));
    }

    // --- Objects: no "type" key at all --------------------------------------------------------

    @Test
    void objectWithNoTypeKeyRecursesIntoItsOwnEntriesUnchangedInStructure() {
        JsonElement input = json("""
                {
                  "a": {"type": ["literal"], "value": "Diabetes"},
                  "b": "hello",
                  "c": 7
                }
                """);

        JsonElement expected = json("""
                {
                  "a": "Diabetes",
                  "b": "hello",
                  "c": 7
                }
                """);

        assertEquals(expected, RemoveLiteralDatatypesTransform.transform(input));
    }

    // --- Objects: "type" present but not the literal-collapse shape --------------------------

    @Test
    void objectWithNonArrayTypeFallsThroughToGenericObjectRecursion() {
        // "type" is present but is a plain string, not a JsonArray -- type.isJsonArray() is false,
        // so the literal-collapse branch (lines 32-39) is never entered and control falls straight
        // to the generic JsonCollectionHelper.map(obj, ...) recursion at the bottom of the method.
        JsonElement input = json("""
                {
                  "type": "class",
                  "value": {"type": ["literal"], "value": "Diabetes"}
                }
                """);

        JsonElement expected = json("""
                {
                  "type": "class",
                  "value": "Diabetes"
                }
                """);

        assertEquals(expected, RemoveLiteralDatatypesTransform.transform(input));
    }

    @Test
    void objectWithTypeArrayNotContainingLiteralFallsThroughToGenericObjectRecursion() {
        // "type" is a JsonArray, but types.contains(new JsonPrimitive("literal")) is false, so the
        // collapse (obj.get("value")) never happens -- the object structure (including the "type"
        // array itself) is preserved, with every value independently recursed into.
        JsonElement input = json("""
                {
                  "type": ["class"],
                  "value": {"type": ["literal"], "value": "Diabetes"}
                }
                """);

        JsonElement expected = json("""
                {
                  "type": ["class"],
                  "value": "Diabetes"
                }
                """);

        assertEquals(expected, RemoveLiteralDatatypesTransform.transform(input));
    }

    // --- Objects: the literal-collapse shape itself -------------------------------------------

    @Test
    void literalObjectWithPlainStringValueCollapsesToTheBareValue() {
        JsonElement input = json("""
                {"type": ["literal"], "value": "Diabetes"}
                """);

        assertEquals(new JsonPrimitive("Diabetes"), RemoveLiteralDatatypesTransform.transform(input));
    }

    @Test
    void literalObjectWithExtraKeysStillCollapsesToJustTheValue() {
        // Datatype/lang metadata alongside "type"/"value" (the real shape rdf2json's
        // PropertyValueLiteral produces, per JooqSupport/OlsPostgresClient JSON fixtures) is
        // discarded entirely -- only obj.get("value") is returned, recursively transformed.
        JsonElement input = json("""
                {
                  "datatype": "http://www.w3.org/2001/XMLSchema#string",
                  "lang": "en",
                  "type": ["literal"],
                  "value": "Diabetes"
                }
                """);

        assertEquals(new JsonPrimitive("Diabetes"), RemoveLiteralDatatypesTransform.transform(input));
    }

    @Test
    void nestedLiteralValueIsFullyUnwrappedRecursively() {
        // A literal object whose own "value" is itself another literal-shaped object -- proves
        // transform(obj.get("value")) recurses rather than returning the inner object as-is.
        JsonElement input = json("""
                {
                  "type": ["literal"],
                  "value": {"type": ["literal"], "value": "Diabetes"}
                }
                """);

        assertEquals(new JsonPrimitive("Diabetes"), RemoveLiteralDatatypesTransform.transform(input));
    }

    // --- The genuinely-no-"value"-key edge case, investigated empirically --------------------

    @Test
    void literalObjectWithNoValueKeyThrowsNullPointerException() {
        // obj.get("value") returns a raw Java null (not Gson's JsonNull) when the "value" member is
        // absent entirely, and transform(null) unconditionally calls object.isJsonArray() on that
        // null reference. This test empirically confirms the resulting NullPointerException.
        //
        // Investigated and confirmed NOT reachable from this codebase's own rdf2json/dataload
        // pipeline: PropertyValueLiteral (dataload/rdf2json/.../properties/PropertyValueLiteral.java)
        // always assigns its "value" field in its constructor from Jena's node.getLiteralLexicalForm(),
        // which per the RDF/Jena contract never returns null for a literal node -- there is no
        // production code path that constructs a literal PropertyValue with a missing value. This
        // was cross-checked against every committed golden fixture: a Python scan of all 7,932 JSON
        // files under testcases_expected_output_api/ and all 444 files under
        // testcases_expected_output/ found zero objects shaped {"type": ["literal"], ...} without a
        // "value" key. Since it cannot occur via this codebase's own pipeline, this is documented as
        // a confirmed non-issue rather than escalated as a separate defect PR (see this repo's
        // defect workflow in docs/backend-testing-strategy.md).
        JsonElement input = json("""
                {"type": ["literal"]}
                """);

        assertThrows(NullPointerException.class, () -> RemoveLiteralDatatypesTransform.transform(input));
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }
}
