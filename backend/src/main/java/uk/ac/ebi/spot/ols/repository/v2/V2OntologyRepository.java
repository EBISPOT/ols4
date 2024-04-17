
package uk.ac.ebi.spot.ols.repository.v2;

import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.internal.LinkedTreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.FilterOption;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;
import uk.ac.ebi.spot.ols.repository.neo4j.OlsNeo4jClient;
import uk.ac.ebi.spot.ols.repository.solr.SearchType;
import uk.ac.ebi.spot.ols.repository.solr.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrQuery;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.repository.Validation;
import uk.ac.ebi.spot.ols.repository.transforms.LocalizationTransform;
import uk.ac.ebi.spot.ols.repository.transforms.RemoveLiteralDatatypesTransform;
import uk.ac.ebi.spot.ols.repository.v2.helpers.V2DynamicFilterParser;
import uk.ac.ebi.spot.ols.repository.v2.helpers.V2SearchFieldsParser;
import java.util.*;
import java.io.IOException;


@Component
public class V2OntologyRepository {

    @Autowired
    OlsSolrClient solrClient;

    @Autowired
    OlsNeo4jClient neo4jClient;


    public OlsFacetedResultsPage<V2Entity> find(
            Pageable pageable, String lang, String search, String searchFields, String boostFields, boolean exactMatch, Map<String, Collection<String>> properties, Collection<String> schemas,Collection<String> classifications,Collection ontologies,boolean exclusive,FilterOption filterOption) throws IOException {

        Validation.validateLang(lang);

        if(search != null && searchFields == null) {
            searchFields = "label^100 ontologyId^100 definition";
        }

        OlsSolrQuery query = new OlsSolrQuery();

        query.setSearchText(search);
        query.setExactMatch(exactMatch);
        query.addFilter("type", List.of("ontology"), SearchType.WHOLE_FIELD);
        System.out.println("0");
        Collection<String> filteredOntologies = filterOntologyIDs(schemas,classifications, ontologies, exclusive, filterOption, lang);
        if(filteredOntologies != null){
            for (String ontologyId : filteredOntologies)
                Validation.validateOntologyId(ontologyId);
            query.addFilter("ontologyId",filteredOntologies, SearchType.CASE_INSENSITIVE_TOKENS);
        }

        V2SearchFieldsParser.addSearchFieldsToQuery(query, searchFields);
        V2SearchFieldsParser.addBoostFieldsToQuery(query, boostFields);
        V2DynamicFilterParser.addDynamicFiltersToQuery(query, properties);

        return solrClient.searchSolrPaginated(query, pageable)
                .map(e -> LocalizationTransform.transform(e, lang))
                .map(RemoveLiteralDatatypesTransform::transform)
                .map(V2Entity::new);
    }

    public V2Entity getById(String ontologyId, String lang) throws ResourceNotFoundException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        OlsSolrQuery query = new OlsSolrQuery();

        query.addFilter("type", List.of("ontology"), SearchType.WHOLE_FIELD);
        query.addFilter("ontologyId", List.of(ontologyId), SearchType.CASE_INSENSITIVE_TOKENS);

