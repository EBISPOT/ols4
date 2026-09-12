package uk.ac.ebi.spot.ols.repository.v1.mappers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import uk.ac.ebi.spot.ols.model.v1.V1OboXref;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct unit coverage for {@link V1OboXrefExtractor}. Like {@code V1OboSynonymExtractor} and
 * {@code V1OboDefinitionCitationExtractor}, this is a pure static-method utility class with no
 * Spring bean and no Postgres dependency -- see docs/backend-testing-strategy.md's "Implemented
 * V1OboXrefExtractor baseline" section for why no *IT layer applies here.
 *
 * <p>This class's own logic (per-axiom description/url derivation, mergeDuplicates, the
 * size&gt;0?list:null contract) is the primary target. It calls into the shared
 * {@link V1OboXref#fromString(String, JsonObject)} value-object method far more heavily than its
 * sibling extractors -- most of the fixtures below use plain-primitive xref strings specifically
 * so that {@code fromString}'s own branches (http(s)-prefixed, "://"-with-uppercase-DOI-style,
 * the "://"-fallthrough, no-colon, and multi-colon shapes) get real, direct coverage as a
 * deliberate side effect, per docs/backend-testing-strategy.md.
 *
 * <p>Fixture shapes (the reified {@code {"value": {...}, "axioms": [...]}} wrapper and the
 * {@code hasDbXref}/{@code source}/{@code label} predicate keys) are modelled on real rdf2json
 * linker output, cross-checked against
 * {@code testcases_expected_output/annotation-properties/gitIssue502/ontologies_linked.json}
 * (which includes a real 3-source axiom on {@code http://purl.obolibrary.org/obo/MONDO_0000001}:
 * {@code ["DOID:4", "EFO:0000408", "MONDO:equivalentTo"]}, used verbatim in the "last source wins"
 * test below). No real committed fixture was found with an axiom's own {@code "url"} field, a
 * {@code rdf-schema#label} axiom value, or a bare (non-reified) primitive {@code hasDbXref} value
 * -- those cases are covered with hand-built synthetic fixtures instead, same as the synonym
 * extractor baseline's precedent for its own unobserved shapes.
 */
class V1OboXrefExtractorTest {

    private static final String HAS_DB_XREF = "http://www.geneontology.org/formats/oboInOwl#hasDbXref";
    private static final String SOURCE = "http://www.geneontology.org/formats/oboInOwl#source";
    private static final String LABEL = "http://www.w3.org/2000/01/rdf-schema#label";

    // --- fixture builders --------------------------------------------------------------------

    /**
     * Every real caller (only {@code V1TermMapper}) supplies a "linkedEntities" object taken
     * from the same top-level entity JSON -- most fixtures below use this so the "happy path"
     * cases match genuine production shape; the NPE-investigation test deliberately omits it.
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

    private static JsonElement literalOrArray(String... values) {
        if (values.length == 1) {
            return wrappedLiteral(values[0]);
        }
        JsonArray arr = new JsonArray();
        for (String v : values) {
            arr.add(wrappedLiteral(v));
        }
        return arr;
    }

    /** An axiom carrying one or more {@code oboInOwl#source} values, optionally a {@code url}. */
    private static JsonObject axiomWithSource(String url, String... sourceValues) {
        JsonObject axiom = new JsonObject();
        axiom.add(SOURCE, literalOrArray(sourceValues));
        if (url != null) {
            axiom.addProperty("url", url);
        }
        return axiom;
    }

    /** An axiom carrying one or more {@code rdf-schema#label} values, optionally a {@code url}. */
    private static JsonObject axiomWithLabel(String url, String... labelValues) {
        JsonObject axiom = new JsonObject();
        axiom.add(LABEL, literalOrArray(labelValues));
        if (url != null) {
            axiom.addProperty("url", url);
        }
        return axiom;
    }

    /** An axiom with neither {@code source} nor {@code label}, optionally a {@code url}. */
    private static JsonObject axiomPlain(String url) {
        JsonObject axiom = new JsonObject();
        if (url != null) {
            axiom.addProperty("url", url);
        }
        return axiom;
    }

    /**
     * A reified xref value object: {@code {"value": {...}, "axioms": [...]}}. Passing zero
     * axioms produces a present-but-empty {@code "axioms": []} array (distinct from
     * {@link #reifiedXrefNoAxiomsKey} below, which omits the key entirely).
     */
    private static JsonObject reifiedXref(String value, JsonObject... axioms) {
        JsonObject obj = new JsonObject();
        JsonArray type = new JsonArray();
        type.add("reification");
        obj.add("type", type);
        obj.add("value", wrappedLiteral(value));
        JsonArray axiomsArr = new JsonArray();
        for (JsonObject a : axioms) {
            axiomsArr.add(a);
        }
        obj.add("axioms", axiomsArr);
        return obj;
    }

    /** A reified xref object with no {@code "axioms"} key at all. */
    private static JsonObject reifiedXrefNoAxiomsKey(String value) {
        JsonObject obj = new JsonObject();
        JsonArray type = new JsonArray();
        type.add("reification");
        obj.add("type", type);
        obj.add("value", wrappedLiteral(value));
        return obj;
    }

    private static void putXrefs(JsonObject json, JsonElement... values) {
        JsonArray arr = new JsonArray();
        for (JsonElement v : values) {
            arr.add(v);
        }
        json.add(HAS_DB_XREF, arr);
    }

    private static void addLinkedEntityWithUrl(JsonObject json, String key, String url) {
        JsonObject entity = new JsonObject();
        entity.addProperty("url", url);
        json.getAsJsonObject("linkedEntities").add(key, entity);
    }

    private static void addLinkedEntityWithoutUrl(JsonObject json, String key) {
        JsonObject entity = new JsonObject();
        entity.addProperty("curie", key);
        json.getAsJsonObject("linkedEntities").add(key, entity);
    }

    // --- plain primitive xref (no object wrapper at all) ----------------------------------------

    @Test
    void primitiveXrefIsAddedDirectlyWithNoAxiomProcessing() {
        JsonObject json = baseJson();
        putXrefs(json, new JsonPrimitive("NCIT:C001"));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).database).isEqualTo("NCIT");
        assertThat(result.get(0).id).isEqualTo("C001");
        assertThat(result.get(0).description).isNull();
    }

    // --- object xref with no axioms key, and with an empty axioms list: same "else" branch -----

    @Test
    void xrefObjectWithNoAxiomsKeyProducesPlainEntryWithNoDescription() {
        JsonObject json = baseJson();
        putXrefs(json, reifiedXrefNoAxiomsKey("NCIT:C002"));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id).isEqualTo("C002");
        assertThat(result.get(0).description).isNull();
    }

    @Test
    void xrefObjectWithEmptyAxiomsListTakesTheSamePlainBranchAsNoAxiomsKeyAtAll() {
        // axioms.size() > 0 is the actual check in the source -- a present-but-empty list must
        // behave identically to a wholly-absent key, not be treated as "has axioms".
        JsonObject json = baseJson();
        putXrefs(json, reifiedXref("NCIT:C002")); // zero axioms passed -> "axioms": []

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id).isEqualTo("C002");
        assertThat(result.get(0).description).isNull();
    }

    // --- one xref object with 2+ axioms produces that many entries before dedup ----------------

    @Test
    void xrefObjectWithMultipleDistinctAxiomsProducesOneEntryPerAxiom() {
        JsonObject json = baseJson();
        putXrefs(json, reifiedXref("NCIT:C003",
                axiomWithSource(null, "SRC:1"),
                axiomPlain(null)));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).description).isEqualTo("SRC:1");
        assertThat(result.get(1).description).isNull();
    }

    // --- source: "last element wins" (mirrors OLS3's overwrite-in-a-loop bug/behaviour) ---------

    @Test
    void axiomWithThreeSourceValuesSetsDescriptionToTheLastElementNotFirstOrMiddle() {
        // Grounded in the real axiom on http://purl.obolibrary.org/obo/MONDO_0000001 in
        // testcases_expected_output/annotation-properties/gitIssue502/ontologies_linked.json.
        JsonObject json = baseJson();
        putXrefs(json, reifiedXref("MESH:D004194",
                axiomWithSource(null, "DOID:4", "EFO:0000408", "MONDO:equivalentTo")));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).description).isEqualTo("MONDO:equivalentTo");
    }

    @Test
    void axiomWithBothSourceAndLabelTakesTheSourcePathNotLabel() {
        // The `continue` after the source branch means label is never even inspected once
        // source is present.
        JsonObject json = baseJson();
        JsonObject axiom = axiomWithSource(null, "SRC:one", "SRC:two");
        axiom.add(LABEL, wrappedLiteral("should be ignored"));
        putXrefs(json, reifiedXref("NCIT:C004", axiom));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).description).isEqualTo("SRC:two");
    }

    @Test
    void axiomWithSourceAndUrlOverridesUrlFromAxiomsOwnField() {
        JsonObject json = baseJson();
        putXrefs(json, reifiedXref("NCIT:C005",
                axiomWithSource("http://axiom-override.example/", "SRC:only")));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).url).isEqualTo("http://axiom-override.example/");
    }

    @Test
    void axiomWithSourceAndNoUrlKeepsWhateverFromStringDerived() {
        JsonObject json = baseJson();
        addLinkedEntityWithUrl(json, "NCIT:C006", "http://from-linked-entities.example/");
        putXrefs(json, reifiedXref("NCIT:C006", axiomWithSource(null, "SRC:only")));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).url).isEqualTo("http://from-linked-entities.example/");
    }

    // --- label: only reached when no source values present ---------------------------------------

    @Test
    void axiomWithLabelOnlySetsDescriptionToLastLabelElement() {
        JsonObject json = baseJson();
        putXrefs(json, reifiedXref("NCIT:C007",
                axiomWithLabel(null, "label one", "label two", "label three")));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).description).isEqualTo("label three");
    }

    @Test
    void axiomWithLabelAndUrlOverridesUrlFromAxiomsOwnField() {
        JsonObject json = baseJson();
        putXrefs(json, reifiedXref("NCIT:C008",
                axiomWithLabel("http://label-axiom-url.example/", "only label")));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).description).isEqualTo("only label");
        assertThat(result.get(0).url).isEqualTo("http://label-axiom-url.example/");
    }

    // --- neither source nor label: plain entry, url override only --------------------------------

    @Test
    void axiomWithNeitherSourceNorLabelProducesPlainEntryWithUrlOverride() {
        JsonObject json = baseJson();
        putXrefs(json, reifiedXref("NCIT:C009", axiomPlain("http://plain-axiom-url.example/")));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).description).isNull();
        assertThat(result.get(0).url).isEqualTo("http://plain-axiom-url.example/");
    }

    @Test
    void axiomWithNeitherSourceNorLabelAndNoUrlKeepsFromStringDerivedUrlAndHasNoDescription() {
        JsonObject json = baseJson();
        addLinkedEntityWithUrl(json, "NCIT:C010", "http://from-linked-entities-2.example/");
        putXrefs(json, reifiedXref("NCIT:C010", axiomPlain(null)));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).description).isNull();
        assertThat(result.get(0).url).isEqualTo("http://from-linked-entities-2.example/");
    }

    // --- mergeDuplicates: collapses genuinely identical entries, keeps entries differing in one field --

    @Test
    void mergeDuplicatesCollapsesTwoAxiomsProducingGenuinelyIdenticalXrefs() {
        JsonObject json = baseJson();
        putXrefs(json, reifiedXref("NCIT:C011",
                axiomWithSource(null, "SRC:same"),
                axiomWithSource(null, "SRC:same")));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
    }

    @Test
    void mergeDuplicatesKeepsTwoEntriesFromDifferentXrefObjectsDifferingOnlyInDescription() {
        JsonObject json = baseJson();
        putXrefs(json,
                reifiedXref("NCIT:C012", axiomWithSource(null, "SRC:alpha")),
                reifiedXref("NCIT:C012", axiomWithSource(null, "SRC:beta")));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).description).isEqualTo("SRC:alpha");
        assertThat(result.get(1).description).isEqualTo("SRC:beta");
    }

    // --- final null-vs-list contract -------------------------------------------------------------

    @Test
    void extractFromJsonReturnsNullWhenNoXrefsAnywhere() {
        JsonObject json = baseJson();

        assertThat(V1OboXrefExtractor.extractFromJson(json)).isNull();
    }

    @Test
    void extractFromJsonReturnsNonNullListWhenAtLeastOneXrefExists() {
        JsonObject json = baseJson();
        putXrefs(json, new JsonPrimitive("NCIT:C013"));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
    }

    // --- V1OboXref.fromString branch coverage, driven through the extractor's primitive path ----
    // (deliberately exercising the shared collaborator's own branches as a side effect, per
    // docs/backend-testing-strategy.md -- see that class's own commented-out dead code showing an
    // alternate, no-longer-active implementation for the "scheme://..." DOI-style case.)

    @Test
    void httpPrefixedXrefSetsIdAndUrlToWholeStringWithNullDatabase() {
        JsonObject json = baseJson();
        putXrefs(json, new JsonPrimitive("http://example.org/foo"));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id).isEqualTo("http://example.org/foo");
        assertThat(result.get(0).url).isEqualTo("http://example.org/foo");
        assertThat(result.get(0).database).isNull();
    }

    @Test
    void httpsPrefixedXrefSetsIdAndUrlToWholeStringWithNullDatabase() {
        JsonObject json = baseJson();
        putXrefs(json, new JsonPrimitive("https://example.org/foo"));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id).isEqualTo("https://example.org/foo");
        assertThat(result.get(0).url).isEqualTo("https://example.org/foo");
        assertThat(result.get(0).database).isNull();
    }

    @Test
    void doiStyleUppercasePrefixedSchemeUrlXrefKeepsEntireStringAsIdWithNullDatabaseAndUrl() {
        // Confirms the CURRENT, active behaviour -- the commented-out code in V1OboXref shows an
        // alternate historical implementation that split this into database/id/url; that is dead
        // code, not what actually runs.
        String xref = "DOI:https://doi.org/10.1378/chest.12-2762";
        JsonObject json = baseJson();
        putXrefs(json, new JsonPrimitive(xref));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id).isEqualTo(xref);
        assertThat(result.get(0).database).isNull();
        assertThat(result.get(0).url).isNull();
    }

    @Test
    void schemeUrlXrefWithLowercasePrefixFallsThroughToColonSplittingProducingMangledDatabaseAndId() {
        // "orcid://0000-0001-2345-6789" contains "://" but does NOT match ^[A-Z]+:.+ (lowercase
        // prefix), and doesn't start with "http:"/"https:" either -- so it falls all the way
        // through to the generic split(":") logic below, which splits on the single colon before
        // the "//", producing database="orcid" and a mangled id starting with "//". Verified
        // empirically (jshell), not assumed. Investigated as a possible production defect: zero
        // occurrences of any hasDbXref value shaped like this were found across all 111 committed
        // testcases_expected_output/*/ontologies_linked.json fixtures (2,388 files matched
        // "linkedEntities", 985 scanned entities) or any of the 1,016 processed oboXrefs entries
        // in testcases_expected_output_api/**/*.json -- real OBO xref data uses either a bare
        // http(s) URL, the uppercase "DATABASE:https://..." DOI-style convention, or a plain
        // "DATABASE:ID" pair, never a lowercase-scheme "scheme://..." shape. Documented here as a
        // confirmed, currently-dormant code path rather than filed as a separate defect PR -- see
        // docs/backend-testing-strategy.md's baseline section for the full writeup.
        String xref = "orcid://0000-0001-2345-6789";
        JsonObject json = baseJson();
        addLinkedEntityWithUrl(json, xref, "http://linked-for-fallthrough.example/");
        putXrefs(json, new JsonPrimitive(xref));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).database).isEqualTo("orcid");
        assertThat(result.get(0).id).isEqualTo("//0000-0001-2345-6789");
        // tokens.length >= 2 here, so the linkedEntities lookup (keyed on the original,
        // unmodified string) still runs, unlike the DOI-style early-return branch above.
        assertThat(result.get(0).url).isEqualTo("http://linked-for-fallthrough.example/");
    }

    @Test
    void noColonXrefKeepsWholeStringAsIdWithNullDatabaseAndNeverTouchesLinkedEntities() {
        // tokens.length < 2 returns before the linkedEntities dereference -- confirmed safe even
        // when "linkedEntities" is entirely absent, in direct contrast to the multi-token branch
        // below (and the NPE-reachability test further down).
        JsonObject json = new JsonObject(); // deliberately no "linkedEntities" key at all
        putXrefs(json, new JsonPrimitive("PlainWordWithNoColon"));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id).isEqualTo("PlainWordWithNoColon");
        assertThat(result.get(0).database).isNull();
    }

    @Test
    void multiColonXrefUsesOnlyFirstTwoTokensAndLooksUpTheOriginalUnmodifiedStringAsTheKey() {
        // "A:B:C".split(":") produces 3 tokens; only tokens[0]/tokens[1] are used, silently
        // dropping everything after the second token. The linkedEntities lookup key is the full
        // original string "A:B:C", NOT a reconstructed "A:B" -- proven by putting a (wrong) entry
        // under the truncated key that must NOT be picked up, alongside the correct one under the
        // full original string.
        JsonObject json = baseJson();
        addLinkedEntityWithUrl(json, "A:B", "http://wrong-truncated-key.example/");
        addLinkedEntityWithUrl(json, "A:B:C", "http://correct-full-key.example/");
        putXrefs(json, new JsonPrimitive("A:B:C"));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).database).isEqualTo("A");
        assertThat(result.get(0).id).isEqualTo("B");
        assertThat(result.get(0).url).isEqualTo("http://correct-full-key.example/");
    }

    @Test
    void standardDatabaseIdXrefWithEmptyLinkedEntitiesResolvesGracefullyWithNullUrl() {
        JsonObject json = baseJson(); // "linkedEntities" present, but empty
        putXrefs(json, new JsonPrimitive("NCIT:C014"));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).database).isEqualTo("NCIT");
        assertThat(result.get(0).id).isEqualTo("C014");
        assertThat(result.get(0).url).isNull();
    }

    @Test
    void standardDatabaseIdXrefWithMatchingLinkedEntityButNoUrlFieldLeavesUrlNull() {
        // Confirms the `.has("url")` check specifically -- a matching entry that lacks its own
        // "url" property must not throw and must leave xref.url null.
        JsonObject json = baseJson();
        addLinkedEntityWithoutUrl(json, "NCIT:C015");
        putXrefs(json, new JsonPrimitive("NCIT:C015"));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).url).isNull();
    }

    @Test
    void standardDatabaseIdXrefWithMatchingLinkedEntityAndUrlFieldResolvesTheUrl() {
        JsonObject json = baseJson();
        addLinkedEntityWithUrl(json, "NCIT:C016", "http://purl.obolibrary.org/obo/NCIT_C016");
        putXrefs(json, new JsonPrimitive("NCIT:C016"));

        List<V1OboXref> result = V1OboXrefExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).url).isEqualTo("http://purl.obolibrary.org/obo/NCIT_C016");
    }

    // --- investigation: null linkedEntities threaded into V1OboXref.fromString -----------------

    /**
     * {@code extractFromJson} reads "linkedEntities" via the null-safe
     * {@code JsonObject.getAsJsonObject(String)} overload, so a genuinely missing key yields a
     * {@code null} local variable here with no exception at that point. That {@code null} is
     * then passed straight into {@code V1OboXref.fromString(oboXref, linkedEntities)}, which only
     * dereferences it in the {@code tokens.length >= 2} branch (a standard "DATABASE:ID"-shaped
     * xref, or anything else that reaches the generic split logic, including the "://"-fallthrough
     * case above) via the unguarded {@code linkedEntities.get(oboXref)} call.
     *
     * <p>Investigated independently for V1OboXrefExtractor's own real production callers, rather
     * than assuming the sibling V1OboSynonymExtractor baseline's conclusion transfers unexamined:
     * the only production caller is {@code V1TermMapper.mapTerm}, which passes the same
     * {@code localizedJson} object to this extractor, {@code V1OboSynonymExtractor}, and
     * {@code V1OboDefinitionCitationExtractor} alike. A fresh scan of all 111 committed
     * {@code ontologies_linked.json} fixtures under {@code testcases_expected_output/} found 985
     * top-level entities across the classes/individuals/properties arrays, and confirmed 0 of
     * them are missing a "linkedEntities" key. {@code V1TermMapper.mapTerm} itself also
     * dereferences {@code localizedJson.getAsJsonObject("linkedEntities").getAsJsonObject()...}
     * completely unconditionally a few lines after calling this extractor, to resolve each
     * RELATED_TO annotation's label -- so the codebase already assumes this invariant a second
     * time, independently, in the same method. Conclusion: the missing-null-guard is real and
     * does cause a live {@link NullPointerException} here, but is confirmed unreachable given the
     * current pipeline's invariant that every top-level entity always carries "linkedEntities".
     */
    @Test
    void xrefResolutionThrowsNpeWhenLinkedEntitiesKeyIsAbsent() {
        JsonObject json = new JsonObject(); // deliberately no "linkedEntities" key at all
        putXrefs(json, new JsonPrimitive("NCIT:C2991"));

        assertThatThrownBy(() -> V1OboXrefExtractor.extractFromJson(json))
                .isInstanceOf(NullPointerException.class);
    }
}
