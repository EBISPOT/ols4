package uk.ac.ebi.spot.ols.model.mcp;

import java.util.List;

import com.google.gson.JsonElement;

import uk.ac.ebi.spot.ols.JsonHelper;

public class McpEntityReference {
    
    public String iri;
    public List<String> definedBy;
    public List<String> label;

    public static McpEntityReference fromJson(JsonElement linkedEntity) {
        var object = linkedEntity.getAsJsonObject();
        var mc = new McpEntityReference();
        mc.iri = JsonHelper.getString(object, "iri");
        mc.definedBy = JsonHelper.getStrings(object, "definedBy");
        mc.label = JsonHelper.getStrings(object, "label");
        return mc;
    }

}