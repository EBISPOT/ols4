package uk.ac.ebi.spot.ols.repository.v1;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.*;
import java.util.stream.Collectors;

public class V1AncestorsJsTreeBuilder {

    Map<String,Object> thisEntity;
    List<String> parentRelationIRIs;
    Set<Map<String,Object>> entities = new LinkedHashSet<>();
    Map<String, Map<String,Object>> entityIriToEntity = new HashMap<>();
    Multimap<String, String> entityIriToChildIris = HashMultimap.create();

    public V1AncestorsJsTreeBuilder(Map<String,Object> thisEntity, List<Map<String,Object>> ancestors, List<String> parentRelationIRIs) {

        this.thisEntity = thisEntity;
        this.parentRelationIRIs = parentRelationIRIs;

        // 1. put all entities (this entity + all ancestors) into an ordered set

        entities.add(thisEntity);
        entities.addAll(ancestors);

        // 2. establish map of IRI -> entity

        for(Map<String,Object> entity : entities) {
            entityIriToEntity.put((String) entity.get("iri"), entity);
        }

        // 3. establish map of IRI -> children

        for(String entityIri : entityIriToEntity.keySet()) {

            Map<String,Object> entity = entityIriToEntity.get(entityIri);

            for (String parentIri : getEntityParentIRIs(entity)) {
                entityIriToChildIris.put(parentIri, (String) entity.get("iri"));
            }
        }
    }

    List<Map<String,Object>> buildJsTree() {

        // 1. establish roots (entities with no parents)

        List<Map<String,Object>> roots = entities.stream()
                .filter(entity -> getEntityParentIRIs(entity).size() == 0)
                .collect(Collectors.toList());

        // 2. build jstree entries starting with roots

        List<Map<String,Object>> jstree = new ArrayList<>();

        for(Map<String,Object> root : roots) {
            createJsTreeEntries(jstree, root, null);
        }

        return jstree;
    }

    private void createJsTreeEntries(List<Map<String,Object>> jstree, Map<String,Object> entity, String concatenatedParentIris) {

        Map<String,Object> jstreeEntry = new LinkedHashMap<>();

        if(concatenatedParentIris != null) {
            jstreeEntry.put("id", concatenatedParentIris + ";" + entity.get("iri"));
            jstreeEntry.put("parent", concatenatedParentIris);
        } else {
            jstreeEntry.put("id", entity.get("iri"));
            jstreeEntry.put("parent", "#");
        }

        jstreeEntry.put("iri", entity.get("iri"));
        jstreeEntry.put("text", entity.get("label")); // TODO what in the case of multiple labels?

        Collection<String> childIris = entityIriToChildIris.get((String) entity.get("iri"));

        if(entity.get("iri").equals(this.thisEntity.get("iri"))) {
            jstreeEntry.put("state", Map.of("opened", childIris.size() > 0, "selected", true));
        } else {
            jstreeEntry.put("state", Map.of("opened", childIris.size() > 0));
        }
        jstreeEntry.put("children", childIris.size() > 0);

        Map<String,Object> attrObj = new LinkedHashMap<>();
        attrObj.put("iri", entity.get("iri"));
        attrObj.put("ontology_name", entity.get("ontologyId"));
        attrObj.put("title", entity.get("iri"));
        attrObj.put("class", "is_a");
        jstreeEntry.put("a_attr", attrObj);

        jstreeEntry.put("ontology_name", entity.get("ontologyId"));

        jstree.add(jstreeEntry);

        for(String childIri : childIris) {

            Map<String,Object> child = entityIriToEntity.get(childIri);

            if(child == null) {
                // child is not in this tree (i.e. cousin of the node requested, will not be displayed)
                continue;
            }

            if(concatenatedParentIris != null) {
                createJsTreeEntries(jstree, child, concatenatedParentIris + ";" + entity.get("iri"));
            } else {
                createJsTreeEntries(jstree, child, (String)entity.get("iri"));
            }
        }
    }

    private Set<String> getEntityParentIRIs(Map<String,Object> entity) {

        Set<Object> parents = new LinkedHashSet<>();

        for(String parentRelationIri : parentRelationIRIs) {

            Object parent = entity.get(parentRelationIri);

            if(parent != null) {
                if(parent instanceof List) {
                    parents.addAll((List<Object>) parent);
                } else {
                    parents.add(parent);
                }
            }
        }

        Set<String> parentIris = new LinkedHashSet<>();

        for (Object parent : parents) {

            // extract value from reified parents
            while(parent instanceof Map) {
                parent = ((Map<String,Object>) parent).get("value");
            }

            String parentIri = (String) parent;

            if(parentIri.equals("http://www.w3.org/2002/07/owl#Thing")
                    || parentIri.equals("http://www.w3.org/2002/07/owl#TopObjectProperty")) {
                continue;
            }

            parentIris.add(parentIri);
        }

        return parentIris;
    }
}

