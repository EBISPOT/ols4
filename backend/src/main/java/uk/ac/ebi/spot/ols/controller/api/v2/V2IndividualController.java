package uk.ac.ebi.spot.ols.controller.api.v2;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.controller.api.v2.helpers.DynamicQueryHelper;
import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedAndFacetedResponse;
import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedResponse;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.v2.V2IndividualRepository;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2")
public class V2IndividualController {

    @Autowired
    V2IndividualRepository individualRepository;

    @RequestMapping(path = "/individuals", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getIndividuals(
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "searchFields", required = false) String searchFields,
            @RequestParam(value = "boostFields", required = false) String boostFields,
            @RequestParam(value = "exactMatch", required = false, defaultValue = "false") boolean exactMatch,
            @RequestParam(value = "includeObsoleteEntities", required = false, defaultValue = "false") boolean includeObsoleteEntities,
            @RequestParam MultiValueMap<String,String> searchProperties
    ) throws ResourceNotFoundException, IOException {

        Map<String, Collection<String>> properties = new HashMap<>();
        if(!includeObsoleteEntities)
            properties.put("isObsolete", List.of("false"));
        properties.putAll(searchProperties);

        return new ResponseEntity<>(
                new V2PagedAndFacetedResponse<>(
                    individualRepository.find(pageable, lang, search, searchFields, boostFields, exactMatch, DynamicQueryHelper.filterProperties(properties))
                ),
                HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/individuals", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getIndividuals(
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

        Map<String, Collection<String>> properties = new HashMap<>();
        if(!includeObsoleteEntities)
            properties.put("isObsolete", List.of("false"));
        properties.putAll(searchProperties);

        return new ResponseEntity<>(
                new V2PagedAndFacetedResponse<>(
                    individualRepository.findByOntologyId(ontologyId, pageable, lang, search, searchFields, boostFields, exactMatch, DynamicQueryHelper.filterProperties(properties))
                ),
                HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/individuals/{individual}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2Entity> getIndividual(
            @PathVariable("onto") String ontologyId,
            @PathVariable("individual") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        V2Entity entity = individualRepository.getByOntologyIdAndIri(ontologyId, iri, lang);
        if (entity == null) throw new ResourceNotFoundException();
        return new ResponseEntity<>( entity, HttpStatus.OK);
    }


    // The instances of classes are individuals. So, the /instances endpoint is part of the Class controller.
    //
    @RequestMapping(path = "/ontologies/{onto}/classes/{class}/instances", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getClassInstances(
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @PathVariable("onto") String ontologyId,
            @PathVariable("class") String classIri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) throws ResourceNotFoundException, IOException {

        classIri = UriUtils.decode(classIri, "UTF-8");

        return new ResponseEntity<>(
                new V2PagedResponse<>(
                        individualRepository.getInstancesOfClass(ontologyId, classIri, pageable, lang)
                ),
                HttpStatus.OK);

    }


}



