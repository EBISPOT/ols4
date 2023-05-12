import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

public class JsonHelper {

    public static List<String> jsonArrayToStrings(JsonArray arr) {
        List<String> strs = new ArrayList<>();
        for(int i = 0; i < arr.size(); ++ i) {
            if(arr.get(i).isJsonPrimitive()) {
                strs.add(arr.get(i).getAsString());
            }
        }
        return strs;
    }

    public static String getFirstStringValue(JsonElement json) {
        if(json.isJsonArray()) {
            return getFirstStringValue( json.getAsJsonArray().get(0) );
        } else if(json.isJsonObject()) {
            return getFirstStringValue( json.getAsJsonObject().get("value") );
        } else {
            return json.getAsString();
        }
    }

    public static List<String> getStringOrListOfStrings(JsonElement json) {
        if(json.isJsonArray()) {
            return jsonArrayToStrings(json.getAsJsonArray());
        } else {
            return List.of(json.getAsString());
        }
    }

}
