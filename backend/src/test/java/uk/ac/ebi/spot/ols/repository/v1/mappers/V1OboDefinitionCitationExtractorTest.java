package uk.ac.ebi.spot.ols.repository.v1.mappers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import uk.ac.ebi.spot.ols.model.v1.V1OboDefinitionCitation;
import uk.ac.ebi.spot.ols.model.v1.V1OboXref;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct unit coverage for {@link V1OboDefinitionCitationExtractor}. This is a pure static-method
 * utility class -- no Spring bean, no constructor state, no Postgres dependency -- with one public
 * method, {@code extractFromJson(JsonObject)}, called only from {@link V1TermMapper}. See
 * docs/backend-testing-strategy.md's "Implemented V1OboDefinitionCitationExtractor baseline"
 * section for why no *IT layer applies here, and for the investigation into the unguarded
 * "linkedEntities" access documented on the first test below.
 */
class V1OboDefinitionCitationExtractorTest {

    private static final String HAS_DB_XREF = "http://www.geneontology.org/formats/oboInOwl#hasDbXref";

    /**
     * Every real caller (V1TermMapper, fed by the OLS4 linker) always supplies a "linkedEntities"
     * object, so every other fixture in this file includes it too, matching production input.
     */
    private static JsonObject baseJson() {
        JsonObject json = new JsonObject();
        json.add("linkedEntities", new JsonObject());
        return json;
    }

    private static JsonObject definitionObject(String text) {
        JsonObject def = new JsonObject();
        def.addProperty("value", text);
        return def;
    }

    private static JsonObject axiomWithXrefValues(String... xrefValues) {
        JsonObject axiom = new JsonObject();
        if (xrefValues.length == 1) {
            JsonObject xref = new JsonObject();
            xref.addProperty("value", xrefValues[0]);
            axiom.add(HAS_DB_XREF, xref);
        } else if (xrefValues.length > 1) {
            JsonArray xrefs = new JsonArray();
            for (String value : xrefValues) {
                JsonObject xref = new JsonObject();
                xref.addProperty("value", value);
                xrefs.add(xref);
            }
            axiom.add(HAS_DB_XREF, xrefs);
        }
        return axiom;
    }

    // --- the unguarded "linkedEntities" access: empirically confirmed, then investigated --------

    /**
     * extractFromJson dereferences "linkedEntities" unconditionally with no null check
     * ({@code json.get("linkedEntities").getAsJsonObject()}) -- if that key is genuinely absent
     * this throws NullPointerException. Confirmed here empirically.
     *
     * <p>Investigation into reachability (see the baseline doc section for the full trail): the
     * only production caller is {@code V1TermMapper.mapTerm}, which is only ever invoked on
     * top-level entity JSON objects (from {@code classes}/{@code individuals}/{@code properties}
     * arrays) produced by the OLS4 linker's {@code write_entity_array}
     * (dataload/linker/link/src/linker_pass2.rs) -- that function unconditionally writes a
     * "linkedEntities" key for every entity it processes, with no conditional guard, before
     * ever returning. Cross-checking every one of the 985 top-level class/individual/property
     * entities across all 111 committed {@code ontologies_linked.json} golden fixtures under
     * {@code testcases_expected_output/} confirms zero exceptions: every one carries the key.
     * (The only entities in those fixtures observed lacking "linkedEntities" are anonymous nested
     * class-expression fragments -- e.g. inline datatype restrictions embedded inside a property's
     * "range" -- which are copied verbatim by a separate, non-entity code path and are never
     * independently passed to {@code extractFromJson} as its top-level argument.)
     *
     * <p>Conclusion: this is a genuine defensive gap in the production code, but it is confirmed
     * unreachable given the current pipeline's invariant that every top-level entity always
     * carries "linkedEntities" -- not a live defect. No separate bug-fix PR is warranted per the
     * defect workflow; documented here and in the baseline doc section instead.
     */
    @Test
    void missingLinkedEntitiesKeyThrowsNullPointerException() {
        JsonObject json = new JsonObject(); // deliberately no "linkedEntities" key
        JsonArray definitions = new JsonArray();
        definitions.add(definitionObject("a definition"));
        json.add("definition", definitions);

        assertThatThrownBy(() -> V1OboDefinitionCitationExtractor.extractFromJson(json))
                .isInstanceOf(NullPointerException.class);
    }

    // --- definitions: non-JsonObject elements are silently skipped -----------------------------

    @Test
    void definitionElementThatIsNotJsonObjectIsSilentlySkipped() {
        JsonObject json = baseJson();
        JsonArray definitions = new JsonArray();
        definitions.add("a plain string definition, not an object");
        json.add("definition", definitions);

        List<V1OboDefinitionCitation> result = V1OboDefinitionCitationExtractor.extractFromJson(json);

        assertThat(result).isNull();
    }

