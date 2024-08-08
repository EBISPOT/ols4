import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static uk.ac.ebi.ols.shared.DefinedFields.HAS_LOCAL_DEFINITION;
import static uk.ac.ebi.ols.shared.DefinedFields.IS_DEFINING_ONTOLOGY;

public class LinkerPass2 {

    private static final JsonParser jsonParser = new JsonParser();

    public static final OboDatabaseUrlService dbUrls = new OboDatabaseUrlService();
    public static final Bioregistry bioregistry = new Bioregistry();

    public static void run(String inputJsonFilename, String outputJsonFilename, LevelDB leveldb, LinkerPass1.LinkerPass1Result pass1Result) throws IOException {

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



                    jsonWriter.name("importsFrom");
                    jsonWriter.beginArray();
                    var imports = pass1Result.ontologyIdToImportedOntologyIds.get(ontologyId);
                    if(imports != null) {
                        for(String ontId : imports) {
                            jsonWriter.value(ontId);
                        }
                    }
                    jsonWriter.endArray();


                    jsonWriter.name("exportsTo");
                    jsonWriter.beginArray();
                    var importedBy = pass1Result.ontologyIdToImportingOntologyIds.get(ontologyId);
                    if(importedBy != null) {
                        for(String ontId : importedBy) {
                            jsonWriter.value(ontId);
                        }
                    }
                    jsonWriter.endArray();


                    Set<String> ontologyGatheredStrings = new TreeSet<>();

                    while(jsonReader.peek() != JsonToken.END_OBJECT) {
                        String key = jsonReader.nextName();
                        jsonWriter.name(key);

                        if(key.equals("classes")) {
                            writeEntityArray(jsonReader, jsonWriter, "class", ontologyId, leveldb, pass1Result);
                            continue;
                        } else if(key.equals("properties")) {
                            writeEntityArray(jsonReader, jsonWriter, "property", ontologyId, leveldb, pass1Result);
                            continue;
                        } else if(key.equals("individuals")) {
                            writeEntityArray(jsonReader, jsonWriter, "individual", ontologyId, leveldb, pass1Result);
                            continue;
                        } else {
                            ontologyGatheredStrings.add(ExtractIriFromPropertyName.extract(key));
                            CopyJsonGatheringStrings.copyJsonGatheringStrings(jsonReader, jsonWriter, ontologyGatheredStrings);
                        }
                    }

                    jsonWriter.name("linkedEntities");
                    writeLinkedEntitiesFromGatheredStrings(jsonWriter, ontologyGatheredStrings, ontologyId, null, leveldb, pass1Result);

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

    private static void writeEntityArray(JsonReader jsonReader, JsonWriter jsonWriter, String entityType, String ontologyId, LevelDB leveldb, LinkerPass1.LinkerPass1Result pass1Result) throws IOException {

        jsonReader.beginArray();
        jsonWriter.beginArray();

        while(jsonReader.peek() != JsonToken.END_ARRAY) {

            jsonWriter.beginObject();
            jsonReader.beginObject();

            Set<String> stringsInEntity = new HashSet<String>();
            String entityIri = null;

            while(jsonReader.peek() != JsonToken.END_OBJECT) {

                String name = jsonReader.nextName();
                stringsInEntity.add(ExtractIriFromPropertyName.extract(name));
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

                jsonWriter.name(IS_DEFINING_ONTOLOGY.getText());
                jsonWriter.value(defOfThisEntity.definingOntologyIds.contains(ontologyId));

                if (defOfThisEntity.definingDefinitions.size() > 0) {
                    jsonWriter.name("definedBy");
                    jsonWriter.beginArray();
                    for (var def : defOfThisEntity.definingDefinitions) {
                        jsonWriter.value(def.ontologyId);
                    }
                    jsonWriter.endArray();
                }

                if (defOfThisEntity.definitions.size() > 0) {
                    jsonWriter.name("appearsIn");
                    jsonWriter.beginArray();
                    for (var def : defOfThisEntity.definitions) {
                        jsonWriter.value(def.ontologyId);
                    }
                    jsonWriter.endArray();
                }
            }

            jsonWriter.name("linkedEntities");
            writeLinkedEntitiesFromGatheredStrings(jsonWriter, stringsInEntity, ontologyId, entityIri, leveldb, pass1Result);

            jsonWriter.endObject();
            jsonReader.endObject();
        }


        jsonReader.endArray();
        jsonWriter.endArray();
    }


    private static void writeLinkedEntitiesFromGatheredStrings(JsonWriter jsonWriter, Set<String> strings, String ontologyId, String entityIri, LevelDB leveldb, LinkerPass1.LinkerPass1Result pass1Result) throws IOException {

        jsonWriter.beginObject();

        for(String str : strings) {

            if(str.trim().length() == 0) {
                continue;
            }

            if(//str.startsWith("http://www.w3.org/2000/01/rdf-schema#") ||
                    str.startsWith("http://www.w3.org/1999/02/22-rdf-syntax-ns#") ||
                    //str.startsWith("http://www.geneontology.org/formats/oboInOwl#") ||
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

            // The string wasn't in any ontology. Maybe bioregistry can turn it into a curie?
            String curie = bioregistry.getCurieForUrl(str);

            if(curie == null) {
                // or maybe the string itself is a curie?
                if (str.matches("^[A-z0-9]+:[A-z0-9]+$")) {
                    curie = str;
                }
            }

            if (curie != null) {

                boolean foundCurieMatchToOntologyTerm = false;

                String databaseId = curie.substring(0, curie.indexOf(':'));
                String entryId = curie.substring(curie.indexOf(':') + 1);

                // The databaseId might be the preferredPrefix of an ontology in OLS
                Set<String> ontologyIds = pass1Result.preferredPrefixToOntologyIds.get(databaseId);
                if (ontologyIds != null) {
                    for (String curieOntologyId : ontologyIds) {
                        Set<String> ontologyBaseUris = pass1Result.ontologyIdToBaseUris
                                .get(curieOntologyId);
                        if (ontologyBaseUris != null) {
                            for (String ontologyBaseUri : ontologyBaseUris) {
                                String iri = ontologyBaseUri + entryId;
                                EntityDefinitionSet curieIriMapping = pass1Result.iriToDefinitions
                                        .get(iri);

                                if (curieIriMapping != null) {
                                    foundCurieMatchToOntologyTerm = true;
                                    jsonWriter.name(str);
                                    jsonWriter.beginObject();
                                    jsonWriter.name("iri");
                                    jsonWriter.value(iri);
                                    writeIriMapping(jsonWriter, curieIriMapping,
                                            ontologyId);
                                    break;
                                }
                            }

                            if(foundCurieMatchToOntologyTerm)
                                break;
                        }
                    }
                }

                CurieMapResult curieMapping = mapCurie(databaseId, entryId);

                if (curieMapping != null) {

                    // It was a CURIE which we were able to map to an URL
		    // using bioregistry.

                    if (!foundCurieMatchToOntologyTerm) {
                        jsonWriter.name(str);
                        jsonWriter.beginObject();
                    }

                    jsonWriter.name("url");
                    jsonWriter.value(curieMapping.url);
                    jsonWriter.name("source");
                    jsonWriter.value(curieMapping.source);
                    jsonWriter.name("curie");
                    jsonWriter.value(curie);

                    foundCurieMatchToOntologyTerm = true;
                }

                if (foundCurieMatchToOntologyTerm)
                    jsonWriter.endObject();

            }

        // No match as an IRI or as a CURIE. Look in LevelDB (for ORCIDs etc.)
            if(leveldb != null) {
                JsonElement leveldbMatch = leveldb.get(str);

                if(leveldbMatch != null) {
                    jsonWriter.name(str);
                    com.google.gson.internal.Streams.write(leveldbMatch, jsonWriter);
                    continue;
                }
            }


        }

        jsonWriter.endObject(); // linkedEntities
    }

    private static void writeIriMapping(JsonWriter jsonWriter, EntityDefinitionSet definitions, String ontologyId) throws IOException {

        if(definitions.definingDefinitions.size() > 0) {
	
	    // There are ontologies which canonically define this term

            jsonWriter.name("definedBy");
            jsonWriter.beginArray();
            for(var def : definitions.definingDefinitions) {
                jsonWriter.value(def.ontologyId);
            }
            jsonWriter.endArray();

        }  else {

		// The term does not have any canonically defining ontologies...

		if(definitions.definingOntologyIds.size() == 1) {

			// ...and is only defined in ONE ontology. Therefore that ontology is the canonical defining ontology as far as OLS is concerned
			jsonWriter.name("definedBy");
			jsonWriter.beginArray();
			jsonWriter.value(definitions.definingOntologyIds.iterator().next());
			jsonWriter.endArray();

		} else {

			// ...and is defined in multiple ontologies. We cannot establish a defining ontology.
		}

	}

	jsonWriter.name("numAppearsIn");
    jsonWriter.value(Integer.toString(definitions.definitions.size()));

	jsonWriter.name(HAS_LOCAL_DEFINITION.getText());
	jsonWriter.value(definitions.ontologyIdToDefinitions.containsKey(ontologyId));

        EntityDefinition defFromThisOntology = definitions.ontologyIdToDefinitions.get(ontologyId);

        // 1. Prefer metadata from the defining ontology
        if(definitions.definingDefinitions.size() > 0) {
	    EntityDefinition definingOntology = definitions.definingDefinitions.iterator().next();

	    jsonWriter.name("label");
	    com.google.gson.internal.Streams.write(definingOntology.label, jsonWriter);
        jsonWriter.name("curie");
        com.google.gson.internal.Streams.write(definingOntology.curie, jsonWriter);
	    jsonWriter.name("type");
        jsonWriter.beginArray();
        for(String type : definingOntology.entityTypes) {
            jsonWriter.value(type);
        }
        jsonWriter.endArray();

	// 2. Else look for metadata from this ontology
	} else if(defFromThisOntology != null) {

            jsonWriter.name("label");
            com.google.gson.internal.Streams.write(defFromThisOntology.label, jsonWriter);
            jsonWriter.name("curie");
            com.google.gson.internal.Streams.write(defFromThisOntology.curie, jsonWriter);
            jsonWriter.name("type");
            jsonWriter.beginArray();
            for(String type : defFromThisOntology.entityTypes) {
                jsonWriter.value(type);
            }
            jsonWriter.endArray();

	// 3. Fall back on the first ontology we encounter that defines the IRI
	//
	// This only applies if (a) the importing ontology didn't define the entity and (b) no other ontology
	// was considered canonical
	//
	} else {
            EntityDefinition fallbackDef = definitions.definitions.iterator().next();
            jsonWriter.name("type");
            jsonWriter.beginArray();
            for(String type : fallbackDef.entityTypes) {
                jsonWriter.value(type);
            }
            jsonWriter.endArray();
            jsonWriter.name("label");
            com.google.gson.internal.Streams.write(fallbackDef.label, jsonWriter);
            jsonWriter.name("curie");
            com.google.gson.internal.Streams.write(fallbackDef.curie, jsonWriter);
        }
    }

    private static CurieMapResult mapCurie(String databaseId, String entryId) {

	// check GO db-xrefs for an URL
	String url = dbUrls.getUrlForId(databaseId, entryId);

	if(url != null) {
		CurieMapResult res = new CurieMapResult();
		res.url = url;
		res.source = dbUrls.getXrefUrls();
		return res;
	}

	// check bioregistry for an URL
	url = bioregistry.getUrlForId(databaseId, entryId);

	if(url != null) {
		CurieMapResult res = new CurieMapResult();
		res.url = url;
		res.source = bioregistry.getRegistryUrl();
		return res;
	}

        return null;
    }

    private static class CurieMapResult {
        public String url;
        public String source;
    }

}
