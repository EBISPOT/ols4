package uk.ac.ebi.spot.ols.repository;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EntityRepositoryTest {

    @Test
    void forwardsIncludeTotalFalseToSearchClient() throws Exception {
        Pageable pageable = PageRequest.of(0, 5);
        RecordingSearchClient searchClient = new RecordingSearchClient();

        EntityRepository repository = new EntityRepository();
        repository.searchClient = searchClient;

        repository.find(
                pageable,
                "en",
                "liver",
                null,
                null,
                null,
                false,
                null,
                Map.of(),
                new JsonTransformOptions(),
                false);

        assertFalse(searchClient.includeTotal);
    }

    @Test
    void includesTotalByDefaultForExistingCallers() throws Exception {
        Pageable pageable = PageRequest.of(0, 5);
        RecordingSearchClient searchClient = new RecordingSearchClient();

        EntityRepository repository = new EntityRepository();
        repository.searchClient = searchClient;

        repository.find(
                pageable,
                "en",
                "liver",
                null,
                null,
                null,
                false,
                null,
                Map.of(),
                new JsonTransformOptions());

        assertEquals(Boolean.TRUE, searchClient.includeTotal);
    }

    private static class RecordingSearchClient extends OlsSearchClient {
        private Boolean includeTotal;

        @Override
        public OlsFacetedResultsPage<JsonElement> searchPaginated(
                OlsSearchQuery query, Pageable pageable, boolean includeTotal) {
            this.includeTotal = includeTotal;
            return new OlsFacetedResultsPage<>(List.of(), Map.of(), pageable, 0);
        }
    }
}
