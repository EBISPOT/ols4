package uk.ac.ebi.spot.ols.repository.helpers;

import uk.ac.ebi.spot.ols.repository.search.SearchType;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery;

import java.util.Arrays;
import java.util.Collection;
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
            for(String v : properties.get(k)) {
                String filterKey = k.replace(":", "__");
                query.addFilter(filterKey, Arrays.asList( v.split(",") ), SearchType.CASE_INSENSITIVE_TOKENS);
            }
        }
    }
}
