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
import uk.ac.ebi.spot.ols.repository.v2.V2ClassRepository;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

@Tag(
        name = "V2 Class Controller",
        description = "This endpoint provides access to class information. \n\n" +
                "**Additionally**, if you want to retrieve detailed information about class synonyms then you need to parse the response using the `synonymProperty` field. \n\n" +
                "For each element of the synonymProperty array you can then find the detailed information about each synonym type within the response as each synonym type is a separate object. \n\n" +
                "For further details about the meaning of different synonym types please refer to this link: [Synonym Documentation](https://ontology-development-kit.readthedocs.io/en/latest/Synonyms.html). \n\n" +
                "### Example \n\n" +
                "For example, for `Lactose Intolerance` class we have following `synonymProperty`: \n\n" +
                "```json\n" +
                "\"synonymProperty\": [ \n" +
                "  \"http://www.geneontology.org/formats/oboInOwl#hasExactSynonym\", \n" +
                "  \"http://www.geneontology.org/formats/oboInOwl#hasRelatedSynonym\" \n" +
                "]\n" +
                "``` \n\n" +
                "and then for each of the elements such as ExactSynonym we have following separate object within the same json response: \n\n" +
                "```json\n" +
                "\"http://www.geneontology.org/formats/oboInOwl#hasExactSynonym\" : [ {\n" +
                "    \"type\" : [ \"reification\" ],\n" +
                "    \"value\" : \"LM - lactose malabsorption\",\n" +
                "    \"axioms\" : [ {\n" +
                "      \"http://www.geneontology.org/formats/oboInOwl#hasDbXref\" : \"DOID:10604\"\n" +
                "    } ]\n" +
                "  }, {\n" +
                "    \"type\" : [ \"reification\" ],\n" +
                "    \"value\" : \"lactose intolerance\",\n" +
                "    \"axioms\" : [ {\n" +
                "      \"http://www.w3.org/2000/01/rdf-schema#comment\" : \"preferred label from MONDO\"\n" +
                "    }, {\n" +
                "      \"http://www.geneontology.org/formats/oboInOwl#hasDbXref\" : [ \"DOID:10604\", \"MONDO:ambiguous\", \"NCIT:C3154\", \"icd11.foundation:1026224967\" ]\n" +
                "    } ]\n" +
                "  }, {\n" +
                "    \"type\" : [ \"reification\" ],\n" +
                "    \"value\" : \"lactose intolerance (disease)\",\n" +
                "    \"axioms\" : [ {\n" +
                "      \"http://www.geneontology.org/formats/oboInOwl#hasDbXref\" : [ \"MONDO:0009116\", \"https://orcid.org/0000-0002-6601-2165\" ]\n" +
                "    } ]\n" +
                "  } ]\n" +
                "```"
)
@RestController
@RequestMapping("/api/v2")
public class V2ClassController {

    Gson gson = new Gson();

    @Autowired
    V2ClassRepository classRepository;

    @RequestMapping(path = "/classes", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getClasses(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
                    description = "Specify the size of the result you want to get in the output",
                    example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "search", required = false)
            @Parameter(name="search",
                    description = "This parameter specify the search query text.",
                    example = "liver disease") String search,
            @RequestParam(value = "searchFields", required = false)
            @Parameter(name = "searchFields",
                    description = "This parameter is a white space separated list of fields to search in. " +
                            "The fields are weighted equally. The fields are defined in the schema. " +
                            "The default fields are label and definition. " +
                            "The fields weights can be boosted by appending a caret ^ and a positive integer to the field name. " +
                            "For example, label^3 synonyms^2 description^1 logical_definition^1",
                    example = "label^100 description") String searchFields,
            @RequestParam(value = "boostFields", required = false)
            @Parameter(name = "boostFields",
                    description = "This parameter is a white space separated list of fields appended with a caret to boost in search. " +
                            "The default fields are type, is_defining_ontology, label, curie, shortForm and synonym . " +
                            "The fields weights can be boosted by appending a caret ^ and a positive integer to the field name. ",
                    example = "label^100 curie^50") String boostFields,
            @RequestParam(value = "exactMatch", required = false, defaultValue = "false")
            @Parameter(name = "exactMatch",
                    description = "As the name suggests its a boolean parameter to specify if search should be exact match or not." +
                            "The default value is false") boolean exactMatch,
            @RequestParam(value = "includeObsoleteEntities", required = false, defaultValue = "false")
            @Parameter(name = "includeObsoleteEntities",
                    description = "A boolean parameter to specify if obsolete entities should be included or not. Default value is false.") boolean includeObsoleteEntities,
            @RequestParam
            @Parameter(name="searchProperties",
                    description = "Specify any other search field here which are not specified by searchFields or boostFields.",
                    example = "{}") MultiValueMap<String,String> searchProperties
    ) throws ResourceNotFoundException, IOException {

	Map<String, Collection<String>> properties = new HashMap<>();
    if(!includeObsoleteEntities)
        properties.put(IS_OBSOLETE.getText(), List.of("false"));
	properties.putAll(searchProperties);

        return new ResponseEntity<>(
                new V2PagedAndFacetedResponse(
                    classRepository.find(pageable, lang, search, searchFields, boostFields, exactMatch, DynamicQueryHelper.filterProperties(properties))
                ),
                HttpStatus.OK
        );
    }

