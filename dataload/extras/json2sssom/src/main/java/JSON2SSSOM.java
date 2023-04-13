import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

public class JSON2SSSOM {

    static Gson gson = new Gson();
    static JsonParser jsonParser = new JsonParser();

    static final List<String> tsvHeader = List.of(
            "subject_id",
            "predicate_id",
            "object_id",
            "mapping_justification",
            "subject_label",
            "object_label",
            "comment"
    );

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option input = new Option(null, "input", true, "pre-linked ontologies JSON input filename");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option(null, "output", true, "output SSSOM TSV filename");
        output.setRequired(true);
        options.addOption(output);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("json2sssom", options);

            System.exit(1);
            return;
        }

        String inputFilePath = cmd.getOptionValue("input");
        String outputFilePath = cmd.getOptionValue("output");


//        Map<String,Map<String,JsonElement>> ontologyConfigs = loadOntologyConfigs(inputFilePath); // ~10 min
        Map<String,Map<String,JsonElement>> ontologyConfigs = Map.of();



        JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(inputFilePath)));
        CSVPrinter writer = CSVFormat.MONGODB_TSV.withHeader(tsvHeader.toArray(new String[0])).print(new File(outputFilePath), Charset.defaultCharset());

        reader.beginObject();
        while(reader.peek() != JsonToken.END_OBJECT) {
            String name = reader.nextName();
            if(name.equals("ontologies")) {
                reader.beginArray();

                while(reader.peek() != JsonToken.END_ARRAY) {

                    reader.beginObject();

                    while(reader.peek() != JsonToken.END_OBJECT) {
                        String propName = reader.nextName();

                        if(propName.equals("classes") || propName.equals("properties") || propName.equals("individuals")) {
                            reader.beginArray();

                            while(reader.peek() != JsonToken.END_ARRAY) {
                                JsonElement entity = jsonParser.parse(reader);
                                writeMappingsForEntity(entity.getAsJsonObject(), writer);
                            }

                            reader.endArray();
                        } else {
                            reader.skipValue();
                        }
                    }


                    reader.endObject();
                }

                reader.endArray();
            }
        }
        reader.endObject();

        writer.close(true);
    }

    public static void writeMappingsForEntity(JsonObject entity, CSVPrinter writer) throws IOException {

        JsonElement exactMatch = entity.get("http://www.w3.org/2004/02/skos/core#exactMatch");
        if(exactMatch != null) {
            writeMappingsForEntity(entity, "skos:exactMatch", exactMatch, null, writer);
        }
        JsonElement hasDbXref = entity.get("http://www.geneontology.org/formats/oboInOwl#hasDbXref");
        if(hasDbXref != null) {
            writeMappingsForEntity(entity, "oboInOwl:hasDbXref", hasDbXref, null, writer);
        }
//        JsonElement equivalentClass = entity.get("http://www.w3.org/2002/07/owl#equivalentClass");
//        if(equivalentClass != null) {
//            writeEquivalentClassMappingsForEntity(entity, equivalentClass, writer);
//        }
    }

    public static void writeMappingsForEntity(JsonObject entity, String predicate, JsonElement mappingValue, JsonObject reificationMetadata, CSVPrinter writer) throws IOException {

        JsonObject linkedEntities = entity.getAsJsonObject("linkedEntities");

        if(mappingValue.isJsonArray()) {
            for(JsonElement entry : mappingValue.getAsJsonArray()) {
                if(entry.isJsonObject()) {
                    writeMappingsForEntity(entry.getAsJsonObject(), writer);
                }
            }
        } else if(mappingValue.isJsonObject()) {
            JsonObject exactMatchObj = mappingValue.getAsJsonObject();
            List<String> types = jsonArrayToStrings(exactMatchObj.getAsJsonArray("type"));
            if(types.contains("reification")) {
                JsonElement value = exactMatchObj.get("value");
                writeMappingsForEntity(entity, predicate, value, exactMatchObj, writer);
            } else if(types.contains("literal")) {
                JsonElement value = exactMatchObj.get("value");
                writeMappingsForEntity(entity, predicate, value, null, writer);
            }
        } else if(mappingValue.isJsonPrimitive()) {

            // all mappings should eventually end up here
            // reificationMetadata may be null

            String value = mappingValue.getAsString();

            JsonObject linkedEntity = linkedEntities.getAsJsonObject(value);

            if(linkedEntity == null) {
                return;
            }

            if(!linkedEntity.has("curie")) {
                return;
            }

            String[] record = new String[tsvHeader.size()];

            for(int i = 0; i < tsvHeader.size(); ++ i) {
                switch(tsvHeader.get(i)) {
                    case "subject_id":
                        record[i] = getFirstStringValue(entity.get("curie"));
                        break;
                    case "predicate_id":
                        record[i] = predicate;
                        break;
                    case "object_id":
                        record[i] = getFirstStringValue(linkedEntity.get("curie"));
                        break;
                    case "mapping_justification":
                        break;
                    case "subject_label":
                        record[i] = getFirstStringValue( entity.get("label") );
                        break;
                    case "object_label":
                        if(linkedEntity.has("label")) {
                            record[i] = getFirstStringValue( linkedEntity.get("label") );
                        }
                        break;
                    case "comment":
                        record[i] = "extracted from " + getFirstStringValue(entity.get("ontologyId"));
                        break;
                    default:
                        record[i] = "";
                        break;
                }
            }

            writer.printRecord(record);
        }
    }

