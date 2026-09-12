package uk.ac.ebi.spot.ols.repository.v1.mappers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import uk.ac.ebi.spot.ols.model.v1.V1OboSynonym;
import uk.ac.ebi.spot.ols.model.v1.V1OboXref;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct unit coverage for {@link V1OboSynonymExtractor}. Like {@code AnnotationExtractor} and
 * {@code V1OboDefinitionCitationExtractor}, this is a pure static-method utility class with no
 * Spring bean and no Postgres dependency -- see docs/backend-testing-strategy.md's "Implemented
 * V1OboSynonymExtractor baseline" section for why no *IT layer applies here.
 *
 * <p>Fixture shapes below (the {@code {"type":[...], "value": ...}} wrapper, the
 * {@code "axioms"} array, and the bare-object-with-no-"axioms"-key shape) are modelled directly
 * on real rdf2json linker output, cross-checked against
 * {@code testcases_expected_output/annotation-properties/gitIssue502/ontologies_linked.json}
 * (reified synonyms with single- and multi-valued {@code hasDbXref} axioms, and a matching
 * {@code linkedEntities} entry for xref "NCIT:C2991") and
 * {@code testcases_expected_output/hierarchical-properties/efo/ontologies_linked.json} (a
 * synonym value with no "axioms" key at all).
 */
class V1OboSynonymExtractorTest {

    private static final String HAS_EXACT_SYNONYM = "http://www.geneontology.org/formats/oboInOwl#hasExactSynonym";
    private static final String HAS_RELATED_SYNONYM = "http://www.geneontology.org/formats/oboInOwl#hasRelatedSynonym";
    private static final String HAS_NARROW_SYNONYM = "http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym";
    private static final String HAS_BROAD_SYNONYM = "http://www.geneontology.org/formats/oboInOwl#hasBroadSynonym";
    private static final String HAS_DB_XREF = "http://www.geneontology.org/formats/oboInOwl#hasDbXref";

    // --- fixture builders --------------------------------------------------------------------

    /**
     * Every real caller (only {@code V1TermMapper}) supplies a "linkedEntities" object taken
     * from the same top-level entity JSON -- most fixtures below use this so the "happy path"
     * cases match genuine production shape; the NPE-investigation tests below deliberately omit
     * it instead.
     */
    private static JsonObject baseJson() {
        JsonObject json = new JsonObject();
        json.add("linkedEntities", new JsonObject());
        return json;
    }

    private static JsonObject wrappedLiteral(String value) {
        JsonObject obj = new JsonObject();
        JsonArray type = new JsonArray();
        type.add("literal");
        obj.add("type", type);
        obj.addProperty("value", value);
        return obj;
    }

    /** One axiom, optionally carrying an {@code oboSynonymTypeName} and zero-or-more xrefs. */
    private static JsonObject axiom(String typeName, String... xrefs) {
        JsonObject axiom = new JsonObject();
        if (typeName != null) {
            axiom.addProperty("oboSynonymTypeName", typeName);
        }
        if (xrefs.length == 1) {
            axiom.add(HAS_DB_XREF, wrappedLiteral(xrefs[0]));
        } else if (xrefs.length > 1) {
            JsonArray arr = new JsonArray();
            for (String xref : xrefs) {
                arr.add(wrappedLiteral(xref));
            }
            axiom.add(HAS_DB_XREF, arr);
        }
        return axiom;
    }

    /** A reified synonym value object: {@code {"value": {...}, "axioms": [...]}}. */
    private static JsonObject reifiedSynonym(String name, JsonObject... axioms) {
        JsonObject obj = new JsonObject();
        JsonArray type = new JsonArray();
        type.add("reification");
        obj.add("type", type);
        obj.add("value", wrappedLiteral(name));
        JsonArray axiomsArr = new JsonArray();
        for (JsonObject a : axioms) {
            axiomsArr.add(a);
        }
        obj.add("axioms", axiomsArr);
        return obj;
    }

