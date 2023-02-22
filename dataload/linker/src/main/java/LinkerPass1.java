import com.google.common.io.CountingInputStream;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class LinkerPass1 {

    private static final JsonParser jsonParser = new JsonParser();

    public static class LinkerPass1Result {
        Map<String, EntityDefinitionSet> iriToDefinitions = new HashMap<>();
	Map<String, Set<String>> ontologyIriToOntologyIds = new HashMap<>();
	Map<String, Set<String>> preferredPrefixToOntologyIds = new HashMap<>();
	Map<String, Set<String>> ontologyIdToBaseUris = new HashMap<>();
    }

    public static LinkerPass1Result run(String inputJsonFilename) throws IOException {

        LinkerPass1Result result = new LinkerPass1Result();

        FileInputStream is = new FileInputStream(inputJsonFilename);
        InputStreamReader reader = new InputStreamReader(is);
        JsonReader jsonReader = new JsonReader(reader);

        int nOntologies = 0;

        System.out.println("--- Linker Pass 1: Scanning " + inputJsonFilename);

        jsonReader.beginObject();

        while (jsonReader.peek() != JsonToken.END_OBJECT) {
            String name = jsonReader.nextName();

            if (name.equals("ontologies")) {
                jsonReader.beginArray();

                while(jsonReader.peek() != JsonToken.END_ARRAY) {

                    jsonReader.beginObject(); // ontology

		    String ontologyId = null;
		    Set<String> ontologyBaseUris = new HashSet<>();

		    String key;

		    while(jsonReader.peek() != JsonToken.END_OBJECT) {

			key = jsonReader.nextName();

			if(key.equals("ontologyId")) {

				ontologyId = jsonReader.nextString();
				++nOntologies;
				System.out.println("Scanning ontology: " + ontologyId);

			} else if(key.equals("iri")) {

				if(ontologyId == null)
					throw new RuntimeException("missing ontologyId");

				String ontologyIri = jsonReader.nextString();

				Set<String> ids = result.ontologyIriToOntologyIds.get(ontologyIri);
				if(ids == null) {
					ids = new HashSet<>();
					ids.add(ontologyId);
					result.ontologyIriToOntologyIds.put(ontologyIri, ids);
				} else {
					ids.add(ontologyId);
				}

			} else if(key.equals("base_uri")) {

				JsonArray baseUris = jsonParser.parse(jsonReader).getAsJsonArray();
				for(JsonElement baseUri : baseUris) {
					ontologyBaseUris.add(baseUri.getAsString());
				}

			} else if(key.equals("preferredPrefix")) {

				String preferredPrefix = jsonReader.nextString();

				ontologyBaseUris.add("http://purl.obolibrary.org/obo/" + preferredPrefix + "_");

				Set<String> ids = result.preferredPrefixToOntologyIds.get(preferredPrefix);
				if(ids == null) {
					ids = new HashSet<>();
					ids.add(ontologyId);
					result.preferredPrefixToOntologyIds.put(preferredPrefix, ids);
				} else {
					ids.add(ontologyId);
				}

			} else if(key.equals("classes")) {

				if(ontologyId == null)
					throw new RuntimeException("missing ontologyId");

				parseEntityArray(jsonReader, "class", ontologyId, ontologyBaseUris, result);

			} else if(key.equals("properties")) {

				if(ontologyId == null)
					throw new RuntimeException("missing ontologyId");

				parseEntityArray(jsonReader, "property", ontologyId, ontologyBaseUris, result);

			} else if(key.equals("individuals")) {

				if(ontologyId == null)
					throw new RuntimeException("missing ontologyId");

				parseEntityArray(jsonReader, "individual", ontologyId, ontologyBaseUris, result);

			}  else {
				jsonReader.skipValue();
			}
		    }

                    jsonReader.endObject(); // ontology

		    result.ontologyIdToBaseUris.put(ontologyId, ontologyBaseUris);

                    System.out.println("Now have " + nOntologies + " ontologies and " + result.iriToDefinitions.size() + " distinct IRIs");
                }

                jsonReader.endArray();

            } else {

                jsonReader.skipValue();

            }
        }

        jsonReader.endObject();
        jsonReader.close();

        System.out.println("--- Linker Pass 1: Finished scan. Establishing defining ontologies...");

	// populate EntityDefinitionSet.definingDefinitions for each IRI

	for(var entry : result.iriToDefinitions.entrySet()) {

		// String iri = entry.getKey();
		EntityDefinitionSet definitions = entry.getValue();

		// definingOntologyIris -> definingOntologyIds
		for(String ontologyIri : definitions.definingOntologyIris) {
			for(String ontologyId : result.ontologyIriToOntologyIds.get(ontologyIri)) {
				definitions.definingOntologyIds.add(ontologyId);
			}
		}

		for(EntityDefinition def : definitions.definitions) {
			if(definitions.definingOntologyIds.contains(def.ontologyId)) {
				def.isDefiningOntology = true;
				continue;
			}
		}

		definitions.definingDefinitions = definitions.definitions.stream().filter(def -> def.isDefiningOntology).collect(Collectors.toSet());
	}

        System.out.println("--- Linker Pass 1 complete. Found " + nOntologies + " ontologies and " + result.iriToDefinitions.size() + " distinct IRIs");

        return result;
    }

    public static void parseEntityArray(JsonReader jsonReader, String entityType, String ontologyId, Set<String> ontologyBaseUris, LinkerPass1Result result) throws IOException {
        jsonReader.beginArray();

        while(jsonReader.peek() != JsonToken.END_ARRAY) {
            parseEntity(jsonReader, entityType, ontologyId, ontologyBaseUris, result);
        }

        jsonReader.endArray();
    }

    public static void parseEntity(JsonReader jsonReader, String entityType, String ontologyId,  Set<String> ontologyBaseUris,LinkerPass1Result result) throws IOException {
        jsonReader.beginObject();

        String iri = null;
        JsonElement label = null;
	Set<String> definedBy = new HashSet<>();

        while(jsonReader.peek() != JsonToken.END_OBJECT) {
            String key = jsonReader.nextName();

            if(key.equals("iri")) {
                iri = jsonReader.nextString();
            } else if(key.equals("label")) {
                label = jsonParser.parse(jsonReader);
            } else if(key.equals("http://www.w3.org/2000/01/rdf-schema#definedBy")) {
                JsonElement jsonDefinedBy = jsonParser.parse(jsonReader);
		if(jsonDefinedBy.isJsonArray()) {
			JsonArray arr = jsonDefinedBy.getAsJsonArray();
			for(JsonElement el : arr) {
				definedBy.add( el.getAsString() );
			}
		} else {
			definedBy.add(jsonDefinedBy.getAsString());
		}
            } else {
                jsonReader.skipValue();
            }
        }

        if(iri == null) {
            throw new RuntimeException("entity had no IRI");
        }

        EntityDefinition entityDefinition = new EntityDefinition();
        entityDefinition.ontologyId = ontologyId;
        entityDefinition.entityType = entityType;
        entityDefinition.label = label;

        EntityDefinitionSet definitionSet = result.iriToDefinitions.get(iri);

        if(definitionSet == null) {
            definitionSet = new EntityDefinitionSet();
            result.iriToDefinitions.put(iri, definitionSet);
        }

        definitionSet.definitions.add(entityDefinition);
        definitionSet.ontologyIdToDefinitions.put(ontologyId, entityDefinition);
	definitionSet.definingOntologyIris.addAll(definedBy);

	for(String baseUri : ontologyBaseUris) {
		if(iri.startsWith(baseUri)) {
			definitionSet.definingOntologyIds.add(ontologyId);
		}
	}

        jsonReader.endObject();
    }


}
