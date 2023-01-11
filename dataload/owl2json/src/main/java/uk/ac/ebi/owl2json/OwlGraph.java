package uk.ac.ebi.owl2json;

import com.fasterxml.jackson.databind.annotation.JsonAppend;
import com.google.gson.stream.JsonWriter;

import uk.ac.ebi.owl2json.annotators.*;
import uk.ac.ebi.owl2json.helpers.RdfListEvaluator;
import uk.ac.ebi.owl2json.properties.*;
import uk.ac.ebi.owl2json.properties.PropertyValue.Type;

import org.apache.jena.riot.Lang;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFParserBuilder;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.shacl.sys.C;
import org.apache.jena.sparql.core.Quad;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;

public class OwlGraph implements StreamRDF {

    public Map<String, Object> config;
    public List<String> importUrls = new ArrayList<>();
    public Set<String> languages = new TreeSet<>();

    public int numberOfClasses = 0;
    public int numberOfProperties = 0;
    public int numberOfIndividuals = 0;



    public static long STATS_TOTAL_DOWNLOAD_TIME = 0;


    private RDFParserBuilder createParser() {

        return RDFParser.create()
                .forceLang(Lang.RDFXML)
                .strict(false)
                .checking(false);
    }

    private void parseRDF(String url)  {

        long begin = System.nanoTime();

	try {
		if (loadLocalFiles && !url.contains("://")) {
			System.out.println("Using local file for " + url);
			createParser().source(new FileInputStream(url)).parse(this);
		} else {
			if (downloadedPath != null) {
				String existingDownload = downloadedPath + "/" + urlToFilename(url);
				try {
					FileInputStream is = new FileInputStream(existingDownload);
					System.out.println("Using predownloaded file for " + url);
					createParser().source(is).parse(this);
				} catch (FileNotFoundException e) {
					System.out.println("Downloading (not predownloaded) " + url);
					createParser().source(url).parse(this);
				}
			} else {
				System.out.println("Downloading (no predownload path provided) " + url);
				createParser().source(url).parse(this);
			}
		}
	} catch (FileNotFoundException e) {
		throw new RuntimeException(e);
	}

        long end = System.nanoTime();

        System.out.println("Downloading " + url + " took " + ((end-begin) / 1000 / 1000 / 1000) + "s");
        STATS_TOTAL_DOWNLOAD_TIME += (end - begin);
    }

    private String urlToFilename(String url) {
        return url.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
    }


    private boolean loadLocalFiles;

    String downloadedPath;


