package uk.ac.ebi.spot.ols.repository.transforms;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Coverage for {@link RemoveReificationTransform} as the class is <strong>actually written
 * today</strong> — not as its class/method-level docstring implies it behaves.
 *
 * <p><b>Confirmed anomaly (see docs/backend-testing-strategy.md "Implemented
 * RemoveReificationTransform baseline" for the full write-up):</b> the array-recursion branch
 * (the {@code isJsonArray()} case) and the generic-object-recursion branch (the fallthrough when
 * an object has no {@code "type"}, or a {@code "type"} that doesn't contain {@code "reification"})
 * both recurse via {@code RemoveLiteralDatatypesTransform::transform} instead of this class's own
 * {@code RemoveReificationTransform::transform}. Only the third recursive call site — the
 * self-recursive {@code transform(obj.get("value"))} used to collapse a reification wrapper that
 * is itself the direct argument to {@code transform()} — refers to the right class. Several tests
 * below deliberately assert the <em>buggy</em>, currently-shipping behavior (a reification wrapper
 * surviving completely unwrapped when it arrives via an array or as some other key's value) so
 * that a future fix of the two wrong call sites will make this suite fail loudly, rather than
 * silently continuing to pass around a defect.
 *
 * <p>This class has zero production callers (confirmed by repo-wide grep and by the graphify
 * knowledge graph — see the doc baseline section) so none of this is a live defect today; it is
 * dead code that happens to look buggy.
 */
class RemoveReificationTransformTest {

    // --- isJsonArray() branch: recurses via RemoveLiteralDatatypesTransform::transform ---

    @Test
    void arrayRecursionActuallyStripsLiteralWrappersProvingWhichTransformRuns() {
        // A plain "literal" wrapper (no reification involved at all) inside an array. This class
        // has no "literal"-stripping logic of its own -- the only way this collapses to the bare
        // string is if the array branch really is delegating to RemoveLiteralDatatypesTransform,
        // exactly as currently written.
        JsonElement input = json("""
                [
                  {"type": ["literal"], "value": "Diabetes"},
                  "http://example.org/plain-uri"
                ]
                """);

        JsonElement expected = json("""
                [
                  "Diabetes",
                  "http://example.org/plain-uri"
                ]
                """);

        assertEquals(expected, RemoveReificationTransform.transform(input));
    }

    @Test
    void arrayRecursionDoesNotUnwrapReificationBecauseWrongTransformRuns() {
        // The docstring's own worked example, but arriving inside an array the way it always
        // would in a real document (a property value is a JSON array of objects). Because the
        // array branch calls RemoveLiteralDatatypesTransform::transform -- which has no concept
        // of "reification" -- the reification wrapper is NOT collapsed to "MEDDRA:10048222" here.
        // It survives structurally intact; only the literal nested inside its own "value" gets
        // stripped, because RemoveLiteralDatatypesTransform does understand "literal".
        JsonElement input = json("""
                [
                  {
                    "type": ["reification"],
                    "value": {"type": ["literal"], "value": "MEDDRA:10048222"},
                    "axioms": []
                  }
                ]
                """);

        JsonElement expected = json("""
                [
                  {
                    "type": ["reification"],
                    "value": "MEDDRA:10048222",
                    "axioms": []
                  }
                ]
                """);

        assertEquals(expected, RemoveReificationTransform.transform(input));
    }

    // --- direct top-level reification wrapper: the one call site that IS correct ---

    @Test
    void directTopLevelReificationWrapperUnwrapsToItsValue() {
        // Passed directly as the top-level argument (not via an array, not via some other key),
        // the self-recursive transform(obj.get("value")) call site is exercised, and it behaves
        // exactly as the docstring promises: the reification indirection is removed. The literal
        // wrapper nested inside is NOT further collapsed by this class -- that is
        // RemoveLiteralDatatypesTransform's own separate job in the real pipeline -- so it is
        // left as-is here, which is this class's correct, intended contract.
        JsonElement input = json("""
                {
                  "type": ["reification"],
                  "value": {"type": ["literal"], "value": "MEDDRA:10048222"},
                  "axioms": [{"foo": "bar"}]
                }
                """);

        JsonElement expected = json("""
                {"type": ["literal"], "value": "MEDDRA:10048222"}
                """);

        assertEquals(expected, RemoveReificationTransform.transform(input));
    }

    @Test
    void twoLevelsOfDirectReificationNestingFullyUnwrapViaSelfRecursiveChain() {
        // A reification wrapper whose own "value" is itself, directly, another reification
        // wrapper (not via an array or another key). Each level recurses through
        // transform(obj.get("value")) again -- the correct self-recursive call site -- so the
        // chain fully cascades no matter how many direct levels deep it goes.
        JsonElement input = json("""
                {
                  "type": ["reification"],
                  "value": {
                    "type": ["reification"],
                    "value": "innermost",
                    "axioms": []
                  },
                  "axioms": []
                }
                """);

        assertEquals(new JsonPrimitive("innermost"), RemoveReificationTransform.transform(input));
    }

    @Test
    void secondLevelReificationArrivingViaArrayIsNotUnwrappedUnlikeDirectNesting() {
        // Same two-level nesting as above, except the inner reification wrapper arrives inside
        // an array held by the outer wrapper's "value" -- exactly where the cross-delegation bug
        // bites. The outer wrapper is still removed correctly (direct self-recursive call), but
        // once transform() recurses into the array, RemoveLiteralDatatypesTransform::transform
        // runs instead and the inner wrapper survives completely untouched -- zero levels
        // unwrapped, in contrast to the fully-cascading direct-nesting case above.
        JsonElement input = json("""
                {
                  "type": ["reification"],
                  "value": [
                    {"type": ["reification"], "value": "innermost", "axioms": []}
                  ],
                  "axioms": []
                }
                """);

        JsonElement expected = json("""
                [
                  {"type": ["reification"], "value": "innermost", "axioms": []}
                ]
                """);

        assertEquals(expected, RemoveReificationTransform.transform(input));
    }

    @Test
    void reificationArrivingAsAnotherKeysValueIsNotUnwrapped() {
        // The reification wrapper arrives as the value of some unrelated key of a plain object
        // (not the top-level argument, not inside an array). The outer object's own "type" does
        // not contain "reification", so it falls through to the generic (buggy) recursion, which
        // delegates to RemoveLiteralDatatypesTransform::transform for every value -- including
        // this one -- and that transform does not understand "reification" either, so it is left
        // completely intact.
        JsonElement input = json("""
                {
                  "type": ["entity", "property"],
                  "http://www.geneontology.org/formats/oboInOwl#hasDbXref": {
                    "type": ["reification"],
                    "value": "MEDDRA:10048222",
                    "axioms": []
                  }
                }
                """);

        JsonElement expected = json("""
                {
                  "type": ["entity", "property"],
                  "http://www.geneontology.org/formats/oboInOwl#hasDbXref": {
                    "type": ["reification"],
                    "value": "MEDDRA:10048222",
                    "axioms": []
                  }
                }
                """);

        assertEquals(expected, RemoveReificationTransform.transform(input));
    }

    // --- object with "type" present but not containing "reification": generic fallthrough ---

    @Test
    void typePresentButNotReificationStillStripsNestedLiteralsViaDelegation() {
        // Proves the generic fallthrough really does run RemoveLiteralDatatypesTransform logic
        // (not reification logic) on this object's own children: a literal-wrapped value one
        // level below a non-reification "type" gets collapsed, because
        // JsonCollectionHelper.map(obj, ...) applies RemoveLiteralDatatypesTransform::transform
        // directly to each immediate child value.
        JsonElement input = json("""
                {
                  "type": ["class"],
                  "label": {"type": ["literal"], "value": "Foo"}
                }
                """);

        JsonElement expected = json("""
                {
                  "type": ["class"],
                  "label": "Foo"
                }
                """);

        assertEquals(expected, RemoveReificationTransform.transform(input));
    }

    // --- object with no "type" key at all: same generic fallthrough ---

    @Test
    void noTypeKeyAtAllStillStripsNestedLiteralsViaDelegation() {
        JsonElement input = json("""
                {
                  "foo": {"type": ["literal"], "value": "Bar"},
                  "baz": "qux"
                }
                """);

        JsonElement expected = json("""
                {
                  "foo": "Bar",
                  "baz": "qux"
                }
                """);

        assertEquals(expected, RemoveReificationTransform.transform(input));
    }

    // --- second, independent bug: "type" present but not a JSON array ---

    @Test
    void typePresentAsNonArrayPrimitiveThrowsInsteadOfFallingThrough() {
        // Unlike RemoveLiteralDatatypesTransform (which guards with `type.isJsonArray()` before
        // calling getAsJsonArray()), this class calls obj.get("type").getAsJsonArray()
        // unconditionally. A "type" that is a JSON primitive rather than an array throws Gson's
        // IllegalStateException instead of gracefully falling through to the generic recursion.
        JsonElement input = json("""
                {"type": "reification", "value": "x"}
                """);

        assertThrows(IllegalStateException.class, () -> RemoveReificationTransform.transform(input));
    }

    @Test
    void typePresentAsObjectThrowsInsteadOfFallingThrough() {
        JsonElement input = json("""
                {"type": {"nested": "object"}, "value": "x"}
                """);

        assertThrows(IllegalStateException.class, () -> RemoveReificationTransform.transform(input));
    }

    // --- primitives and JsonNull: returned as-is ---

    @Test
    void primitivesAndNullAreReturnedUnchanged() {
        assertEquals(new JsonPrimitive("plain string"),
                RemoveReificationTransform.transform(new JsonPrimitive("plain string")));
        assertEquals(new JsonPrimitive(42),
                RemoveReificationTransform.transform(new JsonPrimitive(42)));
        assertEquals(new JsonPrimitive(true),
                RemoveReificationTransform.transform(new JsonPrimitive(true)));
        assertEquals(JsonNull.INSTANCE,
                RemoveReificationTransform.transform(JsonNull.INSTANCE));
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }
}