    /**
     * A synonym value with no "axioms" key at all -- a real, common shape (88 occurrences across
     * the committed fixtures), e.g. a property's own synonym in
     * testcases_expected_output/hierarchical-properties/efo/ontologies_linked.json.
     */
    private static JsonObject synonymWithoutAxioms(String name) {
        return wrappedLiteral(name);
    }

    private static void putSynonyms(JsonObject json, String predicate, JsonElement... values) {
        JsonArray arr = new JsonArray();
        for (JsonElement v : values) {
            arr.add(v);
        }
        json.add(predicate, arr);
    }

    // --- all four scope categories -------------------------------------------------------------

    @Test
    void allFourSynonymScopeCategoriesProduceIndependentlyTaggedEntries() {
        JsonObject json = baseJson();
        putSynonyms(json, HAS_EXACT_SYNONYM, reifiedSynonym("exact one", axiom(null)));
        putSynonyms(json, HAS_RELATED_SYNONYM, reifiedSynonym("related one", axiom(null)));
        putSynonyms(json, HAS_NARROW_SYNONYM, reifiedSynonym("narrow one", axiom(null)));
        putSynonyms(json, HAS_BROAD_SYNONYM, reifiedSynonym("broad one", axiom(null)));

        List<V1OboSynonym> result = V1OboSynonymExtractor.extractFromJson(json);

        assertThat(result).hasSize(4);
        // concatenation order: exact, related, narrow, broad (matches source order)
        assertThat(result.get(0).name).isEqualTo("exact one");
        assertThat(result.get(0).scope).isEqualTo("hasExactSynonym");
        assertThat(result.get(1).name).isEqualTo("related one");
        assertThat(result.get(1).scope).isEqualTo("hasRelatedSynonym");
        assertThat(result.get(2).name).isEqualTo("narrow one");
        assertThat(result.get(2).scope).isEqualTo("hasNarrowSynonym");
        assertThat(result.get(3).name).isEqualTo("broad one");
        assertThat(result.get(3).scope).isEqualTo("hasBroadSynonym");
    }

    // --- fromSynonymObject: plain-primitive shape ----------------------------------------------

    @Test
    void synonymValueAsPlainPrimitiveProducesNoEntries() {
        // "These are ignored in OLS3 for some reason" (see the commented-out dead code in
        // V1OboSynonymExtractor) -- a bare JSON string, not an object, must yield zero entries.
        JsonObject json = baseJson();
        putSynonyms(json, HAS_EXACT_SYNONYM, new JsonPrimitive("bare string synonym"));

        List<V1OboSynonym> result = V1OboSynonymExtractor.extractFromJson(json);

        assertThat(result).isNull();
    }

