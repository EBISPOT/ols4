package uk.ac.ebi.spot.ols.model.mcp;

import java.util.Map;

import com.google.gson.JsonElement;

import uk.ac.ebi.spot.ols.JsonHelper;

// To match OpenAI requirements
// https://platform.openai.com/docs/mcp#create-an-mcp-server

public class McpFetchResult {

    public String id;
    public String title;
    public String text;
    public String url;
    public Object metadata;
    
    public static McpFetchResult fromJson(JsonElement entity) {
        var object = entity.getAsJsonObject();
        var mc = new McpFetchResult();

        mc.id = JsonHelper.getString(object, "ontologyId") + "+" + JsonHelper.getString(object, "iri");
        mc.url = JsonHelper.getString(object, "iri");
        mc.title = JsonHelper.getString(object, "curie") + " " + JsonHelper.getString(object, "label");
        mc.text = JsonHelper.getString(object, "definition");

        var type = JsonHelper.getString(object, "type");
        if(type == "class") {
            mc.metadata = McpClass.fromJson(entity);
        } else {
            mc.metadata = Map.of("type", type);
        }

        return mc;
    }
}
