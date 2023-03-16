package uk.ac.ebi.owl2json;

import uk.ac.ebi.owl2json.properties.PropertySet;

import java.util.*;

public class OwlNode {

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
//    List<OwlNode> parents;
    public PropertySet properties = new PropertySet();



}


