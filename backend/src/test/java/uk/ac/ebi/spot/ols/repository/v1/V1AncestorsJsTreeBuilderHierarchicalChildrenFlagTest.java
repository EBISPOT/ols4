package uk.ac.ebi.spot.ols.repository.v1;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated regression test for a defect discovered while adding full test coverage for
 * {@link V1AncestorsJsTreeBuilder} (see {@code docs/backend-testing-strategy.md}, "Implemented
 * V1AncestorsJsTreeBuilder baseline"): {@code createJsTreeEntries} computed both
 * {@code hasDirectChildren} and {@code hasHierarchicalChildren} by reading the
 * {@code HAS_DIRECT_CHILDREN} field twice, instead of reading {@code HAS_HIERARCHICAL_CHILDREN}
 * for the second flag. Its sibling {@link V1ChildrenJsTreeBuilder} (line 40-41) and
 * {@code V1TermMapper} (line 52-53) both correctly OR the two distinct fields together; this
 * class was the outlier.
 *
 * <p>{@code hasDirectChildren} and {@code hasHierarchicalChildren} are populated independently by
 * {@code HierarchyFlagsAnnotator} in {@code dataload/rdf2json} from two different relation types
 * (subClassOf-derived direct parents vs. hierarchical/part-of-style parents), so an entity with
 * {@code hasDirectChildren=false} and {@code hasHierarchicalChildren=true} is real, reachable
 * ontology data - not a hypothetical edge case. Before this fix, such an entity incorrectly
 * reported {@code children: false} (not expandable) in the V1 ancestors jstree response, when it
 * should report {@code children: true}.
 */
class V1AncestorsJsTreeBuilderHierarchicalChildrenFlagTest {

    @Test
    void reportsChildrenTrueWhenOnlyHasHierarchicalChildrenIsSet() {
        JsonElement thisEntity = JsonParser.parseString("""
                {"iri":"http://example.org/this","label":"This",
                 "hasDirectChildren":"false","hasHierarchicalChildren":"true"}
                """);

        V1AncestorsJsTreeBuilder builder = new V1AncestorsJsTreeBuilder(
                thisEntity, List.of(), List.of("http://example.org/relations/parent"));

        List<Map<String, Object>> tree = builder.buildJsTree();

        assertThat(tree).singleElement().satisfies(entry -> {
            assertThat(entry).containsEntry("iri", "http://example.org/this");
            assertThat(entry).containsEntry("children", true);
        });
    }

    @Test
    void reportsChildrenFalseWhenNeitherFlagIsSet() {
        JsonElement thisEntity = JsonParser.parseString("""
                {"iri":"http://example.org/this","label":"This",
                 "hasDirectChildren":"false","hasHierarchicalChildren":"false"}
                """);

        V1AncestorsJsTreeBuilder builder = new V1AncestorsJsTreeBuilder(
                thisEntity, List.of(), List.of("http://example.org/relations/parent"));

        List<Map<String, Object>> tree = builder.buildJsTree();

        assertThat(tree).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("children", false));
    }

    @Test
    void reportsChildrenTrueWhenOnlyHasDirectChildrenIsSet() {
        JsonObject thisEntityJson = JsonParser.parseString("""
                {"iri":"http://example.org/this","label":"This",
                 "hasDirectChildren":"true","hasHierarchicalChildren":"false"}
                """).getAsJsonObject();

        V1AncestorsJsTreeBuilder builder = new V1AncestorsJsTreeBuilder(
                thisEntityJson, List.of(), List.of("http://example.org/relations/parent"));

        List<Map<String, Object>> tree = builder.buildJsTree();

        assertThat(tree).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("children", true));
    }
}
