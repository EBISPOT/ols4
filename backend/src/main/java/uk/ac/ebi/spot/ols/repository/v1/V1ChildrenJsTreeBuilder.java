package uk.ac.ebi.spot.ols.repository.v1;

import com.google.gson.JsonElement;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static uk.ac.ebi.ols.shared.DefinedFields.*;

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
            jstreeEntry.put("iri", JsonHelper.getString(child.getAsJsonObject(), "iri"));
            jstreeEntry.put("text", JsonHelper.getString(child.getAsJsonObject(), "label"));
            jstreeEntry.put("state", Map.of("opened", false));
            jstreeEntry.put("children",
	    	JsonHelper.getString(child.getAsJsonObject(), HAS_DIRECT_CHILDREN.getText()).equals("true")
		|| JsonHelper.getString(child.getAsJsonObject(), "hasHierarchicalChildren").equals("true")
	    );

            Map<String,Object> attrObj = new LinkedHashMap<>();
            attrObj.put("iri", JsonHelper.getString(child.getAsJsonObject(), "iri"));
            attrObj.put("ontology_name",JsonHelper.getString(child.getAsJsonObject(), "ontologyId"));
            attrObj.put("title", JsonHelper.getString(child.getAsJsonObject(), "iri"));
            attrObj.put("class", "is_a");
            jstreeEntry.put("a_attr", attrObj);

            jstreeEntry.put("ontology_name", JsonHelper.getString(child.getAsJsonObject(), "ontologyId"));
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

