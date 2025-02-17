package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.Gson;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.ols.controller.api.v2.helpers.DynamicQueryHelper;
import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedAndFacetedResponse;
import uk.ac.ebi.spot.ols.model.FilterOption;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.v2.V2OntologyRepository;
import static uk.ac.ebi.ols.shared.DefinedFields.*;

import java.io.IOException;
import java.util.*;

@Tag(name = "V2 Ontology Controller", description = "This endpoint provides access to ontology information.")
@RestController
@RequestMapping("/api/v2/ontologies")
public class V2OntologyController {

    private Gson gson = new Gson();

    @Autowired
    V2OntologyRepository ontologyRepository;

    private static final Logger logger = LoggerFactory.getLogger(V2OntologyController.class);

    @RequestMapping(path = "", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getOntologies(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
                    description = "Specify the size of the result you want to get in the output",
                    example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
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
            @Parameter(name="searchProperties",
                    description = "Specify any other search field here which are not specified by searchFields or boostFields.",
                    example = "{}") Map<String, Collection<String>> searchProperties,
            @RequestParam(value = "schema", required = false) List<String> schemas,
            @RequestParam(value = "classification", required = false) List<String> classifications,
            @RequestParam(value = "ontology", required = false) List<String> ontologies,
            @Parameter(description = "Set to true (default setting is false) for intersection (default behavior is union) of classifications.")
            @RequestParam(value = "exclusive", required = false, defaultValue = "false") boolean exclusive,
            @Parameter(description = "Use License option to filter based on license.label, license.logo and license.url variables. " +
                    "Use Composite Option to filter based on the objects (i.e. collection, subject) within the classifications variable. " +
                    "Use Linear option to filter based on String and Collection<String> based variables.")
            @RequestParam(value = "option", required = false, defaultValue = "LINEAR") FilterOption filterOption
    ) throws ResourceNotFoundException, IOException {
        Map<String,Collection<String>> properties = new HashMap<>();
        if(!includeObsoleteEntities)
            properties.put(IS_OBSOLETE.getText(), List.of("false"));
        properties.putAll(searchProperties);

        return new ResponseEntity<>(
                new V2PagedAndFacetedResponse<>(
                    ontologyRepository.find(pageable, lang, search, searchFields, boostFields, exactMatch, DynamicQueryHelper.filterProperties(properties),schemas,classifications,ontologies,exclusive,filterOption)
                ),
                HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2Entity> getOntology(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "Ontology Id to get the information about.",
                    example = "efo") String ontologyId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) throws ResourceNotFoundException {
        logger.trace("ontologyId = {}, lang = {}", ontologyId, lang);
        V2Entity entity = ontologyRepository.getById(ontologyId, lang);
        if (entity == null) throw new ResourceNotFoundException();
        return new ResponseEntity<>( entity, HttpStatus.OK);
    }

    @RequestMapping(path = "/schemakeys", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<Page<String>> filterKeys(
            @PageableDefault(size = 100, page = 0) Pageable pageable,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            PagedResourcesAssembler assembler){
        Set<String> tempSet = ontologyRepository.getSchemaKeys(lang);
        List<String> tempList = new ArrayList<String>();
        tempList.addAll(tempSet);
        final int start = (int)pageable.getOffset();
        final int end = Math.min((start + pageable.getPageSize()), tempSet.size());
        Page<String> document = new PageImpl<>(tempList.subList(start, end), pageable, tempSet.size());
        return new ResponseEntity<>(document, HttpStatus.OK);
    }

    @RequestMapping(path = "/schemavalues", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<Page<String>> filterValues(
            @RequestParam(value = "schema", required = true) Collection<String> schemas,
            @PageableDefault(size = 100, page = 0) Pageable pageable,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            PagedResourcesAssembler assembler){
        Set<String> tempSet = ontologyRepository.getSchemaValues(schemas,lang);
        List<String> tempList = new ArrayList<String>();
        tempList.addAll(tempSet);
        final int start = (int)pageable.getOffset();
        final int end = Math.min((start + pageable.getPageSize()), tempSet.size());
        Page<String> document = new PageImpl<>(tempList.subList(start, end), pageable, tempSet.size());
        return new ResponseEntity<>(document, HttpStatus.OK);
    }

}
