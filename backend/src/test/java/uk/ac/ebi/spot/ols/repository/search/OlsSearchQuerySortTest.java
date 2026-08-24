package uk.ac.ebi.spot.ols.repository.search;

import org.jooq.SortOrder;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OlsSearchQuerySortTest {

    @Test
    void mapsApiSortFieldsAndAddsDeterministicTieBreaker() {
        OlsSearchQuery query = new OlsSearchQuery();

        var orderBy = query.buildOrderBy(Sort.by(Sort.Direction.DESC, "ontologyId"));

        assertThat(orderBy).extracting(field -> field.getName())
                .containsExactly("ontology_id", "id");
        assertThat(orderBy).extracting(field -> field.getOrder())
                .containsExactly(SortOrder.DESC, SortOrder.ASC);
    }

    @Test
    void rejectsUnknownAndArraySortFields() {
        OlsSearchQuery query = new OlsSearchQuery();

        assertThatThrownBy(() -> query.buildOrderBy(Sort.by("notARealField")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported sort field: notARealField");
        assertThatThrownBy(() -> query.buildOrderBy(Sort.by("label")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported sort field: label");
    }
}
