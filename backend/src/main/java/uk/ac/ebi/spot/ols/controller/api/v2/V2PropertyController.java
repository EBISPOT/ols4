package uk.ac.ebi.spot.ols.controller.api.v2;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.controller.api.v2.helpers.DynamicQueryHelper;
import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedAndFacetedResponse;
import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedResponse;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.PropertyRepository;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import static uk.ac.ebi.ols.shared.DefinedFields.*;


import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "V2 Property Controller", description = "This endpoint provides access to property information.")
@RestController
@RequestMapping("/api/v2")
public class V2PropertyController {

    @Autowired
    PropertyRepository propertyRepository;

    @RequestMapping(path = "/properties", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getProperties(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
                    description = "Specify the size of the result you want to get in the output",
                    example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @RequestParam(value = "search", required = false)
            @Parameter(name="search",
                    description = "This parameter specify the search query text.",
                    example = "definition") String search,
            @RequestParam(value = "searchFields", required = false)
            @Parameter(name = "searchFields",
                    description = "This parameter is a white space separated list of fields to search in. " +
                            "The fields are weighted equally. The fields are defined in the schema. " +
                            "The default fields are label and definition. " +
                            "The fields weights can be boosted by appending a caret ^ and a positive integer to the field name. " +
                            "For example, label^3 synonyms^2 description^1 logical_definition^1",
                    example = "label^100 description") String searchFields,
            @RequestParam(value = "boostFields", required = false)
            @Parameter(name = "boostFields",
                    description = "This parameter is a white space separated list of fields appended with a caret to boost in search. " +
                            "The default fields are type, is_defining_ontology, label, curie, shortForm and synonym . " +
                            "The fields weights can be boosted by appending a caret ^ and a positive integer to the field name. ",
                    example = "label^100 curie^50") String boostFields,
            @RequestParam(value = "exactMatch", required = false, defaultValue = "false")
            @Parameter(name = "exactMatch",
                    description = "As the name suggests its a boolean parameter to specify if search should be exact match or not." +
                            "The default value is false") boolean exactMatch,
            @RequestParam(value = "includeObsoleteEntities", required = false, defaultValue = "false")
            @Parameter(name = "includeObsoleteEntities",
                    description = "A boolean parameter to specify if obsolete entities should be included or not. Default value is false.") boolean includeObsoleteEntities,
            @RequestParam
            @Parameter(name="searchProperties",
                    description = "Specify any other search field here which are not specified by searchFields or boostFields.",
                    example = "{}") Map<String, Collection<String>> searchProperties,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException, IOException {

        Map<String,Collection<String>> properties = new HashMap<>();
        if(!includeObsoleteEntities)
            properties.put(IS_OBSOLETE.getText(), List.of("false"));
        properties.putAll(searchProperties);

        return new ResponseEntity<>(
                new V2PagedAndFacetedResponse<V2Entity>(
                    propertyRepository.find(pageable, lang, search, searchFields, boostFields, exactMatch, DynamicQueryHelper.filterProperties(properties), outputOpts)
                    .map(V2Entity::new)
                ),
                 HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/properties", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getProperties(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
                    description = "Specify the size of the result you want to get in the output",
                    example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @PathVariable("onto") @NotNull
            @Parameter(name = "onto",
                    description = "Ontology Id to search properties in.",
                    example = "efo") String ontologyId,
            @RequestParam(value = "search", required = false)
            @Parameter(name="search",
                    description = "This parameter specify the search query text.",
                    example = "definition") String search,
            @RequestParam(value = "searchFields", required = false)
            @Parameter(name = "search fields",
                    description = "This parameter is a white space separated list of fields to search in. " +
                            "The fields are weighted equally. The fields are defined in the schema. " +
                            "The default fields are label and definition. " +
                            "The fields weights can be boosted by appending a caret ^ and a positive integer to the field name. " +
                            "For example, label^3 synonyms^2 description^1 logical_definition^1") String searchFields,
            @RequestParam(value = "boostFields", required = false)
            @Parameter(name = "boost fields",
                    description = "This parameter is a white space separated list of fields appended with a caret to boost in search. " +
                            "The default fields are type, is_defining_ontology, label, curie, shortForm and synonym . " +
                            "The fields weights can be boosted by appending a caret ^ and a positive integer to the field name. ") String boostFields,
            @RequestParam(value = "exactMatch", required = false, defaultValue = "false")
            @Parameter(name = "exactMatch",
                    description = "As the name suggests its a boolean parameter to specify if search should be exact match or not." +
                            "The default value is false") boolean exactMatch,
            @RequestParam(value = "includeObsoleteEntities", required = false, defaultValue = "false")
            @Parameter(name = "includeObsoleteEntities",
                    description = "A boolean parameter to specify if obsolete entities should be included or not. Default value is false.") boolean includeObsoleteEntities,
            @RequestParam
            @Parameter(name="searchProperties",
                    description = "Specify any other search field here which are not specified by searchFields or boostFields.",
                    example = "{}") MultiValueMap<String,String> searchProperties,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException, IOException {

        Map<String,Collection<String>> properties = new HashMap<>();
        if(!includeObsoleteEntities)
            properties.put(IS_OBSOLETE.getText(), List.of("false"));
        properties.putAll(searchProperties);

        return new ResponseEntity<>(
                new V2PagedAndFacetedResponse<V2Entity>(
                    propertyRepository.findByOntologyId(ontologyId, pageable, lang, search, searchFields, boostFields, exactMatch, DynamicQueryHelper.filterProperties(properties), outputOpts)
                    .map(V2Entity::new)
                ),
                HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/properties/{property}", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2Entity> getProperty(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "Ontology Id to search properties in.",
                    example = "efo") String ontologyId,
            @PathVariable("property")
            @Parameter(name = "property",
                    description = "The IRI of the property, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_0000742") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        V2Entity entity = propertyRepository.getByOntologyIdAndIri(ontologyId, iri, lang, outputOpts);
        if (entity == null) throw new ResourceNotFoundException();
        return new ResponseEntity<>( entity, HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/properties/{property}/children", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getChildrenByOntology(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
                    description = "Specify the size of the result you want to get in the output",
                    example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "Ontology Id to search properties in.",
                    example = "efo") String ontologyId,
            @PathVariable("property")
            @Parameter(name = "property",
                    description = "The IRI of the property, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_0000824") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        return new ResponseEntity<>(
                new V2PagedResponse<V2Entity>(
                    propertyRepository.getChildrenByOntologyId(ontologyId, pageable, iri, lang, outputOpts)
                    .map(V2Entity::new)
                ),
                 HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/properties/{property}/ancestors", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getAncestorsByOntology(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
                    description = "Specify the size of the result you want to get in the output",
                    example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "Ontology Id to search properties in.",
                    example = "efo") String ontologyId,
            @PathVariable("property")
            @Parameter(name = "property",
                    description = "The IRI of the property, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_0000742") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        return new ResponseEntity<>(
                new V2PagedResponse<V2Entity>(
                propertyRepository.getAncestorsByOntologyId(ontologyId, pageable, iri, lang, outputOpts)
                    .map(V2Entity::new)
                ), HttpStatus.OK);
    }

}



