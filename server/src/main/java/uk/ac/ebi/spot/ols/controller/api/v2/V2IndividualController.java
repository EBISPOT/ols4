package uk.ac.ebi.spot.ols.controller.api.v2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.*;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.ols.model.v2.V2Individual;
import uk.ac.ebi.spot.ols.repository.v2.V2IndividualRepository;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.IOException;
import java.util.Map;

@Controller
@RequestMapping("/api/v2")
public class V2IndividualController implements
        ResourceProcessor<RepositoryLinksResource> {

    private Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    V2IndividualAssembler documentAssembler;

    @Autowired
    V2IndividualRepository individualRepository;

    public Logger getLog() {
        return log;
    }

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(ControllerLinkBuilder.linkTo(V2IndividualController.class).withRel("individuals"));
        return resource;
    }

    @RequestMapping(path = "/individuals", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<PagedResources<V2Individual>> getIndividuals(
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "searchFields", required = false) String searchFields,
            PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException, IOException {

        Page<V2Individual> document = individualRepository.find(pageable, lang, search, searchFields, Map.of());

        return new ResponseEntity<>( assembler.toResource(document, documentAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/individuals", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<PagedResources<V2Individual>> getIndividuals(
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @PathVariable("onto") @NotNull String ontologyId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "searchFields", required = false) String searchFields,
            PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException, IOException {

        Page<V2Individual> document = individualRepository.findByOntologyId(ontologyId, pageable, lang, search, searchFields, Map.of());

        return new ResponseEntity<>( assembler.toResource(document, documentAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/individuals/{individual}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<Resource<V2Individual>> getIndividual(
            @PathVariable("onto") String ontologyId,
            @PathVariable("individual") String uri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) throws ResourceNotFoundException {

        V2Individual document = individualRepository.getByOntologyIdAndUri(ontologyId, uri, lang);
        if (document == null) throw new ResourceNotFoundException();
        return new ResponseEntity<>( documentAssembler.toResource(document), HttpStatus.OK);
    }
}



