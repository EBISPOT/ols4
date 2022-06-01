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
            formatter.printHelp("json2csv", options);

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
                    writeProperties(ontology, outputFilePath, nodesAndProps);
                    writeIndividuals(ontology, outputFilePath, nodesAndProps);

                    writeEdges(ontology, outputFilePath, nodesAndProps);
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

        List<String> properties = new ArrayList<String>(ontology.ontologyProperties.keySet());

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add("id:ID");
        csvHeader.add(":LABEL");
        csvHeader.add("config");
        csvHeader.addAll(propertyHeaders(properties));

        String outName = outPath + "/" + (String) ontology.ontologyConfig.get("id") + "_ontologies.csv";

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        String[] row = new String[csvHeader.size()];
        int n = 0;

        row[n++] = (String) ontology.ontologyConfig.get("id");
        row[n++] = "Ontology";
        row[n++] = gson.toJson(ontology.ontologyConfig);

        for (String column : properties) {

            Object value = ontology.ontologyProperties.get(column);

            if (value == null) {
                row[n++] = "";
                continue;
            }

            if (value instanceof String) {
                row[n++] = (String)value;
                continue;
            }

            row[n++] = gson.toJson(value);
        }

        printer.printRecord(row);
        printer.close(true);
    }


    public static void writeClasses(JsonOntology ontology, String outPath, NodesAndPropertiesExtractor.Result nodesAndProps) throws IOException {

        String id = (String) ontology.ontologyConfig.get("id");

        String outName = outPath + "/" + id + "_classes.csv";

        List<String> properties = new ArrayList<String>(nodesAndProps.allClassProperties);

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add("id:ID");
        csvHeader.add(":LABEL");
        csvHeader.add("ontology_id");
        csvHeader.add("uri");
        csvHeader.addAll(propertyHeaders(properties));

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        for (Map<String, Object> _class : ontology.classes) {

            String[] row = new String[csvHeader.size()];
            int n = 0;

            row[n++] = id + "+" + (String) _class.get("uri");
            row[n++] = "OwlClass";
            row[n++] = id;
            row[n++] = (String) _class.get("uri");

            for (String column : properties) {

                if(column.startsWith("axiom+")) {
			Object axiom = _class.get(column.substring(6));
                        row[n++] = axiom != null ? gson.toJson(axiom) : "";
		} else {
			row[n++] = valueToCsv(_class.get(column));
		}

            }

            printer.printRecord(row);
        }

        printer.close(true);
    }


    public static void writeProperties(JsonOntology ontology, String outPath, NodesAndPropertiesExtractor.Result nodesAndProps) throws IOException {

        String id = (String) ontology.ontologyConfig.get("id");

        String outName = outPath + "/" + id + "_properties.csv";

        List<String> properties = new ArrayList<String>(nodesAndProps.allPropertyProperties);

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add("id:ID");
        csvHeader.add(":LABEL");
        csvHeader.add("ontology_id");
        csvHeader.add("uri");
        csvHeader.addAll(propertyHeaders(properties));

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        for (Map<String, Object> _property : ontology.properties) {

            String[] row = new String[csvHeader.size()];
            int n = 0;

            row[n++] = id + "+" + (String) _property.get("uri");
            row[n++] = "OwlProperty";
            row[n++] = id;
            row[n++] = (String) _property.get("uri");

            for (String column : properties) {

                if(column.startsWith("axiom+")) {
			Object axiom = _property.get(column.substring(6));
                        row[n++] = axiom != null ? gson.toJson(axiom) : "";
		} else {
			row[n++] = valueToCsv(_property.get(column));
		}
            }

            printer.printRecord(row);
        }

        printer.close(true);
    }

    public static void writeIndividuals(JsonOntology ontology, String outPath, NodesAndPropertiesExtractor.Result nodesAndProps) throws IOException {

        String id = (String) ontology.ontologyConfig.get("id");

        String outName = outPath + "/" + id + "_individuals.csv";

        List<String> properties = new ArrayList<String>(nodesAndProps.allIndividualProperties);

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add("id:ID");
        csvHeader.add(":LABEL");
        csvHeader.add("ontology_id");
        csvHeader.add("uri");
        csvHeader.addAll(propertyHeaders(properties));

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        for (Map<String, Object> _individual : ontology.individuals) {

            String[] row = new String[csvHeader.size()];
            int n = 0;

            row[n++] = id + "+" + (String) _individual.get("uri");
            row[n++] = "OwlIndividual";
            row[n++] = id;
            row[n++] = (String) _individual.get("uri");

            for (String column : properties) {

                if(column.startsWith("axiom+")) {
			Object axiom = _individual.get(column.substring(6));
                        row[n++] = axiom != null ? gson.toJson(axiom) : "";
		} else {
			row[n++] = valueToCsv(_individual.get(column));
		}
            }

            printer.printRecord(row);
        }

        printer.close(true);
    }


    public static void writeEdges(JsonOntology ontology, String outPath, NodesAndPropertiesExtractor.Result nodesAndProps) throws IOException {

        String ontologyId = (String) ontology.ontologyConfig.get("id");

        String outName = outPath + "/" + ontologyId + "_edges.csv";

        List<String> properties = new ArrayList<String>(nodesAndProps.allEdgeProperties);

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add(":START_ID");
        csvHeader.add(":TYPE");
        csvHeader.add(":END_ID");
        csvHeader.addAll(propertyHeaders(properties));

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
                                printEdge(printer, properties, ontologyId, _class, predicate, axiomValue, mapValue);
                            }
                        }
                    } else if (v instanceof String) {
                        if (nodesAndProps.allNodes.contains((String) v)) {
                            printEdge(printer, properties, ontologyId, _class, predicate, v, new HashMap<>());
                        }
                    } else {
                        assert(false);
                    }

                }

            }
        }

        for (Map<String, Object> property : ontology.properties) {

            for (String predicate : property.keySet()) {

                if (predicate.equals("uri"))
                    continue;

                Object value = property.get(predicate);

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
                                printEdge(printer, properties, ontologyId, property, predicate, axiomValue, mapValue);
                            }
                        }
                    } else if (v instanceof String) {
                        if (nodesAndProps.allNodes.contains((String) v)) {
                            printEdge(printer, properties, ontologyId, property, predicate, v, new HashMap<>());
                        }
                    } else {
                        assert(false);
                    }

                }

            }
        }

        for (Map<String, Object> individual : ontology.individuals) {

            for (String predicate : individual.keySet()) {

                if (predicate.equals("uri"))
                    continue;

                Object value = individual.get(predicate);

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
                                printEdge(printer, properties, ontologyId, individual, predicate, axiomValue, mapValue);
                            }
                        }
                    } else if (v instanceof String) {
                        if (nodesAndProps.allNodes.contains((String) v)) {
                            printEdge(printer, properties, ontologyId, individual, predicate, v, new HashMap<>());
                        }
                    } else {
                        assert(false);
                    }

                }

            }
        }

        printer.close(true);
    }


    private static void printEdge(CSVPrinter printer, List<String> properties, String ontologyId, Map<String,Object> a, String predicate, Object bUri, Map<String,Object> edgeProps) throws IOException {

        String[] row = new String[3 + properties.size()];
        int n = 0;

        row[n++] = ontologyId + "+" + (String) a.get("uri");
        row[n++] = predicate;
        row[n++] = ontologyId + "+" + (String) bUri;

        for (String column : properties) {

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

    private static String valueToCsv(Object value) {

	if(value instanceof List) {
		String out = "";
		for(Object val : (List<Object>) value)  {
			if(out.length() > 0) {
				out += "|";
			}
			out += valueToCsv(val);
		}
		return out;
	}

	if (value == null) {
		return "";
	}

	if (value instanceof String) {
		return replaceNeo4jSpecialCharsValue((String) value);
	}

	if(value instanceof Map) {

		// could be an axiom, but we are writing the value itself as a property
		// directly in this case; the rest of the axiom properties go in
		// the axiom+ field

		Map<String, Object> mapValue = (Map<String, Object>) value;

		if (mapValue.containsKey("value")) {
			Object val = mapValue.get("value");
			if (val instanceof String) {
				return replaceNeo4jSpecialCharsValue((String) val);
			}
		}
	}

	return replaceNeo4jSpecialCharsValue(gson.toJson(value));
    }


    private static String replaceNeo4jSpecialCharsValue(String val) {
	return val.replace("|", "+");
    }

    private static List<String> propertyHeaders(List<String> uris) {
        List<String> headers = new ArrayList<>();

        for(String uri : uris) {
            headers.add(uri.replace(":", "+") + ":string[]");
        }

        return headers;
    }

}


