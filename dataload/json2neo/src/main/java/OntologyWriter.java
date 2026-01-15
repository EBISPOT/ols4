import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import uk.ac.ebi.ols.shared.Embeddings;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

import static uk.ac.ebi.ols.shared.DefinedFields.*;
public class OntologyWriter {

    Gson gson = new Gson();

    JsonReader reader;
    String outputFilePath;
    String ontologyId;
    OntologyManifestInfo manifestInfo;
    Map<String, Embeddings> embeddings;

    List<String> edgesProperties;
    CSVPrinter edgesPrinter;

    public static final Set<String> PROPERTY_BLACKLIST = Set.of(
            // large and doesn't get queried
            APPEARS_IN.getText(),
            // all property values together, this is for solr and not useful in neo4j
            "searchableAnnotationValues"
    );

    public static final Set<String> EDGE_BLACKLIST = Set.of(
            // don't create lots of "iri" edges pointing from each node to itself
            "iri",
            // these properties are informational and should not create edges
            "hierarchicalProperty",
            "definitionProperty",
            "synonymProperty",
            // these are redundant in neo4j as we already have the parent edges and cypher queries can be recursive
            DIRECT_ANCESTOR.getText(),
            HIERARCHICAL_ANCESTOR.getText(),
            // redundant in neo4j because we already have relatedTo which can be queried in both directions
            "relatedFrom"
    );

    public OntologyWriter(JsonReader reader, String outputFilePath, OntologyManifestInfo manifestInfo, Map<String, Embeddings> embeddings) {

        this.ontologyId = manifestInfo.ontologyId;
        this.reader = reader;
        this.manifestInfo = manifestInfo;
        this.outputFilePath = outputFilePath;
        this.embeddings = embeddings;

        edgesProperties = new ArrayList<String>(manifestInfo.allEdgeProperties);
        Collections.sort(edgesProperties);
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


        // Note: reader is already positioned inside the ontology object (ontologyId was already read in NeoConverter)

	Map<String,Object> ontologyProperties = new LinkedHashMap<>();
	
	// Store the ontologyId that was already read
	ontologyProperties.put("ontologyId", ontologyId);

	while(reader.peek() != JsonToken.END_OBJECT) {

		String name = reader.nextName();

		if(name.equals("classes")) {
			writeEntities(outputFilePath + "/" + manifestInfo.ontologyId + "_classes.csv", ontologyId,
				"OntologyEntity|OntologyClass", "class", manifestInfo.allClassProperties);
			continue;
		}

		if(name.equals("properties")) {
			writeEntities(outputFilePath + "/" + manifestInfo.ontologyId + "_properties.csv", ontologyId,
				"OntologyEntity|OntologyProperty", "property", manifestInfo.allPropertyProperties);
			continue;
		}

		if(name.equals("individuals")) {
			writeEntities(outputFilePath + "/" + manifestInfo.ontologyId + "_individuals.csv", ontologyId,
				"OntologyEntity|OntologyIndividual", "individual", manifestInfo.allIndividualProperties);
			continue;
		}

		ontologyProperties.put(name, gson.fromJson(reader, Object.class));
	}

	// Note: reader.endObject() is not called here - it will be called in NeoConverter after this method returns

	writeOntology(ontologyProperties);

        edgesPrinter.close(true);
    }

    public void writeOntology(Map<String,Object> ontologyProperties) throws IOException {

        List<String> properties = new ArrayList<String>( manifestInfo.allOntologyProperties);
        Collections.sort(properties);

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add("id:ID");
        csvHeader.add(":LABEL");
        csvHeader.add("_json");
        csvHeader.addAll(propertyHeaders(properties));

        String outName = outputFilePath + "/" + (String) manifestInfo.ontologyId + "_ontologies.csv";

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        String[] row = new String[csvHeader.size()];
        int n = 0;

        row[n++] = ((String) ontologyProperties.get("ontologyId")) + "+ontology+" + (String) ontologyProperties.get("iri");
        row[n++] = "Ontology";
	row[n++] = gson.toJson(ontologyProperties);

        for (String column : properties) {
            row[n++] = serializeValue(ontologyProperties, column);
        }

        printer.printRecord(row);
        printer.close(true);
    }

