import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

public class OntologyWriter {

    Gson gson = new Gson();

    JsonReader reader;
    String outputFilePath;
    String ontologyId;
    OntologyScanner.Result ontologyScannerResult;

    List<String> edgesProperties;
    CSVPrinter edgesPrinter;

    public OntologyWriter(JsonReader reader, String outputFilePath, OntologyScanner.Result ontologyScannerResult) {

        this.ontologyId = ontologyScannerResult.ontologyId;
        this.reader = reader;
        this.ontologyScannerResult = ontologyScannerResult;
        this.outputFilePath = outputFilePath;

        edgesProperties = new ArrayList<String>(ontologyScannerResult.allEdgeProperties);
    }

    public void write() throws IOException {

        // edges are written throughout writing everything else, so we set up the writer here
        List<String> edgesCsvHeader = new ArrayList<>();
        edgesCsvHeader.add(":START_ID");
        edgesCsvHeader.add(":TYPE");
        edgesCsvHeader.add(":END_ID");
        edgesCsvHeader.add("_json");
        edgesCsvHeader.addAll(propertyHeaders(edgesProperties));

        edgesPrinter = CSVFormat.POSTGRESQL_CSV.withHeader(edgesCsvHeader.toArray(new String[0])).print(
                new File(outputFilePath + "/" + ontologyId + "_edges.csv"), Charset.defaultCharset());


        reader.beginObject(); // ontology

        writeOntology();

        writeTerms(outputFilePath + "/" + ontologyScannerResult.ontologyId + "_classes.csv", ontologyId,
                "OntologyTerm|OntologyClass", "class", ontologyScannerResult.allClassProperties);

        reader.nextName(); // properties

        writeTerms(outputFilePath + "/" + ontologyScannerResult.ontologyId + "_properties.csv", ontologyId,
                "OntologyTerm|OntologyProperty", "property", ontologyScannerResult.allPropertyProperties);

        reader.nextName(); // individuals

        writeTerms(outputFilePath + "/" + ontologyScannerResult.ontologyId + "_individuals.csv", ontologyId,
                "OntologyTerm|OntologyIndividual", "individual", ontologyScannerResult.allIndividualProperties);

        reader.endObject(); // ontology



        edgesPrinter.close(true);
    }

    public void writeOntology() throws IOException {

        List<String> properties = new ArrayList<String>( ontologyScannerResult.allOntologyProperties);

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add("id:ID");
        csvHeader.add(":LABEL");
        csvHeader.addAll(propertyHeaders(properties));

        String outName = outputFilePath + "/" + (String) ontologyScannerResult.ontologyId + "_ontologies.csv";

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        Map<String,Object> ontologyProperties = new HashMap<>();

        while(reader.peek() != JsonToken.END_OBJECT) {

            String propertyName = reader.nextName();

            if(propertyName.equals("classes"))
                break;

            ontologyProperties.put(propertyName, gson.fromJson(reader, Object.class));
        }

        String[] row = new String[csvHeader.size()];
        int n = 0;

        row[n++] = ((String) ontologyProperties.get("ontologyId")) + "+ontology+" + (String) ontologyProperties.get("uri");
        row[n++] = "Ontology";

        for (String column : properties) {
            row[n++] = serializeValue(ontologyProperties, column);
        }

        printer.printRecord(row);
        printer.close(true);
    }

    public void writeTerms(String outName, String ontologyId, String nodeLabels, String type, Set<String> allTermProperties) throws IOException {

        List<String> properties = new ArrayList<String>(allTermProperties);

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add("id:ID");
        csvHeader.add(":LABEL");
        csvHeader.addAll(propertyHeaders(properties));

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        reader.beginArray(); // terms

        while(reader.peek() != JsonToken.END_ARRAY) {

            Map<String, Object> term = gson.fromJson(reader, Map.class);

            String[] row = new String[csvHeader.size()];
            int n = 0;

            row[n++] = ontologyId + "+" + type + "+" + (String) term.get("uri");
            row[n++] = nodeLabels;

            for (String column : properties) {
                row[n++] = serializeValue(term, column);
            }

            printer.printRecord(row);
        }

        reader.endArray();

        printer.close(true);
    }