    OwlGraph(Map<String, Object> config, boolean loadLocalFiles, boolean noDates, String downloadedPath) {

	this.loadLocalFiles = loadLocalFiles;
	this.downloadedPath = downloadedPath;

        long startTime = System.nanoTime();

        this.config = config;

        languages.add("en");

        String url = (String) config.get("ontology_purl");

        if(url == null) {

            Collection<Map<String,Object>> products =
                (Collection<Map<String,Object>>) config.get("products");

            if(products != null) {
                for(Map<String,Object> product : products) {

                    String purl = (String) product.get("ontology_purl");

                    if(purl != null && purl.endsWith(".owl")) {
                        url = purl;
                        break;
                    }

                }
            }

        }

        if(url == null) {
            System.out.println("Could not determine URL for ontology " + (String)config.get("id"));
            return;
        }

        System.out.println("load ontology from: " + url);
        parseRDF(url);

        // Before we evaluate imports, mark all the nodes so far as not imported
        for(String id : nodes.keySet()) {
            OwlNode c = nodes.get(id);
            if(c.uri != null) {
                c.properties.addProperty("imported", PropertyValueLiteral.fromString("false"));
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
            if(!c.properties.hasProperty("imported")) {
                c.properties.addProperty("imported", PropertyValueLiteral.fromString("true"));
            }
        }
    }

	ontologyNode.properties.addProperty(
		"numberOfEntities", PropertyValueLiteral.fromString(Integer.toString(numberOfClasses + numberOfProperties + numberOfIndividuals)));

	ontologyNode.properties.addProperty(
		"numberOfClasses", PropertyValueLiteral.fromString(Integer.toString(numberOfClasses)));

	ontologyNode.properties.addProperty(
		"numberOfProperties", PropertyValueLiteral.fromString(Integer.toString(numberOfProperties)));

	ontologyNode.properties.addProperty(
		"numberOfIndividuals", PropertyValueLiteral.fromString(Integer.toString(numberOfIndividuals)));


    if(!noDates) {
        String now = java.time.LocalDateTime.now().toString();

        ontologyNode.properties.addProperty(
            "loaded", PropertyValueLiteral.fromString(now));
    }

    for(String language : languages) {
        ontologyNode.properties.addProperty("language", PropertyValueLiteral.fromString(language));
    }


    long endTime = System.nanoTime();
    System.out.println("load ontology: " + ((endTime - startTime) / 1000 / 1000 / 1000));

    OboSynonymTypeNameAnnotator.annotateOboSynonymTypeNames(this); // n.b. this one labels axioms so must run before the ReifiedPropertyAnnotator
    DirectParentsAnnotator.annotateDirectParents(this);
    RelatedAnnotator.annotateRelated(this);
    HierarchicalParentsAnnotator.annotateHierarchicalParents(this); // must run after RelatedAnnotator
    ShortFormAnnotator.annotateShortForms(this);
    DefinitionAnnotator.annotateDefinitions(this);
    SynonymAnnotator.annotateSynonyms(this);
    ReifiedPropertyAnnotator.annotateReifiedProperties(this);
    OntologyMetadataAnnotator.annotateOntologyMetadata(this);
    HierarchyFlagsAnnotator.annotateHierarchyFlags(this); // must run after DirectParentsAnnotator and HierarchicalParentsAnnotator
    IsObsoleteAnnotator.annotateIsObsolete(this);
    IsDefiningOntologyAnnotator.annotateIsDefiningOntology(this);
    LabelAnnotator.annotateLabels(this); // must run after ShortFormAnnotator
    ConfigurablePropertyAnnotator.annotateConfigurableProperties(this);
    PreferredRootsAnnotator.annotatePreferredRoots(this);
    DisjointWithAnnotator.annotateDisjointWith(this);
    EquivalenceAnnotator.annotateEquivalance(this);

    }


    static final Set<String> classTypes = new TreeSet<>(Set.of("entity", "class"));
    static final Set<String> propertyTypes = new TreeSet<>(Set.of("entity", "property"));
    static final Set<String> individualTypes = new TreeSet<>(Set.of("entity", "individual"));

