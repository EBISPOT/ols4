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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JSON2CSV {

    static Gson gson = new Gson();

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option input = new Option(null, "input", true, "ontologies JSON input filename");
        input.setRequired(true);
        options.addOption(input);

        Option outOntologies = new Option(null, "out-ontologies", true, "output ontologies CSV filename");
        outOntologies.setRequired(true);
        options.addOption(outOntologies);

        Option outClasses = new Option(null, "out-classes", true, "output classes CSV filename");
        outClasses.setRequired(true);
        options.addOption(outClasses);

        Option outClassEdges = new Option(null, "out-class-edges", true, "output class edges CSV filename");
        outClassEdges.setRequired(true);
        options.addOption(outClassEdges);
        

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
        String outOntologiesPath = cmd.getOptionValue("out-ontologies");
        String outClassesPath = cmd.getOptionValue("out-classes");
        String outClassEdgesPath = cmd.getOptionValue("out-class-edges");


        Set<String> allClassProperties = new HashSet<>();
        Set<String> allEdgeProperties = new HashSet<>();
        Set<String> allNodes = new HashSet<>();
        Set<String> allOntologyProps = new HashSet<>();

        /// 1. populate sets
        ///

        JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(inputFilePath)));
        reader.beginObject();

        while(reader.peek() != JsonToken.END_OBJECT) {

            String name = reader.nextName();

            if (name.equals("ontologies")) {

                reader.beginArray();

                while(reader.peek() != JsonToken.END_ARRAY) {
                    JsonOntology ontology = gson.fromJson(reader, JsonOntology.class);

                    allOntologyProps.addAll(ontology.properties.keySet());

                    NodesAndPropertiesExtractor.extractNodesAndProperties(ontology, allClassProperties, allEdgeProperties, allNodes);
                }

                reader.endArray();

            } else {

                reader.skipValue();

            }
        }
        reader.endObject();
        reader.close();


        /// 2. write ontologies
        ///

        List<String> ontologiesCsvHeader = new ArrayList<>();
        ontologiesCsvHeader.add("ontology_id");
        ontologiesCsvHeader.add("config");
        ontologiesCsvHeader.addAll(allOntologyProps);

        CSVPrinter ontologiesPrinter = CSVFormat.POSTGRESQL_CSV.withHeader(ontologiesCsvHeader.toArray(new String[0])).print(
                new File(outOntologiesPath), Charset.defaultCharset());

        List<String> classesCsvHeader = new ArrayList<>();
        classesCsvHeader.add("id");
        classesCsvHeader.add("ontology_id");
        classesCsvHeader.add("uri");
        classesCsvHeader.addAll(allClassProperties);

        CSVPrinter classesPrinter = CSVFormat.POSTGRESQL_CSV.withHeader(classesCsvHeader.toArray(new String[0])).print(
                new File(outClassesPath), Charset.defaultCharset());

        List<String> edgesCsvHeader = new ArrayList<>();
        edgesCsvHeader.add("a");
        edgesCsvHeader.add("predicate");
        edgesCsvHeader.add("b");
        edgesCsvHeader.addAll(allEdgeProperties);

        CSVPrinter edgesPrinter = CSVFormat.POSTGRESQL_CSV.withHeader(edgesCsvHeader.toArray(new String[0])).print(
                new File(outClassEdgesPath), Charset.defaultCharset());
        

        reader = new JsonReader(new InputStreamReader(new FileInputStream(inputFilePath)));
        reader.beginObject();

        while(reader.peek() != JsonToken.END_OBJECT) {

            String name = reader.nextName();

            if (name.equals("ontologies")) {

                reader.beginArray();

                while(reader.peek() != JsonToken.END_ARRAY) {
                    JsonOntology ontology = gson.fromJson(reader, JsonOntology.class);

                    writeOntology(ontologiesCsvHeader, ontologiesPrinter, ontology);
                    writeClasses(classesCsvHeader, classesPrinter, ontology);
                    writeClassEdges(edgesCsvHeader, edgesPrinter, ontology, allNodes);
                }

                reader.endArray();

            } else {

                reader.skipValue();

            }
        }
        reader.endObject();
        reader.close();

        ontologiesPrinter.close(true);
        classesPrinter.close(true);
        edgesPrinter.close(true);


    }

    public static void writeOntology(
        List<String> ontologiesCsvHeader,
        CSVPrinter ontologiesPrinter,
        JsonOntology ontology) throws IOException {

        List<String> row = new ArrayList<>();
        for (String column : ontologiesCsvHeader) {
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

        ontologiesPrinter.printRecord(row.toArray());
    }

    public static void writeClasses(
        List<String> classesCsvHeader,
        CSVPrinter classesPrinter,
        JsonOntology ontology) throws IOException {

        String id = (String) ontology.config.get("id");

        for (Map<String, Object> _class : ontology.classes) {

            String[] row = new String[classesCsvHeader.size()];
            int n = 0;

            for (String column : classesCsvHeader) {
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

            classesPrinter.printRecord(row);
        }
    }

    public static void writeClassEdges(
        List<String> edgesCsvHeader,
        CSVPrinter edgesPrinter,
        JsonOntology ontology,
        Set<String> allNodes
        ) throws IOException {

        String ontologyId = (String)ontology.config.get("id");

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
                            if (allNodes.contains(axiomValue)) {
                                printClassEdge(edgesPrinter, edgesCsvHeader, ontologyId, _class, predicate, axiomValue, mapValue);
                            }
                        }
                    } else if (v instanceof String) {
                        if (allNodes.contains((String) v)) {
                            printClassEdge(edgesPrinter, edgesCsvHeader, ontologyId, _class, predicate, v, new HashMap<>());
                        }
                    } else {
                        assert(false);
                    }

                }

            }
        }
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


