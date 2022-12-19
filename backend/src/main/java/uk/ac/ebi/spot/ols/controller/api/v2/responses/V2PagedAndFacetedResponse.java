package uk.ac.ebi.spot.ols.controller.api.v2.responses;

import uk.ac.ebi.spot.ols.repository.solr.OlsFacetedResultsPage;

import java.util.Map;

public class V2PagedAndFacetedResponse<T> extends V2PagedResponse<T> {

    public V2PagedAndFacetedResponse(OlsFacetedResultsPage<T> page) {
        super(page);
        this.facetFieldsToCounts = page.facetFieldToCounts;
    }

    public Map<String, Map<String, Long>> facetFieldsToCounts;
}
