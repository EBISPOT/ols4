package uk.ac.ebi.spot.ols.repository.search;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OlsSearchQueryIriTest {

    private static final String TERM_IRI =
            "http://purl.obolibrary.org/obo/NCIT_C2985";

    @Test
    void defaultIriSearchUsesTheExactIriPath() {
        OlsSearchQuery query = new OlsSearchQuery();
        query.setSearchText(TERM_IRI);

        String sql = DSL.using(SQLDialect.POSTGRES)
                .renderInlined(query.buildCondition(Set.of()));

        assertThat(sql)
                .contains("lower(\"iri\") = '")
                .doesNotContain("\"ts_search\"");
    }

    @Test
    void exactIriMatchReceivesARelevanceBoost() {
        OlsSearchQuery query = new OlsSearchQuery();
        query.setSearchText(TERM_IRI);

        String sql = DSL.using(SQLDialect.POSTGRES)
                .renderInlined(query.buildRankExpression(null));

        assertThat(sql)
                .contains("lower(\"iri\") = '")
                .contains("cast(1E6 as double precision)")
                .doesNotContain("ts_rank_cd")
                .doesNotContain("to_tsquery");
    }

    @Test
    void explicitSearchFieldsDoNotBypassFieldRestrictionForIriQueries() {
        OlsSearchQuery query = new OlsSearchQuery();
        query.setSearchText(TERM_IRI);
        query.setSearchFields(List.of("label"));

        String sql = DSL.using(SQLDialect.POSTGRES)
                .renderInlined(query.buildCondition(Set.of()));

        assertThat(sql).doesNotContain("lower(\"iri\") = '");
    }
}