    @Test
    void primitiveSynonymAlongsideReifiedSynonymOnlyReifiedOneSurvives() {
        JsonObject json = baseJson();
        putSynonyms(json, HAS_EXACT_SYNONYM,
                new JsonPrimitive("bare string synonym"),
                reifiedSynonym("real synonym", axiom(null)));

        List<V1OboSynonym> result = V1OboSynonymExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name).isEqualTo("real synonym");
    }

    // --- fromSynonymObject: missing "axioms" key -----------------------------------------------

    @Test
    void synonymObjectWithNoAxiomsKeyProducesNoEntries() {
        JsonObject json = baseJson();
        putSynonyms(json, HAS_EXACT_SYNONYM, synonymWithoutAxioms("part of"));

        List<V1OboSynonym> result = V1OboSynonymExtractor.extractFromJson(json);

        assertThat(result).isNull();
    }

    // --- fromSynonymObject: one entry per axiom -------------------------------------------------

    @Test
    void synonymObjectWithMultipleAxiomsProducesOneEntryPerAxiomSharingNameAndScope() {
        JsonObject json = baseJson();
        JsonObject axiomWithType = axiom("ABBREVIATION", "NCIT:C001");
        JsonObject axiomWithoutType = axiom(null, "NCIT:C002", "NCIT:C003");
        putSynonyms(json, HAS_EXACT_SYNONYM, reifiedSynonym("shared name", axiomWithType, axiomWithoutType));

        List<V1OboSynonym> result = V1OboSynonymExtractor.extractFromJson(json);

        assertThat(result).hasSize(2);

        V1OboSynonym first = result.get(0);
        assertThat(first.name).isEqualTo("shared name");
        assertThat(first.scope).isEqualTo("hasExactSynonym");
        assertThat(first.type).isEqualTo("ABBREVIATION");
        assertThat(first.xrefs).hasSize(1);
        assertThat(first.xrefs.get(0).id).isEqualTo("C001");

        V1OboSynonym second = result.get(1);
        assertThat(second.name).isEqualTo("shared name");
        assertThat(second.scope).isEqualTo("hasExactSynonym");
        assertThat(second.type).isNull(); // oboSynonymTypeName absent on this axiom
        assertThat(second.xrefs).hasSize(2);
        assertThat(second.xrefs.get(0).id).isEqualTo("C002");
        assertThat(second.xrefs.get(1).id).isEqualTo("C003");
    }

    @Test
    void multipleXrefsOnOneAxiomAreAllResolvedInOrder() {
        JsonObject json = baseJson();
        putSynonyms(json, HAS_EXACT_SYNONYM,
                reifiedSynonym("multi xref synonym", axiom(null, "NCIT:C28193", "OGMS:0000086")));

        List<V1OboSynonym> result = V1OboSynonymExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).xrefs).hasSize(2);
        assertThat(result.get(0).xrefs.get(0).id).isEqualTo("C28193");
        assertThat(result.get(0).xrefs.get(0).database).isEqualTo("NCIT");
        assertThat(result.get(0).xrefs.get(1).id).isEqualTo("0000086");
        assertThat(result.get(0).xrefs.get(1).database).isEqualTo("OGMS");
    }

    // --- xrefs is always a list, never null ------------------------------------------------------

    @Test
    void axiomWithNoMatchingXrefValuesProducesEmptyNotNullXrefsList() {
        JsonObject json = baseJson();
        putSynonyms(json, HAS_EXACT_SYNONYM, reifiedSynonym("no xrefs here", axiom("ABBREVIATION")));

        List<V1OboSynonym> result = V1OboSynonymExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).xrefs).isNotNull();
        assertThat(result.get(0).xrefs).isEmpty();
    }

    // --- investigation: null linkedEntities threaded into V1OboXref.fromString -----------------

    /**
     * {@code extractFromJson} reads "linkedEntities" via the null-safe
     * {@code JsonObject.getAsJsonObject(String)} overload, so a genuinely missing key yields a
     * {@code null} local variable here with no exception at that point (unlike
     * {@code V1OboDefinitionCitationExtractor}'s unguarded {@code json.get(...).getAsJsonObject()},
     * which throws immediately). But that {@code null} is then passed straight into
     * {@code V1OboXref.fromString(oboXref, linkedEntities)} whenever a synonym axiom carries an
     * xref. {@code V1OboXref.fromString} only touches its {@code linkedEntities} parameter in its
     * final branch (a standard "DATABASE:ID"-shaped xref, i.e. {@code tokens.length >= 2} and
     * neither the "http(s):"-prefixed nor the "scheme://"-with-uppercase-prefix special case
     * applies) -- via the unguarded {@code linkedEntities.get(oboXref)} call. A common,
     * realistic xref like "NCIT:C2991" (see
     * testcases_expected_output/annotation-properties/gitIssue502/ontologies_linked.json) hits
     * exactly that branch, so this confirms empirically that a missing "linkedEntities" key
     * *does* cause a live {@link NullPointerException} here, in contrast to a linkedEntities
     * object that is merely empty (graceful, resolves no url) or one that has a matching entry
     * (resolves a url) -- both exercised below for contrast.
     */
    @Test
    void xrefResolutionThrowsNpeWhenLinkedEntitiesKeyIsAbsent() {
        JsonObject json = new JsonObject(); // deliberately no "linkedEntities" key at all
        putSynonyms(json, HAS_EXACT_SYNONYM, reifiedSynonym("condition", axiom(null, "NCIT:C2991")));

        assertThatThrownBy(() -> V1OboSynonymExtractor.extractFromJson(json))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void xrefResolutionSucceedsWhenLinkedEntitiesPresentButHasNoMatchingEntry() {
        JsonObject json = baseJson(); // "linkedEntities" present, but empty
        putSynonyms(json, HAS_EXACT_SYNONYM, reifiedSynonym("condition", axiom(null, "NCIT:C2991")));

        List<V1OboSynonym> result = V1OboSynonymExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        V1OboXref xref = result.get(0).xrefs.get(0);
        assertThat(xref.database).isEqualTo("NCIT");
        assertThat(xref.id).isEqualTo("C2991");
        assertThat(xref.url).isNull();
    }

    @Test
    void xrefUrlIsResolvedWhenLinkedEntitiesContainsMatchingEntry() {
        JsonObject json = baseJson();
        JsonObject ncit = new JsonObject();
        ncit.addProperty("url", "http://purl.obolibrary.org/obo/NCIT_C2991");
        ncit.addProperty("curie", "NCIT:C2991");
        json.getAsJsonObject("linkedEntities").add("NCIT:C2991", ncit);

        putSynonyms(json, HAS_EXACT_SYNONYM, reifiedSynonym("condition", axiom(null, "NCIT:C2991")));

        List<V1OboSynonym> result = V1OboSynonymExtractor.extractFromJson(json);

        assertThat(result.get(0).xrefs.get(0).url).isEqualTo("http://purl.obolibrary.org/obo/NCIT_C2991");
    }

    // --- mergeDuplicates: within one synonym object's own axioms --------------------------------

    @Test
    void identicalAxiomsOnSameSynonymObjectCollapseToOneEntry() {
        JsonObject json = baseJson();
        JsonObject axiomA = axiom("ABBREVIATION", "NCIT:C001");
        JsonObject axiomB = axiom("ABBREVIATION", "NCIT:C001"); // fully identical to axiomA
        putSynonyms(json, HAS_EXACT_SYNONYM, reifiedSynonym("dup name", axiomA, axiomB));

        List<V1OboSynonym> result = V1OboSynonymExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
    }

    @Test
    void identicalAxiomsWithNoXrefsAlsoCollapseToOneEntry() {
        JsonObject json = baseJson();
        JsonObject axiomA = axiom("ABBREVIATION");
        JsonObject axiomB = axiom("ABBREVIATION");
        putSynonyms(json, HAS_EXACT_SYNONYM, reifiedSynonym("dup name", axiomA, axiomB));

        List<V1OboSynonym> result = V1OboSynonymExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).xrefs).isEmpty();
    }

    @Test
    void axiomsDifferingOnlyInTypeSurviveAsDistinctEntries() {
        JsonObject json = baseJson();
        JsonObject axiomA = axiom("ABBREVIATION");
        JsonObject axiomB = axiom("UK_SPELLING");
        putSynonyms(json, HAS_EXACT_SYNONYM, reifiedSynonym("same name", axiomA, axiomB));

        List<V1OboSynonym> result = V1OboSynonymExtractor.extractFromJson(json);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).type).isEqualTo("ABBREVIATION");
        assertThat(result.get(1).type).isEqualTo("UK_SPELLING");
    }

    @Test
    void axiomsDifferingOnlyInXrefsSurviveAsDistinctEntries() {
        JsonObject json = baseJson();
        JsonObject axiomA = axiom("ABBREVIATION", "NCIT:C001");
        JsonObject axiomB = axiom("ABBREVIATION"); // same type, no xrefs
        putSynonyms(json, HAS_EXACT_SYNONYM, reifiedSynonym("same name", axiomA, axiomB));

        List<V1OboSynonym> result = V1OboSynonymExtractor.extractFromJson(json);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).xrefs).hasSize(1);
        assertThat(result.get(1).xrefs).isEmpty();
    }

    // --- dedup scoping: mergeDuplicates runs per (synonym-object, scope) call, not globally -----

    /**
     * {@code mergeDuplicates} is invoked from inside {@code fromSynonymObject}, i.e. once per
     * synonym-value object for one fixed scope -- never again across the four categories at the
     * top level. Two otherwise-identical synonyms tagged with different scopes can never be
     * compared against each other by {@code mergeDuplicates} (each comes from its own
     * {@code fromSynonymObject} call), so both are correctly kept -- and even if they somehow
     * were compared, {@code V1OboSynonym.equals()} treats a different {@code scope} as a
     * different object anyway. This confirms the per-category dedup composes correctly at the
     * top level for the cross-scope case.
     */
    @Test
    void identicalSynonymsInDifferentScopeCategoriesBothSurvive() {
        JsonObject json = baseJson();
        putSynonyms(json, HAS_EXACT_SYNONYM, reifiedSynonym("cross-scope name", axiom("ABBREVIATION")));
        putSynonyms(json, HAS_NARROW_SYNONYM, reifiedSynonym("cross-scope name", axiom("ABBREVIATION")));

        List<V1OboSynonym> result = V1OboSynonymExtractor.extractFromJson(json);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name).isEqualTo("cross-scope name");
        assertThat(result.get(1).name).isEqualTo("cross-scope name");
        assertThat(result.get(0).scope).isEqualTo("hasExactSynonym");
        assertThat(result.get(1).scope).isEqualTo("hasNarrowSynonym");
    }

    /**
     * A subtler consequence of the same per-call scoping: {@code mergeDuplicates} doesn't even
     * compose across two <em>different</em> synonym-value objects within the <strong>same</strong>
     * scope category. Two separate objects in the same {@code hasExactSynonym} array that each,
     * independently, resolve to an identical {@code V1OboSynonym} are never compared against each
     * other (each object gets its own {@code fromSynonymObject} call and its own, separately
     * scoped {@code mergeDuplicates} pass over just its own axioms) -- so both survive as
     * duplicate entries in the final result. Contrast with
     * {@link #identicalAxiomsOnSameSynonymObjectCollapseToOneEntry()}, where the *same* dedup
     * pass is what collapses two identical axioms belonging to one object.
     */
    @Test
    void identicalSynonymsFromDifferentObjectsInSameCategoryAreNotDeduplicatedAcrossObjects() {
        JsonObject json = baseJson();
        JsonObject synonymObjectA = reifiedSynonym("duplicate across objects", axiom("ABBREVIATION"));
        JsonObject synonymObjectB = reifiedSynonym("duplicate across objects", axiom("ABBREVIATION"));
        putSynonyms(json, HAS_EXACT_SYNONYM, synonymObjectA, synonymObjectB);

        List<V1OboSynonym> result = V1OboSynonymExtractor.extractFromJson(json);

        assertThat(result).hasSize(2);
    }

    // --- final null-vs-list contract -------------------------------------------------------------

    @Test
    void extractFromJsonReturnsNullWhenNoSynonymsAnywhere() {
        JsonObject json = baseJson();

        assertThat(V1OboSynonymExtractor.extractFromJson(json)).isNull();
    }

    @Test
    void extractFromJsonReturnsNonNullListWhenAtLeastOneSynonymExists() {
        JsonObject json = baseJson();
        putSynonyms(json, HAS_BROAD_SYNONYM, reifiedSynonym("only one", axiom(null)));

        List<V1OboSynonym> result = V1OboSynonymExtractor.extractFromJson(json);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
    }
}
