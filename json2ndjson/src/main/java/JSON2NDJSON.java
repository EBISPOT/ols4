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

public class JSON2NDJSON {

    static Gson gson = new Gson();

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option input = new Option(null, "input", true, "ontologies JSON input filename");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option(null, "outDir", true, "output NDJSON folder path");
        output.setRequired(true);
        options.addOption(output);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("json2csv", options);

            System.exit(1);
            return;
        }

        String inputFilePath = cmd.getOptionValue("input");
        String outPath = cmd.getOptionValue("outDir");

        PrintStream ontologiesWriter = null;
        PrintStream classesWriter = null;


                            String ontologiesOutName = outPath + "/ontologies.ndjson";
                            String classesOutName = outPath + "/classes.ndjson";

                            ontologiesWriter = new PrintStream(ontologiesOutName);
                            classesWriter = new PrintStream(classesOutName);


        JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(inputFilePath)));

        reader.beginObject();

        while(reader.peek() != JsonToken.END_OBJECT) {

            String name = reader.nextName();

            if (name.equals("ontologies")) {

                reader.beginArray();

                while(reader.peek() != JsonToken.END_ARRAY) {

                    reader.beginObject(); // ontology

                    JsonOntology ontology = new JsonOntology();

                    while(reader.peek() != JsonToken.END_OBJECT) {

                        String key = reader.nextName();

                        if(key.equals("ontologyConfig")) {

                            ontology.ontologyConfig = gson.fromJson(reader, Map.class);

                        } else if(key.equals("ontologyProperties")) {
                            ontology.ontologyProperties = gson.fromJson(reader, Map.class);
                        } else if(key.equals("classes")) {

                            reader.beginArray();

                            while(reader.peek() != JsonToken.END_ARRAY) {

                                Map<String,Object> _class = gson.fromJson(reader, Map.class);
                                //classesWriter.println("{\"index\": {\"_index\": \"owl_classes\"}}");

                                // Stringify any nested objects
                                //
                                
                                Map<String,Object> flattenedClass = new HashMap<>();
                                for(String k : _class.keySet()) {

                                    Object v = discardMetadata( _class.get(k) );

                                    if(v instanceof Collection) {
                                        List<String> flattenedList = new ArrayList<String>();
                                        for(Object entry : ((Collection<Object>) v)) {
                                                flattenedList.add( objToString( discardMetadata(entry)));
                                        }
                                        flattenedClass.put(k, flattenedList);
                                    } else {
                                        flattenedClass.put(k, objToString(v));
                                    }
                                }

                                //classesWriter.println(gson.toJson(flattenedClass));
                                classesWriter.println(gson.toJson(flattenedClass));



                            }

                            reader.endArray();

                        } else {
                            reader.skipValue();
                        }



                    }

                    ontologiesWriter.println(gson.toJson(ontology));

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
    public static Object discardMetadata(Object obj) {

        if(obj instanceof Map) {
            Map<String,Object> dict = (Map<String,Object>) obj;
            if(dict.containsKey("value")) {
                return discardMetadata(dict.get("value"));
            }
        }

        return obj;
    }

    public static String objToString(Object obj) {
        if(obj instanceof String) {
            return (String)obj;
        } else {
            return gson.toJson(obj);
        }
    }

}


