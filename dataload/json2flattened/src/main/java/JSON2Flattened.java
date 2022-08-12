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

    // Fields that we never want to query, so shouldn't be added to the flattened
    // objects. We can still access them via the API because they will be stored
    // in the "_json" string field.
    // 
    public static final Set<String> DONT_INDEX_FIELDS = Set.of(
        "propertyLabels", "classes", "properties", "individuals"
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

        for (String k : obj.keySet()) {

            if(DONT_INDEX_FIELDS.contains(k))
                continue;

            Object v = flatten(obj.get(k));
            if(v == null) {
                continue;
            }

            writer.name(k);
            writeGenericValue(writer, v);
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
                Object flattened = flatten(entry);
                if(flattened != null)
                    flattenedList.add(flattened);
            }
            return flattenedList;
        }

        // If this is not a Map {} it's a plain old literal value so we have
        // nothing to do.
        // 
        if (! (obj instanceof Map)) {
            return obj;
        }


        Map<String, Object> dict = new TreeMap<String,Object>( (Map<String, Object>) obj );

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
                Object flattened = flatten(dict.get("value"));

                if(flattened == null)
                    return null;

                Map<String, Object> res = new TreeMap<>(dict);
                res.put("value", flattened);
                return res;
            }

        } else {

            // This is (2) A class expression
            // Let's flatten it!

            return flattenClassExpression(dict);
            
        }
    }


    private static Object flattenClassExpression(Map<String, Object> expr) {

        Collection<Object> types = asCollection(expr.get("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"));

        if(types.contains("http://www.w3.org/2002/07/owl#Restriction")) {

            Object hasValue = expr.get("http://www.w3.org/2002/07/owl#hasValue");

            if(hasValue != null) {
                return flatten(hasValue);
            }

            Object someValuesFrom = expr.get("http://www.w3.org/2002/07/owl#someValuesFrom");

            if(someValuesFrom != null) {
                return flatten(someValuesFrom);
            }

            Object allValuesFrom = expr.get("http://www.w3.org/2002/07/owl#allValuesFrom");

            if(allValuesFrom != null) {
                return flatten(allValuesFrom);
            }

        } else if(types.contains("http://www.w3.org/2002/07/owl#Class")) {

            Collection<Object> oneOf = asCollection(expr.get("http://www.w3.org/2002/07/owl#oneOf"));

            if(oneOf != null && oneOf.size() > 0) {
                return oneOf.stream()
                            .map(obj -> flatten(obj))
                            .filter(obj -> obj != null)
                            .collect(Collectors.toList());
            }

            Collection<Object> intersectionOf = asCollection(expr.get("http://www.w3.org/2002/07/owl#intersectionOf"));

            if(intersectionOf != null && intersectionOf.size() > 0) {
                return intersectionOf.stream()
                            .map(obj -> flatten(obj))
                            .filter(obj -> obj != null)
                            .collect(Collectors.toList());
            }

            Collection<Object> unionOf = asCollection(expr.get("http://www.w3.org/2002/07/owl#unionOf"));

            if(unionOf != null && unionOf.size() > 0) {
                return unionOf.stream()
                            .map(obj -> flatten(obj))
                            .filter(obj -> obj != null)
                            .collect(Collectors.toList());
            }

        }

        // Could be: cardinality, complementOf, any others we don't deal with yet...
        //
        return null;

        //throw new RuntimeException("Unknown class expression: " + gson.toJson(expr, Map.class));
    }


    public static String objToString(Object obj) {
        if(obj instanceof String) {
            return (String)obj;
        } else {
            return gson.toJson(obj);
        }
    }

    private static Collection<Object> asCollection(Object val) {

        if(val instanceof Collection)
            return (Collection<Object>) val;
        else
            return Arrays.asList(val);
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


