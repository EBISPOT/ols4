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
import uk.ac.ebi.spot.ols.model.v1.V1Property;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.v1.V1JsTreeRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1PropertyRepository;

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

class V1OntologyPropertyControllerTest {

    private V1OntologyPropertyController controller;
    private V1PropertyRepository propertyRepository;
    private V1JsTreeRepository jsTreeRepository;
    private V1PropertyAssembler termAssembler;
    private PagedResourcesAssembler<V1Property> propertyResourcesAssembler;
    private Pageable pageable;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        propertyRepository = mock(V1PropertyRepository.class);
        jsTreeRepository = mock(V1JsTreeRepository.class);
        termAssembler = mock(V1PropertyAssembler.class);
        propertyResourcesAssembler = mock(PagedResourcesAssembler.class);
        pageable = PageRequest.of(1, 7);

        controller = new V1OntologyPropertyController();
        ReflectionTestUtils.setField(controller, "propertyRepository", propertyRepository);
        ReflectionTestUtils.setField(controller, "jsTreeRepository", jsTreeRepository);
        controller.termAssembler = termAssembler;
    }

    @Test
    void listsOntologyPropertiesWhenNoIdentifierIsSupplied() {
        OlsFacetedResultsPage<V1Property> properties = new OlsFacetedResultsPage<>(
                List.of(property()), Map.of(), pageable, 1);
        PagedModel<EntityModel<V1Property>> assembled = PagedModel.empty();
        when(propertyRepository.findAllByOntology("efo", "fr", pageable))
                .thenReturn(properties);
        when(propertyResourcesAssembler.toModel(properties, termAssembler))
                .thenReturn(assembled);

        var response = controller.getAllPropertiesByOntology(
                "EFO", null, null, null, "fr", pageable, propertyResourcesAssembler);

        assertSame(assembled, response.getBody());
        verify(propertyRepository).findAllByOntology("efo", "fr", pageable);
    }

    @Test
    void selectsIriBeforeShortFormAndOboIdUsingRepositoryArgumentOrder() {
        when(propertyRepository.findByOntologyAndIri("efo", "the-iri", "fr"))
                .thenReturn(property());

        controller.getAllPropertiesByOntology(
                "EFO", "the-iri", "EFO_0100", "EFO:0100", "fr", pageable,
                propertyResourcesAssembler);

        verify(propertyRepository).findByOntologyAndIri("efo", "the-iri", "fr");
        verify(propertyRepository, never())
                .findByOntologyAndShortForm("efo", "EFO_0100", "fr");
        verify(propertyRepository, never())
                .findByOntologyAndOboId("efo", "EFO:0100", "fr");

        when(propertyRepository.findByOntologyAndShortForm("efo", "EFO_0101", "fr"))
                .thenReturn(property());
        controller.getAllPropertiesByOntology(
                "EFO", null, "EFO_0101", "EFO:0101", "fr", pageable,
                propertyResourcesAssembler);
        verify(propertyRepository).findByOntologyAndShortForm("efo", "EFO_0101", "fr");

        when(propertyRepository.findByOntologyAndOboId("efo", "EFO:0199", "fr"))
                .thenReturn(property());
        controller.getAllPropertiesByOntology(
                "EFO", null, null, "EFO:0199", "fr", pageable,
                propertyResourcesAssembler);
        verify(propertyRepository).findByOntologyAndOboId("efo", "EFO:0199", "fr");

        controller.getAllPropertiesByOntology(
                "EFO", "missing-iri", null, null, "fr", pageable,
                propertyResourcesAssembler);
        controller.getAllPropertiesByOntology(
                "EFO", null, "missing-short-form", null, "fr", pageable,
                propertyResourcesAssembler);
        controller.getAllPropertiesByOntology(
                "EFO", null, null, "MISSING:1", "fr", pageable,
                propertyResourcesAssembler);
        verify(propertyResourcesAssembler, times(3)).toModel(null, termAssembler);
    }

    @Test
    void getsRootsAndReportsMissingResource() throws Exception {
        PageImpl<V1Property> roots = new PageImpl<>(List.of(property()));
        when(propertyRepository.getRoots("efo", false, "fr", pageable)).thenReturn(roots);

        controller.getRoots("EFO", false, "fr", pageable, propertyResourcesAssembler);

        verify(propertyRepository).getRoots("efo", false, "fr", pageable);

        when(propertyRepository.getRoots("efo", true, "en", pageable)).thenReturn(null);
        assertThrows(
                ResourceNotFoundException.class,
                () -> controller.getRoots("EFO", true, "en", pageable, propertyResourcesAssembler));
    }

    @Test
    void getsDecodedPropertyAndReportsMissingProperty() {
        when(propertyRepository.findByOntologyAndIri(
                "efo", "http://example.org/EFO_0100", "en"))
                .thenReturn(property());
        EntityModel<V1Property> assembled = EntityModel.of(property());
        when(termAssembler.toModel(org.mockito.ArgumentMatchers.any()))
                .thenReturn(assembled);

        var response = controller.getProperty(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_0100", "en");

        assertSame(assembled, response.getBody());
        verify(propertyRepository).findByOntologyAndIri(
                "efo", "http://example.org/EFO_0100", "en");

        when(propertyRepository.findByOntologyAndIri("efo", "missing", "en"))
                .thenReturn(null);
        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class,
                () -> controller.getProperty("EFO", "missing", "en"));
        assertEquals("No property with id missing in efo", error.getMessage());
    }

    @Test
    void delegatesDecodedParentsChildrenDescendantsAndAncestors() {
        PageImpl<V1Property> page = new PageImpl<>(List.of(property()));
        when(propertyRepository.getParents(
                "efo", "http://example.org/EFO_0101", "fr", pageable)).thenReturn(page);
        when(propertyRepository.getChildren(
                "efo", "http://example.org/EFO_0100", "fr", pageable)).thenReturn(page);
        when(propertyRepository.getDescendants(
                "efo", "http://example.org/EFO_0100", "fr", pageable)).thenReturn(page);
        when(propertyRepository.getAncestors(
                "efo", "http://example.org/EFO_0101", "fr", pageable)).thenReturn(page);

        controller.getParents(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_0101", "fr", pageable,
                propertyResourcesAssembler);
        controller.children(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_0100", "fr", pageable,
                propertyResourcesAssembler);
        controller.descendants(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_0100", "fr", pageable,
                propertyResourcesAssembler);
        controller.ancestors(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_0101", "fr", pageable,
                propertyResourcesAssembler);

        verify(propertyRepository).getParents(
                "efo", "http://example.org/EFO_0101", "fr", pageable);
        verify(propertyRepository).getChildren(
                "efo", "http://example.org/EFO_0100", "fr", pageable);
        verify(propertyRepository).getDescendants(
                "efo", "http://example.org/EFO_0100", "fr", pageable);
        verify(propertyRepository).getAncestors(
                "efo", "http://example.org/EFO_0101", "fr", pageable);
    }

    @Test
    void serializesTheDecodedPropertyJsTree() {
        when(jsTreeRepository.getJsTreeForProperty(
                "http://example.org/EFO_0101", "efo", "fr"))
                .thenReturn(List.of(Map.of(
                        "iri", "http://example.org/EFO_0101",
                        "text", "has material")));

        var response = controller.getJsTree(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_0101", false, "PreferredRoots", "fr");

        verify(jsTreeRepository).getJsTreeForProperty(
                "http://example.org/EFO_0101", "efo", "fr");
        org.assertj.core.api.Assertions.assertThat(response.getBody())
                .contains("http://example.org/EFO_0101", "has material");
    }

    @Test
    void serializesTheDecodedPropertyJsTreeChildren() {
        when(jsTreeRepository.getJsTreeChildrenForProperty(
                "http://example.org/EFO_0100", "node-1", "efo", "fr"))
                .thenReturn(List.of(Map.of(
                        "iri", "http://example.org/EFO_0101",
                        "text", "has material")));

        var response = controller.graphJsTreeChildren(
                "EFO", "http%3A%2F%2Fexample.org%2FEFO_0100", "node-1", "fr");

        verify(jsTreeRepository).getJsTreeChildrenForProperty(
                "http://example.org/EFO_0100", "node-1", "efo", "fr");
        org.assertj.core.api.Assertions.assertThat(response.getBody())
                .contains("http://example.org/EFO_0101", "has material");
    }

    private static V1Property property() {
        V1Property property = new V1Property();
        property.iri = "http://example.org/EFO_0100";
        return property;
    }
}
