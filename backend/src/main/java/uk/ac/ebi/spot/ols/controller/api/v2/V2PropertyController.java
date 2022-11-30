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
import org.springframework.hateoas.server.RepresentationModelProcessor;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.controller.api.v2.helpers.DynamicQueryHelper;
import uk.ac.ebi.spot.ols.model.v2.V2Class;
import uk.ac.ebi.spot.ols.model.v2.V2Property;
import uk.ac.ebi.spot.ols.repository.v2.V2PropertyRepository;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/api/v2")
public class V2PropertyController implements
        RepresentationModelProcessor<RepositoryLinksResource> {

    private Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    V2PropertyAssembler documentAssembler;

    @Autowired
    V2PropertyRepository propertyRepository;

    public Logger getLog() {
        return log;
    }

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(WebMvcLinkBuilder.linkTo(V2PropertyController.class).withRel("properties"));
        return resource;
    }

    @RequestMapping(path = "/properties", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<PagedModel<V2Property>> getProperties(
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "searchFields", required = false) String searchFields,
            @RequestParam(value = "boostFields", required = false) String boostFields,
            @RequestParam Map<String,String> searchProperties,
            PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException, IOException {

	Map<String,String> properties = new HashMap<>(Map.of("isObsolete", "false"));
	properties.putAll(searchProperties);

        Page<V2Property> document = propertyRepository.find(pageable, lang, search, searchFields, boostFields, DynamicQueryHelper.filterProperties(properties));

        return new ResponseEntity<>( assembler.toModel(document, documentAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/properties", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<PagedModel<V2Property>> getProperties(
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @PathVariable("onto") @NotNull String ontologyId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "searchFields", required = false) String searchFields,
            @RequestParam(value = "boostFields", required = false) String boostFields,
            @RequestParam Map<String,String> searchProperties,
            PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException, IOException {

	Map<String,String> properties = new HashMap<>(Map.of("isObsolete", "false"));
	properties.putAll(searchProperties);

        Page<V2Property> document = propertyRepository.findByOntologyId(ontologyId, pageable, lang, search, searchFields, boostFields, DynamicQueryHelper.filterProperties(properties));

        return new ResponseEntity<>( assembler.toModel(document, documentAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/properties/{property}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<EntityModel<V2Property>> getProperty(
            @PathVariable("onto") String ontologyId,
            @PathVariable("property") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        V2Property document = propertyRepository.getByOntologyIdAndIri(ontologyId, iri, lang);
        if (document == null) throw new ResourceNotFoundException();
        return new ResponseEntity<>( documentAssembler.toModel(document), HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/properties/{property}/children", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<PagedModel<V2Property>> getChildrenByOntology(
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @PathVariable("onto") String ontologyId,
            @PathVariable("property") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        Page<V2Property> document = propertyRepository.getChildrenByOntologyId(ontologyId, pageable, iri, lang);
        return new ResponseEntity<>( assembler.toModel(document, documentAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/properties/{property}/ancestors", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<PagedModel<V2Property>> getAncestorsByOntology(
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @PathVariable("onto") String ontologyId,
            @PathVariable("property") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        Page<V2Property> document = propertyRepository.getAncestorsByOntologyId(ontologyId, pageable, iri, lang);
        return new ResponseEntity<>( assembler.toModel(document, documentAssembler), HttpStatus.OK);
    }

}



