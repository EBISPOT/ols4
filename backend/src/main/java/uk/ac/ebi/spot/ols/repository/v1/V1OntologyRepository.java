
package uk.ac.ebi.spot.ols.repository.v1;

import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.FilterOption;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.model.License;
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

    public Set<V1Ontology> getAll(String lang){
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

    public Collection<String> filterOntologyIDs(Collection<String> schemas, Collection<String> classifications, Collection<String> ontologies, boolean exclusiveFilter, FilterOption filterOption, String lang){
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
        Set<V1Ontology> documents;
        if(FilterOption.COMPOSITE == filterOption)
            documents = filterComposite(schemas, classifications, exclusiveFilter,lang);
        else if (FilterOption.LINEAR == filterOption)
            documents = filter(schemas, classifications, exclusiveFilter,lang);
        else
            documents = filterLicense(schemas, classifications, exclusiveFilter,lang);
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

    public Set<V1Ontology> filter(Collection<String> schemas, Collection<String> classifications, boolean exclusive, String lang){
        if(exclusive)
            return exclusiveFilter(schemas,classifications,lang);
        else
            return inclusiveFilter(schemas,classifications,lang);
    }
    public Set<V1Ontology> inclusiveFilter(Collection<String> schemas, Collection<String> classifications, String lang){
        Set<V1Ontology> tempSet = new HashSet<V1Ontology>();
        Set<V1Ontology> filteredSet = new HashSet<V1Ontology>();
        tempSet.addAll(getAll(lang));

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
        tempSet.addAll(getAll(lang));

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

    public Set<V1Ontology> filterComposite(Collection<String> schemas, Collection<String> classifications, boolean exclusive, String lang){
        Set<V1Ontology> tempSet = new HashSet<V1Ontology>();
        if(schemas != null && classifications != null)
            if(!exclusive) {
                for (V1Ontology ontologyDocument : getAll(lang)) {
                    for(Map<String, Collection<String>> classificationSchema : (Collection<Map<String, Collection<String>>>) ontologyDocument.config.classifications) {
                        for (String schema: schemas)
                            if(classificationSchema.containsKey(schema))
                                for (String classification: classifications) {
                                    if (classificationSchema.get(schema) != null)
                                        if (!classificationSchema.get(schema).isEmpty())
                                            if (classificationSchema.get(schema).contains(classification)) {
                                                tempSet.add(ontologyDocument);
                                            }
                                }

                    }
                }
            } else if (exclusive && schemas != null && schemas.size() == 1 && classifications != null && classifications.size() == 1) {
                String schema = schemas.iterator().next();
                String classification = classifications.iterator().next();
                System.out.println("schema: "+schema);
                System.out.println("classification: "+classification);
                for (V1Ontology ontologyDocument : getAll(lang)){
                    for(Map<String, Collection<String>> classificationSchema : (Collection<Map<String, Collection<String>>>) ontologyDocument.config.classifications){
                        if(classificationSchema.containsKey(schema))
                            if (classificationSchema.get(schema) != null)
                                if (!classificationSchema.get(schema).isEmpty()){
                                    for (String s :classificationSchema.get(schema))
                                        System.out.println(s);
                                    if(classificationSchema.get(schema).contains(classification))
                                        tempSet.add(ontologyDocument);
                                }

                    }
                }
            } else {
                for (V1Ontology ontologyDocument : getAll(lang)) {
                    Set<String> tempClassifications = new HashSet<String>();
                    if(ontologyDocument.config.classifications != null)
                        if (!((Collection<Map<String, Collection<String>>>) ontologyDocument.config.classifications).isEmpty()) {
                            for (Map<String, Collection<String>> classificationSchema : (Collection<Map<String, Collection<String>>>) ontologyDocument.config.classifications) {
                                for (String schema : schemas)
                                    if (classificationSchema.containsKey(schema)) {
                                        for (String classification : classifications) {
                                            if (classificationSchema.get(schema) != null) {
                                                if (!classificationSchema.get(schema).isEmpty()) {
                                                    if (classificationSchema.get(schema).contains(classification)) {
                                                        tempClassifications.add(classification);
                                                    }
                                                }
                                            }
                                        }
                                    }
                            }
                            if (tempClassifications.containsAll(classifications))
                                tempSet.add(ontologyDocument);
                        }
                }
            }
        return tempSet;
    }


    public Set<V1Ontology> filterLicense(Collection<String> schemas, Collection<String> classifications, boolean exclusive, String lang){
        Set<V1Ontology> tempSet = new HashSet<V1Ontology>();
        Set<V1Ontology> filteredSet = new HashSet<V1Ontology>();
        tempSet.addAll(getAll(lang));

        for (V1Ontology ontology : tempSet){
            if (ontology.config.license != null){
                License license = ontology.config.license;
                String label = license.getLabel() != null ? (String) license.getLabel() : "";
                String logo = license.getLogo() != null ? (String) license.getLogo() : "";
                String url = license.getUrl() != null ? (String) license.getUrl() : "";
                if (exclusive){
                    Set<String> tempClassifications = new HashSet<String>();
                    if (schemas.contains("license.label") && label.length() > 0 && classifications.contains(label))
                        tempClassifications.add("license.label");
                    if (schemas.contains("license.logo") && logo.length() > 0 && classifications.contains(logo))
                        tempClassifications.add("license.logo");
                    if (schemas.contains("license.url") && url.length() > 0 && classifications.contains(url))
                        tempClassifications.add("license.url");

                    if(tempClassifications.containsAll(classifications))
                        filteredSet.add(ontology);

                } else {
                    if (schemas.contains("license.label") && label.length() > 0 && classifications.contains(label))
                        filteredSet.add(ontology);
                    if (schemas.contains("license.logo") && logo.length() > 0 && classifications.contains(logo))
                        filteredSet.add(ontology);
                    if (schemas.contains("license.url") && url.length() > 0 && classifications.contains(url))
                        filteredSet.add(ontology);
                }
            }
        }

        return filteredSet;
    }

    public Set<String> getSchemaKeys(String lang){
        Set<V1Ontology> tempSet = new HashSet<V1Ontology>();
        tempSet.addAll(getAll(lang));
        Set<String> keys = new HashSet<>();
        for (V1Ontology ontology : tempSet){
            if (ontology.config.classifications != null){
                Collection<Object> temp = (Collection<Object>) ontology.config.classifications;
                for (Object o : temp){
                    keys.addAll(((Map<String, Collection<Object>>) o).keySet());
                }
            }
        }
        return keys;
    }

    public Set<String> getSchemaValues(Collection<String> schemas,String lang){
        Set<V1Ontology> tempSet = new HashSet<V1Ontology>();
        tempSet.addAll(getAll(lang));
        Set<String> values = new HashSet<>();
        for (V1Ontology ontology : tempSet){
            if (ontology.config.classifications != null){
                Collection<Object> temp = (Collection<Object>) ontology.config.classifications;
                for (Object o : temp){
                    for (Map.Entry<String,Collection<String>> entry : ((Map<String, Collection<String>>) o).entrySet())
                        for (String value : entry.getValue())
                            if(schemas.contains(entry.getKey()))
                                values.add(value);
                }
            }
        }
        return values;
    }

}
