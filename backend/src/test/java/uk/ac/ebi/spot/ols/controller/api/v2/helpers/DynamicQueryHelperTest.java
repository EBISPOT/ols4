package uk.ac.ebi.spot.ols.controller.api.v2.helpers;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicQueryHelperTest {

    @Test
    void excludesIncludeTotalFromDynamicFilters() {
        Map<String, Collection<String>> properties = new LinkedHashMap<>();
        properties.put("includeTotal", List.of("false"));
        properties.put("ontologyId", List.of("efo"));

        assertEquals(
                Map.of("ontologyId", List.of("efo")),
                DynamicQueryHelper.filterProperties(properties));
    }
}
