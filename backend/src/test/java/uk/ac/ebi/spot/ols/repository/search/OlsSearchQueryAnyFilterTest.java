package uk.ac.ebi.spot.ols.repository.search;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OlsSearchQueryAnyFilterTest {

    @Test
    void combinesAlternativeFieldsWithOrInsideTheSurroundingFilters() {
        OlsSearchQuery query = new OlsSearchQuery();
        query.addFilter("isObsolete", List.of("false"), SearchType.WHOLE_FIELD);
        query.addAnyFilter(Map.of(
                "iri", List.of("http://example.org/PARENT"),
                "hierarchicalAncestor", List.of("http://example.org/PARENT")),
                SearchType.WHOLE_FIELD);

        String sql = query.buildCondition(Set.of()).toString();

        assertThat(sql)
                .contains("is_obsolete")
                .contains("iri")
                .contains("hierarchical_ancestors")
                .contains(" or ");
    }
}
