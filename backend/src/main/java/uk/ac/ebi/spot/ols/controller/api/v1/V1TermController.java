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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.v1.V1TermRepository;

/**
 * @author Simon Jupp
 * @date 23/06/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@RestController
@RequestMapping("/api/terms")
@ExposesResourceFor(V1Term.class)
public class V1TermController implements
        ResourceProcessor<RepositoryLinksResource> {

    @Autowired
    private V1TermRepository termRepository;

    @Autowired
    V1TermAssembler termAssembler;

    @RequestMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    private HttpEntity<PagedResources<V1Term>> getTermsByIri(@PathVariable("id") String termId,
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
        return getTerms(decoded, null, null,null, lang, pageable, assembler);
    }

    @RequestMapping(path = "", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    private HttpEntity<PagedResources<V1Term>> getTerms(
            @RequestParam(value = "iri", required = false) String iri,
            @RequestParam(value = "short_form", required = false) String shortForm,
            @RequestParam(value = "obo_id", required = false) String oboId,
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {

        Page<V1Term> terms = null;

        if (iri != null) {
            terms = termRepository.findAllByIri(iri, lang, pageable);
        }
        else if (shortForm != null) {
            terms = termRepository.findAllByShortForm(shortForm, lang, pageable);
        }
        else if (oboId != null) {
            terms = termRepository.findAllByOboId(oboId, lang, pageable);
        }
        else if (id != null) {
            terms = termRepository.findAllByIri(id,lang, pageable);
            if (terms.getContent().isEmpty()) {
                terms = termRepository.findAllByShortForm(id,lang, pageable);
                if (terms.getContent().isEmpty()) {
                    terms = termRepository.findAllByOboId(id,lang, pageable);
                }
            }
        }
        else {
            terms = termRepository.findAll(lang, pageable);
            if (terms == null) throw new ResourceNotFoundException("Ontology not found");
        }

        return new ResponseEntity<>( assembler.toResource(terms, termAssembler), HttpStatus.OK);
    }
    
    @RequestMapping(path = "/findByIdAndIsDefiningOntology/{id}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    private HttpEntity<PagedResources<V1Term>> getTermsByIdAndIsDefiningOntology(@PathVariable("id") String termId,
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
        return getTermsByIdAndIsDefiningOntology(decoded, null, null,null, lang, pageable, assembler);
    }    

    @RequestMapping(path = "/findByIdAndIsDefiningOntology", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    private HttpEntity<PagedResources<V1Term>> getTermsByIdAndIsDefiningOntology(
            @RequestParam(value = "iri", required = false) String iri,
            @RequestParam(value = "short_form", required = false) String shortForm,
            @RequestParam(value = "obo_id", required = false) String oboId,
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {

        Page<V1Term> terms = null;

        if (iri != null) {
        	terms = termRepository.findAllByIriAndIsDefiningOntology(iri, lang, pageable);
        }
        else if (shortForm != null) {
        	terms = termRepository.findAllByShortFormAndIsDefiningOntology(shortForm, lang, pageable);
        }
        else if (oboId != null) {
        	terms = termRepository.findAllByOboIdAndIsDefiningOntology(oboId, lang, pageable);
        }
        else if (id != null) {
            terms = termRepository.findAllByIriAndIsDefiningOntology(id, lang,pageable);
            if (terms.getContent().isEmpty()) {
                terms = termRepository.findAllByShortFormAndIsDefiningOntology(id, lang,pageable);
                if (terms.getContent().isEmpty()) {
                    terms = termRepository.findAllByOboIdAndIsDefiningOntology(id, lang,pageable);
                }
            }
        }       
        else {
        	terms = termRepository.findAllByIsDefiningOntology(lang, pageable);
        	if (terms == null) throw new ResourceNotFoundException("Ontology not found");
        }
        return new ResponseEntity<>( assembler.toResource(terms, termAssembler), HttpStatus.OK);
    }
    
    
    
    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Resource not found")
    @ExceptionHandler(ResourceNotFoundException.class)
    public void handleError(HttpServletRequest req, Exception exception) {
    }

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(ControllerLinkBuilder.linkTo(V1TermController.class).withRel("terms"));
        return resource;
    }
}
