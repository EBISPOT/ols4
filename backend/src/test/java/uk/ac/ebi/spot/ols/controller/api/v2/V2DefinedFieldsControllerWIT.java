package uk.ac.ebi.spot.ols.controller.api.v2;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.ols.config.WebConfig;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.ols.shared.DefinedFields;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(V2DefinedFieldsController.class)
@ContextConfiguration(classes = {
        V2DefinedFieldsController.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V2DefinedFieldsControllerWIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsOneJsonEntryPerDefinedFieldsEnumMember() throws Exception {
        mockMvc.perform(get("/api/v2/defined-fields"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(DefinedFields.values().length));
    }

    @Test
    void serializesTheDeclaredFieldNamesForTheFirstEntry() throws Exception {
        DefinedFields first = DefinedFields.values()[0];

        mockMvc.perform(get("/api/v2/defined-fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ols4FieldName").value(first.getText()))
                .andExpect(jsonPath("$[0].ols3FieldName").value(first.getOls3Text()))
                .andExpect(jsonPath("$[0].description").value(first.getDescription()))
                .andExpect(jsonPath("$[0].dataType").value(first.getType()));
    }

    @Test
    void serializesAFullEntryWithANonEmptyOls3FieldName() throws Exception {
        int index = DefinedFields.IS_OBSOLETE.ordinal();

        mockMvc.perform(get("/api/v2/defined-fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[" + index + "].ols4FieldName").value(DefinedFields.IS_OBSOLETE.getText()))
                .andExpect(jsonPath("$[" + index + "].ols3FieldName").value(DefinedFields.IS_OBSOLETE.getOls3Text()))
                .andExpect(jsonPath("$[" + index + "].description").value(DefinedFields.IS_OBSOLETE.getDescription()))
                .andExpect(jsonPath("$[" + index + "].dataType").value(DefinedFields.IS_OBSOLETE.getType()));
    }

    @Test
    void returnsMethodNotAllowedForUnsupportedMethod() throws Exception {
        mockMvc.perform(post("/api/v2/defined-fields"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405));
    }
}
