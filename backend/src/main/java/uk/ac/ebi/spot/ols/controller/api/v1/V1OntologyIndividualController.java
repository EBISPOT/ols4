package uk.ac.ebi.spot.ols.controller.api.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.model.v1.V1Property;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.v1.V1IndividualRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1JsTreeRepository;
import uk.ac.ebi.spot.ols.service.Neo4jClient;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

/**
 * @author Simon Jupp
 * @date 02/11/15
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Tag(name = "Ontology Individual Controller", description = "NOTE: For IRI parameters, the value must be URL encoded. " +
        "For example, the IRI http://purl.obolibrary.org/obo/IAO_0000103 should be encoded as http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FIAO_0000103.")
@RestController
@RequestMapping("/api/ontologies")
public class V1OntologyIndividualController {

    @Autowired
    private V1IndividualRepository individualRepository;

    @Autowired
    V1IndividualAssembler individualAssembler;

    @Autowired
    V1TermAssembler termAssembler;

    @Autowired
    Neo4jClient neo4jClient;

    @Autowired
    V1JsTreeRepository jsTreeRepository;

    @RequestMapping(path = "/{onto}/individuals", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Individual>> getAllIndividualsByOntology(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Information Artifact Ontology, the ID is iao.",
                    example = "iao") String ontologyId,
            @RequestParam(value = "iri", required = false)
            @Parameter(name = "iri",
                    description = "The IRI of the individual, this IRI should exist in the specified ontology by {onto} param. This value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FIAO_0000103") String iri,
            @RequestParam(value = "short_form", required = false)
            @Parameter(name = "short_form",
                    description = "This refers to the short form of the individual, it should exist in the specified ontology by {onto} param.",
                    example = "IAO_0000124") String shortForm,
            @RequestParam(value = "obo_id", required = false)
            @Parameter(name = "obo_id",
                    description = "This refers to the OBO ID of the individual, it should exist in the specified ontology by {onto} param.",
                    example = "IAO:0000124") String oboId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {

        Page<V1Individual> terms = null;

        ontologyId = ontologyId.toLowerCase();
        if (iri != null) {
            V1Individual term = individualRepository.findByOntologyAndIri(ontologyId, iri, lang);
            if (term != null) {
                terms = new PageImpl<V1Individual>(Arrays.asList(term));
            }
        } else if (shortForm != null) {
            V1Individual term = individualRepository.findByOntologyAndShortForm(ontologyId, shortForm, lang);
            if (term != null) {
                terms = new PageImpl<V1Individual>(Arrays.asList(term));
            }
        } else if (oboId != null) {
            V1Individual term = individualRepository.findByOntologyAndOboId(ontologyId, oboId, lang);
            if (term != null) {
                terms = new PageImpl<V1Individual>(Arrays.asList(term));
            }
        } else {
            terms = individualRepository.findAllByOntology(ontologyId, lang, pageable);
        }

        return new ResponseEntity<>(assembler.toModel(terms, individualAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/individuals/{iri}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<EntityModel<V1Individual>> getIndividual(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Information Artifact Ontology, the ID is iao.",
                    example = "iao") String ontologyId,
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the individual, this IRI should exist in the specified ontology by {onto} param. This value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FIAO_0000103") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) throws ResourceNotFoundException {
        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        V1Individual term = individualRepository.findByOntologyAndIri(ontologyId, decoded, lang);
        return new ResponseEntity<>(individualAssembler.toModel(term), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/individuals/{iri}/types", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> getDirectTypes(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Information Artifact Ontology, the ID is iao.",
                    example = "iao") String ontologyId,
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the individual, this IRI should exist in the specified ontology by {onto} param. This value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FIAO_0000103") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler
    ) {
        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        Page<V1Term> parents = individualRepository.getDirectTypes(ontologyId, decoded, lang, pageable);
        return new ResponseEntity<>(assembler.toModel(parents, termAssembler), HttpStatus.OK);
    }


    @RequestMapping(path = "/{onto}/individuals/{iri}/alltypes", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Property>> ancestors(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Information Artifact Ontology, the ID is iao.",
                    example = "iao") String ontologyId,
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the individual, this IRI should exist in the specified ontology by {onto} param. This value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FIAO_0000103") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {
        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        Page<V1Term> ancestors = individualRepository.getAllTypes(ontologyId, decoded, lang, pageable);
        return new ResponseEntity<>(assembler.toModel(ancestors, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/individuals/{iri}/jstree", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<String> getJsTree(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Information Artifact Ontology, the ID is iao.",
                    example = "iao") String ontologyId,
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the individual, this IRI should exist in the specified ontology by {onto} param. This value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FIAO_0000103") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) {
        ontologyId = ontologyId.toLowerCase();

        try {
            String decoded = UriUtils.decode(termId, "UTF-8");

            Object object = jsTreeRepository.getJsTreeForIndividual(decoded, ontologyId, lang);
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            return new HttpEntity<String>(ow.writeValueAsString(object));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        throw new ResourceNotFoundException();
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "EntityModel not found")
    @ExceptionHandler(ResourceNotFoundException.class)
    public void handleError(HttpServletRequest req, Exception exception) {
    }

}
