package uk.ac.ebi.spot.ols.controller.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.ols.config.WebConfig;
import uk.ac.ebi.spot.ols.controller.api.exception.GlobalExceptionHandler;
import uk.ac.ebi.spot.ols.model.v1.V1Property;
import uk.ac.ebi.spot.ols.repository.v1.V1PropertyRepository;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(V1PropertyController.class)
@ContextConfiguration(classes = {
        V1PropertyController.class,
        V1PropertyAssembler.class,
        GlobalExceptionHandler.class,
        WebConfig.class
})
class V1PropertyControllerWIT {

    private static final String PROPERTY_IRI = "http://example.org/EFO_0100";
    private static final URI PROPERTY_URI = URI.create(
            "/api/properties/http%253A%252F%252Fexample.org%252FEFO_0100");
    private static final URI DEFINING_PROPERTY_URI = URI.create(
            "/api/properties/findByIdAndIsDefiningOntology/"
                    + "http%253A%252F%252Fexample.org%252FEFO_0100");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private V1PropertyRepository propertyRepository;

    @MockitoBean
    private EntityLinks entityLinks;

    @BeforeEach
    void stubPropertyLists() {
        when(propertyRepository.findAll(anyString(), any())).thenAnswer(invocation ->
                page(invocation.getArgument(1), property(invocation.getArgument(0))));
        when(propertyRepository.findAllByIri(anyString(), anyString(), any()))
                .thenAnswer(invocation -> page(
                        invocation.getArgument(2), property(invocation.getArgument(1))));
        when(propertyRepository.findAllByShortForm(anyString(), anyString(), any()))
                .thenAnswer(invocation -> page(
                        invocation.getArgument(2), property(invocation.getArgument(1))));
        when(propertyRepository.findAllByOboId(anyString(), anyString(), any()))
                .thenAnswer(invocation -> page(
                        invocation.getArgument(2), property(invocation.getArgument(1))));
        when(propertyRepository.findAllByIsDefiningOntology(anyString(), any()))
                .thenAnswer(invocation -> page(
                        invocation.getArgument(1), property(invocation.getArgument(0))));
        when(propertyRepository.findAllByIriAndIsDefiningOntology(
                anyString(), anyString(), any())).thenAnswer(invocation -> page(
                        invocation.getArgument(2), property(invocation.getArgument(1))));
        when(propertyRepository.findAllByShortFormAndIsDefiningOntology(
                anyString(), anyString(), any())).thenAnswer(invocation -> page(
                        invocation.getArgument(2), property(invocation.getArgument(1))));
        when(propertyRepository.findAllByOboIdAndIsDefiningOntology(
                anyString(), anyString(), any())).thenAnswer(invocation -> page(
                        invocation.getArgument(2), property(invocation.getArgument(1))));
    }

    @Test
    void returnsDefaultPropertyListContract() throws Exception {
        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$._embedded.properties[0].iri").value(PROPERTY_IRI))
                .andExpect(jsonPath("$._embedded.properties[0].label").value("has specimen"))
                .andExpect(jsonPath("$._embedded.properties[0].ontology_name").value("efo"))
                .andExpect(jsonPath("$._embedded.properties[0].short_form").value("EFO_0100"))
                .andExpect(jsonPath("$._embedded.properties[0].obo_id").value("EFO:0100"))
                .andExpect(jsonPath("$._embedded.properties[0].lang").value("en"))
                .andExpect(jsonPath("$._embedded.properties[0]._links.self.href")
                        .value(endsWith("/api/ontologies/efo/properties/"
                                + "http%253A%252F%252Fexample.org%252FEFO_0100?lang=en")))
                .andExpect(jsonPath("$.page.size").value(1000))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.number").value(0));

        verify(propertyRepository).findAll("en", PageRequest.of(0, 1000));
    }

