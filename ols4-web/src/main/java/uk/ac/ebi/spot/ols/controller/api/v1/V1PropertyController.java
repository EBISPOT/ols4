package uk.ac.ebi.spot.ols.controller.api.v1;

import java.io.UnsupportedEncodingException;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.ResourceProcessor;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.util.UriUtils;

import uk.ac.ebi.spot.ols.model.v1.V1Property;
import uk.ac.ebi.spot.ols.repository.v1.V1PropertyRepository;

@Controller
@RequestMapping("/api/properties")
@ExposesResourceFor(V1Property.class)
public class V1PropertyController implements
        ResourceProcessor<RepositoryLinksResource> {

    @Autowired
    private V1PropertyRepository propertyRepository;

    @Autowired
    V1PropertyAssembler termAssembler;

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(ControllerLinkBuilder.linkTo(V1PropertyController.class).withRel("properties"));
        return resource;
    }

    @RequestMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Property>> getPropertiesByIri(@PathVariable("id") String termId,
                                                              @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                                              Pageable pageable,
                                                              PagedResourcesAssembler assembler

    ) throws ResourceNotFoundException {

        String decoded = null;
        try {
            decoded = UriUtils.decode(termId, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new ResourceNotFoundException("Can't decode IRI: " + termId);
        }
        return getAllProperties(decoded, null, null, lang, pageable, assembler);
    }

    @RequestMapping(path = "", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Property>> getAllProperties(
            @RequestParam(value = "iri", required = false) String iri,
            @RequestParam(value = "short_form", required = false) String shortForm,
            @RequestParam(value = "obo_id", required = false) String oboId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {

        Page<V1Property> terms = null;

        if (iri != null) {
            terms = propertyRepository.findAllByIri(iri, lang, pageable);
        }
        else if (shortForm != null) {
            terms = propertyRepository.findAllByShortForm(shortForm, lang, pageable);
        }
        else if (oboId != null) {
            terms = propertyRepository.findAllByOboId(oboId, lang, pageable);
        }
        else {
            terms = propertyRepository.findAll(lang, pageable);
        }

        return new ResponseEntity<>( assembler.toResource(terms, termAssembler), HttpStatus.OK);
    }


    @RequestMapping(path = "/findByIdAndIsDefiningOntology/{id}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Property>> getPropertiesByIriAndIsDefiningOntology(@PathVariable("id") String termId,
                                                                                   @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                                                                   Pageable pageable,
                                                                                   PagedResourcesAssembler assembler

    ) throws ResourceNotFoundException {

        String decoded = null;
        try {
            decoded = UriUtils.decode(termId, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new ResourceNotFoundException("Can't decode IRI: " + termId);
        }
        return getPropertiesByIdAndIsDefiningOntology(decoded, null, null, lang, pageable, assembler);
    }    
    
    @RequestMapping(path = "/findByIdAndIsDefiningOntology", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Property>> getPropertiesByIdAndIsDefiningOntology(
            @RequestParam(value = "iri", required = false) String iri,
            @RequestParam(value = "short_form", required = false) String shortForm,
            @RequestParam(value = "obo_id", required = false) String oboId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {

        Page<V1Property> terms = null;

        if (iri != null) {
            terms = propertyRepository.findAllByIriAndIsDefiningOntology(iri, lang, pageable);
        }
        else if (shortForm != null) {
            terms = propertyRepository.findAllByShortFormAndIsDefiningOntology(shortForm, lang, pageable);
        }
        else if (oboId != null) {
            terms = propertyRepository.findAllByOboIdAndIsDefiningOntology(oboId, lang, pageable);
        }
        else {
            terms = propertyRepository.findAllByIsDefiningOntology(lang, pageable);
        }

        return new ResponseEntity<>( assembler.toResource(terms, termAssembler), HttpStatus.OK);
    }
    
    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Resource not found")
    @ExceptionHandler(ResourceNotFoundException.class)
    public void handleError(HttpServletRequest req, Exception exception) {
    }
}
