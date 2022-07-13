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
import java.util.Collection;

public class JSON2CSV {

    static Gson gson = new Gson();


    // Fields that we never want to query, so shouldn't be added to the Neo4j
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
                    Map<String,Object> ontology = gson.fromJson(reader, Map.class);

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

    public static void writeOntology(Map<String,Object> ontology, String outPath) throws IOException {

        List<String> properties = new ArrayList<String>(ontology.keySet());

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add("id:ID");
        csvHeader.add(":LABEL");
        csvHeader.add("_json");
        csvHeader.addAll(propertyHeaders(properties));

        String outName = outPath + "/" + (String) ontology.get("id") + "_ontologies.csv";

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        String[] row = new String[csvHeader.size()];
        int n = 0;

        row[n++] = (String) ontology.get("id");
        row[n++] = "Ontology";


        // don't want to store the whole ontology including terms!!
        // just the ontologyConfig and the properties
        Map<String,Object> ontologyJsonObj = new HashMap<>();
        for(String prop : properties) {
            if(prop.equals("classes") || prop.equals("properties") || prop.equals("individuals"))
                continue;
            ontologyJsonObj.put(prop, ontology.get(prop));
        }
        row[n++] = gson.toJson(ontologyJsonObj);


        for (String column : properties) {
            if(!DONT_INDEX_FIELDS.contains(column))
                row[n++] = getValue(ontology, column);
        }

        printer.printRecord(row);
        printer.close(true);
    }


    public static void writeClasses(Map<String,Object> ontology, String outPath, NodesAndPropertiesExtractor.Result nodesAndProps) throws IOException {

        String id = (String) ontology.get("id");

        String outName = outPath + "/" + id + "_classes.csv";

        List<String> properties = new ArrayList<String>(nodesAndProps.allClassProperties);

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add("id:ID");
        csvHeader.add(":LABEL");
        csvHeader.add("ontology_id");
        csvHeader.add("uri");
        csvHeader.add("_json");
        csvHeader.addAll(propertyHeaders(properties));

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        for (Map<String, Object> _class : (Collection<Map<String,Object>>) ontology.get("classes")) {

            String[] row = new String[csvHeader.size()];
            int n = 0;

            row[n++] = id + "+" + (String) _class.get("uri");
            row[n++] = "OwlClass";
            row[n++] = id;
            row[n++] = (String) _class.get("uri");
            row[n++] = gson.toJson(_class);

            for (String column : properties) {
                if(!DONT_INDEX_FIELDS.contains(column))
                    row[n++] = getValue(_class, column);
            }

            printer.printRecord(row);
        }

        printer.close(true);
    }


    public static void writeProperties(Map<String,Object> ontology, String outPath, NodesAndPropertiesExtractor.Result nodesAndProps) throws IOException {

        String id = (String) ontology.get("id");

        String outName = outPath + "/" + id + "_properties.csv";

        List<String> properties = new ArrayList<String>(nodesAndProps.allPropertyProperties);

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add("id:ID");
        csvHeader.add(":LABEL");
        csvHeader.add("ontology_id");
        csvHeader.add("uri");
        csvHeader.add("_json");
        csvHeader.addAll(propertyHeaders(properties));

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        for (Map<String, Object> _property : (Collection<Map<String,Object>>) ontology.get("properties")) {

            String[] row = new String[csvHeader.size()];
            int n = 0;

            row[n++] = id + "+" + (String) _property.get("uri");
            row[n++] = "OwlProperty";
            row[n++] = id;
            row[n++] = (String) _property.get("uri");
            row[n++] = gson.toJson(_property);

            for (String column : properties) {
                if(!DONT_INDEX_FIELDS.contains(column))
                    row[n++] = getValue(_property, column);
            }

            printer.printRecord(row);
        }

        printer.close(true);
    }

