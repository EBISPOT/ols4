package uk.ac.ebi.spot.ols.controller.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.v1.V1TermRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The seven ontology-root-level shortcut routes (/{onto}/children, /{onto}/descendants,
 * /{onto}/parents, /{onto}/ancestors, and their three hierarchical* counterparts) resolved the
 * requested term by calling getOneById(ontologyId, id, lang) with the ontology id exactly as
 * received from the URL, then lowercased it only afterward — for the final delegated repository
 * call and the 404 message, but too late for the identifier lookup itself. Every other route on
 * this controller (termsByOntology, the per-term {iri} routes) lowercases first. Since
 * ontology_id is always stored lowercase (see OlsSearchQuery's own comment on this), a request
 * using the ontology's conventional uppercase acronym (e.g. "EFO", matching how ontologies are
 * commonly referred to) would silently 404 on these seven routes alone, even though the same
 * casing works on every other V1 route.
 */
class V1OntologyTermControllerShortcutOntologyCaseTest {

    private V1OntologyTermController controller;
    private V1TermRepository termRepository;
    private Pageable pageable;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        termRepository = mock(V1TermRepository.class);
        pageable = PageRequest.of(0, 1000);

        controller = new V1OntologyTermController();
        ReflectionTestUtils.setField(controller, "termRepository", termRepository);
        controller.termAssembler = mock(V1TermAssembler.class);
    }

    @Test
    void resolvesTheIdentifierRegardlessOfTheRequestedOntologyIdCasing() {
        V1Term term = new V1Term();
        term.iri = "http://example.org/EFO_1001";
        term.related = List.of();
        when(termRepository.findByOntologyAndIri("efo", "http://example.org/EFO_1001", "en"))
                .thenReturn(term);
        when(termRepository.getChildren(
                "efo", "http://example.org/EFO_1001", "en", pageable))
                .thenReturn(new PageImpl<>(List.of(term)));

        PagedResourcesAssembler<V1Term> assembler = mock(PagedResourcesAssembler.class);

        controller.termChildrenByOntology(
                "EFO", "http://example.org/EFO_1001", null, null, null, "en", pageable, assembler);

        verify(termRepository).findByOntologyAndIri(
                "efo", "http://example.org/EFO_1001", "en");
        verify(termRepository).getChildren(
                "efo", "http://example.org/EFO_1001", "en", pageable);
    }
}
