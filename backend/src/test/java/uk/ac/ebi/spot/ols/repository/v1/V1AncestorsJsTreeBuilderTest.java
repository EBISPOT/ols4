package uk.ac.ebi.spot.ols.repository.v1;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.ebi.ols.shared.DefinedFields.DIRECT_PARENT;
import static uk.ac.ebi.ols.shared.DefinedFields.HIERARCHICAL_PARENT;

/**
 * Direct unit coverage for {@link V1AncestorsJsTreeBuilder}, this class's untested sibling of
 * {@link V1ChildrenJsTreeBuilder} (whose own dedicated test found and fixed a real NPE in
 * PR #1391). Follows {@code V1ChildrenJsTreeBuilderTest}'s idiom: the class and its methods are
 * package-private, tested from the same package with hand-built {@link JsonObject} fixtures, no
 * mocking framework, no Spring context, no Postgres.
 *
 * <p><b>Known defect documented, not silently accepted:</b> {@code createJsTreeEntries}
 * currently reads the {@code HAS_DIRECT_CHILDREN} field twice instead of reading
 * {@code HAS_HIERARCHICAL_CHILDREN} for the second flag, making the
 * {@code hasDirectChildren || hasHierarchicalChildren} OR a no-op. This is isolated into a
 * separate defect-fix PR (see {@code docs/backend-testing-strategy.md}, "Implemented
 * V1AncestorsJsTreeBuilder baseline") per this programme's defect workflow — this branch is
 * deliberately built against the unfixed code so it documents current behaviour precisely.
 * {@link #reportsChildrenFalseWhenOnlyHasHierarchicalChildrenIsSetDueToAKnownDefect()} asserts the
 * current (buggy) result and will need its expectation flipped to {@code true} once this branch
 * is rebased onto the merged fix.
 */
class V1AncestorsJsTreeBuilderTest {

    private static final String THING_IRI = "http://www.w3.org/2002/07/owl#Thing";
    private static final String TOP_OBJECT_PROPERTY_IRI = "http://www.w3.org/2002/07/owl#TopObjectProperty";

    // ------------------------------------------------------------------
    // Constructor / getEntityParentIRIs: multiple parent-relation IRIs
    // ------------------------------------------------------------------

    @Test
    void walksEveryConfiguredParentRelationIriNotJustTheFirst() {
        JsonObject root = entity("http://example.org/P", "P", null);
        // "child" is only linked via hierarchicalParent, never directParent.
        JsonObject child = JsonParser.parseString("""
                {"iri":"http://example.org/child","label":"Child",
                 "hierarchicalParent":["http://example.org/P"]}
                """).getAsJsonObject();

        V1AncestorsJsTreeBuilder builder = new V1AncestorsJsTreeBuilder(
                child, List.of(root),
                List.of(DIRECT_PARENT.getText(), HIERARCHICAL_PARENT.getText()));

        List<Map<String, Object>> tree = builder.buildJsTree();

        assertThat(tree).hasSize(2);
        Map<String, Object> rootEntry = entryWithIri(tree, "http://example.org/P");
        Map<String, Object> childEntry = entryWithIri(tree, "http://example.org/child");
        assertThat(rootEntry).containsEntry("parent", "#");
        assertThat(childEntry).containsEntry("parent", V1AncestorsJsTreeBuilder.base64Encode("http://example.org/P"));
    }

    // ------------------------------------------------------------------
    // getEntityParentIRIs: reified-parent unwrap loop
    // ------------------------------------------------------------------

    @Test
    void recognizesAPlainStringParentIri() {
        JsonObject parent = entity("http://example.org/P", "P", null);
        JsonObject child = JsonParser.parseString("""
                {"iri":"http://example.org/child","label":"Child",
                 "directParent":["http://example.org/P"]}
                """).getAsJsonObject();

        List<Map<String, Object>> tree = new V1AncestorsJsTreeBuilder(
                child, List.of(parent), List.of(DIRECT_PARENT.getText())).buildJsTree();

        assertThat(tree).hasSize(2);
        assertThat(entryWithIri(tree, "http://example.org/child"))
                .containsEntry("parent", V1AncestorsJsTreeBuilder.base64Encode("http://example.org/P"));
    }