//    public static void writeEquivalentClassMappingsForEntity(JsonObject entity, JsonElement mappingValue, CSVPrinter writer) throws IOException {
//
//        if(mappingValue.isJsonArray()) {
//            for(JsonElement entry : mappingValue.getAsJsonArray()) {
//                if(entry.isJsonObject()) {
//                    writeEquivalentClassMappingsForEntity(entity, entry.getAsJsonObject(), writer);
//                }
//            }
//            return;
//        }
//
//    }

//    public static Map<String,Map<String,JsonElement>> loadOntologyConfigs(String jsonFilename) throws IOException {
//
//        Map<String,Map<String,JsonElement>> res = new LinkedHashMap<>();
//        JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(jsonFilename)));
//
//        reader.beginObject();
//
//        while(reader.peek() != JsonToken.END_OBJECT) {
//            String name = reader.nextName();
//            if(name.equals("ontologies")) {
//                reader.beginArray();
//
//                while(reader.peek() != JsonToken.END_ARRAY) {
//
//                    reader.beginObject();
//
//                    Map<String,JsonElement> ontology = new LinkedHashMap<>();
//
//                    while(reader.peek() != JsonToken.END_OBJECT) {
//                        String propName = reader.nextName();
//
//                        if(propName.equals("classes") || propName.equals("properties") || propName.equals("individuals")) {
//                            reader.skipValue();
//                        } else {
//                            ontology.put(propName, jsonParser.parse(reader));
//                        }
//                    }
//
//                    res.put(ontology.get("ontologyId").getAsString(), ontology);
//
//                    reader.endObject();
//                }
//
//                reader.endArray();
//            }
//        }
//        reader.endObject();
//
//        return res;
//    }

    private static List<String> jsonArrayToStrings(JsonArray arr) {
        String[] strs = new String[arr.size()];
        for(int i = 0; i < arr.size(); ++ i) {
            strs[i] = arr.get(i).getAsString();
        }
        return Arrays.asList(strs);
    }

    private static String getFirstStringValue(JsonElement json) {
        if(json.isJsonArray()) {
            return getFirstStringValue( json.getAsJsonArray().get(0) );
        } else if(json.isJsonObject()) {
            return getFirstStringValue( json.getAsJsonObject().get("value") );
        } else {
            return json.getAsString();
        }
    }


}


