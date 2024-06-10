package uk.ac.ebi.spot.ols.repository.v2;

import com.google.gson.JsonObject;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.v1.TreeNode;

import java.io.IOException;
import java.util.*;

import static uk.ac.ebi.spot.ols.model.SKOSRelation.*;

/**
 * @author Erhun Giray TUNCAY
 * @email giray.tuncay@tib.eu
 * TIB-Leibniz Information Center for Science and Technology
 */
@Component
public class V2SKOSRepository extends V2ClassRepository {

    @Cacheable(value = "concepttree", key="#ontologyId.concat('-').concat(#schema).concat('-').concat(#narrower).concat('-').concat(#withChildren)")
    public List<TreeNode<V2Entity>> conceptTree (String ontologyId, boolean schema, boolean narrower, boolean withChildren, Boolean obsoletes, String lang, Pageable pageable) throws IOException {

        Map<String, Collection<String>> properties = new HashMap<>();
        if(!obsoletes)
            properties.put("isObsolete", List.of("false"));

        List<V2Entity> listOfTerms = allClassesOfOntology(ontologyId, obsoletes, pageable, lang);
        List<TreeNode<V2Entity>> rootTerms = new ArrayList<TreeNode<V2Entity>>();
        int count = 0;

        if(schema) {
            for (V2Entity term : listOfTerms)
                if (term.any().get(hasTopConcept.getPropertyName()) != null) {
                    for (String iriTopConcept : (ArrayList<String>) term.any().get(hasTopConcept.getPropertyName())) {
                        V2Entity topConceptTerm = findTerm(listOfTerms,iriTopConcept);
                        TreeNode<V2Entity> topConcept =  new TreeNode<V2Entity>(topConceptTerm);
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
        } else for (V2Entity term : listOfTerms) {
            TreeNode<V2Entity> tree = new TreeNode<V2Entity>(term);

            if (tree.isRoot() && term.any().get(topConceptOf.getPropertyName()) != null) {
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
    public List<TreeNode<V2Entity>> conceptTreeWithoutTop (String ontologyId, boolean isNarrower, boolean withChildren, Boolean obsoletes, String lang, Pageable pageable) throws IOException {

        List<V2Entity> listOfTerms = allClassesOfOntology(ontologyId, obsoletes, pageable, lang);

        Set<String> rootIRIs = new HashSet<String>();
        List<TreeNode<V2Entity>> rootTerms = new ArrayList<TreeNode<V2Entity>>();
        int count = 0;
        if(!isNarrower) {
            for (V2Entity term : listOfTerms) {
                if(term.any() != null && term.any().get(broader.getPropertyName()) != null) {
                    for (String iriBroader : getRelationsAsList(term,broader.getPropertyName())) {
                        V2Entity broaderTerm = findTerm(listOfTerms, iriBroader);
                        if (broaderTerm.any() != null && broaderTerm.any().get(broader.getPropertyName()) == null) {
                            rootIRIs.add(iriBroader);
                        }

                    }
                }
            }

            for (String iri : rootIRIs) {
                V2Entity topConceptTerm = findTerm(listOfTerms, iri);
                TreeNode<V2Entity> topConcept = new TreeNode<V2Entity>(topConceptTerm);
                topConcept.setIndex(String.valueOf(++count));
                if(withChildren)
                    populateChildrenandRelatedByBroader(topConceptTerm,topConcept,listOfTerms);
                rootTerms.add(topConcept);
            }

        } else {
            for (V2Entity term : listOfTerms) {
                if (term.any() != null && term.any().get(narrower) != null) {
                    boolean root = true;
                    for (V2Entity V2Entity : listOfTerms) {
                        if (V2Entity.any() != null && V2Entity.any().get(narrower) != null) {
                            for (String iriNarrower : getRelationsAsList(V2Entity,narrower.getPropertyName())) {
                                if (term.any().get("iri").equals(iriNarrower))
                                    root = false;
                            }
                        }
                    }

                    if (root) {
                        TreeNode<V2Entity> topConcept = new TreeNode<V2Entity>(term);
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
    public TreeNode<V2Entity> conceptSubTree(String ontologyId, String iri, boolean narrower, String index, Boolean obsoletes, String lang, Pageable pageable) throws IOException {
        List<V2Entity> listOfTerms = allClassesOfOntology(ontologyId, obsoletes, pageable, lang);
        V2Entity topConceptTerm = findTerm(listOfTerms,iri);
        TreeNode<V2Entity> topConcept =  new TreeNode<V2Entity>(topConceptTerm);
        topConcept.setIndex(index);
        if(narrower)
            populateChildrenandRelatedByNarrower(topConceptTerm,topConcept,listOfTerms);
        else
            populateChildrenandRelatedByBroader(topConceptTerm,topConcept,listOfTerms);

        return topConcept;
    }

    public V2Entity findTerm(List<V2Entity> wholeList, String iri) {
        for (V2Entity term : wholeList)
            if(term.any().get("iri").equals(iri))
                return term;
        return new V2Entity(new JsonObject());
    }

    public List<V2Entity> findRelated(String ontologyId, String iri, String relationType, String lang) {
        List<V2Entity> related = new ArrayList<V2Entity>();
        V2Entity term = this.findByOntologyAndIri(ontologyId, iri, lang);
        if (term != null)
            if (term.any().get(relationType) != null)
                for (String iriBroader : getRelationsAsList(term,relationType))
                    related.add(this.findByOntologyAndIri(ontologyId, iriBroader, lang));
        return related;
    }

    public List<V2Entity>findRelatedIndirectly(String ontologyId, String iri, String relationType,  Boolean obsoletes, String lang, Pageable pageable) throws IOException {
        List<V2Entity> related = new ArrayList<V2Entity>();

        V2Entity V2Entity = this.findByOntologyAndIri(ontologyId, iri, lang);
        if(V2Entity == null)
            return related;
        if(V2Entity.any().get("iri") == null)
            return related;

        List<V2Entity> listOfTerms = allClassesOfOntology(ontologyId, obsoletes, pageable, lang);

        for (V2Entity term : listOfTerms) {
            if (term != null)
                if (term.any().get(relationType) != null)
                    for (String iriRelated : getRelationsAsList(term,relationType))
                        if(iriRelated.equals(iri))
                            related.add(term);
        }

        return related;
    }

    public void populateChildrenandRelatedByNarrower(V2Entity term, TreeNode<V2Entity> tree, List<V2Entity> listOfTerms ) {

        if (term.any() != null)
            for (String iriRelated : getRelationsAsList(term,related.getPropertyName())) {
                TreeNode<V2Entity> related = new TreeNode<V2Entity>(findTerm(listOfTerms, iriRelated));
                related.setIndex(tree.getIndex() + ".related");
                tree.addRelated(related);
            }
        int count = 0;
        if (term.any() != null)
            for (String iriChild : getRelationsAsList(term,narrower.getPropertyName())) {
                V2Entity childTerm = findTerm(listOfTerms, iriChild);
                TreeNode<V2Entity> child = new TreeNode<V2Entity>(childTerm);
                child.setIndex(tree.getIndex() + "." + ++count);
                populateChildrenandRelatedByNarrower(childTerm, child, listOfTerms);
                tree.addChild(child);
            }
    }

    public void populateChildrenandRelatedByBroader(V2Entity term, TreeNode<V2Entity> tree, List<V2Entity> listOfTerms) {
        if (term.any() != null)
            for (String iriRelated : getRelationsAsList(term,related.getPropertyName())) {
                TreeNode<V2Entity> related = new TreeNode<V2Entity>(findTerm(listOfTerms, iriRelated));
                related.setIndex(tree.getIndex() + ".related");
                tree.addRelated(related);
            }
        int count = 0;
        for ( V2Entity V2Entity : listOfTerms) {
            if (V2Entity.any() != null)
                for (String iriBroader : getRelationsAsList(V2Entity,broader.getPropertyName()))
                    if(term.any().get("iri") != null)
                        if (term.any().get("iri").equals(iriBroader)) {
                            TreeNode<V2Entity> child = new TreeNode<V2Entity>(V2Entity);
                            child.setIndex(tree.getIndex()+"."+ ++count);
                            populateChildrenandRelatedByBroader(V2Entity,child,listOfTerms);
                            tree.addChild(child);
                        }
        }
    }
}
