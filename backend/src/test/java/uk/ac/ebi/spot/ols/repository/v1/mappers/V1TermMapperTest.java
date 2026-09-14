package uk.ac.ebi.spot.ols.repository.v1.mappers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import uk.ac.ebi.spot.ols.model.v1.V1Term;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct unit coverage for {@link V1TermMapper}, the orchestrating mapper that every previously
 * tested V1 extractor in this programme (AnnotationExtractor, V1OboDefinitionCitationExtractor,
 * V1OboXrefExtractor, V1OboSynonymExtractor, ShortFormExtractor) feeds into. Those extractors
 * already have their own exhaustive dedicated test coverage; this class tests V1TermMapper's OWN
 * wiring and logic only. Each delegation test below is a single representative call proving
 * correct delegation (right input passed in, right field assigned out) -- the collaborators' own
 * branches are deliberately not re-enumerated here.
 *
 * Scope: unit-only, no IT layer -- see docs/backend-testing-strategy.md's "Implemented
 * V1TermMapper baseline" section for why (pure static-method logic operating on a JsonElement
 * already read from Postgres; no Postgres dependency of its own).
 */
class V1TermMapperTest {

    private static final String REPLACED_BY_PREDICATE = "http://purl.obolibrary.org/obo/IAO_0100001";
    private static final String HAS_DB_XREF = "http://www.geneontology.org/formats/oboInOwl#hasDbXref";
    private static final String HAS_EXACT_SYNONYM = "http://www.geneontology.org/formats/oboInOwl#hasExactSynonym";

    /**
     * A minimal, fully-populated fixture that reaches the end of mapTerm without throwing. Every
     * real caller of V1TermMapper.mapTerm (V1TermRepository, V1IndividualRepository) supplies a
     * JSON entity that already carries all of these keys -- see the shortForm and linkedEntities
     * reachability investigation notes on the tests below and in
     * docs/backend-testing-strategy.md's "Implemented V1TermMapper baseline" section.
     *
     * No "type" key is set here, so LocalizationTransform.transform takes its "not localizable,
     * return unchanged" branch. That keeps every fixture in this file (other than the dedicated
     * localization-wiring test at the bottom) a flat, deterministic JsonObject, matching this
     * repo's existing V1PropertyMapperTest/V1IndividualMapperTest idiom.
     */
    private static JsonObject baseJson() {
        JsonObject json = new JsonObject();
        json.addProperty("iri", "http://purl.obolibrary.org/obo/GO_0008150");
        json.addProperty("ontologyId", "go");
        json.addProperty("ontologyPreferredPrefix", "GO");
        json.addProperty("ontologyIri", "http://purl.obolibrary.org/obo/go.owl");
        json.addProperty("shortForm", "GO_0008150");
        json.addProperty("label", "biological_process");
        json.add("definition", new JsonArray());
        json.add("synonym", new JsonArray());
        json.addProperty("isDefiningOntology", true);
        json.addProperty("isObsolete", false);
        json.addProperty("hasDirectChildren", false);
        json.addProperty("hasHierarchicalChildren", false);
        json.addProperty("hasDirectParents", false);
        json.addProperty("hasHierarchicalParents", false);
        json.add("linkedEntities", new JsonObject());
        return json;
    }

    private static JsonObject relatedToEntry(String property, String value) {
        JsonObject relatedTo = new JsonObject();
        relatedTo.addProperty("property", property);
        relatedTo.addProperty("value", value);
        return relatedTo;
    }

    // --- oboId construction from shortForm ----------------------------------------------------

