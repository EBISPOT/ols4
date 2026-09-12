package uk.ac.ebi.spot.ols.repository.v1.mappers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit coverage for {@link ShortFormExtractor}. This is a pure static-method utility class
 * with no Spring bean and no Postgres dependency -- see docs/backend-testing-strategy.md's
 * "Implemented ShortFormExtractor baseline" section for why no *IT layer applies here.
 *
 * <p>{@code extractShortForm} has exactly two branches: a {@code urn:} scheme special case
 * (case-sensitive {@code startsWith}, strips exactly 4 characters), and a generic fallback that
 * takes everything after {@code Math.max(lastIndexOf('#'), lastIndexOf('/'))}. Several fixtures
 * below use real IRI shapes pulled from this codebase's own fixtures/test data (found via
 * {@code grep} over {@code backend/src/test/resources} and {@code testcases_expected_output_api})
 * rather than only synthetic strings, per the Tier B methodology's "ground fixtures in real data"
 * guidance -- each such case says so in its comment.
 */
class ShortFormExtractorTest {

    // --- urn: scheme special case -----------------------------------------------------------

    @Test
    void urnSchemeStripsExactlyFourCharacterPrefix() {
        // Real-shaped: "urn:oid:..." is the canonical NID:NSS example from the W3C URN reference
        // this method's own comment links to (https://www.w3.org/Addressing/URL/URI_URN.html).
        String result = ShortFormExtractor.extractShortForm("urn:oid:1.2.3.4");

        assertThat(result).isEqualTo("oid:1.2.3.4");
    }

    @Test
    void urnSchemeWithNothingAfterItReturnsEmptyStringNotException() {
        String result = ShortFormExtractor.extractShortForm("urn:");

        assertThat(result).isEmpty();
    }

    @Test
    void uppercaseUrnPrefixDoesNotMatchCaseSensitiveCheckAndFallsThroughToGenericLogic() {
        // "URN:" must NOT hit the startsWith("urn:") branch (case-sensitive). This particular
        // value then has neither '#' nor '/', so it falls into the "return whole string" edge
        // case below -- confirming the generic path really runs, not just that it doesn't throw.
        String result = ShortFormExtractor.extractShortForm("URN:1.2.3.4");

        assertThat(result).isEqualTo("URN:1.2.3.4");
    }

    // --- generic #/ fallback: only one kind of separator present -----------------------------

    @Test
    void iriWithOnlyFragmentReturnsEverythingAfterHash() {
        // Real: OWL's own namespace IRI for owl:Thing, used verbatim in
        // backend/src/main/java/.../config/OntologyDefaults.java and OLS's committed
        // testcases_expected_output_api fixtures.
        String result = ShortFormExtractor.extractShortForm("http://www.w3.org/2002/07/owl#Thing");

        assertThat(result).isEqualTo("Thing");
    }

    @Test
    void iriWithOnlySlashReturnsEverythingAfterLastSlash() {
        // Real: a genuine OBO PURL term IRI shape found in backend/src/test/resources fixtures
        // (BFO_0000002 and siblings), representative of how OLS mints OBO ontology term IRIs.
        String result = ShortFormExtractor.extractShortForm("http://purl.obolibrary.org/obo/BFO_0000002");

        assertThat(result).isEqualTo("BFO_0000002");
    }

    // --- generic #/ fallback: both separators present, both Math.max directions -------------

    @Test
    void whenLastSlashComesAfterLastHashTheSlashWins() {
        // Real: found verbatim in this codebase's test fixtures/expected-output data -- an ICD-10
        // browse IRI where the fragment itself contains a further '/' segment
        // ("https://icd.who.int/browse10/2019/en#/N18"). lastIndexOf('/') > lastIndexOf('#') here,
        // so Math.max picks the slash and the result is everything after it.
        String result = ShortFormExtractor.extractShortForm("https://icd.who.int/browse10/2019/en#/N18");

        assertThat(result).isEqualTo("N18");
    }

    @Test
    void whenLastHashComesAfterLastSlashTheHashWins() {
        // Real: the oboInOwl "inSubset" predicate IRI, used verbatim elsewhere in this codebase
        // (AnnotationExtractor's hardcoded exclusion and its own test fixtures). lastIndexOf('#')
        // > lastIndexOf('/') here, so Math.max picks the hash.
        String result = ShortFormExtractor.extractShortForm("http://www.geneontology.org/formats/oboInOwl#inSubset");

        assertThat(result).isEqualTo("inSubset");
    }

    // --- generic #/ fallback: separator present but nothing after it ------------------------

    @Test
    void iriEndingInHashWithNothingAfterReturnsEmptyString() {
        String result = ShortFormExtractor.extractShortForm("http://example.org/onto#");

        assertThat(result).isEmpty();
    }

    @Test
    void iriEndingInSlashWithNothingAfterReturnsEmptyString() {
        String result = ShortFormExtractor.extractShortForm("http://purl.obolibrary.org/obo/");

        assertThat(result).isEmpty();
    }

    // --- generic #/ fallback: neither separator present at all -------------------------------

    @Test
    void iriWithNeitherHashNorSlashReturnsEntireOriginalStringUnchanged() {
        // Both lastIndexOf calls return -1; Math.max(-1, -1) == -1; +1 == 0; substring(0) returns
        // the *entire* original string, not an empty string or a thrown exception. Using a
        // CURIE-shaped value ("GO:0008150") here since this project already works with CURIEs
        // elsewhere (see the improve-curie-extraction branch work) -- a value like this has no
        // '#' or '/' at all and so is returned completely untouched by this method.
        String input = "GO:0008150";

        String result = ShortFormExtractor.extractShortForm(input);

        assertThat(result).isEqualTo(input);
    }

    @Test
    void emptyStringInputReturnsEmptyStringWithoutThrowing() {
        // Same all-(-1) logic path as the case above, degenerate at length 0: Math.max(-1, -1) + 1
        // == 0, and "".substring(0) is valid (returns "") rather than throwing.
        String result = ShortFormExtractor.extractShortForm("");

        assertThat(result).isEmpty();
    }
}
