
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class LinkerPass2 {

    public static class LinkerPass2Result {
        Map<String, Set<String>> ontologyIdToReferences = new HashMap<>();
    }

    /* Scan through the JSON again and make a map (ontology ID -> referenced ontology IDs) using O(1) lookups in the map from pass 1
    *
    * How do we decide which ontologies are referenced?
    *
    *   - For each string:
    *
    *        - If it's defined as an entity by the ontology that uses it, NO ontologies are considered referenced.
    *
    *        - If it's defined as an entity by an ontology with isDefiningOntology=true, only that SINGLE ontology is considered referenced.
    *
    *        - If it's defined as an entity by multiple ontologies, none of which are the ontology which uses it, and none of them
    *          have isDefiningOntology=true, the FIRST ontology we encounter is considered referenced (not ideal, but
    *          the best we can do in a bad situation.)
    *
    *        - If it's not defined as an entity by any ontologies at all, there is no reference. (This is most strings)
    *
    * This ensures that we always have *somewhere* to get the entity metadata from for strings that are entity IRIs.
    * ALL of the ontologies that define the entity will still be available to pass3 from the pass1 result. The pass2
    * result is just to establish which ontologie(s) we need to load to get the label/any other metadata that might be
    * required in the future.
    */
    public static LinkerPass2Result run(String inputJsonFilename, LinkerPass1.LinkerPass1Result pass1Result) throws IOException {

        LinkerPass2Result result = new LinkerPass2Result();

        JsonReader jsonReader = new JsonReader(new InputStreamReader(new FileInputStream(inputJsonFilename)));

        System.out.println("--- Linker Pass 2: Scanning " + inputJsonFilename + " to construct ontologyId->referencedOntologyId map");
        int nOntologies = 0;

        jsonReader.beginObject();

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
                    System.out.println("Scanning ontology " + ontologyId + " (" + nOntologies + "/" + pass1Result.ontologies.size() + ")");


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

        System.out.println("--- Linker Pass 2 complete");

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

        Set<EntityReference> ontologies = pass1Result.iriToOntologies.get(str);

        if(ontologies == null || ontologies.size() == 0) {
            // If it's not defined as an entity by any ontologies at all, there is no reference. (This is most strings)
            return;
        }

        // If it's defined by the ontology that uses it, NO ontologies are considered referenced.
        for(var o : ontologies) {
            if(o.ontologyId.equals(ontologyId)) {
                return;
            }
        }

        // If it's defined by an ontology with isDefiningOntology=true, only that SINGLE ontology is considered referenced.
        for(var o : ontologies) {
            if(o.isDefiningOntology) {
                addReferencedOntology(result, ontologyId, o.ontologyId);
                return;
            }
        }

        // If it's defined as an entity by multiple ontologies, none of which are the ontology which uses it, and none of them
        // have isDefiningOntology=true, the FIRST ontology we encounter is considered referenced (not ideal, but
        // the best we can do in a bad situation.)
        //
        addReferencedOntology(result, ontologyId, ontologies.iterator().next().ontologyId);
    }

    private static void addReferencedOntology(LinkerPass2Result result, String ontologyId, String referencedOntologyId) {

        Set<String> found = result.ontologyIdToReferences.get(ontologyId);

        if(found != null) {
            found.add(referencedOntologyId);
        } else {
            found = new TreeSet<>();
            found.add(referencedOntologyId);
            result.ontologyIdToReferences.put(ontologyId, found);
        }
    }
}
