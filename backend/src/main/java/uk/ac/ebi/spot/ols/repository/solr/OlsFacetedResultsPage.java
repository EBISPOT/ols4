package uk.ac.ebi.spot.ols.repository.solr;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class OlsFacetedResultsPage<T> extends PageImpl<T> {

    public Map<String, Map<String, Long>> facetFieldToCounts;

    public OlsFacetedResultsPage(List<T> results, Map<String, Map<String, Long>> facetFieldToCounts, Pageable pageable, long numFound) {
        super(results, pageable, numFound);
        this.facetFieldToCounts = facetFieldToCounts;
    }

    public <U> OlsFacetedResultsPage<U> map(Function<? super T, ? extends U> converter) {
        return new OlsFacetedResultsPage<U>(getConvertedContent(converter), facetFieldToCounts, getPageable(), getTotalElements());
    }




}
