package uk.ac.ebi.spot.ols.repository.v1;

import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.neo4j.OlsNeo4jClient;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrQuery;
import uk.ac.ebi.spot.ols.repository.solr.SearchType;
import uk.ac.ebi.spot.ols.repository.v1.mappers.V1TermMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class V1TermRepository {

    private Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    V1OntologyRepository ontologyRepository;

    @Autowired
    V1PropertyRepository propertyRepository;

    @Autowired
    OlsNeo4jClient neo4jClient;

    @Autowired
    OlsSolrClient solrClient;

    public Page<V1Term> getParents(String ontologyId, String iri, String lang, Pageable pageable) {

        return this.neo4jClient.traverseOutgoingEdges("OntologyClass", ontologyId + "+class+" + iri, Arrays.asList("directParent"), Map.of(), pageable)
                .map(node -> V1TermMapper.mapTerm(node, lang));
    }

    public Page<V1Term> getHierarchicalParents(String ontologyId, String iri, String lang, Pageable pageable) {

        List<String> relationIRIs = List.of("hierarchicalParent");

        return this.neo4jClient.traverseOutgoingEdges("OntologyClass", ontologyId + "+class+" + iri, relationIRIs, Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));
    }

    public Page<V1Term> getHierarchicalAncestors(String ontologyId, String iri, String lang, Pageable pageable) {

        List<String> relationIRIs = List.of("hierarchicalParent");

        return this.neo4jClient.recursivelyTraverseOutgoingEdges("OntologyClass", ontologyId + "+class+" + iri, relationIRIs, Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));

    }

    public Page<V1Term> getChildren(String ontologyId, String iri, String lang, Pageable pageable) {

        return this.neo4jClient.traverseIncomingEdges("OntologyClass", ontologyId + "+class+" + iri, Arrays.asList("directParent"), Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));
    }

    public Page<V1Term> getHierarchicalChildren(String ontologyId, String iri, String lang, Pageable pageable) {

        List<String> relationIRIs = List.of("hierarchicalParent");

        return this.neo4jClient.traverseIncomingEdges("OntologyClass", ontologyId + "+class+" + iri, relationIRIs, Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));

    }

    public Page<V1Term> getHierarchicalDescendants(String ontologyId, String iri, String lang, Pageable pageable) {

        List<String> relationIRIs = List.of("hierarchicalParent");

        return this.neo4jClient.recursivelyTraverseIncomingEdges("OntologyClass", ontologyId + "+class+" + iri, relationIRIs, Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));
    }


    public Page<V1Term> getDescendants(String ontologyId, String iri, String lang, Pageable pageable) {

        return this.neo4jClient.recursivelyTraverseIncomingEdges("OntologyClass", ontologyId + "+class+" + iri, Arrays.asList("directParent"), Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));

    }

    public Page<V1Term> getAncestors(String ontologyId, String iri, String lang, Pageable pageable) {

        V1Ontology ontology = ontologyRepository.get(ontologyId, lang);

        return this.neo4jClient.recursivelyTraverseOutgoingEdges("OntologyClass", ontologyId + "+class+" + iri, Arrays.asList("directParent"), Map.of(), pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));

    }

    public Page<V1Term> getRelated(String ontologyId, String iri, String lang, String relation, Pageable pageable) {

        return this.neo4jClient.traverseOutgoingEdges(
                        "OntologyClass", ontologyId + "+class+" + iri,
                        Arrays.asList("relatedTo"),
                        Map.of("property", relation),
                        pageable)
                .map(record -> V1TermMapper.mapTerm(record, lang));

    }

    public V1Term findByOntologyAndIri(String ontologyId, String iri, String lang) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        JsonElement first;
        try {
            first = solrClient.getFirst(query);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
        return V1TermMapper.mapTerm(first, lang);

    }

    public Page<V1Term> findAllByOntology(String ontologyId, Boolean obsoletes, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        if (obsoletes != null) query.addFilter("isObsolete", List.of(Boolean.toString(obsoletes)), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public V1Term findByOntologyAndShortForm(String ontologyId, String shortForm, String lang) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter("shortForm", List.of(shortForm), SearchType.WHOLE_FIELD);

        JsonElement first;
        try {
            first = solrClient.getFirst(query);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
        return V1TermMapper.mapTerm(first, lang);
    }

    public V1Term findByOntologyAndOboId(String ontologyId, String oboId, String lang) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter("curie", List.of(oboId), SearchType.WHOLE_FIELD);

        JsonElement first;
        try {
            first = solrClient.getFirst(query);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
        return V1TermMapper.mapTerm(first, lang);

    }

    public Page<V1Term> getRoots(String ontologyId, boolean obsolete, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
        query.addFilter("hasDirectParent", List.of("false"), SearchType.WHOLE_FIELD);
        query.addFilter("hasHierarchicalParent", List.of("false"), SearchType.WHOLE_FIELD);

        if (!obsolete)
            query.addFilter("isObsolete", List.of("false"), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    //    @Query (countQuery = "MATCH (n:PreferredRootTerm) WHERE n.ontology_name = {0} AND n.is_obsolete = {1} RETURN count(n)",
    //            value = "MATCH (n:PreferredRootTerm) WHERE n.ontology_name = {0} AND n.is_obsolete = {1} RETURN n")
    public Page<V1Term> getPreferredRootTerms(String ontologyId, boolean obsolete, String lang, Pageable pageable) {
        throw new RuntimeException();
    }

    //    @Query (value = "MATCH (n:PreferredRootTerm) WHERE n.ontology_name = {0} AND n.is_obsolete = {1} RETURN count(n)")
    public long getPreferredRootTermCount(String ontologyId, boolean obsolete) {
        throw new RuntimeException();
    }

    public Page<V1Term> findAll(String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public Page<V1Term> findAllByIsDefiningOntology(String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("isDefiningOntology", List.of("true"), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));

    }

    public Page<V1Term> findAllByIri(String iri, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public Page<V1Term> findAllByIriAndIsDefiningOntology(String iri, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("isDefiningOntology", List.of("true"), SearchType.WHOLE_FIELD);
        query.addFilter("iri", List.of(iri), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public Page<V1Term> findAllByShortForm(String shortForm, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("shortForm", List.of(shortForm), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public Page<V1Term> findAllByShortFormAndIsDefiningOntology(String shortForm, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("isDefiningOntology", List.of("true"), SearchType.WHOLE_FIELD);
        query.addFilter("shortForm", List.of(shortForm), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    public Page<V1Term> findAllByOboId(String oboId, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("curie", List.of(oboId), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));

    }

    public Page<V1Term> findAllByOboIdAndIsDefiningOntology(String oboId, String lang, Pageable pageable) {

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
        query.addFilter("isDefiningOntology", List.of("true"), SearchType.WHOLE_FIELD);
        query.addFilter("curie", List.of(oboId), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1TermMapper.mapTerm(result, lang));
    }

    //    @Query (countQuery = "MATCH (i:Individual)-[INSTANCEOF]->(c:Class) WHERE i.ontology_name = {0} AND c.iri = {1} RETURN count(i)",
    //            value = "MATCH (i:Individual)-[INSTANCEOF]->(c:Class) WHERE i.ontology_name = {0} AND c.iri = {1} RETURN i")
    public Page<V1Individual> getInstances(String ontologyId, String iri, Pageable pageable) {
        throw new RuntimeException();
    }



    @Cacheable(value = "concepttree", key="#ontologyId.concat('-').concat(#schema).concat('-').concat(#narrower).concat('-').concat(#withChildren)")
    public List<TreeNode<V1Term>> conceptTree (String ontologyId, boolean schema, boolean narrower, boolean withChildren, Boolean obsoletes, String lang, Pageable pageable){
        Page<V1Term> terms = this.findAllByOntology(ontologyId, obsoletes, lang, pageable);
        List<V1Term> listOfTerms = new ArrayList<V1Term>();
        listOfTerms.addAll(terms.getContent());

    	while(terms.hasNext()) {
    		terms = this.findAllByOntology(ontologyId, obsoletes, lang, terms.nextPageable());
    		listOfTerms.addAll(terms.getContent());
    	}

        List<TreeNode<V1Term>> rootTerms = new ArrayList<TreeNode<V1Term>>();
        int count = 0;

        if(schema) {
            for (V1Term term : listOfTerms)
           	    if (term.annotation.get("hasTopConcept") != null) {
        		 for (String iriTopConcept : (LinkedHashSet<String>) term.annotation.get("hasTopConcept")) {
        			 V1Term topConceptTerm = findTerm(listOfTerms,iriTopConcept);
        			 TreeNode<V1Term> topConcept =  new TreeNode<V1Term>(topConceptTerm);
        		     topConcept.setIndex(String.valueOf(++count));
        		     if(withChildren) {
            		     if(narrower)
            		         populateChildrenandRelatedByNarrower(topConceptTerm,topConcept,listOfTerms);
            		     else
            		    	 populateChildrenandRelatedByBroader(topConceptTerm,topConcept,listOfTerms);
        		     }
        			 rootTerms.add(topConcept);
        		 }
           	    }
        } else for (V1Term term : listOfTerms) {
        	 TreeNode<V1Term> tree = new TreeNode<V1Term>(term);

        	 if (tree.isRoot() && term.annotation.get("topConceptOf") != null) {
				tree.setIndex(String.valueOf(++count));
				if(withChildren) {
					if(narrower)
	                    populateChildrenandRelatedByNarrower(term,tree,listOfTerms);
					else
						populateChildrenandRelatedByBroader(term,tree,listOfTerms);
				}
				rootTerms.add(tree);
			}
		}

         return rootTerms;
    }

    @Cacheable(value = "concepttree", key="#ontologyId.concat('-').concat(#narrower).concat('-').concat(#withChildren)")
    public List<TreeNode<V1Term>> conceptTreeWithoutTop (String ontologyId, boolean narrower, boolean withChildren,  Boolean obsoletes, String lang, Pageable pageable){
        Page<V1Term> terms = this.findAllByOntology(ontologyId, obsoletes, lang, pageable);
        List<V1Term> listOfTerms = new ArrayList<V1Term>();
        listOfTerms.addAll(terms.getContent());

    	while(terms.hasNext()) {
    		terms = this.findAllByOntology(ontologyId, obsoletes, lang, terms.nextPageable());
    		listOfTerms.addAll(terms.getContent());
    	}

        Set<String> rootIRIs = new HashSet<String>();
        List<TreeNode<V1Term>> rootTerms = new ArrayList<TreeNode<V1Term>>();
        int count = 0;
        if(!narrower) {
            for (V1Term term : listOfTerms) {
                if(term.annotation != null && term.annotation.get("broader") != null) {
                        for (String iriBroader : (LinkedHashSet<String>) term.annotation.get("broader")) {
                            V1Term broaderTerm = findTerm(listOfTerms, iriBroader);
                            if (broaderTerm.annotation != null && broaderTerm.annotation.get("broader") == null) {
                                    rootIRIs.add(iriBroader);
                                }

                        }
                    }
            }

            for (String iri : rootIRIs) {
            	V1Term topConceptTerm = findTerm(listOfTerms, iri);
        		TreeNode<V1Term> topConcept = new TreeNode<V1Term>(topConceptTerm);
        		topConcept.setIndex(String.valueOf(++count));
        		if(withChildren)
    		        populateChildrenandRelatedByBroader(topConceptTerm,topConcept,listOfTerms);
        		rootTerms.add(topConcept);
            }

        } else {
        	for (V1Term term : listOfTerms) {
                if (term.annotation != null && term.annotation.get("narrower") != null) {
                        boolean root = true;
                        for (V1Term v1Term : listOfTerms) {
                            if (v1Term.annotation != null && v1Term.annotation.get("narrower") != null) {
                                    for (String iriNarrower : (LinkedHashSet<String>) v1Term.annotation.get("narrower")) {
                                        if (term.iri.equals(iriNarrower))
                                            root = false;
                                    }
                                }
                        }

                        if (root) {
                            TreeNode<V1Term> topConcept = new TreeNode<V1Term>(term);
                            topConcept.setIndex(String.valueOf(++count));
                            if (withChildren)
                                populateChildrenandRelatedByNarrower(term, topConcept, listOfTerms);
                            rootTerms.add(topConcept);
                        }
                    }
        	}
        }

         return rootTerms;
    }

    @Cacheable(value = "concepttree", key="#ontologyId.concat('-').concat('s').concat('-').concat(#iri).concat('-').concat(#narrower).concat('-').concat(#index)")
    public TreeNode<V1Term> conceptSubTree(String ontologyId, String iri, boolean narrower, String index, Boolean obsoletes, String lang, Pageable pageable){
        Page<V1Term> terms = this.findAllByOntology(ontologyId, obsoletes, lang, pageable);
        List<V1Term> listOfTerms = new ArrayList<V1Term>();
        listOfTerms.addAll(terms.getContent());

    	while(terms.hasNext()) {
    		terms = this.findAllByOntology(ontologyId, obsoletes, lang, terms.nextPageable());
    		listOfTerms.addAll(terms.getContent());
    	}

    	V1Term topConceptTerm = findTerm(listOfTerms,iri);
		TreeNode<V1Term> topConcept =  new TreeNode<V1Term>(topConceptTerm);
		topConcept.setIndex(index);
		 if(narrower)
		     populateChildrenandRelatedByNarrower(topConceptTerm,topConcept,listOfTerms);
		 else
			 populateChildrenandRelatedByBroader(topConceptTerm,topConcept,listOfTerms);

	     return topConcept;
    }

    public V1Term findTerm(List<V1Term> wholeList, String iri) {
    	for (V1Term term : wholeList)
    		if(term.iri.equals(iri))
    			return term;
    	return new V1Term();
    }

    public List<V1Term> findRelated(String ontologyId, String iri, String relationType, String lang) {
    	List<V1Term> related = new ArrayList<V1Term>();
    	V1Term term = this.findByOntologyAndIri(ontologyId, iri, lang);
		if (term != null)
			if (term.annotation.get(relationType) != null)
				for (String iriBroader : (LinkedHashSet<String>) term.annotation.get(relationType))
					related.add(this.findByOntologyAndIri(ontologyId, iriBroader, lang));

    	return related;
    }

    public List<V1Term>findRelatedIndirectly(String ontologyId, String iri, String relationType,  Boolean obsoletes, String lang, Pageable pageable){
    	List<V1Term> related = new ArrayList<V1Term>();

    	V1Term v1Term = this.findByOntologyAndIri(ontologyId, iri, lang);
    	if(v1Term == null)
    		return related;
    	if(v1Term.iri == null)
    		return related;

        Page<V1Term> terms = this.findAllByOntology(ontologyId, obsoletes, lang, pageable);
        List<V1Term> listOfTerms = new ArrayList<V1Term>();
        listOfTerms.addAll(terms.getContent());

    	while(terms.hasNext()) {
    		terms = this.findAllByOntology(ontologyId, obsoletes, lang, terms.nextPageable());
    		listOfTerms.addAll(terms.getContent());
    	}

    	for (V1Term term : listOfTerms) {
    		if (term != null)
    			if (term.annotation.get(relationType) != null)
    				for (String iriRelated : (LinkedHashSet<String>) term.annotation.get(relationType))
    					if(iriRelated.equals(iri))
    					    related.add(term);
    	}

    	return related;
    }

    public void populateChildrenandRelatedByNarrower(V1Term term, TreeNode<V1Term> tree, List<V1Term> listOfTerms ) {

        if (term.annotation != null)
            for (String iriRelated : (LinkedHashSet<String>) term.annotation.getOrDefault("related", new LinkedHashSet<String>())) {
                TreeNode<V1Term> related = new TreeNode<V1Term>(findTerm(listOfTerms, iriRelated));
                related.setIndex(tree.getIndex() + ".related");
                tree.addRelated(related);
            }
    	int count = 0;
        if (term.annotation != null)
            for (String iriChild : (LinkedHashSet<String>) term.annotation.getOrDefault("narrower", new LinkedHashSet<String>())) {
                V1Term childTerm = findTerm(listOfTerms, iriChild);
                TreeNode<V1Term> child = new TreeNode<V1Term>(childTerm);
                child.setIndex(tree.getIndex() + "." + ++count);
                populateChildrenandRelatedByNarrower(childTerm, child, listOfTerms);
                tree.addChild(child);
            }
    }

    public void populateChildrenandRelatedByBroader(V1Term term, TreeNode<V1Term> tree, List<V1Term> listOfTerms) {
        if (term.annotation != null)
            for (String iriRelated : (LinkedHashSet<String>) term.annotation.getOrDefault("related", new LinkedHashSet<String>())) {
                TreeNode<V1Term> related = new TreeNode<V1Term>(findTerm(listOfTerms, iriRelated));
                related.setIndex(tree.getIndex() + ".related");
                tree.addRelated(related);
            }
		int count = 0;
		for ( V1Term v1Term : listOfTerms) {
			if (v1Term.annotation != null)
				for (String iriBroader : (LinkedHashSet<String>) v1Term.annotation.getOrDefault("broader",new LinkedHashSet<String>()))
					if(term.iri != null)
						if (term.iri.equals(iriBroader)) {
							TreeNode<V1Term> child = new TreeNode<V1Term>(v1Term);
							child.setIndex(tree.getIndex()+"."+ ++count);
							populateChildrenandRelatedByBroader(v1Term,child,listOfTerms);
							tree.addChild(child);
						}
		}
    }
}
