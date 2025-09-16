package uk.ac.ebi.spot.ols.model.mcp;

import com.google.gson.JsonElement;

import uk.ac.ebi.spot.ols.JsonHelper;

// To match OpenAI requirements
// https://platform.openai.com/docs/mcp#create-an-mcp-server

public class McpSearchResult {

    public String id;
    public String title;
    public String url;
    
    public static McpSearchResult fromJson(JsonElement entity) {
        var object = entity.getAsJsonObject();
        var mc = new McpSearchResult();

        mc.id = JsonHelper.getString(object, "ontologyId") + "+" + JsonHelper.getString(object, "iri");
        mc.url = JsonHelper.getString(object, "iri");
        mc.title = JsonHelper.getString(object, "curie") + " " + JsonHelper.getString(object, "label");

        return mc;
    }
}
