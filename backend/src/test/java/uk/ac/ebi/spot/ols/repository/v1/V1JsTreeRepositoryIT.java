package uk.ac.ebi.spot.ols.repository.v1;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code V1JsTreeRepository} itself has essentially no logic of its own: every one of its six
 * public methods builds the same {@code ontologyId+type+iri} composite entity id, calls
 * {@link uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient#getOne}/{@code getAncestors}/
 * {@code getDirectChildren} for real data, applies
 * {@link uk.ac.ebi.spot.ols.repository.transforms.LocalizationTransform#transform} to the
 * requested entity and to every related entity, and hands everything to either
 * {@link V1AncestorsJsTreeBuilder} or {@link V1ChildrenJsTreeBuilder} to actually build the jstree
 * structure. Those four collaborators are already exhaustively tested on their own (the two
 * builders directly, {@code OlsPostgresClient} and {@code LocalizationTransform} elsewhere in this
 * programme) — this class exists solely to prove <em>this</em> class's wiring: that the composite
 * id and {@code entityType} string are correct for all three entity types, that localization is
 * genuinely applied on both sides (not just one), and that each builder is constructed with the
 * right arguments in the right order/shape.
 *
 * <p>No separate {@code V1JsTreeRepositoryTest} unit test exists: every method here is Postgres-
 * backed via {@code OlsPostgresClient}, so there is no meaningful pure-logic layer to test in
 * isolation — a hand-rolled fake standing in for {@code OlsPostgresClient} would only prove this
 * class calls a fake correctly, not that the composite id it builds actually resolves against real
 * data (which is the entire point of this class). See
 * {@code docs/backend-testing-strategy.md}'s "Implemented V1JsTreeRepository baseline" section for
 * the full reasoning.
 */
@Testcontainers
class V1JsTreeRepositoryIT {

    /** Reused as-is from {@code V1TermRepositoryIT}: EFO_0001 (root) -> EFO_1001/EFO_1999 (children). */
    @Container
    private static final PostgreSQLContainer<?> CLASS_POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    /** Reused as-is from {@code V1PropertyRepositoryIT}: EFO_0100 (root) -> EFO_0101 (child). */
    @Container
    private static final PostgreSQLContainer<?> PROPERTY_POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    /**
     * The individual fixture (reused from {@code V1IndividualRepositoryIT}: EFO_I100, whose
     * ancestor EFO_0001 is a class — a genuine cross-type ancestor case) plus two small additive
     * fixtures unique to this test: a class root/leaf pair with language-dependent labels
     * (JST_ROOT/JST_LEAF, for the localization proof) and an individual root/leaf pair with a
     * genuine individual-to-individual direct-child relationship (JST_IND_ROOT/JST_IND_LEAF, since
     * no existing individual fixture has one). See
     * {@link PostgresIntegrationTestSupport#initializeJsTreeRepositoryDatabase}.
     */
    @Container
    private static final PostgreSQLContainer<?> JSTREE_POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.V1OntologyTermRepositoryHandle classHandle;
    private static PostgresIntegrationTestSupport.V1OntologyPropertyRepositoryHandle propertyHandle;
    private static PostgresIntegrationTestSupport.V1OntologyIndividualRepositoryHandle individualHandle;
    private static PostgresIntegrationTestSupport.V1OntologyTermRepositoryHandle localizationHandle;

    private static V1JsTreeRepository classJsTree;
    private static V1JsTreeRepository propertyJsTree;
    private static V1JsTreeRepository individualJsTree;
    private static V1JsTreeRepository localizationJsTree;

    @BeforeAll
    static void setUpDatabases() {
        PostgresIntegrationTestSupport.initializeClassDatabase(CLASS_POSTGRES);
        classHandle = PostgresIntegrationTestSupport.createV1OntologyTermRepositories(CLASS_POSTGRES);
        classJsTree = classHandle.jsTreeRepository();

        PostgresIntegrationTestSupport.initializePropertyDatabase(PROPERTY_POSTGRES);
        propertyHandle = PostgresIntegrationTestSupport.createV1OntologyPropertyRepositories(PROPERTY_POSTGRES);
        propertyJsTree = propertyHandle.jsTreeRepository();

        PostgresIntegrationTestSupport.initializeJsTreeRepositoryDatabase(JSTREE_POSTGRES);
        individualHandle = PostgresIntegrationTestSupport.createV1OntologyIndividualRepositories(JSTREE_POSTGRES);
        individualJsTree = individualHandle.jsTreeRepository();
        localizationHandle = PostgresIntegrationTestSupport.createV1OntologyTermRepositories(JSTREE_POSTGRES);
        localizationJsTree = localizationHandle.jsTreeRepository();
    }

    @AfterAll
    static void closeDatabaseClients() {
        classHandle.close();
        propertyHandle.close();
        individualHandle.close();
        localizationHandle.close();
    }

    // ---------------------------------------------------------------------------------------
    // Composite id + entityType wiring, per entity type. A wrong entityType string (e.g. passing
    // "OntologyProperty" for a class lookup) or a wrong composite-id format/ordering would make
    // OlsPostgresClient#getOne find zero matching rows and throw ("expected exactly one result for
    // getOne, but got 0") rather than silently returning wrong data -- so a successful lookup that
    // returns exactly the expected iri/label content is itself direct proof that both the entityType
    // string and the "ontologyId+type+iri" composite id passed to getOne/getAncestors/
    // getDirectChildren for that call are correct.
    // ---------------------------------------------------------------------------------------

    @Test
    void buildsTheClassJsTreeWithTheCorrectCompositeIdAndEntityType() {
        List<Map<String, Object>> tree = classJsTree.getJsTreeForClass(
                "http://example.org/EFO_1001", "efo", "en");

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0))
                .containsEntry("iri", "http://example.org/EFO_0001")
                .containsEntry("text", "Liver disease")
                .containsEntry("parent", "#");
        assertThat(tree.get(1))
                .containsEntry("iri", "http://example.org/EFO_1001")
                .containsEntry("text", "Clinical liver child");
        assertThat(((Map<?, ?>) tree.get(1).get("state")).get("selected")).isEqualTo(true);
    }

    @Test
    void buildsThePropertyJsTreeWithTheCorrectCompositeIdAndEntityType() {
        List<Map<String, Object>> tree = propertyJsTree.getJsTreeForProperty(
                "http://example.org/EFO_0101", "efo", "en");

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0))
                .containsEntry("iri", "http://example.org/EFO_0100")
                .containsEntry("text", "has specimen")
                .containsEntry("parent", "#");
        assertThat(tree.get(1))
                .containsEntry("iri", "http://example.org/EFO_0101")
                .containsEntry("text", "has material");
        assertThat(((Map<?, ?>) tree.get(1).get("state")).get("selected")).isEqualTo(true);
    }

    @Test
    void buildsTheIndividualJsTreeWithTheCorrectCompositeIdAndEntityTypeAcrossACrossTypeAncestor() {
        // EFO_I100's ancestor is EFO_0001, a *class* -- getAncestors has no type filter, so this
        // also confirms getJsTreeForIndividual doesn't accidentally constrain the ancestor lookup
        // to individual-typed rows only.
        List<Map<String, Object>> tree = individualJsTree.getJsTreeForIndividual(
                "http://example.org/EFO_I100", "efo", "en");

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0))
                .containsEntry("iri", "http://example.org/EFO_0001")
                .containsEntry("text", "Liver disease")
                .containsEntry("parent", "#");
        assertThat(tree.get(1))
                .containsEntry("iri", "http://example.org/EFO_I100")
                .containsEntry("text", "Liver specimen alpha");
        assertThat(((Map<?, ?>) tree.get(1).get("state")).get("selected")).isEqualTo(true);
    }

    @Test
    void throwsWhenTheOntologyIdSegmentOfTheCompositeIdDoesNotMatchAnyRow() {
        // "duo+class+http://example.org/EFO_1001" doesn't exist (that iri only exists under the
        // "efo" ontology) -- proving the ontologyId argument is a genuine, load-bearing segment of
        // the composite id, not ignored or defaulted.
        assertThatThrownBy(() -> classJsTree.getJsTreeForClass(
                "http://example.org/EFO_1001", "duo", "en"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expected exactly one result");
    }

    // ---------------------------------------------------------------------------------------
    // jstreeId threading + builder-argument-shape proof for the children path. jstreeId is an
    // opaque, caller-supplied, base64-encoded path string (normally the "id" of some node from a
    // prior getJsTreeFor*/getJsTreeChildrenFor* call); V1JsTreeRepository does nothing with it
    // itself except forward it, verbatim, into V1ChildrenJsTreeBuilder's constructor, which
    // base64-decodes it and uses the decoded string as the literal prefix for every child's own
    // "parent" (and, with ";"+childIri, "id") field -- no entity lookup or validation ever happens
    // against it. Using a marker string unrelated to any entity's own iri (rather than the more
    // usual base64(parent iri) convention already used elsewhere in this programme) proves this is
    // genuine passthrough of the caller's own argument, not something V1JsTreeRepository derives
    // itself from the requested iri.
    // ---------------------------------------------------------------------------------------

    private static final String OPAQUE_JSTREE_ID = base64("opaque-parent-token");

    @Test
    void buildsTheClassJsTreeChildrenWithTheCorrectCompositeIdAndThreadsTheJstreeIdVerbatim() {
        List<Map<String, Object>> children = classJsTree.getJsTreeChildrenForClass(
                "http://example.org/EFO_0001", OPAQUE_JSTREE_ID, "efo", "en");

        assertThat(children).extracting(child -> (String) child.get("iri")).containsExactly(
                "http://example.org/EFO_1001",
                "http://example.org/EFO_1999");
        assertThat(children.get(0))
                .containsEntry("parent", OPAQUE_JSTREE_ID)
                .containsEntry("id", base64("opaque-parent-token;http://example.org/EFO_1001"));
        assertThat(children.get(1))
                .containsEntry("parent", OPAQUE_JSTREE_ID)
                .containsEntry("id", base64("opaque-parent-token;http://example.org/EFO_1999"));
    }

    @Test
    void buildsThePropertyJsTreeChildrenWithTheCorrectCompositeIdAndThreadsTheJstreeIdVerbatim() {
        List<Map<String, Object>> children = propertyJsTree.getJsTreeChildrenForProperty(
                "http://example.org/EFO_0100", OPAQUE_JSTREE_ID, "efo", "en");

        assertThat(children).hasSize(1);
        assertThat(children.get(0))
                .containsEntry("iri", "http://example.org/EFO_0101")
                .containsEntry("text", "has material")
                .containsEntry("parent", OPAQUE_JSTREE_ID)
                .containsEntry("id", base64("opaque-parent-token;http://example.org/EFO_0101"));
    }

    @Test
    void buildsTheIndividualJsTreeChildrenFromAGenuineIndividualToIndividualDirectChildRelationship() {
        // Unlike EFO_I100/EFO_I200 in the shared individual fixture (whose directParents always
        // point at a *class*, never another individual), JST_IND_LEAF's directParents genuinely
        // points at JST_IND_ROOT, another individual -- so this is real, non-degenerate coverage of
        // getJsTreeChildrenForIndividual's own OlsPostgresClient#getDirectChildren call, not just an
        // empty-list passthrough.
        List<Map<String, Object>> children = individualJsTree.getJsTreeChildrenForIndividual(
                "http://example.org/JST_IND_ROOT", OPAQUE_JSTREE_ID, "efo", "en");

        assertThat(children).hasSize(1);
        assertThat(children.get(0))
                .containsEntry("iri", "http://example.org/JST_IND_LEAF")
                .containsEntry("text", "Individual leaf label")
                .containsEntry("parent", OPAQUE_JSTREE_ID)
                .containsEntry("id", base64("opaque-parent-token;http://example.org/JST_IND_LEAF"));
    }

    // ---------------------------------------------------------------------------------------
    // Localization applied to *both* sides. JST_ROOT/JST_LEAF's _json label is a genuinely
    // language-dependent reified-literal array ({lang:"fr", value:"..."} plus a default/no-lang
    // fallback value) -- unlike every other fixture in this suite, whose plain-string labels pass
    // through LocalizationTransform unchanged regardless of the requested language and so could
    // never distinguish "localization was applied" from "localization was skipped". Requesting
    // "fr" vs "en" must visibly change the rendered text on whichever side is genuinely localized;
    // if V1JsTreeRepository only localized one side (e.g. thisEntity but not the ancestors/
    // children, or vice versa), exactly one of the two assertions below would fail.
    // ---------------------------------------------------------------------------------------

    @Test
    void localizesBothTheRequestedEntityAndItsAncestorPerRequestedLanguage() {
        List<Map<String, Object>> frenchTree = localizationJsTree.getJsTreeForClass(
                "http://example.org/JST_LEAF", "efo", "fr");

        assertThat(frenchTree).hasSize(2);
        assertThat(frenchTree.get(0))
                .containsEntry("iri", "http://example.org/JST_ROOT")
                .containsEntry("text", "Racine francaise");
        assertThat(frenchTree.get(1))
                .containsEntry("iri", "http://example.org/JST_LEAF")
                .containsEntry("text", "Feuille francaise");

        List<Map<String, Object>> defaultTree = localizationJsTree.getJsTreeForClass(
                "http://example.org/JST_LEAF", "efo", "en");

        assertThat(defaultTree.get(0))
                .containsEntry("iri", "http://example.org/JST_ROOT")
                .containsEntry("text", "Root default label");
        assertThat(defaultTree.get(1))
                .containsEntry("iri", "http://example.org/JST_LEAF")
                .containsEntry("text", "Leaf default label");
    }

    @Test
    void localizesEveryChildIndependentlyOfTheRequestedLanguage() {
        // Note: getJsTreeChildrenForEntity localizes thisEntity (JST_ROOT here) with the exact same
        // two lines of code as the ancestors path above, but V1ChildrenJsTreeBuilder's buildJsTree()
        // never actually reads its thisEntity field (confirmed by reading the class) -- so, unlike
        // the ancestors path, this call's effect on thisEntity is not independently observable
        // through any output of this method. Only the child side (JST_IND_LEAF... here JST_LEAF) is
        // assertable. This is documented, not glossed over, in the baseline doc section.
        List<Map<String, Object>> frenchChildren = localizationJsTree.getJsTreeChildrenForClass(
                "http://example.org/JST_ROOT", OPAQUE_JSTREE_ID, "efo", "fr");

        assertThat(frenchChildren).singleElement()
                .satisfies(child -> assertThat(child).containsEntry("text", "Feuille francaise"));

        List<Map<String, Object>> defaultChildren = localizationJsTree.getJsTreeChildrenForClass(
                "http://example.org/JST_ROOT", OPAQUE_JSTREE_ID, "efo", "en");

        assertThat(defaultChildren).singleElement()
                .satisfies(child -> assertThat(child).containsEntry("text", "Leaf default label"));
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
