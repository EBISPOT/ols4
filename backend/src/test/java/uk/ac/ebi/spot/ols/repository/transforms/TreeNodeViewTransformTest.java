package uk.ac.ebi.spot.ols.repository.transforms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TreeNodeViewTransform}: the {@code view=tree} projection keeps what a hierarchy
 * tree reads from an entity and drops the rest, {@code linkedEntities} included.
 */
class TreeNodeViewTransformTest {

    private static final String PARENT = "http://www.ebi.ac.uk/coho/COHO_0000000";
    private static final String RELATION = "http://www.ebi.ac.uk/coho/isSubCohortOf";
    private static final String XREF_TARGET = "http://www.example.org/somewhere-else";

    private static JsonArray arrayOf(String... values) {
        JsonArray arr = new JsonArray();
        for (String v : values) arr.add(v);
        return arr;
    }

    private static JsonObject labelled(String label) {
        JsonObject o = new JsonObject();
        o.add("label", arrayOf(label));
        return o;
    }

    /** A cohort as the dataload stores it: identity and hierarchy plus a lot of annotation. */
    private static JsonObject cohort() {
        JsonObject e = new JsonObject();
        e.add("type", arrayOf("entity", "individual"));
        e.addProperty("iri", "http://www.ebi.ac.uk/coho/COHO_0000207");
        e.addProperty("curie", "COHO:0000207");
        e.addProperty("ontologyId", "coho");
        e.add("label", arrayOf("UK Biobank"));
        e.addProperty("isObsolete", false);
        e.addProperty("isDefiningOntology", true);
        e.addProperty("hasDirectChildren", false);
        e.addProperty("hasHierarchicalChildren", true);
        e.addProperty("numHierarchicalDescendants", 3);

        JsonObject reifiedParent = new JsonObject();
        reifiedParent.add("type", arrayOf("reification"));
        reifiedParent.addProperty("value", PARENT);
        JsonObject axiom = new JsonObject();
        axiom.add("childRelationToParent", arrayOf(RELATION));
        JsonArray axioms = new JsonArray();
        axioms.add(axiom);
        reifiedParent.add("axioms", axioms);
        JsonArray parents = new JsonArray();
        parents.add(reifiedParent);
        e.add("hierarchicalParent", parents);

        e.add("synonym", arrayOf("UKB", "UK Biobank cohort"));
        e.add("definition", arrayOf("A prospective cohort of half a million UK participants."));
        e.add("hierarchicalAncestor", arrayOf(PARENT, "http://www.ebi.ac.uk/coho/COHO_0000001"));
        e.add("http://www.geneontology.org/formats/oboInOwl#hasDbXref", arrayOf(XREF_TARGET));

        JsonObject linked = new JsonObject();
        linked.add(PARENT, labelled("cohort"));
        linked.add(RELATION, labelled("is sub-cohort of"));
        linked.add(XREF_TARGET, labelled("somewhere else"));
        linked.add("http://www.ebi.ac.uk/coho/COHO_0000001", labelled("study"));
        e.add("linkedEntities", linked);
        return e;
    }

    @Test
    void keepsWhatATreeNodeNeedsAndDropsTheRest() {
        JsonObject node = TreeNodeViewTransform.transform(cohort()).getAsJsonObject();

        assertThat(node.keySet()).containsExactlyInAnyOrder(
                "type", "iri", "curie", "ontologyId", "label", "isObsolete", "isDefiningOntology",
                "hasDirectChildren", "hasHierarchicalChildren", "numHierarchicalDescendants",
                "hierarchicalParent");
        assertThat(node.get("label")).isEqualTo(arrayOf("UK Biobank"));
        assertThat(node.get("numHierarchicalDescendants").getAsInt()).isEqualTo(3);
        // The reified parent link survives whole: the relation it carries is what the tree shows.
        JsonObject parent = node.getAsJsonArray("hierarchicalParent").get(0).getAsJsonObject();
        assertThat(parent.get("value").getAsString()).isEqualTo(PARENT);
        assertThat(parent.getAsJsonArray("axioms").get(0).getAsJsonObject()
                .getAsJsonArray("childRelationToParent").get(0).getAsString()).isEqualTo(RELATION);
    }

    @Test
    void dropsLinkedEntitiesAndEverythingOnlyTheEntityPageShows() {
        JsonObject node = TreeNodeViewTransform.transform(cohort()).getAsJsonObject();

        assertThat(node.has("linkedEntities")).isFalse();
        assertThat(node.has("synonym")).isFalse();
        assertThat(node.has("definition")).isFalse();
        assertThat(node.has("hierarchicalAncestor")).isFalse();
        assertThat(node.has("http://www.geneontology.org/formats/oboInOwl#hasDbXref")).isFalse();
    }

    @Test
    void mapsArraysElementWise() {
        JsonArray page = new JsonArray();
        page.add(cohort());
        page.add(cohort());

        JsonElement out = TreeNodeViewTransform.transform(page);

        assertThat(out.isJsonArray()).isTrue();
        assertThat(out.getAsJsonArray()).hasSize(2);
        assertThat(out.getAsJsonArray().get(1).getAsJsonObject().has("synonym")).isFalse();
    }

    @Test
    void leavesNonEntitiesAlone() {
        JsonObject config = new JsonObject();
        config.addProperty("id", "coho");
        config.add("type", arrayOf("ontology"));
        config.add("preferredRoots", arrayOf(PARENT));

        assertThat(TreeNodeViewTransform.transform(config)).isEqualTo(config);
        assertThat(TreeNodeViewTransform.transform(new com.google.gson.JsonPrimitive("x")).getAsString()).isEqualTo("x");
    }

    @Test
    void transformerAppliesItOnlyForTheTreeView() {
        JsonTransformOptions full = new JsonTransformOptions();
        JsonTransformOptions tree = new JsonTransformOptions();
        tree.view = JsonTransformOptions.TreeView.tree;

        assertThat(JsonTransformer.transformJson(cohort(), "en", full).getAsJsonObject().has("synonym")).isTrue();
        assertThat(JsonTransformer.transformJson(cohort(), "en", tree).getAsJsonObject().has("synonym")).isFalse();
    }
}
