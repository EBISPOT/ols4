package uk.ac.ebi.spot.ols.controller.api.v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.gson.JsonObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.controller.api.v1.TopConceptEnum;
import uk.ac.ebi.spot.ols.controller.api.v1.V1TermAssembler;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.v1.TreeNode;
import uk.ac.ebi.spot.ols.repository.v1.V1TermRepository;
import uk.ac.ebi.spot.ols.repository.v2.V2ClassRepository;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;

/**
 * @author Simon Jupp
 * @date 02/11/15
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@RestController
@RequestMapping("/api/v2/ontologies")
@Tag(name = "v2-ontology-skos-controller", description = "SKOS concept hierarchies and relations extracted from individuals (instances) from a particular ontology in this service")
public class V2OntologySKOSConceptController {

    @Autowired
    V2ClassRepository classRepository;

    @Operation(description = "Get complete SKOS concept hierarchy or only top concepts based on alternative top concept identification methods and concept relations. If only top concepts are identified, they can be used to extract the following levels of the concept tree one by one using the /{onto}/conceptrelations/{iri} method with broader or narrower concept relations.")
    @RequestMapping(path = "/{onto}/skos/tree", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<List<TreeNode<V2Entity>>> getSKOSConceptHierarchyByOntology(
    	    @Parameter(description = "ontology ID", required = true)
    	    @PathVariable("onto") String ontologyId,
    	    @Parameter(description = "infer top concepts by schema (hasTopConcept) or  TopConceptOf property or broader/narrower relationships", required = true)
            @RequestParam(value = "find_roots", required = true, defaultValue = "SCHEMA") TopConceptEnum topConceptIdentification,
            @Parameter(description = "infer from narrower or broader relationships", required = true)
            @RequestParam(value = "narrower", required = true, defaultValue = "false") boolean narrower,
            @Parameter(description = "Extract the whole tree with children or only the top concepts", required = true)
            @RequestParam(value = "with_children", required = true, defaultValue = "false") boolean withChildren,
            @RequestParam(value = "obsoletes", required = false, defaultValue = "false") Boolean obsoletes,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable) throws IOException {
    	ontologyId = ontologyId.toLowerCase();
    	if (TopConceptEnum.RELATIONSHIPS == topConceptIdentification)
    		return new ResponseEntity<>(classRepository.conceptTreeWithoutTop(ontologyId,narrower,withChildren,obsoletes,lang,pageable), HttpStatus.OK);
    	else
    		return new ResponseEntity<>(classRepository.conceptTree(ontologyId,TopConceptEnum.SCHEMA == topConceptIdentification,narrower, withChildren,obsoletes,lang,pageable), HttpStatus.OK);
    }

    @Operation(description = "Display complete SKOS concept hierarchy or only top concepts based on alternative top concept identification methods and concept relations. If only top concepts are identified, they can be used to extract the following levels of the concept tree one by one using the /{onto}/displayconceptrelations/{iri} method with broader or narrower concept relations.")
    @RequestMapping(path = "/{onto}/skos/displaytree", produces = {MediaType.TEXT_PLAIN_VALUE}, method = RequestMethod.GET)
    @ResponseBody
    HttpEntity<String> displaySKOSConceptHierarchyByOntology(
    	    @Parameter(description = "ontology ID", required = true)
    	    @PathVariable("onto") String ontologyId,
    		@Parameter(description = "infer top concepts by schema (hasTopConcept) or  TopConceptOf property or broader/narrower relationships", required = true)
    	    @RequestParam(value = "find_roots", required = true, defaultValue = "SCHEMA") TopConceptEnum topConceptIdentification,
            @Parameter(description = "infer from narrower or broader relationships", required = true)
            @RequestParam(value = "narrower", required = true, defaultValue = "false") boolean narrower,
            @Parameter(description = "Extract the whole tree with children or only the top concepts", required = true)
            @RequestParam(value = "with_children", required = true, defaultValue = "false") boolean withChildren,
            @Parameter(description = "display related concepts", required = true)
            @RequestParam(value = "display_related", required = true, defaultValue = "false") boolean displayRelated,
            @RequestParam(value = "obsoletes", required = false, defaultValue = "false") Boolean obsoletes,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable) throws IOException {
    	 ontologyId = ontologyId.toLowerCase();
     	 List<TreeNode<V2Entity>> rootIndividuals = null;
    	 if(TopConceptEnum.RELATIONSHIPS == topConceptIdentification)
    		 rootIndividuals = classRepository.conceptTreeWithoutTop(ontologyId,narrower,withChildren,obsoletes,lang,pageable);
    	 else
    		 rootIndividuals = classRepository.conceptTree(ontologyId,TopConceptEnum.SCHEMA == topConceptIdentification,narrower, withChildren,obsoletes,lang,pageable);
         StringBuilder sb = new StringBuilder();
         for (TreeNode<V2Entity> root : rootIndividuals) {
        	 sb.append(root.getIndex() + " , "+ root.getData().any().get("label").toString() + " , " + root.getData().any().get("iri").toString()).append("\n");
        	 sb.append(generateConceptHierarchyTextByOntology(root, displayRelated));
         }

         return new HttpEntity<String>(sb.toString());
    }

    @Operation(description = "Get partial SKOS concept hierarchy based on the encoded iri of the designated top concept")
    @RequestMapping(path = "/{onto}/skos/{iri}/tree", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<TreeNode<V2Entity>> getSKOSConceptHierarchyByOntologyAndIri(
    	    @Parameter(description = "ontology ID", required = true)
    	    @PathVariable("onto") String ontologyId,
            @Parameter(description = "encoded concept IRI", required = true)
            @PathVariable("iri") String iri,
            @Parameter(description = "infer from narrower or broader relationships", required = true)
            @RequestParam(value = "narrower", required = true, defaultValue = "false") boolean narrower,
            @Parameter(description = "index value for the root term", required = true)
            @RequestParam(value = "index", required = true, defaultValue = "1") String index,
            @RequestParam(value = "obsoletes", required = false, defaultValue = "false") Boolean obsoletes,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable) throws IOException {
    	ontologyId = ontologyId.toLowerCase();
    	TreeNode<V2Entity> topConcept = new TreeNode<V2Entity>(new V2Entity(new JsonObject()));
    	String decodedIri;
		decodedIri = UriUtils.decode(iri, "UTF-8");
		topConcept = classRepository.conceptSubTree(ontologyId, decodedIri, narrower, index, obsoletes, lang, pageable);

        if (topConcept.getData().any().get("iri").toString() == null)
            throw new ResourceNotFoundException("No roots could be found for " + ontologyId );
        return new ResponseEntity<>(topConcept, HttpStatus.OK);
    }

    @Operation(description = "Display partial SKOS concept hierarchy based on the encoded iri of the designated top concept")
    @RequestMapping(path = "/{onto}/skos/{iri}/displaytree", produces = {MediaType.TEXT_PLAIN_VALUE}, method = RequestMethod.GET)
    @ResponseBody
    HttpEntity<String> displaySKOSConceptHierarchyByOntologyAndIri(
    	    @Parameter(description = "ontology ID", required = true)
    	    @PathVariable("onto") String ontologyId,
            @Parameter(description = "encoded concept IRI", required = true)
            @PathVariable("iri") String iri,
            @Parameter(description = "infer from narrower or broader relationships", required = true)
            @RequestParam(value = "narrower", required = true, defaultValue = "false") boolean narrower,
            @Parameter(description = "display related concepts", required = true)
            @RequestParam(value = "display_related", required = true, defaultValue = "false") boolean displayRelated,
            @Parameter(description = "index value for the root term", required = true)
            @RequestParam(value = "index", required = true, defaultValue = "1") String index,
            @RequestParam(value = "obsoletes", required = false, defaultValue = "false") Boolean obsoletes,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable) throws IOException {
	    	ontologyId = ontologyId.toLowerCase();
	    	TreeNode<V2Entity> topConcept = new TreeNode<V2Entity>(new V2Entity(new JsonObject()));
	    	String decodedIri;
	    	StringBuilder sb = new StringBuilder();
			decodedIri = UriUtils.decode(iri, "UTF-8");
			topConcept = classRepository.conceptSubTree(ontologyId, decodedIri, narrower, index, obsoletes, lang, pageable);

        	sb.append(topConcept.getIndex() + " , "+ topConcept.getData().any().get("label").toString() + " , " + topConcept.getData().any().get("iri").toString()).append("\n");
	        sb.append(generateConceptHierarchyTextByOntology(topConcept, displayRelated));

            return new HttpEntity<String>(sb.toString());
    }

    @Operation(description = "Broader, Narrower and Related concept relations of a concept are listed in JSON if the concept iri is provided in encoded format.")
    @RequestMapping(path = "/{onto}/skos/{iri}/relations", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<PagedModel<V2Entity>> findRelatedConcepts(
    		@Parameter(description = "ontology ID", required = true)
    		@PathVariable("onto") String ontologyId,
            @Parameter(description = "encoded concept IRI", required = true)
            @PathVariable("iri") String iri,
            @Parameter(description = "skos based concept relation type", required = true)
            @RequestParam(value = "relation_type", required = true, defaultValue = "broader")
    		@Schema(type = "string", allowableValues = { "broader", "narrower", "related" }) String relationType,
            @RequestParam(value = "obsoletes", required = false, defaultValue = "false") Boolean obsoletes,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {

    	ontologyId = ontologyId.toLowerCase();
    	List<V2Entity> related = new ArrayList<V2Entity>();
		String decodedIri = UriUtils.decode(iri, "UTF-8");
		related = classRepository.findRelated(ontologyId, decodedIri, relationType,lang);


        final int start = (int)pageable.getOffset();
        final int end = Math.min((start + pageable.getPageSize()), related.size());
        Page<V2Entity> conceptPage = new PageImpl<>(related.subList(start, end), pageable, related.size());

        return new ResponseEntity<>( assembler.toModel(conceptPage), HttpStatus.OK);

    }

    @Operation(description = "Broader, Narrower and Related concept relations of a concept are displayed as text if the concept iri is provided in encoded format.")
    @RequestMapping(path = "/{onto}/skos/{iri}/displayrelations", produces = {MediaType.TEXT_PLAIN_VALUE}, method = RequestMethod.GET)
    @ResponseBody
    public HttpEntity<String> displayRelatedConcepts(
    		@Parameter(description = "ontology ID", required = true)
    		@PathVariable("onto") String ontologyId,
            @Parameter(description = "encoded concept IRI", required = true)
            @PathVariable("iri") String iri,
            @Parameter(description = "skos based concept relation type", required = true)
            @RequestParam(value = "relation_type", required = true, defaultValue = "broader")
    		@Schema(type = "string", allowableValues = { "broader", "narrower", "related" }) String relationType,
            @RequestParam(value = "obsoletes", required = false, defaultValue = "false") Boolean obsoletes,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable,
            PagedResourcesAssembler assembler) {
    	StringBuilder sb = new StringBuilder();
    	ontologyId = ontologyId.toLowerCase();
    	List<V2Entity> related = new ArrayList<V2Entity>();
		String decodedIri = UriUtils.decode(iri, "UTF-8");
		related = classRepository.findRelated(ontologyId, decodedIri, relationType,lang);

        final int start = (int)pageable.getOffset();
        final int end = Math.min((start + pageable.getPageSize()), related.size());
        Page<V2Entity> conceptPage = new PageImpl<>(related.subList(start, end), pageable, related.size());
        int count = 0;
        for (V2Entity individual : conceptPage.getContent())
        	sb.append(++count).append(" , ").append(individual.any().get("label").toString()).append(" , ").append(individual.any().get("iri").toString()).append("\n");

        return new HttpEntity<>( sb.toString());

    }

    @Operation(description = "Broader, Narrower and Related concept relations of a concept are listed in JSON if the concept iri is provided in encoded format. The relationship is identified indirectly based on the related concept's relation to the concept in question. This requires traversing all the available concepts and checking if they are related to the concept in question. For this reason, this method is relatively slower than the displayconceptrelations method. Nevertheless, it enables to identify unforeseen relations of the concept in question")
    @RequestMapping(path = "/{onto}/skos/{iri}/indirectrelations", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<List<V2Entity>> findRelatedConceptsIndirectly(
    		@Parameter(description = "ontology ID", required = true)
    		@PathVariable("onto") String ontologyId,
            @Parameter(description = "encoded concept IRI", required = true)
            @PathVariable("iri") String iri,
            @Parameter(description = "skos based concept relation type", required = true)
            @RequestParam(value = "relation_type", required = true, defaultValue = "broader")
    		@Schema(type = "string", allowableValues = { "broader", "narrower", "related" }) String relationType,
            @RequestParam(value = "obsoletes", required = false, defaultValue = "false") Boolean obsoletes,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable) throws IOException {

    	ontologyId = ontologyId.toLowerCase();
    	List<V2Entity> related = new ArrayList<V2Entity>();
		String decodedIri = UriUtils.decode(iri, "UTF-8");
		related = classRepository.findRelatedIndirectly(ontologyId, decodedIri, relationType, obsoletes,lang,pageable);

        return new ResponseEntity<>( related, HttpStatus.OK);

    }

    @Operation(description = "Broader, Narrower and Related concept relations of a concept are listed in JSON if the concept iri is provided in encoded format. The relationship is identified indirectly based on the related concept's relation to the concept in question. This requires traversing all the available concepts and checking if they are related to the concept in question. For this reason, this method is relatively slower than the displayconceptrelations method. Nevertheless, it enables to identify unforeseen relations of the concept in question")
    @RequestMapping(path = "/{onto}/skos/{iri}/displayindirectrelations", produces = {MediaType.TEXT_PLAIN_VALUE}, method = RequestMethod.GET)
    @ResponseBody
    public HttpEntity<String> displayRelatedConceptsIndirectly(
    		@Parameter(description = "ontology ID", required = true)
    		@PathVariable("onto") String ontologyId,
            @Parameter(description = "encoded concept IRI", required = true)
            @PathVariable("iri") String iri,
            @Parameter(description = "skos based concept relation type", required = true)
            @RequestParam(value = "relation_type", required = true, defaultValue = "broader")
    		@Schema(type = "string", allowableValues = { "broader", "narrower", "related" }) String relationType,
            @Parameter(description = "Page size to retrieve individuals", required = true)
    		@RequestParam(value = "obsoletes", required = false, defaultValue = "false") Boolean obsoletes,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Pageable pageable) throws IOException {
    	StringBuilder sb = new StringBuilder();
    	ontologyId = ontologyId.toLowerCase();
    	List<V2Entity> related = new ArrayList<V2Entity>();
		String decodedIri = UriUtils.decode(iri, "UTF-8");
		related = classRepository.findRelatedIndirectly(ontologyId, decodedIri, relationType, obsoletes,lang,pageable);

    	int count = 0;
        for (V2Entity individual : related)
        	sb.append(++count).append(" , ").append(individual.any().get("label").toString()).append(" , ").append(individual.any().get("iri").toString()).append("\n");


        return new ResponseEntity<>( sb.toString(), HttpStatus.OK);

    }

    @Operation(description = "Node and Edge definitions needed to visualize the nodes that are directly related with the subject term. Ontology ID and encoded iri are required. ")
    @RequestMapping(path = "/{onto}/skos/{iri}/graph", produces = {MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE}, method = RequestMethod.GET)
    public HttpEntity<String> retrieveImmediateGraph(
            @Parameter(description = "ontology ID", required = true)
            @PathVariable("onto") String ontologyId,
            @Parameter(description = "encoded concept IRI", required = true)
            @PathVariable("iri") String iri,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang){

        List<V2Entity> related = new ArrayList<V2Entity>();
        String decodedIri = UriUtils.decode(iri, "UTF-8");

        V2Entity subjectTerm = classRepository.findByOntologyAndIri(ontologyId, decodedIri, lang);

        related = classRepository.findRelated(ontologyId, decodedIri, "related",lang);

        List<V2Entity> narrower = new ArrayList<V2Entity>();
        narrower = classRepository.findRelated(ontologyId, decodedIri, "narrower",lang);

        List<V2Entity> broader = new ArrayList<V2Entity>();
        broader = classRepository.findRelated(ontologyId, decodedIri, "broader",lang);

        Set<Node> relatedNodes = new HashSet<Node>();
        related.forEach(term -> relatedNodes.add(new Node(term.any().get("iri").toString(), term.any().get("label").toString())));
        Set<Node> narrowerNodes = new HashSet<Node>();
        narrower.forEach(term -> narrowerNodes.add(new Node(term.any().get("iri").toString(), term.any().get("label").toString())));
        Set<Node> broaderNodes = new HashSet<Node>();
        broader.forEach(term -> broaderNodes.add(new Node(term.any().get("iri").toString(), term.any().get("label").toString())));

        Set<Edge> edges = new HashSet<Edge>();
        relatedNodes.forEach(node -> edges.add(new Edge(decodedIri, node.iri, "related","http://www.w3.org/2004/02/skos/core#related")));
        narrowerNodes.forEach(node -> edges.add(new Edge(decodedIri, node.iri, "narrower","http://www.w3.org/2004/02/skos/core#narrower")));
        broaderNodes.forEach(node -> edges.add(new Edge(decodedIri, node.iri, "broader","http://www.w3.org/2004/02/skos/core#broader")));

        Set<Node> nodes = new HashSet<Node>();
        nodes.add(new Node(decodedIri,subjectTerm.any().get("label").toString()));
        nodes.addAll(relatedNodes);
        nodes.addAll(broaderNodes);
        nodes.addAll(narrowerNodes);


        Map<String, Object> graph = new HashMap<String,Object>();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try {
            return new ResponseEntity<>(ow.writeValueAsString(graph),HttpStatus.OK);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public StringBuilder generateConceptHierarchyTextByOntology(TreeNode<V2Entity> rootConcept, boolean displayRelated) {
    	StringBuilder sb = new StringBuilder();
        for (TreeNode<V2Entity> childConcept : rootConcept.getChildren()) {
       	     sb.append(childConcept.getIndex() + " , "+ childConcept.getData().any().get("label").toString() + " , " + childConcept.getData().any().get("iri").toString()).append("\n");
       	     sb.append(generateConceptHierarchyTextByOntology(childConcept,displayRelated));
        }
        if(displayRelated)
	        for (TreeNode<V2Entity> relatedConcept : rootConcept.getRelated()) {
	      	     sb.append(relatedConcept.getIndex() + " , "+ relatedConcept.getData().any().get("label").toString() + " , " + relatedConcept.getData().any().get("iri").toString()).append("\n");
	      	     sb.append(generateConceptHierarchyTextByOntology(relatedConcept,displayRelated));
	       }
        return sb;
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Resource not found")
    @ExceptionHandler(ResourceNotFoundException.class)
    public void handleError(HttpServletRequest req, Exception exception) {
    }

    public class Node {
        String iri;
        String label;

        public Node(String iri, String label) {
            this.iri = iri;
            this.label = label;
        }

        public String getIri() {
            return iri;
        }

        public String getLabel() {
            return label;
        }

    }

    public class Edge {
        String source;
        String target;
        String label;
        String uri;

        public Edge(String source, String target, String label, String uri) {
            this.source = source;
            this.target = target;
            this.label = label;
            this.uri = uri;
        }

        public String getSource() {
            return source;
        }

        public String getTarget() {
            return target;
        }

        public String getLabel() {
            return label;
        }

        public String getUri() {
            return uri;
        }

    }

}
