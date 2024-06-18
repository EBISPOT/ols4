package uk.ac.ebi.spot.ols.controller.api.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.v1.V1GraphRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1JsTreeRepository;
import uk.ac.ebi.spot.ols.repository.v1.V1TermRepository;
import uk.ac.ebi.spot.ols.service.Neo4jClient;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;

/**
 * @author Simon Jupp
 * @date 02/11/15
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Tag(name = "Ontology Term Controller", description = "NOTE: For IRI parameters, the value must be URL encoded. " +
        "For example, the IRI http://purl.obolibrary.org/obo/DUO_0000017 should be encoded as http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017.")
@RestController
@RequestMapping("/api/ontologies")
public class V1OntologyTermController {

    private Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private V1TermRepository termRepository;

    @Autowired
    V1TermAssembler termAssembler;

    @Autowired
    V1PreferredRootTermAssembler preferredRootTermAssembler;

    @Autowired
    V1JsTreeRepository jsTreeRepository;

    @Autowired
    V1GraphRepository graphRepository;

    @Autowired
    Neo4jClient neo4jClient;


    @RequestMapping(path = "/{onto}/terms", produces = {MediaType.APPLICATION_JSON_VALUE,
        MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    private HttpEntity<PagedModel<V1Term>> termsByOntology(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @RequestParam(value = "iri", required = false)
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String iri,
            @RequestParam(value = "short_form", required = false)
            @Parameter(name = "short_form",
                    description = "This refers to the short form of the term.",
                    example = "DUO_0000017") String shortForm,
            @RequestParam(value = "obo_id", required = false)
            @Parameter(name = "obo_id",
                    description = "This refers to the OBO ID of the term.",
                    example = "DUO:0000017") String oboId,
            @RequestParam(value = "id", required = false)
            @Parameter(name = "id",
                    description = "This can be any of the above i.e. iri, short_form or obo_id.") String id,
            @RequestParam(value = "obsoletes", required = false) Boolean obsoletes,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {

        id = getIdFromMultipleOptions(iri, shortForm, oboId, id);
        ontologyId = ontologyId.toLowerCase();
        if (id == null) {
            Page<V1Term> allTerms = termRepository.findAllByOntology(ontologyId, obsoletes, lang, pageable);
            return new ResponseEntity<>(assembler.toModel(allTerms, termAssembler), HttpStatus.OK);
        }

        V1Term target = getOneById(ontologyId, id, lang);
        if (target == null) throw new ResourceNotFoundException("No resource with " + id + " in " + ontologyId);
        Page<V1Term> terms = new PageImpl<V1Term>(Arrays.asList(target));
        return new ResponseEntity<>(assembler.toModel(terms, termAssembler), HttpStatus.OK);
    }

    private V1Term getOneById(String ontologyId, String id, String lang) {

        V1Term term;

        term = termRepository.findByOntologyAndIri(ontologyId, id, lang);
        if (term == null) {
            term = termRepository.findByOntologyAndShortForm(ontologyId, id, lang);
            if (term == null) {
                term = termRepository.findByOntologyAndOboId(ontologyId, id, lang);
            }
        }
        return term;
    }

    private String getIdFromMultipleOptions (String iri, String shortForm, String oboId, String id) {
        if (id == null) {

            if (iri != null) {
                id = iri;
            }
            else if (shortForm != null) {
                id = shortForm;
            }
            else if (oboId != null) {
                id = oboId;
            }
        }
        return id;
    }

    @RequestMapping(path = "/{onto}/terms/roots", produces = {MediaType.APPLICATION_JSON_VALUE,
        MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> getRoots(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @RequestParam(value = "includeObsoletes", defaultValue = "false", required = false)
              boolean includeObsoletes,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException {
        ontologyId = ontologyId.toLowerCase();

        Page<V1Term> roots = termRepository.getRoots(ontologyId, includeObsoletes, lang, pageable);
        if (roots == null)
          throw new ResourceNotFoundException("No roots could be found for " + ontologyId );
        return new ResponseEntity<>( assembler.toModel(roots, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/terms/preferredRoots", produces = {MediaType.APPLICATION_JSON_VALUE,
        MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> getPreferredRoots(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @RequestParam(value = "includeObsoletes", defaultValue = "false", required = false)
              boolean includeObsoletes,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true)Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException {
        ontologyId = ontologyId.toLowerCase();

        Page<V1Term> preferredRoots = termRepository.getPreferredRootTerms(ontologyId,
            includeObsoletes, lang, pageable);

        if (preferredRoots == null)
          throw new ResourceNotFoundException("No preferred roots could be found for " + ontologyId);
        return new ResponseEntity<>(assembler.toModel(preferredRoots, preferredRootTermAssembler),
            HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/terms/{iri}", produces = {MediaType.APPLICATION_JSON_VALUE,
        MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<EntityModel<V1Term>> getTerm(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) throws ResourceNotFoundException {

        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        V1Term term = termRepository.findByOntologyAndIri(ontologyId, decoded, lang);
        if (term == null) throw  new ResourceNotFoundException("No term with id " + decoded +
            " in " + ontologyId);

        return new ResponseEntity<>( termAssembler.toModel(term), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/terms/{iri}/parents", produces = {MediaType.APPLICATION_JSON_VALUE,
        MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> getParents(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {

        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        Page<V1Term> parents = termRepository.getParents(ontologyId, decoded, lang, pageable);
        if (parents == null) throw  new ResourceNotFoundException();

        return new ResponseEntity<>( assembler.toModel(parents, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/terms/{iri}/hierarchicalParents", produces =
      {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> getHierarchicalParents(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {

        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        Page<V1Term> parents = termRepository.getHierarchicalParents(ontologyId, decoded, lang, pageable);
        if (parents == null)
          throw new ResourceNotFoundException("No parents could be found for " + ontologyId
              + " and " + termId);

        return new ResponseEntity<>(assembler.toModel(parents, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/terms/{iri}/hierarchicalAncestors", produces =
      {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> getHierarchicalAncestors(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {

        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        Page<V1Term> parents = termRepository.getHierarchicalAncestors(ontologyId,
            decoded, lang, pageable);
        if (parents == null)
          throw new ResourceNotFoundException("No ancestors could be found for " + ontologyId
              + " and " + termId);

        return new ResponseEntity<>(assembler.toModel(parents, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/terms/{iri}/children", produces = {MediaType.APPLICATION_JSON_VALUE,
        MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> children(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {

        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        Page<V1Term> children = termRepository.getChildren(ontologyId, decoded, lang, pageable);
        if (children == null)
          throw  new ResourceNotFoundException("No children could be found for " + ontologyId
              + " and " + termId);

        return new ResponseEntity<>( assembler.toModel(children, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/terms/{iri}/hierarchicalChildren", produces =
      {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> getHierarchicalChildren(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {

        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        Page<V1Term> children = termRepository.getHierarchicalChildren(ontologyId,
            decoded, lang, pageable);

        if (children == null)
          throw new ResourceNotFoundException("No hierarchical children could be found for "
              + ontologyId + " and " + termId);

        return new ResponseEntity<>(assembler.toModel(children, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/terms/{iri}/hierarchicalDescendants", produces =
      {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> getHierarchicalDescendants(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {

        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        Page<V1Term> children = termRepository.getHierarchicalDescendants(ontologyId,
            decoded, lang, pageable);
        if (children == null)
          throw new ResourceNotFoundException("No hierarchical descendants could be found for "
              + ontologyId + " and " + termId);

        return new ResponseEntity<>( assembler.toModel(children, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/terms/{iri}/descendants", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> descendants(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {
        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        Page<V1Term> descendants = termRepository.getDescendants(ontologyId, decoded, lang, pageable);
        if (descendants == null) throw  new ResourceNotFoundException();

        return new ResponseEntity<>( assembler.toModel(descendants, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/terms/{iri}/ancestors",
        produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE},
        method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> ancestors(@PathVariable("onto")
                                             @Parameter(name = "onto",
                                                     description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                                                     example = "duo") String ontologyId,
                                             @PathVariable("iri")
                                             @Parameter(name = "iri",
                                                     description = "The IRI of the term, this value must be double URL encoded",
                                                     example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String termId,
                                             @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                             @Parameter(hidden = true) Pageable pageable,
                                             @Parameter(hidden = true) PagedResourcesAssembler assembler) {
        ontologyId = ontologyId.toLowerCase();

        String decoded = UriUtils.decode(termId, "UTF-8");
        Page<V1Term> ancestors = termRepository.getAncestors(ontologyId, decoded, lang, pageable);
        if (ancestors == null) throw  new ResourceNotFoundException();

        return new ResponseEntity<>( assembler.toModel(ancestors, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/terms/{iri}/jstree",
        produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE},
        method = RequestMethod.GET)
    HttpEntity<String> graphJsTree(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "siblings", defaultValue = "false", required = false) boolean siblings,
            @RequestParam(value = "viewMode", defaultValue = "PreferredRoots", required = false) String viewMode){

        ontologyId = ontologyId.toLowerCase();

        try {
            String decodedTermId = UriUtils.decode(termId, "UTF-8");
            Object object= jsTreeRepository.getJsTreeForClass(decodedTermId, ontologyId, lang);
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            return new HttpEntity<String>(ow.writeValueAsString(object));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        throw new ResourceNotFoundException();
    }

    @RequestMapping(path = "/{onto}/terms/{iri}/jstree/children/{nodeid}", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<String> graphJsTreeChildren(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the property, this IRI should exist in the specified ontology by {onto} param. This value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FBFO_0000051") String termId,
            @PathVariable("nodeid")
            @Parameter(name = "nodeid",
                    description = "This is the id of the node in the jstree of ontology specified by {onto} parameter",
                    example = "aHR0cDovL3B1cmwub2JvbGlicmFyeS5vcmcvb2JvL0JGT18wMDAwMDUx") String jstreeId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) {
        ontologyId = ontologyId.toLowerCase();

        try {
            String decoded = UriUtils.decode(termId, "UTF-8");

            Object object= jsTreeRepository.getJsTreeChildrenForClass(decoded, jstreeId, ontologyId, lang);
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            return new HttpEntity<String>(ow.writeValueAsString(object));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        throw new ResourceNotFoundException();
    }

    @RequestMapping(path = "/{onto}/terms/{iri}/graph", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<String> graphJson(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @PathVariable("iri")
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) {
        ontologyId = ontologyId.toLowerCase();

        try {
            String decoded = UriUtils.decode(termId, "UTF-8");

            Object object= graphRepository.getGraphForClass(decoded, ontologyId, lang);
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            return new HttpEntity<String>(ow.writeValueAsString(object));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        throw new ResourceNotFoundException();
    }

    @RequestMapping(path = "/{onto}/terms/{iri}/{property_iri}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> related(@PathVariable("onto")
                                           @Parameter(name = "onto",
                                                   description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                                                   example = "duo") String ontologyId,
                                           @PathVariable("iri")
                                           @Parameter(name = "iri",
                                                   description = "The IRI of the term, this value must be double URL encoded.",
                                                   example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String termId,
                                           @PathVariable("property_iri")
                                           @Parameter(name = "property_iri",
                                                   description = "The IRI of the property, this must be double URL encoded.",
                                                   example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000041") String relation,
                                           @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                           @Parameter(hidden = true) Pageable pageable,
                                           @Parameter(hidden = true) PagedResourcesAssembler assembler) {
        ontologyId = ontologyId.toLowerCase();

        String decodedTerm = UriUtils.decode(termId, "UTF-8");
        String decodedRelation = UriUtils.decode(relation, "UTF-8");
        Page<V1Term> related = termRepository.getRelated(ontologyId, decodedTerm, lang, decodedRelation, pageable);

        return new ResponseEntity<>( assembler.toModel(related, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/children", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> termChildrenByOntology(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @RequestParam(value = "iri", required = false)
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String iri,
            @RequestParam(value = "short_form", required = false)
            @Parameter(name = "short_form",
                    description = "This refers to the short form of the term.",
                    example = "DUO_0000017") String shortForm,
            @RequestParam(value = "obo_id", required = false)
            @Parameter(name = "obo_id",
                    description = "This refers to the OBO ID of the term.",
                    example = "DUO:0000017") String oboId,
            @RequestParam(value = "id", required = false)
            @Parameter(name = "id",
                    description = "This can be any of the above i.e. iri, short_form or obo_id.") String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {


        id = getIdFromMultipleOptions(iri, shortForm, oboId, id);
        if (id == null) {
            return new ResponseEntity<>( assembler.toModel(new PageImpl<V1Term>(Collections.emptyList()), termAssembler), HttpStatus.OK);
        }
        V1Term target = getOneById(ontologyId, id, lang);
        ontologyId = ontologyId.toLowerCase();
        if (target == null) throw new ResourceNotFoundException("No resource with " + id + " in " + ontologyId);

        Page<V1Term>  terms = termRepository.getChildren(ontologyId, target.iri, lang, pageable);
        return new ResponseEntity<>( assembler.toModel(terms, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/descendants", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> termDescendantsByOntology(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @RequestParam(value = "iri", required = false)
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String iri,
            @RequestParam(value = "short_form", required = false)
            @Parameter(name = "short_form",
                    description = "This refers to the short form of the term.",
                    example = "DUO_0000017") String shortForm,
            @RequestParam(value = "obo_id", required = false)
            @Parameter(name = "obo_id",
                    description = "This refers to the OBO ID of the term.",
                    example = "DUO:0000017") String oboId,
            @RequestParam(value = "id", required = false)
            @Parameter(name = "id",
                    description = "This can be any of the above i.e. iri, short_form or obo_id.") String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {


        id = getIdFromMultipleOptions(iri, shortForm, oboId, id);
        if (id == null) {
            return new ResponseEntity<>( assembler.toModel(new PageImpl<V1Term>(Collections.emptyList()), termAssembler), HttpStatus.OK);
        }
        V1Term target = getOneById(ontologyId, id, lang);
        ontologyId = ontologyId.toLowerCase();
        if (target == null) throw new ResourceNotFoundException("No resource with " + id + " in " + ontologyId);

        Page<V1Term>  terms = termRepository.getDescendants(ontologyId, target.iri, lang, pageable);
        return new ResponseEntity<>( assembler.toModel(terms, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/hierarchicalChildren", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> termHierarchicalChildrenByOntology(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @RequestParam(value = "iri", required = false)
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String iri,
            @RequestParam(value = "short_form", required = false)
            @Parameter(name = "short_form",
                    description = "This refers to the short form of the term.",
                    example = "DUO_0000017") String shortForm,
            @RequestParam(value = "obo_id", required = false)
            @Parameter(name = "obo_id",
                    description = "This refers to the OBO ID of the term.",
                    example = "DUO:0000017") String oboId,
            @RequestParam(value = "id", required = false)
            @Parameter(name = "id",
                    description = "This can be any of the above i.e. iri, short_form or obo_id.") String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {


        id = getIdFromMultipleOptions(iri, shortForm, oboId, id);
        if (id == null) {
            return new ResponseEntity<>( assembler.toModel(new PageImpl<V1Term>(Collections.emptyList()), termAssembler), HttpStatus.OK);
        }
        V1Term target = getOneById(ontologyId, id, lang);
        ontologyId = ontologyId.toLowerCase();
        if (target == null) throw new ResourceNotFoundException("No resource with " + id + " in " + ontologyId);

        Page<V1Term>  terms = termRepository.getHierarchicalChildren(ontologyId, target.iri, lang, pageable);
        return new ResponseEntity<>( assembler.toModel(terms, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/hierarchicalDescendants", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> termHierarchicalDescendantsByOntology(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @RequestParam(value = "iri", required = false)
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String iri,
            @RequestParam(value = "short_form", required = false)
            @Parameter(name = "short_form",
                    description = "This refers to the short form of the term.",
                    example = "DUO_0000017") String shortForm,
            @RequestParam(value = "obo_id", required = false)
            @Parameter(name = "obo_id",
                    description = "This refers to the OBO ID of the term.",
                    example = "DUO:0000017") String oboId,
            @RequestParam(value = "id", required = false)
            @Parameter(name = "id",
                    description = "This can be any of the above i.e. iri, short_form or obo_id.") String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {


        id = getIdFromMultipleOptions(iri, shortForm, oboId, id);
        if (id == null) {
            return new ResponseEntity<>( assembler.toModel(new PageImpl<V1Term>(Collections.emptyList()), termAssembler), HttpStatus.OK);
        }
        V1Term target = getOneById(ontologyId, id, lang);
        ontologyId = ontologyId.toLowerCase();
        if (target == null) throw new ResourceNotFoundException("No resource with " + id + " in " + ontologyId);

        Page<V1Term>  terms = termRepository.getHierarchicalDescendants(ontologyId, target.iri, lang, pageable);
        return new ResponseEntity<>( assembler.toModel(terms, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/parents", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> termParentsByOntology(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @RequestParam(value = "iri", required = false)
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String iri,
            @RequestParam(value = "short_form", required = false)
            @Parameter(name = "short_form",
                    description = "This refers to the short form of the term.",
                    example = "DUO_0000017") String shortForm,
            @RequestParam(value = "obo_id", required = false)
            @Parameter(name = "obo_id",
                    description = "This refers to the OBO ID of the term.",
                    example = "DUO:0000017") String oboId,
            @RequestParam(value = "id", required = false)
            @Parameter(name = "id",
                    description = "This can be any of the above i.e. iri, short_form or obo_id.") String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {


        id = getIdFromMultipleOptions(iri, shortForm, oboId, id);
        if (id == null) {
            return new ResponseEntity<>( assembler.toModel(new PageImpl<V1Term>(Collections.emptyList()), termAssembler), HttpStatus.OK);
        }
        V1Term target = getOneById(ontologyId, id, lang);
        ontologyId = ontologyId.toLowerCase();
        if (target == null) throw new ResourceNotFoundException("No resource with " + id + " in " + ontologyId);

        Page<V1Term>  terms = termRepository.getParents(ontologyId, target.iri, lang, pageable);
        return new ResponseEntity<>( assembler.toModel(terms, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/ancestors", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> termAncestorsByOntology(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @RequestParam(value = "iri", required = false)
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String iri,
            @RequestParam(value = "short_form", required = false)
            @Parameter(name = "short_form",
                    description = "This refers to the short form of the term.",
                    example = "DUO_0000017") String shortForm,
            @RequestParam(value = "obo_id", required = false)
            @Parameter(name = "obo_id",
                    description = "This refers to the OBO ID of the term.",
                    example = "DUO:0000017") String oboId,
            @RequestParam(value = "id", required = false)
            @Parameter(name = "id",
                    description = "This can be any of the above i.e. iri, short_form or obo_id.") String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {


        id = getIdFromMultipleOptions(iri, shortForm, oboId, id);
        if (id == null) {
            return new ResponseEntity<>( assembler.toModel(new PageImpl<V1Term>(Collections.emptyList()), termAssembler), HttpStatus.OK);
        }
        V1Term target = getOneById(ontologyId, id, lang);
        ontologyId = ontologyId.toLowerCase();
        if (target == null) throw new ResourceNotFoundException("No resource with " + id + " in " + ontologyId);

        Page<V1Term>  terms = termRepository.getAncestors(ontologyId, target.iri, lang, pageable);
        return new ResponseEntity<>( assembler.toModel(terms, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/hierarchicalAncestors", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedModel<V1Term>> termHierarchicalAncestorsByOntology(
            @PathVariable("onto")
            @Parameter(name = "onto",
                    description = "The ID of the ontology. For example for Data Use Ontology, the ID is duo.",
                    example = "duo") String ontologyId,
            @RequestParam(value = "iri", required = false)
            @Parameter(name = "iri",
                    description = "The IRI of the term, this value must be double URL encoded",
                    example = "http%3A%2F%2Fpurl.obolibrary.org%2Fobo%2FDUO_0000017") String iri,
            @RequestParam(value = "short_form", required = false)
            @Parameter(name = "short_form",
                    description = "This refers to the short form of the term.",
                    example = "DUO_0000017") String shortForm,
            @RequestParam(value = "obo_id", required = false)
            @Parameter(name = "obo_id",
                    description = "This refers to the OBO ID of the term.",
                    example = "DUO:0000017") String oboId,
            @RequestParam(value = "id", required = false)
            @Parameter(name = "id",
                    description = "This can be any of the above i.e. iri, short_form or obo_id.") String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @Parameter(hidden = true) Pageable pageable,
            @Parameter(hidden = true) PagedResourcesAssembler assembler) {


        id = getIdFromMultipleOptions(iri, shortForm, oboId, id);
        if (id == null) {
            return new ResponseEntity<>( assembler.toModel(new PageImpl<V1Term>(Collections.emptyList()), termAssembler), HttpStatus.OK);
        }
        V1Term target = getOneById(ontologyId, id, lang);
        ontologyId = ontologyId.toLowerCase();
        if (target == null) throw new ResourceNotFoundException("No resource with " + id + " in " + ontologyId);

        Page<V1Term>  terms = termRepository.getHierarchicalAncestors(ontologyId, target.iri, lang, pageable);
        return new ResponseEntity<>( assembler.toModel(terms, termAssembler), HttpStatus.OK);
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "EntityModel not found")
    @ExceptionHandler(ResourceNotFoundException.class)
    public void handleError(HttpServletRequest req, Exception exception) {

    }

}