    public void writeEntities(String outName, String ontologyId, String nodeLabels, String type, Set<String> allEntityProperties) throws IOException {

        List<String> properties = new ArrayList<String>(allEntityProperties);
        Collections.sort(properties);
        
        // Get embedding model names for CSV columns
        List<String> embeddingModelNames = new ArrayList<>();
        if (embeddings != null) {
            embeddingModelNames.addAll(embeddings.keySet());
            Collections.sort(embeddingModelNames); // Ensure consistent column order
        }

        List<String> csvHeader = new ArrayList<>();
        csvHeader.add("id:ID");
        csvHeader.add(":LABEL");
        csvHeader.add("_json");
        csvHeader.addAll(propertyHeaders(properties));
        
        // Add embedding columns
        for (String modelName : embeddingModelNames) {
            csvHeader.add("embeddings_" + modelName + ":float[]");
        }

        CSVPrinter printer = CSVFormat.POSTGRESQL_CSV.withHeader(csvHeader.toArray(new String[0])).print(
                new File(outName), Charset.defaultCharset());

        reader.beginArray(); // entities

        while(reader.peek() != JsonToken.END_ARRAY) {

            Map<String, Object> entity = gson.fromJson(reader, Map.class);

            String[] row = new String[csvHeader.size()];
            int n = 0;

            row[n++] = ontologyId + "+" + type + "+" + (String) entity.get("iri");
            row[n++] = nodeLabels;
            int _jsonIdx = n++;

            for (String column : properties) {
                row[n++] = serializeValue(entity, column);
            }
            
            // Serialize embedding values as pipe-separated floats
            for (String modelName : embeddingModelNames) {
                row[n++] = serializeEmbedding(ontologyId, type, (String) entity.get("iri"), modelName);
            }

            row[_jsonIdx] = gson.toJson(entity);

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

                Map<String, Object> mapValue = (Map<String, Object>) v;

		Object type = mapValue.get("type");

		if(type == null || ! (type instanceof List)) {
			continue;
		}

		List<String> types = (List<String>) type;

		if(types.contains("reification")) {

                    // reification 
                    Object reifiedValue = mapValue.get("value");
                    assert (reifiedValue instanceof String);

		    List<Map<String,Object>> axioms = (List<Map<String,Object>>) mapValue.get("axioms");
		    assert(axioms != null);

                    // is the value the URI of something that exists in the ontology?
                    if (manifestInfo.uriToTypes.containsKey(reifiedValue)) {
			// create one edge for each axiom
			for(Map<String,Object> axiom : axioms) {
				printEdge(ontologyId, subject, property, reifiedValue, axiom);
			}
                    }

                } else if(types.contains("related")) {

                    Object relatedValue = mapValue.get("value");
                    assert (relatedValue instanceof String);

                    // is the value the URI of something that exists in the ontology?
                    if (manifestInfo.uriToTypes.containsKey(relatedValue)) {
			printEdge(ontologyId, subject, property, relatedValue, mapValue);
                    }
		}

            } else if (v instanceof String) {

                // is the value the URI of something that exists in the ontology?
                if (manifestInfo.uriToTypes.containsKey(v)) {
                    printEdge(ontologyId, subject, property, v, Map.of());
                }

            } else {
                assert(false);
            }

        }

    }

    private void printEdge(String ontologyId, String aUri, String predicate, Object bUri, Map<String,Object> edgeProps) throws IOException {

        if(EDGE_BLACKLIST.contains(predicate))
            return;

        // In the case of punning, the same URI can have multiple types. In this case
        // it is ambiguous which of the types the edge points to/from. For example, if
        // a URI points to a node which is both a Class and an Individual, does it point
        // to the Class or the Individual?
        // 
        // In the hacky approach below, we just make multiple edges: in the above example,
        // one edge would point to the Class and another would point to the Individual.
        //
        // TODO: fix
        //
        Set<OntologyManifestInfo.NodeType> aTypes = manifestInfo.uriToTypes.get(aUri);
        Set<OntologyManifestInfo.NodeType> bTypes = manifestInfo.uriToTypes.get(bUri);

        for(OntologyManifestInfo.NodeType aType : aTypes) {
            for (OntologyManifestInfo.NodeType bType : bTypes) {

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

    private String nodeTypeToString(OntologyManifestInfo.NodeType type) {
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

            // could be a reification or a localisation, but we are writing the value
            // itself as a property directly in this case; the rest of the reification
            // properties or localized strings are preserved in the _json field

            Map<String, Object> mapValue = (Map<String, Object>) value;

            if (mapValue.containsKey("value")) {
                Object val = mapValue.get("value");
                return valueToCsv(val);
            }

	    // probably a class expression; wouldn't result in anything queryable
	    // so store nothing in the field
	    // could also be json junk from the ontology config
	    //
	    return "";
        }

        return replaceNeo4jSpecialCharsValue(gson.toJson(value));
    }


    private String replaceNeo4jSpecialCharsValue(String val) {
        return val.replace("|", "\\u007C");
    }

    private List<String> propertyHeaders(List<String> fieldNames) {
        List<String> headers = new ArrayList<>();

        for(String k : fieldNames) {

            if(k.equals("iri")) {
                headers.add("iri");
            } else if(k.startsWith("embeddings_")) {
                // headers.add("embeddings_" + k.substring("embeddings_".length()) + ":float[]");
            } else {
                headers.add(k.replace(":", "__") + ":string[]");
            }
        }

        return headers;
    }

    private String serializeValue(Map<String,Object> entityProperties, String column) throws IOException {

        if(column.indexOf('+') != -1 && !column.startsWith("related")) {
            String lang = column.substring(0, column.indexOf('+'));
            String predicate = column.substring(column.indexOf('+')+1);

            return valueToCsv(getLocalizedValue(entityProperties, predicate, lang));
        }

        Object value = entityProperties.get(column);

        String uri = (String)entityProperties.get("iri");

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

    /**
     * Serialize embeddings for a given entity and model as a pipe-separated string of floats.
     */
    private String serializeEmbedding(String ontologyId, String entityType, String iri, String modelName) {
        if (embeddings == null) {
            return "";
        }
        
        Embeddings emb = embeddings.get(modelName);
        if (emb == null) {
            return "";
        }
        
        float[] embeddingsArray = emb.getEmbeddings(ontologyId, entityType, iri);
        if (embeddingsArray == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embeddingsArray.length; i++) {
            if (i > 0) {
                sb.append("|");
            }
            sb.append(embeddingsArray[i]);
        }
        return sb.toString();
    }


}
