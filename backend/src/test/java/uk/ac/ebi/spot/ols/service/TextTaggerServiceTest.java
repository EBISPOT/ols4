package uk.ac.ebi.spot.ols.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.spot.ols.service.TextTaggerService.TaggedEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct unit coverage for {@link TextTaggerService}'s pure filtering/parsing logic --
 * {@code parseResponse}, {@code jsonArrayToStringList}, {@code applyPriority} (and its
 * {@code spanKey} helper), {@code applySourceFilter}, {@code applyMinLength}, and
 * {@code removeSubstrings} -- plus the public {@code isAvailable()}/{@code tagText()} default
 * (un-started) state. Every method under test here is {@code private}; they are exercised via
 * reflection, the idiom this programme uses for private-method Tier B coverage (see
 * docs/backend-testing-strategy.md's Tier B methodology).
 *
 * <p>This class needs neither Postgres nor an external process: {@link TextTaggerService} is
 * constructed directly with {@code new TextTaggerService()}, and none of the methods exercised
 * here touch {@code postgresClient} or the CLI process fields. Large-Object download logic
 * ({@code downloadTextTaggerDb}) is covered separately in {@code TextTaggerServiceIT} (needs real
 * Postgres); real external-process management is covered in {@code TextTaggerServiceProcessIT}.
 */
class TextTaggerServiceTest {

    private final TextTaggerService service = new TextTaggerService();

    // ------------------------------------------------------------------
    // isAvailable() / tagText() default (un-started) state
    // ------------------------------------------------------------------

    @Test
    void isAvailableDefaultsToFalseBeforeInitEverRuns() {
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void tagTextReturnsEmptyListImmediatelyWhenUnavailableWithoutTouchingTheProcess() {
        // available defaults to false and is only ever set true inside startProcess(); tagText()
        // checks it before acquiring the lock or touching processStdin/processStdout, so this call
        // must return immediately with no NPE and no process ever spawned.
        List<TaggedEntity> result = service.tagText("insulin resistance", null, null, null, 3, true);

        assertThat(result).isEmpty();
    }

    @Test
    void tagTextThreeArgOverloadAlsoReturnsEmptyListWhenUnavailable() {
        List<TaggedEntity> result = service.tagText("insulin resistance", null, null);

        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------
    // parseResponse(String)
    // ------------------------------------------------------------------

    @Test
    void parseResponseReturnsEmptyListWhenEntitiesArrayIsAbsent() throws Exception {
        assertThat(parseResponse(service, "{}")).isEmpty();
    }

    @Test
    void parseResponseReturnsEmptyListWhenEntitiesArrayIsPresentButEmpty() throws Exception {
        assertThat(parseResponse(service, "{\"entities\":[]}")).isEmpty();
    }

    @Test
    void parseResponseDefaultsTermLabelTermIriAndOntologyIdToEmptyStringWhenAbsent() throws Exception {
        List<TaggedEntity> result = parseResponse(service, "{\"entities\":[{\"start\":0,\"end\":5}]}");

        assertThat(result).hasSize(1);
        TaggedEntity e = result.get(0);
        assertThat(e.start).isEqualTo(0);
        assertThat(e.end).isEqualTo(5);
        assertThat(e.termLabel).isEqualTo("");
        assertThat(e.termIri).isEqualTo("");
        assertThat(e.ontologyId).isEqualTo("");
        assertThat(e.stringType).isNull();
        assertThat(e.source).isNull();
        assertThat(e.subjectCategories).isNull();
        assertThat(e.isObsolete).isFalse();
    }

    @Test
    void parseResponseUsesTermLabelTermIriAndOntologyIdWhenPresent() throws Exception {
        String json = "{\"entities\":[{\"start\":0,\"end\":7,\"term_label\":\"insulin\","
                + "\"term_iri\":\"http://purl.obolibrary.org/obo/CHEBI_5931\",\"ontology_id\":\"chebi\"}]}";

        TaggedEntity e = parseResponse(service, json).get(0);

        assertThat(e.termLabel).isEqualTo("insulin");
        assertThat(e.termIri).isEqualTo("http://purl.obolibrary.org/obo/CHEBI_5931");
        assertThat(e.ontologyId).isEqualTo("chebi");
    }

    @Test
    void parseResponseReadsStringTypeWhenPresent() throws Exception {
        String json = "{\"entities\":[{\"start\":0,\"end\":5,\"string_type\":\"exact\"}]}";

        assertThat(parseResponse(service, json).get(0).stringType).isEqualTo("exact");
    }

    @Test
    void parseResponseReadsSourceWhenPresent() throws Exception {
        String json = "{\"entities\":[{\"start\":0,\"end\":5,\"source\":\"sssom-mappings\"}]}";

        assertThat(parseResponse(service, json).get(0).source).isEqualTo("sssom-mappings");
    }

    @Test
    void parseResponseReadsSubjectCategoriesWhenPresent() throws Exception {
        String json = "{\"entities\":[{\"start\":0,\"end\":5,\"subject_categories\":[\"chemical\",\"hormone\"]}]}";

        assertThat(parseResponse(service, json).get(0).subjectCategories)
                .containsExactly("chemical", "hormone");
    }

    @Test
    void parseResponseReadsIsObsoleteTrueWhenPresent() throws Exception {
        String json = "{\"entities\":[{\"start\":0,\"end\":5,\"is_obsolete\":true}]}";

        assertThat(parseResponse(service, json).get(0).isObsolete).isTrue();
    }

    @Test
    void parseResponseDefaultsIsObsoleteToFalseWhenAbsent() throws Exception {
        String json = "{\"entities\":[{\"start\":0,\"end\":5}]}";

        assertThat(parseResponse(service, json).get(0).isObsolete).isFalse();
    }

    @Test
    void parseResponseParsesMultipleEntitiesInOrder() throws Exception {
        String json = "{\"entities\":[{\"start\":0,\"end\":7,\"term_label\":\"insulin\"},"
                + "{\"start\":8,\"end\":18,\"term_label\":\"resistance\"}]}";

        List<TaggedEntity> result = parseResponse(service, json);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).termLabel).isEqualTo("insulin");
        assertThat(result.get(1).termLabel).isEqualTo("resistance");
    }

    /**
     * {@code parseResponse} reads {@code start}/{@code end} with a bare
     * {@code e.get("start").getAsInt()} -- unlike every other field, there is no {@code .has()}
     * guard. Empirically confirmed here: a missing {@code start} makes {@code e.get("start")}
     * return Java {@code null} (not {@code JsonNull}), so {@code .getAsInt()} throws
     * {@link NullPointerException}. Per the real CLI's own protocol (see {@code text_tagger/src/
     * main.rs}'s {@code Entity} struct: {@code start}/{@code end} are plain, non-{@code Option}
     * {@code usize} fields with no {@code skip_serializing_if}), the real binary always emits
     * both fields for every entity -- so this is not a reachable production defect against the
     * real binary, but it is a real fragility (a malformed/corrupted response line, or a future
     * protocol change, would crash parsing with an NPE instead of degrading gracefully like every
     * other field does). See the doc-update section for the full write-up of this finding.
     */
    @Test
    void parseResponseThrowsNullPointerExceptionWhenStartFieldIsMissing() {
        String json = "{\"entities\":[{\"end\":5}]}";

        assertThatThrownBy(() -> parseResponse(service, json)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void parseResponseThrowsNullPointerExceptionWhenEndFieldIsMissing() {
        String json = "{\"entities\":[{\"start\":0}]}";

        assertThatThrownBy(() -> parseResponse(service, json)).isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------
    // jsonArrayToStringList(JsonObject, String)
    // ------------------------------------------------------------------

    @Test
    void jsonArrayToStringListReturnsNullWhenKeyIsAbsent() throws Exception {
        JsonObject obj = new JsonObject();

        assertThat(jsonArrayToStringList(service, obj, "subject_categories")).isNull();
    }

    @Test
    void jsonArrayToStringListReturnsNullWhenValueIsJsonNull() throws Exception {
        JsonObject obj = new JsonObject();
        obj.add("subject_categories", JsonNull.INSTANCE);

        assertThat(jsonArrayToStringList(service, obj, "subject_categories")).isNull();
    }

    @Test
    void jsonArrayToStringListReturnsNullWhenValueIsNotAnArray() throws Exception {
        JsonObject obj = new JsonObject();
        obj.addProperty("subject_categories", "chemical");

        assertThat(jsonArrayToStringList(service, obj, "subject_categories")).isNull();
    }

    @Test
    void jsonArrayToStringListReturnsNullWhenArrayIsEmpty() throws Exception {
        JsonObject obj = new JsonObject();
        obj.add("subject_categories", new JsonArray());

        assertThat(jsonArrayToStringList(service, obj, "subject_categories")).isNull();
    }

    @Test
    void jsonArrayToStringListReturnsTheListWhenArrayIsNonEmpty() throws Exception {
        JsonObject obj = new JsonObject();
        JsonArray array = new JsonArray();
        array.add("chemical");
        array.add("hormone");
        obj.add("subject_categories", array);

        assertThat(jsonArrayToStringList(service, obj, "subject_categories"))
                .containsExactly("chemical", "hormone");
    }

    // ------------------------------------------------------------------
    // applyPriority(List<TaggedEntity>, List<String>) and spanKey(int, int)
    // ------------------------------------------------------------------

    @Test
    void applyPriorityReturnsEntitiesUnchangedWhenPriorityListIsNull() throws Exception {
        List<TaggedEntity> entities = List.of(entity(0, 7, "chebi"));

        assertThat(applyPriority(service, entities, null)).isSameAs(entities);
    }

    @Test
    void applyPriorityReturnsEntitiesUnchangedWhenPriorityListIsEmpty() throws Exception {
        List<TaggedEntity> entities = List.of(entity(0, 7, "chebi"));

        assertThat(applyPriority(service, entities, List.of())).isSameAs(entities);
    }

    @Test
    void applyPriorityDropsEntitiesFromOntologiesNotInThePriorityList() throws Exception {
        List<TaggedEntity> entities = List.of(entity(0, 7, "chebi"));

        assertThat(applyPriority(service, entities, List.of("efo", "hp"))).isEmpty();
    }

    @Test
    void applyPriorityKeepsOnlyTheHigherPriorityMatchForTheSameSpan() throws Exception {
        TaggedEntity efoMatch = entity(0, 7, "efo");
        TaggedEntity hpMatch = entity(0, 7, "hp");

        List<TaggedEntity> result = applyPriority(service, List.of(hpMatch, efoMatch), List.of("efo", "hp"));

        assertThat(result).extracting(e -> e.ontologyId).containsExactly("efo");
    }

    @Test
    void applyPriorityKeepsBothEntitiesIndependentlyWhenSpansDiffer() throws Exception {
        TaggedEntity first = entity(0, 7, "efo");
        TaggedEntity second = entity(8, 18, "hp");

        List<TaggedEntity> result = applyPriority(service, List.of(first, second), List.of("efo", "hp"));

        assertThat(result).extracting(e -> e.ontologyId).containsExactlyInAnyOrder("efo", "hp");
    }

    /**
     * {@code priorityMap} is built with {@code putIfAbsent}, so a duplicated ontology id keeps
     * its <em>first</em> occurrence's index. Here "efo" appears at index 0 and again at index 2
     * in the priority list; if the map instead kept the id's <em>last</em> occurrence (index 2,
     * via an unconditional overwrite), "hp" (index 1) would beat "efo" for the same span instead
     * -- this test fails under that alternate, wrong semantics.
     */
    @Test
    void applyPriorityFirstOccurrenceOfADuplicatedOntologyIdWinsItsIndex() throws Exception {
        List<String> priorityIds = List.of("efo", "hp", "efo");
        TaggedEntity hpMatch = entity(0, 7, "hp");
        TaggedEntity efoMatch = entity(0, 7, "efo");

        List<TaggedEntity> result = applyPriority(service, List.of(hpMatch, efoMatch), priorityIds);

        assertThat(result).extracting(e -> e.ontologyId).containsExactly("efo");
    }

    @Test
    void spanKeyProducesDistinctValuesForSpansSharingAStartOrEnd() throws Exception {
        long k12 = spanKey(1, 2);
        long k21 = spanKey(2, 1);
        long k13 = spanKey(1, 3);
        long k03 = spanKey(0, 3);

        assertThat(Set.of(k12, k21, k13, k03)).hasSize(4);
    }

    @Test
    void spanKeyProducesDistinctValuesNearTheIntToLongShiftBoundary() throws Exception {
        long kMaxZero = spanKey(Integer.MAX_VALUE, 0);
        long kZeroMax = spanKey(0, Integer.MAX_VALUE);
        long kMaxMax = spanKey(Integer.MAX_VALUE, Integer.MAX_VALUE);
        long kZeroZero = spanKey(0, 0);

        assertThat(Set.of(kMaxZero, kZeroMax, kMaxMax, kZeroZero)).hasSize(4);
    }

    // ------------------------------------------------------------------
    // applySourceFilter(List<TaggedEntity>, List<String>)
    // ------------------------------------------------------------------

    @Test
    void applySourceFilterReturnsEntitiesUnchangedWhenSourcesIsNull() throws Exception {
        List<TaggedEntity> entities = List.of(entityWithSource(0, 7, "chebi", "sssom-mappings"));

        assertThat(applySourceFilter(service, entities, null)).isSameAs(entities);
    }

    @Test
    void applySourceFilterReturnsEntitiesUnchangedWhenSourcesIsEmpty() throws Exception {
        List<TaggedEntity> entities = List.of(entityWithSource(0, 7, "chebi", "sssom-mappings"));

        assertThat(applySourceFilter(service, entities, List.of())).isSameAs(entities);
    }

    @Test
    void applySourceFilterAlwaysKeepsEntitiesWithNullSource() throws Exception {
        TaggedEntity noSource = entityWithSource(0, 7, "chebi", null);

        List<TaggedEntity> result = applySourceFilter(service, List.of(noSource), List.of("sssom-mappings"));

        assertThat(result).containsExactly(noSource);
    }

    @Test
    void applySourceFilterKeepsEntitiesWhoseSourceIsAllowed() throws Exception {
        TaggedEntity allowed = entityWithSource(0, 7, "chebi", "sssom-mappings");

        List<TaggedEntity> result = applySourceFilter(service, List.of(allowed), List.of("sssom-mappings"));

        assertThat(result).containsExactly(allowed);
    }

    @Test
    void applySourceFilterDropsEntitiesWhoseSourceIsNotAllowed() throws Exception {
        TaggedEntity notAllowed = entityWithSource(0, 7, "chebi", "manual-curation");

        List<TaggedEntity> result = applySourceFilter(service, List.of(notAllowed), List.of("sssom-mappings"));

        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------
    // applyMinLength(List<TaggedEntity>, int)
    // ------------------------------------------------------------------

    @Test
    void applyMinLengthReturnsEntitiesUnchangedWhenMinLengthIsZero() throws Exception {
        List<TaggedEntity> entities = List.of(entity(0, 1, "chebi"));

        assertThat(applyMinLength(service, entities, 0)).isSameAs(entities);
    }

    @Test
    void applyMinLengthReturnsEntitiesUnchangedWhenMinLengthIsNegative() throws Exception {
        List<TaggedEntity> entities = List.of(entity(0, 1, "chebi"));

        assertThat(applyMinLength(service, entities, -1)).isSameAs(entities);
    }

    @Test
    void applyMinLengthKeepsASpanExactlyEqualToMinLength() throws Exception {
        TaggedEntity e = entity(0, 5, "chebi"); // span length 5

        assertThat(applyMinLength(service, List.of(e), 5)).containsExactly(e);
    }

    @Test
    void applyMinLengthDropsASpanOneShorterThanMinLength() throws Exception {
        TaggedEntity e = entity(0, 4, "chebi"); // span length 4

        assertThat(applyMinLength(service, List.of(e), 5)).isEmpty();
    }

    // ------------------------------------------------------------------
    // removeSubstrings(List<TaggedEntity>)
    // ------------------------------------------------------------------

    @Test
    void removeSubstringsReturnsTheSameListWhenEmpty() throws Exception {
        List<TaggedEntity> entities = List.of();

        assertThat(removeSubstrings(service, entities)).isSameAs(entities);
    }

    @Test
    void removeSubstringsReturnsTheSameListWhenSingleElement() throws Exception {
        List<TaggedEntity> entities = List.of(entity(0, 7, "chebi"));

        assertThat(removeSubstrings(service, entities)).isSameAs(entities);
    }

    @Test
    void removeSubstringsKeepsBothEntitiesWithIdenticalSpans() throws Exception {
        TaggedEntity first = entity(0, 7, "chebi");
        TaggedEntity second = entity(0, 7, "hp");

        List<TaggedEntity> result = removeSubstrings(service, new ArrayList<>(List.of(first, second)));

        assertThat(result).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void removeSubstringsRemovesASpanStrictlyContainedInAnother() throws Exception {
        TaggedEntity outer = entity(0, 30, "chebi");
        TaggedEntity inner = entity(5, 25, "hp");

        List<TaggedEntity> result = removeSubstrings(service, new ArrayList<>(List.of(outer, inner)));

        assertThat(result).containsExactly(outer);
    }

    /**
     * {@code shortSpan} is listed <em>before</em> {@code longSpan} even though {@code longSpan}
     * contains it. If {@code removeSubstrings} relied on input order instead of its own internal
     * sort (start asc, then span length desc), {@code shortSpan} would be processed first, added
     * to the result before {@code longSpan} is even considered, and both would incorrectly
     * survive. Asserting the single correct survivor proves the sort -- not incidental list
     * order -- is what makes containment detection correct.
     */
    @Test
    void removeSubstringsSortsByStartThenLongestSpanFirstRegardlessOfInputOrder() throws Exception {
        TaggedEntity shortSpan = entity(5, 15, "hp");
        TaggedEntity longSpan = entity(0, 30, "chebi");

        List<TaggedEntity> result = removeSubstrings(service, new ArrayList<>(List.of(shortSpan, longSpan)));

        assertThat(result).containsExactly(longSpan);
    }

    /**
     * {@code smallest} is contained in {@code middle}, which is itself contained in
     * {@code largest}. {@code middle} is removed (it is contained in {@code largest}, which is
     * still in {@code result} at that point), and {@code smallest} must still be excluded even
     * though its immediate container ({@code middle}) never made it into {@code result} -- proving
     * the containment check is evaluated against {@code result} (already-kept entities), not the
     * original candidate list, and that transitively-contained spans are still correctly excluded.
     */
    @Test
    void removeSubstringsExcludesATransitivelyContainedSpanEvenThoughItsImmediateContainerWasRemoved()
            throws Exception {
        TaggedEntity largest = entity(0, 30, "chebi");
        TaggedEntity middle = entity(5, 25, "hp");
        TaggedEntity smallest = entity(10, 20, "efo");

        List<TaggedEntity> result =
                removeSubstrings(service, new ArrayList<>(List.of(smallest, middle, largest)));

        assertThat(result).containsExactly(largest);
    }

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------

    private static TaggedEntity entity(int start, int end, String ontologyId) {
        return new TaggedEntity(start, end, "label", "http://example.org/iri", ontologyId, null, null, null, false);
    }

    private static TaggedEntity entityWithSource(int start, int end, String ontologyId, String source) {
        return new TaggedEntity(
                start, end, "label", "http://example.org/iri", ontologyId, null, source, null, false);
    }

    // ------------------------------------------------------------------
    // Reflection helpers (all methods under test are private)
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<TaggedEntity> parseResponse(TextTaggerService svc, String json) throws Exception {
        Method m = TextTaggerService.class.getDeclaredMethod("parseResponse", String.class);
        m.setAccessible(true);
        return (List<TaggedEntity>) invoke(m, svc, json);
    }

    @SuppressWarnings("unchecked")
    private static List<String> jsonArrayToStringList(TextTaggerService svc, JsonObject obj, String key)
            throws Exception {
        Method m = TextTaggerService.class.getDeclaredMethod("jsonArrayToStringList", JsonObject.class, String.class);
        m.setAccessible(true);
        return (List<String>) invoke(m, svc, obj, key);
    }

    @SuppressWarnings("unchecked")
    private static List<TaggedEntity> applyPriority(
            TextTaggerService svc, List<TaggedEntity> entities, List<String> priorityOntologyIds) throws Exception {
        Method m = TextTaggerService.class.getDeclaredMethod("applyPriority", List.class, List.class);
        m.setAccessible(true);
        return (List<TaggedEntity>) invoke(m, svc, entities, priorityOntologyIds);
    }

    private static long spanKey(int start, int end) throws Exception {
        Method m = TextTaggerService.class.getDeclaredMethod("spanKey", int.class, int.class);
        m.setAccessible(true);
        return (Long) invoke(m, null, start, end);
    }

    @SuppressWarnings("unchecked")
    private static List<TaggedEntity> applySourceFilter(
            TextTaggerService svc, List<TaggedEntity> entities, List<String> sources) throws Exception {
        Method m = TextTaggerService.class.getDeclaredMethod("applySourceFilter", List.class, List.class);
        m.setAccessible(true);
        return (List<TaggedEntity>) invoke(m, svc, entities, sources);
    }

    @SuppressWarnings("unchecked")
    private static List<TaggedEntity> applyMinLength(TextTaggerService svc, List<TaggedEntity> entities, int minLength)
            throws Exception {
        Method m = TextTaggerService.class.getDeclaredMethod("applyMinLength", List.class, int.class);
        m.setAccessible(true);
        return (List<TaggedEntity>) invoke(m, svc, entities, minLength);
    }

    @SuppressWarnings("unchecked")
    private static List<TaggedEntity> removeSubstrings(TextTaggerService svc, List<TaggedEntity> entities)
            throws Exception {
        Method m = TextTaggerService.class.getDeclaredMethod("removeSubstrings", List.class);
        m.setAccessible(true);
        return (List<TaggedEntity>) invoke(m, svc, entities);
    }

    /**
     * Invokes {@code method} and unwraps {@link InvocationTargetException} so callers (and
     * AssertJ's {@code assertThatThrownBy}) see the real exception the private method under test
     * threw (e.g. {@link NullPointerException}), not a reflection wrapper.
     */
    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Exception ex) {
                throw ex;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw e;
        }
    }
}
