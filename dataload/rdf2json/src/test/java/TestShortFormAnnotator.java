import org.junit.jupiter.api.Test;
import static uk.ac.ebi.rdf2json.annotators.ShortFormAnnotator.extractShortForm;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestShortFormAnnotator {
    static Set<String> uriPrefixes = new HashSet<>() {{
        add("http://purl.obolibrary.org/obo/TEST_");
    }};

    @Test
    public void testURN() {
        // Test slicing off front of URN
        assertEquals("garbage", extractShortForm(uriPrefixes, "TEST", "urn:garbage"));
    }

    @Test
    public void testOBODefaultNamespace() {
        // Test OBO default namespaces (added in https://github.com/EBISPOT/ols4/pull/937)
        assertEquals("obo:test#abc", extractShortForm(uriPrefixes, "TEST", "http://purl.obolibrary.org/obo/test#abc"));
    }

    @Test
    public void testRegular() {
        // Test regular parsing based, note that it uses PP instead of TEST
        assertEquals("PP_1234567", extractShortForm(uriPrefixes, "PP", "http://purl.obolibrary.org/obo/TEST_1234567"));
    }

    @Test
    public void testGuesses() {
        // Guesses
        assertEquals("1234567", extractShortForm(uriPrefixes, "PP", "http://example.org/1234567"));
        assertEquals("1234567", extractShortForm(uriPrefixes, "PP", "http://example.org/any.html#1234567"));
    }
}
