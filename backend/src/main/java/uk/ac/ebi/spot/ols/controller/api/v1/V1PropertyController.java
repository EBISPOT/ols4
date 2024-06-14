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
import uk.ac.ebi.spot.ols.model.v1.V1Property;
import uk.ac.ebi.spot.ols.repository.v1.V1PropertyRepository;

import javax.servlet.http.HttpServletRequest;

@Tag(name = "Property Controller", description = "NOTE: For IRI parameters, the value must be URL encoded. " +
        "For example, the IRI http://purl.obolibrary.org/obo/DUO_0000041 should be encoded as http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000041.")
@RestController
@RequestMapping("/api/properties")
@ExposesResourceFor(V1Property.class)
public class V1PropertyController implements
        RepresentationModelProcessor<RepositoryLinksResource> {

    @Autowired
    private V1PropertyRepository propertyRepository;

    @Autowired
    V1PropertyAssembler termAssembler;

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(WebMvcLinkBuilder.linkTo(V1PropertyController.class).withRel("properties"));
        return resource;
    }

    @RequestMapping(path = "/{iri}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Property>> getPropertiesByIri(@PathVariable("iri")
                                                          @Parameter(name = "iri",
                                                                  description = "The IRI of the property, this value must be double URL encoded",
                                                                  example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000041") String termId,
                                                              @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                                              @Parameter(hidden = true) Pageable pageable,
                                                              @Parameter(hidden = true) PagedResourcesAssembler assembler

    ) throws ResourceNotFoundException {

        String decoded = null;
        decoded = UriUtils.decode(termId, "UTF-8");
        return getAllProperties(decoded, null, null, lang, pageable, assembler);
    }

    @RequestMapping(path = "", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Property>> getAllProperties(
            @RequestParam(value = "iri", required = false)
            @Parameter(name = "iri",
                    description = "The IRI of the property, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000041") String iri,
            @RequestParam(value = "short_form", required = false)
            @Parameter(name = "short_form",
                    description = "This refers to the short form of the property.",
                    example = "DUO_0000041") String shortForm,
            @RequestParam(value = "obo_id", required = false)
            @Parameter(name = "obo_id",
                    description = "This refers to the OBO ID of the property.",
                    example = "DUO:0000041") String oboId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {

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

        return new ResponseEntity<>( assembler.toModel(terms, termAssembler), HttpStatus.OK);
    }


    @RequestMapping(path = "/findByIdAndIsDefiningOntology/{iri}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Property>> getPropertiesByIriAndIsDefiningOntology(@PathVariable("iri")
                                                                               @Parameter(name = "iri",
                                                                                       description = "The IRI of the property, this value must be double URL encoded",
                                                                                       example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000041") String termId,
                                                                                   @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                                                                   @Parameter(hidden = true) Pageable pageable,
                                                                                   @Parameter(hidden = true) PagedResourcesAssembler assembler

    ) throws ResourceNotFoundException {

        String decoded = null;
        decoded = UriUtils.decode(termId, "UTF-8");
        return getPropertiesByIdAndIsDefiningOntology(decoded, null, null, lang, pageable, assembler);
    }    
    
    @RequestMapping(path = "/findByIdAndIsDefiningOntology", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Property>> getPropertiesByIdAndIsDefiningOntology(
            @RequestParam(value = "iri", required = false)
            @Parameter(name = "iri",
                    description = "The IRI of the property, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000041") String iri,
            @RequestParam(value = "short_form", required = false)
            @Parameter(name = "short_form",
                    description = "This refers to the name of the property.",
                    example = "DUO_0000041") String shortForm,
            @RequestParam(value = "obo_id", required = false)
            @Parameter(name = "obo_id",
                    description = "This refers to the OBO ID of the property.",
                    example = "DUO:0000041") String oboId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {

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

        return new ResponseEntity<>( assembler.toModel(terms, termAssembler), HttpStatus.OK);
    }
    
    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "EntityModel not found")
    @ExceptionHandler(ResourceNotFoundException.class)
    public void handleError(HttpServletRequest req, Exception exception) {
    }
}