    @Test
    void oboIdReplacesLastUnderscoreWithColon() {
        JsonObject json = baseJson();
        json.addProperty("shortForm", "GO_0008150");

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.shortForm).isEqualTo("GO_0008150");
        assertThat(term.oboId).isEqualTo("GO:0008150");
    }

    @Test
    void oboIdEqualsShortFormWhenNoUnderscorePresent() {
        JsonObject json = baseJson();
        json.addProperty("shortForm", "FOO123");

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.oboId).isEqualTo("FOO123");
    }

    @Test
    void oboIdOnlyReplacesTheLastOfMultipleUnderscores() {
        JsonObject json = baseJson();
        json.addProperty("shortForm", "FOO_BAR_123");

        V1Term term = V1TermMapper.mapTerm(json, "en");

        // The earlier underscore (between FOO and BAR) must survive untouched.
        assertThat(term.oboId).isEqualTo("FOO_BAR:123");
    }

    @Test
    void oboIdHasTrailingColonWithEmptyIdPartWhenShortFormEndsInUnderscore() {
        JsonObject json = baseJson();
        json.addProperty("shortForm", "FOO_");

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.oboId).isEqualTo("FOO:");
    }

    /**
     * Investigation finding: {@code term.shortForm.lastIndexOf("_")} has no null-guard, so a
     * genuinely missing "shortForm" key throws {@link NullPointerException} immediately
     * (JsonHelper.getString returns null for a missing key, and oboId construction is the very
     * next line, calling .lastIndexOf on it unguarded). Confirmed empirically by this test.
     *
     * Reachability: NOT a live production risk. Every real class/property/individual/datatype
     * node with a URI gets "shortForm" set unconditionally by
     * dataload/rdf2json/src/main/java/uk/ac/ebi/rdf2json/annotators/ShortFormAnnotator.java's
     * annotateShortForms (only bnodes, which have no URI and are never the kind of node looked up
     * by IRI/shortForm/oboId through V1TermRepository or V1IndividualRepository, are skipped). An
     * independent scan of every committed testcases_expected_output/star-star/ontologies_linked.json
     * fixture (985 class/property/individual entities across 111 files) found "shortForm" present
     * on all 985 -- 0 missing -- confirming the same baseline already established for
     * V1OboSynonymExtractor/V1OboXrefExtractor still holds. Not a separate defect PR.
     */
    @Test
    void missingShortFormThrowsNullPointerException() {
        JsonObject json = baseJson();
        json.remove("shortForm");

        assertThatThrownBy(() -> V1TermMapper.mapTerm(json, "en"))
                .isInstanceOf(NullPointerException.class);
    }

    // --- hasChildren: HAS_DIRECT_CHILDREN OR HAS_HIERARCHICAL_CHILDREN (two distinct fields) --

    @Test
    void hasChildrenFalseWhenNeitherDirectNorHierarchicalChildrenSet() {
        JsonObject json = baseJson();
        json.addProperty("hasDirectChildren", false);
        json.addProperty("hasHierarchicalChildren", false);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.hasChildren).isFalse();
    }

    @Test
    void hasChildrenTrueWhenOnlyDirectChildrenSet() {
        JsonObject json = baseJson();
        json.addProperty("hasDirectChildren", true);
        json.addProperty("hasHierarchicalChildren", false);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.hasChildren).isTrue();
    }

    @Test
    void hasChildrenTrueWhenOnlyHierarchicalChildrenSet() {
        JsonObject json = baseJson();
        json.addProperty("hasDirectChildren", false);
        json.addProperty("hasHierarchicalChildren", true);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.hasChildren).isTrue();
    }

    @Test
    void hasChildrenTrueWhenBothDirectAndHierarchicalChildrenSet() {
        JsonObject json = baseJson();
        json.addProperty("hasDirectChildren", true);
        json.addProperty("hasHierarchicalChildren", true);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        // Correctly ORs two DISTINCT fields -- contrast with the confirmed bug fixed in PR #1427,
        // where V1AncestorsJsTreeBuilder mistakenly read HAS_DIRECT_CHILDREN twice for both.
        assertThat(term.hasChildren).isTrue();
    }

    // --- isRoot: negation of (HAS_DIRECT_PARENTS || HAS_HIERARCHICAL_PARENTS), via
    //     JsonHelper.getBoolean -- a different helper than the getString+parseBoolean pattern
    //     used for every other boolean field in this method -----------------------------------

    @Test
    void isRootTrueWhenNeitherDirectNorHierarchicalParentsSet() {
        JsonObject json = baseJson();
        json.addProperty("hasDirectParents", false);
        json.addProperty("hasHierarchicalParents", false);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.isRoot).isTrue();
    }

    @Test
    void isRootFalseWhenOnlyDirectParentsSet() {
        JsonObject json = baseJson();
        json.addProperty("hasDirectParents", true);
        json.addProperty("hasHierarchicalParents", false);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.isRoot).isFalse();
    }

    @Test
    void isRootFalseWhenOnlyHierarchicalParentsSet() {
        JsonObject json = baseJson();
        json.addProperty("hasDirectParents", false);
        json.addProperty("hasHierarchicalParents", true);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.isRoot).isFalse();
    }

    @Test
    void isRootFalseWhenBothDirectAndHierarchicalParentsSet() {
        JsonObject json = baseJson();
        json.addProperty("hasDirectParents", true);
        json.addProperty("hasHierarchicalParents", true);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.isRoot).isFalse();
    }

    /**
     * Investigation finding: {@code JsonHelper.getBoolean(json, key)} is
     * {@code json.getAsJsonPrimitive(key).getAsBoolean()}. Unlike {@code JsonHelper.getString}
     * (which returns null for a missing key -- safe for the
     * {@code Boolean.parseBoolean(...)} pattern used everywhere else in mapTerm, since
     * {@code Boolean.parseBoolean(null)} is simply {@code false}), {@code getAsJsonPrimitive(key)}
     * itself returns null for a missing key, and calling {@code .getAsBoolean()} on that null
     * throws {@link NullPointerException} immediately. Confirmed empirically against the real
     * {@code com.google.code.gson:gson:2.13.2} dependency this repo declares (not assumed from
     * documentation): {@code new JsonObject().getAsJsonPrimitive("missing").getAsBoolean()}
     * throws NPE with message "Cannot invoke ... because the return value of
     * ...getAsJsonPrimitive(String) is null".
     *
     * This test reaches that NPE because {@code hasDirectParents} is {@code false}, so Java's
     * {@code ||} operator does NOT short-circuit and evaluates
     * {@code HAS_HIERARCHICAL_PARENTS} too -- reaching, and throwing on, the missing key. See the
     * test immediately below for the converse (short-circuited, non-throwing) case.
     */
    @Test
    void missingHasHierarchicalParentsThrowsNullPointerExceptionWhenDirectParentsIsFalse() {
        JsonObject json = baseJson();
        json.addProperty("hasDirectParents", false);
        json.remove("hasHierarchicalParents");

        assertThatThrownBy(() -> V1TermMapper.mapTerm(json, "en"))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * The converse of the test above: when hasDirectParents is true, Java's {@code ||} operator
     * short-circuits and never evaluates HAS_HIERARCHICAL_PARENTS at all -- so a missing
     * hasHierarchicalParents key does NOT throw in this case, despite being exactly as "missing"
     * as in the test above. An easy detail to get backwards when reasoning about whether the
     * missing-key risk is actually reachable for a given input.
     */
    @Test
    void hasDirectParentsTrueShortCircuitsBeforeMissingHierarchicalParentsKeyIsEvaluated() {
        JsonObject json = baseJson();
        json.addProperty("hasDirectParents", true);
        json.remove("hasHierarchicalParents");

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.isRoot).isFalse();
    }

    // --- replacedBy: the self-documented "fake loop" only ever keeps the first value ---------

    @Test
    void termReplacedByStaysUnsetWhenNoReplacedByValuesPresent() {
        JsonObject json = baseJson();

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.termReplacedBy).isNull();
    }

    @Test
    void termReplacedByIsShortenedWhenFirstValueIsAPlainUriPrimitive() {
        JsonObject json = baseJson();
        json.addProperty(REPLACED_BY_PREDICATE, "http://purl.obolibrary.org/obo/GO_0008151");

        V1Term term = V1TermMapper.mapTerm(json, "en");

        // Shortened via ShortFormExtractor.extractShortForm (delegation only, not re-tested here).
        assertThat(term.termReplacedBy).isEqualTo("GO_0008151");
    }

    @Test
    void termReplacedByUsesRawValueFieldWithoutShorteningWhenFirstValueIsAReifiedObject() {
        JsonObject json = baseJson();
        JsonObject reified = new JsonObject();
        reified.addProperty("value", "http://purl.obolibrary.org/obo/GO_0008152");
        json.add(REPLACED_BY_PREDICATE, reified);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        // NOT shortened -- the full URI string from "value" is used verbatim.
        assertThat(term.termReplacedBy).isEqualTo("http://purl.obolibrary.org/obo/GO_0008152");
    }

    @Test
    void termReplacedByOnlyEverReflectsTheFirstOfMultipleValues() {
        JsonObject json = baseJson();
        JsonArray replacedBy = new JsonArray();
        replacedBy.add("http://purl.obolibrary.org/obo/GO_0008161");
        replacedBy.add("http://purl.obolibrary.org/obo/GO_0008162");
        json.add(REPLACED_BY_PREDICATE, replacedBy);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        // The second (and any further) value is silently ignored -- see the TODO in the source:
        // "fake loop only keeps first, check ols3 behaviour".
        assertThat(term.termReplacedBy).isEqualTo("GO_0008161");
    }

    // --- relatedTo / linkedEntities ------------------------------------------------------------

    /**
     * Investigation finding: {@code localizedJson.getAsJsonObject("linkedEntities")} is the
     * null-safe accessor (returns null for a missing key, no exception) -- but inside the
     * relatedTo loop, {@code linkedEntities.getAsJsonObject()} (the redundant no-arg self-cast,
     * a no-op when linkedEntities is already non-null) is called unconditionally once there is at
     * least one relatedTo entry, and throws {@link NullPointerException} if linkedEntities itself
     * is null. Confirmed empirically by this test. With zero relatedTo entries the loop body (and
     * this line) never executes, so the risk only exists for the compound condition: missing
     * linkedEntities AND at least one relatedTo entry.
     *
     * Reachability: an independent scan of every committed
     * testcases_expected_output/star-star/ontologies_linked.json fixture (985 class/property/
     * individual entities across 111 files) found "linkedEntities" present on all 985 -- 0
     * missing -- confirming the same baseline already established by
     * V1OboSynonymExtractor/V1OboXrefExtractor still holds. Every real caller (V1TermRepository,
     * V1IndividualRepository) reads entities produced by the standard rdf2json/Postgres dataload
     * pipeline; ResolveReferencesTransform (repository/transforms/ResolveReferencesTransform.java)
     * reads "linkedEntities" conditionally rather than defaulting it in, implying it is expected
     * to already be present upstream by the time it runs. Not reachable in practice; not a
     * separate defect PR.
     */
    @Test
    void missingLinkedEntitiesWithAtLeastOneRelatedToEntryThrowsNullPointerException() {
        JsonObject json = baseJson();
        json.remove("linkedEntities");

        JsonArray relatedToArr = new JsonArray();
        relatedToArr.add(relatedToEntry("http://example.org/onto#partOf", "http://example.org/onto#SomeOtherEntity"));
        json.add("relatedTo", relatedToArr);

        assertThatThrownBy(() -> V1TermMapper.mapTerm(json, "en"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void relatedIsAGenuineEmptyArrayListNotNullWhenThereAreNoRelatedToEntries() {
        JsonObject json = baseJson();

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.related).isNotNull();
        assertThat(term.related).isEmpty();
    }

    @Test
    void relatedLabelFallsBackToShortFormExtractorWhenPredicateIsNotInLinkedEntities() {
        JsonObject json = baseJson(); // linkedEntities present but empty

        JsonArray relatedToArr = new JsonArray();
        relatedToArr.add(relatedToEntry("http://example.org/onto#partOf", "http://example.org/onto#SomeOtherEntity"));
        json.add("relatedTo", relatedToArr);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.related).hasSize(1);
        assertThat(term.related.get(0).label).isEqualTo("partOf"); // ShortFormExtractor.extractShortForm
    }

    @Test
    void relatedLabelUsesLinkedEntityLabelWhenPredicateIsInLinkedEntities() {
        JsonObject json = baseJson();

        JsonObject linkedEntity = new JsonObject();
        linkedEntity.addProperty("label", "part of");
        JsonObject linkedEntities = new JsonObject();
        linkedEntities.add("http://example.org/onto#partOf", linkedEntity);
        json.add("linkedEntities", linkedEntities);

        JsonArray relatedToArr = new JsonArray();
        relatedToArr.add(relatedToEntry("http://example.org/onto#partOf", "http://example.org/onto#SomeOtherEntity"));
        json.add("relatedTo", relatedToArr);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.related).hasSize(1);
        assertThat(term.related.get(0).label).isEqualTo("part of");
    }

    @Test
    void relatedLabelIsNullWhenLinkedEntityHasNoLabelFieldAndDoesNotFallBackToShortForm() {
        JsonObject json = baseJson();

        JsonObject linkedEntity = new JsonObject(); // present in linkedEntities, but no "label" field
        JsonObject linkedEntities = new JsonObject();
        linkedEntities.add("http://example.org/onto#partOf", linkedEntity);
        json.add("linkedEntities", linkedEntities);

        JsonArray relatedToArr = new JsonArray();
        relatedToArr.add(relatedToEntry("http://example.org/onto#partOf", "http://example.org/onto#SomeOtherEntity"));
        json.add("relatedTo", relatedToArr);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.related).hasSize(1);
        // Precisely NOT the ShortFormExtractor fallback -- the linkedEntity was found, it just
        // has no "label" field of its own.
        assertThat(term.related.get(0).label).isNull();
    }

    @Test
    void multipleRelatedToEntriesEachProduceAnIndependentV1RelatedWithAllFieldsCorrect() {
        JsonObject json = baseJson();

        JsonArray relatedToArr = new JsonArray();
        relatedToArr.add(relatedToEntry("http://example.org/onto#partOf", "http://example.org/onto#EntityA"));
        relatedToArr.add(relatedToEntry("http://example.org/onto#regulates", "http://example.org/onto#EntityB"));
        json.add("relatedTo", relatedToArr);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.related).hasSize(2);

        var first = term.related.get(0);
        assertThat(first.iri).isEqualTo("http://example.org/onto#partOf");
        assertThat(first.label).isEqualTo("partOf");
        assertThat(first.ontologyName).isEqualTo(term.ontologyName);
        assertThat(first.relatedFromIri).isEqualTo(term.iri);
        assertThat(first.relatedToIri).isEqualTo("http://example.org/onto#EntityA");

        var second = term.related.get(1);
        assertThat(second.iri).isEqualTo("http://example.org/onto#regulates");
        assertThat(second.label).isEqualTo("regulates");
        assertThat(second.ontologyName).isEqualTo(term.ontologyName);
        assertThat(second.relatedFromIri).isEqualTo(term.iri);
        assertThat(second.relatedToIri).isEqualTo("http://example.org/onto#EntityB");
    }

    // --- description / synonyms: always arrays (via .toArray(new String[0])), never null ------

    @Test
    void descriptionAndSynonymsAreEmptyArraysNotNullWhenSourceListsAreEmpty() {
        JsonObject json = baseJson(); // definition/synonym already empty arrays

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.description).isNotNull();
        assertThat(term.description).isEmpty();
        assertThat(term.synonyms).isNotNull();
        assertThat(term.synonyms).isEmpty();
    }

    @Test
    void descriptionAndSynonymsArePopulatedFromSourceListsWhenPresent() {
        JsonObject json = baseJson();
        JsonArray definitions = new JsonArray();
        definitions.add("a biological process definition");
        JsonArray synonyms = new JsonArray();
        synonyms.add("bio process");
        synonyms.add("biological process synonym");
        json.add("definition", definitions);
        json.add("synonym", synonyms);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.description).containsExactly("a biological process definition");
        assertThat(term.synonyms).containsExactly("bio process", "biological process synonym");
    }

    // --- delegation to already-independently-tested collaborators ----------------------------
    // AnnotationExtractor, V1OboDefinitionCitationExtractor, V1OboXrefExtractor and
    // V1OboSynonymExtractor each have their own exhaustive dedicated test suite elsewhere in this
    // repository/programme (ShortFormExtractor's own suite is likewise elsewhere -- it is already
    // exercised above via the relatedTo/replacedBy tests). These tests only confirm that
    // V1TermMapper wires the right input in and assigns the right output field; they do not
    // re-enumerate the collaborators' own branches.

    @Test
    void delegatesToAnnotationExtractorForAnnotationAndInSubsetsFields() {
        JsonObject json = baseJson();
        json.addProperty("http://www.w3.org/2000/01/rdf-schema#comment", "a helpful comment");

        JsonArray subsets = new JsonArray();
        subsets.add("http://purl.obolibrary.org/obo/go#GO_slim");
        json.add("http://www.geneontology.org/formats/oboInOwl#inSubset", subsets);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.annotation).containsKey("comment");
        assertThat(term.inSubsets).containsExactly("GO_slim");
    }

    @Test
    void delegatesToV1OboDefinitionCitationExtractorForOboDefinitionCitations() {
        JsonObject json = baseJson();

        JsonObject axiom = new JsonObject();
        JsonArray xrefs = new JsonArray();
        xrefs.add("PMID:12345678");
        axiom.add(HAS_DB_XREF, xrefs);
        JsonArray axioms = new JsonArray();
        axioms.add(axiom);

        JsonObject definitionValue = new JsonObject();
        definitionValue.addProperty("value", "a term definition");
        definitionValue.add("axioms", axioms);

        JsonArray definitions = new JsonArray();
        definitions.add(definitionValue);
        json.add("definition", definitions);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.oboDefinitionCitations).hasSize(1);
        assertThat(term.oboDefinitionCitations.get(0).definition).isEqualTo("a term definition");
    }

    @Test
    void delegatesToV1OboXrefExtractorForOboXrefs() {
        JsonObject json = baseJson();
        JsonArray xrefs = new JsonArray();
        xrefs.add("PMID:87654321");
        json.add(HAS_DB_XREF, xrefs);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.oboXrefs).hasSize(1);
        assertThat(term.oboXrefs.get(0).database).isEqualTo("PMID");
        assertThat(term.oboXrefs.get(0).id).isEqualTo("87654321");
    }

    @Test
    void delegatesToV1OboSynonymExtractorForOboSynonyms() {
        JsonObject json = baseJson();

        JsonObject synonymValue = new JsonObject();
        synonymValue.addProperty("value", "a synonym text");
        JsonArray axioms = new JsonArray();
        axioms.add(new JsonObject());
        synonymValue.add("axioms", axioms);

        JsonArray exactSynonyms = new JsonArray();
        exactSynonyms.add(synonymValue);
        json.add(HAS_EXACT_SYNONYM, exactSynonyms);

        V1Term term = V1TermMapper.mapTerm(json, "en");

        assertThat(term.oboSynonyms).hasSize(1);
        assertThat(term.oboSynonyms.get(0).name).isEqualTo("a synonym text");
        assertThat(term.oboSynonyms.get(0).scope).isEqualTo("hasExactSynonym");
    }

    // --- LocalizationTransform wiring ----------------------------------------------------------

    /**
     * LocalizationTransform itself already has its own exhaustive dedicated test elsewhere in
     * this programme and is proven to never return null; this only confirms V1TermMapper genuinely
     * wires localization in for at least one language-dependent field, using the real (not faked)
     * LocalizationTransform. Unlike every other fixture in this file, this one sets
     * {@code "type": ["entity"]} so LocalizationTransform.transform actually routes through
     * localizeEntity/localizeLiteral instead of taking the "not localizable, return as-is"
     * shortcut every other fixture in this file relies on for simplicity.
     */
    @Test
    void localizationIsWiredInAndSelectsTheRequestedLanguageForLabel() {
        JsonObject json = new JsonObject();
        JsonArray type = new JsonArray();
        type.add("entity");
        json.add("type", type);

        json.addProperty("iri", "http://purl.obolibrary.org/obo/GO_0008150");
        json.addProperty("ontologyId", "go");
        json.addProperty("ontologyPreferredPrefix", "GO");
        json.addProperty("ontologyIri", "http://purl.obolibrary.org/obo/go.owl");
        json.addProperty("shortForm", "GO_0008150");
        json.addProperty("isDefiningOntology", true);
        json.addProperty("isObsolete", false);
        json.addProperty("hasDirectChildren", false);
        json.addProperty("hasHierarchicalChildren", false);
        json.addProperty("hasDirectParents", false);
        json.addProperty("hasHierarchicalParents", false);
        json.add("linkedEntities", new JsonObject());

        JsonObject englishLabel = new JsonObject();
        JsonArray englishType = new JsonArray();
        englishType.add("literal");
        englishLabel.add("type", englishType);
        englishLabel.addProperty("lang", "en");
        englishLabel.addProperty("value", "English label");

        JsonObject frenchLabel = new JsonObject();
        JsonArray frenchType = new JsonArray();
        frenchType.add("literal");
        frenchLabel.add("type", frenchType);
        frenchLabel.addProperty("lang", "fr");
        frenchLabel.addProperty("value", "French label");

        JsonArray labels = new JsonArray();
        labels.add(englishLabel);
        labels.add(frenchLabel);
        json.add("label", labels);

        V1Term termEn = V1TermMapper.mapTerm(json, "en");
        V1Term termFr = V1TermMapper.mapTerm(json, "fr");

        assertThat(termEn.label).isEqualTo("English label");
        assertThat(termFr.label).isEqualTo("French label");
    }
}
