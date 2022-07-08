import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.jena.vocabulary.RDF;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

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


        String ontologiesOutName = outPath + "/ontologies.jsonl";
        String classesOutName = outPath + "/classes.jsonl";

        ontologiesWriter = new PrintStream(ontologiesOutName);
        classesWriter = new PrintStream(classesOutName);


        JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(inputFilePath)));

        reader.beginObject();

        while (reader.peek() != JsonToken.END_OBJECT) {

            String name = reader.nextName();

            if (name.equals("ontologies")) {

                reader.beginArray();

                while (reader.peek() != JsonToken.END_ARRAY) {

                    reader.beginObject(); // ontology

                    JsonOntology ontology = new JsonOntology();

                    while (reader.peek() != JsonToken.END_OBJECT) {

                        String key = reader.nextName();

                        if (key.equals("ontologyConfig")) {

                            ontology.ontologyConfig = gson.fromJson(reader, Map.class);

                        } else if (key.equals("ontologyProperties")) {
                            ontology.ontologyProperties = gson.fromJson(reader, Map.class);
                        } else if (key.equals("classes")) {

                            reader.beginArray();

                            while (reader.peek() != JsonToken.END_ARRAY) {

                                Map<String, Object> _class = gson.fromJson(reader, Map.class);
                                //classesWriter.println("{\"index\": {\"_index\": \"owl_classes\"}}");


                                Set<String> languages = new HashSet<>();
                                languages.add("en");
                                for(String k : _class.keySet()) {
                                    languages.addAll(extractLanguages(_class.get(k)));
                                }


                                // Create 1 document per language
                                //
                                for(String lang : languages) {

                                    // Stringify any nested objects
                                    //
                                    Map<String, Object> flattenedClass = new HashMap<>();

                                    String ontologyId = (String) ontology.ontologyConfig.get("id");
                                    flattenedClass.put("lang", lang);
                                    flattenedClass.put("ontology_id", ontologyId);
                                    flattenedClass.put("id", ontologyId + "+" + lang + "+" + (String) _class.get("uri"));
                                    flattenedClass.put("propertyLabels", gson.toJson(_class.get("propertyLabels")));

                                    flattenProperties(_class, flattenedClass, lang);

                                    //classesWriter.println(gson.toJson(flattenedClass));
                                    classesWriter.println(gson.toJson(flattenedClass));

                                }



                            }

                            reader.endArray();

                        } else {
                            reader.skipValue();
                        }
                    }

                    Set<String> languages = new HashSet<>();
                    languages.add("en");
                    for(String k : ontology.ontologyProperties.keySet()) {
                        languages.addAll(extractLanguages(ontology.ontologyProperties.get(k)));
                    }

                    for(String lang : languages) {

                        Map<String, Object> flattenedOntology = new HashMap<>();
                        flattenedOntology.put("id", ontology.ontologyConfig.get("id"));
                        flattenedOntology.put("lang", lang);
                        flattenedOntology.put("ontologyConfig", gson.toJson(ontology.ontologyConfig)); 
                        flattenedOntology.put("propertyLabels", gson.toJson(ontology.ontologyProperties.get("propertyLabels"))); 

                        flattenProperties(ontology.ontologyProperties, flattenedOntology, lang);

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

            if(k.equals("propertyLabels"))
                continue;

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

    // Where the JSON has type information or Axiom information (metadata about
    // a property), that is, the two forms:
    //
    //  { datatype: ..., value: ... }
    //
    //  or
    //
    //  { type: Axiom, ....,  value: ... }
    //
    //  We want to discard this, because it's not useful for the full text
    //  indexing and would mean we would have loads of JSON in the index
    //  instead of actual values.
    //  
    //  The metadata is still stored in Neo4j, but here we just read the "value"
    //  and discard everything else.
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

        Set<String> langs = new HashSet<>();

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


