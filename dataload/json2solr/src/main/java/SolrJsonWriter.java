
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import uk.ac.ebi.ols.shared.Embeddings;

import java.io.*;
import java.util.*;


import static uk.ac.ebi.ols.shared.DefinedFields.*;

public class SolrJsonWriter {

    static Gson gson = new Gson();

    /**
     * A rotating writer that creates numbered output files when max rows is reached
     */
    static class RotatingWriter {
        private String basePath;
        private String outputOntologyId;
        private String entityType;
        private int maxRowsPerFile;
        private int currentFileIndex = 0;
        private int currentRowCount = 0;
        private PrintStream currentWriter;

        public RotatingWriter(String outPath, String entityType, String outputOntologyId, int maxRowsPerFile) throws IOException {
            this.basePath = outPath;
            this.outputOntologyId = outputOntologyId;
            this.entityType = entityType;
            this.maxRowsPerFile = maxRowsPerFile;
            openNextFile();
        }

        private void openNextFile() throws IOException {
            if (currentWriter != null) {
                currentWriter.close();
            }
            String filename;
            if(maxRowsPerFile == -1) {
                if(outputOntologyId != null && !outputOntologyId.isEmpty()) {
                    filename = String.format("%s/%s_%s.jsonl", basePath, outputOntologyId, entityType);
                } else {
                    filename = String.format("%s/%s.jsonl", basePath, entityType);
                }
            } else {
                if(outputOntologyId != null && !outputOntologyId.isEmpty()) {
                    filename = String.format("%s/%s_%s_%04d.jsonl", basePath, outputOntologyId, entityType, currentFileIndex);
                } else {
                    filename = String.format("%s/%s_%04d.jsonl", basePath, entityType, currentFileIndex);
                }
            }
            currentWriter = new PrintStream(filename);
            currentRowCount = 0;
            currentFileIndex++;
        }

        public void println(String line) throws IOException {
            if (maxRowsPerFile != -1 && currentRowCount >= maxRowsPerFile) {
                openNextFile();
            }
            currentWriter.println(line);
            currentRowCount++;
        }

        public void close() {
            if (currentWriter != null) {
                currentWriter.close();
            }
        }
    }

