package uk.ac.ebi.spot.ols.controller.api.v2;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.ols.controller.api.exception.ResourceNotFoundException;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.ols.controller.api.v2.helpers.DynamicQueryHelper;
import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedAndFacetedResponse;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.OntologyRepository;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "V2 Ontology Controller", description = "This endpoint provides access to ontology information.")
@RestController
@RequestMapping("/api/v2/ontologies")
public class V2OntologyController {

    @Autowired
    OntologyRepository ontologyRepository;

    private static final Logger logger = LoggerFactory.getLogger(V2OntologyController.class);

    @RequestMapping(path = "", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getOntologies(
            @PageableDefault(size = 20, page = 0)
            @ParameterObject Pageable pageable,
            @RequestParam(value = "search", required = false)
            @Parameter(name="search",
                    description = "This parameter specify the search query text.",
                    example = "efo") String search,
            @RequestParam(value = "searchFields", required = false)
            @Parameter(name = "searchFields",
                    description = "This parameter is a white space separated list of fields to search in. " +
                            "The fields are weighted equally. The fields are defined in the schema. " +
                            "The default fields are label, ontologyId and definition. " +
                            "The fields weights can be boosted by appending a caret ^ and a positive integer to the field name. " +
                            "For example, label^3 synonyms^2 description^1 logical_definition^1",
                    example = "ontologyId") String searchFields,
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
            @Parameter(hidden = true) Map<String, Collection<String>> searchProperties,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @ParameterObject JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException, IOException {

        Map<String,Collection<String>> properties = new HashMap<>();
        if(!includeObsoleteEntities)
            properties.put(IS_OBSOLETE.getText(), List.of("false"));
        properties.putAll(searchProperties);

        return new ResponseEntity<>(
                new V2PagedAndFacetedResponse<V2Entity>(
                    ontologyRepository.find(pageable, lang, search, searchFields, boostFields, exactMatch, DynamicQueryHelper.filterProperties(properties), outputOpts)
                    .map(V2Entity::new)
                ),
                HttpStatus.OK);
    }

    @RequestMapping(path = "/by-tag", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<Map<String, List<V2Entity>>> getOntologiesByTag(
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @ParameterObject JsonTransformOptions outputOpts
    ) throws IOException {
        return new ResponseEntity<>(
                ontologyRepository.getGroupedByField("tags", lang, outputOpts),
                HttpStatus.OK);
    }

    @RequestMapping(path = "/by-domain", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<Map<String, List<V2Entity>>> getOntologiesByDomain(
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @ParameterObject JsonTransformOptions outputOpts
    ) throws IOException {
        return new ResponseEntity<>(
                ontologyRepository.getGroupedByField("domain", lang, outputOpts),
                HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2Entity> getOntology(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "Ontology Id to get the information about.",
                    example = "efo") String ontologyId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @ParameterObject JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException {
        logger.trace("ontologyId = {}, lang = {}", ontologyId, lang);
        V2Entity entity = ontologyRepository.getById(ontologyId, lang, outputOpts);
        if (entity == null) throw new ResourceNotFoundException("The requested resource was not found.");
        return new ResponseEntity<>( entity, HttpStatus.OK);
    }
}