    @Test
    void bindsLanguagePageSizeAndSort() throws Exception {
        mockMvc.perform(get("/api/properties")
                        .param("lang", "fr")
                        .param("page", "2")
                        .param("size", "7")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].lang").value("fr"));

        verify(propertyRepository).findAll(
                "fr", PageRequest.of(2, 7,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @ParameterizedTest
    @CsvSource({
            "list, -1, 20, 0, 20",
            "list, 0, 0, 0, 1000",
            "list, 0, -1, 0, 1000",
            "list, 0, 1001, 0, 1000",
            "path, -1, 20, 0, 20",
            "path, 0, 0, 0, 1000",
            "path, 0, 1001, 0, 1000",
            "defining-list, -1, 20, 0, 20",
            "defining-list, 0, -1, 0, 1000",
            "defining-list, 0, 1001, 0, 1000",
            "defining-path, -1, 20, 0, 20",
            "defining-path, 0, 0, 0, 1000",
            "defining-path, 0, 1001, 0, 1000"
    })
    void normalizesPaginationBoundaries(
            String route,
            int requestedPage,
            int requestedSize,
            int expectedPage,
            int expectedSize) throws Exception {
        mockMvc.perform(get(route(route))
                        .param("page", Integer.toString(requestedPage))
                        .param("size", Integer.toString(requestedSize)))
                .andExpect(status().isOk());

        Pageable expected = PageRequest.of(expectedPage, expectedSize);
        switch (route) {
            case "list" -> verify(propertyRepository).findAll("en", expected);
            case "path" -> verify(propertyRepository).findAllByIri(PROPERTY_IRI, "en", expected);
            case "defining-list" -> verify(propertyRepository)
                    .findAllByIsDefiningOntology("en", expected);
            case "defining-path" -> verify(propertyRepository)
                    .findAllByIriAndIsDefiningOntology(PROPERTY_IRI, "en", expected);
            default -> throw new IllegalArgumentException("Unknown route: " + route);
        }
    }

    @Test
    void usesDefaultsForMalformedNumericPaginationOnEveryRoute() throws Exception {
        Pageable defaults = PageRequest.of(0, 1000);

        mockMvc.perform(get("/api/properties").param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());
        mockMvc.perform(get(PROPERTY_URI).param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/properties/findByIdAndIsDefiningOntology")
                        .param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());
        mockMvc.perform(get(DEFINING_PROPERTY_URI)
                        .param("page", "bad").param("size", "bad"))
                .andExpect(status().isOk());

        verify(propertyRepository).findAll("en", defaults);
        verify(propertyRepository).findAllByIri(PROPERTY_IRI, "en", defaults);
        verify(propertyRepository).findAllByIsDefiningOntology("en", defaults);
        verify(propertyRepository)
                .findAllByIriAndIsDefiningOntology(PROPERTY_IRI, "en", defaults);
    }

    @Test
    void routesIriShortFormAndOboIdParameters() throws Exception {
        Pageable pageable = PageRequest.of(0, 1000);

        mockMvc.perform(get("/api/properties").param("iri", PROPERTY_IRI))
                .andExpect(status().isOk());
        verify(propertyRepository).findAllByIri(PROPERTY_IRI, "en", pageable);

        mockMvc.perform(get("/api/properties").param("short_form", "EFO_0100"))
                .andExpect(status().isOk());
        verify(propertyRepository).findAllByShortForm("EFO_0100", "en", pageable);

        mockMvc.perform(get("/api/properties").param("obo_id", "EFO:0100"))
                .andExpect(status().isOk());
        verify(propertyRepository).findAllByOboId("EFO:0100", "en", pageable);
    }

    @Test
    void appliesIdentifierPrecedence() throws Exception {
        Pageable pageable = PageRequest.of(0, 1000);

        mockMvc.perform(get("/api/properties")
                        .param("iri", PROPERTY_IRI)
                        .param("short_form", "ignored-short-form")
                        .param("obo_id", "IGNORED:0000"))
                .andExpect(status().isOk());

        verify(propertyRepository).findAllByIri(PROPERTY_IRI, "en", pageable);
        verify(propertyRepository, never())
                .findAllByShortForm("ignored-short-form", "en", pageable);
        verify(propertyRepository, never()).findAllByOboId("IGNORED:0000", "en", pageable);
    }

    @Test
    void decodesDoubleEncodedIriPathAndForwardsExplicitOptions() throws Exception {
        mockMvc.perform(get(PROPERTY_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "shortForm,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].iri").value(PROPERTY_IRI));

        verify(propertyRepository).findAllByIri(
                PROPERTY_IRI,
                "fr",
                PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.ASC, "shortForm"));
    }

    @Test
    void ignoresUnsupportedReservedAndDynamicStyleParametersForCompatibility() throws Exception {
        mockMvc.perform(get("/api/properties")
                        .param("search", "specimen")
                        .param("includeObsoleteEntities", "false")
                        .param("subset", "core", "slim")
                        .param("http://example.org/category", "clinical", "policy")
                        .param("domain", "biology,information"))
                .andExpect(status().isOk());

        verify(propertyRepository).findAll("en", PageRequest.of(0, 1000));
    }

    @Test
    void returnsDefaultDefiningOntologyListContract() throws Exception {
        mockMvc.perform(get("/api/properties/findByIdAndIsDefiningOntology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].is_defining_ontology")
                        .value(true))
                .andExpect(jsonPath("$.page.number").value(0));

        verify(propertyRepository)
                .findAllByIsDefiningOntology("en", PageRequest.of(0, 1000));
    }

    @Test
    void routesEveryDefiningOntologyIdentifierParameter() throws Exception {
        Pageable pageable = PageRequest.of(0, 1000);

        mockMvc.perform(get("/api/properties/findByIdAndIsDefiningOntology")
                        .param("iri", PROPERTY_IRI))
                .andExpect(status().isOk());
        verify(propertyRepository)
                .findAllByIriAndIsDefiningOntology(PROPERTY_IRI, "en", pageable);

        mockMvc.perform(get("/api/properties/findByIdAndIsDefiningOntology")
                        .param("short_form", "EFO_0100"))
                .andExpect(status().isOk());
        verify(propertyRepository).findAllByShortFormAndIsDefiningOntology(
                "EFO_0100", "en", pageable);

        mockMvc.perform(get("/api/properties/findByIdAndIsDefiningOntology")
                        .param("obo_id", "EFO:0100"))
                .andExpect(status().isOk());
        verify(propertyRepository)
                .findAllByOboIdAndIsDefiningOntology("EFO:0100", "en", pageable);
    }

    @Test
    void appliesDefiningOntologyIdentifierPrecedenceAndBindsPagination() throws Exception {
        Pageable pageable = PageRequest.of(
                1, 4, org.springframework.data.domain.Sort.Direction.DESC, "shortForm");

        mockMvc.perform(get("/api/properties/findByIdAndIsDefiningOntology")
                        .param("iri", PROPERTY_IRI)
                        .param("short_form", "ignored-short-form")
                        .param("obo_id", "IGNORED:0000")
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "4")
                        .param("sort", "shortForm,desc"))
                .andExpect(status().isOk());

        verify(propertyRepository)
                .findAllByIriAndIsDefiningOntology(PROPERTY_IRI, "fr", pageable);
        verify(propertyRepository, never()).findAllByShortFormAndIsDefiningOntology(
                "ignored-short-form", "fr", pageable);
        verify(propertyRepository, never())
                .findAllByOboIdAndIsDefiningOntology("IGNORED:0000", "fr", pageable);
    }

    @Test
    void definingOntologyPathDecodesIriAndBindsAllOptions() throws Exception {
        mockMvc.perform(get(DEFINING_PROPERTY_URI)
                        .param("lang", "fr")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "iri,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.properties[0].lang").value("fr"));

        verify(propertyRepository).findAllByIriAndIsDefiningOntology(
                PROPERTY_IRI,
                "fr",
                PageRequest.of(1, 3,
                        org.springframework.data.domain.Sort.Direction.DESC, "iri"));
    }

