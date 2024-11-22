package uk.ac.ebi.spot.ols.repository.v1;

import static uk.ac.ebi.ols.shared.DefinedFields.HAS_DIRECT_CHILDREN;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * @author Deepan Anbalagan
 * @email deepan.anbalagan@tib.eu
 * TIB-Leibniz Information Center for Science and Technology
 */
public class V1FullJsTreeBuilder {

    JsonObject thisEntity;
    List<String> parentRelationIRIs;
    Set<JsonElement> entities = new LinkedHashSet<>();
    Map<String, JsonElement> entityIriToEntity = new HashMap<>();
    Multimap<String, String> entityIriToChildIris = HashMultimap.create();
    Set<String> toBeOpenedIris = new HashSet<>();

    public V1FullJsTreeBuilder(JsonElement thisEntity, List<JsonElement> ancestors, List<String> parentRelationIRIs) {

        this.thisEntity = thisEntity.getAsJsonObject();
        this.parentRelationIRIs = parentRelationIRIs;

        // 1. put all entities (this entity + all ancestors) into an ordered set

        entities.add(thisEntity);
        String thisEntityIri = (String) thisEntity.getAsJsonObject().getAsJsonPrimitive("iri").getAsString();
        ancestors.parallelStream()
        		.filter(element -> {
        			return !((String) element.getAsJsonObject().getAsJsonPrimitive("iri").getAsString()).equals(thisEntityIri);
        		})
				.forEach(entities::add);

        // 2. establish map of IRI -> entity

        for(JsonElement entity : entities) {
            entityIriToEntity.put((String) entity.getAsJsonObject().getAsJsonPrimitive("iri").getAsString(), entity);
        }

        // 3. establish map of IRI -> children

        for(String entityIri : entityIriToEntity.keySet()) {

            JsonElement entity = entityIriToEntity.get(entityIri);

            for (String parentIri : getEntityParentIRIs(entity)) {
                entityIriToChildIris.put(parentIri, entity.getAsJsonObject().get("iri").getAsString());
            }
        }
        
        // 4. Get all Iri which needs to be opened
        getAllIrisToBeOpen();
    }

    private void getAllIrisToBeOpen() {
    	Set<String> unVisitedKeys = entityIriToChildIris.keySet();
    	String selectedEntityIri = JsonHelper.getString(thisEntity, "iri");
    	
    	for(String key : unVisitedKeys) {
    		// Check if the current key or any of its descendants contain the selectedEntityIri
            if (checkIrisTobeOpen(key, selectedEntityIri)) {
                toBeOpenedIris.add(key);
            }
    	}
	}
    
    private boolean checkIrisTobeOpen(String key, String selectedEntityIri) {
    	
    	// Check if the current key directly contains the selectedEntityIri
        if (entityIriToChildIris.get(key).contains(selectedEntityIri)) {
            toBeOpenedIris.add(key);
            return true;
        }
        
        // Recursively check children for the selectedEntityIri
        for (String childKey : entityIriToChildIris.get(key)) {
            if (checkIrisTobeOpen(childKey, selectedEntityIri)) {
                toBeOpenedIris.add(key);  
                return true;
            }
        }
    	
    	return false;
    }

	List<Map<String,Object>> buildJsTree() {

        // 1. establish roots (entities with no parents)

        List<JsonElement> roots = entities.stream()
                .filter(entity -> getEntityParentIRIs(entity).size() == 0)
                .collect(Collectors.toList());

        // 2. build jstree entries starting with roots

        List<Map<String,Object>> jstree = new ArrayList<>();

        for(JsonElement root : roots) {
            createJsTreeEntries(jstree, root.getAsJsonObject(), null);
        }
        
        // 3. Retrieve parentIds which are not opened but has children nodes
        Set<String> parentIdsToBeRemoved = new HashSet<>();
        for (Map<String, Object> tree : jstree) {
            // Check if the current tree map has a "parent" key that is not "#"
            if (tree.containsKey("parent") && !"#".equals(tree.get("parent"))) {
                String parentValue = (String) tree.get("parent");
                
                // Find entries with matching "id" and where "opened" is false
                jstree.stream()
                      .filter(tmpTree -> parentValue.equals(tmpTree.get("id")))
                      .filter(tmpTree -> {
                          Map<String, Boolean> state = (Map<String, Boolean>) tmpTree.get("state");
                          return state != null && Boolean.FALSE.equals(state.get("opened"));
                      })
                      .map(tmpTree -> (String) tmpTree.get("id"))
                      .forEach(parentIdsToBeRemoved::add);
            }
        }
        
        // 4. Remove nodes which has parentIds retrieved in previous step(Step 3)
        jstree.removeIf(map -> map.entrySet()
        						  .stream()
        						  .anyMatch(entry -> "parent".equals(entry.getKey()) && parentIdsToBeRemoved.contains(entry.getValue()))
        			   );
        
        return jstree;
    }

