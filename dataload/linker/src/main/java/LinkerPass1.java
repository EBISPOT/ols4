import com.google.common.io.CountingInputStream;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class LinkerPass1 {

    private static final JsonParser jsonParser = new JsonParser();

    public static class LinkerPass1Result {
        Map<String, Ontology> ontologies = new HashMap<>();
        Map<String, Set<LinkerReferencedOntology>> iriToOntologies = new HashMap<>();

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

                while(jsonReader.peek() != JsonToken.END_ARRAY) {
                    long offset = is.getCount();
                    jsonReader.beginObject(); // ontology


                    String ontologyIdName = jsonReader.nextName();
                    if(!ontologyIdName.equals("ontologyId")) {
                        throw new RuntimeException("the json is bad; ontologyId should always come first");
                    }
                    String ontologyId = jsonReader.nextString();

                    LinkerPass1Result.Ontology ontology = new LinkerPass1Result.Ontology();
                    ontology.ontologyId = ontologyId;
                    ontology.fileOffset = offset;

                    while(jsonReader.peek() != JsonToken.END_OBJECT) {
                        String key = jsonReader.nextName();

                        if(key.equals("classes")
                            || key.equals("properties")
                                || key.equals("individuals")) {
                            parseEntityArray(jsonReader, ontologyId, result);
                            continue;
                        } else {
                            jsonReader.skipValue();
                        }
                    }

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

    public static void parseEntityArray(JsonReader jsonReader, String ontologyId, LinkerPass1Result result) throws IOException {
        jsonReader.beginArray();

        while(jsonReader.peek() != JsonToken.END_ARRAY) {
            parseEntity(jsonReader, ontologyId, result);
        }

        jsonReader.endArray();
    }

    public static void parseEntity(JsonReader jsonReader, String ontologyId, LinkerPass1Result result) throws IOException {
        jsonReader.beginObject();

        String iri = null;
        Boolean isDefining = null;

        while(jsonReader.peek() != JsonToken.END_OBJECT) {
            String key = jsonReader.nextName();

            if(key.equals("iri")) {
                iri = jsonReader.nextString();
            } else if(key.equals("isDefiningOntology")) {
                JsonElement elem = jsonParser.parse(jsonReader);
                isDefining = elem.getAsJsonObject().get("value").getAsString().equals("true");
            } else {
                jsonReader.skipValue();
            }
        }

        if(iri == null) {
            throw new RuntimeException("entity had no IRI");
        }

        if(isDefining == null) {
            isDefining = false;
        }

        LinkerReferencedOntology iriToOntology = new LinkerReferencedOntology();
        iriToOntology.ontologyId = ontologyId;
        iriToOntology.isDefiningOntology = isDefining;

        Set<LinkerReferencedOntology> entrySet = result.iriToOntologies.get(iri);

        if(entrySet != null) {
            entrySet.add(iriToOntology);
        } else {
            entrySet = new HashSet<>();
            entrySet.add(iriToOntology);
            result.iriToOntologies.put(iri, entrySet);
        }

        jsonReader.endObject();
    }


}
