package uk.ac.ebi.spot.ols.repository.transforms;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalizationTransformTest {

    @Test
    void languageInvariantValuesDoNotPreventDefaultLiteralFallback() {
        JsonElement ontology = json("""
                {
                  "type": ["ontology"],
                  "contributor": [
                    {"type": ["literal"], "value": "Adrien Barton"},
                    "https://orcid.org/0000-0002-3410-4655",
                    {"type": ["literal"], "value": "Anoosha Sehar"}
                  ]
                }
                """);

        JsonElement expected = json("""
                {
                  "type": ["ontology"],
                  "contributor": [
                    {"type": ["literal"], "value": "Adrien Barton"},
                    "https://orcid.org/0000-0002-3410-4655",
                    {"type": ["literal"], "value": "Anoosha Sehar"}
                  ]
                }
                """);

        assertEquals(expected, LocalizationTransform.transform(ontology, "en"));
    }

    @Test
    void requestedLanguageStillTakesPrecedenceOverDefaultLiterals() {
        JsonElement ontology = json("""
                {
                  "type": ["ontology"],
                  "contributor": [
                    {"type": ["literal"], "value": "Default name"},
                    "https://orcid.org/0000-0002-3410-4655",
                    {"type": ["literal"], "lang": "fr", "value": "Nom francais"}
                  ]
                }
                """);

        JsonElement expected = json("""
                {
                  "type": ["ontology"],
                  "contributor": [
                    "https://orcid.org/0000-0002-3410-4655",
                    "Nom francais"
                  ]
                }
                """);

        assertEquals(expected, LocalizationTransform.transform(ontology, "fr"));
    }

    @Test
    void defaultLiteralsStillTakePrecedenceOverEnglishFallback() {
        JsonElement ontology = json("""
                {
                  "type": ["ontology"],
                  "contributor": [
                    {"type": ["literal"], "lang": "en", "value": "English name"},
                    "https://orcid.org/0000-0002-3410-4655",
                    {"type": ["literal"], "value": "Default name"}
                  ]
                }
                """);

        JsonElement expected = json("""
                {
                  "type": ["ontology"],
                  "contributor": [
                    "https://orcid.org/0000-0002-3410-4655",
                    {"type": ["literal"], "value": "Default name"}
                  ]
                }
                """);

        assertEquals(expected, LocalizationTransform.transform(ontology, "fr"));
    }

    @Test
    void englishFallbackIsUsedWhenRequestedAndDefaultLiteralsAreMissing() {
        JsonElement ontology = json("""
                {
                  "type": ["ontology"],
                  "contributor": [
                    {"type": ["literal"], "lang": "en", "value": "English name"},
                    "https://orcid.org/0000-0002-3410-4655"
                  ]
                }
                """);

        JsonElement expected = json("""
                {
                  "type": ["ontology"],
                  "contributor": [
                    "English name",
                    "https://orcid.org/0000-0002-3410-4655"
                  ]
                }
                """);

        assertEquals(expected, LocalizationTransform.transform(ontology, "fr"));
    }

    @Test
    void apiTransformReturnsMixedUriAndDefaultLiteralValues() {
        JsonElement ontology = json("""
                {
                  "type": ["ontology"],
                  "contributor": [
                    "https://orcid.org/0000-0002-3410-4655",
                    {"type": ["literal"], "value": "Adrien Barton"},
                    {"type": ["literal"], "value": "Anoosha Sehar"}
                  ]
                }
                """);

        JsonElement expected = json("""
                {
                  "type": ["ontology"],
                  "contributor": [
                    "https://orcid.org/0000-0002-3410-4655",
                    "Adrien Barton",
                    "Anoosha Sehar"
                  ]
                }
                """);

        assertEquals(
                expected,
                JsonTransformer.transformJson(ontology, "en", new JsonTransformOptions()));
    }

    @Test
    void allLanguagesStillBypassesLocalization() {
        JsonElement ontology = json("""
                {
                  "type": ["ontology"],
                  "contributor": [
                    {"type": ["literal"], "value": "Default name"},
                    "https://orcid.org/0000-0002-3410-4655",
                    {"type": ["literal"], "lang": "fr", "value": "Nom francais"}
                  ]
                }
                """);

        assertEquals(ontology, LocalizationTransform.transform(ontology, "all"));
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }
}
