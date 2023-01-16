import com.google.common.io.CountingInputStream;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class LinkerPass1 {

    public static class LinkerPass1Result {
        Map<String, Ontology> ontologies = new HashMap<>();
        Map<String, Set<String>> iriToOntologyIds = new HashMap<>();

        public static class Ontology {
            String ontologyId;
            public long fileOffset;
            public long size;
        }
    }

    public static LinkerPass1Result run(String inputJsonFilename) throws IOException {

        LinkerPass1Result result = new LinkerPass1Result();

        CountingInputStream is = new CountingInputStream(new FileInputStream(inputJsonFilename));
        InputStreamReader reader = new InputStreamReader(is);
        JsonReader jsonReader = new JsonReader(reader);

        jsonReader.beginObject();

        while (jsonReader.peek() != JsonToken.END_OBJECT) {

            String name = jsonReader.nextName();

            if (name.equals("ontologies")) {

                jsonReader.beginArray();

                long offset = is.getCount();

                while (jsonReader.peek() != JsonToken.END_ARRAY) {

                    jsonReader.beginObject(); // ontology

                    String ontologyIdName = jsonReader.nextName();
                    if(!ontologyIdName.equals("ontologyId")) {
                        throw new RuntimeException("the json is bad; ontologyId should alwyas come first");
                    }
                    String ontologyId = jsonReader.nextString();

                    LinkerPass1Result.Ontology ontology = new LinkerPass1Result.Ontology();
                    ontology.ontologyId = ontologyId;
                    ontology.fileOffset = offset;

                    parseObjectProperties(jsonReader, ontologyId, result);

                    ontology.size = is.getCount() - offset;

                    jsonReader.endObject(); // ontology

                    result.ontologies.put(ontologyId, ontology);
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

    public static void parseObjectProperties(JsonReader jsonReader, String ontologyId, LinkerPass1Result result) throws IOException {

        while(jsonReader.peek() != JsonToken.END_OBJECT) {

            String propertyName = jsonReader.nextName();
            addPossibleIri(propertyName, ontologyId, result);

            parseValue(jsonReader, ontologyId, result);
        }
    }

    public static void parseValue(JsonReader jsonReader, String ontologyId, LinkerPass1Result result) throws IOException {

        switch(jsonReader.peek()) {
            case BEGIN_ARRAY:
                parseArray(jsonReader, ontologyId, result);
                break;
            case BEGIN_OBJECT:
                jsonReader.beginObject();
                parseObjectProperties(jsonReader, ontologyId, result);
                jsonReader.endObject();
                break;
            case STRING:
                parseString(jsonReader, ontologyId, result);
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

    public static void parseArray(JsonReader jsonReader, String ontologyId, LinkerPass1Result result) throws IOException {
        jsonReader.beginArray();
        while(jsonReader.peek() != JsonToken.END_ARRAY) {
            parseValue(jsonReader, ontologyId, result);
        }
        jsonReader.endArray();
    }

    public static void parseString(JsonReader jsonReader, String ontologyId, LinkerPass1Result result) throws IOException {
        String str = jsonReader.nextString();

        addPossibleIri(str, ontologyId, result);
    }

    public static void addPossibleIri(String str, String ontologyId, LinkerPass1Result result) throws IOException {

        // Very dumb IRI screening but removes much of the noise
        if(!str.contains("://")) {
            return;
        }

        Set<String> found = result.iriToOntologyIds.get(str);

        if(found != null) {
            found.add(ontologyId);
        } else {
            Set<String> s = new HashSet<>();
            s.add(ontologyId);
            result.iriToOntologyIds.put(str, s);
        }
    }

}
