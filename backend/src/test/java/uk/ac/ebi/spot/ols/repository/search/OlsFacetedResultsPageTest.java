package uk.ac.ebi.spot.ols.repository.search;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit coverage for {@link OlsFacetedResultsPage}. This is a thin generic {@link
 * org.springframework.data.domain.PageImpl} subclass adding a facet-counts map alongside the
 * normal page content/pageable/total-elements -- no Postgres dependency of its own (it wraps
 * already-fetched data), so a hand-built fixture per branch is the complete "covered" bar for it,
 * per docs/backend-testing-strategy.md's Tier B methodology. The one piece of real logic worth
 * proving correct is the overridden {@link OlsFacetedResultsPage#map(java.util.function.Function)}:
 * a naive override of a page-wrapper's map() is an easy place to silently drop the facet map or
 * the total-element count during a conversion chain, so every assertion here is aimed at proving
 * that doesn't happen.
 */
class OlsFacetedResultsPageTest {

    /**
     * Shape taken directly from the real production caller, {@link OlsSearchClient#searchPaginated}:
     * a {@code LinkedHashMap} keyed by facet field name, each value itself a map from facet value to
     * document count.
     */
    private static Map<String, Map<String, Long>> sampleFacetCounts() {
        Map<String, Long> ontologyCounts = new LinkedHashMap<>();
        ontologyCounts.put("go", 42L);
        ontologyCounts.put("hp", 17L);

        Map<String, Map<String, Long>> facetFieldToCounts = new LinkedHashMap<>();
        facetFieldToCounts.put("ontology_id", ontologyCounts);
        return facetFieldToCounts;
    }

    // --- constructor -------------------------------------------------------------------------

    @Test
    void constructorStoresContentPageableTotalAndNonEmptyFacetCounts() {
        Pageable pageable = PageRequest.of(0, 5);
        Map<String, Map<String, Long>> facetFieldToCounts = sampleFacetCounts();

        OlsFacetedResultsPage<String> page =
                new OlsFacetedResultsPage<>(List.of("a", "b"), facetFieldToCounts, pageable, 2);

        assertThat(page.getContent()).containsExactly("a", "b");
        assertThat(page.getPageable()).isEqualTo(pageable);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.facetFieldToCounts).isSameAs(facetFieldToCounts);
        assertThat(page.facetFieldToCounts.get("ontology_id")).containsEntry("go", 42L).containsEntry("hp", 17L);
    }

    /**
     * {@code suggest()} and the empty-prefix short-circuit in {@code OlsSearchClient} both pass
     * {@code Map.of()} rather than {@code null} -- an empty (but non-null) facet map is a real,
     * common production shape, not just a hypothetical edge case.
     */
    @Test
    void constructorAcceptsEmptyFacetCountsMap() {
        Pageable pageable = PageRequest.of(0, 10);

        OlsFacetedResultsPage<String> page =
                new OlsFacetedResultsPage<>(List.of(), Map.of(), pageable, 0);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.facetFieldToCounts).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(0);
    }

    /**
     * {@code facetFieldToCounts} is a plain public field with no null-check in the constructor or
     * anywhere else in this class, so {@code null} is tolerated -- it is simply stored as given,
     * with no NPE and no silent substitution of an empty map.
     */
    @Test
    void constructorToleratesNullFacetCountsMap() {
        Pageable pageable = PageRequest.of(0, 10);

        OlsFacetedResultsPage<String> page =
                new OlsFacetedResultsPage<>(List.of("only"), null, pageable, 1);

        assertThat(page.facetFieldToCounts).isNull();
        assertThat(page.getContent()).containsExactly("only");
    }

    // --- map() ---------------------------------------------------------------------------------

    /**
     * The core regression this class exists to guard against: a partial page (numFound greater
     * than the number of results actually loaded onto this page, as happens on every page but the
     * last) must keep reporting the same total-element count and pageable after a .map()
     * conversion, and the facet map must survive as the exact same instance -- not dropped, and
     * not replaced with a fresh empty map.
     */
    @Test
    void mapConvertsContentInOrderWhilePreservingFacetCountsPageableAndTotalElements() {
        Pageable pageable = PageRequest.of(1, 5);
        Map<String, Map<String, Long>> facetFieldToCounts = sampleFacetCounts();
        // Partial page: 5 results loaded onto this page, but 23 total across all pages.
        OlsFacetedResultsPage<Integer> source =
                new OlsFacetedResultsPage<>(List.of(1, 2, 3, 4, 5), facetFieldToCounts, pageable, 23);

        OlsFacetedResultsPage<String> mapped = source.map(i -> "n" + i);

        assertThat(mapped.getContent()).containsExactly("n1", "n2", "n3", "n4", "n5");
        assertThat(mapped.getTotalElements()).isEqualTo(23);
        assertThat(mapped.getPageable()).isEqualTo(pageable);
        assertThat(mapped.facetFieldToCounts).isSameAs(facetFieldToCounts);
    }

    @Test
    void mapPreservesNullFacetCountsMapRatherThanSubstitutingAnEmptyOne() {
        Pageable pageable = PageRequest.of(0, 5);
        OlsFacetedResultsPage<Integer> source =
                new OlsFacetedResultsPage<>(List.of(1, 2), null, pageable, 2);

        OlsFacetedResultsPage<String> mapped = source.map(Object::toString);

        assertThat(mapped.facetFieldToCounts).isNull();
        assertThat(mapped.getContent()).containsExactly("1", "2");
    }

    @Test
    void mapOnEmptyContentPagePreservesFacetCountsPageableAndTotalElements() {
        Pageable pageable = PageRequest.of(0, 10);
        Map<String, Map<String, Long>> facetFieldToCounts = sampleFacetCounts();
        OlsFacetedResultsPage<Integer> source =
                new OlsFacetedResultsPage<>(List.of(), facetFieldToCounts, pageable, 0);

        OlsFacetedResultsPage<String> mapped = source.map(i -> "n" + i);

        assertThat(mapped.getContent()).isEmpty();
        assertThat(mapped.getTotalElements()).isEqualTo(0);
        assertThat(mapped.getPageable()).isEqualTo(pageable);
        assertThat(mapped.facetFieldToCounts).isSameAs(facetFieldToCounts);
    }
}
