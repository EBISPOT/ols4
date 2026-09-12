package uk.ac.ebi.spot.ols.repository.transforms;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolveReferencesTransformTest {

    @Test
    void outermostLinkedEntitiesMapWinsOverANestedObjectsOwnLinkedEntitiesMap() {
        // The nested object below carries its own "linkedEntities" map with a *different*
        // definition of the very same key ("http://example.org/A"). Per the once-only capture
        // rule (linkedEntities == null && obj.has("linkedEntities")), the outer map is what wins,
        // and the nested map is never consulted as a resolution scope anywhere in its own
        // subtree - it is only ever copied through verbatim via the pass-through-keys branch.
        JsonElement doc = json("""
                {
                  "linkedEntities": {
                    "http://example.org/A": {"label": ["Outer A"]}
                  },
                  "nested": {
                    "linkedEntities": {
                      "http://example.org/A": {"label": ["Inner A - should never be used"]}
                    },
                    "ref": "http://example.org/A"
                  }
                }
                """);

        JsonElement expected = json("""
                {
                  "linkedEntities": {
                    "http://example.org/A": {"label": ["Outer A"], "iri": "http://example.org/A"}
                  },
                  "nested": {
                    "linkedEntities": {
                      "http://example.org/A": {"label": ["Inner A - should never be used"]}
                    },
                    "ref": {"label": ["Outer A"], "iri": "http://example.org/A"}
                  }
                }
                """);

        JsonElement result = ResolveReferencesTransform.transform(doc);

        assertEquals(expected, result);
        // The inner map's own entry must not have been touched (no "iri" added) - proof it was
        // never consulted, only copied through unchanged.
        assertFalse(result.getAsJsonObject().getAsJsonObject("nested").getAsJsonObject("linkedEntities")
                .getAsJsonObject("http://example.org/A").has("iri"));
    }

    @Test
    void passThroughKeysAreCopiedVerbatimAndNeverResolvedEvenWhenTheyMatchALinkedEntitiesKey() {
        // The same IRI string appears as the value of "iri", "curie", and "shortForm" (all
        // pass-through keys) as well as under an ordinary key "other". Only "other" - which is
        // not one of the four special keys - gets resolved against linkedEntities.
        JsonElement doc = json("""
                {
                  "linkedEntities": {
                    "http://example.org/should-not-resolve": {"label": ["Should not appear"]}
                  },
                  "iri": "http://example.org/should-not-resolve",
                  "curie": "http://example.org/should-not-resolve",
                  "shortForm": "http://example.org/should-not-resolve",
                  "other": "http://example.org/should-not-resolve"
                }
                """);

        JsonElement expected = json("""
                {
                  "linkedEntities": {
                    "http://example.org/should-not-resolve": {"label": ["Should not appear"], "iri": "http://example.org/should-not-resolve"}
                  },
                  "iri": "http://example.org/should-not-resolve",
                  "curie": "http://example.org/should-not-resolve",
                  "shortForm": "http://example.org/should-not-resolve",
                  "other": {"label": ["Should not appear"], "iri": "http://example.org/should-not-resolve"}
                }
                """);

        assertEquals(expected, ResolveReferencesTransform.transform(doc));
    }

    @Test
    void resolvedReferenceWithoutExistingIriIsMutatedInPlaceAndTheMutationIsSharedAcrossOccurrences() {
        // "firstRef" and "secondRef" both point at the same linkedEntities key, which starts out
        // with no "iri" field. The first resolution mutates the linked object in place, adding
        // "iri"; because linkedEntities' values are shared by reference (not copied) across the
        // whole traversal, the second occurrence must see the very same, already-mutated object -
        // not a fresh copy without "iri". "thirdRef" points at an entry that already has its own
        // "iri", which must be left untouched.
        JsonElement doc = json("""
                {
                  "linkedEntities": {
                    "http://example.org/no-iri-yet": {"label": ["No IRI Yet"]},
                    "http://example.org/has-iri": {"label": ["Has IRI"], "iri": "http://example.org/already-set-iri-value"}
                  },
                  "firstRef": "http://example.org/no-iri-yet",
                  "secondRef": "http://example.org/no-iri-yet",
                  "thirdRef": "http://example.org/has-iri"
                }
                """);

        JsonElement expected = json("""
                {
                  "linkedEntities": {
                    "http://example.org/no-iri-yet": {"label": ["No IRI Yet"], "iri": "http://example.org/no-iri-yet"},
                    "http://example.org/has-iri": {"label": ["Has IRI"], "iri": "http://example.org/already-set-iri-value"}
                  },
                  "firstRef": {"label": ["No IRI Yet"], "iri": "http://example.org/no-iri-yet"},
                  "secondRef": {"label": ["No IRI Yet"], "iri": "http://example.org/no-iri-yet"},
                  "thirdRef": {"label": ["Has IRI"], "iri": "http://example.org/already-set-iri-value"}
                }
                """);

        JsonObject result = ResolveReferencesTransform.transform(doc).getAsJsonObject();

        assertEquals(expected, result);
        // Not just structurally equal - the exact same JsonObject instance is returned at both
        // call sites, proving the shared-by-reference (not copy-on-resolve) semantics.
        assertSame(result.get("firstRef"), result.get("secondRef"));
        // ...and that shared instance is also the one still sitting inside linkedEntities itself.
        assertSame(result.get("firstRef"),
                result.getAsJsonObject("linkedEntities").get("http://example.org/no-iri-yet"));
    }

    @Test
    void stringNotPresentAsALinkedEntitiesKeyIsReturnedUnchangedWhenAContextIsActive() {
        JsonElement doc = json("""
                {
                  "linkedEntities": {
                    "http://example.org/known": {"label": ["Known"]}
                  },
                  "unknownRef": "http://example.org/unknown"
                }
                """);

        // Nothing in this document ever resolves "known", so it stays untouched too - the whole
        // structure is expected to come back byte-for-byte identical.
        assertEquals(doc, ResolveReferencesTransform.transform(doc));
    }

    @Test
    void publicTransformEntryPointWithNoLinkedEntitiesAnywhereResolvesNothingInTheDocument() {
        // The top-level object has no "linkedEntities" key of its own (and nothing nested does
        // either), so linkedEntities stays null for the entire traversal and every string, however
        // IRI-shaped, is returned unchanged. Also covers non-string primitives and JsonNull passing
        // through unchanged with no active context.
        JsonElement doc = json("""
                {
                  "iri": "http://example.org/thing",
                  "directParent": ["http://example.org/parent-iri"],
                  "label": ["A label"],
                  "isObsolete": false,
                  "numDescendants": 3,
                  "definition": null
                }
                """);

        assertEquals(doc, ResolveReferencesTransform.transform(doc));
    }

    @Test
    void nonStringPrimitivesAndJsonNullAreReturnedAsIsEvenWithAnActiveLinkedEntitiesContext() {
        JsonElement doc = json("""
                {
                  "linkedEntities": {
                    "http://example.org/x": {"label": ["X"]}
                  },
                  "aBoolean": true,
                  "aNumber": 42,
                  "aNull": null
                }
                """);

        // None of these values are strings, so none of them ever reach the linkedEntities lookup -
        // and since "x" is never referenced anywhere, linkedEntities itself is untouched too. The
        // whole document should come back unchanged.
        assertEquals(doc, ResolveReferencesTransform.transform(doc));
    }

    @Test
    void arrayElementsAreEachRecursedWithTheSameThreadedLinkedEntitiesContext() {
        JsonElement doc = json("""
                {
                  "linkedEntities": {
                    "http://example.org/array-ref": {"label": ["Array Ref"]}
                  },
                  "mixedArray": [
                    "http://example.org/array-ref",
                    "http://example.org/not-linked",
                    {"nestedRef": "http://example.org/array-ref"}
                  ]
                }
                """);

        JsonElement expected = json("""
                {
                  "linkedEntities": {
                    "http://example.org/array-ref": {"label": ["Array Ref"], "iri": "http://example.org/array-ref"}
                  },
                  "mixedArray": [
                    {"label": ["Array Ref"], "iri": "http://example.org/array-ref"},
                    "http://example.org/not-linked",
                    {"nestedRef": {"label": ["Array Ref"], "iri": "http://example.org/array-ref"}}
                  ]
                }
                """);

        assertEquals(expected, ResolveReferencesTransform.transform(doc));
    }

    @Test
    void linkedEntitiesContextCapturedInsideOneArrayElementDoesNotLeakToSiblingElements() {
        // The array itself never captures a linkedEntities context (only the object branch does).
        // The first element establishes its own context purely for its own recursive call; that
        // Java-local variable does not propagate back out to the array's own transformWithLinkedEntities
        // call, so the second, sibling element - a bare string equal to a key defined only inside
        // the first element - is not resolved.
        JsonElement topArray = json("""
                [
                  {
                    "linkedEntities": {
                      "http://example.org/y": {"label": ["Y"]}
                    },
                    "ref": "http://example.org/y"
                  },
                  "http://example.org/y"
                ]
                """);

        JsonElement expected = json("""
                [
                  {
                    "linkedEntities": {
                      "http://example.org/y": {"label": ["Y"], "iri": "http://example.org/y"}
                    },
                    "ref": {"label": ["Y"], "iri": "http://example.org/y"}
                  },
                  "http://example.org/y"
                ]
                """);

        assertEquals(expected, ResolveReferencesTransform.transform(topArray));
    }

    @Test
    void realisticEntityJsonShapeResolvesDirectParentButLeavesIriCurieShortFormAlone() {
        // Grounded in the actual production shape (see the class's own docstring, and the
        // "json" sub-object of backend/src/test/resources/fixtures/classes/class-fixture.json):
        // an entity's own "iri"/"curie"/"shortForm" sit alongside a "linkedEntities" map and a
        // "directParent" array of IRI strings that are meant to be resolved inline.
        JsonElement doc = json("""
                {
                  "iri": "http://www.ebi.ac.uk/efo/EFO_0001642",
                  "curie": "EFO:0001642",
                  "shortForm": "EFO_0001642",
                  "label": ["epithelial cell line"],
                  "directParent": ["http://www.ebi.ac.uk/efo/EFO_0001641"],
                  "linkedEntities": {
                    "http://www.ebi.ac.uk/efo/EFO_0001641": {
                      "label": ["epithelial cell derived cell line"]
                    }
                  }
                }
                """);

        JsonElement expected = json("""
                {
                  "iri": "http://www.ebi.ac.uk/efo/EFO_0001642",
                  "curie": "EFO:0001642",
                  "shortForm": "EFO_0001642",
                  "label": ["epithelial cell line"],
                  "directParent": [
                    {
                      "label": ["epithelial cell derived cell line"],
                      "iri": "http://www.ebi.ac.uk/efo/EFO_0001641"
                    }
                  ],
                  "linkedEntities": {
                    "http://www.ebi.ac.uk/efo/EFO_0001641": {
                      "label": ["epithelial cell derived cell line"],
                      "iri": "http://www.ebi.ac.uk/efo/EFO_0001641"
                    }
                  }
                }
                """);

        assertEquals(expected, ResolveReferencesTransform.transform(doc));
    }

    /**
     * The object-recursion loop in {@code transformWithLinkedEntities} has an
     * {@code if (res != null) ... else newObj.add(entry.getKey(), entry.getValue())} fallback that
     * looks like defensive dead code: every branch of {@code transformWithLinkedEntities} returns a
     * non-null {@link JsonElement} (the array branch always returns a {@code JsonArray} built by
     * {@code JsonCollectionHelper.map}, the object branch always returns its {@code newObj}, the
     * string-match branch returns either {@code linked} or the input {@code object}, and the final
     * {@code else} returns the input {@code object} itself) - so {@code res} can never actually be
     * {@code null}. That conclusion depends on {@code entry.getValue()} (the values iterated by
     * {@code JsonObject.entrySet()}) never being a raw Java {@code null} either. This test confirms
     * that premise empirically against this project's actual Gson version: {@link JsonObject#add}
     * normalizes a {@code null} argument to {@link JsonNull#INSTANCE}, so a {@code JsonObject} can
     * never hand back a raw {@code null} from {@code entrySet()} - meaning the {@code else} fallback
     * in {@code ResolveReferencesTransform} is permanently unreachable, not a real behavior.
     */
    @Test
    void gsonJsonObjectNeverStoresRawNullSoTheDeadNullCheckFallbackIsUnreachable() {
        JsonObject obj = new JsonObject();

        obj.add("key", null);

        JsonElement stored = obj.get("key");

        assertTrue(stored != null && stored.isJsonNull());
        for (var entry : obj.entrySet()) {
            assertTrue(entry.getValue() != null);
        }
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }
}