    @Test
    void unwrapsASingleReifiedParentValue() {
        JsonObject parent = entity("http://example.org/P", "P", null);
        JsonObject child = JsonParser.parseString("""
                {"iri":"http://example.org/child","label":"Child",
                 "directParent":[{"value":"http://example.org/P"}]}
                """).getAsJsonObject();

        List<Map<String, Object>> tree = new V1AncestorsJsTreeBuilder(
                child, List.of(parent), List.of(DIRECT_PARENT.getText())).buildJsTree();

        assertThat(tree).hasSize(2);
        assertThat(entryWithIri(tree, "http://example.org/child"))
                .containsEntry("parent", V1AncestorsJsTreeBuilder.base64Encode("http://example.org/P"));
    }

    @Test
    void unwrapsADoublyReifiedParentValueProvingTheLoopNotJustAnIf() {
        JsonObject parent = entity("http://example.org/P", "P", null);
        JsonObject child = JsonParser.parseString("""
                {"iri":"http://example.org/child","label":"Child",
                 "directParent":[{"value":{"value":"http://example.org/P"}}]}
                """).getAsJsonObject();

        List<Map<String, Object>> tree = new V1AncestorsJsTreeBuilder(
                child, List.of(parent), List.of(DIRECT_PARENT.getText())).buildJsTree();

        assertThat(tree).hasSize(2);
        assertThat(entryWithIri(tree, "http://example.org/child"))
                .containsEntry("parent", V1AncestorsJsTreeBuilder.base64Encode("http://example.org/P"));
    }

    // ------------------------------------------------------------------
    // getEntityParentIRIs: owl:Thing / owl:TopObjectProperty exclusions
    // ------------------------------------------------------------------

    @Test
    void excludesOwlThingAsAParentSoTheEntityBecomesARoot() {
        JsonObject leaf = JsonParser.parseString("""
                {"iri":"http://example.org/leaf","label":"Leaf",
                 "directParent":["http://www.w3.org/2002/07/owl#Thing"]}
                """).getAsJsonObject();

        List<Map<String, Object>> tree = new V1AncestorsJsTreeBuilder(
                leaf, List.of(), List.of(DIRECT_PARENT.getText())).buildJsTree();

        assertThat(tree).singleElement().satisfies(entry -> {
            assertThat(entry).containsEntry("iri", "http://example.org/leaf");
            assertThat(entry).containsEntry("parent", "#");
        });
    }

    @Test
    void excludesOwlTopObjectPropertyAsAParentSoTheEntityBecomesARoot() {
        JsonObject leaf = JsonParser.parseString("""
                {"iri":"http://example.org/leaf","label":"Leaf",
                 "directParent":["http://www.w3.org/2002/07/owl#TopObjectProperty"]}
                """).getAsJsonObject();

        List<Map<String, Object>> tree = new V1AncestorsJsTreeBuilder(
                leaf, List.of(), List.of(DIRECT_PARENT.getText())).buildJsTree();

        assertThat(tree).singleElement().satisfies(entry -> {
            assertThat(entry).containsEntry("iri", "http://example.org/leaf");
            assertThat(entry).containsEntry("parent", "#");
        });
    }

