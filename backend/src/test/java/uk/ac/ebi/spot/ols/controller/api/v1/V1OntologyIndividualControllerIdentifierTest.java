package uk.ac.ebi.spot.ols.controller.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.repository.v1.V1IndividualRepository;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V1OntologyIndividualControllerIdentifierTest {

    private V1OntologyIndividualController controller;
    private V1IndividualRepository repository;
    private PagedResourcesAssembler<V1Individual> assembler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(V1IndividualRepository.class);
        assembler = mock(PagedResourcesAssembler.class);
        controller = new V1OntologyIndividualController();
        ReflectionTestUtils.setField(controller, "individualRepository", repository);
        controller.individualAssembler = mock(V1IndividualAssembler.class);
    }

    @Test
    void forwardsLanguageBeforeShortForm() {
        when(repository.findByOntologyAndShortForm("efo", "fr", "EFO_I100"))
                .thenReturn(new V1Individual());

        controller.getAllIndividualsByOntology(
                "EFO", null, "EFO_I100", null, "fr", PageRequest.of(0, 20), assembler);

        verify(repository).findByOntologyAndShortForm("efo", "fr", "EFO_I100");
    }

    @Test
    void forwardsLanguageBeforeOboId() {
        when(repository.findByOntologyAndOboId("efo", "fr", "EFO:I100"))
                .thenReturn(new V1Individual());

        controller.getAllIndividualsByOntology(
                "EFO", null, null, "EFO:I100", "fr", PageRequest.of(0, 20), assembler);

        verify(repository).findByOntologyAndOboId("efo", "fr", "EFO:I100");
    }
}
