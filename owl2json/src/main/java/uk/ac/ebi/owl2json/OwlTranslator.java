package uk.ac.ebi.owl2json;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.util.NodeUtils;
import org.apache.jena.util.iterator.ExtendedIterator;
import uk.ac.ebi.owl2json.operations.ClassExpressionEvaluator;
import uk.ac.ebi.owl2json.operations.DefinitionAnnotator;
import uk.ac.ebi.owl2json.operations.ShortFormAnnotator;
import uk.ac.ebi.owl2json.operations.SynonymAnnotator;
import uk.ac.ebi.owl2json.operations.OntologyIdAnnotator;
import uk.ac.ebi.owl2json.operations.TypesAnnotator;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.sparql.core.Quad;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class OwlTranslator {

    public Map<String, Object> config;

    public Graph graph;
    public Model model;

    public List<String> importUrls = new ArrayList<>();
    public Set<String> languages = new HashSet<>();

    public int numberOfClasses = 0;
    public int numberOfProperties = 0;
    public int numberOfIndividuals = 0;

    Node ontologyNode = null;

    private void parseRDF(String url) {

        RDFParser.create()
                .forceLang(Lang.RDFXML)
                .strict(false)
                .checking(false)
                .source(url)
                .parse(model);
    }


    OwlTranslator(Map<String, Object> config) {


        model = ModelFactory.createDefaultModel();
        graph = model.getGraph();

        this.config = config;

        languages.add("en");

        String url = (String) config.get("ontology_purl");

        if (url == null) {

            Collection<Map<String, Object>> products =
                    (Collection<Map<String, Object>>) config.get("products");

            for (Map<String, Object> product : products) {

                String purl = (String) product.get("ontology_purl");

                if (purl != null && purl.endsWith(".owl")) {
                    url = purl;
                    break;
                }

            }

        }

        System.out.println("load ontology from: " + url);
        parseRDF(url);

        // Before we evaluate imports, mark all the nodes so far as not imported

        ResIterator it = model.listSubjects();

        while (it.hasNext()) {
            Resource res = it.next();
            if (res.isURIResource()) {
                graph.add(Triple.create(
                        NodeUtils.asNode(res.getURI()),
                        NodeUtils.asNode("imported"),
                        NodeFactory.createLiteral("false")
                ));
            }
        }

        ontologyNode = nodesWithRdfType("http://www.w3.org/2002/07/owl#Ontology").next();

        while (importUrls.size() > 0) {
            String importUrl = importUrls.get(0);
            importUrls.remove(0);

            System.out.println("import: " + importUrl);
            parseRDF(importUrl);
        }

        // Now the imports are done, mark everything else as imported
        while (it.hasNext()) {
            Resource res = it.next();
            if (res.isURIResource()) {
                if (!graph.contains(
                        NodeUtils.asNode(res.getURI()),
                        NodeUtils.asNode("imported"),
                        Node.ANY
                )) {
                    graph.add(Triple.create(
                            NodeUtils.asNode(res.getURI()),
                            NodeUtils.asNode("imported"),
                            NodeFactory.createLiteral("true")
                    ));
                }
            }
        }

        graph.add(Triple.create(
                ontologyNode,
                NodeUtils.asNode("numberOfClasses"),
                NodeFactory.createLiteral(Integer.toString(numberOfClasses))
        ));

        graph.add(Triple.create(
                ontologyNode,
                NodeUtils.asNode("numberOfProperties"),
                NodeFactory.createLiteral(Integer.toString(numberOfProperties))
        ));

        graph.add(Triple.create(
                ontologyNode,
                NodeUtils.asNode("numberOfIndividuals"),
                NodeFactory.createLiteral(Integer.toString(numberOfIndividuals))
        ));


        String now = java.time.LocalDateTime.now().toString();

        graph.add(Triple.create(
                ontologyNode,
                NodeUtils.asNode("loaded"),
                NodeFactory.createLiteral(now)
        ));

        TypesAnnotator.annotateTypes(this);
        ShortFormAnnotator.annotateShortForms(this);
        DefinitionAnnotator.annotateDefinitions(this);
        SynonymAnnotator.annotateSynonyms(this);
        ClassExpressionEvaluator.evaluateClassExpressions(this);
        OntologyIdAnnotator.annotateOntologyIds(this);

    }


    public void write(JsonWriter writer) throws IOException {

        writer.beginObject();

        writer.name("id");
        writer.value((String) config.get("id"));

        writer.name("ontologyConfig");
        new Gson().toJson(new Gson().toJsonTree(config).getAsJsonObject(), writer);

        writeProperties(writer, ontologyNode);

        writer.name("classes");
        writer.beginArray();

        for (ExtendedIterator<Node> iter = nodesWithType("class"); iter.hasNext(); ) {
            Node node = iter.next();
            writeNode(writer, node);
        }

        writer.endArray();


        writer.name("properties");
        writer.beginArray();

        for (ExtendedIterator<Node> iter = nodesWithType("property"); iter.hasNext(); ) {
            Node node = iter.next();
            writeNode(writer, node);
        }

        writer.endArray();


        writer.name("individuals");
        writer.beginArray();

        for (ExtendedIterator<Node> iter = nodesWithType("individual"); iter.hasNext(); ) {
            Node node = iter.next();
            writeNode(writer, node);
        }

        writer.endArray();


        writer.endObject();

    }


    private void writeNode(JsonWriter writer, Node c) throws IOException {

        // is it a list?
        if (graph.contains(c, NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#first"), Node.ANY)) {

            writer.beginArray();

            for (Node cur = c; ; ) {

                List<Node> first = graph.find(cur, NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#first"), Node.ANY).mapWith(t -> t.getObject()).toList();
                assert (first != null && first.size() == 1);
                writePropertyValue(writer, null, null, first.get(0));

                List<Node> rest = graph.find(cur, NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#rest"), Node.ANY).mapWith(t -> t.getObject()).toList();
                assert (rest != null && rest.size() == 1);

                if (rest.get(0).isURI() &&
                        rest.get(0).getURI().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#nil")) {
                    break;
                }

                cur = rest.get(0);
            }

            writer.endArray();

        } else {

            writer.beginObject();

            if (c.isURI()) {
                writer.name("uri");
                writer.value(c.getURI());
            }

            writeProperties(writer, c);
            writer.endObject();
        }
    }


    private void writeProperties(JsonWriter writer, Node node) throws IOException {

        // TODO: sort keys, type and uri should be first ideally

        List<Triple> triples = graph.find(node, Node.ANY, Node.ANY).toList();

        Set<String> predicates = triples.stream()
                .map(t -> t.getPredicate())
                .map(p -> p.getURI().toString())
                .collect(Collectors.toSet());


        for(String predicate : predicates) {

            writer.name(predicate);

            List<Node> values = triples.stream()
                    .filter(t -> t.getPredicate().toString().equals(predicate))
                    .map(t -> t.getObject())
                    .collect(Collectors.toList());

            if (values.size() == 1) {
                writePropertyValue(writer, node, NodeUtils.asNode(predicate), values.get(0));
            } else {
                writer.beginArray();
                for (Node value : values) {
                    writePropertyValue(writer, node, NodeUtils.asNode(predicate), value);
                }
                writer.endArray();
            }
        }


        // Labels for rendering the properties in the frontend (or for API consumers)
        //
        writer.name("propertyLabels");
        writer.beginObject();

        for (String predicate : predicates) {

            List<Triple> propertyTriples = graph.find(
                    NodeUtils.asNode(predicate),
                    Node.ANY,
                    Node.ANY
            ).toList();

            List<Node> labelProps = propertyTriples.stream()
                    .filter(t -> t.getPredicate().getURI().equals("http://www.w3.org/2000/01/rdf-schema#label"))
                    .map(t -> t.getObject())
                    .collect(Collectors.toList());

            if (labelProps.size() > 0) {
                for (Node prop : labelProps) {

                    if (!prop.isLiteral())
                        continue;

                    String lang = prop.getLiteralLanguage();

                    if (lang == null || lang.equals(""))
                        lang = "en";

                    writer.name(lang + "+" + predicate);
                    writer.value(prop.getLiteralLexicalForm());
                }
            }

        }

        writer.endObject();
    }


    public void writePropertyValue(JsonWriter writer, Node s, Node p, Node o) throws IOException {

        var axiomsOfThisSubject = graph.find(
                Node.ANY,
                NodeUtils.asNode("http://www.w3.org/2002/07/owl#annotatedSource"),
                s
        ).mapWith(t -> t.getSubject());

        var filteredAxioms = axiomsOfThisSubject.filterKeep(axiom -> {

            var annotatedProperty = graph.find(
                    axiom,
                    NodeUtils.asNode("http://www.w3.org/2002/07/owl#annotatedProperty"),
                    Node.ANY).toList().get(0);

            var annotatedTarget = graph.find(
                    axiom,
                    NodeUtils.asNode("http://www.w3.org/2002/07/owl#annotatedTarget"),
                    Node.ANY).toList().get(0);

            return annotatedProperty.equals(p) && annotatedTarget.equals(o);
        }).toList();

        // can only reify once!
        assert(filteredAxioms.size() <= 1);

        if (filteredAxioms.size() > 0) {
            // reified
            writer.beginObject();
            writer.name("value");
            writeValue(writer, o);
            writeProperties(writer, filteredAxioms.get(0));
            writer.endObject();
        } else {
            // not reified
            writeValue(writer, o);
        }

    }

    public void writeValue(JsonWriter writer, Node v) throws IOException {
        //assert (value.properties == null);

        if (v.isBlank()) {
//            Node c = nodes.get(v.getBlankNodeId().toString());
//            if (c == null) {
//                writer.value("?");
//            } else {
                writeNode(writer, v);
//            }
        } else if (v.isURI()) {
            writer.value(v.getURI());
        } else if (v.isLiteral()) {
            if (v.getLiteralDatatypeURI().equals("http://www.w3.org/2001/XMLSchema#string") &&
                    v.getLiteralLanguage().equals("")
            ) {
                writer.value(v.toString(false));
            } else {
                writer.beginObject();
                writer.name("datatype");
                writer.value(v.getLiteralDatatypeURI());
                writer.name("value");
                writer.value(v.getLiteralLexicalForm());
                if (!v.getLiteralLanguage().equals("")) {
                    writer.name("lang");
                    writer.value(v.getLiteralLanguage());
                }
                writer.endObject();
            }
        } else {
            writer.value("?");
        }
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



    public String nodeId(Node node) {
        if (node.isURI()) {
            return node.getURI();
        }
        if (node.isBlank()) {
            return node.getBlankNodeId().toString();
        }
        throw new RuntimeException("unknown node type");
    }


    public ExtendedIterator<Node> nodesWithType(String type) {

        return graph.find(
                Node.ANY,
                NodeUtils.asNode("type"),
                NodeUtils.asNode(type)
        ).mapWith(t -> t.getSubject());
    }

    public ExtendedIterator<Node> nodesWithRdfType(String type) {

        return graph.find(
                Node.ANY,
                NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                NodeUtils.asNode(type)
        ).mapWith(t -> t.getSubject());
    }

    public boolean nodeHasRdfType(Node node, String type) {

        return graph.contains(
                node,
                NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                NodeUtils.asNode(type)
        );
    }
}



