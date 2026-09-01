package uk.ac.ebi.spot.ols.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationTest {

    @Test
    void reportsTheInvalidOntologyId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Validation.validateOntologyId("efo/unsafe"));

        assertEquals("Invalid ontology ID: efo/unsafe", exception.getMessage());
    }
}
