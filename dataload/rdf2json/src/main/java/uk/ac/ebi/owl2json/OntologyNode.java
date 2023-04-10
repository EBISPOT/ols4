package uk.ac.ebi.rdf2json;

import uk.ac.ebi.rdf2json.properties.PropertySet;

import java.util.*;

public class OntologyNode {

    public enum NodeType {
        ONTOLOGY,
        CLASS,
        PROPERTY,
        NAMED_INDIVIDUAL,
        ANNOTATION_PROPERTY,
        OBJECT_PROPERTY,
        AXIOM,
        RESTRICTION,
        RDF_LIST,
	ALL_DISJOINT_CLASSES,
	ALL_DIFFERENT,
	ALL_DISJOINT_PROPERTIES,
	NEGATIVE_PROPERTY_ASSERTION
    }
    

    public String uri;
    public Set<NodeType> types = new TreeSet<>();
//    List<OntologyNode> parents;
    public PropertySet properties = new PropertySet();



}


