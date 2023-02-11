
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.*;
import java.util.*;

public class LinkerPass2 {

    private static final JsonParser jsonParser = new JsonParser();

    public static final OboDatabaseUrlService dbUrls = new OboDatabaseUrlService();
    public static final Bioregistry bioregistry = new Bioregistry();

    public static void run(String inputJsonFilename, String outputJsonFilename, LinkerPass1.LinkerPass1Result pass1Result) throws IOException {

        JsonReader jsonReader = new JsonReader(new InputStreamReader(new FileInputStream(inputJsonFilename)));
        JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(new FileOutputStream(outputJsonFilename)));
        jsonWriter.setIndent("  ");

        System.out.println("--- Linker Pass 2: Processing " + inputJsonFilename);
        int nOntologies = 0;

        jsonReader.beginObject();
        jsonWriter.beginObject();

        while (jsonReader.peek() != JsonToken.END_OBJECT) {

            String name = jsonReader.nextName();

            if (name.equals("ontologies")) {

                jsonReader.beginArray();

                while (jsonReader.peek() != JsonToken.END_ARRAY) {

                    jsonReader.beginObject(); // ontology

                    String ontologyIdName = jsonReader.nextName();
                    if(!ontologyIdName.equals("ontologyId")) {
                        throw new RuntimeException("the json is not formatted correctly; ontologyId should always come first");
                    }
                    String ontologyId = jsonReader.nextString();

                    ++ nOntologies;
                    System.out.println("Writing ontology " + ontologyId + " (" + nOntologies + ")");

                    Set<String> ontologyGatheredStrings = new TreeSet<>();

                    while(jsonReader.peek() != JsonToken.END_OBJECT) {
                        String key = jsonReader.nextName();
                        jsonWriter.name(key);

                        if(key.equals("classes")) {
                            writeEntityArray(jsonReader, jsonWriter, "class", ontologyId, pass1Result);
                            continue;
                        } else if(key.equals("properties")) {
                            writeEntityArray(jsonReader, jsonWriter, "property", ontologyId, pass1Result);
                            continue;
                        } else if(key.equals("individuals")) {
                            writeEntityArray(jsonReader, jsonWriter, "individual", ontologyId, pass1Result);
                            continue;
                        } else {
                            JsonElement someOtherOntologyPropertyValue = jsonParser.parse(jsonReader);
                            gatherStrings(someOtherOntologyPropertyValue, ontologyGatheredStrings);
                            com.google.gson.internal.Streams.write(someOtherOntologyPropertyValue, jsonWriter);
                        }
                    }

                    JsonObject ontologyLinkedEntities = new JsonObject();
                    populateLinkedEntitiesUsingGatheredStrings(ontologyLinkedEntities, ontologyGatheredStrings, ontologyId, pass1Result);

                    if(ontologyLinkedEntities.size() > 0) {
                        jsonWriter.name("linkedEntities");
                        com.google.gson.internal.Streams.write(ontologyLinkedEntities, jsonWriter);
                    }

                    jsonReader.endObject(); // ontology
                }

                jsonReader.endArray();

            } else {

                jsonReader.skipValue();

            }
        }

        jsonReader.endObject();
        jsonWriter.endObject();
        jsonReader.close();
        jsonWriter.close();

