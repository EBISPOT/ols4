package uk.ac.ebi.spot.ols.controller.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Property;
import uk.ac.ebi.spot.ols.repository.v1.V1PropertyRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V1PropertyControllerTest {

    private V1PropertyController controller;
    private V1PropertyRepository repository;
    private V1PropertyAssembler propertyAssembler;
    private PagedResourcesAssembler<V1Property> resourcesAssembler;
    private Pageable pageable;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(V1PropertyRepository.class);
        propertyAssembler = mock(V1PropertyAssembler.class);
        resourcesAssembler = mock(PagedResourcesAssembler.class);
        pageable = PageRequest.of(1, 7);
        controller = new V1PropertyController();
        ReflectionTestUtils.setField(controller, "propertyRepository", repository);
        controller.termAssembler = propertyAssembler;
    }

    @Test
    void listsAllPropertiesWhenNoIdentifierIsSupplied() {
        PageImpl<V1Property> properties = page(property("http://example.org/EFO_0100"));
        PagedModel<EntityModel<V1Property>> assembled = PagedModel.empty();
        when(repository.findAll("fr", pageable)).thenReturn(properties);
        when(resourcesAssembler.toModel(properties, propertyAssembler)).thenReturn(assembled);

        var response = controller.getAllProperties(
                null, null, null, "fr", pageable, resourcesAssembler);

        assertEquals(HttpStatus.OK, ((ResponseEntity<?>) response).getStatusCode());
        assertSame(assembled, response.getBody());
        verify(repository).findAll("fr", pageable);
        verify(resourcesAssembler).toModel(properties, propertyAssembler);
    }

    @Test
    void selectsIriBeforeShortFormAndOboId() {
        when(repository.findAllByIri("the-iri", "en", pageable))
                .thenReturn(page(property("the-iri")));

        controller.getAllProperties(
                "the-iri", "EFO_0100", "EFO:0100",
                "en", pageable, resourcesAssembler);

        verify(repository).findAllByIri("the-iri", "en", pageable);
        verify(repository, never()).findAllByShortForm("EFO_0100", "en", pageable);
        verify(repository, never()).findAllByOboId("EFO:0100", "en", pageable);
    }

    @Test
    void selectsShortFormThenOboIdWhenHigherPriorityIdentifiersAreAbsent() {
        when(repository.findAllByShortForm("EFO_0100", "en", pageable))
                .thenReturn(page(property("http://example.org/EFO_0100")));

        controller.getAllProperties(
                null, "EFO_0100", "EFO:0100",
                "en", pageable, resourcesAssembler);

        verify(repository).findAllByShortForm("EFO_0100", "en", pageable);
        verify(repository, never()).findAllByOboId("EFO:0100", "en", pageable);

        when(repository.findAllByOboId("EFO:0101", "en", pageable))
                .thenReturn(page(property("http://example.org/EFO_0101")));
        controller.getAllProperties(
                null, null, "EFO:0101",
                "en", pageable, resourcesAssembler);
        verify(repository).findAllByOboId("EFO:0101", "en", pageable);
    }

    @Test
    void pathRouteDecodesTheIriBeforeUsingTheListDecision() {
        when(repository.findAllByIri("http://example.org/EFO_0100", "en", pageable))
                .thenReturn(page(property("http://example.org/EFO_0100")));

        controller.getPropertiesByIri(
                "http%3A%2F%2Fexample.org%2FEFO_0100",
                "en", pageable, resourcesAssembler);

        verify(repository).findAllByIri(
                "http://example.org/EFO_0100", "en", pageable);
    }

    @Test
    void definingOntologyListUsesTheDefiningRepositoryVariants() {
        when(repository.findAllByIriAndIsDefiningOntology("the-iri", "en", pageable))
                .thenReturn(page(property("the-iri")));

        controller.getPropertiesByIdAndIsDefiningOntology(
                "the-iri", "EFO_0100", "EFO:0100",
                "en", pageable, resourcesAssembler);

        verify(repository).findAllByIriAndIsDefiningOntology("the-iri", "en", pageable);
        verify(repository, never()).findAllByShortFormAndIsDefiningOntology(
                "EFO_0100", "en", pageable);
        verify(repository, never()).findAllByOboIdAndIsDefiningOntology(
                "EFO:0100", "en", pageable);
    }

    @Test
    void listsAllDefiningOntologyPropertiesWhenNoIdentifierIsSupplied() {
        when(repository.findAllByIsDefiningOntology("fr", pageable))
                .thenReturn(page(property("http://example.org/EFO_0100")));

        controller.getPropertiesByIdAndIsDefiningOntology(
                null, null, null, "fr", pageable, resourcesAssembler);

        verify(repository).findAllByIsDefiningOntology("fr", pageable);
    }

    @Test
    void definingOntologyPathDecodesTheIri() {
        when(repository.findAllByIriAndIsDefiningOntology(
                "http://example.org/EFO_0100", "fr", pageable))
                .thenReturn(page(property("http://example.org/EFO_0100")));

        controller.getPropertiesByIriAndIsDefiningOntology(
                "http%3A%2F%2Fexample.org%2FEFO_0100",
                "fr", pageable, resourcesAssembler);

        verify(repository).findAllByIriAndIsDefiningOntology(
                "http://example.org/EFO_0100", "fr", pageable);
    }

    @Test
    void advertisesTheV1PropertiesCollectionFromTheApiRoot() {
        RepositoryLinksResource resource = new RepositoryLinksResource();

        RepositoryLinksResource processed = controller.process(resource);

        assertSame(resource, processed);
        assertTrue(processed.hasLink("properties"));
        assertEquals("/api/properties", processed.getRequiredLink("properties").getHref());
    }

    private PageImpl<V1Property> page(V1Property... properties) {
        return new PageImpl<>(List.of(properties), pageable, properties.length);
    }

    private static V1Property property(String iri) {
        V1Property property = new V1Property();
        property.iri = iri;
        return property;
    }
}
