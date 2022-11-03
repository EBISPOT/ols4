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
import java.util.*;
import java.util.stream.Collectors;

public class JSON2Flattened {

    static Gson gson = new Gson();

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



                reader.beginArray();
                while (reader.peek() != JsonToken.END_ARRAY) {


                    writer.beginObject();

                    Map<String,Object> ontology = new TreeMap<>();
                    boolean wroteOntologyProperties = false;

                    reader.beginObject(); // ontology
                    while (reader.peek() != JsonToken.END_OBJECT) {

                        String key = reader.nextName();

                        if (key.equals("classes") ||
                                key.equals("properties") ||
                                key.equals("individuals")) {

                            if(!wroteOntologyProperties) {

                                System.out.println("Write ontology properties: " + (String)ontology.get("ontologyId"));

                                writeFlattenedObjectProperties(writer, ontology);
                                wroteOntologyProperties = true;
                            }
                        }

                        if (key.equals("classes")) {

                            writer.name("classes");

                            writer.beginArray();
                            reader.beginArray();

                            while (reader.peek() != JsonToken.END_ARRAY) {
                                Map<String, Object> _class = gson.fromJson(reader, TreeMap.class);
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
                                Map<String, Object> property = gson.fromJson(reader,  TreeMap.class);
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

                                Map<String, Object> individual = gson.fromJson(reader, TreeMap.class);
                                writer.beginObject();
                                writeFlattenedObjectProperties(writer, individual);
                                writer.endObject();
                            }

                            reader.endArray();
                            writer.endArray();

                        } else {

                            if(wroteOntologyProperties) {
                                throw new RuntimeException("found ontology metadata after classes/properties/individuals lists: " + key);
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


        Map<String,Object> objFlattened = (Map<String,Object>) Flattener.flatten(obj);

        for (String k : objFlattened.keySet()) {
            writer.name(k);
            writeGenericValue(writer, objFlattened.get(k));
        }
    }

    private static void writeGenericValue(JsonWriter writer, Object val) throws IOException {

        if(val instanceof Collection) {
            writer.beginArray();
            for(Object entry : ((Collection<Object>) val)) {
                writeGenericValue(writer, entry);
            }
            writer.endArray();
        } else if(val instanceof Map) {
            Map<String,Object> map = new TreeMap<String,Object> ( (Map<String,Object>) val );
            writer.beginObject();
            for(String k : map.keySet()) {
                writer.name(k);
                writeGenericValue(writer, map.get(k));
            }
            writer.endObject();
        } else if(val instanceof String) {
            writer.value((String) val);
        } else if(val instanceof Long) {
            writer.value((Long) val);
        } else if(val instanceof Integer) {
            writer.value((Integer) val);
        } else if(val instanceof Double) {
            writer.value((Double) val);
        } else if(val instanceof Boolean) {
            writer.value((Boolean) val);
        } else if(val == null) {
            writer.nullValue();
        } else {
            throw new RuntimeException("Unknown value type");
        }

    }


}


