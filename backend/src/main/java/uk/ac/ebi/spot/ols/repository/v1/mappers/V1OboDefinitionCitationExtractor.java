package uk.ac.ebi.spot.ols.repository.v1.mappers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import uk.ac.ebi.spot.ols.model.v1.V1OboDefinitionCitation;
import uk.ac.ebi.spot.ols.model.v1.V1OboXref;
import uk.ac.ebi.spot.ols.repository.v1.JsonHelper;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class V1OboDefinitionCitationExtractor {

    public static List<V1OboDefinitionCitation> extractFromJson(JsonObject json) {

        JsonObject linkedEntities = json.get("linkedEntities").getAsJsonObject();

        List<JsonElement> definitions = JsonHelper.getValues(json, DEFINITION.getText());
        List<V1OboDefinitionCitation> res = new ArrayList<>();

        for(JsonElement def : definitions) {

            if(def.isJsonObject()) {

                JsonObject defObj = def.getAsJsonObject();

                String definition = JsonHelper.getString(defObj, "value");

                List<JsonObject> axioms = JsonHelper.getObjects(defObj, "axioms");

                for(JsonObject axiom : axioms) {

                    List<JsonElement> xrefs = JsonHelper.getValues(axiom, "http://www.geneontology.org/formats/oboInOwl#hasDbXref");

                    if(xrefs.size() > 0) {

                        V1OboDefinitionCitation citation = new V1OboDefinitionCitation();
                        citation.definition = definition;
                        citation.oboXrefs = xrefs.stream().map(xref -> V1OboXref.fromString(JsonHelper.objectToString(xref), linkedEntities)).collect(Collectors.toList());
                        res.add(citation);
                    }
                }
            }
        }

        if(res.size() == 0) {
            return null;
        }

        return res;
    }
}
