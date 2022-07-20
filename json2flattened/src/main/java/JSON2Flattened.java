import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
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

        writer.beginObject();
        reader.beginObject();

        while (reader.peek() != JsonToken.END_OBJECT) {

            String name = reader.nextName();

            if (name.equals("ontologies")) {

                writer.name("ontologies");
                writer.beginArray();


                boolean wroteOntologyProperties = false;


                reader.beginArray();
                while (reader.peek() != JsonToken.END_ARRAY) {


                    writer.beginObject();

                    Map<String,Object> ontology = new HashMap<>();

                    reader.beginObject(); // ontology
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

                            writer.name("classes");

                            writer.beginArray();
                            reader.beginArray();

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
                                ontology.put(key, gson.fromJson(reader, Object.class));
                            }
                        }
                    }
                    reader.endObject(); // ontology


                    writer.endObject();
                }
                reader.endArray();


                writer.endArray();

            } else {

                reader.skipValue();

            }
        }

        reader.endObject();
        writer.endObject();

        reader.close();
        writer.close();
    }

    static private void writeFlattenedObjectProperties(JsonWriter writer, Map<String,Object> obj) throws IOException {

        writer.name("_json");
        writer.value(gson.toJson(obj));

        for (String k : obj.keySet()) {

            if(DONT_INDEX_FIELDS.contains(k))
                continue;

            Object v = flatten(obj.get(k));
            if(v == null) {
                continue;
            }

            writer.name(k);

            if (v instanceof Collection) {

                writer.beginArray();

                for (Object entry : ((Collection<Object>) v)) {
                    Object entryObj = flatten(entry);
                    if(entryObj != null) {
                        writer.value(objToString(entryObj));
                    }
                }

                writer.endArray();

            } else {

                writer.value(objToString(v));

            }
        }

    }

    // There are four cases when the object can be a Map {} instead of a literal.
    //
    //  (1) It's a value with type information { datatype: ..., value: ... }
    //
    //  (2) It's a class expression
    //
    //  (3) It's a localization, which is a specific case of (1) where a
    //      language and localized value are provided.
    //
    //  (4) It's reification { type: Axiom, ....,  value: ... }
    //
    // The job of this flattener is to ditch all of the metadata associated with
    // (1) and (2), leaving just the raw value. The metadata is preserved in a
    // field called "_json", which stores the entire object prior to flattening,
    // so the original information can still be returned by the API.
    //
    // The reason we ditch it is because it would be problematic in both Neo4j
    // and Solr: if it's a complex JSON object we can't query the values. So we
    // want to leave the values and nothing more.
    //
    // The reason we don't deal with (3) and (4) is that Neo4j and Solr deal
    // with them in different ways. Neo4j wants reification info for edge
    // properties, and both Solr and Neo4j need the localized strings.
    //
    //
    public static Object flatten(Object obj) {

        if (obj instanceof Collection) {
            List<Object> flattenedList = new ArrayList<>();
            for (Object entry : ((Collection<Object>) obj)) {
                flattenedList.add(flatten(entry));
            }
            return flattenedList;
        }


        // Is this a Map {}, rather than just a plain old value?
        // 
        if (obj instanceof Map) {

            Map<String, Object> dict = (Map<String, Object>) obj;


            // Does the Map have a field called `value`? If so, it's one of:
            //
            // (1) A value with type information { datatype: ..., value: ... }
            // (3) A localization
            // (4) Reification
            //
            // But it's _not_   (2) A class expression
            //
            if (dict.containsKey("value")) {

                if(dict.containsKey("datatype") && !dict.containsKey("lang")) {

                    // This is (1) A value with type information.
                    // Just return the value with any metadata discarded.

                    return flatten(dict.get("value"));

                } else {

                    // This is (3) a localization or (4) reification. We do not
                    // process these in the flattener. However, we still need
                    // to recursively process the value.
                    //  
                    Map<String, Object> res = new HashMap<>(dict);
                    res.put("value", flatten(dict.get("value")));
                    return res;

                }

            } else {

                // This is (2) A class expression
                // TBD!
                
                return objToString(obj);
                
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


