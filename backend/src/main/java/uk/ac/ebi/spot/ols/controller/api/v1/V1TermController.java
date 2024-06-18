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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.v1.V1TermRepository;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Simon Jupp
 * @date 23/06/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Tag(name = "Terms Controller", description = "NOTE: For IRI parameters, the value must be URL encoded. " +
        "For example, the IRI http://purl.obolibrary.org/obo/DUO_0000017 should be encoded as http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017.")
@RestController
@RequestMapping("/api/terms")
@ExposesResourceFor(V1Term.class)
public class V1TermController implements
        RepresentationModelProcessor<RepositoryLinksResource> {

    @Autowired
    private V1TermRepository termRepository;

    @Autowired
    V1TermAssembler termAssembler;

    @RequestMapping(path = "/{iri}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    private HttpEntity<PagedModel<V1Term>> getTermsByIri(@PathVariable("iri")
                                                         @Parameter(name = "iri",
                                                                     description = "The IRI of the term, this value must be double URL encoded",
                                                                     example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String termId,
                                                         @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                                         @Parameter(hidden = true) Pageable pageable,
                                                         @Parameter(hidden = true) PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException {

        String decoded = null;
        decoded = UriUtils.decode(termId, "UTF-8");

        return getTerms(decoded, null, null, null, lang, pageable, assembler);
    }

    @RequestMapping(path = "", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    private HttpEntity<PagedModel<V1Term>> getTerms(
            @RequestParam(value = "iri", required = false)
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String iri,
            @RequestParam(value = "short_form", required = false)
            @Parameter(name = "short_form",
                    description = "This refers to the short form of the term.",
                    example = "DUO_0000017") String shortForm,
            @RequestParam(value = "obo_id", required = false)
            @Parameter(name = "obo_id",
                    description = "This refers to the OBO ID of the term.",
                    example = "DUO:0000017") String oboId,
            @RequestParam(value = "id", required = false)
            @Parameter(name = "id",
                    description = "This can be any of the above i.e. iri, short_form or obo_id.") String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {

        Page<V1Term> terms = null;
        if (id == null) {
            if (iri != null) {
                terms = termRepository.findAllByIri(iri, lang, pageable);
            } else if (shortForm != null) {
                terms = termRepository.findAllByShortForm(shortForm, lang, pageable);
            } else if (oboId != null) {
                terms = termRepository.findAllByOboId(oboId, lang, pageable);
            } else {
                terms = termRepository.findAll(lang, pageable);
                if (terms == null) throw new ResourceNotFoundException("Ontology not found");
            }
        } else {
            terms = termRepository.findAllByIri(id, lang, pageable);
            if (terms.getContent().isEmpty()) {
                terms = termRepository.findAllByShortForm(id, lang, pageable);
                if (terms.getContent().isEmpty()) {
                    terms = termRepository.findAllByOboId(id, lang, pageable);
                }
            }
        }

        return new ResponseEntity<>(assembler.toModel(terms, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/findByIdAndIsDefiningOntology/{iri}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    private HttpEntity<PagedModel<V1Term>> getTermsByIdAndIsDefiningOntology(@PathVariable("iri")
                                                                             @Parameter(name = "iri",
                                                                                         description = "The IRI of the term, this value must be double URL encoded",
                                                                                         example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String termId,
                                                                             @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                                                             @Parameter(hidden = true) Pageable pageable,
                                                                             @Parameter(hidden = true) PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException {

        String decoded = null;
        decoded = UriUtils.decode(termId, "UTF-8");
        return getTermsByIdAndIsDefiningOntology(decoded, null, null, null, lang, pageable, assembler);
    }

    @RequestMapping(path = "/findByIdAndIsDefiningOntology", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    private HttpEntity<PagedModel<V1Term>> getTermsByIdAndIsDefiningOntology(
            @RequestParam(value = "iri", required = false)
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String iri,
            @RequestParam(value = "short_form", required = false)
            @Parameter(name = "short_form",
                    description = "This refers to the short form of the term.",
                    example = "DUO_0000017") String shortForm,
            @RequestParam(value = "obo_id", required = false)
            @Parameter(name = "obo_id",
                    description = "This refers to the OBO ID of the term.",
                    example = "DUO:0000017") String oboId,
            @RequestParam(value = "id", required = false)
            @Parameter(name = "id",
                    description = "This can be any of the above i.e. iri, short_form or obo_id.") String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {


        Page<V1Term> terms = null;
        if (id == null) {
            if (iri != null) {
                terms = termRepository.findAllByIriAndIsDefiningOntology(iri, lang, pageable);
            } else if (shortForm != null) {
                terms = termRepository.findAllByShortFormAndIsDefiningOntology(shortForm, lang, pageable);
            } else if (oboId != null) {
                terms = termRepository.findAllByOboIdAndIsDefiningOntology(oboId, lang, pageable);
            } else {
                terms = termRepository.findAllByIsDefiningOntology(lang, pageable);
                if (terms == null) throw new ResourceNotFoundException("Ontology not found");
            }
        } else {
            terms = termRepository.findAllByIriAndIsDefiningOntology(id, lang, pageable);
            if (terms.getContent().isEmpty()) {
                terms = termRepository.findAllByShortFormAndIsDefiningOntology(id, lang, pageable);
                if (terms.getContent().isEmpty()) {
                    terms = termRepository.findAllByOboIdAndIsDefiningOntology(id, lang, pageable);
                }
            }
        }

        return new ResponseEntity<>(assembler.toModel(terms, termAssembler), HttpStatus.OK);
    }


    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "EntityModel not found")
    @ExceptionHandler(ResourceNotFoundException.class)
    public void handleError(HttpServletRequest req, Exception exception) {
    }

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(WebMvcLinkBuilder.linkTo(V1TermController.class).withRel("terms"));
        return resource;
    }
}
