package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.ols.repository.OntologyRepository;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class V2OntologyControllerDynamicParametersTest {

    @Test
    void bindsRepeatedAndUriNamedDynamicParameters() throws Exception {
        RecordingOntologyRepository repository = new RecordingOntologyRepository();
        V2OntologyController controller = new V2OntologyController();
        controller.ontologyRepository = repository;

        MockMvc mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        mockMvc.perform(get("/api/v2/ontologies")
                        .param("domain", "biology", "health")
                        .param("http://example.org/category", "experimental"))
                .andExpect(status().isOk());

        assertThat(repository.properties.get("domain"))
                .containsExactly("biology", "health");
        assertThat(repository.properties.get("http://example.org/category"))
                .containsExactly("experimental");
    }

    private static class RecordingOntologyRepository extends OntologyRepository {
        private Map<String, Collection<String>> properties;

        @Override
        public OlsFacetedResultsPage<JsonElement> find(
                Pageable pageable,
                String lang,
                String search,
                String searchFields,
                String boostFields,
                boolean exactMatch,
                Map<String, Collection<String>> properties,
                JsonTransformOptions outputOpts) throws IOException {
            this.properties = properties;
            return new OlsFacetedResultsPage<>(List.of(), Map.of(), pageable, 0);
        }
    }
}