    @Test
    void doesNotExcludeANormalParentIriEvenWhenOwlThingIsAlsoPresent() {
        JsonObject parent = entity("http://example.org/P", "P", null);
        JsonObject leaf = JsonParser.parseString("""
                {"iri":"http://example.org/leaf","label":"Leaf",
                 "directParent":["http://www.w3.org/2002/07/owl#Thing","http://example.org/P"]}
                """).getAsJsonObject();

        V1AncestorsJsTreeBuilder builder = new V1AncestorsJsTreeBuilder(
                leaf, List.of(parent), List.of(DIRECT_PARENT.getText()));

        // owl:Thing must never be recorded as a real parent-child relationship...
        assertThat(builder.entityIriToChildIris.get(THING_IRI)).isEmpty();
        // ...while the genuine parent P must still be recognized.
        assertThat(builder.entityIriToChildIris.get("http://example.org/P"))
                .containsExactly("http://example.org/leaf");

        List<Map<String, Object>> tree = builder.buildJsTree();
        assertThat(tree).hasSize(2);
        assertThat(entryWithIri(tree, "http://example.org/leaf"))
                .containsEntry("parent", V1AncestorsJsTreeBuilder.base64Encode("http://example.org/P"));
    }

    // ------------------------------------------------------------------
    // buildJsTree: root detection
    // ------------------------------------------------------------------

    @Test
    void identifiesASingleRootWhenExactlyOneEntityHasNoParents() {
        JsonObject root = entity("http://example.org/P", "P", null);
        JsonObject child = entityWithParent("http://example.org/child", "Child", "http://example.org/P");

        List<Map<String, Object>> tree = new V1AncestorsJsTreeBuilder(
                child, List.of(root), List.of(DIRECT_PARENT.getText())).buildJsTree();

        assertThat(tree).hasSize(2);
        assertThat(entryWithIri(tree, "http://example.org/P")).containsEntry("parent", "#");
    }

    @Test
    void identifiesMultipleRootsWhenThisEntityHasTwoIndependentParentlessParents() {
        JsonObject rootA = entity("http://example.org/A", "A", null);
        JsonObject rootB = entity("http://example.org/B", "B", null);
        JsonObject thisEntity = JsonParser.parseString("""
                {"iri":"http://example.org/this","label":"This",
                 "directParent":["http://example.org/A","http://example.org/B"]}
                """).getAsJsonObject();

        List<Map<String, Object>> tree = new V1AncestorsJsTreeBuilder(
                thisEntity, List.of(rootA, rootB), List.of(DIRECT_PARENT.getText())).buildJsTree();

        // thisEntity is reachable from both independent roots, so it is rendered once per branch.
        assertThat(tree).hasSize(4);
        assertThat(entryWithIri(tree, "http://example.org/A")).containsEntry("parent", "#");
        assertThat(entryWithIri(tree, "http://example.org/B")).containsEntry("parent", "#");

        List<Map<String, Object>> thisEntityEntries = tree.stream()
                .filter(e -> "http://example.org/this".equals(e.get("iri")))
                .toList();
        assertThat(thisEntityEntries).hasSize(2);
        assertThat(thisEntityEntries.stream().map(e -> e.get("parent")))
                .containsExactlyInAnyOrder(
                        V1AncestorsJsTreeBuilder.base64Encode("http://example.org/A"),
                        V1AncestorsJsTreeBuilder.base64Encode("http://example.org/B"));
    }

    // ------------------------------------------------------------------
    // createJsTreeEntries: id/parent chain across multiple levels + selected/opened state
    // ------------------------------------------------------------------

