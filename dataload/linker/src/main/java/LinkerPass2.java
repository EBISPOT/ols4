import com.google.common.io.CountingInputStream;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class LinkerPass2 {

    public static class LinkerPass2Result {
        Map<String, Set<String>> ontologyIdToReferencedOntologyIds = new HashMap<>();
    }

    public static LinkerPass2Result run(String inputJsonFilename, LinkerPass1.LinkerPass1Result pass1Result) throws IOException {

        LinkerPass2Result result = new LinkerPass2Result();

        JsonReader jsonReader = new JsonReader(new InputStreamReader(new FileInputStream(inputJsonFilename)));

        jsonReader.beginObject();

        while (jsonReader.peek() != JsonToken.END_OBJECT) {

            String name = jsonReader.nextName();

            if (name.equals("ontologies")) {

                jsonReader.beginArray();

                while (jsonReader.peek() != JsonToken.END_ARRAY) {

                    jsonReader.beginObject(); // ontology

                    String ontologyIdName = jsonReader.nextName();
                    if(!ontologyIdName.equals("ontologyId")) {
                        throw new RuntimeException("the json is bad; ontologyId should alwyas come first");
                    }
                    String ontologyId = jsonReader.nextString();

                    parseObject(jsonReader, ontologyId, pass1Result, result);

                    jsonReader.endObject(); // ontology
                }

                jsonReader.endArray();

            } else {

                jsonReader.skipValue();

            }
        }

        jsonReader.endObject();
        jsonReader.close();

        return result;
    }

    public static void parseObject(JsonReader jsonReader, String ontologyId, LinkerPass1.LinkerPass1Result pass1Result, LinkerPass2Result result) throws IOException {

        while(jsonReader.peek() != JsonToken.END_OBJECT) {

            jsonReader.nextName();

            parseValue(jsonReader, ontologyId, pass1Result, result);
        }
    }

    public static void parseValue(JsonReader jsonReader, String ontologyId, LinkerPass1.LinkerPass1Result pass1Result, LinkerPass2Result result) throws IOException {

        switch(jsonReader.peek()) {
            case BEGIN_ARRAY:
                parseArray(jsonReader, ontologyId, pass1Result, result);
                break;
            case BEGIN_OBJECT:
                parseObject(jsonReader, ontologyId, pass1Result, result);
                break;
            case STRING:
                parseString(jsonReader, ontologyId, pass1Result, result);
                break;
            case BOOLEAN:
            case NUMBER:
            case NULL:
                break;
            default:
                throw new RuntimeException("invalid json");
        }
    }

    public static void parseArray(JsonReader jsonReader, String ontologyId, LinkerPass1.LinkerPass1Result pass1Result, LinkerPass2Result result) throws IOException {
        while(jsonReader.peek() != JsonToken.END_ARRAY) {
            parseValue(jsonReader, ontologyId, pass1Result, result);
        }
    }

    public static void parseString(JsonReader jsonReader, String ontologyId, LinkerPass1.LinkerPass1Result pass1Result, LinkerPass2Result result) throws IOException {

        String str = jsonReader.nextString();

        Set<String> ontologyIds = pass1Result.iriToOntologyIds.get(str);


    }
}
