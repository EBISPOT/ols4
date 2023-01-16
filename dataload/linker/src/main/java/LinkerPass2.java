
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class LinkerPass2 {

    public static class LinkerPass2Result {
        Map<String, Set<LinkerReferencedOntology>> ontologyIdToReferencedOntologies = new HashMap<>();
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
                jsonReader.beginArray();
                parseArray(jsonReader, ontologyId, pass1Result, result);
                jsonReader.endArray();
                break;
            case BEGIN_OBJECT:
                jsonReader.beginObject();
                parseObject(jsonReader, ontologyId, pass1Result, result);
                jsonReader.endObject();
                break;
            case STRING:
                parseString(jsonReader, ontologyId, pass1Result, result);
                break;
            case BOOLEAN:
            case NUMBER:
            case NULL:
                jsonReader.skipValue();
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

        Set<LinkerReferencedOntology> ontologies = pass1Result.iriToOntologies.get(str);

        if(ontologies != null) {

            Set<LinkerReferencedOntology> found = result.ontologyIdToReferencedOntologies.get(ontologyId);

            if(found != null) {
                found.addAll(ontologies);
            } else {
                found = new HashSet<>();
                found.addAll(ontologies);
                result.ontologyIdToReferencedOntologies.put(ontologyId, found);
            }
        }
    }
}
