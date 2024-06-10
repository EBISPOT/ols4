package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.ols.controller.api.v2.helpers.DynamicQueryHelper;
import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedAndFacetedResponse;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.solr.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.v2.V2EntityRepository;
import uk.ac.ebi.spot.ols.repository.v2.V2OntologyRepository;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            @RequestParam Map<String, Collection<String>> searchProperties
    ) throws ResourceNotFoundException, IOException {

        Map<String,Collection<String>> properties = new HashMap<>();
        if(!includeObsoleteEntities)
            properties.put("isObsolete", List.of("false"));
        properties.putAll(searchProperties);

        return new ResponseEntity<>(
                new V2PagedAndFacetedResponse<>(
                    ontologyRepository.find(pageable, lang, search, searchFields, boostFields, exactMatch, DynamicQueryHelper.filterProperties(properties))
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
}
