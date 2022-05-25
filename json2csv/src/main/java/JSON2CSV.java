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

        List<String> props = new ArrayList<>(ontology.properties.keySet());

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add("ontology_id");
        csvHeader.add("config");
        csvHeader.addAll(props);

        String outName = outPath + "/" + (String) ontology.config.get("id") + ".csv";

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        List<String> row = new ArrayList<>();
        for (String column : csvHeader) {
            if (column.equals("ontology_id")) {
                row.add((String) ontology.config.get("id"));
                continue;
            }
            if (column.equals("config")) {
                row.add(gson.toJson(ontology.config));
                continue;
            }

            Object value = ontology.properties.get(column);

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
        csvHeader.add("id");
        csvHeader.add("ontology_id");
        csvHeader.add("uri");
        csvHeader.addAll(nodesAndProps.allClassProperties);

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        for (Map<String, Object> _class : ontology.classes) {

            String[] row = new String[csvHeader.size()];
            int n = 0;

            for (String column : csvHeader) {
                if (column.equals("id")) {
                    row[n++] = id + "+" + (String) _class.get("uri");
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

                    Object value = _class.get(column.substring(6));

                    if(value instanceof Map) {
                        row[n++] = gson.toJson(value);
                        continue;
                    }

                    row[n++] = "";
                    continue;
                }

                Object value = _class.get(column);

                if (value == null) {
                    row[n++] = "";
                    continue;
                }

                if (value instanceof String) {
                    row[n++] = (String) value;
                    continue;
                }

                if(value instanceof Map) {

                    Map<String, Object> mapValue = (Map<String, Object>) value;

                    if(mapValue.containsKey("value")) {
                        Object val = mapValue.get("value");
                        assert(val instanceof String);
                        row[n++] = (String) val;
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
        csvHeader.add("a");
        csvHeader.add("predicate");
        csvHeader.add("b");
        csvHeader.addAll(nodesAndProps.allEdgeProperties);

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        for (Map<String, Object> _class : ontology.classes) {

            for (String predicate : _class.keySet()) {

                if (predicate.equals("uri"))
                    continue;

                Object value = _class.get(predicate);

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
            if (column.equals("a")) {
                row[n++] = ontologyId + "+" + (String) a.get("uri");
                continue;
            }
            if (column.equals("predicate")) {
                row[n++] = predicate;
                continue;
            }
            if (column.equals("b")) {
                row[n++] = ontologyId + "+" + (String) bUri;
                continue;
            }

            // anything else are properties on the edge itself (from axioms)
            //
            Object val = edgeProps.get(column);

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
}


