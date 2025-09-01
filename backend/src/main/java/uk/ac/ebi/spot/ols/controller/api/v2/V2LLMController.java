package uk.ac.ebi.spot.ols.controller.api.v2;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import com.google.gson.Gson;

import uk.ac.ebi.spot.ols.controller.api.v2.helpers.DynamicQueryHelper;
import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedAndFacetedResponse;
import uk.ac.ebi.spot.ols.controller.api.v2.responses.V2PagedResponse;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.ClassRepository;
import uk.ac.ebi.spot.ols.repository.PropertyRepository;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

@Tag(
        name = "V2 LLM Controller"
)
@RestController
@RequestMapping("/api/v2")
public class V2LLMController {

    Gson gson = new Gson();

    @Autowired
    ClassRepository classRepository;

    @Autowired
    PropertyRepository propertyRepository;

    @RequestMapping(path = "/classes/llm_embedding", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.POST)
    public HttpEntity<V2PagedResponse<V2Entity>> searchClassesByVector(
                @RequestBody List<Double> vector,
                @PageableDefault(size = 20, page = 0)
                @Parameter(name = "pageable",
                        description = "Specify the size of the result you want to get in the output",
                        example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
                @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                JsonTransformOptions outputOpts
        ) throws ResourceNotFoundException, IOException {
                return new ResponseEntity<>(
                        new V2PagedResponse<V2Entity>(
                        classRepository.searchByVector(vector, pageable, lang, outputOpts).map(V2Entity::new)
                        ),
                        HttpStatus.OK
                );
        }

    @RequestMapping(path = "/ontologies/{onto}/classes/{class}/llm_similar", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getSimilarByOntology(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
                    description = "Specify the size of the result you want to get in the output",
                    example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "Ontology Id to get the information about.",
                    example = "efo") String ontologyId,
            @PathVariable("class")
            @Parameter(name = "class",
                    description = "The IRI of the class, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_1000967") String iri,
        @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
        JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        return new ResponseEntity<>(
                new V2PagedResponse<V2Entity>(
                        classRepository.getSimilarByOntologyId(ontologyId, pageable, iri, false, lang, outputOpts).map(V2Entity::new)
                ),
                HttpStatus.OK
        );
    }

    @RequestMapping(path = "/ontologies/{onto}/classes/{class}/llm_embedding", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<String> getEmbeddingByOntology(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
                    description = "Specify the size of the result you want to get in the output",
                    example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "Ontology Id to get the information about.",
                    example = "efo") String ontologyId,
            @PathVariable("class")
            @Parameter(name = "class",
                    description = "The IRI of the class, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_1000967") String iri
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        return new ResponseEntity<>(
                gson.toJson( classRepository.getEmbeddingVectorByOntologyId(ontologyId, iri) ),
                HttpStatus.OK
        );
    }

    @RequestMapping(path = "/ontologies/{onto}/classes/{class}/llm_similarity/{otherclass}", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<String> getSimilarityByOntology(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
                    description = "Specify the size of the result you want to get in the output",
                    example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "Ontology Id to get the information about.",
                    example = "efo") String ontologyId,
            @PathVariable("class")
            @Parameter(name = "class",
                    description = "The IRI of the class, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_1000967") String iri,
            @PathVariable("otherclass")
            @Parameter(name = "otherclass",
                    description = "The IRI of the other class, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_1000967") String iri2
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");
        iri2 = UriUtils.decode(iri2, "UTF-8");

        return new ResponseEntity<>(
                Double.toString( classRepository.getSimilarityByOntologyId(ontologyId, iri, iri2) ),
                HttpStatus.OK
        );
    }

    @RequestMapping(path = "/ontologies/{onto}/properties/{property}/llm_similar", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getSimilarPropertiesByOntology(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
                    description = "Specify the size of the result you want to get in the output",
                    example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "Ontology Id to get the information about.",
                    example = "efo") String ontologyId,
            @PathVariable("property")
            @Parameter(name = "property",
                    description = "The IRI of the property, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_0000742") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            JsonTransformOptions outputOpts
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        return new ResponseEntity<>(
                new V2PagedResponse<V2Entity>(
                        propertyRepository.getSimilarByOntologyId(ontologyId, pageable, iri, lang, outputOpts)
                        .map(V2Entity::new)
                ),
                HttpStatus.OK
        );
    }
}

