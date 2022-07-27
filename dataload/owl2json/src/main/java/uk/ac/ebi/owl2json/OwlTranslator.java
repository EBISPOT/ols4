package uk.ac.ebi.owl2json;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import org.apache.jena.riot.Lang;
import uk.ac.ebi.owl2json.operations.*;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.sparql.core.Quad;

import java.io.IOException;
import java.util.*;

public class OwlTranslator implements StreamRDF {

    public Map<String, Object> config;
    public List<String> importUrls = new ArrayList<>();
    public Set<String> languages = new HashSet<>();

    public Set<String> hasChildren = new HashSet<>();
    public Set<String> hasParents = new HashSet<>();

    public int numberOfClasses = 0;
    public int numberOfProperties = 0;
    public int numberOfIndividuals = 0;

    private void parseRDF(String url) {

        RDFParser.create()
                .forceLang(Lang.RDFXML)
                .strict(false)
                .checking(false)
                .source(url)
                .parse(this);
    }


    OwlTranslator(Map<String, Object> config) {

        long startTime = System.nanoTime();

        this.config = config;

        languages.add("en");

        String url = (String) config.get("ontology_purl");

        if(url == null) {

            Collection<Map<String,Object>> products =
                (Collection<Map<String,Object>>) config.get("products");

            for(Map<String,Object> product : products) {

                String purl = (String) product.get("ontology_purl");

                if(purl != null && purl.endsWith(".owl")) {
                    url = purl;
                    break;
                }

            }

        }

        System.out.println("load ontology from: " + url);
        parseRDF(url);

        // Before we evaluate imports, mark all the nodes so far as not imported
        for(String id : nodes.keySet()) {
            OwlNode c = nodes.get(id);
            if(c.uri != null) {
                c.properties.addProperty("imported", NodeFactory.createLiteral("false"));
            }
        }


	while(importUrls.size() > 0) {
		String importUrl = importUrls.get(0);
		importUrls.remove(0);

		System.out.println("import: " + importUrl);
        parseRDF(importUrl);
	}

        // Now the imports are done, mark everything else as imported
    for(String id : nodes.keySet()) {
        OwlNode c = nodes.get(id);
        if(c.uri != null) {
            if(!c.properties.properties.containsKey("imported")) {
                c.properties.addProperty("imported", NodeFactory.createLiteral("true"));
            }
        }
    }

	ontologyNode.properties.addProperty(
		"numberOfTerms", NodeFactory.createLiteral(Integer.toString(numberOfClasses + numberOfProperties + numberOfIndividuals)));

	ontologyNode.properties.addProperty(
		"numberOfClasses", NodeFactory.createLiteral(Integer.toString(numberOfClasses)));

	ontologyNode.properties.addProperty(
		"numberOfProperties", NodeFactory.createLiteral(Integer.toString(numberOfProperties)));

	ontologyNode.properties.addProperty(
		"numberOfIndividuals", NodeFactory.createLiteral(Integer.toString(numberOfIndividuals)));


	String now = java.time.LocalDateTime.now().toString();

	ontologyNode.properties.addProperty(
		"loaded", NodeFactory.createLiteral(now));


    long endTime = System.nanoTime();
    System.out.println("load ontology: " + ((endTime - startTime) / 1000 / 1000 / 1000));

	ShortFormAnnotator.annotateShortForms(this);
	DefinitionAnnotator.annotateDefinitions(this);
	SynonymAnnotator.annotateSynonyms(this);
	AxiomEvaluator.evaluateAxioms(this);
	ClassExpressionEvaluator.evaluateClassExpressions(this);
    OntologyIdAnnotator.annotateOntologyIds(this);
    TypesAnnotator.annotateTypes(this);
    HierarchyFlagsAnnotator.annotateHierarchyFlags(this);

    }


