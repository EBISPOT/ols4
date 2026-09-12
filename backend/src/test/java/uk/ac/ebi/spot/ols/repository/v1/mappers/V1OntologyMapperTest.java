package uk.ac.ebi.spot.ols.repository.v1.mappers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import uk.ac.ebi.spot.ols.model.v1.V1Ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Direct unit coverage for {@link V1OntologyMapper}. This is a pure static-method mapper -- no
 * Spring bean, no constructor state, no Postgres dependency of its own -- called from
 * {@link uk.ac.ebi.spot.ols.repository.v1.V1OntologyRepository} to turn one ontology's raw
 * rdf2json/linker JSON into the {@code V1Ontology}/{@code V1OntologyConfig} pair served by the V1
 * API. Per the Tier B methodology's "what covered means" section, a pure-logic class with no
 * Postgres dependency of its own does not get a dedicated {@code *IT.java} layer -- there is no
 * real-database behaviour here beyond what a direct unit test with hand-built {@link JsonObject}
 * fixtures already proves. See docs/backend-testing-strategy.md's "Implemented V1OntologyMapper
 * baseline" section for why no IT layer applies here, and for the two investigation findings
 * documented below.
 *
 * <p>Fixtures are modelled on the real rdf2json/linker output shape committed at
 * {@code testcases_expected_output/defined-fields/BaseUri/ontologies_linked.json}: a top-level
 * {@code "type": ["ontology"]} array (required for {@code LocalizationTransform} to actually
 * localize the object instead of passing it through unchanged -- see the {@code
 * localizationIsWiredIn...} test below), plain-string values for config-sourced fields, and the
 * {@code http://www.w3.org/2002/07/owl#...} predicate URIs used verbatim as JSON keys.
 */
class V1OntologyMapperTest {

    /**
     * A minimal-but-complete ontology JSON: every field {@link V1OntologyMapper#mapOntology}
     * unconditionally dereferences (the three {@code numberOfClasses}/{@code numberOfProperties}/
     * {@code numberOfIndividuals} counts, in particular -- see the unguarded-parseInt
     * investigation) is present, so this fixture alone drives the mapper's full straight-line
     * body without throwing. Individual tests copy this and delete/override exactly the key(s)
     * under investigation.
     */
    private static JsonObject baseOntologyJson() {
        JsonObject json = new JsonObject();

        JsonArray type = new JsonArray();
        type.add("ontology");
        json.add("type", type);

        json.addProperty("ontologyId", "test-onto");
        json.addProperty("fileHash", "abc123");

        JsonArray language = new JsonArray();
        language.add("en");
        json.add("language", language);

        json.addProperty("http://www.w3.org/2002/07/owl#versionIRI", "http://example.org/v1");
        json.addProperty("http://www.w3.org/2002/07/owl#versionInfo", "owl-version-1.0");
        json.addProperty("preferredPrefix", "TEST");
        json.addProperty("title", "Test Title");
        json.addProperty("description", "Test Description");
        json.addProperty("homepage", "http://example.org/home");
        json.addProperty("version", "generic-version-999");
        json.addProperty("mailingList", "list@example.org");
        json.addProperty("tracker", "http://example.org/tracker");
        json.addProperty("logo", "http://example.org/logo.png");

        JsonArray creators = new JsonArray();
        creators.add("Alice");
        creators.add("Bob");
        json.add("creators", creators);

        JsonObject annotations = new JsonObject();
        JsonArray annoValues = new JsonArray();
        annoValues.add("bar");
        annotations.add("foo", annoValues);
        json.add("annotations", annotations);

        json.addProperty("ontologyPurl", "http://example.org/purl.owl");

        JsonArray definitionProperty = new JsonArray();
        definitionProperty.add("http://example.org/definition");
        json.add("definition_property", definitionProperty);

        JsonArray synonymProperty = new JsonArray();
        synonymProperty.add("http://example.org/synonym");
        json.add("synonym_property", synonymProperty);

        JsonArray hierarchicalProperty = new JsonArray();
        hierarchicalProperty.add("http://example.org/hierarchical");
        json.add("hierarchical_property", hierarchicalProperty);

        JsonArray baseUri = new JsonArray();
        baseUri.add("http://example.org/TEST_");
        json.add("baseUri", baseUri);

        JsonArray hiddenProperty = new JsonArray();
        hiddenProperty.add("http://example.org/hidden");
        json.add("hidden_property", hiddenProperty);

        JsonArray preferredRootTerms = new JsonArray();
        preferredRootTerms.add("http://example.org/TEST_0000001");
        json.add("preferredRootTerms", preferredRootTerms);

        json.addProperty("numberOfClasses", "5");
        json.addProperty("numberOfProperties", "2");
        json.addProperty("numberOfIndividuals", "1");

        json.addProperty("loaded", "2026-01-01T00:00:00Z");

        return json;
    }

    // --- straight-line field mapping ---------------------------------------------------------

    @Test
    void mapsCoreOntologyAndConfigFields() {
        JsonObject json = baseOntologyJson();

        V1Ontology ontology = V1OntologyMapper.mapOntology(json, "en");

        assertThat(ontology.lang).isEqualTo("en");
        assertThat(ontology.ontologyId).isEqualTo("test-onto");
        assertThat(ontology.fileHash).isEqualTo("abc123");
        assertThat(ontology.languages).containsExactly("en");
        assertThat(ontology.status).isEqualTo("LOADED");
        assertThat(ontology.message).isEqualTo("");
        assertThat(ontology.loadAttempts).isZero();
        assertThat(ontology.loaded).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(ontology.updated).isEqualTo("2026-01-01T00:00:00Z");

        // numberOfTerms is read from the "numberOfClasses" JSON key -- a V1-API naming quirk
        // (classes are called "terms" throughout the V1 surface), not a bug.
        assertThat(ontology.numberOfTerms).isEqualTo(5);
        assertThat(ontology.numberOfProperties).isEqualTo(2);
        assertThat(ontology.numberOfIndividuals).isEqualTo(1);

        assertThat(ontology.config.id).isEqualTo("test-onto");
        assertThat(ontology.config.versionIri).isEqualTo("http://example.org/v1");
        assertThat(ontology.config.preferredPrefix).isEqualTo("TEST");
        assertThat(ontology.config.title).isEqualTo("Test Title");
        assertThat(ontology.config.description).isEqualTo("Test Description");
        assertThat(ontology.config.homepage).isEqualTo("http://example.org/home");
        assertThat(ontology.config.mailingList).isEqualTo("list@example.org");
        assertThat(ontology.config.tracker).isEqualTo("http://example.org/tracker");
        assertThat(ontology.config.logo).isEqualTo("http://example.org/logo.png");
        assertThat(ontology.config.creators).containsExactly("Alice", "Bob");
        assertThat(ontology.config.fileLocation).isEqualTo("http://example.org/purl.owl");
        assertThat(ontology.config.definitionProperties).containsExactly("http://example.org/definition");
        assertThat(ontology.config.synonymProperties).containsExactly("http://example.org/synonym");
        assertThat(ontology.config.hierarchicalProperties).containsExactly("http://example.org/hierarchical");
        assertThat(ontology.config.baseUris).containsExactly("http://example.org/TEST_");
        assertThat(ontology.config.hiddenProperties).containsExactly("http://example.org/hidden");
        assertThat(ontology.config.preferredRootTerms).containsExactly("http://example.org/TEST_0000001");
    }

    @Test
    void namespaceIsAlwaysADuplicateOfId() {
        JsonObject json = baseOntologyJson();

        V1Ontology ontology = V1OntologyMapper.mapOntology(json, "en");

        assertThat(ontology.config.namespace)
                .isEqualTo(ontology.config.id)
                .isEqualTo("test-onto");
    }

    // --- investigation 1: "version" is written twice; owl#versionInfo always wins -----------

    /**
     * {@code config.version} is written twice: first from the generic {@code "version"} JSON key
     * (line ~45), then unconditionally overwritten at the end of the method from {@code
     * http://www.w3.org/2002/07/owl#versionInfo} (lines ~84-85), which also sets {@code
     * ontology.version}. The first write is therefore always discarded -- dead code, not a
     * conditional default.
     *
     * <p>Investigated for reachability before concluding this is harmless: the generic
     * {@code "version"} JSON key is populated by rdf2json's {@code OntologyGraph.write()} only as
     * an arbitrary pass-through of a per-ontology config file's own {@code version:} entry (see
     * {@code dataload/rdf2json/.../OntologyGraph.java}'s "everything else from the config is
     * stored as a normal property" loop) -- a mechanism that is real but, across every raw
     * rdf2json/linker fixture committed in this repo (the entire {@code testcases_expected_output/}
     * tree), is never actually exercised: no committed fixture's raw JSON ever carries a bare
     * {@code "version"} key. Every ontology's version in this codebase's test corpus comes from
     * OWL's own {@code owl:versionInfo} triple instead (see e.g.
     * {@code testcases_expected_output/defined-fields/BaseUri/ontologies_linked.json}, and the W3C
     * OWL vocabulary's own {@code $Date: 2009/11/15 10:54:12 $} versionInfo asserted in
     * {@code testcases_expected_output_api/ontologies.json}). The committed {@code test_api.sh}
     * golden file for that "owl" ontology also asserts {@code config.version} equal to the
     * owl#versionInfo value with no bare {@code "version"} key present in its raw input at all --
     * i.e. the current "owl#versionInfo always wins" behaviour is already the asserted regression
     * baseline, not an accidental side effect. Conclusion: documented dead code with no observed
     * real-world impact; not filed as a defect.
     */
    @Test
    void versionAlwaysComesFromOwlVersionInfo_genericVersionKeyIsDiscarded() {
        JsonObject json = baseOntologyJson();
        json.addProperty("version", "generic-version-999");
        json.addProperty("http://www.w3.org/2002/07/owl#versionInfo", "owl-version-1.0");

        V1Ontology ontology = V1OntologyMapper.mapOntology(json, "en");

        assertThat(ontology.version).isEqualTo("owl-version-1.0");
        assertThat(ontology.config.version).isEqualTo("owl-version-1.0");
        assertThat(ontology.config.version).isNotEqualTo("generic-version-999");
    }

    // --- investigation 2: unguarded Integer.parseInt on the three entity counts --------------

    @Test
    void numberOfClassesMissing_throwsNumberFormatException() {
        JsonObject json = baseOntologyJson();
        json.remove("numberOfClasses");

        assertThrows(NumberFormatException.class, () -> V1OntologyMapper.mapOntology(json, "en"));
    }

    @Test
    void numberOfPropertiesMissing_throwsNumberFormatException() {
        JsonObject json = baseOntologyJson();
        json.remove("numberOfProperties");

        assertThrows(NumberFormatException.class, () -> V1OntologyMapper.mapOntology(json, "en"));
    }

    @Test
    void numberOfIndividualsMissing_throwsNumberFormatException() {
        JsonObject json = baseOntologyJson();
        json.remove("numberOfIndividuals");

        assertThrows(NumberFormatException.class, () -> V1OntologyMapper.mapOntology(json, "en"));
    }

    // --- investigation 3: Gson.fromJson(JsonElement, Class) with a raw-null element ----------

    @Test
    void annotationsMissing_gsonFromJsonToleratesNullElementAndReturnsNull() {
        JsonObject json = baseOntologyJson();
        json.remove("annotations");

        // localizedJson.get("annotations") is a raw Java null here (not JsonNull), because Gson's
        // JsonObject#get returns null for an absent key. Confirmed empirically: Gson.fromJson
        // does NOT throw for a null JsonElement argument -- it has its own explicit null check
        // and returns null gracefully.
        V1Ontology ontology = V1OntologyMapper.mapOntology(json, "en");

        assertThat(ontology.config.annotations).isNull();
    }

    @Test
    void annotationsPresent_isParsedAsMap() {
        JsonObject json = baseOntologyJson();

        V1Ontology ontology = V1OntologyMapper.mapOntology(json, "en");

        assertThat(ontology.config.annotations).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> annotations = (java.util.Map<String, Object>) ontology.config.annotations;
        assertThat(annotations).containsKey("foo");
    }

    // --- investigation 4: the three has(key) && getAsBoolean() flags ------------------------

    @Test
    void booleanFlagsDefaultToFalse_whenKeysAbsent() {
        JsonObject json = baseOntologyJson();
        // baseOntologyJson() never sets oboSlims/isSkos/allowDownload -- exercises the
        // short-circuit "key absent" branch for all three flags at once.

        V1Ontology ontology = V1OntologyMapper.mapOntology(json, "en");

        assertThat(ontology.config.oboSlims).isFalse();
        assertThat(ontology.config.isSkos).isFalse();
        assertThat(ontology.config.allowDownload).isFalse();
    }

    @Test
    void booleanFlagsPassThroughTrue_whenKeysPresentAsTrue() {
        JsonObject json = baseOntologyJson();
        json.addProperty("oboSlims", true);
        json.addProperty("isSkos", true);
        json.addProperty("allowDownload", true);

        V1Ontology ontology = V1OntologyMapper.mapOntology(json, "en");

        assertThat(ontology.config.oboSlims).isTrue();
        assertThat(ontology.config.isSkos).isTrue();
        assertThat(ontology.config.allowDownload).isTrue();
    }

    @Test
    void booleanFlagsPassThroughFalse_whenKeysPresentAsFalse() {
        JsonObject json = baseOntologyJson();
        json.addProperty("oboSlims", false);
        json.addProperty("isSkos", false);
        json.addProperty("allowDownload", false);

        V1Ontology ontology = V1OntologyMapper.mapOntology(json, "en");

        assertThat(ontology.config.oboSlims).isFalse();
        assertThat(ontology.config.isSkos).isFalse();
        assertThat(ontology.config.allowDownload).isFalse();
    }

    // --- investigation 5: labelProperty default ----------------------------------------------

    @Test
    void labelPropertyDefaultsToRdfsLabel_whenAbsent() {
        JsonObject json = baseOntologyJson();
        // label_property is never set by baseOntologyJson()

        V1Ontology ontology = V1OntologyMapper.mapOntology(json, "en");

        assertThat(ontology.config.labelProperty).isEqualTo("http://www.w3.org/2000/01/rdf-schema#label");
    }

    @Test
    void labelPropertyUsesProvidedValue_whenPresent() {
        JsonObject json = baseOntologyJson();
        json.addProperty("label_property", "http://example.org/customLabel");

        V1Ontology ontology = V1OntologyMapper.mapOntology(json, "en");

        assertThat(ontology.config.labelProperty).isEqualTo("http://example.org/customLabel");
    }

    // --- investigation 6: Dublin Core embedded-metadata override -----------------------------

    @Test
    void dcTitleAndDescriptionBothPresent_overrideBothPlainKeys() {
        JsonObject json = baseOntologyJson();
        json.addProperty("http://purl.org/dc/elements/1.1/title", "DC Title");
        json.addProperty("http://purl.org/dc/elements/1.1/description", "DC Description");

        V1Ontology ontology = V1OntologyMapper.mapOntology(json, "en");

        assertThat(ontology.config.title).isEqualTo("DC Title");
        assertThat(ontology.config.description).isEqualTo("DC Description");
    }

    @Test
    void dcTitleOnlyPresent_overridesTitleButLeavesDescriptionAlone() {
        JsonObject json = baseOntologyJson();
        json.addProperty("http://purl.org/dc/elements/1.1/title", "DC Title");

        V1Ontology ontology = V1OntologyMapper.mapOntology(json, "en");

        assertThat(ontology.config.title).isEqualTo("DC Title");
        assertThat(ontology.config.description).isEqualTo("Test Description");
    }

    @Test
    void dcDescriptionOnlyPresent_overridesDescriptionButLeavesTitleAlone() {
        JsonObject json = baseOntologyJson();
        json.addProperty("http://purl.org/dc/elements/1.1/description", "DC Description");

        V1Ontology ontology = V1OntologyMapper.mapOntology(json, "en");

        assertThat(ontology.config.title).isEqualTo("Test Title");
        assertThat(ontology.config.description).isEqualTo("DC Description");
    }

    @Test
    void dcTitleAndDescriptionBothAbsent_keepsPlainKeyValues() {
        JsonObject json = baseOntologyJson();
        // neither DC key is set

        V1Ontology ontology = V1OntologyMapper.mapOntology(json, "en");

        assertThat(ontology.config.title).isEqualTo("Test Title");
        assertThat(ontology.config.description).isEqualTo("Test Description");
    }

    // --- investigation 8: LocalizationTransform is genuinely wired in, not bypassed ---------

    /**
     * {@code LocalizationTransform} itself has its own exhaustive dedicated test suite
     * elsewhere in this programme ({@code LocalizationTransformTest}) and is proven never to
     * return null, so this does not re-test its internals -- it only proves that
     * {@code V1OntologyMapper} genuinely applies it (rather than e.g. accidentally passing the
     * raw, unlocalized JSON straight to the field reads). A single field ("title") is given two
     * language-tagged literal values; requesting each language in turn must select the matching
     * one, which is only possible if localization actually ran.
     */
    @Test
    void localizationIsWiredIn_languageDependentTitleIsSelectedPerRequestedLang() {
        JsonObject json = baseOntologyJson();

        JsonArray localizedTitle = new JsonArray();

        JsonArray literalType = new JsonArray();
        literalType.add("literal");

        JsonObject englishTitle = new JsonObject();
        englishTitle.add("type", literalType);
        englishTitle.addProperty("lang", "en");
        englishTitle.addProperty("value", "English Title");
        localizedTitle.add(englishTitle);

        JsonObject frenchTitle = new JsonObject();
        frenchTitle.add("type", literalType);
        frenchTitle.addProperty("lang", "fr");
        frenchTitle.addProperty("value", "Titre Francais");
        localizedTitle.add(frenchTitle);

        json.add("title", localizedTitle);

        V1Ontology englishOntology = V1OntologyMapper.mapOntology(json, "en");
        V1Ontology frenchOntology = V1OntologyMapper.mapOntology(json, "fr");

        assertThat(englishOntology.config.title).isEqualTo("English Title");
        assertThat(frenchOntology.config.title).isEqualTo("Titre Francais");
    }
}
