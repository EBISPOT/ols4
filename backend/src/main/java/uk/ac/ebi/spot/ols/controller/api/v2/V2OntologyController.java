package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.Gson;
import io.swagger.v3.oas.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.ols.controller.api.v2.helpers.DynamicQueryHelper;
import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedAndFacetedResponse;
import uk.ac.ebi.spot.ols.model.FilterOption;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.solr.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.v2.V2EntityRepository;
import uk.ac.ebi.spot.ols.repository.v2.V2OntologyRepository;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/v2/ontologies")
public class V2OntologyController {

    private Gson gson = new Gson();

    @Autowired
    V2OntologyRepository ontologyRepository;

    private static final Logger logger = LoggerFactory.getLogger(V2OntologyController.class);

    @RequestMapping(path = "", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getOntologies(
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "searchFields", required = false) String searchFields,
            @RequestParam(value = "boostFields", required = false) String boostFields,
            @RequestParam(value = "exactMatch", required = false, defaultValue = "false") boolean exactMatch,
            @RequestParam(value = "includeObsoleteEntities", required = false, defaultValue = "false") boolean includeObsoleteEntities,
            @RequestParam Map<String, Collection<String>> searchProperties,
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
            properties.put("isObsolete", List.of("false"));
        properties.putAll(searchProperties);

        return new ResponseEntity<>(
                new V2PagedAndFacetedResponse<>(
                    ontologyRepository.find(pageable, lang, search, searchFields, boostFields, exactMatch, DynamicQueryHelper.filterProperties(properties),schemas,classifications,ontologies,exclusive,filterOption)
                ),
                HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2Entity> getOntology(
            @PathVariable("onto") String ontologyId,
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
