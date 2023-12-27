package uk.ac.ebi.spot.ols.controller.api.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
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
import uk.ac.ebi.spot.ols.model.v1.V1Property;
import uk.ac.ebi.spot.ols.repository.v1.V1JsTreeRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1PropertyRepository;
import uk.ac.ebi.spot.ols.service.Neo4jClient;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

@RestController
@RequestMapping("/api/ontologies")
public class V1OntologyPropertyController {

    @Autowired
    private V1PropertyRepository propertyRepository;

    @Autowired
    V1PropertyAssembler termAssembler;

    @Autowired
    V1JsTreeRepository jsTreeRepository;

    @Autowired
    Neo4jClient neo4jClient;

    @RequestMapping(path = "/{onto}/properties", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Property>> getAllPropertiesByOntology(
            @PathVariable("onto") String ontologyId,
            @RequestParam(value = "iri", required = false) String iri,
            @RequestParam(value = "short_form", required = false) String shortForm,
            @RequestParam(value = "obo_id", required = false) String oboId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {

        Page<V1Property> terms = null;

        ontologyId = ontologyId.toLowerCase();
        if (iri != null) {
            V1Property term = propertyRepository.findByOntologyAndIri(ontologyId, iri, lang);
            if (term != null) {
                terms =  new PageImpl<V1Property>(Arrays.asList(term));
            }
        }
        else if (shortForm != null) {
            V1Property term = propertyRepository.findByOntologyAndShortForm(ontologyId, shortForm, lang);
            if (term != null) {
                terms =  new PageImpl<V1Property>(Arrays.asList(term));
            }
        }
        else if (oboId != null) {
            V1Property term = propertyRepository.findByOntologyAndOboId(ontologyId, oboId, lang);
            if (term != null) {
                terms =  new PageImpl<V1Property>(Arrays.asList(term));
            }
        }
        else {
            terms = propertyRepository.findAllByOntology(ontologyId, lang, pageable);
        }

        return new ResponseEntity<>( assembler.toModel(terms, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/properties/roots", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Property>> getRoots(
            @PathVariable("onto") String ontologyId,
            @RequestParam(value = "includeObsoletes", defaultValue = "false", required = false) boolean includeObsoletes,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException {
        ontologyId = ontologyId.toLowerCase();

        Page<V1Property> roots = propertyRepository.getRoots(ontologyId, includeObsoletes, lang, pageable);
        if (roots == null) throw  new ResourceNotFoundException();
        return new ResponseEntity<>( assembler.toModel(roots, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/properties/{id}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<EntityModel<V1Property>> getProperty(
            @PathVariable("onto") String ontologyId,
            @PathVariable("id") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) throws ResourceNotFoundException {
        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        V1Property term = propertyRepository.findByOntologyAndIri(ontologyId, decoded, lang);
        return new ResponseEntity<>( termAssembler.toModel(term), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/properties/{id}/parents", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Property>> getParents(
            @PathVariable("onto") String ontologyId,
            @PathVariable("id") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {
        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        Page<V1Property> parents = propertyRepository.getParents(ontologyId, decoded, lang, pageable);
        return new ResponseEntity<>( assembler.toModel(parents, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/properties/{id}/children", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Property>> children(
            @PathVariable("onto") String ontologyId,
            @PathVariable("id") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {
        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        Page<V1Property> children = propertyRepository.getChildren(ontologyId, decoded, lang, pageable);
        return new ResponseEntity<>( assembler.toModel(children, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/properties/{id}/descendants", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Property>> descendants(
            @PathVariable("onto") String ontologyId,
            @PathVariable("id") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {
        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        Page<V1Property> descendants = propertyRepository.getDescendants(ontologyId, decoded, lang, pageable);
        return new ResponseEntity<>( assembler.toModel(descendants, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/properties/{id}/ancestors", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Property>> ancestors(
            @PathVariable("onto") String ontologyId,
            @PathVariable("id") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {
        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        Page<V1Property> ancestors = propertyRepository.getAncestors(ontologyId, decoded, lang, pageable);
        return new ResponseEntity<>( assembler.toModel(ancestors, termAssembler), HttpStatus.OK);
    }


    @RequestMapping(path = "/{onto}/properties/{id}/jstree/children/{nodeid}", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<String> graphJsTreeChildren(
            @PathVariable("onto") String ontologyId,
            @PathVariable("id") String termId,
            @PathVariable("nodeid") String jstreeId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) {
        ontologyId = ontologyId.toLowerCase();

        try {
            String decoded = UriUtils.decode(termId, "UTF-8");

            Object object= jsTreeRepository.getJsTreeChildrenForProperty(decoded, jstreeId, ontologyId, lang);
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            return new HttpEntity<String>(ow.writeValueAsString(object));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        throw new ResourceNotFoundException();
    }

    @RequestMapping(path = "/{onto}/properties/{id}/jstree",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE},
            method = RequestMethod.GET)
    HttpEntity<String> getJsTree(
            @PathVariable("onto") String ontologyId,
            @PathVariable("id") String termId,
            @RequestParam(value = "siblings", defaultValue = "false", required = false) boolean siblings,
            @RequestParam(value = "viewMode", defaultValue = "PreferredRoots", required = false) String viewMode,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang)
    {
        ontologyId = ontologyId.toLowerCase();

        try {
            String decoded = UriUtils.decode(termId, "UTF-8");

            Object object= jsTreeRepository.getJsTreeForProperty(decoded, ontologyId, lang);
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
