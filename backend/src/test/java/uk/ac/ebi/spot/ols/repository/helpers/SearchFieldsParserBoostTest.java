package uk.ac.ebi.spot.ols.repository.helpers;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery;
import uk.ac.ebi.spot.ols.repository.search.SearchType;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchFieldsParserBoostTest {

    @Test
    void preservesExplicitBoostWeights() {
        RecordingQuery query = new RecordingQuery();

        SearchFieldsParser.addBoostFieldsToQuery(query, "definition^1000 label^25");

        assertThat(query.boosts).containsExactly(
                new RecordedBoost("definition", null, 1000, SearchType.CASE_INSENSITIVE_TOKENS),
                new RecordedBoost("label", null, 25, SearchType.CASE_INSENSITIVE_TOKENS));
    }

    private static class RecordingQuery extends OlsSearchQuery {
        private final List<RecordedBoost> boosts = new ArrayList<>();

        @Override
        public void addBoostField(
                String propertyName,
                String propertyValue,
                int weight,
                SearchType searchType) {
            boosts.add(new RecordedBoost(propertyName, propertyValue, weight, searchType));
        }
    }

    private record RecordedBoost(
            String propertyName,
            String propertyValue,
            int weight,
            SearchType searchType) {
    }
}
