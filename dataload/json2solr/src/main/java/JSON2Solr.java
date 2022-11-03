import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.jena.vocabulary.RDF;

import java.io.*;
import java.nio.charset.Charset;
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


        String ontologiesOutName = outPath + "/ontologies.jsonl";
        String classesOutName = outPath + "/classes.jsonl";
        String propertiesOutName = outPath + "/properties.jsonl";
        String individualsOutName = outPath + "/individuals.jsonl";

        ontologiesWriter = new PrintStream(ontologiesOutName);
        classesWriter = new PrintStream(classesOutName);
        propertiesWriter = new PrintStream(propertiesOutName);
        individualsWriter = new PrintStream(individualsOutName);


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

                                Set<String> languages = new TreeSet<>();
                                languages.add("en");
                                for(String k : _class.keySet()) {
                                    languages.addAll(extractLanguages(_class.get(k)));
                                }


                                // Create 1 document per language
                                //
                                for(String lang : languages) {

                                    // Stringify any nested objects
                                    //
                                    Map<String, Object> flattenedClass = new TreeMap<>();

                                    String ontologyId = (String) ontology.get("ontologyId");
                                    flattenedClass.put("lang", lang);
                                    flattenedClass.put("id", lang + "_" + ontologyId + "+class+" + (String) _class.get("iri"));

                                    flattenProperties(_class, flattenedClass, lang);

                                    classesWriter.println(gson.toJson(flattenedClass));

                                }



                            }

                            reader.endArray();

                        } else if (key.equals("properties")) {

                            reader.beginArray();

                            while (reader.peek() != JsonToken.END_ARRAY) {

                                Map<String, Object> property = gson.fromJson(reader, Map.class);

                                Set<String> languages = new TreeSet<>();
                                languages.add("en");
                                for(String k : property.keySet()) {
                                    languages.addAll(extractLanguages(property.get(k)));
                                }


                                // Create 1 document per language
                                //
                                for(String lang : languages) {

                                    // Stringify any nested objects
                                    //
                                    Map<String, Object> flattenedProperty = new TreeMap<>();

                                    String ontologyId = (String) ontology.get("ontologyId");
                                    flattenedProperty.put("lang", lang);
                                    flattenedProperty.put("id", lang + "_" + ontologyId + "+property+" + (String) property.get("iri"));

                                    flattenProperties(property, flattenedProperty, lang);

                                    propertiesWriter.println(gson.toJson(flattenedProperty));

                                }



                            }

                            reader.endArray();

                        } else if (key.equals("individuals")) {

                            reader.beginArray();

                            while (reader.peek() != JsonToken.END_ARRAY) {

                                Map<String, Object> individual = gson.fromJson(reader, Map.class);
                                //classesWriter.println("{\"index\": {\"_index\": \"owlindividuales\"}}");


                                Set<String> languages = new TreeSet<>();
                                languages.add("en");
                                for(String k : individual.keySet()) {
                                    languages.addAll(extractLanguages(individual.get(k)));
                                }


                                // Create 1 document per language
                                //
                                for(String lang : languages) {

                                    // Stringify any nested objects
                                    //
                                    Map<String, Object> flattenedIndividual = new TreeMap<>();

                                    String ontologyId = (String) ontology.get("ontologyId");
                                    flattenedIndividual.put("lang", lang);
                                    flattenedIndividual.put("id", lang + "_" + ontologyId + "+individual+" + (String) individual.get("iri"));

                                    flattenProperties(individual, flattenedIndividual, lang);

                                    individualsWriter.println(gson.toJson(flattenedIndividual));

                                }



                            }

                            reader.endArray();

                        } else {
                            ontology.put(key, gson.fromJson(reader, Object.class));
                        }
                    }

                    Set<String> languages = new TreeSet<>();
                    languages.add("en");
                    for(String k : ontology.keySet()) {
                        languages.addAll(extractLanguages(ontology.get(k)));
                    }

                    for(String lang : languages) {

                        String ontologyId = (String) ontology.get("ontologyId");


                        Map<String, Object> flattenedOntology = new TreeMap<>();

                        // don't want to store a copy of all the entities in here too
                        Map<String, Object> ontologyJsonObj = new TreeMap<>();
                        for(String k : ontology.keySet()) {
                            if(k.equals("classes") || k.equals("properties") || k.equals("individuals"))
                                continue;
                            ontologyJsonObj.put(k, ontology.get(k));
                        }

                        flattenedOntology.put("id", lang + "_" + ontologyId + "+ontology+" + ontology.get("iri"));
                        flattenedOntology.put("lang", lang);

                        flattenProperties(ontology, flattenedOntology, lang);

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
    }

    static private void flattenProperties(Map<String,Object> properties, Map<String,Object> flattened, String lang) {

        for (String k : properties.keySet()) {

            Object v = discardMetadata(properties.get(k), lang);
            if(v == null) {
                continue;
            }

            k = k.replace(":", "__");

            if (v instanceof Collection) {
                List<String> flattenedList = new ArrayList<String>();
                for (Object entry : ((Collection<Object>) v)) {
                    Object obj = discardMetadata(entry, lang);
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

    // There are 4 cases when the object can be a Map {} instead of a literal.
    //
    //  (1) It's a value with type information { datatype: ..., value: ... }
    //
    //  (2) It's a class expression
    //
    //  (3) It's a localization, which is a specific case of (1) where a
    //      language and localized value are provided.
    //
    //  (4) It's reification { type: Axiom|Restriction, ....,  value: ... }
    // 
    // The JSON provided to json2solr has been preprocessed by the flattener,
    // so (1) and (2) have already been evaluated. However, (3) and (4) are up
    // to us.
    //
    // In the case of (3), we create a Solr document for each language (see 
    // above), and the language is passed into this function so we know which
    // language's strings to keep.
    //
    // In the case of (4), we discard any metadata (in Neo4j the metadata is
    // preserved for edges, but in Solr we don't care about it).
    //
    //  
    public static Object discardMetadata(Object obj, String lang) {

        if (obj instanceof Map) {
            Map<String, Object> dict = (Map<String, Object>) obj;
            if (dict.containsKey("value")) {
                if(dict.containsKey("lang")) {
                    String valLang = (String)dict.get("lang");
                    assert(valLang != null);
                    if(! (valLang.equals(lang))) {
                        return null;
                    }
                }
                return discardMetadata(dict.get("value"), lang);
            }
        }

        return obj;
    }

    // Gather all of the lang: attributes from an object and all of its descendants
    //
    public static Collection<String> extractLanguages(Object obj) {

        Set<String> langs = new TreeSet<>();

        if (obj instanceof Map) {

            Map<String, Object> mapObj = (Map<String, Object>) obj;

            if (mapObj.containsKey("lang")) {
                langs.add((String) mapObj.get("lang"));
            }

            for (String k : mapObj.keySet()) {

                Object value = mapObj.get(k);

                langs.addAll(extractLanguages(value));
            }

            return langs;
        }

        if(obj instanceof List) {
            for(Object obj2 : (List<Object>) obj) {
                langs.addAll(extractLanguages(obj2));
            }
        }

        return langs;
    }

    public static String objToString(Object obj) {
        if(obj instanceof String) {
            return (String)obj;
        } else {
            return gson.toJson(obj);
        }
    }

}