    public void write(JsonWriter writer) throws IOException {

        writer.beginObject();

        writer.name("ontologyId");
        writer.value(((String) config.get("id")).toLowerCase());

        writer.name("iri");
        writer.value(ontologyNode.uri);

        for(String configKey : config.keySet()) {
            Object configVal = config.get(configKey);

            // we include this (lowercased) as "ontologyId" rather than "id",
	    // so that the name "id" doesn't clash with downstream id fields in neo4j/solr
	    //
            if(configKey.equals("id"))
                continue;

	    // already included explicitly above
            if(configKey.equals("ontologyId"))
                continue;
                
            // don't print the iri from the config, we already printed the one from the OWL
            // TODO: which one to keep, or should we keep both?
            if(configKey.equals("iri"))
                continue;

            // everything else from the config is stored as a normal property
            writer.name(configKey); 
            writeGenericValue(writer, configVal);
        }

        writeProperties(writer, ontologyNode.properties, Set.of("ontology"));

        writer.name("classes");
        writer.beginArray();

        for(String id : nodes.keySet()) {
            OwlNode c = nodes.get(id);
            if (c.uri == null) {
                // don't print bnodes at top level
                continue;
            }
            if (c.types.contains(OwlNode.NodeType.CLASS)) {
                writeNode(writer, c, classTypes);
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
            if (c.types.contains(OwlNode.NodeType.PROPERTY)) {
                writeNode(writer, c, propertyTypes);
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
            if (c.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {
                writeNode(writer, c, individualTypes);
            }
        }

        writer.endArray();


        writer.endObject();

    }


    private void writeNode(JsonWriter writer, OwlNode c, Set<String> types) throws IOException {

        if(c.types.contains(OwlNode.NodeType.RDF_LIST)) {

            writer.beginArray();

            for(PropertyValue listEntry : RdfListEvaluator.evaluateRdfList(c, this)) {
                writePropertyValue(writer, listEntry, null);
            }

            writer.endArray();

        } else {

            writer.beginObject();

            if (c.uri != null) {
                writer.name("iri");
                writer.value(c.uri);
            }

            writeProperties(writer, c.properties, types);
            writer.endObject();
        }
    }

    private void writeProperties(JsonWriter writer, PropertySet properties, Set<String> types) throws IOException {

        if(types != null) {
            writer.name("type");
            writer.beginArray();
            for(String type : types) {
                writer.value(type);
            }
            writer.endArray();
        }

        // TODO: sort keys, rdf:type should be first ideally
        for (String predicate : properties.getPropertyPredicates()) {

		if(types != null && types.contains("ontology") && predicate.equals("ontologyId")) {
			// hack to workaround a punning issue.
			// if the Ontology is also a Class it will have an ontologyId added by
			// the OntologyMetadataAnnotator, but there is already an ontologyId field
			// printed as part of the ontology object, so skip this one...
			// TODO: fix this as part of the punning refactoring
			//
			continue;
		}

            List<PropertyValue> values = properties.getPropertyValues(predicate);

//            String name = predicate
//                    .replace("http://www.w3.org/2002/07/owl#", "owl:")
//                    .replace("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:");

            writer.name(predicate);

            if(values.size() == 1) {
                writePropertyValue(writer, values.get(0), null);
            } else {
                writer.beginArray();
                for (PropertyValue value : values) {
                    writePropertyValue(writer, value, null);
                }
                writer.endArray();
            }
        }


        // Labels for rendering the properties in the frontend (or for API consumers)
        //
        writer.name("iriToLabels");
        writer.beginObject();

	Set<String> urisToGetLabelsFor = new LinkedHashSet<>();
	urisToGetLabelsFor.addAll(properties.getPropertyPredicates()); // need labels for all the predicates
	
	for(String predicate : properties.getPropertyPredicates()) {
		for(PropertyValue val : properties.getPropertyValues(predicate)) { // need labels for any IRI values
			if(val.getType() == Type.URI) {
				urisToGetLabelsFor.add( ((PropertyValueURI) val).getUri() );
			} else if(val.getType() == Type.BNODE) {
                OwlNode bnode = getNodeForPropertyValue(val);
                if(bnode != null) { // empty bnode values present in some ontologies, see issue #116
                    if(bnode.types.contains(OwlNode.NodeType.RDF_LIST)) {
                        for(PropertyValue listEntry : RdfListEvaluator.evaluateRdfList(bnode, this)) {
                            if(listEntry.getType() == Type.URI) {
                                urisToGetLabelsFor.add( ((PropertyValueURI) listEntry).getUri() );
                            }
                        }
                    }
                }
            } else if(val.getType() == Type.RELATED) {
			urisToGetLabelsFor.add(((PropertyValueRelated) val).getProperty());
			urisToGetLabelsFor.add(((PropertyValueRelated) val).getFiller().uri);
	    }
		}
	}

        for(String k : urisToGetLabelsFor) {

            OwlNode labelledNode = nodes.get(k);
            if(labelledNode == null) {
                continue;
            }

	    // TODO: any other label props to look for?
            List<PropertyValue> labelProps = labelledNode.properties.getPropertyValues("http://www.w3.org/2000/01/rdf-schema#label");
	    Map<String, TreeSet<String>> langToLabels = new HashMap<>();

            if(labelProps != null && labelProps.size() > 0) {
                for (PropertyValue prop : labelProps) {

                    if(prop.getType() != PropertyValue.Type.LITERAL)
                        continue;

                    String lang = ((PropertyValueLiteral) prop).getLang();

                    if(lang==null||lang.equals(""))
                        lang="en";

		    TreeSet<String> labels = langToLabels.get(lang);
		    if(labels == null) {
			labels = new TreeSet<>();
			langToLabels.put(lang, labels);
		    }

		    labels.add( ((PropertyValueLiteral) prop).getValue() );
                }
            }

	    for(String lang : langToLabels.keySet()) {

		TreeSet<String> labels = langToLabels.get(lang);

		writer.name(lang+"+"+k);
		if(labels.size() > 0 ){
			writer.beginArray();
			for(String label : labels) {
				writer.value(label);
			}
			writer.endArray();
		} else {
			writer.value(labels.iterator().next());
		}
	    }

        }

        writer.endObject();
    }


    public void writePropertyValue(JsonWriter writer, PropertyValue value, Set<String> types) throws IOException {
        if (value.axioms.size() > 0) {
            // reified
            writer.beginObject();
            writer.name("type");
	    writer.beginArray();
	    writer.value("reification");
	    writer.endArray();
            writer.name("value");
            writeValue(writer, value);
            writer.name("axioms");
	    writer.beginArray();
	    for(PropertySet axiom : value.axioms) {
		writer.beginObject();
		writeProperties(writer, axiom, null);
		writer.endObject();
	    }
	    writer.endArray();
            writer.endObject();
        } else {
            // not reified
            writeValue(writer, value);
        }

    }

    public void writeValue(JsonWriter writer, PropertyValue value) throws IOException {
        assert (value.axioms == null);

        switch(value.getType()) {
            case BNODE:
                OwlNode c = nodes.get(((PropertyValueBNode) value).getId());
                if (c == null) {
                    // empty bnode values present in some ontologies, see issue #116
                    writer.value("");
                } else {
                    writeNode(writer, c, null);
                }
                break;
            case ID:
                break;
            case LITERAL:
                PropertyValueLiteral literal = (PropertyValueLiteral) value;
		writer.beginObject();
		writer.name("type");
		writer.beginArray();
		writer.value("literal");
		writer.endArray();
		if(!literal.getDatatype().equals("http://www.w3.org/2001/XMLSchema#string")) {
			writer.name("datatype");
			writer.value(literal.getDatatype());
		}
		writer.name("value");
		writer.value(literal.getValue());
		if(!literal.getLang().equals("")) {
			writer.name("lang");
			writer.value(literal.getLang());
		}
		writer.endObject();
                break;
            case URI:
                writer.value(((PropertyValueURI) value).getUri());
                break;
            case RELATED:
                writer.beginObject();
                writer.name("property");
                writer.value(((PropertyValueRelated) value).getProperty());
                writer.name("value");
                writer.value(((PropertyValueRelated) value).getFiller().uri);
                writeProperties(writer, ((PropertyValueRelated) value).getClassExpression().properties, Set.of("related"));
                writer.endObject();
                break;
            default:
                writer.value("?");
                break;
        }
    }






    public Map<String, OwlNode> nodes = new TreeMap<>();
    public OwlNode ontologyNode = null;

    private OwlNode getOrCreateNode(Node node) {
        String id = nodeIdFromJenaNode(node);
        OwlNode entity = nodes.get(id);
        if (entity != null) {
            return entity;
        }

        entity = new OwlNode();

        if(!node.isBlank())
            entity.uri = id;

        nodes.put(id, entity);
        return entity;
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

        String subjId = nodeIdFromJenaNode(triple.getSubject());
        OwlNode subjNode = getOrCreateNode(triple.getSubject());

        String lang = triple.getObject().getLiteralLanguage();
        if(lang != null && !lang.equals("")) {
            languages.add(lang);
        }

        subjNode.properties.addProperty(triple.getPredicate().getURI(), PropertyValue.fromJenaNode(triple.getObject()));

    }

    public void handleNamedNodeTriple(Triple triple) {

        OwlNode subjNode = getOrCreateNode(triple.getSubject());

        switch (triple.getPredicate().getURI()) {
            case "http://www.w3.org/1999/02/22-rdf-syntax-ns#type":
                handleType(subjNode, triple.getObject());
                break;
            case "http://www.w3.org/1999/02/22-rdf-syntax-ns#rest":
            case "http://www.w3.org/1999/02/22-rdf-syntax-ns#first":
                subjNode.types.add(OwlNode.NodeType.RDF_LIST);
                break;

            case "http://www.w3.org/2002/07/owl#imports":
                importUrls.add(triple.getObject().getURI());
                break;
        }

        subjNode.properties.addProperty(triple.getPredicate().getURI(), PropertyValue.fromJenaNode(triple.getObject()));


    }

    public void handleType(OwlNode subjNode, Node type) {

        if(!type.isURI())
            return;

        switch (type.getURI()) {

            case "http://www.w3.org/2002/07/owl#Ontology":

                subjNode.types.add(OwlNode.NodeType.ONTOLOGY);

                if(ontologyNode == null) {
                    ontologyNode = subjNode;
                }

                break;

            case "http://www.w3.org/2002/07/owl#Class":
                subjNode.types.add(OwlNode.NodeType.CLASS);
                ++ numberOfClasses;
                break;

            case "http://www.w3.org/2002/07/owl#AnnotationProperty":
            case "http://www.w3.org/2002/07/owl#ObjectProperty":
            case "http://www.w3.org/2002/07/owl#DatatypeProperty":
                subjNode.types.add(OwlNode.NodeType.PROPERTY);
                ++ numberOfProperties;
                break;

            case "http://www.w3.org/2002/07/owl#NamedIndividual":
                subjNode.types.add(OwlNode.NodeType.NAMED_INDIVIDUAL);
                ++ numberOfIndividuals;
                break;

            case "http://www.w3.org/2002/07/owl#Axiom":
                subjNode.types.add(OwlNode.NodeType.AXIOM);
                break;

            case "http://www.w3.org/2002/07/owl#Restriction":
                subjNode.types.add(OwlNode.NodeType.RESTRICTION);
                break;

            case "http://www.w3.org/2002/07/owl#AllDisjointClasses":
                subjNode.types.add(OwlNode.NodeType.ALL_DISJOINT_CLASSES);
                break;
            case "http://www.w3.org/2002/07/owl#AllDisjointProperties":
                subjNode.types.add(OwlNode.NodeType.ALL_DISJOINT_PROPERTIES);
                break;
            case "http://www.w3.org/2002/07/owl#AllDifferent":
                subjNode.types.add(OwlNode.NodeType.ALL_DIFFERENT);
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


    public String nodeIdFromJenaNode(Node node)  {
        if(node.isURI()) {
            return node.getURI();
        }
        if(node.isBlank()) {
            return node.getBlankNodeId().toString();
        }
        throw new RuntimeException("unknown node type");
    }

    public String nodeIdFromPropertyValue(PropertyValue node)  {
        if(node.getType() == PropertyValue.Type.URI) {
            return ((PropertyValueURI) node).getUri();
        }
        if(node.getType() == PropertyValue.Type.BNODE) {
            return ((PropertyValueBNode) node).getId();
        }
        throw new RuntimeException("unknown node type");
    }



    private static void writeGenericValue(JsonWriter writer, Object val) throws IOException {

        if(val instanceof Collection) {
            writer.beginArray();
            for(Object entry : ((Collection<Object>) val)) {
                writeGenericValue(writer, entry);
            }
            writer.endArray();
        } else if(val instanceof Map) {
            Map<String,Object> map = new TreeMap<String,Object> ( (Map<String,Object>) val );
            writer.beginObject();
            for(String k : map.keySet()) {
                writer.name(k);
                writeGenericValue(writer, map.get(k));
            }
            writer.endObject();
        } else if(val instanceof String) {
            writer.value((String) val);
        } else if(val instanceof Integer) {
            writer.value((Integer) val);
        } else if(val instanceof Double) {
            writer.value((Double) val);
        } else if(val instanceof Long) {
            writer.value((Long) val);
        } else if(val instanceof Boolean) {
            writer.value((Boolean) val);
        } else if(val == null) {
            writer.nullValue();
        } else {
            throw new RuntimeException("Unknown value type");
        }

    }


    public boolean areSubgraphsIsomorphic(PropertyValue rootNodeA, PropertyValue rootNodeB) {

	OwlNode a = nodes.get(nodeIdFromPropertyValue(rootNodeA));
	OwlNode b = nodes.get(nodeIdFromPropertyValue(rootNodeB));

	if(! a.properties.getPropertyPredicates().equals( b.properties.getPropertyPredicates() )) {
		return false;
	}

	for(String predicate : a.properties.getPropertyPredicates()) {
		List<PropertyValue> valuesA = a.properties.getPropertyValues(predicate);
		List<PropertyValue> valuesB = b.properties.getPropertyValues(predicate);

		if(valuesA.size() != valuesB.size())
			return false;

		for(int n = 0; n < valuesA.size(); ++ n) {
			PropertyValue valueA = valuesA.get(n);
			PropertyValue valueB = valuesB.get(n);

			if(valueA.getType() != PropertyValue.Type.BNODE) {
				// non bnode value, simple case
				return valueA.equals(valueB);
			} 

			// bnode value

			if(valueB.getType() != PropertyValue.Type.BNODE)
				return false;

			if(!areSubgraphsIsomorphic(valueA, valueB))
				return false;
		}
	}

	return true;
    }


    public OwlNode getNodeForPropertyValue(PropertyValue value) {

        switch(value.getType()) {
            case URI:
                return nodes.get( ((PropertyValueURI) value).getUri() );
            case BNODE:
                return nodes.get( ((PropertyValueBNode) value).getId() );
            default:
                throw new RuntimeException("not a node");
        }
    }
}
