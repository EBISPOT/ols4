package uk.ac.ebi.spot.ols.repository.v2.helpers;

import uk.ac.ebi.spot.ols.repository.solr.SearchType;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrQuery;

import java.util.Map;

public class V2DynamicFilterParser {

    public static void addDynamicFiltersToQuery(OlsSolrQuery query, Map<String, String> properties) {
        for (String k : properties.keySet()) {
            if(k.equals("searchFields") || k.equals("boostFields") || k.equals("facetFields") || k.equals("lang")) {
                continue;
            }
            String value = properties.get(k);
            k = k.replace(":", "__");
            query.addFilter(k, value, SearchType.CASE_INSENSITIVE_TOKENS);
        }
    }
}
