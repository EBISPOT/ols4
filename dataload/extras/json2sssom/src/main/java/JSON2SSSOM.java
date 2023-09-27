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
                    CurieMap curieMap = new CurieMap();

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
                                writeMappingsForEntity(entity.getAsJsonObject(), csvPrinter, ontologyProperties, curieMap);
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
                    yamlHeader.put("curie_map", curieMap.curiePrefixToNamespace);

                    String yamlStr = yaml.dump(yamlHeader);
                    yamlStr = Stream.of(yamlStr.split("\\n")).map(line -> "# " + line).collect(Collectors.joining("\r\n"));

                    FileOutputStream fos = new FileOutputStream( outputFilePath + "/" + ontologyProperties.get("ontologyId").getAsString() + ".ols.sssom.tsv");
                    fos.write(yamlStr.getBytes(StandardCharsets.UTF_8));
                    fos.write('\r');
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

    public static void writeMappingsForEntity(JsonObject entity, CSVPrinter writer, Map<String,JsonElement> ontologyProperties, CurieMap curieMap) throws IOException {

        JsonElement exactMatch = entity.get("http://www.w3.org/2004/02/skos/core#exactMatch");
        if(exactMatch != null) {
            writeMappingsForEntity(entity, "skos:exactMatch", exactMatch, null, null, ontologyProperties, writer, curieMap);
        }
        JsonElement hasDbXref = entity.get("http://www.geneontology.org/formats/oboInOwl#hasDbXref");
        if(hasDbXref != null) {
            writeMappingsForEntity(entity, "oboInOwl:hasDbXref", hasDbXref, null, null, ontologyProperties, writer, curieMap);
        }
        JsonElement equivalentClass = entity.get("http://www.w3.org/2002/07/owl#equivalentClass");
        if(equivalentClass != null) {
            writeMappingsForEntity(entity, "owl:equivalentClass", equivalentClass, null, null, ontologyProperties, writer, curieMap);
        }

        // hacky special cases for chemical specific mapping predicates
        //
        JsonElement inchi = entity.get("http://purl.obolibrary.org/obo/chebi/inchi");
        if(inchi != null) {
            writeMappingsForEntity(entity, "oboInOwl:hasDbXref", inchi, "inchi:", null, ontologyProperties, writer, curieMap);
        }
        JsonElement inchiKey = entity.get("http://purl.obolibrary.org/obo/chebi/inchikey");
        if(inchiKey != null) {
            writeMappingsForEntity(entity, "oboInOwl:hasDbXref", inchiKey, "inchikey:", null, ontologyProperties, writer, curieMap);
        }
        JsonElement smiles = entity.get("http://purl.obolibrary.org/obo/chebi/smiles");
        if(smiles != null) {
            writeMappingsForEntity(entity, "oboInOwl:hasDbXref", smiles, "smiles:", null, ontologyProperties, writer, curieMap);
        }
    }

    public static void writeMappingsForEntity(
            JsonObject entity,
            String predicate,
            JsonElement mappingValue,
            String valuePrefix,
            JsonObject reificationMetadata,
            Map<String,JsonElement> ontologyProperties,
            CSVPrinter writer,
            CurieMap curieMap) throws IOException {

        JsonElement isDefiningOntology = entity.get("isDefiningOntology");

        if(isDefiningOntology != null && isDefiningOntology.getAsBoolean() == false) {
            // don't print mappings for imported entities (they will already be printed in the defining ontology)
            return;
        }

        JsonObject linkedEntities = entity.getAsJsonObject("linkedEntities");

        if(linkedEntities == null) {
            System.out.println("entity had no linkedEntities? " + gson.toJson(entity));
            return;
        }

        if(mappingValue.isJsonArray()) {
            for(JsonElement entry : mappingValue.getAsJsonArray()) {
                if(entry.isJsonObject()) {
                    writeMappingsForEntity(entity, predicate, entry, valuePrefix, null, ontologyProperties, writer, curieMap);
                }
            }
        } else if(mappingValue.isJsonObject()) {
            JsonObject exactMatchObj = mappingValue.getAsJsonObject();
            JsonElement type = exactMatchObj.get("type");
            if(type != null && type.isJsonArray()) {
                List<String> types = JsonHelper.jsonArrayToStrings(type.getAsJsonArray());
                if(types.contains("reification")) {
                    JsonElement value = exactMatchObj.get("value");
                    writeMappingsForEntity(entity, predicate, value, valuePrefix, exactMatchObj, ontologyProperties, writer, curieMap);
                } else if(types.contains("literal")) {
                    JsonElement value = exactMatchObj.get("value");
                    writeMappingsForEntity(entity, predicate, value, valuePrefix, null, ontologyProperties, writer, curieMap);
                }
            } else {
//                System.out.println("mapping value was object but had no type? " + gson.toJson(mappingValue));
                // e.g. mapping value was object but had no type? {"http://www.w3.org/1999/02/22-rdf-syntax-ns#type":"http://www.w3.org/2002/07/owl#Class","http://www.w3.org/2002/07/owl#intersectionOf":["http://purl.obolibrary.org/obo/GO_0048856",{"http://www.w3.org/1999/02/22-rdf-syntax-ns#type":"http://www.w3.org/2002/07/owl#Restriction","http://www.w3.org/2002/07/owl#onProperty":"http://purl.obolibrary.org/obo/RO_0002296","http://www.w3.org/2002/07/owl#someValuesFrom":"http://purl.obolibrary.org/obo/UBERON_0004490","isObsolete":{"type":["literal"],"value":"false"}}]}
                    return;
            }
        } else if(mappingValue.isJsonPrimitive()) {

            // all mappings should eventually end up here
            // reificationMetadata may be null

            CurieMap.CurieMapping subjCurie = curieMap.mapEntity(entity);

            if(subjCurie == null) {
                return;
            }

            String subject_id = subjCurie.curie != null ? subjCurie.curie : subjCurie.iriOrUrl;



            String value = mappingValue.getAsString();

            String object_id = null;
            String object_label = "";


            if(valuePrefix != null) {

                // hack for chemical mappings (smiles, inchi, inchikey)
                object_id = valuePrefix + value;
                object_label = value;

            } else {

                // all the other mappings in OLS end up here

                JsonElement linkedEntityElem = linkedEntities.get(value);

                if(linkedEntityElem == null || !linkedEntityElem.isJsonObject()) {
                    return;
                }

                JsonObject linkedEntity = linkedEntityElem.getAsJsonObject();

                CurieMap.CurieMapping objCurie = curieMap.mapEntity(linkedEntity);

                if(objCurie == null) {
                    return;
                }

                object_id = objCurie.curie != null ? objCurie.curie : objCurie.iriOrUrl;

                if(linkedEntity.has("label"))  {
                    object_label = JsonHelper.getFirstStringValue( linkedEntity.get("label") );
                }
            }


            String[] record = new String[tsvHeader.size()];

            for(int i = 0; i < tsvHeader.size(); ++ i) {
                switch(tsvHeader.get(i)) {
                    case "subject_id":
                        record[i] = subject_id;
                        break;
                    case "predicate_id":
                        record[i] = predicate;
                        break;
                    case "object_id":
                        record[i] = object_id;
                        break;
                    case "mapping_justification":
                        record[i] = "semapv:UnspecifiedMatching";
                        break;
                    case "subject_label":
                        record[i] = JsonHelper.getFirstStringValue( entity.get("label") );
                        break;
                    case "object_label":
                        record[i] = object_label;
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

}


