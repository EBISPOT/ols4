package uk.ac.ebi.spot.ols.repository.helpers;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery;
import uk.ac.ebi.spot.ols.repository.search.SearchType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicFilterParserTest {

    @Test
    void combinesRepeatedAndCommaSeparatedValuesIntoOneAlternativeFilter() {
        RecordingQuery query = new RecordingQuery();

        DynamicFilterParser.addDynamicFiltersToQuery(
                query,
                Map.of("domain", List.of("biology,health", "information")));

        assertThat(query.filters).containsExactly(
                new RecordedFilter(
                        "domain",
                        List.of("biology", "health", "information"),
                        SearchType.CASE_INSENSITIVE_TOKENS));
    }

    private static class RecordingQuery extends OlsSearchQuery {
        private final List<RecordedFilter> filters = new ArrayList<>();

        @Override
        public void addFilter(
                String propertyName,
                Collection<String> propertyValues,
                SearchType searchType) {
            filters.add(new RecordedFilter(propertyName, List.copyOf(propertyValues), searchType));
        }
    }

    private record RecordedFilter(
            String propertyName,
            List<String> values,
            SearchType searchType) {
    }
}
