package uk.ac.ebi.spot.ols.model.mcp;

import java.util.List;
import com.google.gson.JsonElement;
import uk.ac.ebi.spot.ols.JsonHelper;

public class McpClass {

    public String ontologyId;
    public List<String> type;
    public String iri;
    public String curie;
    public List<String> label;
    public List<String> definition;
    public boolean isObsolete;
    public List<McpEntityReference> directAncestor;
    public List<McpEntityReference> directParent;
    public List<McpEntityReference> hierarchicalParent;

    public static McpClass fromJson(JsonElement entity) {
        var object = entity.getAsJsonObject();
        var mc = new McpClass();
        mc.type = JsonHelper.getStrings(object, "type");
        mc.iri = JsonHelper.getString(object, "iri");
        mc.curie = JsonHelper.getString(object, "curie");
        mc.ontologyId = JsonHelper.getString(object, "ontologyId");
        mc.label = JsonHelper.getStrings(object, "label");
        mc.definition = JsonHelper.getStrings(object, "definition");
        mc.isObsolete = Boolean.parseBoolean(JsonHelper.getString(object, "isObsolete"));

        mc.directAncestor = JsonHelper.getObjects(object, "directAncestor")
                .stream().map(McpEntityReference::fromJson).toList();

        mc.directParent = JsonHelper.getObjects(object, "directParent")
                .stream().map(McpEntityReference::fromJson).toList();

        mc.hierarchicalParent = JsonHelper.getObjects(object, "hierarchicalParent")
                .stream().map(McpEntityReference::fromJson).toList();

        return mc;
    }

    
}
