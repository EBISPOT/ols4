
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

                jsonWriter.name("ontologies");

                jsonReader.beginArray();
                jsonWriter.beginArray();

                while (jsonReader.peek() != JsonToken.END_ARRAY) {

                    jsonReader.beginObject(); // ontology
                    jsonWriter.beginObject();

                    String ontologyIdName = jsonReader.nextName();
                    if(!ontologyIdName.equals("ontologyId")) {
                        throw new RuntimeException("the json is not formatted correctly; ontologyId should always come first");
                    }
                    String ontologyId = jsonReader.nextString();

                    ++ nOntologies;
                    System.out.println("Writing ontology " + ontologyId + " (" + nOntologies + ")");

                    jsonWriter.name("ontologyId");
                    jsonWriter.value(ontologyId);

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
                            CopyJsonGatheringStrings.copyJsonGatheringStrings(jsonReader, jsonWriter, ontologyGatheredStrings);
                        }
                    }

                    jsonWriter.name("linkedEntities");
                    writeLinkedEntitiesFromGatheredStrings(jsonWriter, ontologyGatheredStrings, ontologyId, null, pass1Result);

                    jsonReader.endObject(); // ontology
                    jsonWriter.endObject();
                }

                jsonReader.endArray();
                jsonWriter.endArray();

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

            jsonWriter.beginObject();
            jsonReader.beginObject();

            Set<String> stringsInEntity = new HashSet<String>();
            String entityIri = null;

            while(jsonReader.peek() != JsonToken.END_OBJECT) {

                String name = jsonReader.nextName();
                stringsInEntity.add(name);
                jsonWriter.name(name);

                if(name.equals("iri")) {
                    entityIri = jsonReader.nextString();
                    jsonWriter.value(entityIri);
                } else {
                    CopyJsonGatheringStrings.copyJsonGatheringStrings(jsonReader, jsonWriter, stringsInEntity);
                }
            }


            EntityDefinitionSet defOfThisEntity = pass1Result.iriToDefinitions.get(entityIri);
            if(defOfThisEntity != null) {
                writeDefinedByAndDefinedIn(jsonWriter, defOfThisEntity);
            }

            jsonWriter.name("linkedEntities");
            writeLinkedEntitiesFromGatheredStrings(jsonWriter, stringsInEntity, ontologyId, entityIri, pass1Result);

            jsonWriter.endObject();
            jsonReader.endObject();
        }


        jsonReader.endArray();
        jsonWriter.endArray();
    }


    private static void writeLinkedEntitiesFromGatheredStrings(JsonWriter jsonWriter, Set<String> strings, String ontologyId, String entityIri, LinkerPass1.LinkerPass1Result pass1Result) throws IOException {

        jsonWriter.beginObject();

        for(String str : strings) {

            if(str.startsWith("http://www.w3.org/2000/01/rdf-schema#") ||
                    str.startsWith("http://www.w3.org/1999/02/22-rdf-syntax-ns#") ||
                    str.startsWith("http://www.geneontology.org/formats/oboInOwl#") ||
                    str.startsWith("http://www.w3.org/2002/07/owl#")) {
                continue;
            }

            if(entityIri != null && str.equals(entityIri)) {
                continue;
            }

            EntityDefinitionSet iriMapping = pass1Result.iriToDefinitions.get(str);

            if(iriMapping != null) {
                jsonWriter.name(str);
                jsonWriter.beginObject();
                writeIriMapping(jsonWriter, iriMapping, ontologyId);
                jsonWriter.endObject();
                continue;
            }

            CurieMapResult curieMapping = mapCurie(str);

            if(curieMapping != null) {

                // It was a CURIE which we were able to map.
                jsonWriter.name(str);
                jsonWriter.beginObject();
                jsonWriter.name("url");
                jsonWriter.value(curieMapping.url);
                jsonWriter.name("source");
                jsonWriter.value(curieMapping.source);

                // Maybe the IRI the CURIE mapped to maps to an entity in OLS?
                EntityDefinitionSet curieIriMapping = pass1Result.iriToDefinitions.get(curieMapping.url);
                if(curieIriMapping != null) {
                    writeIriMapping(jsonWriter, curieIriMapping, ontologyId);
                }

                jsonWriter.endObject();
            }

        }

        jsonWriter.endObject();
    }

    private static void writeIriMapping(JsonWriter jsonWriter, EntityDefinitionSet definitions, String ontologyId) throws IOException {

        writeDefinedByAndDefinedIn(jsonWriter, definitions);

        boolean foundDefinition = false;

        // 1. Prefer labels from this ontology
        EntityDefinition defFromThisOntology = definitions.ontologyIdToDefinitions.get(ontologyId);
        if(defFromThisOntology != null) {
            jsonWriter.name("label");
            com.google.gson.internal.Streams.write(defFromThisOntology.label, jsonWriter);
            jsonWriter.name("type");
            jsonWriter.value(defFromThisOntology.entityType);
            foundDefinition = true;
        }

        // 2. Look for a defining ontology
        if(definitions.definingDefinitions.size() > 0) {

            EntityDefinition definingOntology = definitions.definingDefinitions.iterator().next();

            jsonWriter.name("ontologyId");
            jsonWriter.value(definingOntology.ontologyId);

            // Only take the label from the defining ontology if it wasn't set in the importing ontology
            if(!foundDefinition) {
                jsonWriter.name("label");
                com.google.gson.internal.Streams.write(definingOntology.label, jsonWriter);
            }

            foundDefinition = true;
        }

        if(!foundDefinition) {

            // 3. Fall back on the first ontology we encounter that defines the IRI
            //
            // This only applies if (a) the importing ontology didn't define the entity and (b) no other ontology
            // had isDefiningOntology=true for that entity
            //
            EntityDefinition fallbackDef = definitions.definitions.iterator().next();
            jsonWriter.name("ontologyId");
            jsonWriter.value(fallbackDef.ontologyId);
            jsonWriter.name("type");
            jsonWriter.value(fallbackDef.entityType);
            jsonWriter.name("label");
            com.google.gson.internal.Streams.write(fallbackDef.label, jsonWriter);
        }
    }

    private static void writeDefinedByAndDefinedIn(JsonWriter jsonWriter, EntityDefinitionSet definitions) throws IOException {

        if(definitions.definingDefinitions.size() > 0) {
            jsonWriter.name("definedBy");
            jsonWriter.beginArray();
            for(var def : definitions.definingDefinitions) {
                jsonWriter.value(def.ontologyId);
            }
            jsonWriter.endArray();
        }

        if(definitions.definitions.size() > 0) {
            jsonWriter.name("definedIn");
            jsonWriter.beginArray();
            for(var def : definitions.definitions) {
                jsonWriter.value(def.ontologyId);
            }
            jsonWriter.endArray();
        }
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