    private static int countOntologies(String ontologiesJsonPath) throws IOException {
        int count = 0;
        JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(ontologiesJsonPath)));
        
        reader.beginObject();
        while (reader.peek() != JsonToken.END_OBJECT) {
            String name = reader.nextName();
            if (name.equals("ontologies")) {
                reader.beginArray();
                while (reader.peek() != JsonToken.END_ARRAY) {
                    reader.skipValue(); // Skip entire ontology object
                    count++;
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        reader.close();
        
        return count;
    }

    public static void writeSolrJson(String outputOntologyId, String ontologiesJsonPath, String outPath, Map<String, Embeddings> embeddings, int maxRowsPerFile) throws IOException {

        System.err.println("Starting json2solr processing...");
        System.err.println("Input file: " + ontologiesJsonPath);
        System.err.println("Output directory: " + outPath);

        // First pass: count total ontologies for progress reporting
        int totalOntologies = countOntologies(ontologiesJsonPath);
        System.err.println("Found " + totalOntologies + " ontologies to process");

        // Create writers once before processing any ontologies
        // They will be shared across all ontologies, rotating only by row count if maxRowsPerFile is set
        RotatingWriter ontologiesWriter = new RotatingWriter(outPath, outputOntologyId, "ontologies", maxRowsPerFile);
        RotatingWriter classesWriter = new RotatingWriter(outPath, outputOntologyId, "classes", maxRowsPerFile);
        RotatingWriter propertiesWriter = new RotatingWriter(outPath, outputOntologyId, "properties", maxRowsPerFile);
        RotatingWriter individualsWriter = new RotatingWriter(outPath, outputOntologyId, "individuals", maxRowsPerFile);
        RotatingWriter autocompleteWriter = new RotatingWriter(outPath, outputOntologyId, "autocomplete", maxRowsPerFile);

        JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(ontologiesJsonPath)));

        reader.beginObject();

        int processedOntologies = 0;

        while (reader.peek() != JsonToken.END_OBJECT) {

            String name = reader.nextName();

            if (name.equals("ontologies")) {

                reader.beginArray();

                while (reader.peek() != JsonToken.END_ARRAY) {

                    reader.beginObject(); // ontology

                    Map<String,Object> ontology = new TreeMap<>();
                    
                    // First pass: read ontologyId to check if we should process this ontology
                    String ontologyId = null;
                    boolean shouldProcess = false;

                    while (reader.peek() != JsonToken.END_OBJECT) {

                        String key = reader.nextName();
                        
                        // Read ontologyId first to determine if we should process
                        if (key.equals("ontologyId")) {
                            ontologyId = gson.fromJson(reader, String.class);
                            ontology.put(key, ontologyId);

                            shouldProcess = outputOntologyId == null || ontologyId.equals(outputOntologyId);

                            // Report progress
                            processedOntologies++;
                            double progressPercent = (double) processedOntologies / totalOntologies * 100;
                            System.err.printf("[%d/%d] (%.1f%%) %s ontology: %s%n", 
                                processedOntologies, totalOntologies, progressPercent,
                                shouldProcess ? "Processing" : "Skipping",
                                ontologyId);
                            
                            if (!shouldProcess) {
                                // Skip the rest of this ontology
                                while (reader.peek() != JsonToken.END_OBJECT) {
                                    reader.nextName();
                                    reader.skipValue();
                                }
                                break;
                            }

                        } else if (key.equals("classes") && shouldProcess) {

                            reader.beginArray();
                            
                            int classCount = 0;
                            while (reader.peek() != JsonToken.END_ARRAY) {
                                classCount++;

                                Map<String, Object> _class = gson.fromJson(reader, Map.class);

                                Map<String, Object> flattenedClass = new TreeMap<>();

                                String entityId = ontologyId + "+class+" + (String) _class.get("iri");

                                flattenedClass.put("id", entityId);

                                flattenProperties(_class, flattenedClass);
                                
                                addEmbeddings(ontologyId, "class", (String) _class.get("iri"), _class, flattenedClass, embeddings);
                                
                                _class.remove("embeddings"); // remains in flattenedClass
                                flattenedClass.put("_json", gson.toJson(_class));

                                classesWriter.println(gson.toJson(flattenedClass));

                                writeAutocompleteEntries(ontologyId, entityId, flattenedClass, autocompleteWriter);
                            
                                if (classCount % 1000 == 0) {
                                    System.err.println("  - Processed " + classCount + " classes...");
                                }
                            }

                            reader.endArray();

                        } else if (key.equals("properties")) {

                            reader.beginArray();
                            
                            int propertyCount = 0;
                            while (reader.peek() != JsonToken.END_ARRAY) {
                                propertyCount++;

                                Map<String, Object> property = gson.fromJson(reader, Map.class);

                                Map<String, Object> flattenedProperty = new TreeMap<>();

                                String entityId = ontologyId + "+property+" + (String) property.get("iri");

                                flattenedProperty.put("id", entityId);

                                flattenProperties(property, flattenedProperty);
                                
                                addEmbeddings(ontologyId, "property", (String) property.get("iri"), property, flattenedProperty, embeddings);
                                
                                property.remove("embeddings");
                                flattenedProperty.put("_json", gson.toJson(property));

                                propertiesWriter.println(gson.toJson(flattenedProperty));

                                writeAutocompleteEntries(ontologyId, entityId, flattenedProperty, autocompleteWriter);
                            }

                            reader.endArray();
                            
                            if (propertyCount > 0) {
                                System.err.println("  - Processed " + propertyCount + " properties");
                            }

                        } else if (key.equals("individuals")) {

                            reader.beginArray();
                            
                            int individualCount = 0;
                            while (reader.peek() != JsonToken.END_ARRAY) {
                                individualCount++;

                                Map<String, Object> individual = gson.fromJson(reader, Map.class);

                                Map<String, Object> flattenedIndividual = new TreeMap<>();

                                String entityId = ontologyId + "+individual+" + (String) individual.get("iri");
                                flattenedIndividual.put("id", entityId);

                                flattenProperties(individual, flattenedIndividual);
                                
                                addEmbeddings(ontologyId, "individual", (String) individual.get("iri"), individual, flattenedIndividual, embeddings);
                                
                                individual.remove("embeddings");
                                flattenedIndividual.put("_json", gson.toJson(individual));

                                individualsWriter.println(gson.toJson(flattenedIndividual));

                                writeAutocompleteEntries(ontologyId, entityId, flattenedIndividual, autocompleteWriter);
                            }

                            reader.endArray();
                            
                            if (individualCount > 0) {
                                System.err.println("  - Processed " + individualCount + " individuals");
                            }

                        } else {
                            ontology.put(key, gson.fromJson(reader, Object.class));
                        }
                    }

                    // Only write ontology document if we processed this ontology
                    if (shouldProcess) {
                        Map<String, Object> flattenedOntology = new TreeMap<>();

                        // don't want to store a copy of all the entities in here too
                        Map<String, Object> ontologyJsonObj = new TreeMap<>();
                        for(String k : ontology.keySet()) {
                            if(k.equals("classes") || k.equals("properties") || k.equals("individuals"))
                                continue;
                            ontologyJsonObj.put(k, ontology.get(k));
                        }

                        flattenedOntology.put("_json", gson.toJson(ontologyJsonObj));
                        flattenedOntology.put("id", ontologyId + "+ontology+" + ontology.get("iri"));

                        flattenProperties(ontology, flattenedOntology);

                        ontologiesWriter.println(gson.toJson(flattenedOntology));
                    }

                    reader.endObject(); // ontology
                }

                reader.endArray();

            } else {

                reader.skipValue();

            }
        }

        reader.endObject();
        reader.close();
        
        // Close all writers (may be null if all ontologies were skipped)
        if (ontologiesWriter != null) ontologiesWriter.close();
        if (classesWriter != null) classesWriter.close();
        if (propertiesWriter != null) propertiesWriter.close();
        if (individualsWriter != null) individualsWriter.close();
        if (autocompleteWriter != null) autocompleteWriter.close();
        
        System.err.println("json2solr processing completed successfully!");
        System.err.println("Processed " + processedOntologies + " ontologies total");
    }

    static private void flattenProperties(Map<String,Object> properties, Map<String,Object> flattened) {

        for (String k : properties.keySet()) {

            Object v = discardMetadata(properties.get(k));
            if(v == null) {
                continue;
            }

            k = k.replace(":", "__");

            if (v instanceof Collection) {
                List<String> flattenedList = new ArrayList<String>();
                for (Object entry : ((Collection<Object>) v)) {
                    Object obj = discardMetadata(entry);
                    if(obj != null) {
                        flattenedList.add(objToString(obj));
                    }
                }
                flattened.put(k, flattenedList);
            } else {
                flattened.put(k, objToString(v));
            }
        }

    }

    // There are 5 cases when the object can be a Map {} instead of a literal.
    //
    //  (1) It's a literal with type information { datatype: ..., value: ... }
    //
    //  (2) It's a class expression
    //
    //  (3) It's a localization, which is a specific case of (1) where a
    //      language and localized value are provided.
    //
    //  (4) It's reification { type: reification|related, ....,  value: ... }
    //
    //  (5) it's some random json object from the ontology config
    // 
    // In the case of (1), we discard the datatype and keep the value
    //
    // In the case of (2), we don't store anything in solr fields. Class
    // expressions should already have been evaluated into separate "related"
    // fields by the RelatedAnnotator in rdf2json.
    //
    // In the case of (3), we create a Solr document for each language (see 
    // above), and the language is passed into this function so we know which
    // language's strings to keep.
    //
    // In the case of (4), we discard any metadata (in Neo4j the metadata is
    // preserved for edges, but in Solr we don't care about it).
    // 
    // In the case of (5) we discard it in solr because json objects won't be
    // querable anyway.
    //
    //  
    public static Object discardMetadata(Object obj) {

        if (obj instanceof Map) {

            Map<String, Object> dict = (Map<String, Object>) obj;

            Object type = dict.get("type");

            if(type == null || !(type instanceof List)) {
            // (2) class expression  or  json junk from the ontology config
            return null;
	    }

	    List<String> types = (List<String>) type;

	    if(types.contains("literal")) {

		// (1) typed literal
                return discardMetadata(dict.get("value"));

	    } else if(types.contains("reification") || types.contains("related")) {

		// (4) reification
                return discardMetadata(dict.get("value"));

	    } else if(types.contains("datatype")) {
            return null;
	    } else {
		    throw new RuntimeException("???");
	    }

        } else {
	
		return obj;
	    }
    }

    public static String objToString(Object obj) {
        if(obj instanceof String) {
            return (String)obj;
        } else {
            return gson.toJson(obj);
        }
    }




   static void writeAutocompleteEntries(String ontologyId, String entityId, Map<String,Object> flattenedEntity, RotatingWriter autocompleteWriter) throws IOException {

	Object labels = flattenedEntity.get(LABEL.getText());

    if (labels instanceof String) {
        labels = (new ArrayList<>()).add(labels);
    }

    for(Object label : (List<Object>) labels) {
        autocompleteWriter.println( gson.toJson(makeAutocompleteEntry(ontologyId, entityId, (String)label), Map.class) );
    }

	Object synonyms = flattenedEntity.get(SYNONYM.getText());

	if(synonyms instanceof List) {
		for(Object label : (List<Object>) synonyms) {
			autocompleteWriter.println( gson.toJson(makeAutocompleteEntry(ontologyId, entityId, (String)label), Map.class) );
		}
	} else if(synonyms instanceof String) {
			autocompleteWriter.println( gson.toJson(makeAutocompleteEntry(ontologyId, entityId, (String)synonyms), Map.class) );
	}
   }

    static Map<String,String> makeAutocompleteEntry(String ontologyId, String entityId, String label) {
        Map<String,String> entry = new LinkedHashMap<>();
        entry.put("ontologyId", ontologyId);
        entry.put("id", entityId);
        entry.put("label", label);
        return entry;
    }

    static void addEmbeddings(String ontologyId, String entityType, String iri, Map<String, Object> sourceEntity, Map<String, Object> flattenedEntity, Map<String, Embeddings> embeddings) {
        if(embeddings == null || embeddings.isEmpty()) {
            return;
        }
        
        for(String modelName : embeddings.keySet()) {
            String embeddingKey = "embeddings_" + modelName;
        
            Embeddings emb = embeddings.get(modelName);
            float[] embeddingsArray = emb.getEmbeddings(ontologyId, entityType, iri);
            
            if(embeddingsArray != null) {
                List<Float> embeddingsList = new ArrayList<>();
                for(float f : embeddingsArray) {
                    embeddingsList.add(f);
                }
                flattenedEntity.put(embeddingKey, embeddingsList);
            }
        }
    }

}
