package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.ols.config.WebConfig;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.repository.*;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import java.net.URI;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({V2ClassController.class, V2IndividualController.class, V2PropertyController.class})
@ContextConfiguration(classes = {V2ClassController.class, V2IndividualController.class,
        V2PropertyController.class, GlobalExceptionHandler.class, WebConfig.class})
class NativeTreeHttpWIT {
    @Autowired MockMvc mvc;
    @MockitoBean ClassRepository classes;
    @MockitoBean IndividualRepository individuals;
    @MockitoBean PropertyRepository properties;
    private static final String PREFIX = "/api/v2/ontologies/native/";
    private static final String IRI = "https%253A%252F%252Fexample.org%252Fnative%2523R";

    @Test
    void treeContextBindsAndReachesEveryChildRepositoryWithSubsetAndVisibility() throws Exception {
        when(classes.getChildrenByOntologyId(any(), any(), any(), anyBoolean(), anyBoolean(), any(), any(), anyBoolean(), any(), any())).thenReturn(Page.empty());
        when(classes.getHierarchicalChildrenByOntologyId(any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), any(), any())).thenReturn(Page.empty());
        when(individuals.getHierarchicalChildrenByOntologyId(any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), any(), any())).thenReturn(Page.empty());
        when(individuals.getIndividualsOfClass(any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), any(), any()))
                .thenReturn(new OlsFacetedResultsPage<JsonElement>(List.of(), Map.of(), org.springframework.data.domain.PageRequest.of(0,20), 0));
        when(properties.getChildrenByOntologyId(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any(), any())).thenReturn(Page.empty());
        for (String route : List.of("classes/" + IRI + "/children", "classes/" + IRI + "/hierarchicalChildren",
                "individuals/" + IRI + "/hierarchicalChildren", "classes/" + IRI + "/individuals", "properties/" + IRI + "/children")) {
            mvc.perform(get(URI.create(PREFIX + route)).param("treeContext", "true").param("subsetTree", "S,S2")
                    .param("includeImportedEntities", "false").param("includeObsoleteEntities", "false"))
                    .andExpect(status().isOk());
        }
        verify(classes).getChildrenByOntologyId(eq("native"), any(), eq("https://example.org/native#R"), eq(false), eq(false), isNull(), eq(List.of("S","S2")), eq(true), eq("en"), any());
        verify(classes).getHierarchicalChildrenByOntologyId(eq("native"), any(), any(), eq(false), eq(false), eq(List.of("S","S2")), eq(true), eq("en"), any());
        verify(individuals).getHierarchicalChildrenByOntologyId(eq("native"), any(), any(), eq(false), eq(false), eq(List.of("S","S2")), eq(true), eq("en"), any());
        verify(individuals).getIndividualsOfClass(eq("native"), any(), any(), eq(false), eq(false), eq(List.of("S","S2")), eq(true), eq("en"), any());
        verify(properties).getChildrenByOntologyId(eq("native"), any(), any(), eq(false), eq(List.of("S","S2")), eq(true), eq("en"), any());
    }

    @Test
    void treeContextRootFlagIsForwardedAsControlInputInsteadOfDiscarded() throws Exception {
        when(classes.findByOntologyId(any(), any(), any(), any(), any(), any(), anyBoolean(), anyMap(), any()))
                .thenReturn(new OlsFacetedResultsPage<JsonElement>(List.of(), Map.of(), org.springframework.data.domain.PageRequest.of(0,20), 0));
        mvc.perform(get(PREFIX + "classes").param("treeContext", "true").param("subsetTree", "S")
                .param("hasDirectParents", "false")).andExpect(status().isOk());
        verify(classes).findByOntologyId(eq("native"), any(), eq("en"), any(), any(), any(), eq(false),
                argThat(p -> p.get("treeContext").equals(List.of("true")) && p.get("subsetTree").equals(List.of("S"))), any());
    }
}
