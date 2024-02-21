package uk.ac.ebi.spot.ols.controller.api.v1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
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

import io.swagger.v3.oas.annotations.Parameter;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.repository.v1.V1OntologyRepository;
import java.lang.reflect.*;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Simon Jupp
 * @date 19/08/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Controller
@RequestMapping("/api/ontologies")
@ExposesResourceFor(V1Ontology.class)
public class V1OntologyController implements
        RepresentationModelProcessor<RepositoryLinksResource> {

    private Logger log = LoggerFactory.getLogger(getClass());

    public Logger getLog() {
        return log;
    }

    @Autowired
    private V1OntologyRepository ontologyRepository;

    @Autowired
    V1OntologyAssembler documentAssembler;

    @Autowired
    V1TermAssembler termAssembler;

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(WebMvcLinkBuilder.linkTo(V1OntologyController.class).withRel("ontologies"));
        return resource;
    }

    @RequestMapping(path = "", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Ontology>> getOntologies(
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException {
        Page<V1Ontology> document = ontologyRepository.getAll(lang, pageable);
        return new ResponseEntity<>( assembler.toModel(document, documentAssembler), HttpStatus.OK);
    }


    @RequestMapping(path = "/{onto}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<EntityModel<V1Ontology>> getOntology(
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @PathVariable("onto") String ontologyId) throws ResourceNotFoundException {
        ontologyId = ontologyId.toLowerCase();
        V1Ontology document = ontologyRepository.get(ontologyId, lang);
        if (document == null) throw new ResourceNotFoundException();
        return new ResponseEntity<>( documentAssembler.toModel(document), HttpStatus.OK);
    }

    @RequestMapping(path = "/filterby", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Ontology>> getOntologiesByMetadata(
    		@RequestParam(value = "schema", required = true) Collection<String> schemas,
    		@RequestParam(value = "classification", required = true) Collection<String> classifications,
    		@Parameter(description = "Set to true (default setting is false) for intersection (default behavior is union) of classifications.")
    		@RequestParam(value = "exclusive", required = false, defaultValue = "false") boolean exclusive,
            @PageableDefault(size = 100, page = 0) Pageable pageable,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException {
        Set<V1Ontology> tempSet = ontologyRepository.filter(schemas,classifications,exclusive,lang);
        List<V1Ontology> tempList = new ArrayList<V1Ontology>();
        tempList.addAll(tempSet);
        final int start = (int)pageable.getOffset();
        final int end = Math.min((start + pageable.getPageSize()), tempSet.size());
        Page<V1Ontology> document = new PageImpl<>(tempList.subList(start, end), pageable, tempSet.size());

        return new ResponseEntity<>( assembler.toModel(document, documentAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/schemakeys", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<Set<String>> filterKeys(
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang){
        return new ResponseEntity<>(ontologyRepository.getSchemaKeys(lang), HttpStatus.OK);
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "EntityModel not found")
    @ExceptionHandler(ResourceNotFoundException.class)
    public void handleError(HttpServletRequest req, Exception exception) {
    }

}
