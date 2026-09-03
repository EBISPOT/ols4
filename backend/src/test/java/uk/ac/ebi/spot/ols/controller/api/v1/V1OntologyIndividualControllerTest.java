package uk.ac.ebi.spot.ols.controller.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.v1.V1IndividualRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1JsTreeRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V1OntologyIndividualControllerTest {

    private V1OntologyIndividualController controller;
    private V1IndividualRepository individualRepository;
    private V1JsTreeRepository jsTreeRepository;
    private V1IndividualAssembler individualAssembler;
    private V1TermAssembler termAssembler;
    private PagedResourcesAssembler<V1Individual> individualResourcesAssembler;
    private PagedResourcesAssembler<V1Term> termResourcesAssembler;
    private Pageable pageable;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        individualRepository = mock(V1IndividualRepository.class);
        jsTreeRepository = mock(V1JsTreeRepository.class);
        individualAssembler = mock(V1IndividualAssembler.class);
        termAssembler = mock(V1TermAssembler.class);
        individualResourcesAssembler = mock(PagedResourcesAssembler.class);
        termResourcesAssembler = mock(PagedResourcesAssembler.class);
        pageable = PageRequest.of(1, 7);

        controller = new V1OntologyIndividualController();
        ReflectionTestUtils.setField(controller, "individualRepository", individualRepository);
        ReflectionTestUtils.setField(controller, "jsTreeRepository", jsTreeRepository);
        controller.individualAssembler = individualAssembler;
        controller.termAssembler = termAssembler;
    }

    @Test
    void listsOntologyIndividualsWhenNoIdentifierIsSupplied() {
        OlsFacetedResultsPage<V1Individual> individuals = new OlsFacetedResultsPage<>(
                List.of(individual()), Map.of(), pageable, 1);
        PagedModel<EntityModel<V1Individual>> assembled = PagedModel.empty();
        when(individualRepository.findAllByOntology("efo", "fr", pageable))
                .thenReturn(individuals);
        when(individualResourcesAssembler.toModel(individuals, individualAssembler))
                .thenReturn(assembled);

        var response = controller.getAllIndividualsByOntology(
                "EFO", null, null, null, "fr", pageable, individualResourcesAssembler);

        assertSame(assembled, response.getBody());
        verify(individualRepository).findAllByOntology("efo", "fr", pageable);
    }

    @Test
    void selectsIriBeforeShortFormAndOboIdUsingRepositoryArgumentOrder() {
        when(individualRepository.findByOntologyAndIri("efo", "the-iri", "fr"))
                .thenReturn(individual());

        controller.getAllIndividualsByOntology(
                "EFO", "the-iri", "EFO_I100", "EFO:I100", "fr", pageable,
                individualResourcesAssembler);

        verify(individualRepository).findByOntologyAndIri("efo", "the-iri", "fr");
        verify(individualRepository, never())
                .findByOntologyAndShortForm("efo", "fr", "EFO_I100");
        verify(individualRepository, never())
                .findByOntologyAndOboId("efo", "fr", "EFO:I100");

        when(individualRepository.findByOntologyAndShortForm("efo", "fr", "EFO_I200"))
                .thenReturn(individual());
        controller.getAllIndividualsByOntology(
                "EFO", null, "EFO_I200", "EFO:I200", "fr", pageable,
                individualResourcesAssembler);
        verify(individualRepository).findByOntologyAndShortForm("efo", "fr", "EFO_I200");

        when(individualRepository.findByOntologyAndOboId("efo", "fr", "EFO:I300"))
                .thenReturn(individual());
        controller.getAllIndividualsByOntology(
                "EFO", null, null, "EFO:I300", "fr", pageable,
                individualResourcesAssembler);
        verify(individualRepository).findByOntologyAndOboId("efo", "fr", "EFO:I300");

        controller.getAllIndividualsByOntology(
                "EFO", "missing-iri", null, null, "fr", pageable,
                individualResourcesAssembler);
        controller.getAllIndividualsByOntology(
                "EFO", null, "missing-short-form", null, "fr", pageable,
                individualResourcesAssembler);
        controller.getAllIndividualsByOntology(
                "EFO", null, null, "MISSING:1", "fr", pageable,
                individualResourcesAssembler);
        verify(individualResourcesAssembler, times(3)).toModel(null, individualAssembler);
    }

    @Test
    void getsDecodedIndividualAndReportsMissingIndividual() {
        when(individualRepository.findByOntologyAndIri(
                "efo", "http://example.org/EFO_I100", "en"))
                .thenReturn(individual());
        EntityModel<V1Individual> assembled = EntityModel.of(individual());
        when(individualAssembler.toModel(org.mockito.ArgumentMatchers.any()))
                .thenReturn(assembled);

        var response = controller.getIndividual(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_I100", "en");

        assertSame(assembled, response.getBody());
        verify(individualRepository).findByOntologyAndIri(
                "efo", "http://example.org/EFO_I100", "en");

        when(individualRepository.findByOntologyAndIri("efo", "missing", "en"))
                .thenReturn(null);
        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class,
                () -> controller.getIndividual("EFO", "missing", "en"));
        assertEquals("No individual with id missing in efo", error.getMessage());
    }

    @Test
    void delegatesDecodedDirectAndAllTypes() {
        PageImpl<V1Term> terms = new PageImpl<>(List.of(term()));
        when(individualRepository.getDirectTypes(
                "efo", "http://example.org/EFO_I100", "fr", pageable)).thenReturn(terms);
        when(individualRepository.getAllTypes(
                "efo", "http://example.org/EFO_I100", "fr", pageable)).thenReturn(terms);

        controller.getDirectTypes(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_I100", "fr", pageable,
                termResourcesAssembler);
        controller.ancestors(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_I100", "fr", pageable,
                termResourcesAssembler);

        verify(individualRepository).getDirectTypes(
                "efo", "http://example.org/EFO_I100", "fr", pageable);
        verify(individualRepository).getAllTypes(
                "efo", "http://example.org/EFO_I100", "fr", pageable);
    }

    @Test
    void serializesTheDecodedIndividualJsTree() {
        when(jsTreeRepository.getJsTreeForIndividual(
                "http://example.org/EFO_I100", "efo", "fr"))
                .thenReturn(List.of(Map.of(
                        "iri", "http://example.org/EFO_I100",
                        "text", "Liver specimen alpha")));

        var response = controller.getJsTree(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_I100", "fr");

        verify(jsTreeRepository).getJsTreeForIndividual(
                "http://example.org/EFO_I100", "efo", "fr");
        org.assertj.core.api.Assertions.assertThat(response.getBody())
                .contains("http://example.org/EFO_I100", "Liver specimen alpha");
    }

    private static V1Individual individual() {
        V1Individual individual = new V1Individual();
        individual.iri = "http://example.org/EFO_I100";
        return individual;
    }

    private static V1Term term() {
        V1Term term = new V1Term();
        term.iri = "http://example.org/EFO_0001";
        return term;
    }
}