    // --- definitions with no "axioms" key, or an empty axioms list ------------------------------

    @Test
    void definitionWithNoAxiomsKeyProducesNoCitationsAndDoesNotCrash() {
        JsonObject json = baseJson();
        JsonArray definitions = new JsonArray();
        definitions.add(definitionObject("a definition with no axioms field at all"));
        json.add("definition", definitions);

        List<V1OboDefinitionCitation> result = V1OboDefinitionCitationExtractor.extractFromJson(json);

        assertThat(result).isNull();
    }

    @Test
    void definitionWithEmptyAxiomsListProducesNoCitationsAndDoesNotCrash() {
        JsonObject json = baseJson();
        JsonObject def = definitionObject("a definition with an empty axioms array");
        def.add("axioms", new JsonArray());
        JsonArray definitions = new JsonArray();
        definitions.add(def);
        json.add("definition", definitions);

        List<V1OboDefinitionCitation> result = V1OboDefinitionCitationExtractor.extractFromJson(json);

        assertThat(result).isNull();
    }

    // --- an axiom that exists but has zero matching xref values ---------------------------------

    @Test
    void axiomWithNoMatchingXrefValuesIsSkipped() {
        JsonObject json = baseJson();
        JsonObject axiom = new JsonObject();
        axiom.addProperty("http://www.geneontology.org/formats/oboInOwl#someOtherPredicate", "irrelevant");

        JsonObject def = definitionObject("a definition whose only axiom has no hasDbXref");
        JsonArray axioms = new JsonArray();
        axioms.add(axiom);
        def.add("axioms", axioms);

        JsonArray definitions = new JsonArray();
        definitions.add(def);
        json.add("definition", definitions);

        List<V1OboDefinitionCitation> result = V1OboDefinitionCitationExtractor.extractFromJson(json);

        assertThat(result).isNull();
    }

    // --- an axiom with one or more matching xref values produces exactly one citation -----------

    @Test
    void axiomWithOneMatchingXrefProducesExactlyOneCitation() {
        JsonObject json = baseJson();
        JsonObject def = definitionObject("liver disease");
        JsonArray axioms = new JsonArray();
        axioms.add(axiomWithXrefValues("OGMS:0000031"));
        def.add("axioms", axioms);

        JsonArray definitions = new JsonArray();
        definitions.add(def);
        json.add("definition", definitions);

        List<V1OboDefinitionCitation> result = V1OboDefinitionCitationExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        V1OboDefinitionCitation citation = result.get(0);
        assertThat(citation.definition).isEqualTo("liver disease");
        assertThat(citation.oboXrefs).hasSize(1);
        assertThat(citation.oboXrefs.get(0).database).isEqualTo("OGMS");
        assertThat(citation.oboXrefs.get(0).id).isEqualTo("0000031");
    }