        return new V2Entity(
                LocalizationTransform.transform(
                        RemoveLiteralDatatypesTransform.transform(
                                solrClient.getFirst(query)
                        ),
                        lang
                )
        );
    }

    public Set<V2Entity> getOntologies(String lang){
        Set<V2Entity> entities = new HashSet<>();
        OlsSolrQuery query = new OlsSolrQuery();

        query.addFilter("type", List.of("ontology"), SearchType.WHOLE_FIELD);
        for (JsonElement element : solrClient.getSet(query))
            entities.add(new V2Entity(
                    LocalizationTransform.transform(
                            RemoveLiteralDatatypesTransform.transform(
                                    element
                            ),
                            lang
                    )
            ));
        return entities;
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
        Set<V2Entity> documents;
        if(FilterOption.COMPOSITE == filterOption)
            documents = filterComposite(schemas, classifications, exclusiveFilter,lang);
        else if (FilterOption.LINEAR == filterOption)
            documents = filter(schemas, classifications, exclusiveFilter,lang);
        else
            documents = filterLicense(schemas, classifications, exclusiveFilter,lang);
        Set<String> filteredOntologySet = new HashSet<String>();
        for (V2Entity document : documents){
            filteredOntologySet.add(document.any().get("ontologyId").toString());
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

    public Set<V2Entity> filterComposite(Collection<String> schemas, Collection<String> classifications, boolean exclusive, String lang){
        Set<V2Entity> tempSet = new HashSet<V2Entity>();
        if(schemas != null && classifications != null)
            if(!exclusive) {
                for (V2Entity ontologyDocument : getOntologies(lang)) {
                    for(Map<String, Collection<String>> classificationSchema : (Collection<Map<String, Collection<String>>>) ontologyDocument.any().get("classifications")) {
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
                for (V2Entity ontologyDocument : getOntologies(lang)){
                    for(Map<String, Collection<String>> classificationSchema : (Collection<Map<String, Collection<String>>>) ontologyDocument.any().get("classifications")){
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
                for (V2Entity ontologyDocument : getOntologies(lang)) {
                    Set<String> tempClassifications = new HashSet<String>();
                    if(ontologyDocument.any().get("classifications") != null)
                        if (!((Collection<Map<String, Collection<String>>>) ontologyDocument.any().get("classifications")).isEmpty()) {
                            for (Map<String, Collection<String>> classificationSchema : (Collection<Map<String, Collection<String>>>) ontologyDocument.any().get("classifications")) {
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

    public Set<V2Entity> filter(Collection<String> schemas, Collection<String> classifications, boolean exclusive, String lang){
        if(exclusive)
            return exclusiveFilter(schemas,classifications,lang);
        else
            return inclusiveFilter(schemas,classifications,lang);
    }

    public Set<V2Entity> inclusiveFilter(Collection<String> schemas, Collection<String> classifications, String lang){
        Set<V2Entity> tempSet = new HashSet<V2Entity>();
        Set<V2Entity> filteredSet = new HashSet<V2Entity>();
        tempSet.addAll(getOntologies(lang));

        for (V2Entity ontology : tempSet){
            for (String key : ontology.any().keySet()){
                if (schemas.contains(key)){
                    if(ontology.any().get(key) != null)
                        if (ontology.any().get(key) instanceof Collection) {
                            for (String ontologyClassification : (Collection<String>) ontology.any().get(key)){
                                if(classifications.contains(ontologyClassification))
                                    filteredSet.add(ontology);
                            }
                        } else if (ontology.any().get(key) instanceof String) {
                            if(ontology.any().get(key) != null)
                                if(classifications.contains(ontology.any().get(key)))
                                    filteredSet.add(ontology);
                        }
                }
            }
        }
        return filteredSet;
    }

    public Set<V2Entity> exclusiveFilter(Collection<String> schemas, Collection<String> classifications, String lang){
        Set<V2Entity> tempSet = new HashSet<V2Entity>();
        Set<V2Entity> filteredSet = new HashSet<V2Entity>();
        tempSet.addAll(getOntologies(lang));

        for (V2Entity ontology : tempSet){
            Set<String> fieldSet =ontology.any().keySet();
            if (fieldSet.containsAll(schemas)){
                Set<String> tempClassifications = new HashSet<String>();
                for (String key : ontology.any().keySet()){
                    if (ontology.any().get(key) instanceof Collection){
                        if(ontology.any().get(key) != null)
                            for (String classification :  classifications){
                                if(((Collection<String>) ontology.any().get(key)).contains(classification))
                                    tempClassifications.add(classification);
                            }
                    } else if (ontology.any().get(key) instanceof String) {
                        if(ontology.any().get(key) != null)
                            if(classifications.contains((String) ontology.any().get(key)))
                                tempClassifications.add( (String) ontology.any().get(key));
                    }
                }
                if(tempClassifications.containsAll(classifications))
                    filteredSet.add(ontology);
            }
        }
        return filteredSet;
    }

    public Set<V2Entity> filterLicense(Collection<String> schemas, Collection<String> classifications, boolean exclusive, String lang){
        Set<V2Entity> tempSet = new HashSet<V2Entity>();
        Set<V2Entity> filteredSet = new HashSet<V2Entity>();
        tempSet.addAll(getOntologies(lang));

        for (V2Entity ontology : tempSet){
            if (ontology.any().keySet().contains("license")){
                LinkedTreeMap<String, Object> license = (LinkedTreeMap<String, Object>) ontology.any().get("license");
                String label = license.get("label") != null ? (String) license.get("label") : "";
                String logo = license.get("logo") != null ? (String) license.get("logo") : "";
                String url = license.get("url") != null ? (String) license.get("url") : "";
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
        Set<V2Entity> tempSet = new HashSet<V2Entity>();
        tempSet.addAll(getOntologies(lang));
        Set<String> keys = new HashSet<>();
        for (V2Entity ontology : tempSet){
            if (ontology.any().containsKey("classifications")){
                Collection<Object> temp = (Collection<Object>) ontology.any().get("classifications");
                for (Object o : temp){
                    keys.addAll(((Map<String, Collection<Object>>) o).keySet());
                }
            }
        }
        return keys;
    }

    public Set<String> getSchemaValues(Collection<String> schemas,String lang){
        Set<V2Entity> tempSet = new HashSet<V2Entity>();
        tempSet.addAll(getOntologies(lang));
        Set<String> values = new HashSet<>();
        for (V2Entity ontology : tempSet){
            if (ontology.any().containsKey("classifications")){
                Collection<Object> temp = (Collection<Object>) ontology.any().get("classifications");
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



