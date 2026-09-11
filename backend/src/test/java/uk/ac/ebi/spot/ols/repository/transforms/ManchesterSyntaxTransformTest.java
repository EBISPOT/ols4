package uk.ac.ebi.spot.ols.repository.transforms;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Direct unit coverage for {@link ManchesterSyntaxTransform}, a pure static-method utility with
 * no Spring bean and no Postgres dependency. All cases drive the single public entry point,
 * {@code transform(JsonElement)}, since that is the only non-private surface and mirrors this
 * repo's existing idiom for these transform classes (see {@link LocalizationTransformTest}).
 * Driving {@code isClassExpressionObject}/{@code isNamedEntityObject}/{@code toManchester}
 * indirectly this way still lets every one of their branches be asserted precisely: whether an
 * input object collapses to a single Manchester-syntax string (those private methods returned
 * true/true-not-named) or is left structurally intact and recursed into (false, or named-entity
 * override) is directly observable in the shape of {@code transform}'s return value.
 *
 * <p>Fixture shapes are grounded in real rdf2json output found in this repo's committed
 * {@code testcases_expected_output/owl2-primer/*}/ontologies_linked.json golden files (located via
 * {@code graphify query} for production callers of this class through {@code JsonTransformer}),
 * not invented from scratch — e.g. the intersectionOf/someValuesFrom/qualifiedCardinality/
 * datatype-minmax/hasSelf examples below reuse the exact predicate/value shapes those fixtures
 * contain.
 */
class ManchesterSyntaxTransformTest {

    // ==== transform(JsonElement) — top-level dispatch ====

    @Test
    void nullInputReturnsJsonNull() {
        assertEquals(JsonNull.INSTANCE, ManchesterSyntaxTransform.transform(null));
    }

    @Test
    void jsonNullInputReturnsJsonNull() {
        assertEquals(JsonNull.INSTANCE, ManchesterSyntaxTransform.transform(JsonNull.INSTANCE));
    }

    @Test
    void arrayInputRecursesIntoEachElementViaJsonCollectionHelper() {
        JsonElement input = json("""
                [
                  {"http://www.w3.org/2002/07/owl#complementOf": "http://example.org#Foo"},
                  "http://example.org#Bar",
                  7
                ]
                """);

        JsonElement expected = json("""
                [
                  "not http://example.org#Foo",
                  "http://example.org#Bar",
                  7
                ]
                """);

        assertEquals(expected, ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void objectThatIsNotAClassExpressionRecursesIntoItsValues() {
        JsonElement input = json("""
                {
                  "label": "Foo",
                  "restriction": {"http://www.w3.org/2002/07/owl#complementOf": "http://example.org#Bar"}
                }
                """);

        JsonElement expected = json("""
                {
                  "label": "Foo",
                  "restriction": "not http://example.org#Bar"
                }
                """);

        assertEquals(expected, ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void namedEntityCarryingAnAxiomKeyRecursesInsteadOfCollapsing() {
        // Precedence: named entities are preserved even when they carry OWL axiom fields like
        // owl:inverseOf, per the code's own comment. The outer object must survive intact (its
        // "iri" field stays present) rather than collapsing to a single Manchester string, while
        // the nested (non-named-entity) axiom value still collapses normally.
        JsonElement input = json("""
                {
                  "iri": "http://example.org#superProperty",
                  "http://www.w3.org/2002/07/owl#inverseOf": {
                    "http://www.w3.org/2002/07/owl#complementOf": "http://example.org#Blocked"
                  }
                }
                """);

        JsonElement expected = json("""
                {
                  "iri": "http://example.org#superProperty",
                  "http://www.w3.org/2002/07/owl#inverseOf": "not http://example.org#Blocked"
                }
                """);

        assertEquals(expected, ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void primitivesAndBooleansAreReturnedAsIs() {
        assertEquals(new JsonPrimitive("http://example.org#Foo"),
                ManchesterSyntaxTransform.transform(new JsonPrimitive("http://example.org#Foo")));
        assertEquals(new JsonPrimitive(42),
                ManchesterSyntaxTransform.transform(new JsonPrimitive(42)));
        assertEquals(new JsonPrimitive(true),
                ManchesterSyntaxTransform.transform(new JsonPrimitive(true)));
    }

    // ==== isClassExpressionObject — negative cases and the datatype+equivalentClass special case ====

    @Test
    void objectWithNoMatchingKeysAndNoTypeIsNotAClassExpression() {
        JsonElement input = json("""
                {"foo": "bar", "baz": 1}
                """);

        assertEquals(input, ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void datatypeTypeAloneWithoutEquivalentClassIsNotAClassExpression() {
        JsonElement input = json("""
                {"type": ["datatype"], "label": "Score"}
                """);

        assertEquals(input, ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void equivalentClassAloneWithoutDatatypeTypeIsNotAClassExpression() {
        JsonElement input = json("""
                {
                  "type": ["class"],
                  "http://www.w3.org/2002/07/owl#equivalentClass": "http://example.org#Foo"
                }
                """);

        assertEquals(input, ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void datatypeTypeWithEquivalentClassIsAClassExpressionAndCombinesTheFacets() {
        // Grounded in testcases_expected_output/owl2-primer/datatype-minmax: onDatatype=xsd:integer
        // with withRestrictions [{minInclusive:0}, {maxInclusive:150}], raw JSON numbers (not
        // pre-stringified) — exercises normalizeNumberToString and the combined-facets join.
        JsonElement input = json("""
                {
                  "type": ["datatype"],
                  "http://www.w3.org/2002/07/owl#equivalentClass": {
                    "http://www.w3.org/2002/07/owl#onDatatype": "http://www.w3.org/2001/XMLSchema#integer",
                    "http://www.w3.org/2002/07/owl#withRestrictions": [
                      {"http://www.w3.org/2001/XMLSchema#minInclusive": 0},
                      {"http://www.w3.org/2001/XMLSchema#maxInclusive": 150}
                    ]
                  }
                }
                """);

        assertEquals(
                new JsonPrimitive("http://www.w3.org/2001/XMLSchema#integer [≥ 0, ≤ 150]"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void datatypeEquivalentClassCase0LabelPrefixTakesPriorityOverGeneralLabelShortcut() {
        // The outer object's own "label" is read directly here (case 0's own handling, no quotes,
        // trailing space) rather than falling into the later generic `obj.has("label")` shortcut
        // (which would instead quote the label and ignore the datatype entirely) — case 0 is
        // checked first in toManchester. Empty withRestrictions also proves the bare-IRI fallback.
        JsonElement input = json("""
                {
                  "type": ["datatype"],
                  "label": "score-range",
                  "http://www.w3.org/2002/07/owl#equivalentClass": {
                    "http://www.w3.org/2002/07/owl#onDatatype": "http://www.w3.org/2001/XMLSchema#integer",
                    "http://www.w3.org/2002/07/owl#withRestrictions": []
                  }
                }
                """);

        assertEquals(
                new JsonPrimitive("score-range http://www.w3.org/2001/XMLSchema#integer"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void generalLabelShortcutTakesPrecedenceOverIntersectionOf() {
        // A resolved/referenced entity (post ResolveReferencesTransform) carrying both a "label"
        // and owl:intersectionOf must render as the quoted label only — the intersectionOf array
        // must never be touched. This is the "label wins" precedence the task explicitly calls out,
        // grounded in the linkedEntities-carries-a-label shape seen in the real fixtures.
        JsonElement input = json("""
                {
                  "label": "Parent",
                  "http://www.w3.org/2002/07/owl#intersectionOf": [
                    "http://example.org#Man",
                    "http://example.org#Parent"
                  ]
                }
                """);

        assertEquals(new JsonPrimitive("'Parent'"), ManchesterSyntaxTransform.transform(input));
    }

    // ==== owl:Class expressions: intersectionOf / unionOf / complementOf / oneOf / inverseOf ====

    @Test
    void intersectionOfTwoElementsProducesExactParenthesizedAndJoinedFormat() {
        // Matches testcases_expected_output/owl2-primer/subClassOf-intersectionOf exactly:
        // Grandfather = Man and Parent.
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#intersectionOf": [
                    "http://example.org#Man",
                    "http://example.org#Parent"
                  ]
                }
                """);

        assertEquals(
                new JsonPrimitive("(http://example.org#Man and http://example.org#Parent)"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void unionOfThreeElementsProducesExactParenthesizedAndJoinedFormat() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#unionOf": [
                    "http://example.org#Cat",
                    "http://example.org#Dog",
                    "http://example.org#Bird"
                  ]
                }
                """);

        assertEquals(
                new JsonPrimitive(
                        "(http://example.org#Cat or http://example.org#Dog or http://example.org#Bird)"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void intersectionOfAsBareNonArrayValueIsTreatedAsOneElementList() {
        // asList()'s non-array branch — a to-many property collapsed to a bare value, a shape
        // rdf2json/JSON-LD can plausibly produce for a single-member list.
        JsonElement input = json("""
                {"http://www.w3.org/2002/07/owl#intersectionOf": "http://example.org#Man"}
                """);

        assertEquals(new JsonPrimitive("(http://example.org#Man)"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void complementOfProducesNotPrefix() {
        JsonElement input = json("""
                {"http://www.w3.org/2002/07/owl#complementOf": "http://example.org#Sick"}
                """);

        assertEquals(new JsonPrimitive("not http://example.org#Sick"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void oneOfBraceJoinsMembersAndNormalizesNumericMembersToStrings() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#oneOf": [
                    "http://example.org#Monday",
                    3
                  ]
                }
                """);

        assertEquals(new JsonPrimitive("{http://example.org#Monday, 3}"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void inverseOfProducesInverseWrapper() {
        JsonElement input = json("""
                {"http://www.w3.org/2002/07/owl#inverseOf": "http://example.org#hasChild"}
                """);

        assertEquals(new JsonPrimitive("inverse(http://example.org#hasChild)"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void nullMemberInsideIntersectionOfRendersTheBottomSymbol() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#intersectionOf": [
                    null,
                    "http://example.org#Concept"
                  ]
                }
                """);

        assertEquals(new JsonPrimitive("(⊥ and http://example.org#Concept)"),
                ManchesterSyntaxTransform.transform(input));
    }

    // ==== Datatype restrictions: owl:onDatatype / owl:withRestrictions ====

    @Test
    void datatypeRestrictionCombinesMinExclusiveAndMaxExclusiveInOneRestrictionObject() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onDatatype": "http://www.w3.org/2001/XMLSchema#decimal",
                  "http://www.w3.org/2002/07/owl#withRestrictions": [
                    {
                      "http://www.w3.org/2001/XMLSchema#minExclusive": 0,
                      "http://www.w3.org/2001/XMLSchema#maxExclusive": 10
                    }
                  ]
                }
                """);

        assertEquals(
                new JsonPrimitive("http://www.w3.org/2001/XMLSchema#decimal [> 0, < 10]"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void datatypeRestrictionWithRestrictionsKeyEntirelyAbsentFallsBackToBareIri() {
        JsonElement input = json("""
                {"http://www.w3.org/2002/07/owl#onDatatype": "http://www.w3.org/2001/XMLSchema#string"}
                """);

        assertEquals(new JsonPrimitive("http://www.w3.org/2001/XMLSchema#string"),
                ManchesterSyntaxTransform.transform(input));
    }

    // ==== Unqualified property restrictions: gated on owl:onProperty ====

    @Test
    void noOnPropertyFallsThroughToUnknownClassExpressionEvenWithAnotherRestrictionKeyPresent() {
        // isJsonBoolean is a permanently-false stub (per its own comment), so this branch is
        // effectively unconditional whenever onProperty is absent: confirmed empirically here
        // with minCardinality present but ignored entirely.
        JsonElement input = json("""
                {"http://www.w3.org/2002/07/owl#minCardinality": 1}
                """);

        assertEquals(new JsonPrimitive("unknown class expression"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void onPropertyAloneWithNoOtherMatchingKeyFallsThroughToUnknownClassExpression() {
        JsonElement input = json("""
                {"http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild"}
                """);

        assertEquals(new JsonPrimitive("unknown class expression"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void someValuesFromProducesSomeKeyword() {
        // Matches testcases_expected_output/owl2-primer/equivalent-propertyRestriction-someValuesFrom:
        // onProperty=hasChild, someValuesFrom=Person.
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#someValuesFrom": "http://example.org#Person"
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#hasChild some http://example.org#Person"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void allValuesFromProducesOnlyKeyword() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#allValuesFrom": "http://example.org#Doctor"
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#hasChild only http://example.org#Doctor"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void hasValueWithNumericValueProducesValueKeywordAndNormalizesTheNumber() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasAge",
                  "http://www.w3.org/2002/07/owl#hasValue": 21
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#hasAge value 21"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void hasValueWithBooleanValueUsesStringValueOfDispatch() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#isActive",
                  "http://www.w3.org/2002/07/owl#hasValue": true
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#isActive value true"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void minCardinalityProducesMinKeyword() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#minCardinality": 2
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#hasChild min 2"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void maxCardinalityProducesMaxKeyword() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#maxCardinality": 4
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#hasChild max 4"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void cardinalityProducesExactlyKeyword() {
        // Matches testcases_expected_output/owl2-primer/unqualified-cardinality-exact-restriction.
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#cardinality": 3
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#hasChild exactly 3"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void hasSelfWhenTruthyProducesSelfSuffix() {
        // Matches testcases_expected_output/owl2-primer/self-restriction: hasSelf=true, onProperty=loves.
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#loves",
                  "http://www.w3.org/2002/07/owl#hasSelf": true
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#loves Self"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void hasSelfWhenFalseDoesNotTriggerSelfSuffixAndFallsThroughToUnknown() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#loves",
                  "http://www.w3.org/2002/07/owl#hasSelf": false
                }
                """);

        assertEquals(new JsonPrimitive("unknown class expression"),
                ManchesterSyntaxTransform.transform(input));
    }

    // Precedence among the unqualified-restriction branches: each is a separate early-return, in
    // this exact order (someValuesFrom, allValuesFrom, hasValue, minCardinality, maxCardinality,
    // cardinality, hasSelf) — each test below gives two adjacent keys and asserts only the
    // first-checked one fires, spot-checking the whole chain link by link.

    @Test
    void someValuesFromTakesPrecedenceOverAllValuesFrom() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#someValuesFrom": "http://example.org#Person",
                  "http://www.w3.org/2002/07/owl#allValuesFrom": "http://example.org#Doctor"
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#hasChild some http://example.org#Person"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void allValuesFromTakesPrecedenceOverHasValue() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#allValuesFrom": "http://example.org#Doctor",
                  "http://www.w3.org/2002/07/owl#hasValue": "http://example.org#Bob"
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#hasChild only http://example.org#Doctor"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void hasValueTakesPrecedenceOverMinCardinality() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#hasValue": "http://example.org#Bob",
                  "http://www.w3.org/2002/07/owl#minCardinality": 1
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#hasChild value http://example.org#Bob"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void minCardinalityTakesPrecedenceOverMaxCardinality() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#minCardinality": 1,
                  "http://www.w3.org/2002/07/owl#maxCardinality": 5
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#hasChild min 1"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void maxCardinalityTakesPrecedenceOverCardinality() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#maxCardinality": 3,
                  "http://www.w3.org/2002/07/owl#cardinality": 7
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#hasChild max 3"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void cardinalityTakesPrecedenceOverHasSelf() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#loves",
                  "http://www.w3.org/2002/07/owl#cardinality": 1,
                  "http://www.w3.org/2002/07/owl#hasSelf": true
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#loves exactly 1"),
                ManchesterSyntaxTransform.transform(input));
    }

    // ==== Qualified cardinalities: gated on owl:onClass ====

    @Test
    void minQualifiedCardinalityProducesMinKeywordWithOnClassSuffix() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#onClass": "http://example.org#Parent",
                  "http://www.w3.org/2002/07/owl#minQualifiedCardinality": 2
                }
                """);

        assertEquals(
                new JsonPrimitive("http://example.org#hasChild min 2 http://example.org#Parent"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void maxQualifiedCardinalityProducesMaxKeywordWithOnClassSuffix() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#onClass": "http://example.org#Parent",
                  "http://www.w3.org/2002/07/owl#maxQualifiedCardinality": 5
                }
                """);

        assertEquals(
                new JsonPrimitive("http://example.org#hasChild max 5 http://example.org#Parent"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void qualifiedCardinalityProducesExactlyKeywordWithOnClassSuffix() {
        // Matches testcases_expected_output/owl2-primer/propertyRestriction-qualifiedCardinality
        // exactly: onProperty=hasChild, onClass=Parent, qualifiedCardinality=2.
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#onClass": "http://example.org#Parent",
                  "http://www.w3.org/2002/07/owl#qualifiedCardinality": 2
                }
                """);

        assertEquals(
                new JsonPrimitive("http://example.org#hasChild exactly 2 http://example.org#Parent"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void minQualifiedCardinalityTakesPrecedenceOverMaxAndExactQualifiedCardinality() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#onClass": "http://example.org#Parent",
                  "http://www.w3.org/2002/07/owl#minQualifiedCardinality": 1,
                  "http://www.w3.org/2002/07/owl#maxQualifiedCardinality": 3,
                  "http://www.w3.org/2002/07/owl#qualifiedCardinality": 2
                }
                """);

        assertEquals(
                new JsonPrimitive("http://example.org#hasChild min 1 http://example.org#Parent"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void onClassPresentButNoQualifiedCardinalityKeyFallsThroughToUnknownClassExpression() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#onClass": "http://example.org#Parent"
                }
                """);

        assertEquals(new JsonPrimitive("unknown class expression"),
                ManchesterSyntaxTransform.transform(input));
    }

    // ==== isNamedEntityObject — direct fields ====

    @Test
    void namedEntityIriFieldPreventsCollapse() {
        JsonElement input = json("""
                {
                  "iri": "http://example.org#Thing",
                  "http://www.w3.org/2002/07/owl#complementOf": "http://example.org#Sick"
                }
                """);

        assertEquals(input, ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void namedEntityOntologyIdFieldPreventsCollapse() {
        JsonElement input = json("""
                {
                  "ontologyId": "go",
                  "http://www.w3.org/2002/07/owl#complementOf": "http://example.org#Sick"
                }
                """);

        assertEquals(input, ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void namedEntityOntologyIriFieldPreventsCollapse() {
        JsonElement input = json("""
                {
                  "ontologyIri": "http://purl.obolibrary.org/obo/go.owl",
                  "http://www.w3.org/2002/07/owl#complementOf": "http://example.org#Sick"
                }
                """);

        assertEquals(input, ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void namedEntityCurieFieldPreventsCollapse() {
        JsonElement input = json("""
                {
                  "curie": {"type": ["literal"], "value": "Parent"},
                  "http://www.w3.org/2002/07/owl#complementOf": "http://example.org#Sick"
                }
                """);

        assertEquals(input, ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void namedEntityShortFormFieldPreventsCollapse() {
        JsonElement input = json("""
                {
                  "shortForm": "GO_0008150",
                  "http://www.w3.org/2002/07/owl#complementOf": "http://example.org#Sick"
                }
                """);

        assertEquals(input, ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void namedEntityTypeArrayContainingClassPreventsCollapse() {
        JsonElement input = json("""
                {
                  "type": ["class"],
                  "http://www.w3.org/2002/07/owl#complementOf": "http://example.org#Sick"
                }
                """);

        assertEquals(input, ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void namedEntityTypeArrayContainingObjectPropertyPreventsCollapse() {
        JsonElement input = json("""
                {
                  "type": ["objectProperty"],
                  "http://www.w3.org/2002/07/owl#complementOf": "http://example.org#Sick"
                }
                """);

        assertEquals(input, ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void typeArrayPresentButContainingNoEntityTypeStringFallsThroughToNotNamedEntityAndCollapses() {
        // Not to be confused with the true case above: a "type" array is present, but none of its
        // entries is one of the eight recognised entity-type strings, so isNamedEntityObject must
        // fall through to false and the object collapses like any other anonymous expression.
        JsonElement input = json("""
                {
                  "type": ["literal"],
                  "http://www.w3.org/2002/07/owl#complementOf": "http://example.org#Sick"
                }
                """);

        assertEquals(new JsonPrimitive("not http://example.org#Sick"),
                ManchesterSyntaxTransform.transform(input));
    }

    // ==== Remaining reachable-but-otherwise-untouched branches (identified from an initial JaCoCo
    // pass: toManchester's own top-level array dispatch, safeArray's scalar-wrapping branch, and
    // firstOf's array-unwrapping branch are all genuinely reachable from production call sites
    // that don't pre-unwrap their input, just not through any of the cases above). ====

    @Test
    void equivalentClassAsAnArrayReachesToManchestersOwnArrayDispatchBranch() {
        // Unlike every other call site above, case 0 passes owl:equivalentClass's raw value
        // straight into toManchester without unwrapping it via firstOf/asList first, so if it is
        // itself a JSON array (a multi-valued RDF property, which rdf2json can produce for any
        // property) toManchester's own `el.isJsonArray()` branch — not transform()'s array
        // handling via JsonCollectionHelper — is what renders it.
        JsonElement input = json("""
                {
                  "type": ["datatype"],
                  "http://www.w3.org/2002/07/owl#equivalentClass": [
                    "http://example.org#A",
                    "http://example.org#B"
                  ]
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#A, http://example.org#B"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void bareStringTypeFieldIsWrappedBySafeArrayBeforeTheEntityTypeCheck() {
        // "type" is written as a bare scalar here instead of the single-element array every other
        // test above uses, exercising safeArray's wrap-a-lone-value-into-a-one-element-array
        // branch. isNamedEntityObject still correctly recognises it as an entity type.
        JsonElement input = json("""
                {
                  "type": "class",
                  "http://www.w3.org/2002/07/owl#complementOf": "http://example.org#Sick"
                }
                """);

        assertEquals(input, ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void someValuesFromAsASingleElementArrayIsUnwrappedByFirstOfToTheSameResultAsABareValue() {
        // Every someValuesFrom/hasValue/cardinality/etc. fixture above used a bare scalar value
        // (the shape the real someValuesFrom golden fixture actually has); this proves firstOf's
        // array-unwrapping branch normalizes the array-wrapped shape to an identical result.
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#someValuesFrom": ["http://example.org#Person"]
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#hasChild some http://example.org#Person"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void datatypeTypeWithoutEquivalentClassStillFallsThroughToWhicheverOtherKeyIsPresent() {
        // hasType(obj,"datatype") is true here, but obj.get(equivalentClass) is absent (Java
        // null), so case 0's own `eq != null` guard must skip the block entirely rather than
        // NPE-ing, falling through to the ordinary someValuesFrom handling below it.
        JsonElement input = json("""
                {
                  "type": ["datatype"],
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#someValuesFrom": "http://example.org#Person"
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#hasChild some http://example.org#Person"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void datatypeEquivalentClassWithANonPrimitiveLabelSilentlyIgnoresTheLabel() {
        // Case 0's label handling requires obj.get("label").isJsonPrimitive(); a non-primitive
        // label (the raw, not-yet-localized {"type":["literal"],"value":...} shape) does not
        // satisfy that check, so no prefix is added at all rather than being rendered incorrectly.
        JsonElement input = json("""
                {
                  "type": ["datatype"],
                  "label": {"type": ["literal"], "value": "score-range"},
                  "http://www.w3.org/2002/07/owl#equivalentClass": {
                    "http://www.w3.org/2002/07/owl#onDatatype": "http://www.w3.org/2001/XMLSchema#integer"
                  }
                }
                """);

        assertEquals(new JsonPrimitive("http://www.w3.org/2001/XMLSchema#integer"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void withRestrictionsSkipsNonObjectEntriesInsteadOfFailing() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onDatatype": "http://www.w3.org/2001/XMLSchema#integer",
                  "http://www.w3.org/2002/07/owl#withRestrictions": [
                    "not-an-object",
                    {"http://www.w3.org/2001/XMLSchema#minInclusive": 5}
                  ]
                }
                """);

        assertEquals(new JsonPrimitive("http://www.w3.org/2001/XMLSchema#integer [≥ 5]"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void explicitJsonNullHasSelfDoesNotTriggerSelfSuffixAndFallsThroughToUnknown() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#loves",
                  "http://www.w3.org/2002/07/owl#hasSelf": null
                }
                """);

        assertEquals(new JsonPrimitive("unknown class expression"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void emptyArrayValueForSomeValuesFromIsTreatedAsAbsentAndFallsThroughToUnknown() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#hasChild",
                  "http://www.w3.org/2002/07/owl#someValuesFrom": []
                }
                """);

        assertEquals(new JsonPrimitive("unknown class expression"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void hasSelfAsANonBooleanStringDoesNotTriggerSelfSuffix() {
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#loves",
                  "http://www.w3.org/2002/07/owl#hasSelf": "yes"
                }
                """);

        assertEquals(new JsonPrimitive("unknown class expression"),
                ManchesterSyntaxTransform.transform(input));
    }

    @Test
    void hasValueAsAResolvedNamedIndividualObjectIsNotNumberNormalizedAndRendersItsLabel() {
        // Grounded in testcases_expected_output/owl2-primer/value-restriction-on-individual's
        // shape: hasValue targeting an individual rather than a literal. normalizeNumberToString's
        // isJsonPrimitive() guard leaves the object untouched, and toManchester then renders it via
        // the general label shortcut (a resolved reference, post ResolveReferencesTransform).
        JsonElement input = json("""
                {
                  "http://www.w3.org/2002/07/owl#onProperty": "http://example.org#marriedTo",
                  "http://www.w3.org/2002/07/owl#hasValue": {"iri": "http://example.org#Bob", "label": "Bob"}
                }
                """);

        assertEquals(new JsonPrimitive("http://example.org#marriedTo value 'Bob'"),
                ManchesterSyntaxTransform.transform(input));
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }
}
