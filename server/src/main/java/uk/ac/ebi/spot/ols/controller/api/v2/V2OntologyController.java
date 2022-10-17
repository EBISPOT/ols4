package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.Gson;
import org.apache.solr.client.solrj.SolrServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.*;
import org.springframework.hateoas.core.Relation;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.ols.controller.api.v2.V2OntologyAssembler;
import uk.ac.ebi.spot.ols.controller.api.v2.helpers.DynamicQueryHelper;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.model.v2.V2Ontology;
import uk.ac.ebi.spot.ols.repository.Neo4jQueryHelper;
import uk.ac.ebi.spot.ols.repository.v2.V2OntologyRepository;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/api/v2/ontologies")
public class V2OntologyController implements
        ResourceProcessor<RepositoryLinksResource> {

    private Gson gson = new Gson();

    private Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    V2OntologyAssembler documentAssembler;

    @Autowired
    V2OntologyRepository ontologyRepository;

    public Logger getLog() {
        return log;
    }

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(ControllerLinkBuilder.linkTo(V2OntologyController.class).withRel("ontologies"));
        return resource;
    }

    @RequestMapping(path = "", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<PagedResources<V2Ontology>> getOntologies(
            @PageableDefault(size = 20, page = 0) Pageable pageable,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "searchFields", required = false) String searchFields,
            @RequestParam Map<String,String> searchProperties,
            PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException, IOException {

	Map<String,String> properties = new HashMap<>(Map.of("isObsolete", "false"));
	properties.putAll(searchProperties);

        Page<V2Ontology> document = ontologyRepository.find(pageable, lang, search, searchFields, DynamicQueryHelper.filterProperties(properties));

        return new ResponseEntity<>( assembler.toResource(document, documentAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<Resource<V2Ontology>> getOntology(
            @PathVariable("onto") String ontologyId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) throws ResourceNotFoundException {
        V2Ontology document = ontologyRepository.getById(ontologyId, lang);
        if (document == null) throw new ResourceNotFoundException();
        return new ResponseEntity<>( documentAssembler.toResource(document), HttpStatus.OK);
    }
}
