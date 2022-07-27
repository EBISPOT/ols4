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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.controller.api.v2.helpers.DynamicQueryHelper;
import uk.ac.ebi.spot.ols.model.v2.V2Term;
import uk.ac.ebi.spot.ols.repository.v2.V2TermRepository;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

@Controller
@RequestMapping("/api/v2")
public class V2TermController implements
        ResourceProcessor<RepositoryLinksResource> {

    private Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    V2TermAssembler documentAssembler;

    @Autowired
    V2TermRepository termRepository;

    public Logger getLog() {
        return log;
    }

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(ControllerLinkBuilder.linkTo(V2TermController.class).withRel("terms"));
        return resource;
    }

    @RequestMapping(path = "/terms", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<PagedResources<V2Term>> getTerms(
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "searchFields", required = false) String searchFields,
            @RequestParam Map<String,String> properties,
            PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException, IOException {

        Page<V2Term> document = termRepository.find(pageable, lang, search, searchFields, DynamicQueryHelper.filterProperties(properties));

        return new ResponseEntity<>( assembler.toResource(document, documentAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/terms", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<PagedResources<V2Term>> getTerms(
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @PathVariable("onto") @NotNull String ontologyId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "searchFields", required = false) String searchFields,
            @RequestParam Map<String,String> properties,
            PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException, IOException {

        Page<V2Term> document = termRepository.findByOntologyId(ontologyId, pageable, lang, search, searchFields, DynamicQueryHelper.filterProperties(properties));

        return new ResponseEntity<>( assembler.toResource(document, documentAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/terms/{term}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<Resource<V2Term>> getTerm(
            @PathVariable("onto") String ontologyId,
            @PathVariable("term") String uri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) throws ResourceNotFoundException {

        try {
            uri = UriUtils.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new ResourceNotFoundException();
        }

        V2Term document = termRepository.getByOntologyIdAndUri(ontologyId, uri, lang);
        if (document == null) throw new ResourceNotFoundException();
        return new ResponseEntity<>( documentAssembler.toResource(document), HttpStatus.OK);
    }
}


