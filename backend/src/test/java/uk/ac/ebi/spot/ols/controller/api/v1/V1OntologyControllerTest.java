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
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.repository.v1.V1OntologyRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V1OntologyControllerTest {

    private V1OntologyController controller;
    private V1OntologyRepository repository;
    private V1OntologyAssembler ontologyAssembler;

    @BeforeEach
    void setUp() {
        repository = mock(V1OntologyRepository.class);
        ontologyAssembler = mock(V1OntologyAssembler.class);
        controller = new V1OntologyController();
        ReflectionTestUtils.setField(controller, "ontologyRepository", repository);
        controller.documentAssembler = ontologyAssembler;
        controller.termAssembler = mock(V1TermAssembler.class);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void listsOntologiesUsingTheRequestedLanguageAndPageable() {
        Pageable pageable = PageRequest.of(2, 7);
        V1Ontology ontology = ontology("efo");
        PageImpl<V1Ontology> page = new PageImpl<>(List.of(ontology), pageable, 15);
        PagedResourcesAssembler resourcesAssembler = mock(PagedResourcesAssembler.class);
        PagedModel<?> assembled = PagedModel.empty();
        when(repository.getAll("fr", pageable)).thenReturn(page);
        when(resourcesAssembler.toModel(page, ontologyAssembler)).thenReturn(assembled);

        var response = controller.getOntologies("fr", pageable, resourcesAssembler);

        assertEquals(HttpStatus.OK, ((ResponseEntity<?>) response).getStatusCode());
        assertSame(assembled, response.getBody());
        verify(repository).getAll("fr", pageable);
        verify(resourcesAssembler).toModel(page, ontologyAssembler);
    }

    @Test
    void getsOntologyUsingALowercaseIdentifier() {
        V1Ontology ontology = ontology("efo");
        EntityModel<V1Ontology> assembled = EntityModel.of(ontology);
        when(repository.get("efo", "fr")).thenReturn(ontology);
        when(ontologyAssembler.toModel(ontology)).thenReturn(assembled);

        var response = controller.getOntology("fr", "EFO");

        assertEquals(HttpStatus.OK, ((ResponseEntity<?>) response).getStatusCode());
        assertSame(assembled, response.getBody());
        verify(repository).get("efo", "fr");
    }

    @Test
    void reportsMissingOntology() {
        when(repository.get("missing", "en")).thenReturn(null);

        assertThrows(
                ResourceNotFoundException.class,
                () -> controller.getOntology("en", "MISSING"));
    }

    @Test
    void advertisesTheV1OntologiesCollectionFromTheApiRoot() {
        RepositoryLinksResource resource = new RepositoryLinksResource();

        RepositoryLinksResource processed = controller.process(resource);

        assertSame(resource, processed);
        assertTrue(processed.hasLink("ontologies"));
        assertEquals("/api/ontologies", processed.getRequiredLink("ontologies").getHref());
    }

    private static V1Ontology ontology(String ontologyId) {
        V1Ontology ontology = new V1Ontology();
        ontology.ontologyId = ontologyId;
        ontology.lang = "en";
        return ontology;
    }
}
