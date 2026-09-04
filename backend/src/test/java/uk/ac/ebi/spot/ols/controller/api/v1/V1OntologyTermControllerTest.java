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
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.v1.V1JsTreeRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1TermRepository;

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

class V1OntologyTermControllerTest {

    private V1OntologyTermController controller;
    private V1TermRepository termRepository;
    private V1JsTreeRepository jsTreeRepository;
    private V1TermAssembler termAssembler;
    private V1PreferredRootTermAssembler preferredRootTermAssembler;
    private PagedResourcesAssembler<V1Term> termResourcesAssembler;
    private Pageable pageable;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        termRepository = mock(V1TermRepository.class);
        jsTreeRepository = mock(V1JsTreeRepository.class);
        termAssembler = mock(V1TermAssembler.class);
        preferredRootTermAssembler = mock(V1PreferredRootTermAssembler.class);
        termResourcesAssembler = mock(PagedResourcesAssembler.class);
        pageable = PageRequest.of(1, 7);

        controller = new V1OntologyTermController();
        ReflectionTestUtils.setField(controller, "termRepository", termRepository);
        controller.termAssembler = termAssembler;
        controller.preferredRootTermAssembler = preferredRootTermAssembler;
        controller.jsTreeRepository = jsTreeRepository;
    }

    @Test
    void listsOntologyTermsWhenNoIdentifierIsSupplied() {
        OlsFacetedResultsPage<V1Term> terms = new OlsFacetedResultsPage<>(
                List.of(term()), Map.of(), pageable, 1);
        PagedModel<EntityModel<V1Term>> assembled = PagedModel.empty();
        when(termRepository.findAllByOntology("efo", null, "fr", pageable))
                .thenReturn(terms);
        when(termResourcesAssembler.toModel(terms, termAssembler)).thenReturn(assembled);

        var response = controller.termsByOntology(
                "EFO", null, null, null, null, null, "fr", pageable, termResourcesAssembler);

        assertSame(assembled, response.getBody());
        verify(termRepository).findAllByOntology("efo", null, "fr", pageable);
    }

    @Test
    void passesObsoletesFilterThrough() {
        OlsFacetedResultsPage<V1Term> terms = new OlsFacetedResultsPage<>(
                List.of(term()), Map.of(), pageable, 1);
        when(termRepository.findAllByOntology("efo", Boolean.TRUE, "en", pageable))
                .thenReturn(terms);

        controller.termsByOntology(
                "EFO", null, null, null, null, true, "en", pageable, termResourcesAssembler);

        verify(termRepository).findAllByOntology("efo", true, "en", pageable);
    }

    @Test
    void resolvesIdByTryingIriThenShortFormThenOboId() {
        when(termRepository.findByOntologyAndIri("efo", "the-iri", "fr")).thenReturn(term());
        controller.termsByOntology(
                "EFO", "the-iri", null, null, null, null, "fr", pageable,
                termResourcesAssembler);
        verify(termRepository).findByOntologyAndIri("efo", "the-iri", "fr");
        verify(termRepository, never()).findByOntologyAndShortForm("efo", "the-iri", "fr");

        when(termRepository.findByOntologyAndIri("efo", "EFO_1001", "fr")).thenReturn(null);
        when(termRepository.findByOntologyAndShortForm("efo", "EFO_1001", "fr"))
                .thenReturn(term());
        controller.termsByOntology(
                "EFO", null, "EFO_1001", null, null, null, "fr", pageable,
                termResourcesAssembler);
        verify(termRepository).findByOntologyAndIri("efo", "EFO_1001", "fr");
        verify(termRepository).findByOntologyAndShortForm("efo", "EFO_1001", "fr");

        when(termRepository.findByOntologyAndIri("efo", "EFO:1001", "fr")).thenReturn(null);
        when(termRepository.findByOntologyAndShortForm("efo", "EFO:1001", "fr")).thenReturn(null);
        when(termRepository.findByOntologyAndOboId("efo", "EFO:1001", "fr")).thenReturn(term());
        controller.termsByOntology(
                "EFO", null, null, "EFO:1001", null, null, "fr", pageable,
                termResourcesAssembler);
        verify(termRepository).findByOntologyAndOboId("efo", "EFO:1001", "fr");
    }

    @Test
    void appliesIdentifierPrecedenceIdThenIriThenShortFormThenOboId() {
        when(termRepository.findByOntologyAndIri("efo", "explicit-id", "en"))
                .thenReturn(term());

        controller.termsByOntology(
                "EFO", "ignored-iri", "ignored-short-form", "ignored-obo-id", "explicit-id",
                null, "en", pageable, termResourcesAssembler);

        verify(termRepository).findByOntologyAndIri("efo", "explicit-id", "en");
        verify(termRepository, never()).findByOntologyAndIri("efo", "ignored-iri", "en");
    }

    @Test
    void reportsMissingResourceWhenNoIdentifierResolves() {
        when(termRepository.findByOntologyAndIri("efo", "missing", "en")).thenReturn(null);
        when(termRepository.findByOntologyAndShortForm("efo", "missing", "en")).thenReturn(null);
        when(termRepository.findByOntologyAndOboId("efo", "missing", "en")).thenReturn(null);

        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class,
                () -> controller.termsByOntology(
                        "EFO", "missing", null, null, null, null, "en", pageable,
                        termResourcesAssembler));
        assertEquals("No resource with missing in efo", error.getMessage());
    }

    @Test
    void getsRootsAndPreferredRootsReportingMissingResource() {
        PageImpl<V1Term> roots = new PageImpl<>(List.of(term()));
        when(termRepository.getRoots("efo", false, "fr", pageable)).thenReturn(roots);
        controller.getRoots("EFO", false, "fr", pageable, termResourcesAssembler);
        verify(termRepository).getRoots("efo", false, "fr", pageable);

        when(termRepository.getRoots("efo", true, "en", pageable)).thenReturn(null);
        assertThrows(ResourceNotFoundException.class,
                () -> controller.getRoots("EFO", true, "en", pageable, termResourcesAssembler));

        PageImpl<V1Term> preferredRoots = new PageImpl<>(List.of(term()));
        when(termRepository.getPreferredRootTerms("efo", false, "fr", pageable))
                .thenReturn(preferredRoots);
        controller.getPreferredRoots("EFO", false, "fr", pageable, termResourcesAssembler);
        verify(termRepository).getPreferredRootTerms("efo", false, "fr", pageable);

        when(termRepository.getPreferredRootTerms("efo", true, "en", pageable)).thenReturn(null);
        assertThrows(ResourceNotFoundException.class,
                () -> controller.getPreferredRoots(
                        "EFO", true, "en", pageable, termResourcesAssembler));
    }

    @Test
    void getsDecodedTermAndReportsMissingTerm() {
        when(termRepository.findByOntologyAndIri(
                "efo", "http://example.org/EFO_1001", "en"))
                .thenReturn(term());
        EntityModel<V1Term> assembled = EntityModel.of(term());
        when(termAssembler.toModel(org.mockito.ArgumentMatchers.any())).thenReturn(assembled);

        var response = controller.getTerm(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_1001", "en");

        assertSame(assembled, response.getBody());
        verify(termRepository).findByOntologyAndIri(
                "efo", "http://example.org/EFO_1001", "en");

        when(termRepository.findByOntologyAndIri("efo", "missing", "en")).thenReturn(null);
        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class,
                () -> controller.getTerm("EFO", "missing", "en"));
        assertEquals("No term with id missing in efo", error.getMessage());
    }

    @Test
    void delegatesDecodedParentsChildrenDescendantsAndAncestors() {
        PageImpl<V1Term> page = new PageImpl<>(List.of(term()));
        when(termRepository.getParents(
                "efo", "http://example.org/EFO_1001", "fr", pageable)).thenReturn(page);
        when(termRepository.getChildren(
                "efo", "http://example.org/EFO_0001", "fr", pageable)).thenReturn(page);
        when(termRepository.getDescendants(
                "efo", "http://example.org/EFO_0001", "fr", pageable)).thenReturn(page);
        when(termRepository.getAncestors(
                "efo", "http://example.org/EFO_1001", "fr", pageable)).thenReturn(page);

        controller.getParents(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_1001", "fr", pageable,
                termResourcesAssembler);
        controller.children(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_0001", "fr", pageable,
                termResourcesAssembler);
        controller.descendants(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_0001", "fr", pageable,
                termResourcesAssembler);
        controller.ancestors(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_1001", "fr", pageable,
                termResourcesAssembler);

        verify(termRepository).getParents(
                "efo", "http://example.org/EFO_1001", "fr", pageable);
        verify(termRepository).getChildren(
                "efo", "http://example.org/EFO_0001", "fr", pageable);
        verify(termRepository).getDescendants(
                "efo", "http://example.org/EFO_0001", "fr", pageable);
        verify(termRepository).getAncestors(
                "efo", "http://example.org/EFO_1001", "fr", pageable);
    }

    @Test
    void reportsMissingResourceForEachHierarchyRouteWhenRepositoryReturnsNull() {
        when(termRepository.getParents("efo", "missing", "en", pageable)).thenReturn(null);
        assertThrows(ResourceNotFoundException.class,
                () -> controller.getParents("EFO", "missing", "en", pageable,
                        termResourcesAssembler));

        when(termRepository.getChildren("efo", "missing", "en", pageable)).thenReturn(null);
        ResourceNotFoundException childrenError = assertThrows(
                ResourceNotFoundException.class,
                () -> controller.children("EFO", "missing", "en", pageable,
                        termResourcesAssembler));
        assertEquals("No children could be found for efo and missing", childrenError.getMessage());

        when(termRepository.getDescendants("efo", "missing", "en", pageable)).thenReturn(null);
        assertThrows(ResourceNotFoundException.class,
                () -> controller.descendants("EFO", "missing", "en", pageable,
                        termResourcesAssembler));

        when(termRepository.getAncestors("efo", "missing", "en", pageable)).thenReturn(null);
        assertThrows(ResourceNotFoundException.class,
                () -> controller.ancestors("EFO", "missing", "en", pageable,
                        termResourcesAssembler));
    }

    @Test
    void serializesTheDecodedTermJsTree() {
        when(jsTreeRepository.getJsTreeForClass(
                "http://example.org/EFO_1001", "efo", "fr"))
                .thenReturn(List.of(Map.of(
                        "iri", "http://example.org/EFO_1001",
                        "text", "Clinical liver child")));

        var response = controller.graphJsTree(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_1001", "fr", false, "PreferredRoots");

        verify(jsTreeRepository).getJsTreeForClass(
                "http://example.org/EFO_1001", "efo", "fr");
        org.assertj.core.api.Assertions.assertThat(response.getBody())
                .contains("http://example.org/EFO_1001", "Clinical liver child");
    }

    @Test
    void serializesTheDecodedTermJsTreeChildren() {
        when(jsTreeRepository.getJsTreeChildrenForClass(
                "http://example.org/EFO_0001", "node-1", "efo", "fr"))
                .thenReturn(List.of(Map.of(
                        "iri", "http://example.org/EFO_1001",
                        "text", "Clinical liver child")));

        var response = controller.graphJsTreeChildren(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_0001", "node-1", "fr");

        verify(jsTreeRepository).getJsTreeChildrenForClass(
                "http://example.org/EFO_0001", "node-1", "efo", "fr");
        org.assertj.core.api.Assertions.assertThat(response.getBody())
                .contains("http://example.org/EFO_1001", "Clinical liver child");
    }

    private static V1Term term() {
        V1Term term = new V1Term();
        term.iri = "http://example.org/EFO_1001";
        term.related = List.of();
        return term;
    }
}