    @Test
    void producesTheCorrectNestedIdParentChainAcrossThreeLevelsAndMarksOnlyThisEntitySelected() {
        JsonObject grandparent = entity("http://example.org/G", "G", null);
        JsonObject parent = JsonParser.parseString("""
                {"iri":"http://example.org/P","label":"P","ontologyId":"efo",
                 "directParent":["http://example.org/G"],"hasDirectChildren":"true"}
                """).getAsJsonObject();
        JsonObject thisEntity = entityWithParent("http://example.org/T", "T", "http://example.org/P");

        List<Map<String, Object>> tree = new V1AncestorsJsTreeBuilder(
                thisEntity, List.of(grandparent, parent), List.of(DIRECT_PARENT.getText())).buildJsTree();

        assertThat(tree).hasSize(3);

        Map<String, Object> gEntry = entryWithIri(tree, "http://example.org/G");
        Map<String, Object> pEntry = entryWithIri(tree, "http://example.org/P");
        Map<String, Object> tEntry = entryWithIri(tree, "http://example.org/T");

        assertThat(gEntry).containsEntry("id", V1AncestorsJsTreeBuilder.base64Encode("http://example.org/G"));
        assertThat(gEntry).containsEntry("parent", "#");

        String gp = "http://example.org/G;http://example.org/P";
        assertThat(pEntry).containsEntry("id", V1AncestorsJsTreeBuilder.base64Encode(gp));
        assertThat(pEntry).containsEntry("parent", V1AncestorsJsTreeBuilder.base64Encode("http://example.org/G"));

        String gpt = "http://example.org/G;http://example.org/P;http://example.org/T";
        assertThat(tEntry).containsEntry("id", V1AncestorsJsTreeBuilder.base64Encode(gpt));
        assertThat(tEntry).containsEntry("parent", V1AncestorsJsTreeBuilder.base64Encode(gp));

        // Only thisEntity (T) is selected; G and P are opened (expanded) and not selected.
        assertThat(gEntry.get("state")).isEqualTo(Map.of("opened", true));
        assertThat(pEntry.get("state")).isEqualTo(Map.of("opened", true));
        assertThat(tEntry.get("state")).isEqualTo(Map.of("opened", false, "selected", true));

        // P has hasDirectChildren=true, but it is an ancestor (opened), so children must be false
        // regardless of its own flags — only the leaf (thisEntity) node's children flag matters.
        assertThat(pEntry).containsEntry("children", false);
    }

    // ------------------------------------------------------------------
    // createJsTreeEntries: a_attr / ontology_name
    // ------------------------------------------------------------------

    @Test
    void populatesAAttrAndOntologyNameFromTheEntity() {
        JsonObject thisEntity = JsonParser.parseString("""
                {"iri":"http://example.org/this","label":"This","ontologyId":"efo"}
                """).getAsJsonObject();

        List<Map<String, Object>> tree = new V1AncestorsJsTreeBuilder(
                thisEntity, List.of(), List.of(DIRECT_PARENT.getText())).buildJsTree();

        Map<String, Object> entry = tree.get(0);
        assertThat(entry).containsEntry("ontology_name", "efo");
        assertThat(entry.get("a_attr")).isEqualTo(Map.of(
                "iri", "http://example.org/this",
                "ontology_name", "efo",
                "title", "http://example.org/this",
                "class", "is_a"));
    }

    // ------------------------------------------------------------------
    // createJsTreeEntries: children flag
    // ------------------------------------------------------------------

