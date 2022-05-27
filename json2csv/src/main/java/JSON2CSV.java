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
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class JSON2CSV {

    static Gson gson = new Gson();

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option input = new Option(null, "input", true, "ontologies JSON input filename");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option(null, "outDir", true, "output CSV folder path");
        output.setRequired(true);
        options.addOption(output);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("ols-json2csv", options);

            System.exit(1);
            return;
        }

        String inputFilePath = cmd.getOptionValue("input");
        String outputFilePath = cmd.getOptionValue("outDir");

        JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(inputFilePath)));

        reader.beginObject();

        while(reader.peek() != JsonToken.END_OBJECT) {

            String name = reader.nextName();

            if (name.equals("ontologies")) {

                reader.beginArray();

                while(reader.peek() != JsonToken.END_ARRAY) {
                    JsonOntology ontology = gson.fromJson(reader, JsonOntology.class);

                    NodesAndPropertiesExtractor.Result nodesAndProps =
                            NodesAndPropertiesExtractor.extractNodesAndProperties(ontology);

                    //EdgesExtractor.Result edges =
                    //EdgesExtractor.extractEdges(ontology, nodesAndProps);

                    writeOntology(ontology, outputFilePath);
                    writeClasses(ontology, outputFilePath, nodesAndProps);
                    writeClassEdges(ontology, outputFilePath, nodesAndProps);
                }

                reader.endArray();

            } else {

                reader.skipValue();

            }
        }

        reader.endObject();
        reader.close();
    }

    public static void writeOntology(JsonOntology ontology, String outPath) throws IOException {

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add("ontologyId:ID");
        csvHeader.add(":LABEL");
        csvHeader.add("config");
        csvHeader.addAll(replaceNeo4jSpecialChars(ontology.properties.keySet()));

        String outName = outPath + "/" + (String) ontology.config.get("id") + "_ontologies.csv";

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        List<String> row = new ArrayList<>();
        for (String column : csvHeader) {
            if (column.equals("ontologyId:ID")) {
                row.add((String) ontology.config.get("id"));
                continue;
            }
            if (column.equals(":LABEL")) {
                row.add("Ontology");
                continue;
            }
            if (column.equals("config")) {
                row.add(gson.toJson(ontology.config));
                continue;
            }

            Object value = ontology.properties.get(unreplaceNeo4jSpecialChars(column));

            if (value == null) {
                row.add("");
                continue;
            }

            if (value instanceof String) {
                row.add((String) value);
                continue;
            }

            row.add(gson.toJson(value));
        }

        printer.printRecord(row.toArray());
        printer.close(true);
    }


    public static void writeClasses(JsonOntology ontology, String outPath, NodesAndPropertiesExtractor.Result nodesAndProps) throws IOException {

        String id = (String) ontology.config.get("id");

        String outName = outPath + "/" + id + "_classes.csv";

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add("classId:ID");
        csvHeader.add(":LABEL");
        csvHeader.add("ontology_id");
        csvHeader.add("uri");
        csvHeader.addAll(replaceNeo4jSpecialChars(nodesAndProps.allClassProperties));

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        for (Map<String, Object> _class : ontology.classes) {

            String[] row = new String[csvHeader.size()];
            int n = 0;

            for (String column : csvHeader) {
                if (column.equals("classId:ID")) {
                    row[n++] = id + "+" + (String) _class.get("uri");
                    continue;
                }
                if (column.equals(":LABEL")) {
                    row[n++] = "OwlClass";
                    continue;
                }
                if (column.equals("ontology_id")) {
                    row[n++] = id;
                    continue;
                }
                if (column.equals("uri")) {
                    row[n++] = (String) _class.get("uri");
                    continue;
                }

                if(column.startsWith("axiom+")) {

                    Object value = _class.get(unreplaceNeo4jSpecialChars(column.substring(6)));

                    if(value instanceof Map) {
                        row[n++] = gson.toJson(value);
                        continue;
                    }

                    row[n++] = "";
                    continue;
                }

                Object value = _class.get(unreplaceNeo4jSpecialChars(column));

                if (value == null) {
                    row[n++] = "";
                    continue;
                }

                if (value instanceof String) {
                    row[n++] = (String) value;
                    continue;
                }

                if(value instanceof Map) {

                    // axiom, but we are writing the value itself as a property directly
                    // in this case; the rest of the axiom properties go in the axiom+ field

                    Map<String, Object> mapValue = (Map<String, Object>) value;

                    if(mapValue.containsKey("value")) {
                        Object val = mapValue.get("value");
                        if(val instanceof String) {
                            row[n++] = (String) val;
                        } else {
                            row[n++] = gson.toJson(value);
                        }
                    } else {
                        row[n++] = gson.toJson(value);
                    }
                }

            }

            printer.printRecord(row);
        }

        printer.close(true);
    }

    public static void writeClassEdges(JsonOntology ontology, String outPath, NodesAndPropertiesExtractor.Result nodesAndProps) throws IOException {

        String ontologyId = (String) ontology.config.get("id");

        String outName = outPath + "/" + ontologyId + "_class_edges.csv";

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add(":START_ID");
        csvHeader.add(":TYPE");
        csvHeader.add(":END_ID");
        csvHeader.addAll(replaceNeo4jSpecialChars(nodesAndProps.allEdgeProperties));

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        for (Map<String, Object> _class : ontology.classes) {

            for (String predicate : _class.keySet()) {

                if (predicate.equals("uri"))
                    continue;

                Object value = _class.get(unreplaceNeo4jSpecialChars(predicate));

                List<Object> values;

                if(value instanceof List) {
                    values = (List<Object>) value;
                } else {
                    values = new ArrayList<>();
                    values.add(value);
                }

                for(Object v : values) {

                    if (v instanceof Map) {
                        // maybe axiom
                        Map<String, Object> mapValue = (Map<String, Object>) v;
                        if (mapValue.containsKey("value")) {
                            // axiom
                            Object axiomValue = mapValue.get("value");
                            assert (axiomValue instanceof String);
                            if (nodesAndProps.allNodes.contains(axiomValue)) {
                                printClassEdge(printer, csvHeader, ontologyId, _class, predicate, axiomValue, mapValue);
                            }
                        }
                    } else if (v instanceof String) {
                        if (nodesAndProps.allNodes.contains((String) v)) {
                            printClassEdge(printer, csvHeader, ontologyId, _class, predicate, v, new HashMap<>());
                        }
                    } else {
                        assert(false);
                    }

                }

            }
        }

        printer.close(true);
    }


    private static void printClassEdge(CSVPrinter printer, List<String> csvHeader, String ontologyId, Map<String,Object> a, String predicate, Object bUri, Map<String,Object> edgeProps) throws IOException {

        String[] row = new String[csvHeader.size()];
        int n = 0;

        for (String column : csvHeader) {
            if (column.equals(":START_ID")) {
                row[n++] = ontologyId + "+" + (String) a.get("uri");
                continue;
            }
            if (column.equals(":TYPE")) {
                row[n++] = predicate;
                continue;
            }
            if (column.equals(":END_ID")) {
                row[n++] = ontologyId + "+" + (String) bUri;
                continue;
            }

            // anything else are properties on the edge itself (from axioms)
            //
            Object val = edgeProps.get(unreplaceNeo4jSpecialChars(column));

            if (val == null) {
                row[n++] = "";
                continue;
            }

            if (val instanceof String) {
                row[n++] = (String) val;
                continue;
            }

            row[n++] = gson.toJson(val);
        }

        printer.printRecord(row);
    }

    public static Set<String> replaceNeo4jSpecialChars(Set<String> uris) {
        Set<String> newUris = new HashSet<>();

        for(String uri : uris) {
            newUris.add(uri.replace("http:", "http+"));
        }

        return newUris;
    }

    public static String unreplaceNeo4jSpecialChars(String uri) {
        return uri.replace("http+", "http:");
    }
}


