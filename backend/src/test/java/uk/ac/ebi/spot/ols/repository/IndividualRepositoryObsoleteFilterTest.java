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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IndividualRepositoryObsoleteFilterTest {

    @Test
    void classMembershipExcludesObsoleteIndividualsUnlessExplicitlyIncluded() throws Exception {
        RecordingSearchClient searchClient = new RecordingSearchClient();
        IndividualRepository repository = new IndividualRepository();
        repository.searchClient = searchClient;

        repository.getIndividualsOfClass(
                "efo", "http://example.org/EFO_0001", PageRequest.of(0, 20), false, "en",
                new JsonTransformOptions());
        assertThat(searchClient.condition())
                .contains("is_obsolete")
                .contains("false");

        repository.getIndividualsOfClass(
                "efo", "http://example.org/EFO_0001", PageRequest.of(0, 20), true, "en",
                new JsonTransformOptions());
        assertThat(searchClient.condition()).doesNotContain("is_obsolete");
    }

    private static class RecordingSearchClient extends OlsSearchClient {
        private OlsSearchQuery query;

        @Override
        public OlsFacetedResultsPage<JsonElement> searchPaginated(
                OlsSearchQuery query, Pageable pageable) {
            this.query = query;
            return new OlsFacetedResultsPage<>(List.of(), Map.of(), pageable, 0);
        }

        private String condition() {
            return query.buildCondition(Set.of(
                    "filter_http://www.w3.org/1999/02/22-rdf-syntax-ns#type"))
                    .toString();
        }
    }
}
