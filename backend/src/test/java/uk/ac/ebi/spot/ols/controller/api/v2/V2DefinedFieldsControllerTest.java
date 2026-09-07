package uk.ac.ebi.spot.ols.controller.api.v2;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.ols.shared.DefinedFields;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class V2DefinedFieldsControllerTest {

    private final V2DefinedFieldsController controller = new V2DefinedFieldsController();

    @Test
    void returnsOneEntryPerDefinedFieldsEnumMember() {
        List<V2DefinedFieldsController.DefinedFieldDto> result = controller.getDefinedFields();

        assertEquals(DefinedFields.values().length, result.size());
    }

    @Test
    void mapsEveryEntryFromItsCorrespondingEnumMemberInDeclarationOrder() {
        List<V2DefinedFieldsController.DefinedFieldDto> result = controller.getDefinedFields();

        DefinedFields[] fields = DefinedFields.values();
        for (int i = 0; i < fields.length; i++) {
            DefinedFields field = fields[i];
            V2DefinedFieldsController.DefinedFieldDto dto = result.get(i);

            assertEquals(field.getText(), dto.getOls4FieldName(), "ols4FieldName for " + field);
            assertEquals(field.getOls3Text(), dto.getOls3FieldName(), "ols3FieldName for " + field);
            assertEquals(field.getDescription(), dto.getDescription(), "description for " + field);
            assertEquals(field.getType(), dto.getDataType(), "dataType for " + field);
        }
    }
}
