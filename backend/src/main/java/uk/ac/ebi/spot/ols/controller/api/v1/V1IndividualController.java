package uk.ac.ebi.spot.ols.controller.api.v1;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.server.ExposesResourceFor;
import org.springframework.hateoas.server.RepresentationModelProcessor;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.repository.v1.V1IndividualRepository;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Simon Jupp
 * @date 18/08/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Tag(name = "Individual Controller", description = "NOTE: For IRI parameters, the value must be URL encoded. " +
        "For example, the IRI http://purl.obolibrary.org/obo/IAO_0000124 should be encoded as http%3A%252F%2Fpurl.obolibrary.org%2Fobo%2FIAO_0000124.")
@RestController
@RequestMapping("/api/individuals")
@ExposesResourceFor(V1Individual.class)
public class V1IndividualController implements
        RepresentationModelProcessor<RepositoryLinksResource> {

    @Autowired
    private V1IndividualRepository individualRepository;

    @Autowired
    V1IndividualAssembler individualAssembler;

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(WebMvcLinkBuilder.linkTo(V1IndividualController.class).withRel("individuals"));
        return resource;
    }

    @RequestMapping(path = "/{iri}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<PagedModel<V1Individual>> getAllIndividuals(
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the individual, this value must be double URL encoded",
                    example = "http%3A%252F%2Fpurl.obolibrary.org%2Fobo%2FIAO_0000124") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {
        String decoded = null;
        decoded = UriUtils.decode(termId, "UTF-8");
        return getAllIndividuals(decoded, null, null, lang, pageable, assembler);

    }

    @RequestMapping(path = "", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<PagedModel<V1Individual>> getAllIndividuals(
            @RequestParam(value = "iri", required = false)
            @Parameter(name = "iri",
                    description = "The IRI of the individual, this value must be double URL encoded",
                    example = "http%3A%252F%2Fpurl.obolibrary.org%2Fobo%2FIAO_0000124") String iri,
            @RequestParam(value = "short_form", required = false)
            @Parameter(name = "short_form",
                    description = "This refers to the short form of the individual.",
                    example = "IAO_0000124") String shortForm,
            @RequestParam(value = "obo_id", required = false)
            @Parameter(name = "obo_id",
                    description = "This refers to the OBO ID of the individual.",
                    example = "IAO:0000124") String oboId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {

        Page<V1Individual> terms = null;

        if (iri != null) {
            terms = individualRepository.findAllByIri(iri, lang, pageable);
        } else if (shortForm != null) {
            terms = individualRepository.findAllByShortForm(shortForm, lang, pageable);
        } else if (oboId != null) {
            terms = individualRepository.findAllByOboId(oboId, lang, pageable);
        } else {
            terms = individualRepository.findAll(lang, pageable);
        }

        return new ResponseEntity<>(assembler.toModel(terms, individualAssembler), HttpStatus.OK);
    }
    
    @RequestMapping(path = "/findByIdAndIsDefiningOntology/{iri}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<PagedModel<V1Individual>> getAllIndividualsByIdAndIsDefiningOntology(
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the individual, this value must be double URL encoded",
                    example = "http%3A%252F%2Fpurl.obolibrary.org%2Fobo%2FIAO_0000124") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {
        String decoded = null;
        decoded = UriUtils.decode(termId, "UTF-8");
        return getAllIndividualsByIdAndIsDefiningOntology(decoded, null, null, lang, pageable, assembler);

    }    
    
    
    @RequestMapping(path = "/findByIdAndIsDefiningOntology", 
    		produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, 
    		method = RequestMethod.GET)
    public HttpEntity<PagedModel<V1Individual>> getAllIndividualsByIdAndIsDefiningOntology(
            @RequestParam(value = "iri", required = false)
            @Parameter(name = "iri",
                    description = "The IRI of the individual, this value must be double URL encoded",
                    example = "http%3A%252F%2Fpurl.obolibrary.org%2Fobo%2FIAO_0000124") String iri,
            @RequestParam(value = "short_form", required = false)
            @Parameter(name = "short_form",
                    description = "This refers to the short form of the individual.",
                    example = "IAO_0000124") String shortForm,
            @RequestParam(value = "obo_id", required = false)
            @Parameter(name = "obo_id",
                    description = "This refers to the OBO ID of the individual.",
                    example = "IAO:0000124") String oboId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {

        Page<V1Individual> terms = null;

        if (iri != null) {
            terms = individualRepository.findAllByIriAndIsDefiningOntology(iri, lang, pageable);
        } else if (shortForm != null) {
            terms = individualRepository.findAllByShortFormAndIsDefiningOntology(shortForm, lang, pageable);
        } else if (oboId != null) {
            terms = individualRepository.findAllByOboIdAndIsDefiningOntology(oboId, lang, pageable);
        } else {
            terms = individualRepository.findAllByIsDefiningOntology(lang, pageable);
        }

        return new ResponseEntity<>(assembler.toModel(terms, individualAssembler), HttpStatus.OK);
    }
    

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "EntityModel not found")
    @ExceptionHandler(ResourceNotFoundException.class)
    void handleError(HttpServletRequest req, Exception exception) {
    }

}