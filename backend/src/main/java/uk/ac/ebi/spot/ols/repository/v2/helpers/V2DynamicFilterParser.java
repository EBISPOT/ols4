package uk.ac.ebi.spot.ols.repository.v2.helpers;

import uk.ac.ebi.spot.ols.repository.solr.SearchType;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrQuery;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public class V2DynamicFilterParser {

    public static void addDynamicFiltersToQuery(OlsSolrQuery query, Map<String, Collection<String>> properties) {
        for (String k : properties.keySet()) {
            if(k.equals("searchFields") || k.equals("boostFields") || k.equals("facetFields") || k.equals("lang")) {
                continue;
            }
            for(String v : properties.get(k)) {
                String solrKey = k.replace(":", "__");
                query.addFilter(solrKey, Arrays.asList( v.split(",") ), SearchType.CASE_INSENSITIVE_TOKENS);
            }
        }
    }
}
