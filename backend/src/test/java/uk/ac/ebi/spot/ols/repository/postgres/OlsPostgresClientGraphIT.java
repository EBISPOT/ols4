package uk.ac.ebi.spot.ols.repository.postgres;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport.OlsPostgresClientRepositoryHandle;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres coverage of {@link OlsPostgresClient}'s graph-traversal family. This is the
 * "milestone 2 of 3" slice of this Tier B target (see {@code docs/backend-testing-strategy.md}'s
 * "Implemented OlsPostgresClient" baseline for the full milestone split, and milestone 1's
 * {@code OlsPostgresClientIT} for the static-logic and {@code getAll}/{@code getOne} slice):
 * {@code getDirectParents}/{@code getDirectChildren} (both overloads)/{@code getHierarchicalParents}/
 * {@code getHierarchicalChildren}/{@code getAncestors}/{@code getDescendants}/
 * {@code getHierarchicalAncestors}/{@code getHierarchicalDescendants}/{@code getRelatedTo}/
 * {@code getRelatedFrom}, backed by the private {@code lookupArrayTargets} (parents/ancestors/
 * related-to -- entities <em>referenced by</em> the given id's array column) and
 * {@code lookupArraySources} (children/descendants/related-from -- entities <em>whose</em> array
 * column references the given id) helpers, plus {@code buildNodePropCondition}'s {@code isObsolete}/
 * {@code type} filters and {@code lookupArraySources}'s case-insensitive {@code search} parameter.
 *
 * <p>The dedicated fixture ({@link PostgresIntegrationTestSupport#initializeOlsPostgresClientGraphDatabase},
 * {@code graph-fixture.json}) is built specifically to distinguish behaviour that a lazier fixture
 * could accidentally leave unproven:
 * <ul>
 *   <li>{@code GRAPH_CHILD_ACTIVE} has <em>two</em> {@code direct_parents} (one active, one
 *       obsolete) but only <em>one</em> {@code hierarchical_parents} -- proving
 *       {@code getDirectParents}/{@code getHierarchicalParents} read genuinely different columns,
 *       not just different names for the same data.</li>
 *   <li>{@code GRAPH_GRANDCHILD}'s {@code direct_ancestors} is non-transitive (its immediate parent
 *       only) while its {@code hierarchical_ancestors} is transitive (both levels) -- proving
 *       {@code getAncestors}/{@code getDescendants} and {@code getHierarchicalAncestors}/
 *       {@code getHierarchicalDescendants} are not interchangeable.</li>
 *   <li>{@code graphtest2}'s {@code GRAPH_PARENT} shares its IRI <em>and</em> label with
 *       {@code graphtest}'s {@code GRAPH_PARENT}, and {@code graphtest2}'s {@code GRAPH_CROSS_CHILD}
 *       has {@code graphtest}'s {@code GRAPH_PARENT} IRI in its own {@code direct_parents} -- both
 *       deliberately excluded from every {@code graphtest}-scoped lookup, proving the
 *       same-ontology-only join condition on both the targets and sources side.</li>
 * </ul>
 *
 * <p>The fixture also carries a self-contained {@code GRAPH_RED_*} sub-graph (rooted at
 * {@code GRAPH_RED_A}, never referencing the records above) for the {@code excludeRedundantEdges}
 * overloads of {@code getDirectChildren}/{@code getHierarchicalChildren} -- the transitive reduction
 * of the hierarchy behind GitHub issue #1252, implemented by the private
 * {@code redundantEdgeWitness} subquery. Each record's {@code definition} states which rule of the
 * reduction it exists to prove: the canonical {@code c is_a a, c part_of b, b is_a a} case, a longer
 * chain, independent parents, an obsolete witness, a witness of another type, a witness from another
 * ontology, two shapes of hierarchical cycle, and an individuals-only hierarchy.
 */
class OlsPostgresClientGraphIT {

    private static PostgreSQLContainer<?> container;
    private static OlsPostgresClientRepositoryHandle handle;
    private static OlsPostgresClient client;

    @BeforeAll
    static void setUpDatabase() {
        container = PostgresIntegrationTestSupport.newContainer();
        container.start();
        PostgresIntegrationTestSupport.initializeOlsPostgresClientGraphDatabase(container);
        handle = PostgresIntegrationTestSupport.createOlsPostgresClientRepositories(container);
        client = handle.olsPostgresClient();
    }

    @AfterAll
    static void tearDownDatabase() {
        if (handle != null) {
            handle.close();
        }
        if (container != null) {
            container.stop();
        }
    }

    private static final String GRAPH_PARENT = "graphtest+class+http://example.org/GRAPH_PARENT";
    private static final String GRAPH_PARENT_OBSOLETE = "graphtest+class+http://example.org/GRAPH_PARENT_OBSOLETE";
    private static final String GRAPH_CHILD_ACTIVE = "graphtest+class+http://example.org/GRAPH_CHILD_ACTIVE";
    private static final String GRAPH_CHILD_OBSOLETE = "graphtest+class+http://example.org/GRAPH_CHILD_OBSOLETE";
    private static final String GRAPH_CHILD_PROPERTY = "graphtest+property+http://example.org/GRAPH_CHILD_PROPERTY";
    private static final String GRAPH_GRANDCHILD = "graphtest+class+http://example.org/GRAPH_GRANDCHILD";
    private static final String GRAPH_RELATED = "graphtest+class+http://example.org/GRAPH_RELATED";

    private static final String RED_A = "graphtest+class+http://example.org/GRAPH_RED_A";
    private static final String RED_B = "graphtest+class+http://example.org/GRAPH_RED_B";
    private static final String RED_C = "graphtest+class+http://example.org/GRAPH_RED_C";
    private static final String RED_C2 = "graphtest+class+http://example.org/GRAPH_RED_C2";
    private static final String RED_C3 = "graphtest+class+http://example.org/GRAPH_RED_C3";
    private static final String RED_D = "graphtest+class+http://example.org/GRAPH_RED_D";
    private static final String RED_E = "graphtest+class+http://example.org/GRAPH_RED_E";
    private static final String RED_F = "graphtest+class+http://example.org/GRAPH_RED_F";
    private static final String RED_OBS = "graphtest+class+http://example.org/GRAPH_RED_OBS";
    private static final String RED_G = "graphtest+class+http://example.org/GRAPH_RED_G";
    private static final String RED_H = "graphtest+class+http://example.org/GRAPH_RED_H";
    private static final String RED_X = "graphtest+class+http://example.org/GRAPH_RED_X";
    private static final String RED_Y = "graphtest+class+http://example.org/GRAPH_RED_Y";
    private static final String RED_P = "graphtest+class+http://example.org/GRAPH_RED_P";
    private static final String RED_Q = "graphtest+class+http://example.org/GRAPH_RED_Q";
    private static final String RED_R = "graphtest+class+http://example.org/GRAPH_RED_R";
    private static final String RED_IND = "graphtest+individual+http://example.org/GRAPH_RED_IND";
    private static final String RED_I_PARENT = "graphtest+individual+http://example.org/GRAPH_RED_I_PARENT";
    private static final String RED_I_MID = "graphtest+individual+http://example.org/GRAPH_RED_I_MID";
    private static final String RED_I_LEAF = "graphtest+individual+http://example.org/GRAPH_RED_I_LEAF";

    private static final Map<String, String> ACTIVE_CLASSES = Map.of("type", "OntologyClass", "isObsolete", "false");
    private static final Map<String, String> ALL_CLASSES = Map.of("type", "OntologyClass");
    private static final Map<String, String> ACTIVE_INDIVIDUALS = Map.of("type", "OntologyIndividual", "isObsolete", "false");

    private static final PageRequest ANY_PAGEABLE = PageRequest.of(0, 10);
    private static final PageRequest LARGE_PAGEABLE = PageRequest.of(0, 100);

    private static List<String> ids(Page<JsonElement> page) {
        return page.getContent().stream()
                .map(e -> e.getAsJsonObject().get("id").getAsString())
                .toList();
    }

    // ------------------------------------------------------------------
    // getDirectParents / getHierarchicalParents / getAncestors / getHierarchicalAncestors /
    // getRelatedTo -- all backed by lookupArrayTargets.
    // ------------------------------------------------------------------

    @Test
    void getDirectParentsReturnsBothParentsWhenNoNodePropsFilterApplied() {
        Page<JsonElement> page = client.getDirectParents(GRAPH_CHILD_ACTIVE, Map.of(), ANY_PAGEABLE);

        assertThat(ids(page)).containsExactlyInAnyOrder(GRAPH_PARENT, GRAPH_PARENT_OBSOLETE);
    }

    @Test
    void getDirectParentsFiltersByIsObsoleteFalse() {
        Page<JsonElement> page = client.getDirectParents(GRAPH_CHILD_ACTIVE, Map.of("isObsolete", "false"), ANY_PAGEABLE);

        assertThat(ids(page)).containsExactly(GRAPH_PARENT);
    }

    @Test
    void getDirectParentsFiltersByIsObsoleteTrue() {
        Page<JsonElement> page = client.getDirectParents(GRAPH_CHILD_ACTIVE, Map.of("isObsolete", "true"), ANY_PAGEABLE);

        assertThat(ids(page)).containsExactly(GRAPH_PARENT_OBSOLETE);
    }

    @Test
    void getDirectParentsExcludesASameIriAndLabelEntityFromADifferentOntology() {
        // graphtest2's GRAPH_PARENT has the identical IRI and label as graphtest's GRAPH_PARENT.
        // Only 2 parents (not 3) must come back: the cross-ontology duplicate must be excluded.
        Page<JsonElement> page = client.getDirectParents(GRAPH_CHILD_ACTIVE, Map.of(), ANY_PAGEABLE);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(ids(page)).allMatch(id -> id.startsWith("graphtest+"));
    }

    @Test
    void getHierarchicalParentsReadsADifferentColumnThanGetDirectParents() {
        // GRAPH_CHILD_ACTIVE's hierarchical_parents has only GRAPH_PARENT, unlike its two-entry
        // direct_parents -- proving the two methods are not reading the same data.
        Page<JsonElement> page = client.getHierarchicalParents(GRAPH_CHILD_ACTIVE, Map.of(), ANY_PAGEABLE);

        assertThat(ids(page)).containsExactly(GRAPH_PARENT);
    }

    @Test
    void getAncestorsReturnsOnlyTheNonTransitiveDirectAncestor() {
        Page<JsonElement> page = client.getAncestors(GRAPH_GRANDCHILD, Map.of(), ANY_PAGEABLE);

        assertThat(ids(page)).containsExactly(GRAPH_CHILD_ACTIVE);
    }

    @Test
    void getHierarchicalAncestorsReturnsTheFullTransitiveChainOrderedByIri() {
        Page<JsonElement> page = client.getHierarchicalAncestors(GRAPH_GRANDCHILD, Map.of(), ANY_PAGEABLE);

        // Ordered by e2.iri ascending: ".../GRAPH_CHILD_ACTIVE" < ".../GRAPH_PARENT".
        assertThat(ids(page)).containsExactly(GRAPH_CHILD_ACTIVE, GRAPH_PARENT);
    }

    @Test
    void getRelatedToReturnsTheEntityItPointsTo() {
        Page<JsonElement> page = client.getRelatedTo(GRAPH_CHILD_ACTIVE, Map.of(), ANY_PAGEABLE);

        assertThat(ids(page)).containsExactly(GRAPH_RELATED);
    }

    // ------------------------------------------------------------------
    // getDirectChildren (both overloads) / getHierarchicalChildren / getDescendants /
    // getHierarchicalDescendants / getRelatedFrom -- all backed by lookupArraySources.
    // ------------------------------------------------------------------

    @Test
    void getDirectChildrenThreeArgOverloadExcludesTheCrossOntologyChild() {
        // GRAPH_CROSS_CHILD (ontology graphtest2) also has GRAPH_PARENT's IRI in its
        // direct_parents, but must be excluded: only the 3 graphtest-ontology children come back.
        Page<JsonElement> page = client.getDirectChildren(GRAPH_PARENT, Map.of(), ANY_PAGEABLE);

        assertThat(ids(page)).containsExactlyInAnyOrder(GRAPH_CHILD_ACTIVE, GRAPH_CHILD_OBSOLETE, GRAPH_CHILD_PROPERTY);
    }

    @Test
    void getDirectChildrenFourArgOverloadWithNullSearchBehavesLikeTheThreeArgOverload() {
        Page<JsonElement> page = client.getDirectChildren(GRAPH_PARENT, Map.of(), ANY_PAGEABLE, null);

        assertThat(ids(page)).containsExactlyInAnyOrder(GRAPH_CHILD_ACTIVE, GRAPH_CHILD_OBSOLETE, GRAPH_CHILD_PROPERTY);
    }

    @Test
    void getDirectChildrenFiltersByIsObsoleteFalse() {
        Page<JsonElement> page = client.getDirectChildren(GRAPH_PARENT, Map.of("isObsolete", "false"), ANY_PAGEABLE);

        assertThat(ids(page)).containsExactlyInAnyOrder(GRAPH_CHILD_ACTIVE, GRAPH_CHILD_PROPERTY);
    }

    @Test
    void getDirectChildrenFiltersByIsObsoleteTrue() {
        Page<JsonElement> page = client.getDirectChildren(GRAPH_PARENT, Map.of("isObsolete", "true"), ANY_PAGEABLE);

        assertThat(ids(page)).containsExactly(GRAPH_CHILD_OBSOLETE);
    }

    @Test
    void getDirectChildrenFiltersByType() {
        Page<JsonElement> page = client.getDirectChildren(GRAPH_PARENT, Map.of("type", "OntologyProperty"), ANY_PAGEABLE);

        assertThat(ids(page)).containsExactly(GRAPH_CHILD_PROPERTY);
    }

    @Test
    void getDirectChildrenIgnoresAnUnrecognizedNodePropKey() {
        Page<JsonElement> filtered = client.getDirectChildren(GRAPH_PARENT, Map.of("thisIsNotRecognized", "x"), ANY_PAGEABLE);
        Page<JsonElement> unfiltered = client.getDirectChildren(GRAPH_PARENT, Map.of(), ANY_PAGEABLE);

        assertThat(filtered.getTotalElements()).isEqualTo(unfiltered.getTotalElements());
    }

    @Test
    void getDirectChildrenSearchMatchesCaseInsensitiveSubstringOfTheLabel() {
        Page<JsonElement> lower = client.getDirectChildren(GRAPH_PARENT, Map.of(), ANY_PAGEABLE, "active");
        Page<JsonElement> upper = client.getDirectChildren(GRAPH_PARENT, Map.of(), ANY_PAGEABLE, "ACTIVE");

        assertThat(ids(lower)).containsExactly(GRAPH_CHILD_ACTIVE);
        assertThat(ids(upper)).containsExactly(GRAPH_CHILD_ACTIVE);
    }

    @Test
    void getDirectChildrenSearchWithNoSubstringMatchReturnsEmpty() {
        Page<JsonElement> page = client.getDirectChildren(GRAPH_PARENT, Map.of(), ANY_PAGEABLE, "zzz-no-match");

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(0);
    }

    @Test
    void getDirectChildrenTreatsNullEmptyAndWhitespaceOnlySearchIdentically() {
        Page<JsonElement> nullSearch = client.getDirectChildren(GRAPH_PARENT, Map.of(), ANY_PAGEABLE, null);
        Page<JsonElement> emptySearch = client.getDirectChildren(GRAPH_PARENT, Map.of(), ANY_PAGEABLE, "");
        Page<JsonElement> whitespaceSearch = client.getDirectChildren(GRAPH_PARENT, Map.of(), ANY_PAGEABLE, "   ");

        assertThat(nullSearch.getTotalElements()).isEqualTo(3);
        assertThat(emptySearch.getTotalElements()).isEqualTo(3);
        assertThat(whitespaceSearch.getTotalElements()).isEqualTo(3);
    }

    @Test
    void getHierarchicalChildrenDiffersFromGetDirectChildrenForAnObsoleteParent() {
        // GRAPH_CHILD_ACTIVE lists GRAPH_PARENT_OBSOLETE in its direct_parents but not in its
        // hierarchical_parents -- so GRAPH_PARENT_OBSOLETE has 1 direct child but 0 hierarchical
        // children.
        Page<JsonElement> direct = client.getDirectChildren(GRAPH_PARENT_OBSOLETE, Map.of(), ANY_PAGEABLE);
        Page<JsonElement> hierarchical = client.getHierarchicalChildren(GRAPH_PARENT_OBSOLETE, Map.of(), ANY_PAGEABLE);

        assertThat(ids(direct)).containsExactly(GRAPH_CHILD_ACTIVE);
        assertThat(hierarchical.getContent()).isEmpty();
    }

    @Test
    void getDescendantsExcludesTheNonTransitiveGrandchild() {
        Page<JsonElement> page = client.getDescendants(GRAPH_PARENT, Map.of(), ANY_PAGEABLE);

        assertThat(ids(page)).containsExactlyInAnyOrder(GRAPH_CHILD_ACTIVE, GRAPH_CHILD_OBSOLETE, GRAPH_CHILD_PROPERTY);
    }

    @Test
    void getHierarchicalDescendantsIncludesTheTransitiveGrandchild() {
        Page<JsonElement> page = client.getHierarchicalDescendants(GRAPH_PARENT, Map.of(), ANY_PAGEABLE);

        assertThat(ids(page)).containsExactlyInAnyOrder(
                GRAPH_CHILD_ACTIVE, GRAPH_CHILD_OBSOLETE, GRAPH_CHILD_PROPERTY, GRAPH_GRANDCHILD);
    }

    @Test
    void getRelatedFromReturnsTheEntityThatPointsToIt() {
        Page<JsonElement> page = client.getRelatedFrom(GRAPH_RELATED, Map.of(), ANY_PAGEABLE);

        assertThat(ids(page)).containsExactly(GRAPH_CHILD_ACTIVE);
    }

    // ------------------------------------------------------------------
    // excludeRedundantEdges -- the getDirectChildren 5-arg and getHierarchicalChildren 4-arg
    // overloads, backed by lookupArraySources + redundantEdgeWitness (GitHub issue #1252).
    // ------------------------------------------------------------------

    @Test
    void hierarchicalChildrenKeepEveryEdgeUnlessRedundantEdgesAreExcluded() {
        Page<JsonElement> threeArg = client.getHierarchicalChildren(RED_A, ACTIVE_CLASSES, LARGE_PAGEABLE);
        Page<JsonElement> explicitFalse = client.getHierarchicalChildren(RED_A, ACTIVE_CLASSES, LARGE_PAGEABLE, false);

        assertThat(ids(threeArg)).containsExactlyInAnyOrder(
                RED_B, RED_C, RED_C2, RED_C3, RED_D, RED_F, RED_G, RED_X, RED_Y);
        assertThat(ids(explicitFalse)).containsExactlyInAnyOrderElementsOf(ids(threeArg));
        assertThat(explicitFalse.getTotalElements()).isEqualTo(9);
    }

    @Test
    void excludingRedundantEdgesDropsChildrenAlreadyReachableThroughAMoreSpecificParent() {
        // C: is_a A and part_of B (B is_a A) -- the canonical case from the issue.
        // D: is_a A and part_of E, E part_of B, B is_a A -- the same over a longer chain.
        Page<JsonElement> page = client.getHierarchicalChildren(RED_A, ACTIVE_CLASSES, LARGE_PAGEABLE, true);

        assertThat(ids(page)).containsExactlyInAnyOrder(RED_B, RED_C2, RED_C3, RED_F, RED_G, RED_X, RED_Y);
        // The count query applies the same reduction as the page query.
        assertThat(page.getTotalElements()).isEqualTo(7);
    }

    @Test
    void excludingRedundantEdgesKeepsTheMoreSpecificEdge() {
        assertThat(ids(client.getHierarchicalChildren(RED_B, ACTIVE_CLASSES, LARGE_PAGEABLE, true)))
                .containsExactlyInAnyOrder(RED_C, RED_E);
        assertThat(ids(client.getHierarchicalChildren(RED_E, ACTIVE_CLASSES, LARGE_PAGEABLE, true)))
                .containsExactly(RED_D);
    }

    @Test
    void excludingRedundantEdgesAppliesToDirectChildrenToo() {
        Page<JsonElement> reduced = client.getDirectChildren(RED_A, ACTIVE_CLASSES, LARGE_PAGEABLE, null, true);
        Page<JsonElement> all = client.getDirectChildren(RED_A, ACTIVE_CLASSES, LARGE_PAGEABLE, null, false);

        assertThat(ids(reduced)).containsExactlyInAnyOrder(RED_B, RED_C2, RED_C3, RED_F, RED_G, RED_X, RED_Y);
        assertThat(ids(all)).containsExactlyInAnyOrder(
                RED_B, RED_C, RED_C2, RED_C3, RED_D, RED_F, RED_G, RED_X, RED_Y);
    }

    @Test
    void excludingRedundantEdgesCombinesWithTheLabelSearch() {
        // "redundancy c" matches C, C2 and C3; only C's edge to A is redundant.
        Page<JsonElement> page = client.getDirectChildren(RED_A, ACTIVE_CLASSES, LARGE_PAGEABLE, "redundancy c", true);

        assertThat(ids(page)).containsExactlyInAnyOrder(RED_C2, RED_C3);
    }

    @Test
    void anObsoleteWitnessOnlyCountsWhenObsoleteEntitiesAreIncluded() {
        // F: is_a A and part_of OBS, with OBS obsolete and is_a A. While obsolete entities are hidden
        // the path through OBS is invisible, so F must keep its edge to A...
        assertThat(ids(client.getHierarchicalChildren(RED_A, ACTIVE_CLASSES, LARGE_PAGEABLE, true)))
                .contains(RED_F);

        // ...but once they are shown, F is reachable through OBS and its edge to A is redundant.
        Page<JsonElement> withObsolete = client.getHierarchicalChildren(RED_A, ALL_CLASSES, LARGE_PAGEABLE, true);
        assertThat(ids(withObsolete)).contains(RED_OBS).doesNotContain(RED_F);
    }

    @Test
    void aWitnessOfAnotherTypeOnlyCountsWithoutTheTypeFilter() {
        // C2's only other hierarchical parent is the OntologyIndividual row IND.
        assertThat(ids(client.getHierarchicalChildren(RED_A, ALL_CLASSES, LARGE_PAGEABLE, true)))
                .contains(RED_C2);

        Page<JsonElement> untyped = client.getHierarchicalChildren(RED_A, Map.of(), LARGE_PAGEABLE, true);
        assertThat(ids(untyped)).contains(RED_IND).doesNotContain(RED_C2);
    }

    @Test
    void aWitnessFromAnotherOntologyIsIgnored() {
        // C3's other hierarchical parent IRI only exists in graphtest2 (GRAPH_RED_Z), which lists A
        // as an ancestor -- it must not be able to make C3's edge redundant within graphtest.
        assertThat(ids(client.getHierarchicalChildren(RED_A, Map.of(), LARGE_PAGEABLE, true)))
                .contains(RED_C3);
    }

    @Test
    void independentParentsAreNeverRedundant() {
        // G: is_a A and part_of H, with H unrelated to A.
        assertThat(ids(client.getHierarchicalChildren(RED_A, ACTIVE_CLASSES, LARGE_PAGEABLE, true)))
                .contains(RED_G);
        assertThat(ids(client.getHierarchicalChildren(RED_H, ACTIVE_CLASSES, LARGE_PAGEABLE, true)))
                .containsExactly(RED_G);
    }

    @Test
    void edgesBetweenMembersOfAHierarchicalCycleAreNeverRedundant() {
        // X <-> Y is a cycle and both are is_a A: neither is a "more specific" path to the other,
        // so both keep their edge to A (dropping both would disconnect them from A entirely).
        assertThat(ids(client.getHierarchicalChildren(RED_A, ACTIVE_CLASSES, LARGE_PAGEABLE, true)))
                .contains(RED_X, RED_Y);

        // P <-> Q is a cycle between two parents of R: R stays under both, and the cycle edges
        // themselves are kept.
        assertThat(ids(client.getHierarchicalChildren(RED_P, ACTIVE_CLASSES, LARGE_PAGEABLE, true)))
                .containsExactlyInAnyOrder(RED_Q, RED_R);
        assertThat(ids(client.getHierarchicalChildren(RED_Q, ACTIVE_CLASSES, LARGE_PAGEABLE, true)))
                .containsExactlyInAnyOrder(RED_P, RED_R);
    }

    @Test
    void excludingRedundantEdgesWorksForIndividualHierarchies() {
        assertThat(ids(client.getHierarchicalChildren(RED_I_PARENT, ACTIVE_INDIVIDUALS, LARGE_PAGEABLE, false)))
                .containsExactlyInAnyOrder(RED_I_MID, RED_I_LEAF);
        assertThat(ids(client.getHierarchicalChildren(RED_I_PARENT, ACTIVE_INDIVIDUALS, LARGE_PAGEABLE, true)))
                .containsExactly(RED_I_MID);
        assertThat(ids(client.getHierarchicalChildren(RED_I_MID, ACTIVE_INDIVIDUALS, LARGE_PAGEABLE, true)))
                .containsExactly(RED_I_LEAF);
    }

    @Test
    void excludingRedundantEdgesLeavesSingleParentChildrenUntouched() {
        // GRAPH_PARENT's children each have exactly one hierarchical parent: nothing is redundant.
        Page<JsonElement> page = client.getHierarchicalChildren(GRAPH_PARENT, Map.of(), ANY_PAGEABLE, true);

        assertThat(ids(page)).containsExactlyInAnyOrder(GRAPH_CHILD_ACTIVE, GRAPH_CHILD_OBSOLETE, GRAPH_CHILD_PROPERTY);
    }
}
