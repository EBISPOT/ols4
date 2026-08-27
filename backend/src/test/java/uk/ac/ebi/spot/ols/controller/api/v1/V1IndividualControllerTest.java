package uk.ac.ebi.spot.ols.controller.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.v1.V1IndividualRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V1IndividualControllerTest {

    private V1IndividualController controller;
    private V1IndividualRepository repository;
    private V1IndividualAssembler individualAssembler;
    private PagedResourcesAssembler<V1Individual> resourcesAssembler;
    private Pageable pageable;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(V1IndividualRepository.class);
        individualAssembler = mock(V1IndividualAssembler.class);
        resourcesAssembler = mock(PagedResourcesAssembler.class);
        pageable = PageRequest.of(1, 7);
        controller = new V1IndividualController();
        ReflectionTestUtils.setField(controller, "individualRepository", repository);
        controller.individualAssembler = individualAssembler;
    }

    @Test
    void listsAllIndividualsWhenNoIdentifierIsSupplied() {
        OlsFacetedResultsPage<V1Individual> individuals =
                page(individual("http://example.org/EFO_I100"));
        PagedModel<EntityModel<V1Individual>> assembled = PagedModel.empty();
        when(repository.findAll("fr", pageable)).thenReturn(individuals);
        when(resourcesAssembler.toModel(individuals, individualAssembler)).thenReturn(assembled);

        var response = controller.getAllIndividuals(
                null, null, null, "fr", pageable, resourcesAssembler);

        assertEquals(HttpStatus.OK, ((ResponseEntity<?>) response).getStatusCode());
        assertSame(assembled, response.getBody());
        verify(repository).findAll("fr", pageable);
        verify(resourcesAssembler).toModel(individuals, individualAssembler);
    }

    @Test
    void selectsIriBeforeShortFormAndOboId() {
        when(repository.findAllByIri("the-iri", "en", pageable))
                .thenReturn(page(individual("the-iri")));

        controller.getAllIndividuals(
                "the-iri", "EFO_I100", "EFO:I100",
                "en", pageable, resourcesAssembler);

        verify(repository).findAllByIri("the-iri", "en", pageable);
        verify(repository, never()).findAllByShortForm("EFO_I100", "en", pageable);
        verify(repository, never()).findAllByOboId("EFO:I100", "en", pageable);
    }

    @Test
    void selectsShortFormThenOboIdWhenHigherPriorityIdentifiersAreAbsent() {
        when(repository.findAllByShortForm("EFO_I100", "en", pageable))
                .thenReturn(page(individual("http://example.org/EFO_I100")));

        controller.getAllIndividuals(
                null, "EFO_I100", "EFO:I100",
                "en", pageable, resourcesAssembler);

        verify(repository).findAllByShortForm("EFO_I100", "en", pageable);
        verify(repository, never()).findAllByOboId("EFO:I100", "en", pageable);

        when(repository.findAllByOboId("EFO:I200", "en", pageable))
                .thenReturn(page(individual("http://example.org/EFO_I200")));
        controller.getAllIndividuals(
                null, null, "EFO:I200",
                "en", pageable, resourcesAssembler);
        verify(repository).findAllByOboId("EFO:I200", "en", pageable);
    }

    @Test
    void pathRouteDecodesTheIriBeforeUsingTheListDecision() {
        when(repository.findAllByIri("http://example.org/EFO_I100", "en", pageable))
                .thenReturn(page(individual("http://example.org/EFO_I100")));

        controller.getAllIndividuals(
                "http%3A%2F%2Fexample.org%2FEFO_I100",
                "en", pageable, resourcesAssembler);

        verify(repository).findAllByIri(
                "http://example.org/EFO_I100", "en", pageable);
    }

    @Test
    void definingOntologyListUsesTheDefiningRepositoryVariants() {
        when(repository.findAllByIriAndIsDefiningOntology("the-iri", "en", pageable))
                .thenReturn(page(individual("the-iri")));

        controller.getAllIndividualsByIdAndIsDefiningOntology(
                "the-iri", "EFO_I100", "EFO:I100",
                "en", pageable, resourcesAssembler);

        verify(repository).findAllByIriAndIsDefiningOntology("the-iri", "en", pageable);
        verify(repository, never()).findAllByShortFormAndIsDefiningOntology(
                "EFO_I100", "en", pageable);
        verify(repository, never()).findAllByOboIdAndIsDefiningOntology(
                "EFO:I100", "en", pageable);
    }

    @Test
    void listsAllDefiningOntologyIndividualsWhenNoIdentifierIsSupplied() {
        when(repository.findAllByIsDefiningOntology("fr", pageable))
                .thenReturn(page(individual("http://example.org/EFO_I100")));

        controller.getAllIndividualsByIdAndIsDefiningOntology(
                null, null, null, "fr", pageable, resourcesAssembler);

        verify(repository).findAllByIsDefiningOntology("fr", pageable);
    }

    @Test
    void definingOntologyPathDecodesTheIri() {
        when(repository.findAllByIriAndIsDefiningOntology(
                "http://example.org/EFO_I100", "fr", pageable))
                .thenReturn(page(individual("http://example.org/EFO_I100")));

        controller.getAllIndividualsByIdAndIsDefiningOntology(
                "http%3A%2F%2Fexample.org%2FEFO_I100",
                "fr", pageable, resourcesAssembler);

        verify(repository).findAllByIriAndIsDefiningOntology(
                "http://example.org/EFO_I100", "fr", pageable);
    }

    @Test
    void advertisesTheV1IndividualsCollectionFromTheApiRoot() {
        RepositoryLinksResource resource = new RepositoryLinksResource();

        RepositoryLinksResource processed = controller.process(resource);

        assertSame(resource, processed);
        assertTrue(processed.hasLink("individuals"));
        assertEquals("/api/individuals", processed.getRequiredLink("individuals").getHref());
    }

    private OlsFacetedResultsPage<V1Individual> page(V1Individual... individuals) {
        return new OlsFacetedResultsPage<>(
                List.of(individuals), Map.of(), pageable, individuals.length);
    }

    private static V1Individual individual(String iri) {
        V1Individual individual = new V1Individual();
        individual.iri = iri;
        return individual;
    }
}
