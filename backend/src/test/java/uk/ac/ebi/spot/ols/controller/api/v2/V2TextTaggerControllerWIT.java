package uk.ac.ebi.spot.ols.controller.api.v2;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.ols.config.WebConfig;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.service.TextTaggerService;
import uk.ac.ebi.spot.ols.service.TextTaggerService.TaggedEntity;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(V2TextTaggerController.class)
@ContextConfiguration(classes = {
        V2TextTaggerController.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V2TextTaggerControllerWIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TextTaggerService textTaggerService;

    @MockitoBean
    private OlsSearchClient searchClient;

    // ------------------------------------------------------------------
    // POST /tag_text
    // ------------------------------------------------------------------

    @Test
    void postTagTextReturns503WhenServiceUnavailable() throws Exception {
        when(textTaggerService.isAvailable()).thenReturn(false);

        mockMvc.perform(post("/api/v2/tag_text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"insulin resistance\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Text tagger service is not available"))
                .andExpect(jsonPath("$.message").value(
                        "The text tagger database has not been configured or the binary is not on the PATH"));

        verify(textTaggerService, never()).tagText(
                anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyInt(), anyBoolean());
    }

    @Test
    void postTagTextReturns400WhenTextFieldIsMissing() throws Exception {
        when(textTaggerService.isAvailable()).thenReturn(true);

        mockMvc.perform(post("/api/v2/tag_text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Missing required field: text"));
    }

    @Test
    void postTagTextReturns400WhenTextFieldIsEmpty() throws Exception {
        when(textTaggerService.isAvailable()).thenReturn(true);

        mockMvc.perform(post("/api/v2/tag_text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required field: text"));
    }

    @Test
    void postTagTextUsesDefaultsWhenOptionalParametersAreOmitted() throws Exception {
        when(textTaggerService.isAvailable()).thenReturn(true);
        when(textTaggerService.tagText(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyInt(), anyBoolean()))
                .thenReturn(new ArrayList<>());

        mockMvc.perform(post("/api/v2/tag_text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"insulin resistance\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("insulin resistance"))
                .andExpect(jsonPath("$.entities").isArray())
                .andExpect(jsonPath("$.entities.length()").value(0));

        verify(textTaggerService).tagText(
                eq("insulin resistance"), isNull(), isNull(), isNull(), eq(3), eq(true));
    }

    @Test
    void postTagTextForwardsRepeatedOntologyIdAndSourceAndEveryOtherParameter() throws Exception {
        when(textTaggerService.isAvailable()).thenReturn(true);
        when(textTaggerService.tagText(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyInt(), anyBoolean()))
                .thenReturn(new ArrayList<>());

        mockMvc.perform(post("/api/v2/tag_text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"insulin resistance\"}")
                        .param("ontologyId", "efo")
                        .param("ontologyId", "hp")
                        .param("source", "sssom-mappings")
                        .param("source", "manual-curation")
                        .param("delimiters", "|")
                        .param("minLength", "5")
                        .param("includeSubstrings", "false"))
                .andExpect(status().isOk());

        verify(textTaggerService).tagText(
                eq("insulin resistance"),
                eq(List.of("efo", "hp")),
                eq(List.of("sssom-mappings", "manual-curation")),
                eq("|"),
                eq(5),
                eq(false));
    }

    @Test
    void postTagTextMapsAllFieldsAndOmitsOptionalFieldsWhenAbsent() throws Exception {
        when(textTaggerService.isAvailable()).thenReturn(true);
        when(textTaggerService.tagText(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyInt(), anyBoolean()))
                .thenReturn(new ArrayList<>(List.of(
                        new TaggedEntity(0, 7, "insulin", "http://purl.obolibrary.org/obo/CHEBI_5931", "chebi",
                                "exact", "sssom-mappings", List.of("chemical", "hormone"), true),
                        new TaggedEntity(8, 18, "resistance", "http://example.org/HP_0000001", "hp",
                                null, null, null, false))));

        mockMvc.perform(post("/api/v2/tag_text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"insulin resistance\"}")
                        .param("includeObsoleteEntities", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.text").value("insulin resistance"))
                .andExpect(jsonPath("$.entities.length()").value(2))
                .andExpect(jsonPath("$.entities[0].start").value(0))
                .andExpect(jsonPath("$.entities[0].end").value(7))
                .andExpect(jsonPath("$.entities[0].term_label").value("insulin"))
                .andExpect(jsonPath("$.entities[0].term_iri")
                        .value("http://purl.obolibrary.org/obo/CHEBI_5931"))
                .andExpect(jsonPath("$.entities[0].ontology_id").value("chebi"))
                .andExpect(jsonPath("$.entities[0].string_type").value("exact"))
                .andExpect(jsonPath("$.entities[0].source").value("sssom-mappings"))
                .andExpect(jsonPath("$.entities[0].subject_categories[0]").value("chemical"))
                .andExpect(jsonPath("$.entities[0].subject_categories[1]").value("hormone"))
                .andExpect(jsonPath("$.entities[0].is_obsolete").value(true))
                .andExpect(jsonPath("$.entities[1].term_label").value("resistance"))
                .andExpect(jsonPath("$.entities[1].string_type").doesNotExist())
                .andExpect(jsonPath("$.entities[1].source").doesNotExist())
                .andExpect(jsonPath("$.entities[1].subject_categories").doesNotExist())
                .andExpect(jsonPath("$.entities[1].is_obsolete").doesNotExist());
    }

    @Test
    void postTagTextExcludesObsoleteEntitiesByDefault() throws Exception {
        when(textTaggerService.isAvailable()).thenReturn(true);
        when(textTaggerService.tagText(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyInt(), anyBoolean()))
                .thenReturn(new ArrayList<>(List.of(
                        new TaggedEntity(0, 7, "insulin", "http://example.org/CHEBI_5931", "chebi",
                                null, null, null, true),
                        new TaggedEntity(8, 18, "resistance", "http://example.org/HP_0000001", "hp",
                                null, null, null, false))));

        mockMvc.perform(post("/api/v2/tag_text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"insulin resistance\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities.length()").value(1))
                .andExpect(jsonPath("$.entities[0].term_label").value("resistance"));
    }

    @Test
    void postTagTextRejectsMalformedMinLengthWithStableErrorFields() throws Exception {
        when(textTaggerService.isAvailable()).thenReturn(true);

        mockMvc.perform(post("/api/v2/tag_text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"insulin resistance\"}")
                        .param("minLength", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Method parameter 'minLength': Failed to convert value of type "
                                + "'java.lang.String' to required type 'int'; "
                                + "For input string: \"not-a-number\""));
    }

    @Test
    void postTagTextRejectsMalformedIncludeSubstringsWithStableErrorFields() throws Exception {
        when(textTaggerService.isAvailable()).thenReturn(true);

        mockMvc.perform(post("/api/v2/tag_text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"insulin resistance\"}")
                        .param("includeSubstrings", "not-a-boolean"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Method parameter 'includeSubstrings': Failed to convert value of type "
                                + "'java.lang.String' to required type 'boolean'; "
                                + "Invalid boolean value [not-a-boolean]"));
    }

    @Test
    void postTagTextRejectsMalformedIncludeObsoleteEntitiesWithStableErrorFields() throws Exception {
        when(textTaggerService.isAvailable()).thenReturn(true);

        mockMvc.perform(post("/api/v2/tag_text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"insulin resistance\"}")
                        .param("includeObsoleteEntities", "not-a-boolean"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Method parameter 'includeObsoleteEntities': Failed to convert value of type "
                                + "'java.lang.String' to required type 'boolean'; "
                                + "Invalid boolean value [not-a-boolean]"));
    }

    @Test
    void tagTextRouteReturnsMethodNotAllowedForUnsupportedMethod() throws Exception {
        mockMvc.perform(delete("/api/v2/tag_text"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405));
    }

    // ------------------------------------------------------------------
    // GET /tag_text
    // ------------------------------------------------------------------

    @Test
    void getTagTextReportsAvailableTrue() throws Exception {
        when(textTaggerService.isAvailable()).thenReturn(true);

        mockMvc.perform(get("/api/v2/tag_text"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void getTagTextReportsAvailableFalse() throws Exception {
        when(textTaggerService.isAvailable()).thenReturn(false);

        mockMvc.perform(get("/api/v2/tag_text"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    // ------------------------------------------------------------------
    // GET /curation_sources
    // ------------------------------------------------------------------

    @Test
    void getCurationSourcesReturnsTheSearchClientsDistinctSources() throws Exception {
        when(searchClient.getDistinctCuratedSources())
                .thenReturn(List.of("sssom-mappings", "manual-curation"));

        mockMvc.perform(get("/api/v2/curation_sources"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("sssom-mappings"))
                .andExpect(jsonPath("$[1]").value("manual-curation"));
    }

    @Test
    void curationSourcesRouteReturnsMethodNotAllowedForUnsupportedMethod() throws Exception {
        mockMvc.perform(post("/api/v2/curation_sources"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }
}
