import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.sparql.core.Quad;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OwlTranslator implements StreamRDF {

    Map<String, Object> config;
    List<String> importUrls = new ArrayList<>();

    OwlTranslator(Map<String, Object> config) {


        this.config = config;

        String url = (String) config.get("ontology_purl");

        RDFDataMgr.parse(this, url);

	while(importUrls.size() > 0) {
		String importUrl = importUrls.get(0);
		importUrls.remove(0);

		System.out.println("import: " + importUrl);
		RDFDataMgr.parse(this, importUrl);
	}


        long startTime3 = System.nanoTime();
        for(String id : nodes.keySet()) {
            OwlNode c = nodes.get(id);
            if (c.type == OwlNode.NodeType.AXIOM) {

                List<OwlNode.Property> sourceProp = c.properties.properties.get("http://www.w3.org/2002/07/owl#annotatedSource");
                assert(sourceProp.size() == 1);
                Node source = sourceProp.get(0).value;

                List<OwlNode.Property> propertyProp = c.properties.properties.get("http://www.w3.org/2002/07/owl#annotatedProperty");
                assert(propertyProp.size() == 1);
                String property = propertyProp.get(0).value.toString();

                List<OwlNode.Property> targetProp = c.properties.properties.get("http://www.w3.org/2002/07/owl#annotatedTarget");
                assert(targetProp.size() == 1);
                Node target = targetProp.get(0).value;

                OwlNode sourceNode = nodes.get(nodeId(source));

                for(String p2 : c.properties.properties.keySet()) {
                    List<OwlNode.Property> v2 = c.properties.properties.get(p2);
                    for(OwlNode.Property prop : v2) {
                        if(!p2.equals("http://www.w3.org/2002/07/owl#annotatedSource")
                                && !p2.equals("http://www.w3.org/2002/07/owl#annotatedProperty")
                                && !p2.equals("http://www.w3.org/2002/07/owl#annotatedTarget")) {
                            sourceNode.properties.annotateProperty(property, target, p2, prop.value);
                        }
                    }
                }
            }
        }
        long endTime3 = System.nanoTime();
        System.out.println("reification: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000) + " - now have " + nodes.size() + " nodes");

    }

    public void write(JsonWriter writer) throws IOException {

        writer.beginObject();

        writer.name("config");
        new Gson().toJson(new Gson().toJsonTree(config).getAsJsonObject(), writer);

        writer.name("properties");
        writeNode(writer, ontologyNode);

        writer.name("classes");
        writer.beginArray();

        for(String id : nodes.keySet()) {
            OwlNode c = nodes.get(id);
            if (c.uri == null) {
                // don't print bnodes at top level
                continue;
            }
            if (c.type == OwlNode.NodeType.CLASS) {
                writeNode(writer, c);
            }
        }

        writer.endArray();
        writer.endObject();

    }


    private void writeNode(JsonWriter writer, OwlNode c) throws IOException {

        if(c.type == OwlNode.NodeType.RDF_LIST) {

            writer.beginArray();

            for(OwlNode cur = c;;) {

                List<OwlNode.Property> first = cur.properties.properties.get("http://www.w3.org/1999/02/22-rdf-syntax-ns#first");
                assert(first != null && first.size() == 1);
                writePropertyValue(writer, first.get(0));

                List<OwlNode.Property> rest = cur.properties.properties.get("http://www.w3.org/1999/02/22-rdf-syntax-ns#rest");
                assert(rest != null && rest.size() == 1);

                if(rest.get(0).value.isURI() &&
                        rest.get(0).value.getURI().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#nil")) {
                    break;
                }

                cur = nodes.get(nodeId(rest.get(0).value));
            }

            writer.endArray();

        } else {

            writer.beginObject();

            if (c.uri != null) {
                writer.name("uri");
                writer.value(c.uri);
            }

            writeProperties(writer, c.properties.properties);
            writer.endObject();
        }
    }

    private void writeProperties(JsonWriter writer, Map<String, List<OwlNode.Property>> properties) throws IOException {

        // TODO: sort keys, rdf:type should be first ideally
        for (String predicate : properties.keySet()) {

            List<OwlNode.Property> values = properties.get(predicate);

//            String name = predicate
//                    .replace("http://www.w3.org/2002/07/owl#", "owl:")
//                    .replace("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:");

            writer.name(predicate);

            if(values.size() == 1) {
                writePropertyValue(writer, values.get(0));
            } else {
                writer.beginArray();
                for (OwlNode.Property value : values) {
                    writePropertyValue(writer, value);
                }
                writer.endArray();
            }
        }
    }


    public void writePropertyValue(JsonWriter writer, OwlNode.Property value) throws IOException {
        if (value.properties != null) {
            // reified
            writer.beginObject();
            writer.name("value");
            writeValue(writer, value);
            writeProperties(writer, value.properties.properties);
            writer.endObject();
        } else {
            // not reified
            writeValue(writer, value);
        }

    }

    public void writeValue(JsonWriter writer, OwlNode.Property value) throws IOException {
        assert (value.properties == null);

        Node v = value.value;

        if(v.isBlank()) {
            OwlNode c = nodes.get(v.getBlankNodeId().toString());
            if(c == null) {
                writer.value("?");
            } else {
                writeNode(writer, c);
            }
        } else if(v.isURI()) {
            writer.value(v.getURI());
        } else if(v.isLiteral()) {
            if(v.getLiteralDatatypeURI().equals("http://www.w3.org/2001/XMLSchema#string") &&
                    v.getLiteralLanguage().equals("")
            ) {
                writer.value(v.toString(false));
            } else {
                writer.beginObject();
                writer.name("datatype");
                writer.value(v.getLiteralDatatypeURI());
                writer.name("value");
                writer.value(v.getLiteralLexicalForm());
                if(!v.getLiteralLanguage().equals("")) {
                    writer.name("lang");
                    writer.value(v.getLiteralLanguage());
                }
                writer.endObject();
            }
        } else {
            writer.value("?");
        }
    }






    private Map<String, OwlNode> nodes = new HashMap<>();
    OwlNode ontologyNode = null;

    private OwlNode getOrCreateTerm(Node node) {
        String id = nodeId(node);
        OwlNode term = nodes.get(id);
        if (term != null) {
            return term;
        }

        term = new OwlNode();

        if(!node.isBlank())
            term.uri = id;

        nodes.put(id, term);
        return term;
    }

    @Override
    public void start() {

    }

    @Override
    public void triple(Triple triple) {

        if(triple.getObject().isURI()) {
            handleUriTriple(triple);
        } else {
            handleLiteralTriple(triple);
        }

    }


    public void handleLiteralTriple(Triple triple) {

        String subjId = nodeId(triple.getSubject());
        OwlNode subjNode = getOrCreateTerm(triple.getSubject());

        subjNode.properties.addProperty(triple.getPredicate().getURI(), triple.getObject());

    }


//        <owl:equivalentClass>
//            <owl:Restriction>
//                <owl:onProperty rdf:resource="http://purl.obolibrary.org/obo/BFO_0000051"/>
//                <owl:someValuesFrom>
//                    <owl:Class>
//                        <owl:intersectionOf rdf:parseType="Collection">
//                            <rdf:Description rdf:about="http://purl.obolibrary.org/obo/PATO_0001509"/>
//                            <owl:Restriction>
//                                <owl:onProperty rdf:resource="http://purl.obolibrary.org/obo/RO_0000052"/>
//                                <owl:someValuesFrom rdf:resource="http://purl.obolibrary.org/obo/UBERON_0001255"/>
//                            </owl:Restriction>
//                            <owl:Restriction>
//                                <owl:onProperty rdf:resource="http://purl.obolibrary.org/obo/RO_0002573"/>
//                                <owl:someValuesFrom rdf:resource="http://purl.obolibrary.org/obo/PATO_0000460"/>
//                            </owl:Restriction>
//                        </owl:intersectionOf>
//                    </owl:Class>
//                </owl:someValuesFrom>
//            </owl:Restriction>
//        </owl:equivalentClass>


    public void handleUriTriple(Triple triple) {

        OwlNode subjNode = getOrCreateTerm(triple.getSubject());

        switch (triple.getPredicate().getURI()) {
            case "http://www.w3.org/1999/02/22-rdf-syntax-ns#type":
                handleType(subjNode, triple.getObject());
                break;
            case "http://www.w3.org/1999/02/22-rdf-syntax-ns#rest":
            case "http://www.w3.org/1999/02/22-rdf-syntax-ns#first":
                subjNode.type = OwlNode.NodeType.RDF_LIST;
                break;

	     case "http://www.w3.org/2002/07/owl#imports":
		importUrls.add(triple.getObject().getURI());
		//fallthrough

//            default:
//                subjNode.properties.addProperty(triple.getPredicate().getURI(), triple.getObject());
//                break;
        }

        subjNode.properties.addProperty(triple.getPredicate().getURI(), triple.getObject());


    }

    public void handleType(OwlNode subjNode, Node type) {

        switch (type.getURI()) {
            case "http://www.w3.org/2002/07/owl#Ontology":

		subjNode.type = OwlNode.NodeType.ONTOLOGY;

                if(ontologyNode == null) {
			ontologyNode = subjNode;
		}

                break;
            case "http://www.w3.org/2002/07/owl#Class":
                subjNode.type = OwlNode.NodeType.CLASS;
                break;
            case "http://www.w3.org/2002/07/owl#Axiom":
                subjNode.type = OwlNode.NodeType.AXIOM;
                break;
            case "http://www.w3.org/2002/07/owl#Restriction":
                subjNode.type = OwlNode.NodeType.RESTRICTION;
                break;
        }
    }

    @Override
    public void quad(Quad quad) {

    }

    @Override
    public void base(String s) {

    }

    @Override
    public void prefix(String s, String s1) {

    }

    @Override
    public void finish() {

    }



    private String nodeId(Node node)  {
        if(node.isURI()) {
            return node.getURI();
        }
        if(node.isBlank()) {
            return node.getBlankNodeId().toString();
        }
        throw new RuntimeException("unknown node type");
    }



}