    private void createJsTreeEntries(List<Map<String,Object>> jstree, JsonObject entity, String concatenatedParentIris) {

        String entityIri = JsonHelper.getString(entity, "iri");

        Map<String,Object> jstreeEntry = new LinkedHashMap<>();

        if(concatenatedParentIris != null) {
            jstreeEntry.put("id", base64Encode(concatenatedParentIris + ";" + entityIri));
            jstreeEntry.put("parent", base64Encode(concatenatedParentIris));
        } else {
            jstreeEntry.put("id", base64Encode(entityIri));
            jstreeEntry.put("parent", "#");
        }

        jstreeEntry.put("iri", entityIri);
        jstreeEntry.put("text", JsonHelper.getString(entity, "label"));

        Collection<String> childIris = entityIriToChildIris.get(entityIri);

        // only the leaf node is selected (= highlighted in the tree)
        boolean selected = JsonHelper.getString(thisEntity, "iri").equals(entityIri);

        // only nodes that aren't the leaf node are marked as opened (expanded)
        boolean opened = toBeOpenedIris.contains(entityIri);


        boolean hasDirectChildren = Objects.equals(JsonHelper.getString(entity, HAS_DIRECT_CHILDREN.getText()), "true");
        boolean hasHierarchicalChildren = Objects.equals(JsonHelper.getString(entity, HAS_DIRECT_CHILDREN.getText()), "true");

        // only nodes that aren't already opened are marked as having children, (iff they actually have children!)
        boolean children = (hasDirectChildren || hasHierarchicalChildren);

        Map<String,Boolean> state = new LinkedHashMap<>();
        state.put("opened", opened);
        state.put("selected", selected);

        jstreeEntry.put("state", state);
        jstreeEntry.put("children", children);

        Map<String,Object> attrObj = new LinkedHashMap<>();
        attrObj.put("iri", JsonHelper.getString(entity, "iri"));
        attrObj.put("ontology_name", JsonHelper.getString(entity, "ontologyId"));
        attrObj.put("title", JsonHelper.getString(entity, "iri"));
        attrObj.put("class", "is_a");
        jstreeEntry.put("a_attr", attrObj);

        jstreeEntry.put("ontology_name", JsonHelper.getString(entity, "ontologyId"));

        jstree.add(jstreeEntry);

        for(String childIri : childIris) {

            JsonElement child = entityIriToEntity.get(childIri);

            if(child == null) {
                // child is not in this tree (i.e. cousin of the node requested, will not be displayed)
                continue;
            }

            if(concatenatedParentIris != null) {
                createJsTreeEntries(jstree, child.getAsJsonObject(), concatenatedParentIris + ";" + entityIri);
            } else {
                createJsTreeEntries(jstree, child.getAsJsonObject(), entityIri);
            }
        }
    }

    private Set<String> getEntityParentIRIs(JsonElement entity) {

        List<JsonElement> parents = new ArrayList<>();

        for(String parentRelationIri : parentRelationIRIs) {
            parents.addAll( JsonHelper.getValues(entity.getAsJsonObject(), parentRelationIri) );
        }

        Set<String> parentIris = new LinkedHashSet<>();

        for (JsonElement parent : parents) {

            // extract value from reified parents
            while(parent.isJsonObject()) {
                parent = parent.getAsJsonObject().get("value");
            }

            String parentIri = parent.getAsString();

            if(parentIri.equals("http://www.w3.org/2002/07/owl#Thing")
                    || parentIri.equals("http://www.w3.org/2002/07/owl#TopObjectProperty")) {
                continue;
            }

            parentIris.add(parentIri);
        }

        return parentIris;
    }

    static String base64Encode(String str) {
        return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }
}