    public static void writeIndividuals(Map<String,Object> ontology, String outPath, NodesAndPropertiesExtractor.Result nodesAndProps) throws IOException {

        String id = (String) ontology.get("id");

        String outName = outPath + "/" + id + "_individuals.csv";

        List<String> properties = new ArrayList<String>(nodesAndProps.allIndividualProperties);

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add("id:ID");
        csvHeader.add(":LABEL");
        csvHeader.add("ontology_id");
        csvHeader.add("uri");
        csvHeader.add("_json");
        csvHeader.addAll(propertyHeaders(properties));

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        for (Map<String, Object> _individual : (Collection<Map<String,Object>>) ontology.get("individuals")) {

            String[] row = new String[csvHeader.size()];
            int n = 0;

            row[n++] = id + "+" + (String) _individual.get("uri");
            row[n++] = "OwlIndividual";
            row[n++] = id;
            row[n++] = (String) _individual.get("uri");
            row[n++] = gson.toJson(_individual);

            for (String column : properties) {
                if(!DONT_INDEX_FIELDS.contains(column))
                    row[n++] = getValue(_individual, column);
            }

            printer.printRecord(row);
        }

        printer.close(true);
    }


    public static void writeEdges(Map<String,Object> ontology, String outPath, NodesAndPropertiesExtractor.Result nodesAndProps) throws IOException {

        String ontologyId = (String) ontology.get("id");

        String outName = outPath + "/" + ontologyId + "_edges.csv";

        List<String> properties = new ArrayList<String>(nodesAndProps.allEdgeProperties);

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add(":START_ID");
        csvHeader.add(":TYPE");
        csvHeader.add(":END_ID");
        csvHeader.add("_json");
        csvHeader.addAll(propertyHeaders(properties));

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        for (Map<String, Object> _class : (Collection<Map<String,Object>>) ontology.get("classes")) {

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
                        if (mapValue.containsKey("value") && !mapValue.containsKey("lang")) {
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

        for (Map<String, Object> property : (Collection<Map<String,Object>>) ontology.get("properties")) {

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
                        if (mapValue.containsKey("value") && !mapValue.containsKey("lang")) {
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

        for (Map<String, Object> individual : (Collection<Map<String,Object>>) ontology.get("individuals")) {

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
                        if (mapValue.containsKey("value") && !mapValue.containsKey("lang")) {
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

        String[] row = new String[4 + properties.size()];
        int n = 0;

        row[n++] = ontologyId + "+" + (String) a.get("uri");
        row[n++] = predicate;
        row[n++] = ontologyId + "+" + (String) bUri;
        row[n++] = gson.toJson(edgeProps);

        for (String column : properties) {

            // anything else are properties on the edge itself (from axioms)
            //
            row[n++] = getValue(edgeProps, column);
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

		// could be an axiom or a langString, but we are writing the value
        // itself as a property directly in this case; the rest of the axiom 
        // properties or localized strings are preserved in the _json field

		Map<String, Object> mapValue = (Map<String, Object>) value;

		if (mapValue.containsKey("value")) {
			Object val = mapValue.get("value");
            return valueToCsv(val);
		}
	}

	return replaceNeo4jSpecialCharsValue(gson.toJson(value));
    }


    private static String replaceNeo4jSpecialCharsValue(String val) {
	return val.replace("|", "+");
    }

    private static List<String> propertyHeaders(List<String> fieldNames) {
        List<String> headers = new ArrayList<>();

        for(String k : fieldNames) {
            if(!DONT_INDEX_FIELDS.contains(k))
                headers.add(k.replace(":", "__") + ":string[]");
        }

        return headers;
    }

    private static String getValue(Map<String,Object> properties, String column) {

            if(column.indexOf('+') != -1) {
                String lang = column.substring(0, column.indexOf('+'));
                String predicate = column.substring(column.indexOf('+')+1);

                return valueToCsv(getLocalizedValue(properties, predicate, lang));
            }

            Object value = properties.get(column);

            return valueToCsv(value);
    }


    private static Object getLocalizedValue(Map<String,Object> properties, String predicate, String lang) {

            Object values = properties.get(predicate);

            if(values == null)
                return null;

            if(! (values instanceof List)) {
                List<Object> valuesList = new ArrayList<>();
                valuesList.add(values);
                values = valuesList;
            }

            for(Object value : ((List<Object>) values)) {
                if(value instanceof Map) {
                    Map<String, Object> mapValue = (Map<String, Object>) value;
                    String valueLang = (String)mapValue.get("lang");
                    if(valueLang != null && valueLang.equals(lang)) {
                        return valueToCsv(mapValue.get("value"));
                    }
                }
            }

            return null;
    }

}


