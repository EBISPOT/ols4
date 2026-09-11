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

    private static final PageRequest ANY_PAGEABLE = PageRequest.of(0, 10);

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
}
