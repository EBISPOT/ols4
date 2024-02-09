
package uk.ac.ebi.spot.ols.repository.v1;

import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.repository.solr.SearchType;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrQuery;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.repository.Validation;
import uk.ac.ebi.spot.ols.repository.v1.mappers.V1OntologyMapper;

import java.lang.reflect.Field;
import java.util.*;

@Component
public class V1OntologyRepository {

    @Autowired
    OlsSolrClient solrClient;

    public V1Ontology get(String ontologyId, String lang) {

        Validation.validateLang(lang);
        Validation.validateOntologyId(ontologyId);

        OlsSolrQuery query = new OlsSolrQuery();
	query.addFilter("type", List.of("ontology"), SearchType.WHOLE_FIELD);
	query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);

        return V1OntologyMapper.mapOntology(solrClient.getFirst(query), lang);
    }

    public Set<V1Ontology> getSet(String lang){
        Set<V1Ontology> tempSet = new HashSet<>();
        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();
        query.addFilter("type", List.of("ontology"), SearchType.WHOLE_FIELD);

        for (JsonElement element : solrClient.getSet(query))
            tempSet.add(V1OntologyMapper.mapOntology(element, lang));
        return tempSet;
    }

    public Page<V1Ontology> getAll(String lang, Pageable pageable) {

        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();
	query.addFilter("type", List.of("ontology"), SearchType.WHOLE_FIELD);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(result -> V1OntologyMapper.mapOntology(result, lang));
    }

    public Collection<String> filterOntologyIDs(Collection<String> schemas,Collection<String> classifications, Collection<String> ontologies, boolean exclusiveFilter, String lang){
        if (schemas != null)
            schemas.remove("");
        if (classifications != null)
            classifications.remove("");
        if(ontologies != null)
            ontologies.remove("");
        if((schemas == null || schemas.size() == 0 ) && (classifications == null || classifications.size() == 0 ) && (ontologies == null || ontologies.size() == 0))
            return null;
        if ((schemas == null || schemas.size() == 0 ) || (classifications == null || classifications.size() == 0 ))
            return ontologies;
        Set<V1Ontology> documents = filter(schemas, classifications, exclusiveFilter,lang);
        Set<String> filteredOntologySet = new HashSet<String>();
        for (V1Ontology document : documents){
            filteredOntologySet.add(document.ontologyId);
        }
        System.out.println("filteredOntologySet: "+filteredOntologySet);
        if (( ontologies == null || ontologies.size() == 0) && filteredOntologySet.size() > 0)
            return filteredOntologySet;
        else if (schemas != null)
            if ((ontologies == null || ontologies.size() == 0) && (schemas.size() > 0 || classifications.size() > 0 ))
                return new HashSet<String>(Arrays.asList("nosuchontologyfound"));

        Set<String> postFilterOntologySet;

        if(ontologies == null){
            ontologies = new HashSet<String>();
            System.out.println("ontologies == null");
        } else {
            ontologies = new HashSet<String>(ontologies);
            System.out.println("ontologies <> null");
        }

        System.out.println("ontologies: "+ontologies);
        if (exclusiveFilter){
            postFilterOntologySet = Sets.intersection(filteredOntologySet,new HashSet<String>(ontologies));
            System.out.println("intersection");
        } else {
            postFilterOntologySet = Sets.union(filteredOntologySet,new HashSet<String>(ontologies));
            System.out.println("union");
        }
        if(postFilterOntologySet.size() == 0)
            postFilterOntologySet = new HashSet<String>(Arrays.asList("nosuchontologyfound"));
        return postFilterOntologySet;
    }

    Set<String> union(Collection<String> a, Collection<String> b ) {
        Set<String> union = new HashSet<String>();
        for (String s : a){
            union.add(s);
        }
        for (String s : b){
            union.add(s);
        }
        return union;
    }

    Set<String> intersection(Collection<String> a, Collection<String> b ) {
        Set<String> intersection = new HashSet<String>();
        for (String s1 : a){
            for (String s2 : b){
                if (s1.equals(s2))
                    intersection.add(s1);
            }
        }
        return intersection;
    }

    public Set<V1Ontology> filter(Collection<String> schemas, Collection<String> classifications, boolean exclusive, String lang){
        if(exclusive)
            return exclusiveFilter(schemas,classifications,lang);
        else
            return inclusiveFilter(schemas,classifications,lang);
    }
    public Set<V1Ontology> inclusiveFilter(Collection<String> schemas, Collection<String> classifications, String lang){
        Set<V1Ontology> tempSet = new HashSet<V1Ontology>();
        Set<V1Ontology> filteredSet = new HashSet<V1Ontology>();
/*        Page<V1Ontology> document = getAll(lang, pageable);
        tempSet.addAll(document.getContent());
        while(document.hasNext()){
            pageable = pageable.next();
            document = getAll(lang, pageable);
            tempSet.addAll(document.getContent());
        }*/
        tempSet.addAll(getSet(lang));

        for (V1Ontology ontology : tempSet){
            for (Field field : ontology.config.getClass().getDeclaredFields()){
                if (schemas.contains(field.getName())){
                    try {
                        if(field.get(ontology.config) != null)
                            if (Collection.class.isAssignableFrom(field.getType())) {
                                for (String ontologyClassification : (Collection<String>) field.get(ontology.config)){
                                    if(classifications.contains(ontologyClassification))
                                        filteredSet.add(ontology);
                                }
                            } else if (String.class.isAssignableFrom(field.getType())) {
                                if(field.get(ontology.config) != null)
                                    if(classifications.contains(field.get(ontology.config)))
                                        filteredSet.add(ontology);
                            }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return filteredSet;
    }

    public Set<V1Ontology> exclusiveFilter(Collection<String> schemas, Collection<String> classifications, String lang){
        Set<V1Ontology> tempSet = new HashSet<V1Ontology>();
        Set<V1Ontology> filteredSet = new HashSet<V1Ontology>();
/*        Page<V1Ontology> document = getAll(lang, pageable);
        tempSet.addAll(document.getContent());
        while(document.hasNext()){
            pageable = pageable.next();
            document = getAll(lang, pageable);
            tempSet.addAll(document.getContent());
        }*/
        tempSet.addAll(getSet(lang));

        for (V1Ontology ontology : tempSet){
            Set<String> fieldSet = new HashSet<>();
            for (Field field : ontology.config.getClass().getDeclaredFields()){
                fieldSet.add(field.getName());
            }
            if (fieldSet.containsAll(schemas)){
                Set<String> tempClassifications = new HashSet<String>();
                for (Field field : ontology.config.getClass().getDeclaredFields()){
                    if (Collection.class.isAssignableFrom(field.getType())){
                        try {
                            if(field.get(ontology.config) != null)
                                for (String classification :  classifications){
                                    if(((Collection<String>) field.get(ontology.config)).contains(classification))
                                        tempClassifications.add(classification);
                                }

                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    else if (String.class.isAssignableFrom(field.getType())) {
                        try {
                            if(field.get(ontology.config) != null)
                                if(classifications.contains((String) field.get(ontology.config)))
                                    tempClassifications.add( (String) field.get(ontology.config));
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }

                }
                if(tempClassifications.containsAll(classifications))
                    filteredSet.add(ontology);
            }
        }
        return filteredSet;
    }

}
