package uk.ac.ebi.rdf2json;

import uk.ac.ebi.rdf2json.properties.PropertySet;

import java.util.*;
import java.util.stream.Collectors;

public class OntologyNode {

    public enum NodeType {
        ONTOLOGY("ontology"),
        ENTITY("entity"),
        CLASS("class"),
        PROPERTY("property"),
        INDIVIDUAL("individual"),
        ANNOTATION_PROPERTY("annotationProperty"),
        OBJECT_PROPERTY("objectProperty"),
        DATA_PROPERTY("dataProperty"),

        AXIOM("axiom"),
        RESTRICTION("restriction"),
        RDF_LIST("rdfList"),
        ALL_DISJOINT_CLASSES("allDisjointClasses"),
        ALL_DIFFERENT("allDifferent"),
        ALL_DISJOINT_PROPERTIES("allDisjointProperties"),
        NEGATIVE_PROPERTY_ASSERTION("negativePropertyAssertion");

        public final String name;

        NodeType (String name) {
            this.name = name;
        }

        static public Set<String> toString(Set<NodeType> nodeTypes) {
            return nodeTypes.stream().map(t -> t.name).collect(Collectors.toSet());
        }
    }
    

    public String uri;
    public Set<NodeType> types = new TreeSet<>();
//    List<OntologyNode> parents;
    public PropertySet properties = new PropertySet();



}


