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
        Map<String, Set<EntityReference>> iriToOntologies = new HashMap<>();

        public static class Ontology {
            String ontologyId;
            public long fileOffset;
            public long size;
        }
    }

    /*
    Scan through the JSON and make two maps:
    ontology ID -> (file offset in the JSON to read just that single ontology, ontology size)
    IRI -> set of ontology IDs that define that IRI and whether or not they are the defining ontology.
    */
    public static LinkerPass1Result run(String inputJsonFilename) throws IOException {

        LinkerPass1Result result = new LinkerPass1Result();

        CountingInputStream is = new CountingInputStream(new FileInputStream(inputJsonFilename));
        InputStreamReader reader = new InputStreamReader(is);
        JsonReader jsonReader = new JsonReader(reader);

        int nOntologies = 0;

        System.out.println("--- Linker Pass 1: Scanning " + inputJsonFilename + " to construct IRI->ontologies map");

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
                        throw new RuntimeException("the json is not formatted correctly; ontologyId should always come first");
                    }
                    String ontologyId = jsonReader.nextString();

                    ++ nOntologies;
                    System.out.println("Scanning ontology: " + ontologyId + " (" + nOntologies + " ontologies and " + result.iriToOntologies.size() + " IRIs so far)");

                    LinkerPass1Result.Ontology ontology = new LinkerPass1Result.Ontology();
                    ontology.ontologyId = ontologyId;
                    ontology.fileOffset = offset;

                    while(jsonReader.peek() != JsonToken.END_OBJECT) {
                        String key = jsonReader.nextName();

                        if(key.equals("classes")) {
                            parseEntityArray(jsonReader, "class", ontologyId, result);
                            continue;
                        } else if(key.equals("properties")) {
                            parseEntityArray(jsonReader, "property", ontologyId, result);
                            continue;
                        } else if(key.equals("individuals")) {
                            parseEntityArray(jsonReader, "individual", ontologyId, result);
                            continue;
                        } else {
                            jsonReader.skipValue();
                        }
                    }

                    ontology.size = is.getCount() - offset;
                    jsonReader.endObject(); // ontology

                    System.out.println("Ontology " + ontologyId + " scan complete; file offset " + ontology.fileOffset + ", size " + ontology.size + " bytes");
                    System.out.println("Now have " + nOntologies + " ontologies and " + result.iriToOntologies.size() + " IRIs");

                    result.ontologies.put(ontologyId, ontology);
                }

                jsonReader.endArray();

            } else {

                jsonReader.skipValue();

            }
        }

        jsonReader.endObject();
        jsonReader.close();

        System.out.println("--- Linker Pass 1 complete. Found " + nOntologies + " ontologies and " + result.iriToOntologies.size() + " IRIs");

        return result;
    }

    public static void parseEntityArray(JsonReader jsonReader, String entityType, String ontologyId, LinkerPass1Result result) throws IOException {
        jsonReader.beginArray();

        while(jsonReader.peek() != JsonToken.END_ARRAY) {
            parseEntity(jsonReader, entityType, ontologyId, result);
        }

        jsonReader.endArray();
    }

    public static void parseEntity(JsonReader jsonReader, String entityType, String ontologyId, LinkerPass1Result result) throws IOException {
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

        EntityReference iriToOntology = new EntityReference();
        iriToOntology.ontologyId = ontologyId;
        iriToOntology.entityType = entityType;
        iriToOntology.isDefiningOntology = isDefining;

        Set<EntityReference> entrySet = result.iriToOntologies.get(iri);

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
