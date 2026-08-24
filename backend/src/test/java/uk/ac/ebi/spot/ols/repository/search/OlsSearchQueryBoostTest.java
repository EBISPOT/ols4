package uk.ac.ebi.spot.ols.repository.search;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OlsSearchQueryBoostTest {

    @Test
    void addsExplicitFieldMatchBonusToRank() {
        OlsSearchQuery query = new OlsSearchQuery();
        query.setSearchText("experimental");
        query.addBoostField("definition", null, 1000, SearchType.CASE_INSENSITIVE_TOKENS);

        String sql = DSL.using(SQLDialect.POSTGRES).renderInlined(query.buildRankExpression(null));

        assertThat(sql)
                .contains("ols_tsvector(\"definition\")")
                .contains("experimental:*")
                .contains("cast(1E3 as double precision)");
    }
}
