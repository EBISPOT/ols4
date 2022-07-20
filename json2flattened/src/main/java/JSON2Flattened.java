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

public class JSON2Flattened {

    static Gson gson = new Gson();

    // Fields that we never want to query, so shouldn't be added to the flattened
    // objects. We can still access them via the API because they will be stored
    // in the "_json" string field.
    // 
    public static final Set<String> DONT_INDEX_FIELDS = Set.of(
        "ontologyConfig", "propertyLabels", "classes", "properties", "individuals"
    );

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option input = new Option(null, "input", true, "ontologies JSON input filename");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option(null, "output", true, "ontologies JSON output filename");
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
        String outputFilePath = cmd.getOptionValue("output");

        JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(inputFilePath)));

        JsonWriter writer = new JsonWriter(new FileWriter(outputFilePath));
        writer.setIndent("  ");


        reader.beginObject();

        while (reader.peek() != JsonToken.END_OBJECT) {

            String name = reader.nextName();

            if (name.equals("ontologies")) {

                reader.beginArray();

                writer.name("ontologies");
                writer.beginArray();



                boolean wroteOntologyProperties = false;

                while (reader.peek() != JsonToken.END_ARRAY) {

                    reader.beginObject(); // ontology
                    writer.beginObject();

                    writeFlattenedObject(writer, property);

                    while (reader.peek() != JsonToken.END_OBJECT) {

                        String key = reader.nextName();

                        if (key.equals("classes") ||
                                key.equals("properties") ||
                                key.equals("individuals")) {

                            if(!wroteOntologyProperties) {
                                writeFlattenedObjectProperties(writer, ontology);
                                wroteOntologyProperties = true;
                            }
                        }

                        if (key.equals("classes")) {

                            reader.beginArray();

                            writer.name("classes");
                            writer.beginArray();

                            while (reader.peek() != JsonToken.END_ARRAY) {
                                Map<String, Object> _class = gson.fromJson(reader, Map.class);
                                writer.beginObject();
                                writeFlattenedObjectProperties(writer, _class);
                                writer.endObject();
                            }

                            reader.endArray();
                            writer.endArray();

                        } else if (key.equals("properties")) {

                            reader.beginArray();

                            writer.name("properties");
                            writer.beginArray();

                            while (reader.peek() != JsonToken.END_ARRAY) {
                                Map<String, Object> property = gson.fromJson(reader, Map.class);
                                writer.beginObject();
                                writeFlattenedObjectProperties(writer, property);
                                writer.endObject();
                            }

                            reader.endArray();
                            writer.endArray();

                        } else if (key.equals("individuals")) {

                            reader.beginArray();

                            writer.name("individuals");
                            writer.beginArray();

                            while (reader.peek() != JsonToken.END_ARRAY) {

                                Map<String, Object> individual = gson.fromJson(reader, Map.class);
                                writer.beginObject();
                                writeFlattenedObjectProperties(writer, individual);
                                writer.endObject();
                            }

                            reader.endArray();
                            writer.endArray();

                        } else {

                            if(wroteOntologyProperties) {
                                throw new RuntimeException("found ontology metadata after classes/properties/individuals lists");
                            } else {
                                ontology.put(key, gson.fromJson(reader));
                            }
                        }
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

    static private void writeFlattenedObjectProperties(JsonWriter writer, Map<String,Object> obj) {

        writer.name("_json");
        writer.value(gson.toJson(obj));

        for (String k : properties.keySet()) {

            if(DONT_INDEX_FIELDS.contains(k))
                continue;

            Object v = discardMetadata(properties.get(k), lang);
            if(v == null) {
                continue;
            }

            writer.name(k);

            if (v instanceof Collection) {

                writer.beginArray();

                for (Object entry : ((Collection<Object>) v)) {
                    Object obj = discardMetadata(entry, lang);
                    if(obj != null) {
                        writer.value(objToString(obj));
                    }
                }

                writer.endArray();

            } else {

                writer.value(objToString(v));

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


    public static String objToString(Object obj) {
        if(obj instanceof String) {
            return (String)obj;
        } else {
            return gson.toJson(obj);
        }
    }

}


