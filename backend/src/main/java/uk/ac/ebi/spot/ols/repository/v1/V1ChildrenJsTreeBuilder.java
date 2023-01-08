package uk.ac.ebi.spot.ols.repository.v1;

import com.google.gson.JsonElement;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class V1ChildrenJsTreeBuilder {

    String thisEntityJsTreeIdDecoded;
    JsonElement thisEntity;
    List<String> parentRelationIRIs;
    List<JsonElement> children;

    public V1ChildrenJsTreeBuilder(String thisEntityJsTreeId, JsonElement thisEntity, List<JsonElement> children) {

        this.thisEntityJsTreeIdDecoded = base64Decode(thisEntityJsTreeId);
        this.thisEntity = thisEntity;
        this.parentRelationIRIs = parentRelationIRIs;
        this.children = children;

    }

    List<Map<String,Object>> buildJsTree() {

        List<Map<String,Object>> jstree = new ArrayList<>();

        for(JsonElement child : children) {

            Map<String,Object> jstreeEntry = new LinkedHashMap<>();
            jstreeEntry.put("id", base64Encode(thisEntityJsTreeIdDecoded + ";" + child.getAsJsonObject().get("iri").getAsString()));
            jstreeEntry.put("parent", base64Encode(thisEntityJsTreeIdDecoded));
            jstreeEntry.put("iri", child.getAsJsonObject().get("iri").getAsString());
            jstreeEntry.put("text", child.getAsJsonObject().get("label").getAsString());
            jstreeEntry.put("state", Map.of("opened", false));
            jstreeEntry.put("children",
	    	child.getAsJsonObject().get("hasDirectChildren").getAsString().equals("true")
		|| child.getAsJsonObject().get("hasHierarchicalChildren").getAsString().equals("true")
	    );

            Map<String,Object> attrObj = new LinkedHashMap<>();
            attrObj.put("iri", child.getAsJsonObject().get("iri").getAsString());
            attrObj.put("ontology_name", child.getAsJsonObject().get("ontologyId").getAsString());
            attrObj.put("title", child.getAsJsonObject().get("iri").getAsString());
            attrObj.put("class", "is_a");
            jstreeEntry.put("a_attr", attrObj);

            jstreeEntry.put("ontology_name", child.getAsJsonObject().get("ontologyId"));
            jstree.add(jstreeEntry);
        }

        return jstree;
    }

    static String base64Encode(String str) {
        return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    static String base64Decode(String str) {
        return new String(Base64.getDecoder().decode(str), StandardCharsets.UTF_8);
    }

}