    @RequestMapping(path = "/ontologies/{onto}/classes", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2PagedAndFacetedResponse<V2Entity>> getClasses(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
                    description = "Specify the size of the result you want to get in the output",
                    example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @PathVariable("onto") @NotNull
            @Parameter(name = "onto",
                    description = "Ontology Id to get the information about.",
                    example = "efo") String ontologyId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "search", required = false)
            @Parameter(name="search",
                    description = "This parameter specify the search query text.",
                    example = "liver disease") String search,
            @RequestParam(value = "searchFields", required = false)
            @Parameter(name = "searchFields",
                    description = "This parameter is a white space separated list of fields to search in. " +
                            "The fields are weighted equally. The fields are defined in the schema. " +
                            "The default fields are label and definition. " +
                            "The fields weights can be boosted by appending a caret ^ and a positive integer to the field name. " +
                            "For example, label^3 synonyms^2 description^1 logical_definition^1",
                    example = "label^100 description") String searchFields,
            @RequestParam(value = "boostFields", required = false)
            @Parameter(name = "boostFields",
                    description = "This parameter is a white space separated list of fields appended with a caret to boost in search. " +
                            "The default fields are type, is_defining_ontology, label, curie, shortForm and synonym . " +
                            "The fields weights can be boosted by appending a caret ^ and a positive integer to the field name. ",
                    example = "label^100 curie^50") String boostFields,
            @RequestParam(value = "exactMatch", required = false, defaultValue = "false")
            @Parameter(name = "exactMatch",
                    description = "As the name suggests its a boolean parameter to specify if search should be exact match or not." +
                            "The default value is false") boolean exactMatch,
            @RequestParam(value = "includeObsoleteEntities", required = false, defaultValue = "false")
            @Parameter(name = "includeObsoleteEntities",
                    description = "A boolean parameter to specify if obsolete entities should be included or not. Default value is false.") boolean includeObsoleteEntities,
            @RequestParam
            @Parameter(name="searchProperties",
                    description = "Specify any other search field here which are not specified by searchFields or boostFields.",
                    example = "{}") MultiValueMap<String,String> searchProperties
    ) throws ResourceNotFoundException, IOException {

        Map<String,Collection<String>> properties = new HashMap<>();
        if(!includeObsoleteEntities)
            properties.put(IS_OBSOLETE.getText(), List.of("false"));
        properties.putAll(searchProperties);

        return new ResponseEntity<>(
                new V2PagedAndFacetedResponse(
                    classRepository.findByOntologyId(ontologyId, pageable, lang, search, searchFields, boostFields, exactMatch,  DynamicQueryHelper.filterProperties(properties))
                ),
                HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/classes/{class}", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2Entity> getClass(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "Ontology Id to get the information about.",
                    example = "efo") String ontologyId,
            @PathVariable("class")
            @Parameter(name = "class",
                    description = "The IRI of the class, this value must be double URL encoded",
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_1000967") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        V2Entity entity = classRepository.getByOntologyIdAndIri(ontologyId, iri, lang);
        if (entity == null) throw new ResourceNotFoundException();
        return new ResponseEntity<>( entity, HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/classes/{class}/children", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getChildrenByOntology(
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
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_0000001") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "includeObsoleteEntities", required = false, defaultValue = "false")
            @Parameter(name = "includeObsoleteEntities",
                    description = "A boolean parameter to specify if obsolete entities should be included or not. Default value is false.") boolean includeObsoleteEntities
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        return new ResponseEntity<>(
                new V2PagedResponse<>(
                    classRepository.getChildrenByOntologyId(ontologyId, pageable, iri, includeObsoleteEntities, lang)
                ),
                HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/classes/{class}/ancestors", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getAncestorsByOntology(
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
            @RequestParam(value = "includeObsoleteEntities", required = false, defaultValue = "false")
            @Parameter(name = "includeObsoleteEntities",
                    description = "A boolean parameter to specify if obsolete entities should be included or not. Default value is false.") boolean includeObsoleteEntities
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        return new ResponseEntity<>(
                new V2PagedResponse<>(
                    classRepository.getAncestorsByOntologyId(ontologyId, pageable, iri, includeObsoleteEntities, lang)
                ),
                HttpStatus.OK
        );
    }

    @RequestMapping(path = "/ontologies/{onto}/classes/{class}/hierarchicalChildren", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getHierarchicalChildrenByOntology(
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
                    example = "http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_0000001") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "includeObsoleteEntities", required = false, defaultValue = "false")
            @Parameter(name = "includeObsoleteEntities",
                    description = "A boolean parameter to specify if obsolete entities should be included or not. Default value is false.") boolean includeObsoleteEntities
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        return new ResponseEntity<>(
                new V2PagedResponse<>(
                        classRepository.getHierarchicalChildrenByOntologyId(ontologyId, pageable, iri, includeObsoleteEntities, lang)
                ),
                HttpStatus.OK);
    }

    @RequestMapping(path = "/ontologies/{onto}/classes/{class}/hierarchicalAncestors", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getHierarchicalAncestorsByOntology(
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
            @RequestParam(value = "includeObsoleteEntities", required = false, defaultValue = "false")
            @Parameter(name = "includeObsoleteEntities",
                    description = "A boolean parameter to specify if obsolete entities should be included or not. Default value is false.") boolean includeObsoleteEntities
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        return new ResponseEntity<>(
                new V2PagedResponse<>(
                        classRepository.getHierarchicalAncestorsByOntologyId(ontologyId, pageable, iri, includeObsoleteEntities, lang)
                ),
                HttpStatus.OK
        );
    }

    // The ancestors of individuals are classes. So, the /ancestors endpoint is part of the Class controller.
    //
    @RequestMapping(path = "/ontologies/{onto}/individuals/{individual}/ancestors", produces = {MediaType.APPLICATION_JSON_VALUE }, method = RequestMethod.GET)
    public HttpEntity<V2PagedResponse<V2Entity>> getIndividualAncestorsByOntology(
            @PageableDefault(size = 20, page = 0)
            @Parameter(name = "pageable",
                    description = "Specify the size of the result you want to get in the output",
                    example = "{\"page\": 0,\"size\": 20}") Pageable pageable,
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "Ontology Id to get the information about.",
                    example = "afo") String ontologyId,
            @PathVariable("individual")
            @Parameter(name = "individual",
                    description = "The IRI of the individual, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.allotrope.org%2Fontologies%2Fprocess%23AFP_0003781") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "includeObsoleteEntities", required = false, defaultValue = "false")
            @Parameter(name = "includeObsoleteEntities",
                    description = "A boolean parameter to specify if obsolete entities should be included or not. Default value is false.") boolean includeObsoleteEntities
    ) throws ResourceNotFoundException {

        iri = UriUtils.decode(iri, "UTF-8");

        return new ResponseEntity<>(
                new V2PagedResponse<>(
                    classRepository.getIndividualAncestorsByOntologyId(ontologyId, pageable, iri, includeObsoleteEntities, lang)
                ),
                HttpStatus.OK);

    }
}

