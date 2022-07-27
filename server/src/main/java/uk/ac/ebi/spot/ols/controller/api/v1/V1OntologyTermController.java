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
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.v1.V1TermRepository;
import uk.ac.ebi.spot.ols.service.ClassJsTreeBuilder;
import uk.ac.ebi.spot.ols.service.ViewMode;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;

/**
 * @author Simon Jupp
 * @date 02/11/15
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@RestController
@RequestMapping("/api/ontologies")
public class V1OntologyTermController {

    @Autowired
    private V1TermRepository termRepository;

    @Autowired
    V1TermAssembler termAssembler;

    @Autowired
    V1PreferredRootTermAssembler preferredRootTermAssembler;
    
    @Autowired
    ClassJsTreeBuilder jsTreeBuilder;


    @RequestMapping(path = "/{onto}/terms", produces = {MediaType.APPLICATION_JSON_VALUE, 
        MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    private HttpEntity<PagedResources<V1Term>> termsByOntology(
            @PathVariable("onto") String ontologyId,
            @RequestParam(value = "iri", required = false) String iri,
            @RequestParam(value = "short_form", required = false) String shortForm,
            @RequestParam(value = "obo_id", required = false) String oboId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {

        Page<V1Term> terms = null;

        ontologyId = ontologyId.toLowerCase();
        if (iri != null) {
            V1Term term = termRepository.findByOntologyAndIri(ontologyId, iri, lang);
            if (term == null) 
              throw new ResourceNotFoundException("No resource with " + oboId + " in " + ontologyId);
            terms =  new PageImpl<V1Term>(Arrays.asList(term));
        }
        else if (shortForm != null) {
            V1Term term = termRepository.findByOntologyAndShortForm(ontologyId, shortForm, lang);
            if (term == null) 
              throw new ResourceNotFoundException("No resource with " + oboId + " in " + ontologyId);
            terms =  new PageImpl<V1Term>(Arrays.asList(term));
        }
        else if (oboId != null) {
            V1Term term = termRepository.findByOntologyAndOboId(ontologyId, oboId, lang);
            if (term == null) 
              throw new ResourceNotFoundException("No resource with " + oboId + " in " + ontologyId);
            terms =  new PageImpl<V1Term>(Arrays.asList(term));
        }
        else {
            terms = termRepository.findAllByOntology(ontologyId, lang, pageable);
            if (terms == null) throw new ResourceNotFoundException("Ontology not found");
        }

        return new ResponseEntity<>( assembler.toResource(terms, termAssembler), HttpStatus.OK);
    }

    private V1Term getOneById(String ontologyId, String id, String lang) {

        V1Term term = null;

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
    HttpEntity<PagedResources<V1Term>> getRoots(
            @PathVariable("onto") String ontologyId,
            @RequestParam(value = "includeObsoletes", defaultValue = "false", required = false) 
              boolean includeObsoletes,
            Pageable pageable,
            PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException {
        ontologyId = ontologyId.toLowerCase();

        Page<V1Term> roots = termRepository.getRoots(ontologyId, includeObsoletes, pageable);
        if (roots == null) 
          throw new ResourceNotFoundException("No roots could be found for " + ontologyId );
        return new ResponseEntity<>( assembler.toResource(roots, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/terms/preferredRoots", produces = {MediaType.APPLICATION_JSON_VALUE, 
        MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> getPreferredRoots(
            @PathVariable("onto") String ontologyId,
            @RequestParam(value = "includeObsoletes", defaultValue = "false", required = false) 
              boolean includeObsoletes,
            Pageable pageable,
            PagedResourcesAssembler assembler
    ) throws ResourceNotFoundException {
        ontologyId = ontologyId.toLowerCase();

        Page<V1Term> preferredRoots = termRepository.getPreferredRootTerms(ontologyId,
            includeObsoletes, pageable);
        
        if (preferredRoots == null) 
          throw new ResourceNotFoundException("No preferred roots could be found for " + ontologyId);
        return new ResponseEntity<>(assembler.toResource(preferredRoots, preferredRootTermAssembler), 
            HttpStatus.OK);
    }    
    
    @RequestMapping(path = "/{onto}/terms/{id}", produces = {MediaType.APPLICATION_JSON_VALUE, 
        MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<Resource<V1Term>> getTerm(@PathVariable("onto") String ontologyId,
                                         @PathVariable("id") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    )
            throws ResourceNotFoundException {
      
        ontologyId = ontologyId.toLowerCase();

        try {
            String decoded = UriUtils.decode(termId, "UTF-8");
            V1Term term = termRepository.findByOntologyAndIri(ontologyId, decoded, lang);
            if (term == null) throw  new ResourceNotFoundException("No term with id " + decoded + 
                " in " + ontologyId);

            return new ResponseEntity<>( termAssembler.toResource(term), HttpStatus.OK);
        } catch (UnsupportedEncodingException e) {
            throw new ResourceNotFoundException();
        }
    }

    @RequestMapping(path = "/{onto}/terms/{id}/parents", produces = {MediaType.APPLICATION_JSON_VALUE, 
        MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> getParents(@PathVariable("onto") String ontologyId,
                                                  @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                                  @PathVariable("id") String termId, Pageable pageable, PagedResourcesAssembler assembler) {
      
        ontologyId = ontologyId.toLowerCase();

        try {
            String decoded = UriUtils.decode(termId, "UTF-8");
            Page<V1Term> parents = termRepository.getParents(ontologyId, decoded, lang, pageable);
            if (parents == null) throw  new ResourceNotFoundException();

            return new ResponseEntity<>( assembler.toResource(parents, termAssembler), HttpStatus.OK);
        }
        catch (UnsupportedEncodingException e) {
            throw new ResourceNotFoundException();
        }
    }

    @RequestMapping(path = "/{onto}/terms/{id}/hierarchicalParents", produces = 
      {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> getHierarchicalParents(@PathVariable("onto") String ontologyId,
                                                              @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                                              @PathVariable("id") String termId, Pageable pageable, PagedResourcesAssembler assembler) {
      
        ontologyId = ontologyId.toLowerCase();

        try {
            String decoded = UriUtils.decode(termId, "UTF-8");
            Page<V1Term> parents = termRepository.getHierarchicalParents(ontologyId, decoded, lang, pageable);
            if (parents == null) 
              throw new ResourceNotFoundException("No parents could be found for " + ontologyId
                  + " and " + termId);

            return new ResponseEntity<>(assembler.toResource(parents, termAssembler), HttpStatus.OK);
        }
        catch (UnsupportedEncodingException e) {
            throw new ResourceNotFoundException();
        }
    }

    @RequestMapping(path = "/{onto}/terms/{id}/hierarchicalAncestors", produces = 
      {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> getHierarchicalAncestors(@PathVariable("onto") String ontologyId,
                                                                @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                                                @PathVariable("id") String termId, Pageable pageable, PagedResourcesAssembler assembler) {
      
        ontologyId = ontologyId.toLowerCase();

        try {
            String decoded = UriUtils.decode(termId, "UTF-8");
            Page<V1Term> parents = termRepository.getHierarchicalAncestors(ontologyId,
                decoded, lang, pageable);
            if (parents == null) 
              throw new ResourceNotFoundException("No ancestors could be found for " + ontologyId
                  + " and " + termId);

            return new ResponseEntity<>(assembler.toResource(parents, termAssembler), HttpStatus.OK);
        }
        catch (UnsupportedEncodingException e) {
            throw new ResourceNotFoundException();
        }
    }

    @RequestMapping(path = "/{onto}/terms/{id}/children", produces = {MediaType.APPLICATION_JSON_VALUE, 
        MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> children(@PathVariable("onto") String ontologyId,
                                                @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                                @PathVariable("id") String termId, Pageable pageable, PagedResourcesAssembler assembler) {
      
        ontologyId = ontologyId.toLowerCase();

        try {
            String decoded = UriUtils.decode(termId, "UTF-8");
            Page<V1Term> children = termRepository.getChildren(ontologyId, decoded, lang, pageable);
            if (children == null) 
              throw  new ResourceNotFoundException("No children could be found for " + ontologyId
                  + " and " + termId);

            return new ResponseEntity<>( assembler.toResource(children, termAssembler), HttpStatus.OK);
        }
        catch (UnsupportedEncodingException e) {
            throw new ResourceNotFoundException();
        }
    }

    @RequestMapping(path = "/{onto}/terms/{id}/hierarchicalChildren", produces = 
      {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> getHierarchicalChildren(@PathVariable("onto") String ontologyId,
                                                               @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                                               @PathVariable("id") String termId, Pageable pageable, PagedResourcesAssembler assembler) {
      
        ontologyId = ontologyId.toLowerCase();

        try {
            String decoded = UriUtils.decode(termId, "UTF-8");
            Page<V1Term> children = termRepository.getHierarchicalChildren(ontologyId,
                decoded, lang, pageable);
            
            if (children == null) 
              throw new ResourceNotFoundException("No hierarchical children could be found for " 
                  + ontologyId + " and " + termId);

            return new ResponseEntity<>(assembler.toResource(children, termAssembler), HttpStatus.OK);
        }
        catch (UnsupportedEncodingException e) {
            throw new ResourceNotFoundException();
        }
    }

    @RequestMapping(path = "/{onto}/terms/{id}/hierarchicalDescendants", produces = 
      {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> getHierarchicalDescendants(@PathVariable("onto") String ontologyId,
                                                                  @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                                                  @PathVariable("id") String termId, Pageable pageable, PagedResourcesAssembler assembler) {
        
        ontologyId = ontologyId.toLowerCase();

        try {
            String decoded = UriUtils.decode(termId, "UTF-8");
            Page<V1Term> children = termRepository.getHierarchicalDescendants(ontologyId,
                decoded, lang, pageable);
            if (children == null) 
              throw new ResourceNotFoundException("No hierarchical descendants could be found for " 
                  + ontologyId + " and " + termId);

            return new ResponseEntity<>( assembler.toResource(children, termAssembler), HttpStatus.OK);
        }
        catch (UnsupportedEncodingException e) {
            throw new ResourceNotFoundException();
        }
    }

    @RequestMapping(path = "/{onto}/terms/{id}/descendants", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> descendants(@PathVariable("onto") String ontologyId,
                                                   @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                                   @PathVariable("id") String termId, Pageable pageable,
                                                   PagedResourcesAssembler assembler) {
        ontologyId = ontologyId.toLowerCase();

        try {
            String decoded = UriUtils.decode(termId, "UTF-8");
            Page<V1Term> descendants = termRepository.getDescendants(ontologyId, decoded, lang, pageable);
            if (descendants == null) throw  new ResourceNotFoundException();

            return new ResponseEntity<>( assembler.toResource(descendants, termAssembler), HttpStatus.OK);
        }
        catch (UnsupportedEncodingException e) {
            throw new ResourceNotFoundException();
        }
    }

    @RequestMapping(path = "/{onto}/terms/{id}/ancestors", 
        produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, 
        method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> ancestors(@PathVariable("onto") String ontologyId, @PathVariable("id") String termId, Pageable pageable,
                                                 @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                                 PagedResourcesAssembler assembler) {
        ontologyId = ontologyId.toLowerCase();

        try {
            String decoded = UriUtils.decode(termId, "UTF-8");
            Page<V1Term> ancestors = termRepository.getAncestors(ontologyId, decoded, lang, pageable);
            if (ancestors == null) throw  new ResourceNotFoundException();

            return new ResponseEntity<>( assembler.toResource(ancestors, termAssembler), HttpStatus.OK);
        }
        catch (UnsupportedEncodingException e) {
            throw new ResourceNotFoundException();
        }
    }

    @RequestMapping(path = "/{onto}/terms/{id}/jstree", 
        produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, 
        method = RequestMethod.GET)
    HttpEntity<String> graphJsTree(
            @PathVariable("onto") String ontologyId,
            @PathVariable("id") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            @RequestParam(value = "siblings", defaultValue = "false", required = false) boolean siblings,
            @RequestParam(value = "viewMode", defaultValue = "PreferredRoots", required = false) String viewMode){
      
        ontologyId = ontologyId.toLowerCase();

        try {
            String decodedTermId = UriUtils.decode(termId, "UTF-8");

            Object object= jsTreeBuilder.getJsTree(ontologyId, decodedTermId, siblings, ViewMode.getFromShortName(viewMode));
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            return new HttpEntity<String>(ow.writeValueAsString(object));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        throw new ResourceNotFoundException();
    }

    @RequestMapping(path = "/{onto}/terms/{id}/jstree/children/{nodeid}", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<String> graphJsTreeChildren(
            @PathVariable("onto") String ontologyId,
            @PathVariable("id") String termId,
            @PathVariable("nodeid") String nodeId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) {
        ontologyId = ontologyId.toLowerCase();

        try {
            String decoded = UriUtils.decode(termId, "UTF-8");

            Object object= jsTreeBuilder.getJsTreeChildren(ontologyId, decoded, lang, nodeId);
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            return new HttpEntity<String>(ow.writeValueAsString(object));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        throw new ResourceNotFoundException();
    }

    @RequestMapping(path = "/{onto}/terms/{id}/graph", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<String> graphJson(
            @PathVariable("onto") String ontologyId,
            @PathVariable("id") String termId,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang
    ) {
        ontologyId = ontologyId.toLowerCase();

        try {
            String decoded = UriUtils.decode(termId, "UTF-8");

            Object object= termRepository.getGraphJson(ontologyId, decoded);
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            return new HttpEntity<String>(ow.writeValueAsString(object));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        throw new ResourceNotFoundException();
    }

    @RequestMapping(path = "/{onto}/terms/{id}/{relation}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> related(@PathVariable("onto") String ontologyId, @PathVariable("id") String termId, @PathVariable("relation") String relation, Pageable pageable,
                                               @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                                               PagedResourcesAssembler assembler) {
        ontologyId = ontologyId.toLowerCase();

        try {
            String decodedTerm = UriUtils.decode(termId, "UTF-8");
            String decodedRelation = UriUtils.decode(relation, "UTF-8");
            Page<V1Term> related = termRepository.getRelated(ontologyId, decodedTerm, decodedRelation, lang, pageable);

            return new ResponseEntity<>( assembler.toResource(related, termAssembler), HttpStatus.OK);
        }
        catch (UnsupportedEncodingException e) {
            throw new ResourceNotFoundException();
        }
    }

    @RequestMapping(path = "/{onto}/children", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> termChildrenByOntology(
            @PathVariable("onto") String ontologyId,
            @RequestParam(value = "iri", required = false) String iri,
            @RequestParam(value = "short_form", required = false) String shortForm,
            @RequestParam(value = "obo_id", required = false) String oboId,
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {


        id = getIdFromMultipleOptions(iri, shortForm, oboId, id);
        if (id == null) {
            return new ResponseEntity<>( assembler.toResource(new PageImpl<V1Term>(Collections.emptyList()), termAssembler), HttpStatus.OK);
        }
        V1Term target = getOneById(ontologyId, lang, id);
        ontologyId = ontologyId.toLowerCase();
        if (target == null) throw new ResourceNotFoundException("No resource with " + id + " in " + ontologyId);

        Page<V1Term>  terms = termRepository.getChildren(ontologyId, target.iri, lang, pageable);
        return new ResponseEntity<>( assembler.toResource(terms, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/descendants", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> termDescendantsByOntology(
            @PathVariable("onto") String ontologyId,
            @RequestParam(value = "iri", required = false) String iri,
            @RequestParam(value = "short_form", required = false) String shortForm,
            @RequestParam(value = "obo_id", required = false) String oboId,
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {


        id = getIdFromMultipleOptions(iri, shortForm, oboId, id);
        if (id == null) {
            return new ResponseEntity<>( assembler.toResource(new PageImpl<V1Term>(Collections.emptyList()), termAssembler), HttpStatus.OK);
        }
        V1Term target = getOneById(ontologyId, lang, id);
        ontologyId = ontologyId.toLowerCase();
        if (target == null) throw new ResourceNotFoundException("No resource with " + id + " in " + ontologyId);

        Page<V1Term>  terms = termRepository.getDescendants(ontologyId, target.iri, lang, pageable);
        return new ResponseEntity<>( assembler.toResource(terms, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/hierarchicalChildren", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> termHierarchicalChildrenByOntology(
            @PathVariable("onto") String ontologyId,
            @RequestParam(value = "iri", required = false) String iri,
            @RequestParam(value = "short_form", required = false) String shortForm,
            @RequestParam(value = "obo_id", required = false) String oboId,
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {


        id = getIdFromMultipleOptions(iri, shortForm, oboId, id);
        if (id == null) {
            return new ResponseEntity<>( assembler.toResource(new PageImpl<V1Term>(Collections.emptyList()), termAssembler), HttpStatus.OK);
        }
        V1Term target = getOneById(ontologyId, lang, id);
        ontologyId = ontologyId.toLowerCase();
        if (target == null) throw new ResourceNotFoundException("No resource with " + id + " in " + ontologyId);

        Page<V1Term>  terms = termRepository.getHierarchicalChildren(ontologyId, target.iri, lang, pageable);
        return new ResponseEntity<>( assembler.toResource(terms, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/hierarchicalDescendants", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> termHierarchicalDescendantsByOntology(
            @PathVariable("onto") String ontologyId,
            @RequestParam(value = "iri", required = false) String iri,
            @RequestParam(value = "short_form", required = false) String shortForm,
            @RequestParam(value = "obo_id", required = false) String oboId,
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {


        id = getIdFromMultipleOptions(iri, shortForm, oboId, id);
        if (id == null) {
            return new ResponseEntity<>( assembler.toResource(new PageImpl<V1Term>(Collections.emptyList()), termAssembler), HttpStatus.OK);
        }
        V1Term target = getOneById(ontologyId, lang, id);
        ontologyId = ontologyId.toLowerCase();
        if (target == null) throw new ResourceNotFoundException("No resource with " + id + " in " + ontologyId);

        Page<V1Term>  terms = termRepository.getHierarchicalDescendants(ontologyId, target.iri, lang, pageable);
        return new ResponseEntity<>( assembler.toResource(terms, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/parents", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> termParentsByOntology(
            @PathVariable("onto") String ontologyId,
            @RequestParam(value = "iri", required = false) String iri,
            @RequestParam(value = "short_form", required = false) String shortForm,
            @RequestParam(value = "obo_id", required = false) String oboId,
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {


        id = getIdFromMultipleOptions(iri, shortForm, oboId, id);
        if (id == null) {
            return new ResponseEntity<>( assembler.toResource(new PageImpl<V1Term>(Collections.emptyList()), termAssembler), HttpStatus.OK);
        }
        V1Term target = getOneById(ontologyId, id, lang);
        ontologyId = ontologyId.toLowerCase();
        if (target == null) throw new ResourceNotFoundException("No resource with " + id + " in " + ontologyId);

        Page<V1Term>  terms = termRepository.getParents(ontologyId, target.iri, lang, pageable);
        return new ResponseEntity<>( assembler.toResource(terms, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/ancestors", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> termAncestorsByOntology(
            @PathVariable("onto") String ontologyId,
            @RequestParam(value = "iri", required = false) String iri,
            @RequestParam(value = "short_form", required = false) String shortForm,
            @RequestParam(value = "obo_id", required = false) String oboId,
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {


        id = getIdFromMultipleOptions(iri, shortForm, oboId, id);
        if (id == null) {
            return new ResponseEntity<>( assembler.toResource(new PageImpl<V1Term>(Collections.emptyList()), termAssembler), HttpStatus.OK);
        }
        V1Term target = getOneById(ontologyId, id, lang);
        ontologyId = ontologyId.toLowerCase();
        if (target == null) throw new ResourceNotFoundException("No resource with " + id + " in " + ontologyId);

        Page<V1Term>  terms = termRepository.getAncestors(ontologyId, target.iri, lang, pageable);
        return new ResponseEntity<>( assembler.toResource(terms, termAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = "/{onto}/hierarchicalAncestors", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<PagedResources<V1Term>> termHierarchicalAncestorsByOntology(
            @PathVariable("onto") String ontologyId,
            @RequestParam(value = "iri", required = false) String iri,
            @RequestParam(value = "short_form", required = false) String shortForm,
            @RequestParam(value = "obo_id", required = false) String oboId,
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {


        id = getIdFromMultipleOptions(iri, shortForm, oboId, id);
        if (id == null) {
            return new ResponseEntity<>( assembler.toResource(new PageImpl<V1Term>(Collections.emptyList()), termAssembler), HttpStatus.OK);
        }
        V1Term target = getOneById(ontologyId, id, lang);
        ontologyId = ontologyId.toLowerCase();
        if (target == null) throw new ResourceNotFoundException("No resource with " + id + " in " + ontologyId);

        Page<V1Term>  terms = termRepository.getHierarchicalAncestors(ontologyId, target.iri, lang, pageable);
        return new ResponseEntity<>( assembler.toResource(terms, termAssembler), HttpStatus.OK);
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Resource not found")
    @ExceptionHandler(ResourceNotFoundException.class)
    public void handleError(HttpServletRequest req, Exception exception) {

    }

}
