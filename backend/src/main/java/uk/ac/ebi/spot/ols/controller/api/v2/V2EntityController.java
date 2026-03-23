package uk.ac.ebi.spot.ols.controller.api.v2;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.ols.controller.api.exception.ResourceNotFoundException;
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
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.EntityRepository;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

@Tag(name = "V2 Entity Controller", description = "This endpoint provides access to entity information.")
@RestController
@RequestMapping("/api/v2")
public class V2EntityController {

    @Autowired
    EntityRepository entityRepository;

    @RequestMapping(path = "/entities", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getEntities(
            @PageableDefault(size = 20, page = 0)
            @ParameterObject Pageable pageable,
            @RequestParam(value = "search", required = false)
            @Parameter(name="search",
                    description = "This parameter specify the search query text.",
                    example = "liver disease") String search,
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
            @RequestParam(value = "facetFields", required = false)
            @Parameter(name = "facetFields",
                    description = "This parameter is a white space separated list of fields to facet data by.") String facetFields,
            @RequestParam(value = "exactMatch", required = false, defaultValue = "false")
            @Parameter(name = "exactMatch",
                    description = "As the name suggests its a boolean parameter to specify if search should be exact match or not." +
                            "The default value is false") boolean exactMatch,
            @RequestParam(value = "includeObsoleteEntities", required = false, defaultValue = "false")
            @Parameter(name = "includeObsoleteEntities",
                    description = "A boolean parameter to specify if obsolete entities should be included or not. Default value is false.") boolean includeObsoleteEntities,
            @RequestParam(value = "excludeOntologyId", required = false)
            @Parameter(name = "excludeOntologyId",
                    description = "Exclude entities from specific ontologies. Provide a comma-separated list of ontology IDs.",
                    example = "ncit,snomed") String excludeOntologyIds,
            @RequestParam
            @Parameter(hidden = true) MultiValueMap<String,String> searchProperties,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @ParameterObject JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException, IOException {

        Collection<String> excludeOntologyIdsList = (excludeOntologyIds != null && !excludeOntologyIds.isEmpty())
                ? List.of(excludeOntologyIds.split(","))
                : null;

        Map<String,Collection<String>> properties = new HashMap<>();
        if(!includeObsoleteEntities)
            properties.put(IS_OBSOLETE.getText(), List.of("false"));
        properties.putAll(searchProperties);

        return new ResponseEntity<>(
                new V2PagedAndFacetedResponse<V2Entity>(
                    entityRepository.find(pageable, lang, search, searchFields, boostFields, facetFields, exactMatch, excludeOntologyIdsList, DynamicQueryHelper.filterProperties(properties), outputOpts) .map(V2Entity::new)
                        ),
                    HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/entities", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getTerms(
            @PageableDefault(size = 20, page = 0)
            @ParameterObject Pageable pageable,
            @PathVariable("onto") @NotNull
            @Parameter(name = "onto",
                    description = "Ontology Id to get the information about.",
                    example = "efo") String ontologyId,
            @RequestParam(value = "search", required = false)
            @Parameter(name="search",
                    description = "This parameter specify the search query text.",
                    example = "liver disease") String search,
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
            @RequestParam(value = "facetFields", required = false)
            @Parameter(name = "facetFields",
                    description = "This parameter is a white space separated list of fields to facet data by.") String facetFields,
            @RequestParam(value = "exactMatch", required = false, defaultValue = "false")
            @Parameter(name = "exactMatch",
                    description = "As the name suggests its a boolean parameter to specify if search should be exact match or not." +
                            "The default value is false") boolean exactMatch,
            @RequestParam(value = "includeObsoleteEntities", required = false, defaultValue = "false")
            @Parameter(name = "includeObsoleteEntities",
                    description = "A boolean parameter to specify if obsolete entities should be included or not. Default value is false.") boolean includeObsoleteEntities,
            @RequestParam
            @Parameter(hidden = true) MultiValueMap<String,String> searchProperties,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @ParameterObject JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException, IOException {

        Map<String,Collection<String>> properties = new HashMap<>();
        if(!includeObsoleteEntities)
            properties.put(IS_OBSOLETE.getText(), List.of("false"));
        properties.putAll(searchProperties);

        return new ResponseEntity<>(
                new V2PagedAndFacetedResponse<V2Entity>(
                    entityRepository.findByOntologyId(ontologyId, pageable, lang, search, searchFields, boostFields, facetFields, exactMatch, DynamicQueryHelper.filterProperties(properties), outputOpts).map(V2Entity::new)
                ),
                HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/entities/{entity}", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2Entity> getEntity(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "Ontology Id to get the information about.",
                    example = "efo") String ontologyId,
            @PathVariable("entity")
            @Parameter(name = "entity",
                    description = "The IRI of the entity, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_1000967") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @ParameterObject JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        var entity = entityRepository.getByOntologyIdAndIri(ontologyId, iri, lang, outputOpts);
        if (entity == null) throw new ResourceNotFoundException("The requested resource was not found.");
        return new ResponseEntity<V2Entity>( new V2Entity(entity), HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/entities/{entity}/relatedFrom", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getEntityRelatedFrom(
            @PageableDefault(size = 20, page = 0)
            @ParameterObject Pageable pageable,
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "Ontology Id to get the information about.",
                    example = "efo") String ontologyId,
            @PathVariable("entity")
            @Parameter(name = "entity",
                    description = "The IRI of the entity, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_1000967") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @ParameterObject JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException, IOException {

        iri = UriUtils.decode(iri, "UTF-8");

        return new ResponseEntity<>(
                new V2PagedAndFacetedResponse<V2Entity>(
                        entityRepository.getRelatedFrom(ontologyId, iri, pageable, lang, outputOpts).map(V2Entity::new)
                ),
                HttpStatus.OK);
    }
}


