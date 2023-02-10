package uk.ac.ebi.owl2json.properties;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.graph.Node;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;

public abstract class PropertyValue {

    public enum Type {
        LITERAL,
        URI,
        BNODE,
        ID,
	    RELATED,
	    REFERENCED_ENTITIES,
        ANCESTORS
    }

    // reification
    public List<PropertySet> axioms = new ArrayList<>();

    public static PropertyValue fromJenaNode(Node node) {

       if(node.isLiteral()) {
           return new PropertyValueLiteral(node.getLiteralLexicalForm(), node.getLiteralDatatypeURI(), node.getLiteralLanguage());
       }
       if(node.isURI()) {
           return new PropertyValueURI(node.getURI());
       }
       if(node.isBlank()) {
           return new PropertyValueBNode(node.getBlankNodeId().toString());
       }

       throw new RuntimeException("Unknown node type");
    }

    protected PropertyValue() {
    }

    public abstract Type getType();
    public abstract boolean equals(PropertyValue other);

}

