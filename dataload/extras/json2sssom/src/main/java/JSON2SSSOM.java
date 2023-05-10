import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JSON2SSSOM {

    static Gson gson = new Gson();
    static JsonParser jsonParser = new JsonParser();

    static final List<String> tsvHeader = List.of(
            "subject_id",
            "predicate_id",
            "object_id",
            "mapping_justification",
            "subject_label",
            "object_label"
//            "comment"
    );

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option input = new Option(null, "input", true, "pre-linked ontologies JSON input filename");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option(null, "outDir", true, "output directory for SSSOM TSV files");
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
        String outputFilePath = cmd.getOptionValue("outDir");


//        Map<String,Map<String,JsonElement>> ontologyConfigs = loadOntologyConfigs(inputFilePath); // ~10 min
        Map<String,Map<String,JsonElement>> ontologyConfigs = Map.of();



        DumperOptions yamlOptions = new DumperOptions();
        yamlOptions.setIndent(2);
        yamlOptions.setPrettyFlow(true);
        yamlOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(yamlOptions);


        JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(inputFilePath)));

        reader.beginObject();
        while(reader.peek() != JsonToken.END_OBJECT) {
            String name = reader.nextName();
            if(name.equals("ontologies")) {
                reader.beginArray();

                while(reader.peek() != JsonToken.END_ARRAY) {

                    reader.beginObject();

                    Map<String, JsonElement> ontologyProperties = new LinkedHashMap<>();
                    ByteArrayOutputStream tsv = null;
                    OutputStreamWriter writer = null;
                    CSVPrinter csvPrinter = null;
                    Map<String, String> curieMap = new LinkedHashMap<>();

                    while(reader.peek() != JsonToken.END_OBJECT) {

                        String propName = reader.nextName();

                        if(propName.equals("classes") || propName.equals("properties") || propName.equals("individuals")) {

                            if(tsv == null) {

                                System.out.println("Writing mappings for ontology: " + ontologyProperties.get("ontologyId").getAsString());

                                tsv = new ByteArrayOutputStream();
                                writer = new OutputStreamWriter(tsv);
                                csvPrinter = new CSVPrinter(writer, CSVFormat.MONGODB_TSV.withHeader(tsvHeader.toArray(new String[0])));
                            }

                            reader.beginArray();

                            while(reader.peek() != JsonToken.END_ARRAY) {
                                JsonElement entity = jsonParser.parse(reader);
                                writeMappingsForEntity(entity.getAsJsonObject(), csvPrinter, curieMap);
                            }

                            reader.endArray();
                        } else {
                            ontologyProperties.put(propName, jsonParser.parse(reader));
                        }
                    }

                    Map<String, Object> yamlHeader = new LinkedHashMap<>();
                    yamlHeader.put("mapping_set_id", "https://w3id.org/commons/ols/mappings/" + ontologyProperties.get("ontologyId").getAsString() + ".ols.sssom.tsv");
                    yamlHeader.put("mapping_set_group", "ols_sssom_extracts");
                    yamlHeader.put("mapping_set_confidence", "0.7");
                    Map<String, Object> yamlHeaderOther = new LinkedHashMap<>();
                    yamlHeaderOther.put("mapping_set_source", "https://www.ebi.ac.uk/ols4/ontologies/" + ontologyProperties.get("ontologyId").getAsString());
                    yamlHeaderOther.put("local_id", ontologyProperties.get("ontologyId").getAsString() + ".ols");
                    yamlHeader.put("other", yamlHeaderOther);
                    yamlHeader.put("local_name", ontologyProperties.get("ontologyId").getAsString() + ".ols.sssom.tsv");
                    yamlHeader.put("curie_map", curieMap);

                    String yamlStr = yaml.dump(yamlHeader);
                    yamlStr = Stream.of(yamlStr.split("\\n")).map(line -> "# " + line).collect(Collectors.joining("\n"));

                    FileOutputStream fos = new FileOutputStream( outputFilePath + "/" + ontologyProperties.get("ontologyId").getAsString() + ".ols.sssom.tsv");
                    fos.write(yamlStr.getBytes(StandardCharsets.UTF_8));
                    fos.write('\n');

                    if(csvPrinter != null) {
                        csvPrinter.close(true);
                        fos.write(tsv.toByteArray());
                        tsv.close();
                    }

                    reader.endObject();
                }

                reader.endArray();
            }
        }
        reader.endObject();
    }

    public static void writeMappingsForEntity(JsonObject entity, CSVPrinter writer, Map<String,String> curieMap) throws IOException {

        JsonElement exactMatch = entity.get("http://www.w3.org/2004/02/skos/core#exactMatch");
        if(exactMatch != null) {
            writeMappingsForEntity(entity, "skos:exactMatch", exactMatch, null, writer, curieMap);
        }
        JsonElement hasDbXref = entity.get("http://www.geneontology.org/formats/oboInOwl#hasDbXref");
        if(hasDbXref != null) {
            writeMappingsForEntity(entity, "oboInOwl:hasDbXref", hasDbXref, null, writer, curieMap);
        }
//        JsonElement equivalentClass = entity.get("http://www.w3.org/2002/07/owl#equivalentClass");
//        if(equivalentClass != null) {
//            writeEquivalentClassMappingsForEntity(entity, equivalentClass, writer);
//        }
    }

    public static void writeMappingsForEntity(JsonObject entity, String predicate, JsonElement mappingValue, JsonObject reificationMetadata, CSVPrinter writer, Map<String,String> curieMap) throws IOException {

        JsonObject linkedEntities = entity.getAsJsonObject("linkedEntities");

        if(mappingValue.isJsonArray()) {
            for(JsonElement entry : mappingValue.getAsJsonArray()) {
                if(entry.isJsonObject()) {
                    writeMappingsForEntity(entry.getAsJsonObject(), writer, curieMap);
                }
            }
        } else if(mappingValue.isJsonObject()) {
            JsonObject exactMatchObj = mappingValue.getAsJsonObject();
            JsonElement type = exactMatchObj.get("type");
            if(type != null && type.isJsonArray()) {
                List<String> types = jsonArrayToStrings(type.getAsJsonArray());
                if(types.contains("reification")) {
                    JsonElement value = exactMatchObj.get("value");
                    writeMappingsForEntity(entity, predicate, value, exactMatchObj, writer, curieMap);
                } else if(types.contains("literal")) {
                    JsonElement value = exactMatchObj.get("value");
                    writeMappingsForEntity(entity, predicate, value, null, writer, curieMap);
                }
            } else {
                System.out.println("entity had no type? " + entity.get("iri").getAsString());
            }
        } else if(mappingValue.isJsonPrimitive()) {

            // all mappings should eventually end up here
            // reificationMetadata may be null

            String value = mappingValue.getAsString();

            JsonObject linkedEntity = linkedEntities.getAsJsonObject(value);

            if(linkedEntity == null) {
                return;
            }

            CurieMapping subjCurie = getCurieMapping(entity, curieMap);
            CurieMapping objCurie = getCurieMapping(linkedEntity, curieMap);

            if(subjCurie == null || objCurie == null) {
                return;
            }

            String[] record = new String[tsvHeader.size()];

            for(int i = 0; i < tsvHeader.size(); ++ i) {
                switch(tsvHeader.get(i)) {
                    case "subject_id":
                        record[i] = subjCurie.curie != null ? subjCurie.curie : subjCurie.iriOrUrl;
                        break;
                    case "predicate_id":
                        record[i] = predicate;
                        break;
                    case "object_id":
                        record[i] = objCurie.curie != null ? objCurie.curie : objCurie.iriOrUrl;
                        break;
                    case "mapping_justification":
                        record[i] = "semapv:UnspecifiedMatching";
                        break;
                    case "subject_label":
                        record[i] = getFirstStringValue( entity.get("label") );
                        break;
                    case "object_label":
                        if(linkedEntity.has("label")) {
                            record[i] = getFirstStringValue( linkedEntity.get("label") );
                        }
                        break;
//                    case "comment":
//                        record[i] = "extracted from " + getFirstStringValue(entity.get("ontologyId"));
//                        break;
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

    static class CurieMapping {
        String iriOrUrl = null;
        String curie = null; // FOO:12345
        String curiePrefix = null; // FOO
        String curieLocalPart = null; // 12345
        String curieNamespace = null; // http://foobar/FOO_
    }

    static CurieMapping getCurieMapping(JsonObject entityOrLinkedEntity, Map<String,String> curieMap) {

        if(!entityOrLinkedEntity.has("iri") && !entityOrLinkedEntity.has("url")) {
            System.out.println("entity/linkedEntity had no iri or url, so cannot be mapped to anything");
            return null;
        }

        CurieMapping res = new CurieMapping();

        if(entityOrLinkedEntity.has("iri")) {
            res.iriOrUrl = entityOrLinkedEntity.get("iri").getAsString();
        } else if(entityOrLinkedEntity.has("url")) {
            res.iriOrUrl = entityOrLinkedEntity.get("url").getAsString();
        }

        if(!entityOrLinkedEntity.has("curie")) {
            return res;
        }

        String curie = getFirstStringValue(entityOrLinkedEntity.get("curie"));

        if(!curie.contains(":")) {
            System.out.println("curie provided by OLS " + curie + " does not look like a curie");
            return res;
        }

        String curiePrefix = curie.split(":")[0];
        String curieLocalPart = curie.split(":")[1];
        String curieNamespace = res.iriOrUrl.substring(0, res.iriOrUrl.length() - curieLocalPart.length());

        if(curieMap.containsKey(curiePrefix)) {

            String existingNs = curieMap.get(curiePrefix);

            if(!existingNs.equals(curieNamespace)) {
                System.out.println("Namespace " + curieNamespace + " did not match existing namespace " + existingNs + " for curie prefix " + curiePrefix);
                return res;
            }

        } else {
            curieMap.put(curiePrefix, curieNamespace);
        }

        res.curie = curie;
        res.curiePrefix = curiePrefix;
        res.curieLocalPart = curieLocalPart;
        res.curieNamespace = curieNamespace;

        return res;
    }

}