        System.out.println("--- Linker Pass 2 complete");
    }

    private static void writeEntityArray(JsonReader jsonReader, JsonWriter jsonWriter, String entityType, String ontologyId, LinkerPass1.LinkerPass1Result pass1Result) throws IOException {

        jsonReader.beginArray();
        jsonWriter.beginArray();

        while(jsonReader.peek() != JsonToken.END_ARRAY) {

            JsonObject entity = jsonParser.parse(jsonReader).getAsJsonObject();

            Set<String> gatheredStrings = new TreeSet<>();
            gatherStrings(entity, gatheredStrings);

            JsonObject linkedEntities = new JsonObject();
            populateLinkedEntitiesUsingGatheredStrings(linkedEntities, gatheredStrings, ontologyId, pass1Result);

            if(linkedEntities.size() > 0) {
                entity.add("linkedEntities", linkedEntities);
            }

            com.google.gson.internal.Streams.write(entity, jsonWriter);
        }


        jsonReader.endArray();
        jsonWriter.endArray();
    }

    private static void gatherStrings(JsonElement json, Set<String> strings) {
        if(json.isJsonArray()) {
            JsonArray arr = json.getAsJsonArray();
            for(JsonElement el : arr) {
                gatherStrings(el, strings);
            }
        } else if(json.isJsonObject()) {
            JsonObject obj = json.getAsJsonObject();
            for(var entry : obj.entrySet()) {
                strings.add(entry.getKey());
                gatherStrings(entry.getValue(), strings);
            }
        } else if(json.isJsonPrimitive()) {
            strings.add(json.getAsJsonPrimitive().getAsString());
        }
    }


    private static void populateLinkedEntitiesUsingGatheredStrings(JsonObject linkedEntities, Set<String> strings, String ontologyId, LinkerPass1.LinkerPass1Result pass1Result) {

        for(String str : strings) {

            JsonObject linkedEntity = iriToLinkedEntity(str, ontologyId, pass1Result);

            if(linkedEntity == null) {

                // the string didn't map to an entity in OLS. Maybe it's a CURIE?
                CurieMapResult mappedCurie = mapCurie(str);

                if(mappedCurie != null) {

                    // It was a CURIE. Maybe the IRI the CURIE mapped to maps to an entity in OLS?
                    linkedEntity = iriToLinkedEntity(mappedCurie.url, ontologyId, pass1Result);

                    if(linkedEntity == null) {

                        // The CURIE resolved to an IRI which did not map to an entity in OLS.
                        // Add it to linkedEntities as a CURIE->URL reference so it can be turned into a link
                        //
                        JsonObject curieMapping = new JsonObject();
                        curieMapping.addProperty("url", mappedCurie.url);
                        curieMapping.addProperty("source", mappedCurie.source);
                        linkedEntity.add(str, curieMapping);
                        return;
                    }
                }
            }

            // The IRI resolved to an entity in OLS
            linkedEntities.add(str, linkedEntity);
        }
    }

    private static JsonObject iriToLinkedEntity(String iri, String ontologyId, LinkerPass1.LinkerPass1Result pass1Result) {

        List<EntityDefinition> definitions = pass1Result.iriToDefinition.get(iri);

        if(definitions == null) {
            return null;
        }

        JsonObject linkedEntity = new JsonObject();

        JsonArray definedIn = new JsonArray();
        JsonArray definedBy = new JsonArray();
        for(EntityDefinition def : definitions) {
            definedIn.add(def.ontologyId);
            if(def.isDefiningOntology) {
                definedBy.add(def.ontologyId);
            }
        }

        // If there's an isDefiningOntology, set definedBy
        // Otherwise, set definedIn
        //
        if(definedBy.size() > 0) {
            linkedEntity.add("definedBy", definedBy);
        } else {
            if(definedIn.size() > 0)
                linkedEntity.add("definedIn", definedIn);
        }

        // 1. Prefer labels from this ontology
        boolean foundDefinition = false;
        for(EntityDefinition def2 : definitions) {
            if(def2.ontologyId.equals(ontologyId)) {
                linkedEntity.addProperty("ontologyId", def2.ontologyId);
                linkedEntity.add("label", def2.label);
                linkedEntity.addProperty("type", def2.entityType);
                foundDefinition = true;
                break;
            }
        }

        // 2. Look for a defining ontology
        for(EntityDefinition def3 : definitions) {
            if(def3.isDefiningOntology) {
                linkedEntity.addProperty("ontologyId", def3.ontologyId);
                linkedEntity.addProperty("type", def3.entityType);

                // Only take the label from the defining ontology if it wasn't set in the importing ontology
                if(!linkedEntity.has("label")) {
                    linkedEntity.add("label", def3.label);
                }

                foundDefinition = true;
                break;
            }
        }

        if(!foundDefinition) {

            // 3. Fall back on the first ontology we encounter that defines the IRI
            //
            // This only applies if (a) the importing ontology didn't define the entity and (b) no other ontology
            // had isDefiningOntology=true for that entity
            //
            EntityDefinition fallbackDef = definitions.iterator().next();
            linkedEntity.addProperty("ontologyId", fallbackDef.ontologyId);
            linkedEntity.addProperty("type", fallbackDef.entityType);
            linkedEntity.add("label", definitions.iterator().next().label);
        }

        return linkedEntity;
    }

    private static CurieMapResult mapCurie(String maybeCurie) {

        // No definitions for this string as an IRI in OLS. Maybe it's a CURIE.
        //
        if(maybeCurie.matches("^[A-z0-9]+:[A-z0-9]+$")) {
            String databaseId = maybeCurie.substring(0, maybeCurie.indexOf(':'));
            String entryId = maybeCurie.substring(maybeCurie.indexOf(':') + 1);

            // check GO db-xrefs
            String url = dbUrls.getUrlForId(databaseId, entryId);

            if(url != null) {
                CurieMapResult res = new CurieMapResult();
                res.url = url;
                res.source = dbUrls.getXrefUrls();
                return res;
            }

            // check bioregistry
            url = bioregistry.getUrlForId(databaseId, entryId);

            if(url != null) {
                CurieMapResult res = new CurieMapResult();
                res.url = url;
                res.source = bioregistry.getRegistryUrl();
                return res;
            }
        }

        return null;
    }

    private static class CurieMapResult {
        public String url;
        public String source;
    }

}