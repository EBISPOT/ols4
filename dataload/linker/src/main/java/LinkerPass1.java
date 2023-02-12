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
        Map<String, EntityDefinitionSet> iriToDefinitions = new HashMap<>();
    }

    public static LinkerPass1Result run(String inputJsonFilename) throws IOException {

        LinkerPass1Result result = new LinkerPass1Result();

        FileInputStream is = new FileInputStream(inputJsonFilename);
        InputStreamReader reader = new InputStreamReader(is);
        JsonReader jsonReader = new JsonReader(reader);

        int nOntologies = 0;

        System.out.println("--- Linker Pass 1: Scanning " + inputJsonFilename + " to construct (IRI->ontologies,labels) map");

        jsonReader.beginObject();

        while (jsonReader.peek() != JsonToken.END_OBJECT) {
            String name = jsonReader.nextName();

            if (name.equals("ontologies")) {
                jsonReader.beginArray();

                while(jsonReader.peek() != JsonToken.END_ARRAY) {

                    jsonReader.beginObject(); // ontology

                    String ontologyIdName = jsonReader.nextName();
                    if(!ontologyIdName.equals("ontologyId")) {
                        throw new RuntimeException("the json is not formatted correctly; ontologyId should always come first");
                    }
                    String ontologyId = jsonReader.nextString();

                    ++ nOntologies;
                    System.out.println("Scanning ontology: " + ontologyId);

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

                    jsonReader.endObject(); // ontology

                    System.out.println("Now have " + nOntologies + " ontologies and " + result.iriToDefinitions.size() + " distinct IRIs");
                }

                jsonReader.endArray();

            } else {

                jsonReader.skipValue();

            }
        }

        jsonReader.endObject();
        jsonReader.close();

        System.out.println("--- Linker Pass 1 complete. Found " + nOntologies + " ontologies and " + result.iriToDefinitions.size() + " distinct IRIs");

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
        JsonElement label = null;

        while(jsonReader.peek() != JsonToken.END_OBJECT) {
            String key = jsonReader.nextName();

            if(key.equals("iri")) {
                iri = jsonReader.nextString();
            } else if(key.equals("isDefiningOntology")) {
                JsonElement elem = jsonParser.parse(jsonReader);
                isDefining = elem.getAsJsonObject().get("value").getAsString().equals("true");
            } else if(key.equals("label")) {
                label = jsonParser.parse(jsonReader);
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

        EntityDefinition entityDefinition = new EntityDefinition();
        entityDefinition.ontologyId = ontologyId;
        entityDefinition.entityType = entityType;
        entityDefinition.isDefiningOntology = isDefining;
        entityDefinition.label = label;

        EntityDefinitionSet definitionSet = result.iriToDefinitions.get(iri);

        if(definitionSet == null) {
            definitionSet = new EntityDefinitionSet();
            definitionSet.definitions = new HashSet<>();
            definitionSet.definingDefinitions = new HashSet<>();
            definitionSet.ontologyIdToDefinitions = new HashMap<>();
            result.iriToDefinitions.put(iri, definitionSet);
        }

        definitionSet.definitions.add(entityDefinition);
        definitionSet.ontologyIdToDefinitions.put(ontologyId, entityDefinition);

        if(entityDefinition.isDefiningOntology)
            definitionSet.definingDefinitions.add(entityDefinition);

        jsonReader.endObject();
    }


}
