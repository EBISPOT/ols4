package uk.ac.ebi.spot.ols.controller.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.v1.V1TermRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V1TermControllerTest {

    private V1TermController controller;
    private V1TermRepository repository;
    private V1TermAssembler termAssembler;
    private PagedResourcesAssembler<V1Term> resourcesAssembler;
    private Pageable pageable;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(V1TermRepository.class);
        termAssembler = mock(V1TermAssembler.class);
        resourcesAssembler = mock(PagedResourcesAssembler.class);
        pageable = PageRequest.of(1, 7);
        controller = new V1TermController();
        ReflectionTestUtils.setField(controller, "termRepository", repository);
        controller.termAssembler = termAssembler;
    }

    @Test
    void listsAllTermsWhenNoIdentifierIsSupplied() {
        PageImpl<V1Term> terms = page(term("http://example.org/EFO_0001"));
        PagedModel<EntityModel<V1Term>> assembled = PagedModel.empty();
        when(repository.findAll("fr", pageable)).thenReturn(terms);
        when(resourcesAssembler.toModel(terms, termAssembler)).thenReturn(assembled);

        var response = controller.getTerms(
                null, null, null, null, "fr", pageable, resourcesAssembler);

        assertEquals(HttpStatus.OK, ((ResponseEntity<?>) response).getStatusCode());
        assertSame(assembled, response.getBody());
        verify(repository).findAll("fr", pageable);
        verify(resourcesAssembler).toModel(terms, termAssembler);
    }

    @Test
    void selectsIriBeforeShortFormAndOboId() {
        PageImpl<V1Term> terms = page(term("http://example.org/EFO_0001"));
        when(repository.findAllByIri("the-iri", "en", pageable)).thenReturn(terms);

        controller.getTerms(
                "the-iri", "EFO_0001", "EFO:0001", null,
                "en", pageable, resourcesAssembler);

        verify(repository).findAllByIri("the-iri", "en", pageable);
        verify(repository, never()).findAllByShortForm("EFO_0001", "en", pageable);
        verify(repository, never()).findAllByOboId("EFO:0001", "en", pageable);
    }

    @Test
    void selectsShortFormThenOboIdWhenHigherPriorityIdentifiersAreAbsent() {
        when(repository.findAllByShortForm("EFO_0001", "en", pageable))
                .thenReturn(page(term("http://example.org/EFO_0001")));

        controller.getTerms(
                null, "EFO_0001", "EFO:0001", null,
                "en", pageable, resourcesAssembler);

        verify(repository).findAllByShortForm("EFO_0001", "en", pageable);
        verify(repository, never()).findAllByOboId("EFO:0001", "en", pageable);

        when(repository.findAllByOboId("EFO:0002", "en", pageable))
                .thenReturn(page(term("http://example.org/EFO_0002")));
        controller.getTerms(
                null, null, "EFO:0002", null,
                "en", pageable, resourcesAssembler);
        verify(repository).findAllByOboId("EFO:0002", "en", pageable);
    }

    @Test
    void genericIdFallsBackFromIriToShortFormToOboId() {
        PageImpl<V1Term> empty = page();
        PageImpl<V1Term> found = page(term("http://example.org/EFO_0001"));
        when(repository.findAllByIri("EFO:0001", "en", pageable)).thenReturn(empty);
        when(repository.findAllByShortForm("EFO:0001", "en", pageable)).thenReturn(empty);
        when(repository.findAllByOboId("EFO:0001", "en", pageable)).thenReturn(found);

        controller.getTerms(
                "ignored", "ignored", "ignored", "EFO:0001",
                "en", pageable, resourcesAssembler);

        verify(repository).findAllByIri("EFO:0001", "en", pageable);
        verify(repository).findAllByShortForm("EFO:0001", "en", pageable);
        verify(repository).findAllByOboId("EFO:0001", "en", pageable);
    }

    @Test
    void genericIdStopsAtTheFirstMatchingRepresentation() {
        PageImpl<V1Term> found = page(term("http://example.org/EFO_0001"));
        when(repository.findAllByIri("http://example.org/EFO_0001", "en", pageable))
                .thenReturn(found);

        controller.getTerms(
                null, null, null, "http://example.org/EFO_0001",
                "en", pageable, resourcesAssembler);

        verify(repository, never()).findAllByShortForm(
                "http://example.org/EFO_0001", "en", pageable);
        verify(repository, never()).findAllByOboId(
                "http://example.org/EFO_0001", "en", pageable);
    }

    @Test
    void pathRouteDecodesTheIriBeforeUsingTheListDecision() {
        when(repository.findAllByIri("http://example.org/EFO_0001", "en", pageable))
                .thenReturn(page(term("http://example.org/EFO_0001")));

        controller.getTermsByIri(
                "http%3A%2F%2Fexample.org%2FEFO_0001",
                "en", pageable, resourcesAssembler);

        verify(repository).findAllByIri(
                "http://example.org/EFO_0001", "en", pageable);
    }

    @Test
    void definingOntologyListUsesTheDefiningRepositoryVariants() {
        PageImpl<V1Term> empty = page();
        PageImpl<V1Term> found = page(term("http://example.org/EFO_0001"));
        when(repository.findAllByIriAndIsDefiningOntology("EFO:0001", "en", pageable))
                .thenReturn(empty);
        when(repository.findAllByShortFormAndIsDefiningOntology("EFO:0001", "en", pageable))
                .thenReturn(empty);
        when(repository.findAllByOboIdAndIsDefiningOntology("EFO:0001", "en", pageable))
                .thenReturn(found);

        controller.getTermsByIdAndIsDefiningOntology(
                null, null, null, "EFO:0001",
                "en", pageable, resourcesAssembler);

        verify(repository).findAllByIriAndIsDefiningOntology("EFO:0001", "en", pageable);
        verify(repository).findAllByShortFormAndIsDefiningOntology("EFO:0001", "en", pageable);
        verify(repository).findAllByOboIdAndIsDefiningOntology("EFO:0001", "en", pageable);
    }

    @Test
    void listsAllDefiningOntologyTermsWhenNoIdentifierIsSupplied() {
        when(repository.findAllByIsDefiningOntology("fr", pageable))
                .thenReturn(page(term("http://example.org/EFO_0001")));

        controller.getTermsByIdAndIsDefiningOntology(
                null, null, null, null,
                "fr", pageable, resourcesAssembler);

        verify(repository).findAllByIsDefiningOntology("fr", pageable);
    }

    @Test
    void definingOntologyPathDecodesTheIri() {
        when(repository.findAllByIriAndIsDefiningOntology(
                "http://example.org/EFO_0001", "fr", pageable))
                .thenReturn(page(term("http://example.org/EFO_0001")));

        controller.getTermsByIdAndIsDefiningOntology(
                "http%3A%2F%2Fexample.org%2FEFO_0001",
                "fr", pageable, resourcesAssembler);

        verify(repository).findAllByIriAndIsDefiningOntology(
                "http://example.org/EFO_0001", "fr", pageable);
    }

    @Test
    void reportsMissingUnfilteredTermCollection() {
        when(repository.findAll("en", pageable)).thenReturn(null);

        assertThrows(
                ResourceNotFoundException.class,
                () -> controller.getTerms(
                        null, null, null, null,
                        "en", pageable, resourcesAssembler));
    }

    @Test
    void advertisesTheV1TermsCollectionFromTheApiRoot() {
        RepositoryLinksResource resource = new RepositoryLinksResource();

        RepositoryLinksResource processed = controller.process(resource);

        assertSame(resource, processed);
        assertTrue(processed.hasLink("terms"));
        assertEquals("/api/terms", processed.getRequiredLink("terms").getHref());
    }

    private PageImpl<V1Term> page(V1Term... terms) {
        return new PageImpl<>(List.of(terms), pageable, terms.length);
    }

    private static V1Term term(String iri) {
        V1Term term = new V1Term();
        term.iri = iri;
        return term;
    }
}