    public void write(JsonWriter writer) throws IOException {

        writer.beginObject();

        writer.name("ontologyId");
        writer.value((String) config.get("id"));

        writer.name("uri");
        writer.value(ontologyNode.uri);

        writer.name("ontologyConfig");
        new Gson().toJson(new Gson().toJsonTree(config).getAsJsonObject(), writer);

        writeProperties(writer, ontologyNode.properties.properties);

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


        writer.name("properties");
        writer.beginArray();

        for(String id : nodes.keySet()) {
            OwlNode c = nodes.get(id);
            if (c.uri == null) {
                // don't print bnodes at top level
                continue;
            }
            if (c.type == OwlNode.NodeType.PROPERTY) {
                writeNode(writer, c);
            }
        }

        writer.endArray();


        writer.name("individuals");
        writer.beginArray();

        for(String id : nodes.keySet()) {
            OwlNode c = nodes.get(id);
            if (c.uri == null) {
                // don't print bnodes at top level
                continue;
            }
            if (c.type == OwlNode.NodeType.NAMED_INDIVIDUAL) {
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


        // Labels for rendering the properties in the frontend (or for API consumers)
        //
        writer.name("propertyLabels");
        writer.beginObject();

        for(String k : properties.keySet()) {

            OwlNode labelNode = nodes.get(k);
            if(labelNode == null) {
                continue;
            }

            List<OwlNode.Property> labelProps = labelNode.properties.properties.get("http://www.w3.org/2000/01/rdf-schema#label");

            if(labelProps != null && labelProps.size() > 0) {
                for (OwlNode.Property prop : labelProps) {

                    if(!prop.value.isLiteral())
                        continue;

                    String lang = prop.value.getLiteralLanguage();

                    if(lang==null||lang.equals(""))
                        lang="en";

                    writer.name(lang+"+"+k);
                    writer.value(prop.value.getLiteralLexicalForm());
                }
            }

        }

        writer.endObject();
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






    public Map<String, OwlNode> nodes = new HashMap<>();
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

        if(triple.getObject().isLiteral()) {
            handleLiteralTriple(triple);
        } else {
            handleNamedNodeTriple(triple);
        }

        // TODO: BNodes?

    }


    public void handleLiteralTriple(Triple triple) {

        String subjId = nodeId(triple.getSubject());
        OwlNode subjNode = getOrCreateTerm(triple.getSubject());

        String lang = triple.getObject().getLiteralLanguage();
        if(lang != null) {
            languages.add(lang);
        }

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


    public void handleNamedNodeTriple(Triple triple) {

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
                break;

            case "http://www.w3.org/2000/01/rdf-schema#subClassOf":

                boolean top = triple.getObject().isURI() &&
                        triple.getObject().getURI().equals("http://www.w3.org/2002/07/owl#Thing");

                if(!top) {

                    if(subjNode.uri != null)
                        hasParents.add(subjNode.uri);

                    if(triple.getObject().isURI())
                        hasChildren.add(triple.getObject().getURI());

                }

                break;
        }

        subjNode.properties.addProperty(triple.getPredicate().getURI(), triple.getObject());


    }

    public void handleType(OwlNode subjNode, Node type) {

        if(!type.isURI())
            return;

        switch (type.getURI()) {

            case "http://www.w3.org/2002/07/owl#Ontology":

		subjNode.type = OwlNode.NodeType.ONTOLOGY;

                if(ontologyNode == null) {
			ontologyNode = subjNode;
		}

                break;

            case "http://www.w3.org/2002/07/owl#Class":
                subjNode.type = OwlNode.NodeType.CLASS;
		++ numberOfClasses;
                break;

            case "http://www.w3.org/2002/07/owl#AnnotationProperty":
            case "http://www.w3.org/2002/07/owl#ObjectProperty":
            case "http://www.w3.org/2002/07/owl#DatatypeProperty":
                subjNode.type = OwlNode.NodeType.PROPERTY;
		++ numberOfProperties;
                break;

            case "http://www.w3.org/2002/07/owl#NamedIndividual":
                subjNode.type = OwlNode.NodeType.NAMED_INDIVIDUAL;
		++ numberOfIndividuals;
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



    public String nodeId(Node node)  {
        if(node.isURI()) {
            return node.getURI();
        }
        if(node.isBlank()) {
            return node.getBlankNodeId().toString();
        }
        throw new RuntimeException("unknown node type");
    }




}
