package uk.ac.ebi.spot.ols.controller.api.v2.cache;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import uk.ac.ebi.spot.ols.controller.api.v2.helpers.DynamicQueryHelper;
import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedAndFacetedResponse;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.v2.V2OntologyRepository;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *  This controller caches all ontologies for use in the ontologies tab.
 *  This is done to address OLS slowness as backend, solr and neo4j pods all located on different nodes. See
 *  https://overcast.blog/minimizing-inter-node-communication-in-kubernetes-dc53a8e28212.
 *
 */
@Controller
@RequestMapping("/api/v2/cache/ontologies")
public class V2OntologyCacheController {

    private Gson gson = new Gson();

    @Autowired
    V2OntologyRepository ontologyRepository;

    private static ResponseEntity allOntologiesResponse = null;

    private static final Logger logger = LoggerFactory.getLogger(V2OntologyCacheController.class);

    @RequestMapping(path = "", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getAllOntologies(
    ) throws ResourceNotFoundException, IOException {

        if (allOntologiesResponse == null) {
            logger.debug("getAllOntologies from Solr");
            Map<String, Collection<String>> properties = new HashMap<>();
            properties.put("isObsolete", List.of("false"));

            Pageable pageable = PageRequest.of(0, 1000, Sort.by("ontologyId"));
            allOntologiesResponse = new ResponseEntity<>(
                    new V2PagedAndFacetedResponse<>(
                            ontologyRepository.find(pageable, "en", null, null, null, false, DynamicQueryHelper.filterProperties(properties))
                    ),
                    HttpStatus.OK);
            logger.trace("allOntologiesResponse = {}", allOntologiesResponse);
        } else {
            logger.debug("getAllOntologies from cache");
        }
        return allOntologiesResponse;
    }
}