    /**
     * One definition with three axioms: only the 1st and 3rd have matching xrefs. The result must
     * contain exactly one citation per qualifying axiom (not one per definition), in the same
     * order the axioms appear, and the skipped axiom must contribute nothing.
     */
    @Test
    void onlyAxiomsWithMatchingXrefsProduceCitations_oneCitationPerQualifyingAxiomInOrder() {
        JsonObject json = baseJson();
        JsonObject def = definitionObject("a definition with mixed axioms");

        JsonArray axioms = new JsonArray();
        axioms.add(axiomWithXrefValues("OGMS:0000031"));       // qualifies
        axioms.add(axiomWithXrefValues());                     // no hasDbXref key -- skipped
        axioms.add(axiomWithXrefValues("NCIT:P378"));          // qualifies
        def.add("axioms", axioms);

        JsonArray definitions = new JsonArray();
        definitions.add(def);
        json.add("definition", definitions);

        List<V1OboDefinitionCitation> result = V1OboDefinitionCitationExtractor.extractFromJson(json);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).definition).isEqualTo("a definition with mixed axioms");
        assertThat(result.get(0).oboXrefs).extracting(x -> x.id).containsExactly("0000031");
        assertThat(result.get(1).definition).isEqualTo("a definition with mixed axioms");
        assertThat(result.get(1).oboXrefs).extracting(x -> x.id).containsExactly("P378");
    }

    // --- multiple xref values on a single qualifying axiom ---------------------------------------

    /**
     * All xref values on one axiom are mapped through the real {@link V1OboXref#fromString}
     * parser and all appear, in order, in that one citation's oboXrefs list. Also demonstrates
     * that the "linkedEntities" object passed into extractFromJson is genuinely threaded through
     * to V1OboXref.fromString: the second xref resolves a "url" from a matching linkedEntities
     * entry, the first does not (no matching entry), proving both are real, independent lookups
     * rather than a hardcoded/faked value.
     */
    @Test
    void multipleXrefValuesOnOneAxiomAllAppearInOrderInSingleCitation() {
        JsonObject json = baseJson();
        JsonObject linkedEntity = new JsonObject();
        linkedEntity.addProperty("url", "http://example.org/ncit/P378");
        json.getAsJsonObject("linkedEntities").add("NCIT:P378", linkedEntity);

        JsonObject def = definitionObject("hypogonadism");
        JsonArray axioms = new JsonArray();
        axioms.add(axiomWithXrefValues("OGMS:0000031", "NCIT:P378"));
        def.add("axioms", axioms);

        JsonArray definitions = new JsonArray();
        definitions.add(def);
        json.add("definition", definitions);

        List<V1OboDefinitionCitation> result = V1OboDefinitionCitationExtractor.extractFromJson(json);

        assertThat(result).hasSize(1);
        List<V1OboXref> oboXrefs = result.get(0).oboXrefs;
        assertThat(oboXrefs).hasSize(2);

        assertThat(oboXrefs.get(0).database).isEqualTo("OGMS");
        assertThat(oboXrefs.get(0).id).isEqualTo("0000031");
        assertThat(oboXrefs.get(0).url).isNull();

        assertThat(oboXrefs.get(1).database).isEqualTo("NCIT");
        assertThat(oboXrefs.get(1).id).isEqualTo("P378");
        assertThat(oboXrefs.get(1).url).isEqualTo("http://example.org/ncit/P378");
    }

    // --- multiple definitions on one entity, each independently producing 0+ citations ----------

    @Test
    void multipleDefinitionsFlattenIntoOneCombinedResultInOrder() {
        JsonObject json = baseJson();

        JsonObject defWithNoCitation = definitionObject("a definition with no axioms");

        JsonObject defWithOneCitation = definitionObject("a definition with one qualifying axiom");
        JsonArray axiomsOne = new JsonArray();
        axiomsOne.add(axiomWithXrefValues("OGMS:0000031"));
        defWithOneCitation.add("axioms", axiomsOne);

        JsonObject defWithTwoCitations = definitionObject("a definition with two qualifying axioms");
        JsonArray axiomsTwo = new JsonArray();
        axiomsTwo.add(axiomWithXrefValues("NCIT:P378"));
        axiomsTwo.add(axiomWithXrefValues("NCIT:C28193"));
        defWithTwoCitations.add("axioms", axiomsTwo);

        JsonArray definitions = new JsonArray();
        definitions.add(defWithNoCitation);
        definitions.add(defWithOneCitation);
        definitions.add(defWithTwoCitations);
        json.add("definition", definitions);

        List<V1OboDefinitionCitation> result = V1OboDefinitionCitationExtractor.extractFromJson(json);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).definition).isEqualTo("a definition with one qualifying axiom");
        assertThat(result.get(0).oboXrefs).extracting(x -> x.id).containsExactly("0000031");
        assertThat(result.get(1).definition).isEqualTo("a definition with two qualifying axioms");
        assertThat(result.get(1).oboXrefs).extracting(x -> x.id).containsExactly("P378");
        assertThat(result.get(2).definition).isEqualTo("a definition with two qualifying axioms");
        assertThat(result.get(2).oboXrefs).extracting(x -> x.id).containsExactly("C28193");
    }

    // --- the res.size() == 0 -> null contract, and its non-null counterpart ---------------------

    @Test
    void returnsNullWhenNothingQualifiesAnywhere() {
        JsonObject json = baseJson();

        JsonArray definitions = new JsonArray();
        definitions.add("a plain string definition");                                  // skipped
        definitions.add(definitionObject("no axioms key"));                             // no citation
        JsonObject defWithEmptyAxioms = definitionObject("empty axioms list");
        defWithEmptyAxioms.add("axioms", new JsonArray());
        definitions.add(defWithEmptyAxioms);                                            // no citation
        json.add("definition", definitions);

        List<V1OboDefinitionCitation> result = V1OboDefinitionCitationExtractor.extractFromJson(json);

        assertThat(result).isNull();
    }

    @Test
    void returnsActualNonNullListWhenAtLeastOneCitationQualifies() {
        JsonObject json = baseJson();
        JsonObject def = definitionObject("a definition with one qualifying axiom");
        JsonArray axioms = new JsonArray();
        axioms.add(axiomWithXrefValues("OGMS:0000031"));
        def.add("axioms", axioms);

        JsonArray definitions = new JsonArray();
        definitions.add(def);
        json.add("definition", definitions);

        List<V1OboDefinitionCitation> result = V1OboDefinitionCitationExtractor.extractFromJson(json);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
    }
}
