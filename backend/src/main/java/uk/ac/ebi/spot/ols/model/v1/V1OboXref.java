package uk.ac.ebi.spot.ols.model.v1;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import uk.ac.ebi.spot.ols.repository.v1.JsonHelper;

import java.util.Objects;

public class V1OboXref {

    public String database;
    public String id;
    public String description;
    public String url;


    public static V1OboXref fromJson(JsonElement oboXref) {

        if(oboXref.isJsonPrimitive()) {

            String value = oboXref.getAsString();

            if(value.startsWith("http:") || value.startsWith("https:")) {
                V1OboXref xref = new V1OboXref();
                xref.id = value;
                xref.url = value;
                return xref;
            }

            if(value.contains("://")) {
                if(value.matches("^[A-Z]+:.+")) {
                        // e.g. DOI:https://doi.org/10.1378/chest.12-2762
                        V1OboXref xref = new V1OboXref();
                        xref.id = value;
        //                xref.database = oboXref.split(":")[0];
        //                xref.id = oboXref.substring(oboXref.indexOf(':') + 1);
        //                xref.url = oboXref.substring(oboXref.indexOf(':') + 1);
                        return xref;
                }
            }

            String[] tokens = value.split(":");

            if(tokens.length < 2) {
                V1OboXref xref = new V1OboXref();
                xref.id = tokens[0];
                return xref;
            }

            V1OboXref xref = new V1OboXref();
            xref.database = tokens[0];
            xref.id = tokens[1];
            return xref;

        } else if(oboXref.isJsonObject()) {

            JsonObject oboXrefObj = oboXref.getAsJsonObject();

            V1OboXref xref = fromJson(oboXrefObj.get("value"));

            if(oboXrefObj.has("url")) {
                xref.url = oboXrefObj.get("url").getAsString();
            }

            if(oboXrefObj.has("axioms")) {
                JsonArray axioms = oboXrefObj.getAsJsonArray("axioms");
                for(JsonElement axiom : axioms) {
                    JsonObject axiomObj = axiom.getAsJsonObject();
                    if(axiomObj.has("url")) {
                        xref.url = JsonHelper.getString(axiomObj, "url");
                        break;
                    }
                }
            }

            return xref;
        } else {
            throw new RuntimeException("unknown xref type");
        }

    }


    @Override
    public boolean equals(Object other) {

        if(! (other instanceof V1OboXref)) {
            return false;
        }

        return Objects.equals(((V1OboXref) other).id, this.id) &&
                Objects.equals(((V1OboXref) other).description, this.description)  &&
                Objects.equals(((V1OboXref) other).database, this.database)  &&
                Objects.equals(((V1OboXref) other).url, this.url);
    }
}
