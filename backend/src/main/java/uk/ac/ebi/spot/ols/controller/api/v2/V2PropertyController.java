package uk.ac.ebi.spot.ols.controller.api.v2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PageableDefault;
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
import uk.ac.ebi.spot.ols.repository.v2.V2PropertyRepository;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/v2")
public class V2PropertyController {

    @Autowired
    V2PropertyRepository propertyRepository;

    @RequestMapping(path = "/properties", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getProperties(
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
                    propertyRepository.find(pageable, lang, search, searchFields, boostFields, exactMatch, DynamicQueryHelper.filterProperties(properties))
                ),
                 HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/properties", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getProperties(
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @PathVariable("onto") @NotNull String ontologyId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "searchFields", required = false) String searchFields,
            @RequestParam(value = "boostFields", required = false) String boostFields,
            @RequestParam(value = "exactMatch", required = false, defaultValue = "false") boolean exactMatch,
            @RequestParam(value = "includeObsoleteEntities", required = false, defaultValue = "false") boolean includeObsoleteEntities,
            @RequestParam MultiValueMap<String,String> searchProperties
    ) throws ResourceNotFoundException, IOException {

        Map<String,Collection<String>> properties = new HashMap<>();
        if(!includeObsoleteEntities)
            properties.put("isObsolete", List.of("false"));
        properties.putAll(searchProperties);

        return new ResponseEntity<>(
                new V2PagedAndFacetedResponse<>(
                    propertyRepository.findByOntologyId(ontologyId, pageable, lang, search, searchFields, boostFields, exactMatch, DynamicQueryHelper.filterProperties(properties))
                ),
                HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/properties/{property}", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2Entity> getProperty(
            @PathVariable("onto") String ontologyId,
            @PathVariable("property") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        V2Entity entity = propertyRepository.getByOntologyIdAndIri(ontologyId, iri, lang);
        if (entity == null) throw new ResourceNotFoundException();
        return new ResponseEntity<>( entity, HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/properties/{property}/children", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getChildrenByOntology(
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @PathVariable("onto") String ontologyId,
            @PathVariable("property") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        return new ResponseEntity<>(
                new V2PagedResponse<>(
                    propertyRepository.getChildrenByOntologyId(ontologyId, pageable, iri, lang)
                ),
                 HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/properties/{property}/ancestors", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getAncestorsByOntology(
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @PathVariable("onto") String ontologyId,
            @PathVariable("property") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        return new ResponseEntity<>(
                new V2PagedResponse<>(
                propertyRepository.getAncestorsByOntologyId(ontologyId, pageable, iri, lang)
                ), HttpStatus.OK);
    }

}



