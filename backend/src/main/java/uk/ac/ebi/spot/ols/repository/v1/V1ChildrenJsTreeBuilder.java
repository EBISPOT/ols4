package uk.ac.ebi.spot.ols.repository.v1;

import java.util.*;

public class V1ChildrenJsTreeBuilder {

    String thisEntityJsTreeId;
    Map<String,Object> thisEntity;
    List<String> parentRelationIRIs;
    List<Map<String,Object>> children;

    public V1ChildrenJsTreeBuilder(String thisEntityJsTreeId, Map<String,Object> thisEntity, List<Map<String,Object>> children) {

        this.thisEntityJsTreeId = thisEntityJsTreeId;
        this.thisEntity = thisEntity;
        this.parentRelationIRIs = parentRelationIRIs;
        this.children = children;

    }

    List<Map<String,Object>> buildJsTree() {

        List<Map<String,Object>> jstree = new ArrayList<>();

        for(Map<String,Object> child : children) {

            Map<String,Object> jstreeEntry = new LinkedHashMap<>();
            jstreeEntry.put("id", thisEntityJsTreeId + ";" + child.get("iri"));
            jstreeEntry.put("parent", thisEntityJsTreeId);
            jstreeEntry.put("iri", child.get("iri"));
            jstreeEntry.put("text", child.get("label"));
            jstreeEntry.put("state", Map.of("opened", false));
            jstreeEntry.put("children", child.get("hasChildren").equals("true"));

            Map<String,Object> attrObj = new LinkedHashMap<>();
            attrObj.put("iri", child.get("iri"));
            attrObj.put("ontology_name", child.get("ontologyId"));
            attrObj.put("title", child.get("iri"));
            attrObj.put("class", "is_a");
            jstreeEntry.put("a_attr", attrObj);

            jstreeEntry.put("ontology_name", child.get("ontologyId"));
        }

        return jstree;
    }
}