    @Test
    void supportsLegacyHalMediaTypeOnEveryRoute() throws Exception {
        mockMvc.perform(get("/api/properties").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
        mockMvc.perform(get(PROPERTY_URI).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
        mockMvc.perform(get("/api/properties/findByIdAndIsDefiningOntology")
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
        mockMvc.perform(get(DEFINING_PROPERTY_URI).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON));
    }

    @Test
    void preservesArbitraryV1LanguageCompatibility() throws Exception {
        mockMvc.perform(get("/api/properties").param("lang", "en_US"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$._embedded.properties[0].lang").value("en_US"));

        verify(propertyRepository).findAll("en_US", PageRequest.of(0, 1000));
    }

    @Test
    void returnsStableBadRequestFieldsForUnsupportedSort() throws Exception {
        Pageable pageable = PageRequest.of(
                0, 1000, org.springframework.data.domain.Sort.by("bad"));
        when(propertyRepository.findAll("en", pageable))
                .thenThrow(new IllegalArgumentException("Unsupported sort field: bad"));

        mockMvc.perform(get("/api/properties").param("sort", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported sort field: bad"));
    }

    @Test
    void returnsStableLegacyNotFoundStatusAndMessage() throws Exception {
        when(propertyRepository.findAll("en", PageRequest.of(0, 1000)))
                .thenThrow(new org.springframework.data.rest.webmvc.ResourceNotFoundException());

        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertEquals(
                        "EntityModel not found", result.getResponse().getErrorMessage()))
                .andExpect(content().string(""));
    }

    @Test
    void rejectsUnsupportedHttpMethodWithStableErrorFields() throws Exception {
        mockMvc.perform(post("/api/properties"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private static URI route(String route) {
        return switch (route) {
            case "list" -> URI.create("/api/properties");
            case "path" -> PROPERTY_URI;
            case "defining-list" -> URI.create(
                    "/api/properties/findByIdAndIsDefiningOntology");
            case "defining-path" -> DEFINING_PROPERTY_URI;
            default -> throw new IllegalArgumentException("Unknown route: " + route);
        };
    }

    private static PageImpl<V1Property> page(Pageable pageable, V1Property... properties) {
        return new PageImpl<>(List.of(properties), pageable, properties.length);
    }

    private static V1Property property(String lang) {
        V1Property property = new V1Property();
        property.iri = PROPERTY_IRI;
        property.lang = lang;
        property.label = "has specimen";
        property.description = new String[]{"Relates a study to its specimen."};
        property.synonyms = new String[]{"specimen relation"};
        property.ontologyName = "efo";
        property.ontologyPrefix = "EFO";
        property.ontologyIri = "http://www.ebi.ac.uk/efo";
        property.isObsolete = false;
        property.isLocal = true;
        property.hasChildren = false;
        property.isRoot = true;
        property.shortForm = "EFO_0100";
        property.oboId = "EFO:0100";
        property.annotation = Map.of();
        return property;
    }
}