    public void maybeWriteEdges(String subject, String property, Object value) throws IOException {

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

                    // is the value the URI of something that exists in the ontology?
                    if (ontologyScannerResult.uriToTypes.containsKey(axiomValue)) {
                        printEdge(ontologyId, subject, property, axiomValue, mapValue);
                    }
                }
            } else if (v instanceof String) {

                // is the value the URI of something that exists in the ontology?
                if (ontologyScannerResult.uriToTypes.containsKey(v)) {
                    printEdge(ontologyId, subject, property, v, new HashMap<>());
                }

            } else {
                assert(false);
            }

        }

    }

    private void printEdge(String ontologyId, String aUri, String predicate, Object bUri, Map<String,Object> edgeProps) throws IOException {

        // In the case of punning, the same URI can have multiple types. In this case
        // it is ambiguous which of the types the edge points to/from. For example, if
        // a URI points to a node which is both a Class and an Individual, does it point
        // to the Class or the Individual?
        // 
        // In the hacky approach below, we just make multiple edges: in the above example,
        // one edge would point to the Class and another would point to the Individual.
        //
        // TODO: We should instead look up "predicate" and find out what the semantics
        // of the property are.
        //
        Set<OntologyScanner.NodeType> aTypes = ontologyScannerResult.uriToTypes.get(aUri);
        Set<OntologyScanner.NodeType> bTypes = ontologyScannerResult.uriToTypes.get(bUri);

        for(OntologyScanner.NodeType aType : aTypes) {
            for (OntologyScanner.NodeType bType : bTypes) {

                String[] row = new String[4 + edgesProperties.size()];
                int n = 0;

                row[n++] = ontologyId + "+" + nodeTypeToString(aType) + "+" + aUri;
                row[n++] = predicate;
                row[n++] = ontologyId + "+" + nodeTypeToString(bType) + "+" + bUri;
                row[n++] = gson.toJson(edgeProps);

                for (String column : edgesProperties) {
                    row[n++] = serializeValue(edgeProps, column);
                }

                edgesPrinter.printRecord(row);
            }
        }

    }

    private String nodeTypeToString(OntologyScanner.NodeType type) {
        switch(type) {
            case CLASS:
                return "class";
            case PROPERTY:
                return "property";
            case INDIVIDUAL:
                return "individual";
            case ONTOLOGY:
                return "ontology";
            default:
                throw new RuntimeException("Unknown node type");
        }
    }

    private String valueToCsv(Object value) {

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


    private String replaceNeo4jSpecialCharsValue(String val) {
        return val.replace("|", "+");
    }

    private List<String> propertyHeaders(List<String> fieldNames) {
        List<String> headers = new ArrayList<>();

        for(String k : fieldNames) {

            if(k.equals("_json")) {
                headers.add("_json");
            } else if(k.equals("uri")) {
                headers.add("uri");
            } else {
                headers.add(k.replace(":", "__") + ":string[]");
            }
        }

        return headers;
    }

    private String serializeValue(Map<String,Object> termProperties, String column) throws IOException {

        if(column.indexOf('+') != -1) {
            String lang = column.substring(0, column.indexOf('+'));
            String predicate = column.substring(column.indexOf('+')+1);

            return valueToCsv(getLocalizedValue(termProperties, predicate, lang));
        }

        Object value = termProperties.get(column);

        String uri = (String)termProperties.get("uri");

        // BNodes subjects don't get edges in the graph, so only write if there
        // is a URI
        //
        if(uri != null) {
            maybeWriteEdges(uri, column, value);
        }

        return valueToCsv(value);
    }


    private Object getLocalizedValue(Map<String,Object> properties, String predicate, String lang) {

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
