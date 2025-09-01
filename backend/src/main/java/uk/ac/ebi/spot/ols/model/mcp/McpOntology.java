
package uk.ac.ebi.spot.ols.model.mcp;

import java.util.List;

import com.google.gson.JsonElement;
import uk.ac.ebi.spot.ols.JsonHelper;

public class McpOntology {

    public String ontologyId;
    public List<String> label;
    public List<String> definition;

    public static McpOntology fromJson(JsonElement json) {
        var object = json.getAsJsonObject();
        var mc = new McpOntology();
        mc.ontologyId = JsonHelper.getString(object, "ontologyId");
        mc.label = JsonHelper.getStrings(object, "label");
        mc.definition = JsonHelper.getStrings(object, "definition");
        return mc;
    }

    
}