    @Test
    void reportsChildrenTrueWhenHasDirectChildrenIsSetAndHierarchicalIsNot() {
        JsonElement thisEntity = leafWithChildFlags("true", "false");
        List<Map<String, Object>> tree = treeFor(thisEntity);

        assertThat(tree).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("children", true));
    }

    @Test
    void reportsChildrenFalseWhenNeitherChildFlagIsSet() {
        JsonElement thisEntity = leafWithChildFlags("false", "false");
        List<Map<String, Object>> tree = treeFor(thisEntity);

        assertThat(tree).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("children", false));
    }

    @Test
    void reportsChildrenTrueWhenBothChildFlagsAreSet() {
        JsonElement thisEntity = leafWithChildFlags("true", "true");
        List<Map<String, Object>> tree = treeFor(thisEntity);

        assertThat(tree).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("children", true));
    }

    /**
     * Documents the KNOWN, currently-unfixed defect (see class javadoc and the separate
     * {@code fix/v1-ancestors-jstree-hierarchical-children} PR): on this branch,
     * {@code hasHierarchicalChildren} is computed by re-reading {@code HAS_DIRECT_CHILDREN}
     * instead of {@code HAS_HIERARCHICAL_CHILDREN}, so an entity with only
     * {@code hasHierarchicalChildren=true} incorrectly reports {@code children:false}. This
     * assertion must be flipped to {@code true} once this branch rebases onto the merged fix.
     */
    @Test
    void reportsChildrenFalseWhenOnlyHasHierarchicalChildrenIsSetDueToAKnownDefect() {
        JsonElement thisEntity = leafWithChildFlags("false", "true");
        List<Map<String, Object>> tree = treeFor(thisEntity);

        assertThat(tree).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("children", false));
    }

    @Test
    void treatsMissingChildFlagsAsFalseInsteadOfThrowing() {
        JsonElement thisEntity = JsonParser.parseString("""
                {"iri":"http://example.org/this","label":"This"}
                """);
        List<Map<String, Object>> tree = treeFor(thisEntity);

        assertThat(tree).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("children", false));
    }

    // ------------------------------------------------------------------
    // createJsTreeEntries: child IRI not present among the provided entities ("cousin")
    // ------------------------------------------------------------------

    @Test
    void skipsAChildIriThatIsNotAmongTheProvidedEntities() {
        JsonObject thisEntity = entity("http://example.org/this", "This", null);

        V1AncestorsJsTreeBuilder builder = new V1AncestorsJsTreeBuilder(
                thisEntity, List.of(), List.of(DIRECT_PARENT.getText()));

        // Only the constructor can normally populate entityIriToChildIris, and it only ever does
        // so with iris of entities actually passed in - so a genuine "cousin" (an entity that
        // records thisEntity as its parent but that was never fetched/included, e.g. because
        // V1JsTreeRepository's ancestor fetch is capped and truncated it) cannot be reproduced
        // through the public constructor alone. Reaching into this package-private field is the
        // most direct way to prove the defensive "child == null" skip in createJsTreeEntries
        // behaves correctly: it must not throw and must not render the missing child.
        builder.entityIriToChildIris.put("http://example.org/this", "http://example.org/cousin-not-in-tree");

        List<Map<String, Object>> tree = builder.buildJsTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0)).containsEntry("iri", "http://example.org/this");
        assertThat(tree.stream().map(e -> e.get("iri"))).doesNotContain("http://example.org/cousin-not-in-tree");
    }

    // ------------------------------------------------------------------
    // base64Encode
    // ------------------------------------------------------------------

    @Test
    void base64EncodesExactlyAsExpected() {
        assertThat(V1AncestorsJsTreeBuilder.base64Encode("hello")).isEqualTo("aGVsbG8=");
        assertThat(V1AncestorsJsTreeBuilder.base64Encode("http://example.org/EFO_0001"))
                .isEqualTo(Base64.getEncoder().encodeToString(
                        "http://example.org/EFO_0001".getBytes(StandardCharsets.UTF_8)));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static JsonObject entity(String iri, String label, String directParentIri) {
        JsonObject json = new JsonObject();
        json.addProperty("iri", iri);
        json.addProperty("label", label);
        if (directParentIri != null) {
            com.google.gson.JsonArray parents = new com.google.gson.JsonArray();
            parents.add(directParentIri);
            json.add("directParent", parents);
        }
        return json;
    }

    private static JsonObject entityWithParent(String iri, String label, String directParentIri) {
        return entity(iri, label, directParentIri);
    }

    private static JsonElement leafWithChildFlags(String hasDirectChildren, String hasHierarchicalChildren) {
        JsonObject json = new JsonObject();
        json.addProperty("iri", "http://example.org/this");
        json.addProperty("label", "This");
        json.addProperty("hasDirectChildren", hasDirectChildren);
        json.addProperty("hasHierarchicalChildren", hasHierarchicalChildren);
        return json;
    }

    private static List<Map<String, Object>> treeFor(JsonElement thisEntity) {
        return new V1AncestorsJsTreeBuilder(thisEntity, List.of(), List.of(DIRECT_PARENT.getText())).buildJsTree();
    }

    private static Map<String, Object> entryWithIri(List<Map<String, Object>> tree, String iri) {
        return tree.stream()
                .filter(e -> iri.equals(e.get("iri")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No tree entry found with iri " + iri));
    }
}
