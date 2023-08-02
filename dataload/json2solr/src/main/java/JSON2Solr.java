import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.apache.commons.cli.*;

import java.io.*;
import java.util.*;

public class JSON2Solr {

    static Gson gson = new Gson();

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option input = new Option(null, "input", true, "ontologies JSON input filename");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option(null, "outDir", true, "output JSON folder path");
        output.setRequired(true);
        options.addOption(output);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("json2solr", options);

            System.exit(1);
            return;
        }

        String inputFilePath = cmd.getOptionValue("input");
        String outPath = cmd.getOptionValue("outDir");

        PrintStream ontologiesWriter = null;
        PrintStream classesWriter = null;
        PrintStream propertiesWriter = null;
        PrintStream individualsWriter = null;
        PrintStream autocompleteWriter = null;


        String ontologiesOutName = outPath + "/ontologies.jsonl";
        String classesOutName = outPath + "/classes.jsonl";
        String propertiesOutName = outPath + "/properties.jsonl";
        String individualsOutName = outPath + "/individuals.jsonl";
        String autocompleteOutName = outPath + "/autocomplete.jsonl";

        ontologiesWriter = new PrintStream(ontologiesOutName);
        classesWriter = new PrintStream(classesOutName);
        propertiesWriter = new PrintStream(propertiesOutName);
        individualsWriter = new PrintStream(individualsOutName);
        autocompleteWriter = new PrintStream(autocompleteOutName);


        JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(inputFilePath)));

        reader.beginObject();

        while (reader.peek() != JsonToken.END_OBJECT) {

            String name = reader.nextName();

            if (name.equals("ontologies")) {

                reader.beginArray();

                while (reader.peek() != JsonToken.END_ARRAY) {

                    reader.beginObject(); // ontology

                    Map<String,Object> ontology = new TreeMap<>();

                    while (reader.peek() != JsonToken.END_OBJECT) {

                        String key = reader.nextName();

                        if (key.equals("classes")) {

                            reader.beginArray();

                            while (reader.peek() != JsonToken.END_ARRAY) {

                                Map<String, Object> _class = gson.fromJson(reader, Map.class);

                                Map<String, Object> flattenedClass = new TreeMap<>();

                                String ontologyId = (String) ontology.get("ontologyId");
                                String entityId = ontologyId + "+class+" + (String) _class.get("iri");

                                flattenedClass.put("_json", gson.toJson(_class));
                                flattenedClass.put("id", entityId);

                                flattenProperties(_class, flattenedClass);

                                classesWriter.println(gson.toJson(flattenedClass));

                                writeAutocompleteEntries(ontologyId, entityId, flattenedClass, autocompleteWriter);
                            }

                            reader.endArray();

                        } else if (key.equals("properties")) {

                            reader.beginArray();

                            while (reader.peek() != JsonToken.END_ARRAY) {

                                Map<String, Object> property = gson.fromJson(reader, Map.class);

                                Map<String, Object> flattenedProperty = new TreeMap<>();

                                String ontologyId = (String) ontology.get("ontologyId");
                                String entityId = ontologyId + "+property+" + (String) property.get("iri");
                                flattenedProperty.put("_json", gson.toJson(property));
                                flattenedProperty.put("id", entityId);

                                flattenProperties(property, flattenedProperty);

                                propertiesWriter.println(gson.toJson(flattenedProperty));

                                writeAutocompleteEntries(ontologyId, entityId, flattenedProperty, autocompleteWriter);
                            }

                            reader.endArray();

                        } else if (key.equals("individuals")) {

                            reader.beginArray();

                            while (reader.peek() != JsonToken.END_ARRAY) {

                                Map<String, Object> individual = gson.fromJson(reader, Map.class);

                                Map<String, Object> flattenedIndividual = new TreeMap<>();

                                String ontologyId = (String) ontology.get("ontologyId");
                                String entityId = ontologyId + "+individual+" + (String) individual.get("iri");
                                flattenedIndividual.put("_json", gson.toJson(individual));
                                flattenedIndividual.put("id", entityId);

                                flattenProperties(individual, flattenedIndividual);

                                individualsWriter.println(gson.toJson(flattenedIndividual));

                                writeAutocompleteEntries(ontologyId, entityId, flattenedIndividual, autocompleteWriter);
                            }

                            reader.endArray();

                        } else {
                            ontology.put(key, gson.fromJson(reader, Object.class));
                        }
                    }

                    String ontologyId = (String) ontology.get("ontologyId");

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

                    reader.endObject(); // ontology
                }

                reader.endArray();

            } else {

                reader.skipValue();

            }
        }

        reader.endObject();
        reader.close();
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




   static void writeAutocompleteEntries(String ontologyId, String entityId, Map<String,Object> flattenedEntity, PrintStream autocompleteWriter) {

	Object labels = flattenedEntity.get("label");

	if(labels instanceof List) {
		for(Object label : (List<Object>) labels) {
			autocompleteWriter.println( gson.toJson(makeAutocompleteEntry(ontologyId, entityId, (String)label), Map.class) );
		}
	} else if(labels instanceof String) {
			autocompleteWriter.println( gson.toJson(makeAutocompleteEntry(ontologyId, entityId, (String)labels), Map.class) );
	}

	Object synonyms = flattenedEntity.get("synonym");

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


}


