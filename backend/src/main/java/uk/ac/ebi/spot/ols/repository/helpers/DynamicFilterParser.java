package uk.ac.ebi.spot.ols.repository.helpers;

import uk.ac.ebi.spot.ols.repository.search.SearchType;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DynamicFilterParser {

    public static void addDynamicFiltersToQuery(OlsSearchQuery query, Map<String, Collection<String>> properties) {
        if(properties == null) {
            return;
        }
        for (String k : properties.keySet()) {
            if(k.equals("searchFields") || k.equals("boostFields") || k.equals("facetFields") || k.equals("lang")) {
                continue;
            }
            // "q" is a legacy alias for the search text parameter used by many OLS clients
            if (k.equals("q")) {
                if (query.getSearchText() == null) {
                    Collection<String> vals = properties.get(k);
                    if (vals != null && !vals.isEmpty()) {
                        query.setSearchText(vals.iterator().next());
                    }
                }
                continue;
            }
            List<String> values = new ArrayList<>();
            for (String value : properties.get(k)) {
                values.addAll(List.of(value.split(",")));
            }
            if (!values.isEmpty()) {
                String filterKey = k.replace(":", "__");
                query.addFilter(filterKey, values, SearchType.CASE_INSENSITIVE_TOKENS);
            }
        }
    }
}
