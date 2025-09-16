package uk.ac.ebi.spot.ols.controller.api.v2;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PageableDefault;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.controller.api.v2.helpers.DynamicQueryHelper;
import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedAndFacetedResponse;
import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedResponse;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.IndividualRepository;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "V2 Individual Controller", description = "This endpoint provides access to individuals information.")
@RestController
@RequestMapping("/api/v2")
public class V2IndividualController {

    @Autowired
    IndividualRepository individualRepository;

    @RequestMapping(path = "/individuals", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getIndividuals(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
                    description = "Specify the size of the result you want to get in the output",
                    example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @RequestParam(value = "search", required = false)
            @Parameter(name="search",
                    description = "This parameter specify the search query text.",
                    example = "metadata complete") String search,
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
                    example = "{}") MultiValueMap<String,String> searchProperties,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException, IOException {

        Map<String, Collection<String>> properties = new HashMap<>();
        if(!includeObsoleteEntities)
            properties.put(IS_OBSOLETE.getText(), List.of("false"));
        properties.putAll(searchProperties);

        return new ResponseEntity<>(
                new V2PagedAndFacetedResponse<V2Entity>(
                    individualRepository.find(pageable, lang, search, searchFields, boostFields, exactMatch, DynamicQueryHelper.filterProperties(properties), outputOpts).map(V2Entity::new)
                ),
                HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/individuals", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getIndividuals(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
            description = "Specify the size of the result you want to get in the output",
            example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @PathVariable("onto") @NotNull
            @Parameter(name = "onto",
                    description = "Ontology Id to search individuals in.",
                    example = "efo") String ontologyId,
            @RequestParam(value = "search", required = false)
            @Parameter(name="search",
                    description = "This parameter specify the search query text.",
                    example = "metadata complete") String search,
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
            example = "{}") MultiValueMap<String,String> searchProperties,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException, IOException {

        Map<String, Collection<String>> properties = new HashMap<>();
        if(!includeObsoleteEntities)
            properties.put(IS_OBSOLETE.getText(), List.of("false"));
        properties.putAll(searchProperties);

        return new ResponseEntity<>(
                new V2PagedAndFacetedResponse<V2Entity>(
                    individualRepository.findByOntologyId(ontologyId, pageable, lang, search, searchFields, boostFields, exactMatch, DynamicQueryHelper.filterProperties(properties), outputOpts).map(V2Entity::new)
                ),
                HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/individuals/{individual}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2Entity> getIndividual(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "Ontology Id to get the information about.",
                    example = "efo") String ontologyId,
            @PathVariable("individual")
            @Parameter(name = "individual",
                    description = "The IRI of the individual, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FIAO_0000002") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        V2Entity entity = individualRepository.getByOntologyIdAndIri(ontologyId, iri, lang, outputOpts);
        if (entity == null) throw new ResourceNotFoundException();
        return new ResponseEntity<>( entity, HttpStatus.OK);
    }


    @RequestMapping(path = "/ontologies/{onto}/classes/{class}/individuals", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getClassIndividuals(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
                    description = "Specify the size of the result you want to get in the output",
                    example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "Ontology Id to get the information about.") String ontologyId,
            @PathVariable("class")
            @Parameter(name = "class",
                    description = "The IRI of the class, this value must be double URL encoded") String classIri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException, IOException {

        classIri = UriUtils.decode(classIri, "UTF-8");

        return new ResponseEntity<>(
                new V2PagedResponse<V2Entity>(
                        individualRepository.getIndividualsOfClass(ontologyId, classIri, pageable, lang, outputOpts).map(V2Entity::new)
                ),
                HttpStatus.OK);

    }


}



