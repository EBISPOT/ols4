package uk.ac.ebi.spot.ols.repository.v1;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres coverage for {@link V1GraphRepository}. {@code getNode}, {@code
 * getParentsAndRelatedTo}, and {@code getRelatedFrom} are package-private methods whose parameter
 * and return types ({@code GraphNode}/{@code GraphEdge}) are declared {@code private static}
 * nested classes -- inaccessible even from this same-package test class -- so every branch below
 * is exercised through the three public {@code getGraphFor*} wrappers instead, exactly as
 * production callers use them. This mirrors the fixture-design discipline established by {@code
 * OlsPostgresClientGraphIT}'s dedicated {@code graph-fixture.json}: an isolated {@code v1graph}/
 * {@code v1graph2} ontology pair built specifically to prove the same-ontology-only join and
 * array-containment semantics this class also relies on. See {@code v1-graph-fixture.json} and
 * docs/backend-testing-strategy.md's "Implemented V1GraphRepository baseline" section for the full
 * rationale.
 */
@Testcontainers
class V1GraphRepositoryIT {

    private static final String ONTOLOGY = "v1graph";
    private static final String SUBCLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf";

    private static final String CENTER_IRI = "http://example.org/V1G_CENTER";
    private static final String PARENT_IRI = "http://example.org/V1G_PARENT";
    private static final String CHILD_IRI = "http://example.org/V1G_CHILD";
    private static final String RELATED_A_IRI = "http://example.org/V1G_RELATED_A";
    private static final String RELATED_B_IRI = "http://example.org/V1G_RELATED_B";
    private static final String PROP_IRI = "http://example.org/V1G_PROP";
    private static final String INDIV_IRI = "http://example.org/V1G_INDIV";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.V1OntologyTermRepositoryHandle repositoryHandle;
    private static V1GraphRepository graphRepository;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializeV1GraphDatabase(POSTGRES);
        repositoryHandle = PostgresIntegrationTestSupport.createV1OntologyTermRepositories(POSTGRES);
        graphRepository = repositoryHandle.graphRepository();
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> nodesOf(Map<String, Object> graph) {
        return (List<Map<String, Object>>) graph.get("nodes");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> edgesOf(Map<String, Object> graph) {
        return (List<Map<String, Object>>) graph.get("edges");
    }

    private static Map<String, Object> nodeWithIri(List<Map<String, Object>> nodes, String iri) {
        return nodes.stream()
                .filter(n -> iri.equals(n.get("iri")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No node found with iri " + iri));
    }

    private static Map<String, Object> edgeBetween(
            List<Map<String, Object>> edges, String source, String target) {
        return edges.stream()
                .filter(e -> source.equals(e.get("source")) && target.equals(e.get("target")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No edge found from " + source + " to " + target));
    }

    /**
     * The main, exhaustive proof for {@code getGraphForEntity}'s assembly logic, centered on
     * V1G_CENTER. In one call this exercises: all three unioned branches of {@code
     * getParentsAndRelatedTo} (parent: CENTER-&gt;PARENT; child: CHILD-&gt;CENTER; relatedTo:
     * CENTER-&gt;RELATED_A/B), {@code getRelatedFrom} (PARENT-&gt;CENTER, since PARENT's own
     * related_to contains CENTER's iri), node dedup by iri (PARENT is reachable via both the
     * parent branch and getRelatedFrom, yet appears exactly once), the same-ontology-only filter
     * on every branch (v1graph2's CROSS_PARENT_DUP/CROSS_CHILD/CROSS_RELATED_FROM are all absent,
     * and -- since CROSS_PARENT_DUP shares PARENT's own iri, which a broken filter could silently
     * dedup away without changing the node count -- PARENT's own label is asserted to prove the
     * *correct* row, not the cross-ontology duplicate, was the one joined), and the full 3-way
     * edge-label resolution: a relatedTo edge whose property resolves to a real collected label
     * (CENTER-&gt;RELATED_A), one whose property can't be resolved at all so it falls back to the
     * generic "related to" with no "uri" field (CENTER-&gt;RELATED_B), and two cases where a
     * property resolves to a URI with no matching collected label so they fall back to "is a" --
     * the hardcoded subClassOf edges (CENTER-&gt;PARENT, CHILD-&gt;CENTER) and, more subtly, the
     * relatedFrom edge (PARENT-&gt;CENTER), whose property URI is resolved by searching PARENT's
     * own _json.relatedTo (the *other* entity's json, not CENTER's) -- proving the two
     * findRelatedPropertyUri lookup directions are genuinely different, not assumed symmetric.
     */
    @Test
    void getGraphForClass_assemblesFullGraph_dedupesNodesAndResolvesEdgeLabels() {
        Map<String, Object> graph = graphRepository.getGraphForClass(CENTER_IRI, ONTOLOGY, "en");

        List<Map<String, Object>> nodes = nodesOf(graph);
        List<Map<String, Object>> edges = edgesOf(graph);

        assertThat(nodes).hasSize(5);
        assertThat(nodes).extracting(n -> n.get("iri")).containsExactlyInAnyOrder(
                CENTER_IRI, PARENT_IRI, CHILD_IRI, RELATED_A_IRI, RELATED_B_IRI);

        assertThat(nodeWithIri(nodes, CENTER_IRI)).containsEntry("label", "Center Class");
        // Proves the same-ontology filter picked the real v1graph PARENT row, not v1graph2's
        // "Cross Parent Duplicate" row that shares this exact iri.
        assertThat(nodeWithIri(nodes, PARENT_IRI)).containsEntry("label", "Parent Class");
        assertThat(nodeWithIri(nodes, CHILD_IRI)).containsEntry("label", "Child Class");
        assertThat(nodeWithIri(nodes, RELATED_A_IRI)).containsEntry("label", "Related A");
        assertThat(nodeWithIri(nodes, RELATED_B_IRI)).containsEntry("label", "Related B");

        assertThat(edges).hasSize(5);

        // Parent branch: edge points from-source-to-parent, hardcoded subClassOf, no collected
        // label anywhere for that predicate -> falls back to "is a".
        Map<String, Object> parentEdge = edgeBetween(edges, CENTER_IRI, PARENT_IRI);
        assertThat(parentEdge).containsEntry("uri", SUBCLASS_OF).containsEntry("label", "is a");

        // Child branch: edge points from-child-to-source, same subClassOf predicate and fallback.
        Map<String, Object> childEdge = edgeBetween(edges, CHILD_IRI, CENTER_IRI);
        assertThat(childEdge).containsEntry("uri", SUBCLASS_OF).containsEntry("label", "is a");

        // relatedTo branch, resolvable case: CENTER's own _json.relatedTo has an entry for
        // RELATED_A, whose property has a real collected label via linkedEntities.
        Map<String, Object> relatedAEdge = edgeBetween(edges, CENTER_IRI, RELATED_A_IRI);
        assertThat(relatedAEdge)
                .containsEntry("uri", "http://example.org/hasRelatedA")
                .containsEntry("label", "has related a");

        // relatedTo branch, unresolvable case: RELATED_B is absent from CENTER's own
        // _json.relatedTo, so findRelatedPropertyUri returns null -> generic fallback, and "uri"
        // is omitted entirely (not present with a null value).
        Map<String, Object> relatedBEdge = edgeBetween(edges, CENTER_IRI, RELATED_B_IRI);
        assertThat(relatedBEdge).containsEntry("label", "related to").doesNotContainKey("uri");

        // getRelatedFrom: edge points from-the-other-entity-to-the-source (PARENT -> CENTER).
        // Its property is resolved by searching PARENT's *own* _json.relatedTo for CENTER's iri
        // (not CENTER's json, which has no entry for this pairing) -- the reverse lookup
        // direction from the relatedTo-branch case above. No node's linkedEntities carries a
        // label for this property, so it falls back to "is a" too.
        Map<String, Object> relatedFromEdge = edgeBetween(edges, PARENT_IRI, CENTER_IRI);
        assertThat(relatedFromEdge)
                .containsEntry("uri", "http://example.org/hasParentToCenter")
                .containsEntry("label", "is a");
    }

    @Test
    void getGraphForClass_nonexistentEntity_returnsEmptyGraphNotCrash() {
        Map<String, Object> graph = graphRepository.getGraphForClass(
                "http://example.org/DOES_NOT_EXIST", ONTOLOGY, "en");

        assertThat(nodesOf(graph)).isEmpty();
        assertThat(edgesOf(graph)).isEmpty();
    }

    /**
     * Confirms the composite entity-id format {@code ontologyId + "+" + type + "+" + iri} used by
     * getGraphForEntity actually matches how a property-typed row is keyed in ols_entities.id
     * ("v1graph+property+..."), the same "+property+" convention PropertyRepository/
     * V1PropertyRepository use in production.
     */
    @Test
    void getGraphForProperty_usesPropertyEntityIdFormat() {
        Map<String, Object> graph = graphRepository.getGraphForProperty(PROP_IRI, ONTOLOGY, "en");

        List<Map<String, Object>> nodes = nodesOf(graph);
        List<Map<String, Object>> edges = edgesOf(graph);

        assertThat(nodes).hasSize(2);
        assertThat(nodes).extracting(n -> n.get("iri"))
                .containsExactlyInAnyOrder(PROP_IRI, PARENT_IRI);

        assertThat(edges).hasSize(1);
        Map<String, Object> parentEdge = edgeBetween(edges, PROP_IRI, PARENT_IRI);
        assertThat(parentEdge).containsEntry("uri", SUBCLASS_OF).containsEntry("label", "is a");
    }

    /**
     * Same confirmation as above for the "+individual+" id convention IndividualRepository/
     * V1IndividualRepository use in production.
     */
    @Test
    void getGraphForIndividual_usesIndividualEntityIdFormat() {
        Map<String, Object> graph = graphRepository.getGraphForIndividual(INDIV_IRI, ONTOLOGY, "en");

        List<Map<String, Object>> nodes = nodesOf(graph);
        List<Map<String, Object>> edges = edgesOf(graph);

        assertThat(nodes).hasSize(2);
        assertThat(nodes).extracting(n -> n.get("iri"))
                .containsExactlyInAnyOrder(INDIV_IRI, PARENT_IRI);

        assertThat(edges).hasSize(1);
        Map<String, Object> parentEdge = edgeBetween(edges, INDIV_IRI, PARENT_IRI);
        assertThat(parentEdge).containsEntry("uri", SUBCLASS_OF).containsEntry("label", "is a");
    }
}
