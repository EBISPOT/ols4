package uk.ac.ebi.spot.ols.controller.api.v2;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.ols.shared.DefinedFields;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
public class V2DefinedFieldsController {

    @GetMapping("/api/v2/defined-fields")
    public List<DefinedFieldDto> getDefinedFields() {
        return Stream.of(DefinedFields.values())
                .map(field -> new DefinedFieldDto(
                        field.getText(),
                        field.getOls3Text(),
                        field.getDescription(),
                        field.getType()
                ))
                .collect(Collectors.toList());
    }

    // DTO class for serialization
    static class DefinedFieldDto {
        private String ols4FieldName;
        private String ols3FieldName;
        private String description;
        private String dataType;

        public DefinedFieldDto(String ols4FieldName, String ols3FieldName, String description, String dataType) {
            this.ols4FieldName = ols4FieldName;
            this.ols3FieldName = ols3FieldName;
            this.description = description;
            this.dataType = dataType;
        }

        public String getOls4FieldName() {
            return ols4FieldName;
        }

        public String getOls3FieldName() {
            return ols3FieldName;
        }

        public String getDescription() {
            return description;
        }

        public String getDataType() {
            return dataType;
        }
    }
}
