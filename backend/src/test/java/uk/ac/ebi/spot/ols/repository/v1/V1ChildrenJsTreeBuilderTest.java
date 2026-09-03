package uk.ac.ebi.spot.ols.repository.v1;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class V1ChildrenJsTreeBuilderTest {

    @Test
    void treatsAMissingHasChildrenFlagAsFalseInsteadOfThrowing() {
        JsonObject parent = JsonParser.parseString(
                "{\"iri\":\"http://example.org/parent\"}").getAsJsonObject();
        JsonObject childWithoutHasChildrenFlags = JsonParser.parseString("""
                {"iri":"http://example.org/child","label":"Child"}
                """).getAsJsonObject();

        V1ChildrenJsTreeBuilder builder = new V1ChildrenJsTreeBuilder(
                base64("http://example.org/parent"), parent, List.of(childWithoutHasChildrenFlags));

        List<Map<String, Object>> tree = builder.buildJsTree();

        assertThat(tree).singleElement().satisfies(entry -> {
            assertThat(entry).containsEntry("iri", "http://example.org/child");
            assertThat(entry).containsEntry("children", false);
        });
    }

    @Test
    void reportsHasChildrenTrueWhenTheFlagIsSet() {
        JsonObject parent = JsonParser.parseString(
                "{\"iri\":\"http://example.org/parent\"}").getAsJsonObject();
        JsonObject childWithChildren = JsonParser.parseString("""
                {"iri":"http://example.org/child","label":"Child","hasDirectChildren":"true"}
                """).getAsJsonObject();

        V1ChildrenJsTreeBuilder builder = new V1ChildrenJsTreeBuilder(
                base64("http://example.org/parent"), parent, List.of(childWithChildren));

        List<Map<String, Object>> tree = builder.buildJsTree();

        assertThat(tree).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("children", true));
    }

    private static String base64(String value) {
        return java.util.Base64.getEncoder().encodeToString(
                value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
