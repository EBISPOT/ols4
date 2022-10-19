package uk.ac.ebi.spot.ols.repository.v2.helpers;

import uk.ac.ebi.spot.ols.repository.solr.Fuzziness;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrQuery;

import java.util.Map;

public class V2DynamicFilterParser {

    public static void addDynamicFiltersToQuery(OlsSolrQuery query, Map<String, String> properties) {
        for (String k : properties.keySet()) {
            String value = properties.get(k);
            k = k.replace(":", "__");
            query.addFilter(k, value, Fuzziness.CASE_INSENSITIVE_SUBSTRING);
        }
    }
}
